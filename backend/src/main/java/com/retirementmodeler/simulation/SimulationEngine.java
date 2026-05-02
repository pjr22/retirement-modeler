package com.retirementmodeler.simulation;

import com.retirementmodeler.model.Account;
import com.retirementmodeler.model.AccountType;
import com.retirementmodeler.model.FilingStatus;
import com.retirementmodeler.model.IncomeSource;
import com.retirementmodeler.model.IncomeType;
import com.retirementmodeler.model.SimulationAssumptions;
import com.retirementmodeler.model.WithdrawalOrderingStrategy;
import com.retirementmodeler.model.YearlyProjection;
import com.retirementmodeler.simulation.withdrawal.AccountSnapshot;
import com.retirementmodeler.simulation.withdrawal.CustomAllocator;
import com.retirementmodeler.simulation.withdrawal.ProportionalAllocator;
import com.retirementmodeler.simulation.withdrawal.TaxOptimizedAllocator;
import com.retirementmodeler.simulation.withdrawal.WithdrawalAllocator;
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
  private static final BigDecimal UNDER_FRA_REDUCTION_RATIO = BigDecimal.valueOf(0.5);
  private static final BigDecimal YEAR_OF_FRA_REDUCTION_RATIO =
      BigDecimal.ONE.divide(BigDecimal.valueOf(3), MC);

  private final TaxBracketProvider taxBracketProvider;
  private final TaxCalculator taxCalculator;
  private final SocialSecurityTaxer socialSecurityTaxer;

  public SimulationEngine(
      TaxBracketProvider taxBracketProvider,
      TaxCalculator taxCalculator,
      SocialSecurityTaxer socialSecurityTaxer) {
    this.taxBracketProvider = taxBracketProvider;
    this.taxCalculator = taxCalculator;
    this.socialSecurityTaxer = socialSecurityTaxer;
  }

  /** Convenience constructor for tests — wires default instances of the tax components. */
  public SimulationEngine() {
    this(new TaxBracketProvider(), new TaxCalculator(), new SocialSecurityTaxer());
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
    return project(
        accounts,
        incomeSources,
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
    return project(
            accounts,
            incomeSources,
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
      SimulationAssumptions assumptions,
      FilingStatus filingStatus,
      LocalDate dateOfBirth,
      LocalDate plannedRetirementDate,
      int lifeExpectancy,
      DoubleSupplier monthlyReturnSampler) {

    List<IncomeSource> sources = incomeSources != null ? incomeSources : List.of();
    FilingStatus status = filingStatus != null ? filingStatus : FilingStatus.SINGLE;

    LocalDate today = LocalDate.now().withDayOfMonth(1);
    LocalDate deathDate = dateOfBirth.plusYears(lifeExpectancy);
    LocalDate retirementStart = transitionStart(plannedRetirementDate);
    Month rowAnchorMonth = plannedRetirementDate.getMonth();

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
    // per-account result without scanning.
    List<BigDecimal> balances = new ArrayList<>(accounts.size());
    Map<UUID, Integer> idxByUuid = new HashMap<>();
    for (int i = 0; i < accounts.size(); i++) {
      Account a = accounts.get(i);
      balances.add(scaled(a.getBalance() != null ? a.getBalance() : BigDecimal.ZERO));
      if (a.getId() != null) {
        idxByUuid.put(a.getId(), i);
      }
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
      for (int i = 0; i < accounts.size(); i++) {
        balances.set(i, scaled(balances.get(i).multiply(growthFactor, MC)));
      }

      // 2. Pre-retirement contributions to retirement-savings accounts.
      BigDecimal monthContrib = BigDecimal.ZERO;
      for (int i = 0; i < accounts.size(); i++) {
        Account account = accounts.get(i);
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
      if (isRetired) {
        BigDecimal totalBalanceNow = sum(balances);
        BigDecimal requested =
            computeMonthlyWithdrawal(totalBalanceNow, assumptions, inflationFactor, monthIncome);
        if (requested.signum() > 0 && totalBalanceNow.signum() > 0) {
          List<AccountSnapshot> snapshots = new ArrayList<>(accounts.size());
          for (int i = 0; i < accounts.size(); i++) {
            snapshots.add(
                new AccountSnapshot(
                    accounts.get(i).getId(), accounts.get(i).getAccountType(), balances.get(i)));
          }
          Map<UUID, BigDecimal> allocation = allocator.allocate(snapshots, requested);
          for (Map.Entry<UUID, BigDecimal> entry : allocation.entrySet()) {
            Integer idx = idxByUuid.get(entry.getKey());
            if (idx == null) continue;
            BigDecimal share = entry.getValue();
            if (share.signum() <= 0) continue;
            balances.set(idx, scaled(balances.get(idx).subtract(share, MC)));
            monthWithdrawal = scaled(monthWithdrawal.add(share, MC));
            switch (accounts.get(idx).getAccountType()) {
              case TRADITIONAL_401K, TRADITIONAL_IRA ->
                  monthOrdinaryFromWithdrawals =
                      scaled(monthOrdinaryFromWithdrawals.add(share, MC));
              case TAXABLE_BROKERAGE ->
                  monthLtcgFromWithdrawals = scaled(monthLtcgFromWithdrawals.add(share, MC));
              case ROTH_401K, ROTH_IRA, HSA, SAVINGS -> {
                // Tax-free for projection purposes — see class javadoc.
              }
            }
          }
        }
      }

      yearContributions = scaled(yearContributions.add(monthContrib, MC));
      yearWithdrawals = scaled(yearWithdrawals.add(monthWithdrawal, MC));
      yearIncomeNonSS = scaled(yearIncomeNonSS.add(monthIncomeNonSS, MC));
      yearSocialSecurityGross = scaled(yearSocialSecurityGross.add(monthSSPaid, MC));
      yearOrdinaryFromWithdrawals =
          scaled(yearOrdinaryFromWithdrawals.add(monthOrdinaryFromWithdrawals, MC));
      yearCapitalGainsFromWithdrawals =
          scaled(yearCapitalGainsFromWithdrawals.add(monthLtcgFromWithdrawals, MC));

      // 5. Emit a row at each retirement-anchor month.
      if (currentMonth.getMonth() == rowAnchorMonth) {
        BigDecimal totalBalance = sum(balances);

        // Tax pipeline. Order matters: SS taxability depends on other ordinary income +
        // capital gains; the calculator then fold taxable-SS into ordinary income.
        BigDecimal ordinaryNonSS = yearIncomeNonSS.add(yearOrdinaryFromWithdrawals, MC);
        BigDecimal capitalGains = yearCapitalGainsFromWithdrawals;
        BigDecimal taxableSS =
            socialSecurityTaxer.computeTaxableAmount(
                status, yearSocialSecurityGross, ordinaryNonSS.add(capitalGains, MC));
        BigDecimal ordinaryIncome = ordinaryNonSS.add(taxableSS, MC);

        TaxBrackets brackets =
            taxBracketProvider.bracketsForYear(currentMonth.getYear(), inflationFactor);
        TaxResult tax = taxCalculator.compute(status, ordinaryIncome, capitalGains, brackets);

        BigDecimal yearIncome = yearIncomeNonSS.add(yearSocialSecurityGross, MC);

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
                scaled(inflationFactor)));

        // Reset year-deltas; advance inflation for the next year.
        yearContributions = scaled(BigDecimal.ZERO);
        yearWithdrawals = scaled(BigDecimal.ZERO);
        yearIncomeNonSS = scaled(BigDecimal.ZERO);
        yearSocialSecurityGross = scaled(BigDecimal.ZERO);
        yearOrdinaryFromWithdrawals = scaled(BigDecimal.ZERO);
        yearCapitalGainsFromWithdrawals = scaled(BigDecimal.ZERO);
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
}
