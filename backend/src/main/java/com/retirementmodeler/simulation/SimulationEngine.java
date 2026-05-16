package com.retirementmodeler.simulation;

import com.retirementmodeler.model.Account;
import com.retirementmodeler.model.AccountType;
import com.retirementmodeler.model.FilingStatus;
import com.retirementmodeler.model.IncomeSource;
import com.retirementmodeler.model.IncomeType;
import com.retirementmodeler.model.Property;
import com.retirementmodeler.model.PropertyType;
import com.retirementmodeler.model.SimulationAssumptions;
import com.retirementmodeler.model.WithdrawalOrderingStrategy;
import com.retirementmodeler.model.WithdrawalStrategy;
import com.retirementmodeler.model.YearlyProjection;
import com.retirementmodeler.simulation.mortgage.MortgageAmortizer;
import com.retirementmodeler.simulation.withdrawal.AccountSnapshot;
import com.retirementmodeler.simulation.withdrawal.CustomAllocator;
import com.retirementmodeler.simulation.withdrawal.ProportionalAllocator;
import com.retirementmodeler.simulation.withdrawal.TaxOptimizedAllocator;
import com.retirementmodeler.simulation.withdrawal.WithdrawalAllocator;
import com.retirementmodeler.tax.FederalTaxBrackets2026;
import com.retirementmodeler.tax.RmdCalculator;
import com.retirementmodeler.tax.SocialSecurityTaxer;
import com.retirementmodeler.tax.TaxBracketProvider;
import com.retirementmodeler.tax.TaxBrackets;
import com.retirementmodeler.tax.TaxCalculator;
import com.retirementmodeler.tax.TaxResult;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.Period;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

/**
 * Simulates retirement projections at monthly granularity. The result is still emitted as one row
 * per year, anchored to the retirement month — i.e., if a user retires in October, every emitted
 * {@link YearlyProjection} is dated October of some year. Each row's per-year fields ({@code
 * yearContributions}, {@code yearWithdrawals}, etc.) cover the 12 months ending at that row's date.
 *
 * <p>Income comes from {@link IncomeSource} entities (pension, Social Security, employment, rental,
 * etc.). Pension and Social Security are not accounts; they are income streams that arrive directly
 * to the user. Social Security receives the SSA earnings-test treatment: pre-FRA earned income
 * causes a benefit reduction, and the cumulative withheld amount is recouped as a permanent monthly
 * bonus starting at FRA.
 *
 * <h2>Phase 4 tax model</h2>
 *
 * <p>Each year-anchor emit invokes the tax pipeline:
 *
 * <ol>
 *   <li>{@link SocialSecurityTaxer} — computes the taxable portion of gross SS benefits given the
 *       year's other income (provisional-income test).
 *   <li>{@link TaxBracketProvider} — returns the year's federal brackets, scaled from the 2026
 *       baseline by the simulation's cumulative inflation factor.
 *   <li>{@link TaxCalculator} — computes ordinary tax (progressive brackets on
 *       wages+pension+traditional-withdrawals+taxable-SS) and capital-gains tax (LTCG brackets
 *       stacked on top of taxable ordinary income).
 * </ol>
 *
 * <p>Withdrawals are categorized by {@link AccountType}:
 *
 * <ul>
 *   <li>{@code TRADITIONAL_401K}, {@code TRADITIONAL_IRA} → ordinary income.
 *   <li>{@code TAXABLE_BROKERAGE} → LTCG. <strong>Cost basis is not tracked; we assume 100%
 *       gains.</strong> Conservative on the tax side; refine when basis tracking lands.
 *   <li>{@code ROTH_401K}, {@code ROTH_IRA}, {@code HSA}, {@code SAVINGS} → tax-free for projection
 *       purposes. Savings interest is technically ordinary income; the simplification slightly
 *       under-taxes savings balances. Acceptable for MVP given typical balances.
 * </ul>
 *
 * <p>Year alignment caveat: rows are anchored to the retirement month, so the 12-month aggregation
 * window is e.g. Nov–Oct rather than Jan–Dec. Brackets are still looked up by calendar year, so tax
 * for a row is computed against the brackets of the row's anchor year using a Nov–Oct income
 * window. The mismatch is at most 1–3 months of income placed in the wrong calendar year — small
 * relative to the other modeling approximations.
 *
 * <h2>Phase 5 — Required Minimum Distributions</h2>
 *
 * <p>Starting at the SECURE 2.0 RMD age (73 for DOB ≤ 1959, 75 for DOB ≥ 1960), the engine forces a
 * minimum draw from Traditional 401(k) + Traditional IRA accounts each calendar year. The annual
 * obligation is computed at Jan 1 from prior-Dec-31 Traditional balance / IRS Uniform Lifetime
 * divisor for the age the user attains during the year. Strategy-driven Traditional withdrawals
 * count toward satisfying the obligation; any shortfall is force-drained in December
 * (proportionally across Traditional balances). Forced-RMD cash is deposited into the first SAVINGS
 * account — or, if none exists, a transient synthetic SAVINGS account that lives only for the
 * simulation. Per-year RMD totals surface on {@link YearlyProjection#yearRmd}.
 */
@Component
public class SimulationEngine {

  private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);
  private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

  // BigDecimal's MathContext bounds *precision* (significant digits) but not *scale* (decimal
  // digits). For zero-valued operands, multiply preserves scale, so a zero balance multiplied by
  // a high-scale growth factor each month gradually accumulates scale — eventually breaching
  // PostgreSQL numeric's 16,383-digit fractional limit and breaking JSONB round-trip. We enforce
  // a fixed scale on every BigDecimal we keep around so this can't happen.
  private static final int INTERNAL_SCALE = 8;
  private static final int OUTPUT_SCALE = 2;
  private static final RoundingMode ROUND = RoundingMode.HALF_UP;

  // SSA earnings-test thresholds (2025 nominal values). These are wage-indexed annually by SSA;
  // we proxy that by multiplying by the simulation's cumulative inflation factor each year.
  private static final BigDecimal UNDER_FRA_EARNINGS_THRESHOLD = BigDecimal.valueOf(23_400);
  private static final BigDecimal YEAR_OF_FRA_EARNINGS_THRESHOLD = BigDecimal.valueOf(62_160);

  // §121 home-sale capital-gains exclusion. Statutory: $250K single / $500K MFJ, frozen nominal
  // since 1997. We inflate-adjust to keep long-horizon projections sensible (without it, a 2055
  // primary-residence sale would show wildly inflated taxable gains). Documented simplification.
  private static final BigDecimal SECTION_121_EXCLUSION_SINGLE = new BigDecimal("250000");
  private static final BigDecimal SECTION_121_EXCLUSION_MFJ = new BigDecimal("500000");

  // Default selling cost (realtor + closing) if a property doesn't specify one. 6% covers a
  // typical full-service-realtor + closing-cost scenario.
  private static final BigDecimal DEFAULT_SELLING_COST_PCT = new BigDecimal("0.06");
  private static final BigDecimal UNDER_FRA_REDUCTION_RATIO = BigDecimal.valueOf(0.5);
  private static final BigDecimal YEAR_OF_FRA_REDUCTION_RATIO =
      BigDecimal.ONE.divide(BigDecimal.valueOf(3), MC);

  private final TaxBracketProvider taxBracketProvider;
  private final TaxCalculator taxCalculator;
  private final SocialSecurityTaxer socialSecurityTaxer;
  private final RmdCalculator rmdCalculator;

  public SimulationEngine(
      TaxBracketProvider taxBracketProvider,
      TaxCalculator taxCalculator,
      SocialSecurityTaxer socialSecurityTaxer,
      RmdCalculator rmdCalculator) {
    this.taxBracketProvider = taxBracketProvider;
    this.taxCalculator = taxCalculator;
    this.socialSecurityTaxer = socialSecurityTaxer;
    this.rmdCalculator = rmdCalculator;
  }

  /** Convenience constructor for tests — wires default instances of the tax components. */
  public SimulationEngine() {
    this(
        new TaxBracketProvider(),
        new TaxCalculator(),
        new SocialSecurityTaxer(),
        new RmdCalculator());
  }

  private static BigDecimal scaled(BigDecimal v) {
    return v.setScale(INTERNAL_SCALE, ROUND);
  }

  private static BigDecimal output(BigDecimal v) {
    return v.setScale(OUTPUT_SCALE, ROUND);
  }

  public List<YearlyProjection> projectDeterministic(
      List<Account> accounts,
      List<IncomeSource> incomeSources,
      SimulationAssumptions assumptions,
      FilingStatus filingStatus,
      LocalDate dateOfBirth,
      LocalDate plannedRetirementDate,
      int lifeExpectancy) {
    return projectDeterministic(
        accounts,
        incomeSources,
        List.of(),
        assumptions,
        filingStatus,
        dateOfBirth,
        plannedRetirementDate,
        lifeExpectancy);
  }

  public List<YearlyProjection> projectDeterministic(
      List<Account> accounts,
      List<IncomeSource> incomeSources,
      List<Property> properties,
      SimulationAssumptions assumptions,
      FilingStatus filingStatus,
      LocalDate dateOfBirth,
      LocalDate plannedRetirementDate,
      int lifeExpectancy) {
    return project(
        accounts,
        incomeSources,
        properties,
        assumptions,
        filingStatus,
        dateOfBirth,
        plannedRetirementDate,
        lifeExpectancy,
        null);
  }

  /**
   * Runs a single simulation trial and returns the trailing total balance at each row date. Used by
   * the Monte Carlo engine; the supplied {@code monthlyReturnSampler} should yield a return
   * appropriate for one month (already scaled from any annual mean / std-dev).
   */
  public List<BigDecimal> projectSingleTrial(
      List<Account> accounts,
      List<IncomeSource> incomeSources,
      SimulationAssumptions assumptions,
      FilingStatus filingStatus,
      LocalDate dateOfBirth,
      LocalDate plannedRetirementDate,
      int lifeExpectancy,
      DoubleSupplier monthlyReturnSampler) {
    return projectSingleTrial(
        accounts,
        incomeSources,
        List.of(),
        assumptions,
        filingStatus,
        dateOfBirth,
        plannedRetirementDate,
        lifeExpectancy,
        monthlyReturnSampler);
  }

  public List<BigDecimal> projectSingleTrial(
      List<Account> accounts,
      List<IncomeSource> incomeSources,
      List<Property> properties,
      SimulationAssumptions assumptions,
      FilingStatus filingStatus,
      LocalDate dateOfBirth,
      LocalDate plannedRetirementDate,
      int lifeExpectancy,
      DoubleSupplier monthlyReturnSampler) {
    return project(
            accounts,
            incomeSources,
            properties,
            assumptions,
            filingStatus,
            dateOfBirth,
            plannedRetirementDate,
            lifeExpectancy,
            monthlyReturnSampler)
        .stream()
        .map(YearlyProjection::balance)
        .toList();
  }

  private List<YearlyProjection> project(
      List<Account> accounts,
      List<IncomeSource> incomeSources,
      List<Property> properties,
      SimulationAssumptions assumptions,
      FilingStatus filingStatus,
      LocalDate dateOfBirth,
      LocalDate plannedRetirementDate,
      int lifeExpectancy,
      DoubleSupplier monthlyReturnSampler) {

    List<IncomeSource> sources = incomeSources != null ? incomeSources : List.of();
    List<Property> props = properties != null ? properties : List.of();
    FilingStatus status = filingStatus != null ? filingStatus : FilingStatus.SINGLE;

    LocalDate today = LocalDate.now().withDayOfMonth(1);
    LocalDate deathDate = dateOfBirth.plusYears(lifeExpectancy);
    LocalDate retirementStart = transitionStart(plannedRetirementDate);
    // Rows are anchored to December and represent calendar-year aggregates (Jan-Dec). The first
    // row may be partial if the simulation starts mid-year (sim-start → Dec covers fewer than
    // 12 months); the death year is dropped entirely if death falls before December (the prior
    // year's Dec row is the last emit). Both are accepted edges.
    Month rowAnchorMonth = Month.DECEMBER;

    LocalDate fraMonth = computeFraMonth(dateOfBirth);

    BigDecimal annualReturnRate =
        assumptions.getExpectedRateOfReturn() != null
            ? assumptions.getExpectedRateOfReturn()
            : BigDecimal.ZERO;
    BigDecimal monthlyDeterministicRate =
        BigDecimal.valueOf(Math.pow(1.0 + annualReturnRate.doubleValue(), 1.0 / 12.0) - 1.0);
    BigDecimal inflationRate =
        assumptions.getInflationRate() != null ? assumptions.getInflationRate() : BigDecimal.ZERO;

    WithdrawalAllocator allocator = buildAllocator(assumptions);

    // Per-account running balances + a fast id→index lookup so we can apply the allocator's
    // per-account result without scanning. `activeAccounts` is a mutable shallow copy because the
    // RMD path may lazily append a synthetic Savings account during the simulation.
    List<Account> activeAccounts = new ArrayList<>(accounts);
    List<BigDecimal> balances = new ArrayList<>(activeAccounts.size());
    Map<UUID, Integer> idxByUuid = new HashMap<>();
    for (int i = 0; i < activeAccounts.size(); i++) {
      Account a = activeAccounts.get(i);
      balances.add(scaled(a.getBalance() != null ? a.getBalance() : BigDecimal.ZERO));
      if (a.getId() != null) {
        idxByUuid.put(a.getId(), i);
      }
    }

    // RMD state (SECURE 2.0). rmdRemainingThisYear is the unsatisfied portion of the current
    // calendar year's RMD obligation; reset every Jan 1 to the year's annual RMD computed from
    // the prior-Dec-31 Traditional balance (or, on sim start mid-year, the user's current balance
    // as a proxy — a documented simplification). yearRmdTotal is the satisfied portion attributable
    // to the 12-month row window and emits as YearlyProjection.yearRmd.
    int rmdStartAge = RmdCalculator.rmdStartAge(dateOfBirth.getYear());
    BigDecimal rmdRemainingThisYear = scaled(BigDecimal.ZERO);
    BigDecimal yearRmdTotal = scaled(BigDecimal.ZERO);

    // Property state (Phase 5.2). Parallel arrays keyed by index into `props`:
    //   propertyMortgageBalances — running mortgage principal, amortized monthly. Initialized
    //     from Property.mortgageBalance.
    //   propertySold — true once the sale event has fired for the property; thereafter, no
    //     property expenses are added except the replacement housing cost, and the value drops
    //     to zero for yearPropertyValueTotal.
    // Value / property-tax / insurance / HOA / maintenance growth is computed on-the-fly by
    // multiplying the entity's "today's-dollars" amount by the current inflationFactor; no
    // explicit per-year mutation needed.
    List<BigDecimal> propertyMortgageBalances = new ArrayList<>(props.size());
    boolean[] propertySold = new boolean[props.size()];
    for (Property prop : props) {
      propertyMortgageBalances.add(
          scaled(prop.getMortgageBalance() != null ? prop.getMortgageBalance() : BigDecimal.ZERO));
    }

    BigDecimal inflationFactor = scaled(BigDecimal.ONE);

    // Per-year aggregates. yearIncomeNonSS / yearSocialSecurityGross feed both the existing
    // yearIncome field (their sum) and the new tax-breakdown fields.
    BigDecimal yearContributions = scaled(BigDecimal.ZERO);
    BigDecimal yearWithdrawals = scaled(BigDecimal.ZERO);
    BigDecimal yearIncomeNonSS = scaled(BigDecimal.ZERO);
    BigDecimal yearSocialSecurityGross = scaled(BigDecimal.ZERO);
    BigDecimal yearOrdinaryFromWithdrawals = scaled(BigDecimal.ZERO);
    BigDecimal yearCapitalGainsFromWithdrawals = scaled(BigDecimal.ZERO);
    // Property year aggregates.
    BigDecimal yearMortgageInterest = scaled(BigDecimal.ZERO);
    BigDecimal yearPropertyTaxPaid = scaled(BigDecimal.ZERO);
    BigDecimal yearHousingExpenses = scaled(BigDecimal.ZERO);
    BigDecimal yearSaleProceedsNet = scaled(BigDecimal.ZERO);
    BigDecimal yearSaleCapitalGains = scaled(BigDecimal.ZERO);

    // SS earnings-test state. ssWithholdRemaining is reset at each calendar-year start to the
    // year's projected reduction; cumulativeWithheldPreFRA accumulates across years and is
    // converted to a permanent monthly bonus (monthlyRecoupBonus) at the FRA month.
    BigDecimal ssWithholdRemaining = scaled(BigDecimal.ZERO);
    BigDecimal cumulativeWithheldPreFRA = scaled(BigDecimal.ZERO);
    BigDecimal monthlyRecoupBonus = scaled(BigDecimal.ZERO);
    boolean recoupComputed = false;

    List<YearlyProjection> rows = new ArrayList<>();
    LocalDate currentMonth = today;

    while (!currentMonth.isAfter(deathDate)) {
      // Recompute SS earnings-test reduction at each calendar-year boundary (and on the very
      // first iteration, which may be a partial year).
      if (currentMonth.equals(today) || currentMonth.getMonthValue() == 1) {
        ssWithholdRemaining =
            scaled(
                projectAnnualEarningsTestReduction(
                    sources, currentMonth, deathDate, fraMonth, inflationFactor));
        // Compute the year's RMD obligation. Balances at this point reflect end-of-prior-Dec (no
        // growth applied yet for the new year); on sim start mid-year, we use the user's current
        // balance as a proxy for prior-Dec-31 — a documented simplification for the partial first
        // year.
        int ageAttainedThisYear = currentMonth.getYear() - dateOfBirth.getYear();
        if (ageAttainedThisYear >= rmdStartAge) {
          BigDecimal traditionalBalance = BigDecimal.ZERO;
          for (int i = 0; i < activeAccounts.size(); i++) {
            if (isTraditional(activeAccounts.get(i).getAccountType())) {
              traditionalBalance = traditionalBalance.add(balances.get(i), MC);
            }
          }
          rmdRemainingThisYear =
              scaled(
                  rmdCalculator.computeAnnualRmd(
                      dateOfBirth.getYear(), ageAttainedThisYear, traditionalBalance));
        } else {
          rmdRemainingThisYear = scaled(BigDecimal.ZERO);
        }
      }

      // At the FRA month, freeze the recoup bonus. Spread the lifetime withheld evenly over
      // remaining simulation months as a permanent monthly increase to the SS benefit.
      if (!recoupComputed && !currentMonth.isBefore(fraMonth)) {
        long monthsRemaining = monthsRemaining(currentMonth, deathDate);
        if (monthsRemaining > 0 && cumulativeWithheldPreFRA.signum() > 0) {
          monthlyRecoupBonus =
              scaled(cumulativeWithheldPreFRA.divide(BigDecimal.valueOf(monthsRemaining), MC));
        }
        recoupComputed = true;
      }

      // Age at *end* of the current month — so a user born on Oct 22 is reported as 57 (not 56)
      // in October of the year they turn 57.
      LocalDate endOfMonth = currentMonth.withDayOfMonth(currentMonth.lengthOfMonth());
      int ageThisMonth = Period.between(dateOfBirth, endOfMonth).getYears();
      boolean isRetired = !currentMonth.isBefore(retirementStart);

      // 1. Apply this month's return to every account.
      BigDecimal monthlyRate =
          monthlyReturnSampler != null
              ? BigDecimal.valueOf(monthlyReturnSampler.getAsDouble())
              : monthlyDeterministicRate;
      BigDecimal growthFactor = BigDecimal.ONE.add(monthlyRate);
      for (int i = 0; i < activeAccounts.size(); i++) {
        balances.set(i, scaled(balances.get(i).multiply(growthFactor, MC)));
      }

      // 2. Pre-retirement contributions to retirement-savings accounts.
      BigDecimal monthContrib = BigDecimal.ZERO;
      for (int i = 0; i < activeAccounts.size(); i++) {
        Account account = activeAccounts.get(i);
        if (!isRetired
            && account.getAnnualContribution() != null
            && isContributionType(account.getAccountType())) {
          BigDecimal monthly =
              account.getAnnualContribution().divide(TWELVE, MC).multiply(inflationFactor, MC);
          balances.set(i, scaled(balances.get(i).add(monthly, MC)));
          monthContrib = scaled(monthContrib.add(monthly, MC));
        }
      }

      // 3. Income from all sources active this month. SS is aggregated separately so we can
      // apply the earnings-test withhold (pre-FRA) and recoup bonus (post-FRA) once at the user
      // level, not per source.
      BigDecimal monthIncomeNonSS = BigDecimal.ZERO;
      BigDecimal monthSSGross = BigDecimal.ZERO;
      for (IncomeSource src : sources) {
        if (!isIncomeActive(src, currentMonth)) continue;
        BigDecimal monthly =
            src.isInflationAdjusted()
                ? src.getMonthlyAmount().multiply(inflationFactor, MC)
                : src.getMonthlyAmount();
        if (src.getType() == IncomeType.SOCIAL_SECURITY) {
          monthSSGross = scaled(monthSSGross.add(monthly, MC));
        } else {
          monthIncomeNonSS = scaled(monthIncomeNonSS.add(monthly, MC));
        }
      }

      BigDecimal monthSSPaid = monthSSGross;
      if (monthSSGross.signum() > 0) {
        if (currentMonth.isBefore(fraMonth)) {
          BigDecimal withheld = ssWithholdRemaining.min(monthSSGross);
          monthSSPaid = scaled(monthSSGross.subtract(withheld, MC));
          ssWithholdRemaining = scaled(ssWithholdRemaining.subtract(withheld, MC));
          cumulativeWithheldPreFRA = scaled(cumulativeWithheldPreFRA.add(withheld, MC));
        } else {
          monthSSPaid = scaled(monthSSGross.add(monthlyRecoupBonus, MC));
        }
      }

      BigDecimal monthIncome = scaled(monthIncomeNonSS.add(monthSSPaid, MC));

      // 4. Post-retirement withdrawals. The allocator decides which accounts to drain from and
      // returns the per-account split; we apply it back to balances and bucket each piece by its
      // account's tax treatment.
      BigDecimal monthWithdrawal = BigDecimal.ZERO;
      BigDecimal monthOrdinaryFromWithdrawals = BigDecimal.ZERO;
      BigDecimal monthLtcgFromWithdrawals = BigDecimal.ZERO;
      BigDecimal monthRmdSatisfied = BigDecimal.ZERO;
      if (isRetired) {
        BigDecimal totalBalanceNow = sum(balances);
        BigDecimal requested =
            computeMonthlyWithdrawal(totalBalanceNow, assumptions, inflationFactor, monthIncome);
        if (requested.signum() > 0 && totalBalanceNow.signum() > 0) {
          List<AccountSnapshot> snapshots = new ArrayList<>(activeAccounts.size());
          for (int i = 0; i < activeAccounts.size(); i++) {
            snapshots.add(
                new AccountSnapshot(
                    activeAccounts.get(i).getId(),
                    activeAccounts.get(i).getAccountType(),
                    balances.get(i)));
          }
          Map<UUID, BigDecimal> allocation = allocator.allocate(snapshots, requested);
          for (Map.Entry<UUID, BigDecimal> entry : allocation.entrySet()) {
            Integer idx = idxByUuid.get(entry.getKey());
            if (idx == null) continue;
            BigDecimal share = entry.getValue();
            if (share.signum() <= 0) continue;
            balances.set(idx, scaled(balances.get(idx).subtract(share, MC)));
            monthWithdrawal = scaled(monthWithdrawal.add(share, MC));
            switch (activeAccounts.get(idx).getAccountType()) {
              case TRADITIONAL_401K, TRADITIONAL_IRA -> {
                monthOrdinaryFromWithdrawals = scaled(monthOrdinaryFromWithdrawals.add(share, MC));
                // Strategy-driven Traditional withdrawals count toward this year's RMD.
                BigDecimal applied = share.min(rmdRemainingThisYear);
                if (applied.signum() > 0) {
                  rmdRemainingThisYear = scaled(rmdRemainingThisYear.subtract(applied, MC));
                  monthRmdSatisfied = scaled(monthRmdSatisfied.add(applied, MC));
                }
              }
              case TAXABLE_BROKERAGE ->
                  monthLtcgFromWithdrawals = scaled(monthLtcgFromWithdrawals.add(share, MC));
              case ROTH_401K, ROTH_IRA, HSA, SAVINGS -> {
                // Tax-free for projection purposes — see class javadoc.
              }
            }
          }
        }
      }

      // 4.5 Property processing (Phase 5.2). Per active property:
      //   a) If the planned sale date triggers this month (and not already sold), execute sale —
      //      pay off mortgage, deposit net proceeds to Savings, record taxable cap gains.
      //   b) If sold, the replacement housing cost (today's dollars × inflationFactor) is the
      //      property's only ongoing expense for the rest of the simulation.
      //   c) If not sold, amortize one month of mortgage and accumulate property tax, insurance,
      //      HOA, and maintenance into this month's housing expense.
      // Housing expenses are mandatory outflows funded after the strategy-driven withdrawal in
      // step 4. They drain accounts via the same allocator, are bucketed by tax type just like
      // withdrawals, and Traditional-account draws count toward the year's RMD.
      BigDecimal monthHousingExpenses = BigDecimal.ZERO;
      BigDecimal monthMortgageInterest = BigDecimal.ZERO;
      BigDecimal monthPropertyTaxPaid = BigDecimal.ZERO;
      BigDecimal monthSaleProceedsNet = BigDecimal.ZERO;
      BigDecimal monthSaleCapitalGainsAmt = BigDecimal.ZERO;
      for (int p = 0; p < props.size(); p++) {
        Property prop = props.get(p);

        // 4.5(a) Sale event — fires on the first month >= transitionStart(saleDate).
        if (!propertySold[p] && prop.getPlannedSaleDate() != null) {
          LocalDate saleStart = transitionStart(prop.getPlannedSaleDate());
          if (!currentMonth.isBefore(saleStart)) {
            BigDecimal grossValue =
                (prop.getCurrentValue() != null ? prop.getCurrentValue() : BigDecimal.ZERO)
                    .multiply(inflationFactor, MC);
            BigDecimal sellingCostPct =
                prop.getSellingCostPct() != null
                    ? prop.getSellingCostPct()
                    : DEFAULT_SELLING_COST_PCT;
            BigDecimal grossProceeds =
                grossValue.multiply(BigDecimal.ONE.subtract(sellingCostPct, MC), MC);
            BigDecimal mortgagePayoff = propertyMortgageBalances.get(p);
            BigDecimal netProceeds =
                grossProceeds.subtract(mortgagePayoff, MC).max(BigDecimal.ZERO);
            BigDecimal costBasis =
                prop.getCostBasis() != null ? prop.getCostBasis() : BigDecimal.ZERO;
            BigDecimal gain = grossProceeds.subtract(costBasis, MC).max(BigDecimal.ZERO);
            BigDecimal taxableGain = gain;
            if (prop.getType() == PropertyType.PRIMARY_RESIDENCE) {
              BigDecimal exclusion =
                  (status == FilingStatus.MARRIED_FILING_JOINTLY
                          ? SECTION_121_EXCLUSION_MFJ
                          : SECTION_121_EXCLUSION_SINGLE)
                      .multiply(inflationFactor, MC);
              taxableGain = gain.subtract(exclusion, MC).max(BigDecimal.ZERO);
            }
            // Deposit net proceeds into the first SAVINGS account (creating a synthetic one if
            // none exists — same pattern as the RMD overflow path).
            int savingsIdx = findSavingsIndex(activeAccounts);
            if (savingsIdx < 0) {
              Account synthetic = new Account();
              synthetic.setId(UUID.randomUUID());
              synthetic.setAccountType(AccountType.SAVINGS);
              synthetic.setName("Savings (sale proceeds)");
              synthetic.setBalance(BigDecimal.ZERO);
              activeAccounts.add(synthetic);
              balances.add(scaled(BigDecimal.ZERO));
              idxByUuid.put(synthetic.getId(), balances.size() - 1);
              savingsIdx = balances.size() - 1;
            }
            balances.set(savingsIdx, scaled(balances.get(savingsIdx).add(netProceeds, MC)));
            propertySold[p] = true;
            propertyMortgageBalances.set(p, BigDecimal.ZERO);
            monthSaleProceedsNet = scaled(monthSaleProceedsNet.add(netProceeds, MC));
            monthSaleCapitalGainsAmt = scaled(monthSaleCapitalGainsAmt.add(taxableGain, MC));
          }
        }

        // 4.5(b) / 4.5(c) Ongoing housing expense for this month.
        if (propertySold[p]) {
          BigDecimal monthlyReplacement =
              (prop.getPostSaleMonthlyHousingCost() != null
                      ? prop.getPostSaleMonthlyHousingCost()
                      : BigDecimal.ZERO)
                  .multiply(inflationFactor, MC);
          monthHousingExpenses = scaled(monthHousingExpenses.add(monthlyReplacement, MC));
        } else {
          // Mortgage amortization.
          BigDecimal mortgageBalance = propertyMortgageBalances.get(p);
          BigDecimal monthlyPI =
              prop.getMortgageMonthlyPi() != null ? prop.getMortgageMonthlyPi() : BigDecimal.ZERO;
          if (mortgageBalance.signum() > 0 && monthlyPI.signum() > 0) {
            BigDecimal rate =
                prop.getMortgageAnnualRate() != null
                    ? prop.getMortgageAnnualRate()
                    : BigDecimal.ZERO;
            MortgageAmortizer.MonthlyStep step =
                MortgageAmortizer.step(mortgageBalance, rate, monthlyPI);
            propertyMortgageBalances.set(p, scaled(step.newBalance()));
            monthHousingExpenses = scaled(monthHousingExpenses.add(step.paymentMade(), MC));
            monthMortgageInterest = scaled(monthMortgageInterest.add(step.interest(), MC));
          }
          // Recurring expenses — each inflates from today's-dollars on the entity.
          BigDecimal monthlyTax =
              (prop.getAnnualPropertyTax() != null ? prop.getAnnualPropertyTax() : BigDecimal.ZERO)
                  .divide(TWELVE, MC)
                  .multiply(inflationFactor, MC);
          monthHousingExpenses = scaled(monthHousingExpenses.add(monthlyTax, MC));
          monthPropertyTaxPaid = scaled(monthPropertyTaxPaid.add(monthlyTax, MC));
          BigDecimal monthlyInsurance =
              (prop.getAnnualInsurance() != null ? prop.getAnnualInsurance() : BigDecimal.ZERO)
                  .divide(TWELVE, MC)
                  .multiply(inflationFactor, MC);
          monthHousingExpenses = scaled(monthHousingExpenses.add(monthlyInsurance, MC));
          BigDecimal monthlyHoa =
              (prop.getMonthlyHoa() != null ? prop.getMonthlyHoa() : BigDecimal.ZERO)
                  .multiply(inflationFactor, MC);
          monthHousingExpenses = scaled(monthHousingExpenses.add(monthlyHoa, MC));
          BigDecimal currentValueInflated =
              (prop.getCurrentValue() != null ? prop.getCurrentValue() : BigDecimal.ZERO)
                  .multiply(inflationFactor, MC);
          BigDecimal maintenancePct =
              prop.getAnnualMaintenancePct() != null
                  ? prop.getAnnualMaintenancePct()
                  : BigDecimal.ZERO;
          BigDecimal monthlyMaintenance =
              currentValueInflated.multiply(maintenancePct, MC).divide(TWELVE, MC);
          monthHousingExpenses = scaled(monthHousingExpenses.add(monthlyMaintenance, MC));
        }
      }

      // Sale capital gains feed the existing LTCG tax bucket alongside brokerage-withdrawal gains.
      if (monthSaleCapitalGainsAmt.signum() > 0) {
        monthLtcgFromWithdrawals =
            scaled(monthLtcgFromWithdrawals.add(monthSaleCapitalGainsAmt, MC));
      }

      // How much of this month's housing expense needs to come from accounts (vs. paid out of
      // income or, pre-retirement, wages that aren't modeled):
      //   - Pre-retirement: 0. The user is presumed to be working; wages (not modeled in the
      //     engine) cover housing alongside the contributions already going into accounts.
      //   - Post-retirement: housing expense minus the income that's "free" after the user's
      //     discretionary need has been met.
      //       CASHFLOW_TARGET: surplus = income − target_inflated. Income above target offsets
      //         housing (so when SS at 67 swings income above target, the account drain for
      //         housing also drops correspondingly).
      //       PORTFOLIO_PERCENTAGE: the strategy's 4%-of-balance draw is independent of income,
      //         so all income is available to offset housing.
      // yearHousingExpenses still tracks the full housing cost for display — what changes is
      // only how much is actually drained from accounts.
      BigDecimal housingToDrain;
      if (!isRetired) {
        housingToDrain = BigDecimal.ZERO;
      } else {
        BigDecimal incomeForHousing;
        if (assumptions.getWithdrawalStrategy() == WithdrawalStrategy.CASHFLOW_TARGET) {
          BigDecimal targetInflated =
              (assumptions.getWithdrawalMonthlyAmount() != null
                      ? assumptions.getWithdrawalMonthlyAmount()
                      : BigDecimal.ZERO)
                  .multiply(inflationFactor, MC);
          incomeForHousing = monthIncome.subtract(targetInflated, MC).max(BigDecimal.ZERO);
        } else {
          incomeForHousing = monthIncome;
        }
        housingToDrain = monthHousingExpenses.subtract(incomeForHousing, MC).max(BigDecimal.ZERO);
      }

      // Drain housing expenses from accounts via the same allocator used for discretionary
      // withdrawals. Bucket by tax type; Traditional draws count toward RMD. Housing $ does NOT
      // accumulate into yearWithdrawals — it surfaces separately as yearHousingExpenses.
      if (housingToDrain.signum() > 0) {
        BigDecimal totalBalanceForHousing = sum(balances);
        BigDecimal toFund = housingToDrain.min(totalBalanceForHousing);
        if (toFund.signum() > 0) {
          List<AccountSnapshot> snapshots = new ArrayList<>(activeAccounts.size());
          for (int i = 0; i < activeAccounts.size(); i++) {
            snapshots.add(
                new AccountSnapshot(
                    activeAccounts.get(i).getId(),
                    activeAccounts.get(i).getAccountType(),
                    balances.get(i)));
          }
          Map<UUID, BigDecimal> allocation = allocator.allocate(snapshots, toFund);
          for (Map.Entry<UUID, BigDecimal> entry : allocation.entrySet()) {
            Integer idx = idxByUuid.get(entry.getKey());
            if (idx == null) continue;
            BigDecimal share = entry.getValue();
            if (share.signum() <= 0) continue;
            balances.set(idx, scaled(balances.get(idx).subtract(share, MC)));
            // Roll housing drain into monthWithdrawal so the "Withdrawals" column matches the
            // user's mental model of "total amount drawn from my accounts this period." The
            // separate yearHousingExpenses column still surfaces the total housing cost for
            // display; the difference between yearHousingExpenses and the housing portion of
            // yearWithdrawals = the part that was covered by income surplus (no account drain).
            monthWithdrawal = scaled(monthWithdrawal.add(share, MC));
            switch (activeAccounts.get(idx).getAccountType()) {
              case TRADITIONAL_401K, TRADITIONAL_IRA -> {
                monthOrdinaryFromWithdrawals = scaled(monthOrdinaryFromWithdrawals.add(share, MC));
                BigDecimal applied = share.min(rmdRemainingThisYear);
                if (applied.signum() > 0) {
                  rmdRemainingThisYear = scaled(rmdRemainingThisYear.subtract(applied, MC));
                  monthRmdSatisfied = scaled(monthRmdSatisfied.add(applied, MC));
                }
              }
              case TAXABLE_BROKERAGE ->
                  monthLtcgFromWithdrawals = scaled(monthLtcgFromWithdrawals.add(share, MC));
              case ROTH_401K, ROTH_IRA, HSA, SAVINGS -> {
                // Tax-free for projection purposes.
              }
            }
          }
        }
      }

      // RMD top-up. In calendar December, if the year's RMD obligation is not yet satisfied by
      // strategy withdrawals, force the shortfall from Traditional accounts (proportional within
      // tier). Gross amount lands in Savings — existing if any, else a transient synthetic Savings
      // account (per the design decision: forced cash has to go somewhere and Savings is the most
      // realistic landing spot for unspent post-RMD wealth).
      if (currentMonth.getMonthValue() == 12 && rmdRemainingThisYear.signum() > 0) {
        BigDecimal traditionalAvailable = BigDecimal.ZERO;
        for (int i = 0; i < activeAccounts.size(); i++) {
          if (isTraditional(activeAccounts.get(i).getAccountType())) {
            traditionalAvailable = traditionalAvailable.add(balances.get(i), MC);
          }
        }
        BigDecimal forced = rmdRemainingThisYear.min(traditionalAvailable);
        if (forced.signum() > 0 && traditionalAvailable.signum() > 0) {
          BigDecimal allocated = BigDecimal.ZERO;
          for (int i = 0; i < activeAccounts.size(); i++) {
            if (!isTraditional(activeAccounts.get(i).getAccountType())) continue;
            BigDecimal accountBalance = balances.get(i);
            if (accountBalance.signum() <= 0) continue;
            BigDecimal share = accountBalance.divide(traditionalAvailable, MC).multiply(forced, MC);
            balances.set(i, scaled(accountBalance.subtract(share, MC)));
            allocated = scaled(allocated.add(share, MC));
            monthWithdrawal = scaled(monthWithdrawal.add(share, MC));
            monthOrdinaryFromWithdrawals = scaled(monthOrdinaryFromWithdrawals.add(share, MC));
            monthRmdSatisfied = scaled(monthRmdSatisfied.add(share, MC));
          }
          // Deposit gross forced amount into Savings. Existing first; else synthetic.
          int savingsIdx = findSavingsIndex(activeAccounts);
          if (savingsIdx < 0) {
            Account synthetic = new Account();
            synthetic.setId(UUID.randomUUID());
            synthetic.setAccountType(AccountType.SAVINGS);
            synthetic.setName("Savings (RMD overflow)");
            synthetic.setBalance(BigDecimal.ZERO);
            activeAccounts.add(synthetic);
            balances.add(scaled(BigDecimal.ZERO));
            idxByUuid.put(synthetic.getId(), balances.size() - 1);
            savingsIdx = balances.size() - 1;
          }
          balances.set(savingsIdx, scaled(balances.get(savingsIdx).add(allocated, MC)));
        }
        rmdRemainingThisYear = scaled(BigDecimal.ZERO);
      }

      yearContributions = scaled(yearContributions.add(monthContrib, MC));
      yearWithdrawals = scaled(yearWithdrawals.add(monthWithdrawal, MC));
      yearIncomeNonSS = scaled(yearIncomeNonSS.add(monthIncomeNonSS, MC));
      yearSocialSecurityGross = scaled(yearSocialSecurityGross.add(monthSSPaid, MC));
      yearOrdinaryFromWithdrawals =
          scaled(yearOrdinaryFromWithdrawals.add(monthOrdinaryFromWithdrawals, MC));
      yearCapitalGainsFromWithdrawals =
          scaled(yearCapitalGainsFromWithdrawals.add(monthLtcgFromWithdrawals, MC));
      yearRmdTotal = scaled(yearRmdTotal.add(monthRmdSatisfied, MC));
      yearMortgageInterest = scaled(yearMortgageInterest.add(monthMortgageInterest, MC));
      yearPropertyTaxPaid = scaled(yearPropertyTaxPaid.add(monthPropertyTaxPaid, MC));
      yearHousingExpenses = scaled(yearHousingExpenses.add(monthHousingExpenses, MC));
      yearSaleProceedsNet = scaled(yearSaleProceedsNet.add(monthSaleProceedsNet, MC));
      yearSaleCapitalGains = scaled(yearSaleCapitalGains.add(monthSaleCapitalGainsAmt, MC));

      // 5. Emit a row at each retirement-anchor month.
      if (currentMonth.getMonth() == rowAnchorMonth) {
        BigDecimal totalBalance = sum(balances);

        // Tax pipeline. Order matters: SS taxability depends on other ordinary income +
        // capital gains; the calculator then folds taxable-SS into ordinary income.
        BigDecimal ordinaryNonSS = yearIncomeNonSS.add(yearOrdinaryFromWithdrawals, MC);
        BigDecimal capitalGains = yearCapitalGainsFromWithdrawals;
        BigDecimal taxableSS =
            socialSecurityTaxer.computeTaxableAmount(
                status, yearSocialSecurityGross, ordinaryNonSS.add(capitalGains, MC));
        BigDecimal ordinaryIncome = ordinaryNonSS.add(taxableSS, MC);

        TaxBrackets brackets =
            taxBracketProvider.bracketsForYear(currentMonth.getYear(), inflationFactor);
        // Itemized deduction = mortgage interest + SALT-capped property tax. SALT cap is held
        // constant nominal (documented simplification — OBBBA has a 2026-2029 ramp followed by a
        // reversion in 2030 that we don't model). TaxCalculator picks max(itemized, standard).
        BigDecimal saltCappedTax = yearPropertyTaxPaid.min(FederalTaxBrackets2026.SALT_CAP);
        BigDecimal itemizedDeduction = yearMortgageInterest.add(saltCappedTax, MC);
        TaxResult tax =
            taxCalculator.compute(
                status, ordinaryIncome, capitalGains, brackets, itemizedDeduction);
        BigDecimal standardDeduction = brackets.standardDeductionFor(status);
        BigDecimal deductionUsed = standardDeduction.max(itemizedDeduction);

        BigDecimal yearIncome = yearIncomeNonSS.add(yearSocialSecurityGross, MC);

        // Net-worth display: sum of active (not-yet-sold) property values at this row time.
        BigDecimal propertyValueTotal = BigDecimal.ZERO;
        for (int p = 0; p < props.size(); p++) {
          if (propertySold[p]) continue;
          Property prop = props.get(p);
          if (prop.getCurrentValue() != null) {
            propertyValueTotal =
                propertyValueTotal.add(prop.getCurrentValue().multiply(inflationFactor, MC), MC);
          }
        }

        rows.add(
            new YearlyProjection(
                ageThisMonth,
                currentMonth,
                output(totalBalance),
                output(yearContributions),
                output(yearWithdrawals),
                output(yearIncome),
                output(ordinaryIncome),
                output(capitalGains),
                output(yearSocialSecurityGross),
                output(taxableSS),
                output(tax.ordinaryTax()),
                output(tax.capitalGainsTax()),
                output(tax.totalTax()),
                output(yearRmdTotal),
                output(yearMortgageInterest),
                output(yearPropertyTaxPaid),
                output(yearHousingExpenses),
                output(yearSaleProceedsNet),
                output(yearSaleCapitalGains),
                output(propertyValueTotal),
                output(deductionUsed),
                scaled(inflationFactor)));

        // Reset year-deltas; advance inflation for the next year.
        yearContributions = scaled(BigDecimal.ZERO);
        yearWithdrawals = scaled(BigDecimal.ZERO);
        yearIncomeNonSS = scaled(BigDecimal.ZERO);
        yearSocialSecurityGross = scaled(BigDecimal.ZERO);
        yearOrdinaryFromWithdrawals = scaled(BigDecimal.ZERO);
        yearCapitalGainsFromWithdrawals = scaled(BigDecimal.ZERO);
        yearRmdTotal = scaled(BigDecimal.ZERO);
        yearMortgageInterest = scaled(BigDecimal.ZERO);
        yearPropertyTaxPaid = scaled(BigDecimal.ZERO);
        yearHousingExpenses = scaled(BigDecimal.ZERO);
        yearSaleProceedsNet = scaled(BigDecimal.ZERO);
        yearSaleCapitalGains = scaled(BigDecimal.ZERO);
        inflationFactor = scaled(inflationFactor.multiply(BigDecimal.ONE.add(inflationRate), MC));
      }

      currentMonth = currentMonth.plusMonths(1);
    }

    return rows;
  }

  private static WithdrawalAllocator buildAllocator(SimulationAssumptions assumptions) {
    WithdrawalOrderingStrategy strategy = assumptions.getWithdrawalOrderingStrategy();
    if (strategy == null) {
      strategy = WithdrawalOrderingStrategy.PROPORTIONAL;
    }
    return switch (strategy) {
      case PROPORTIONAL -> new ProportionalAllocator();
      case TAX_OPTIMIZED -> new TaxOptimizedAllocator();
      case CUSTOM -> new CustomAllocator(assumptions.getCustomWithdrawalOrder());
    };
  }

  private BigDecimal computeMonthlyWithdrawal(
      BigDecimal totalBalance,
      SimulationAssumptions assumptions,
      BigDecimal inflationFactor,
      BigDecimal monthIncome) {
    if (totalBalance.signum() <= 0) {
      return BigDecimal.ZERO;
    }
    return switch (assumptions.getWithdrawalStrategy()) {
      case PORTFOLIO_PERCENTAGE -> {
        // Conventional 4%-rule semantics: withdraw a percentage of savings. Income (pension /
        // SS / employment) is supplemental, not netted out.
        BigDecimal pct =
            assumptions.getWithdrawalPercentage() != null
                ? assumptions.getWithdrawalPercentage()
                : BigDecimal.valueOf(0.04);
        yield totalBalance.multiply(pct, MC).divide(TWELVE, MC);
      }
      case CASHFLOW_TARGET -> {
        // The configured monthly amount is the user's cashflow target. Savings only cover the
        // gap between target and incoming income. If income meets or exceeds the target,
        // savings withdrawal is zero (surplus is unused, not banked).
        BigDecimal monthly =
            assumptions.getWithdrawalMonthlyAmount() != null
                ? assumptions.getWithdrawalMonthlyAmount()
                : BigDecimal.ZERO;
        BigDecimal inflatedTarget = monthly.multiply(inflationFactor, MC);
        yield inflatedTarget.subtract(monthIncome, MC).max(BigDecimal.ZERO);
      }
    };
  }

  /**
   * Returns the calendar month in which a transition triggered by {@code triggerDate} takes effect.
   * Triggers on the 1st of a month take effect that same month; otherwise they take effect at the
   * start of the next month.
   */
  private static LocalDate transitionStart(LocalDate triggerDate) {
    return triggerDate.getDayOfMonth() == 1
        ? triggerDate
        : triggerDate.withDayOfMonth(1).plusMonths(1);
  }

  /**
   * Whether an income source is active in the given month. Active for any month whose YearMonth
   * falls in [startDate's YearMonth, endDate's YearMonth] (NULL bounds = open).
   */
  private static boolean isIncomeActive(IncomeSource src, LocalDate currentMonth) {
    YearMonth ym = YearMonth.from(currentMonth);
    if (src.getStartDate() != null && ym.isBefore(YearMonth.from(src.getStartDate()))) {
      return false;
    }
    if (src.getEndDate() != null && ym.isAfter(YearMonth.from(src.getEndDate()))) {
      return false;
    }
    return true;
  }

  /**
   * Full Retirement Age month — the calendar month containing the FRA date, per SSA's lookup tables
   * (1937 → 65; sliding 1938-1959; 1960+ → 67). Earnings test no longer applies starting with this
   * month.
   */
  private static LocalDate computeFraMonth(LocalDate dateOfBirth) {
    int birthYear = dateOfBirth.getYear();
    int baseAge;
    int extraMonths;
    if (birthYear <= 1937) {
      baseAge = 65;
      extraMonths = 0;
    } else if (birthYear == 1938) {
      baseAge = 65;
      extraMonths = 2;
    } else if (birthYear == 1939) {
      baseAge = 65;
      extraMonths = 4;
    } else if (birthYear == 1940) {
      baseAge = 65;
      extraMonths = 6;
    } else if (birthYear == 1941) {
      baseAge = 65;
      extraMonths = 8;
    } else if (birthYear == 1942) {
      baseAge = 65;
      extraMonths = 10;
    } else if (birthYear <= 1954) {
      baseAge = 66;
      extraMonths = 0;
    } else if (birthYear == 1955) {
      baseAge = 66;
      extraMonths = 2;
    } else if (birthYear == 1956) {
      baseAge = 66;
      extraMonths = 4;
    } else if (birthYear == 1957) {
      baseAge = 66;
      extraMonths = 6;
    } else if (birthYear == 1958) {
      baseAge = 66;
      extraMonths = 8;
    } else if (birthYear == 1959) {
      baseAge = 66;
      extraMonths = 10;
    } else {
      baseAge = 67;
      extraMonths = 0;
    }
    return dateOfBirth.plusYears(baseAge).plusMonths(extraMonths).withDayOfMonth(1);
  }

  /**
   * Project the SSA earnings-test reduction for the calendar year starting at {@code yearStart}.
   * Returns the dollar amount of SS to withhold across the year, capped at the year's projected SS.
   */
  private BigDecimal projectAnnualEarningsTestReduction(
      List<IncomeSource> sources,
      LocalDate yearStart,
      LocalDate deathDate,
      LocalDate fraMonth,
      BigDecimal inflationFactor) {
    if (!yearStart.isBefore(fraMonth)) {
      return BigDecimal.ZERO;
    }
    LocalDate calendarYearEnd = LocalDate.of(yearStart.getYear(), 12, 1);
    LocalDate yearWindowEnd = !calendarYearEnd.isAfter(deathDate) ? calendarYearEnd : deathDate;

    boolean yearOfFra = !fraMonth.isAfter(yearWindowEnd) && !fraMonth.isBefore(yearStart);

    BigDecimal threshold;
    BigDecimal ratio;
    LocalDate earnedWindowEnd;
    if (yearOfFra) {
      threshold = YEAR_OF_FRA_EARNINGS_THRESHOLD.multiply(inflationFactor, MC);
      ratio = YEAR_OF_FRA_REDUCTION_RATIO;
      earnedWindowEnd = fraMonth.minusMonths(1);
      if (earnedWindowEnd.isBefore(yearStart)) {
        return BigDecimal.ZERO;
      }
    } else {
      threshold = UNDER_FRA_EARNINGS_THRESHOLD.multiply(inflationFactor, MC);
      ratio = UNDER_FRA_REDUCTION_RATIO;
      earnedWindowEnd = yearWindowEnd;
    }

    BigDecimal earned =
        sumActiveMonthlyIncome(
            sources, t -> t.isEarned(), yearStart, earnedWindowEnd, inflationFactor);
    BigDecimal excess = earned.subtract(threshold, MC).max(BigDecimal.ZERO);
    BigDecimal reduction = excess.multiply(ratio, MC);

    BigDecimal annualSS =
        sumActiveMonthlyIncome(
            sources,
            t -> t == IncomeType.SOCIAL_SECURITY,
            yearStart,
            yearWindowEnd,
            inflationFactor);
    return reduction.min(annualSS);
  }

  /** Sum income from sources of matching type that are active in any month within the window. */
  private BigDecimal sumActiveMonthlyIncome(
      List<IncomeSource> sources,
      Predicate<IncomeType> typeFilter,
      LocalDate windowStart,
      LocalDate windowEndInclusive,
      BigDecimal inflationFactor) {
    BigDecimal total = BigDecimal.ZERO;
    for (IncomeSource src : sources) {
      if (!typeFilter.test(src.getType())) continue;
      BigDecimal monthly =
          src.isInflationAdjusted()
              ? src.getMonthlyAmount().multiply(inflationFactor, MC)
              : src.getMonthlyAmount();
      LocalDate cursor = windowStart;
      while (!cursor.isAfter(windowEndInclusive)) {
        if (isIncomeActive(src, cursor)) {
          total = total.add(monthly, MC);
        }
        cursor = cursor.plusMonths(1);
      }
    }
    return total;
  }

  private static long monthsRemaining(LocalDate fromMonth, LocalDate deathDate) {
    return ChronoUnit.MONTHS.between(fromMonth, deathDate.withDayOfMonth(1)) + 1;
  }

  private static BigDecimal sum(List<BigDecimal> values) {
    return values.stream().reduce(BigDecimal.ZERO, (a, b) -> a.add(b, MC));
  }

  private static boolean isContributionType(AccountType type) {
    return type == AccountType.TRADITIONAL_401K
        || type == AccountType.TRADITIONAL_IRA
        || type == AccountType.ROTH_401K
        || type == AccountType.ROTH_IRA
        || type == AccountType.HSA;
  }

  private static boolean isTraditional(AccountType type) {
    return type == AccountType.TRADITIONAL_401K || type == AccountType.TRADITIONAL_IRA;
  }

  /** Index of the first {@code SAVINGS} account in the list, or -1 if none. */
  private static int findSavingsIndex(List<Account> accounts) {
    for (int i = 0; i < accounts.size(); i++) {
      if (accounts.get(i).getAccountType() == AccountType.SAVINGS) {
        return i;
      }
    }
    return -1;
  }
}
