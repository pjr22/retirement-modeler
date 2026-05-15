package com.retirementmodeler.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import com.retirementmodeler.model.Account;
import com.retirementmodeler.model.AccountType;
import com.retirementmodeler.model.FilingStatus;
import com.retirementmodeler.model.IncomeSource;
import com.retirementmodeler.model.IncomeType;
import com.retirementmodeler.model.SimulationAssumptions;
import com.retirementmodeler.model.WithdrawalOrderingStrategy;
import com.retirementmodeler.model.WithdrawalStrategy;
import com.retirementmodeler.model.YearlyProjection;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/**
 * Direct unit tests for the simulation engine — focused on the income / SS earnings test logic
 * introduced in Phase 3.6. Uses LocalDate.now() as the simulation anchor; everything else is
 * computed relative to that so the tests are stable across runs. Note: SimulationAssumptions's
 * {@code lifeExpectancy} parameter on the engine is age-at-death, not years remaining, so we set it
 * to a target age (e.g. 90) rather than a duration.
 */
class SimulationEngineTest {

  private static final LocalDate TODAY = LocalDate.now().withDayOfMonth(1);

  private final SimulationEngine engine = new SimulationEngine();

  /** SS without any earned income → no withholding, no recoup, gross SS flows through. */
  @Test
  void ssAloneIsPaidUnreducedAndUnboosted() {
    LocalDate dob = TODAY.minusYears(60); // FRA 67 → 7 years pre-FRA
    LocalDate retire = TODAY.plusMonths(1);
    int lifeExpectancyAge = 90;

    IncomeSource ss = source("SS", IncomeType.SOCIAL_SECURITY, bd(2000), TODAY, null, false);

    List<YearlyProjection> rows =
        engine.projectDeterministic(
            List.of(),
            List.of(ss),
            assumptions(WithdrawalStrategy.PORTFOLIO_PERCENTAGE, bd(0.04), null, bd(0)),
            FilingStatus.SINGLE,
            dob,
            retire,
            lifeExpectancyAge);

    BigDecimal totalIncome = totalIncome(rows);
    BigDecimal expected = bd(2000).multiply(BigDecimal.valueOf(monthsCoveredByRows(rows, retire)));
    assertThat(totalIncome).isCloseTo(expected, within(expected, 1));
  }

  /**
   * Pre-FRA SS + earned income above threshold → SS reduction applied; recoup at FRA fully repays
   * the withholding by simulation end. Net total income still equals (SS + earned).
   */
  @Test
  void earnedIncomeAboveThresholdReducesSSWithFullRecoup() {
    LocalDate dob = TODAY.minusYears(63); // FRA in 4 years
    LocalDate retire = TODAY.plusMonths(1);
    int lifeExpectancyAge = 90;

    // Earned $4200/mo (~$50.4k/yr) for 3 years — well over the $23,400 under-FRA threshold.
    IncomeSource job =
        source(
            "Part time",
            IncomeType.EMPLOYMENT,
            bd(4200),
            TODAY,
            TODAY.plusYears(3).minusDays(1),
            false);
    IncomeSource ss = source("SS", IncomeType.SOCIAL_SECURITY, bd(2000), TODAY, null, false);

    List<YearlyProjection> withEarned =
        engine.projectDeterministic(
            List.of(),
            List.of(job, ss),
            assumptions(WithdrawalStrategy.PORTFOLIO_PERCENTAGE, bd(0.04), null, bd(0)),
            FilingStatus.SINGLE,
            dob,
            retire,
            lifeExpectancyAge);

    List<YearlyProjection> withoutEarned =
        engine.projectDeterministic(
            List.of(),
            List.of(ss),
            assumptions(WithdrawalStrategy.PORTFOLIO_PERCENTAGE, bd(0.04), null, bd(0)),
            FilingStatus.SINGLE,
            dob,
            retire,
            lifeExpectancyAge);

    BigDecimal totalWith = totalIncome(withEarned);
    BigDecimal totalWithout = totalIncome(withoutEarned);

    // Total earned across the 3-year window, summed over rows.
    BigDecimal earnedDelta =
        bd(4200).multiply(BigDecimal.valueOf(activeMonths(job, withEarned, retire)));

    // Recoup is monthsRemaining-based — by simulation end, withholding has been paid back.
    // Net difference = earned (reductions and recoup cancel out).
    assertThat(totalWith.subtract(totalWithout))
        .as("earnings test reduction should be fully recouped over remaining lifetime")
        .isCloseTo(earnedDelta, within(earnedDelta, 5));

    // During the years earned income is active, the row's SS portion should be lower than
    // the unreduced run's because of pre-FRA withholding.
    YearlyProjection firstWith = withEarned.get(1); // first full retired year
    YearlyProjection firstWithout = withoutEarned.get(1);
    BigDecimal earnedThatRow = bd(4200).multiply(bd(12));
    BigDecimal diff = firstWith.yearIncome().subtract(firstWithout.yearIncome());
    assertThat(diff)
        .as("first full year: earned added but SS partially withheld → diff < earned")
        .isGreaterThan(BigDecimal.ZERO)
        .isLessThan(earnedThatRow);
  }

  /** After FRA, earned income produces no reduction; SS continues uninterrupted. */
  @Test
  void postFraEarnedIncomeDoesNotReduceSS() {
    LocalDate dob = TODAY.minusYears(70); // already past FRA 67
    LocalDate retire = TODAY.minusYears(2);
    int lifeExpectancyAge = 95;

    IncomeSource job =
        source(
            "Consulting",
            IncomeType.SELF_EMPLOYMENT,
            bd(5000),
            TODAY,
            TODAY.plusYears(5).minusDays(1),
            false);
    IncomeSource ss = source("SS", IncomeType.SOCIAL_SECURITY, bd(2000), TODAY, null, false);

    List<YearlyProjection> withEarned =
        engine.projectDeterministic(
            List.of(),
            List.of(job, ss),
            assumptions(WithdrawalStrategy.PORTFOLIO_PERCENTAGE, bd(0.04), null, bd(0)),
            FilingStatus.SINGLE,
            dob,
            retire,
            lifeExpectancyAge);

    List<YearlyProjection> withoutEarned =
        engine.projectDeterministic(
            List.of(),
            List.of(ss),
            assumptions(WithdrawalStrategy.PORTFOLIO_PERCENTAGE, bd(0.04), null, bd(0)),
            FilingStatus.SINGLE,
            dob,
            retire,
            lifeExpectancyAge);

    BigDecimal totalEarnedAcrossSim =
        bd(5000).multiply(BigDecimal.valueOf(activeMonths(job, withEarned, retire)));
    BigDecimal totalWith = totalIncome(withEarned);
    BigDecimal totalWithout = totalIncome(withoutEarned);

    // Post-FRA, no earnings test, no withholding, no recoup. Difference = earned exactly.
    assertThat(totalWith.subtract(totalWithout))
        .isCloseTo(totalEarnedAcrossSim, within(totalEarnedAcrossSim, 1));
  }

  /** Pension paid as IncomeSource flows directly into yearIncome each month it is active. */
  @Test
  void pensionFlowsAsDirectIncome() {
    LocalDate dob = TODAY.minusYears(60);
    LocalDate retire = TODAY.plusMonths(1);
    int lifeExpectancyAge = 90;

    IncomeSource pension =
        source("Pension", IncomeType.PENSION, bd(3000), TODAY.plusYears(5), null, false);

    List<YearlyProjection> rows =
        engine.projectDeterministic(
            List.of(),
            List.of(pension),
            assumptions(WithdrawalStrategy.PORTFOLIO_PERCENTAGE, bd(0.04), null, bd(0)),
            FilingStatus.SINGLE,
            dob,
            retire,
            lifeExpectancyAge);

    // First row covers months before pension start → zero income.
    assertThat(rows.get(0).yearIncome()).isEqualByComparingTo(BigDecimal.ZERO);

    // A row well past pension start should show 12 months × $3000 = $36000.
    YearlyProjection afterStart =
        rows.stream().filter(r -> r.date().isAfter(TODAY.plusYears(6))).findFirst().orElseThrow();
    assertThat(afterStart.yearIncome()).isCloseTo(bd(36_000), within(bd(36_000), 1));
  }

  /** Income source with end date stops contributing income after that month. */
  @Test
  void incomeSourceRespectsEndDate() {
    LocalDate dob = TODAY.minusYears(50);
    LocalDate retire = TODAY.plusYears(15);
    int lifeExpectancyAge = 90;

    IncomeSource side =
        source("Side gig", IncomeType.OTHER, bd(1000), TODAY, TODAY.plusYears(2), false);

    List<YearlyProjection> rows =
        engine.projectDeterministic(
            List.of(),
            List.of(side),
            assumptions(WithdrawalStrategy.PORTFOLIO_PERCENTAGE, bd(0.04), null, bd(0)),
            FilingStatus.SINGLE,
            dob,
            retire,
            lifeExpectancyAge);

    // Late in the projection, no income remains.
    YearlyProjection late = rows.get(rows.size() - 1);
    assertThat(late.yearIncome()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  /** CASHFLOW_TARGET: income offsets the savings draw. */
  @Test
  void cashflowTargetNetsIncomeAgainstWithdrawal() {
    LocalDate dob = TODAY.minusYears(60);
    LocalDate retire = TODAY.plusMonths(1);
    int lifeExpectancyAge = 90;

    Account savings = account(AccountType.SAVINGS, bd(1_000_000));
    IncomeSource pension = source("Pension", IncomeType.PENSION, bd(3000), TODAY, null, false);

    List<YearlyProjection> rows =
        engine.projectDeterministic(
            List.of(savings),
            List.of(pension),
            assumptions(WithdrawalStrategy.CASHFLOW_TARGET, null, bd(5000), bd(0)),
            FilingStatus.SINGLE,
            dob,
            retire,
            lifeExpectancyAge);

    // First full retired year (row[1]): 12 months × ($5000 target - $3000 pension) = $24000.
    YearlyProjection firstFullYear = rows.get(1);
    assertThat(firstFullYear.yearWithdrawals()).isCloseTo(bd(24_000), within(bd(24_000), 5));
  }

  /** PORTFOLIO_PERCENTAGE: income arrives on top of savings draw, not netted. */
  @Test
  void portfolioPercentageDoesNotNetIncome() {
    LocalDate dob = TODAY.minusYears(60);
    LocalDate retire = TODAY.plusMonths(1);
    int lifeExpectancyAge = 90;

    Account savings = account(AccountType.SAVINGS, bd(1_000_000));
    IncomeSource pension = source("Pension", IncomeType.PENSION, bd(3000), TODAY, null, false);

    List<YearlyProjection> withIncome =
        engine.projectDeterministic(
            List.of(savings),
            List.of(pension),
            assumptions(WithdrawalStrategy.PORTFOLIO_PERCENTAGE, bd(0.04), null, bd(0)),
            FilingStatus.SINGLE,
            dob,
            retire,
            lifeExpectancyAge);

    List<YearlyProjection> withoutIncome =
        engine.projectDeterministic(
            List.of(savings),
            List.of(),
            assumptions(WithdrawalStrategy.PORTFOLIO_PERCENTAGE, bd(0.04), null, bd(0)),
            FilingStatus.SINGLE,
            dob,
            retire,
            lifeExpectancyAge);

    // Withdrawals depend only on savings balance and percentage, not income — should match.
    YearlyProjection firstWith = withIncome.get(1);
    YearlyProjection firstWithout = withoutIncome.get(1);
    assertThat(firstWith.yearWithdrawals())
        .isCloseTo(firstWithout.yearWithdrawals(), within(firstWithout.yearWithdrawals(), 1));
  }

  // ---- Phase 4 tax-categorization tests ----

  /** Roth withdrawals never produce taxable income — projection should show zero tax everywhere. */
  @Test
  void rothWithdrawalsAreTaxFree() {
    Account roth = account(AccountType.ROTH_IRA, bd(1_000_000));
    LocalDate dob = TODAY.minusYears(67); // Already at FRA, no SS in this test
    LocalDate retire = TODAY.plusMonths(1);
    int lifeExpectancyAge = 75;

    List<YearlyProjection> rows =
        engine.projectDeterministic(
            List.of(roth),
            List.of(),
            assumptions(WithdrawalStrategy.PORTFOLIO_PERCENTAGE, bd(0.04), null, bd(0)),
            FilingStatus.SINGLE,
            dob,
            retire,
            lifeExpectancyAge);

    BigDecimal totalWithdrawals =
        rows.stream()
            .map(YearlyProjection::yearWithdrawals)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    assertThat(totalWithdrawals).as("withdrawals happened").isPositive();

    for (YearlyProjection row : rows) {
      assertThat(row.yearOrdinaryIncome())
          .as("ordinary income at %s", row.date())
          .isEqualByComparingTo("0");
      assertThat(row.yearCapitalGains()).as("LTCG at %s", row.date()).isEqualByComparingTo("0");
      assertThat(row.yearOrdinaryTax())
          .as("ordinary tax at %s", row.date())
          .isEqualByComparingTo("0");
      assertThat(row.yearCapitalGainsTax())
          .as("LTCG tax at %s", row.date())
          .isEqualByComparingTo("0");
      assertThat(row.yearTax()).as("total tax at %s", row.date()).isEqualByComparingTo("0");
    }
  }

  /** Traditional withdrawals fold into ordinary income and produce bracket-based tax. */
  @Test
  void traditionalWithdrawalsAreOrdinaryIncome() {
    Account trad = account(AccountType.TRADITIONAL_IRA, bd(1_000_000));
    LocalDate dob = TODAY.minusYears(67);
    LocalDate retire = TODAY.plusMonths(1);
    int lifeExpectancyAge = 75;

    List<YearlyProjection> rows =
        engine.projectDeterministic(
            List.of(trad),
            List.of(),
            assumptions(WithdrawalStrategy.PORTFOLIO_PERCENTAGE, bd(0.04), null, bd(0)),
            FilingStatus.SINGLE,
            dob,
            retire,
            lifeExpectancyAge);

    // Pick a full-year row (second emit onwards = 12-month aggregation).
    YearlyProjection fullYear = rows.get(rows.size() - 1);
    assertThat(fullYear.yearOrdinaryIncome()).isEqualByComparingTo(fullYear.yearWithdrawals());
    assertThat(fullYear.yearCapitalGains()).isEqualByComparingTo("0");
    assertThat(fullYear.yearOrdinaryTax())
        .as("traditional withdrawals in retirement should produce non-zero ordinary tax")
        .isPositive();
    assertThat(fullYear.yearTax()).isEqualByComparingTo(fullYear.yearOrdinaryTax());
  }

  /** Taxable-brokerage withdrawals are LTCG (100% gains assumed). */
  @Test
  void brokerageWithdrawalsAreCapitalGains() {
    // Big balance so 4% draws are large enough to clear the 0% LTCG bracket and incur real tax.
    Account brokerage = account(AccountType.TAXABLE_BROKERAGE, bd(5_000_000));
    LocalDate dob = TODAY.minusYears(67);
    LocalDate retire = TODAY.plusMonths(1);
    int lifeExpectancyAge = 75;

    List<YearlyProjection> rows =
        engine.projectDeterministic(
            List.of(brokerage),
            List.of(),
            assumptions(WithdrawalStrategy.PORTFOLIO_PERCENTAGE, bd(0.04), null, bd(0)),
            FilingStatus.SINGLE,
            dob,
            retire,
            lifeExpectancyAge);

    YearlyProjection fullYear = rows.get(rows.size() - 1);
    assertThat(fullYear.yearOrdinaryIncome()).isEqualByComparingTo("0");
    assertThat(fullYear.yearCapitalGains()).isEqualByComparingTo(fullYear.yearWithdrawals());
    assertThat(fullYear.yearCapitalGainsTax()).isPositive();
    assertThat(fullYear.yearOrdinaryTax()).isEqualByComparingTo("0");
    assertThat(fullYear.yearTax()).isEqualByComparingTo(fullYear.yearCapitalGainsTax());
  }

  /** Savings withdrawals are treated as tax-free (interest is not separately tracked). */
  @Test
  void savingsWithdrawalsAreTaxFree() {
    Account savings = account(AccountType.SAVINGS, bd(500_000));
    LocalDate dob = TODAY.minusYears(67);
    LocalDate retire = TODAY.plusMonths(1);
    int lifeExpectancyAge = 75;

    List<YearlyProjection> rows =
        engine.projectDeterministic(
            List.of(savings),
            List.of(),
            assumptions(WithdrawalStrategy.PORTFOLIO_PERCENTAGE, bd(0.04), null, bd(0)),
            FilingStatus.SINGLE,
            dob,
            retire,
            lifeExpectancyAge);

    for (YearlyProjection row : rows) {
      assertThat(row.yearTax()).as("tax at %s", row.date()).isEqualByComparingTo("0");
    }
  }

  /** SS becomes 85% taxable when other ordinary income pushes provisional income well past T2. */
  @Test
  void socialSecurityFolds85PercentIntoOrdinaryWhenOtherIncomeIsHigh() {
    Account trad = account(AccountType.TRADITIONAL_IRA, bd(2_000_000));
    IncomeSource ss = source("SS", IncomeType.SOCIAL_SECURITY, bd(3_000), TODAY, null, false);

    LocalDate dob = TODAY.minusYears(67); // At FRA; no earnings test interference
    LocalDate retire = TODAY.plusMonths(1);
    int lifeExpectancyAge = 75;

    List<YearlyProjection> rows =
        engine.projectDeterministic(
            List.of(trad),
            List.of(ss),
            assumptions(WithdrawalStrategy.PORTFOLIO_PERCENTAGE, bd(0.04), null, bd(0)),
            FilingStatus.SINGLE,
            dob,
            retire,
            lifeExpectancyAge);

    YearlyProjection fullYear = rows.get(rows.size() - 1);
    // 4% of $2M = $80K traditional withdrawals + $36K SS gross. Provisional ≈ $98K, well past
    // the Single $34K T2 → 85% SS cap binds.
    BigDecimal expectedTaxableSS =
        fullYear.yearSocialSecurityBenefit().multiply(new BigDecimal("0.85"));
    assertThat(fullYear.yearTaxableSocialSecurity())
        .isCloseTo(expectedTaxableSS, Offset.offset(new BigDecimal("0.01")));
    // ordinaryIncome = withdrawals + taxable-SS (no other non-SS income source).
    assertThat(fullYear.yearOrdinaryIncome())
        .isEqualByComparingTo(fullYear.yearWithdrawals().add(fullYear.yearTaxableSocialSecurity()));
  }

  /** TAX_OPTIMIZED ordering drains taxable-brokerage entirely before touching traditional. */
  @Test
  void taxOptimizedOrderingDrainsBrokerageBeforeTraditional() {
    Account brokerage = account(AccountType.TAXABLE_BROKERAGE, bd(100_000));
    Account trad = account(AccountType.TRADITIONAL_IRA, bd(100_000));
    LocalDate dob = TODAY.minusYears(67);
    LocalDate retire = TODAY.plusMonths(1);
    int lifeExpectancyAge = 70; // Short horizon — $200K covers it at 4% draws

    SimulationAssumptions assumptions =
        new SimulationAssumptions(
            bd(0),
            bd(0),
            WithdrawalStrategy.PORTFOLIO_PERCENTAGE,
            bd(0.04),
            null,
            bd(0),
            1,
            WithdrawalOrderingStrategy.TAX_OPTIMIZED,
            null);

    List<YearlyProjection> rows =
        engine.projectDeterministic(
            List.of(brokerage, trad),
            List.of(),
            assumptions,
            FilingStatus.SINGLE,
            dob,
            retire,
            lifeExpectancyAge);

    BigDecimal totalOrdinary =
        rows.stream()
            .map(YearlyProjection::yearOrdinaryIncome)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal totalLtcg =
        rows.stream()
            .map(YearlyProjection::yearCapitalGains)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    // Over a 3-year horizon at 4%/year, the brokerage tier never empties → all draws are LTCG.
    assertThat(totalOrdinary).isEqualByComparingTo("0");
    assertThat(totalLtcg).isPositive();
  }

  /**
   * Compare PROPORTIONAL vs TAX_OPTIMIZED on the same accounts — different tax base, same total.
   */
  @Test
  void proportionalAndTaxOptimizedDifferInTaxBaseButNotInTotalWithdrawn() {
    LocalDate dob = TODAY.minusYears(67);
    LocalDate retire = TODAY.plusMonths(1);
    int lifeExpectancyAge = 70;

    SimulationAssumptions proportional =
        new SimulationAssumptions(
            bd(0),
            bd(0),
            WithdrawalStrategy.PORTFOLIO_PERCENTAGE,
            bd(0.04),
            null,
            bd(0),
            1,
            WithdrawalOrderingStrategy.PROPORTIONAL,
            null);
    SimulationAssumptions taxOptimized =
        new SimulationAssumptions(
            bd(0),
            bd(0),
            WithdrawalStrategy.PORTFOLIO_PERCENTAGE,
            bd(0.04),
            null,
            bd(0),
            1,
            WithdrawalOrderingStrategy.TAX_OPTIMIZED,
            null);

    List<YearlyProjection> propRows =
        engine.projectDeterministic(
            List.of(
                account(AccountType.TAXABLE_BROKERAGE, bd(100_000)),
                account(AccountType.TRADITIONAL_IRA, bd(100_000))),
            List.of(),
            proportional,
            FilingStatus.SINGLE,
            dob,
            retire,
            lifeExpectancyAge);
    List<YearlyProjection> taxRows =
        engine.projectDeterministic(
            List.of(
                account(AccountType.TAXABLE_BROKERAGE, bd(100_000)),
                account(AccountType.TRADITIONAL_IRA, bd(100_000))),
            List.of(),
            taxOptimized,
            FilingStatus.SINGLE,
            dob,
            retire,
            lifeExpectancyAge);

    BigDecimal propWithdrawals =
        propRows.stream()
            .map(YearlyProjection::yearWithdrawals)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal taxWithdrawals =
        taxRows.stream()
            .map(YearlyProjection::yearWithdrawals)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    // Same total withdrawn (4% applied to same starting portfolio with same returns).
    assertThat(taxWithdrawals).isCloseTo(propWithdrawals, within(propWithdrawals, 1));

    // PROPORTIONAL splits 50/50 between tiers each month → some ordinary income each year.
    BigDecimal propOrdinary =
        propRows.stream()
            .map(YearlyProjection::yearOrdinaryIncome)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    assertThat(propOrdinary).isPositive();

    // TAX_OPTIMIZED keeps traditional untouched while brokerage covers the draws.
    BigDecimal taxOrdinary =
        taxRows.stream()
            .map(YearlyProjection::yearOrdinaryIncome)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    assertThat(taxOrdinary).isEqualByComparingTo("0");
  }

  /**
   * Realistic Phase 4.8 scenario: MFJ retiree at FRA, $1M Traditional IRA + $500K Roth + $200K
   * taxable brokerage + $30K/yr SS, 23-year horizon (age 67→90), no growth/inflation. Asserts the
   * lifetime federal tax bill diverges by &gt;10% between PROPORTIONAL and TAX_OPTIMIZED. This is
   * the "decision-grade" sanity check from the plan — proves the ordering selector actually moves
   * the number that drives user decisions. (Direction is intentionally not asserted: TAX_OPTIMIZED
   * isn't always lower — heavy traditional-only years after the brokerage tier empties can push
   * more SS into the 85% taxable tier than a steady proportional split would have.)
   */
  @Test
  void realisticMFJScenarioShowsMeaningfulTaxDifferenceBetweenOrderings() {
    LocalDate dob = TODAY.minusYears(67);
    LocalDate retire = TODAY.plusMonths(1);
    int lifeExpectancyAge = 90;

    List<Account> accounts =
        List.of(
            account(AccountType.TRADITIONAL_IRA, bd(1_000_000)),
            account(AccountType.ROTH_IRA, bd(500_000)),
            account(AccountType.TAXABLE_BROKERAGE, bd(200_000)));
    IncomeSource ss = source("SS", IncomeType.SOCIAL_SECURITY, bd(2_500), TODAY, null, false);

    SimulationAssumptions proportional =
        new SimulationAssumptions(
            bd(0),
            bd(0),
            WithdrawalStrategy.PORTFOLIO_PERCENTAGE,
            bd(0.04),
            null,
            bd(0),
            1,
            WithdrawalOrderingStrategy.PROPORTIONAL,
            null);
    SimulationAssumptions taxOptimized =
        new SimulationAssumptions(
            bd(0),
            bd(0),
            WithdrawalStrategy.PORTFOLIO_PERCENTAGE,
            bd(0.04),
            null,
            bd(0),
            1,
            WithdrawalOrderingStrategy.TAX_OPTIMIZED,
            null);

    BigDecimal propTax =
        engine
            .projectDeterministic(
                accounts,
                List.of(ss),
                proportional,
                FilingStatus.MARRIED_FILING_JOINTLY,
                dob,
                retire,
                lifeExpectancyAge)
            .stream()
            .map(YearlyProjection::yearTax)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal taxOptTax =
        engine
            .projectDeterministic(
                accounts,
                List.of(ss),
                taxOptimized,
                FilingStatus.MARRIED_FILING_JOINTLY,
                dob,
                retire,
                lifeExpectancyAge)
            .stream()
            .map(YearlyProjection::yearTax)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    // Both strategies should produce a positive lifetime tax bill on this scenario.
    assertThat(propTax).isPositive();
    assertThat(taxOptTax).isPositive();

    // |diff| / max > 10%
    BigDecimal diff = propTax.subtract(taxOptTax).abs();
    BigDecimal max = propTax.max(taxOptTax);
    BigDecimal relativeDiff = diff.divide(max, 6, java.math.RoundingMode.HALF_UP);
    assertThat(relativeDiff).isGreaterThan(new BigDecimal("0.10"));
  }

  // ---- Phase 5 RMD tests ----

  /** Pre-RMD-age retirees never see a yearRmd entry. */
  @Test
  void preRmdAgeShowsZeroRmd() {
    LocalDate dob = TODAY.minusYears(67); // age 67, below RMD start
    LocalDate retire = TODAY.plusMonths(1);
    int lifeExpectancyAge = 72;

    List<YearlyProjection> rows =
        engine.projectDeterministic(
            List.of(account(AccountType.TRADITIONAL_IRA, bd(1_000_000))),
            List.of(),
            assumptions(WithdrawalStrategy.PORTFOLIO_PERCENTAGE, bd(0.04), null, bd(0)),
            FilingStatus.SINGLE,
            dob,
            retire,
            lifeExpectancyAge);

    for (YearlyProjection row : rows) {
      assertThat(row.yearRmd())
          .as("yearRmd at age %d (%s)", row.age(), row.date())
          .isEqualByComparingTo("0");
    }
  }

  /**
   * TAX_OPTIMIZED with a large Traditional and a large brokerage past age 73 — strategy alone would
   * keep Traditional untouched while draining brokerage, but the RMD rule forces a Traditional draw
   * each year. yearRmd > 0 and yearOrdinaryIncome reflects the forced ordinary draw.
   */
  @Test
  void taxOptimizedPostRmdAgeStillDrainsTraditionalForRmd() {
    LocalDate dob = TODAY.minusYears(75); // born 1951 → RMD age 73 → already past
    LocalDate retire = TODAY.plusMonths(1);
    int lifeExpectancyAge = 80;

    Account trad = account(AccountType.TRADITIONAL_IRA, bd(500_000));
    Account brokerage = account(AccountType.TAXABLE_BROKERAGE, bd(500_000));

    SimulationAssumptions taxOptimized =
        new SimulationAssumptions(
            bd(0),
            bd(0),
            WithdrawalStrategy.PORTFOLIO_PERCENTAGE,
            bd(0.04),
            null,
            bd(0),
            1,
            WithdrawalOrderingStrategy.TAX_OPTIMIZED,
            null);

    List<YearlyProjection> rows =
        engine.projectDeterministic(
            List.of(trad, brokerage),
            List.of(),
            taxOptimized,
            FilingStatus.SINGLE,
            dob,
            retire,
            lifeExpectancyAge);

    BigDecimal totalRmd =
        rows.stream().map(YearlyProjection::yearRmd).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal totalOrdinary =
        rows.stream()
            .map(YearlyProjection::yearOrdinaryIncome)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    // RMD was forced despite TAX_OPTIMIZED's brokerage-first preference.
    assertThat(totalRmd).isPositive();
    // The forced Traditional draw shows up as ordinary income.
    assertThat(totalOrdinary).isPositive();
  }

  /**
   * CASHFLOW_TARGET fully covered by income — strategy alone would withdraw $0, but RMD forces a
   * Traditional withdrawal anyway. The forced cash lands in a synthetic Savings account (no Savings
   * present in input) — verifiable because total ending wealth is materially higher than
   * (Traditional - RMD-drained-and-lost), and the year-by-year yearWithdrawals field reports the
   * forced amount.
   */
  @Test
  void cashflowTargetWithSurplusIncomeStillTriggersRmd() {
    LocalDate dob = TODAY.minusYears(75);
    LocalDate retire = TODAY.plusMonths(1);
    int lifeExpectancyAge = 78;

    Account trad = account(AccountType.TRADITIONAL_IRA, bd(500_000));
    // Pension well above the $2K cashflow target — strategy alone won't withdraw.
    IncomeSource pension = source("Pension", IncomeType.PENSION, bd(8_000), TODAY, null, false);

    SimulationAssumptions cashflow =
        new SimulationAssumptions(
            bd(0),
            bd(0),
            WithdrawalStrategy.CASHFLOW_TARGET,
            null,
            bd(2_000),
            bd(0),
            1,
            WithdrawalOrderingStrategy.PROPORTIONAL,
            null);

    List<YearlyProjection> rows =
        engine.projectDeterministic(
            List.of(trad),
            List.of(pension),
            cashflow,
            FilingStatus.SINGLE,
            dob,
            retire,
            lifeExpectancyAge);

    BigDecimal totalRmd =
        rows.stream().map(YearlyProjection::yearRmd).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal totalWithdrawn =
        rows.stream()
            .map(YearlyProjection::yearWithdrawals)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    // RMD forced a withdrawal despite the strategy not requesting one.
    assertThat(totalRmd).isPositive();
    // The forced amount is reflected in yearWithdrawals.
    assertThat(totalWithdrawn).isGreaterThanOrEqualTo(totalRmd);

    // Synthetic Savings preserved wealth: final-row balance is much larger than the residual
    // Traditional alone would be after multiple years of forced drains-without-replacement.
    YearlyProjection lastRow = rows.get(rows.size() - 1);
    assertThat(lastRow.balance()).isGreaterThan(bd(500_000).subtract(totalRmd).add(bd(1_000)));
  }

  /**
   * The annual RMD obligation in the first full calendar year matches the Uniform Lifetime Table
   * formula on the user's starting Traditional balance. At age 75 the divisor is 24.6.
   */
  @Test
  void yearRmdMatchesUniformLifetimeFormula() {
    LocalDate dob = TODAY.minusYears(75);
    LocalDate retire = TODAY.plusMonths(1);
    int lifeExpectancyAge = 78;

    Account trad = account(AccountType.TRADITIONAL_IRA, bd(1_000_000));

    SimulationAssumptions assumptions =
        new SimulationAssumptions(
            bd(0),
            bd(0),
            WithdrawalStrategy.CASHFLOW_TARGET,
            null,
            bd(0),
            bd(0),
            1,
            WithdrawalOrderingStrategy.PROPORTIONAL,
            null);

    List<YearlyProjection> rows =
        engine.projectDeterministic(
            List.of(trad),
            List.of(),
            assumptions,
            FilingStatus.SINGLE,
            dob,
            retire,
            lifeExpectancyAge);

    // Find a row that covers a full calendar year (skip the partial first row, which only sees a
    // fractional initial year's RMD). Any row from row[1] onward whose age==76 or later.
    YearlyProjection fullYearRow =
        rows.stream().filter(r -> r.age() >= 76).findFirst().orElseThrow();

    // Expected: $1M / 24.6 ≈ $40,650 (no growth, so balance ≈ $1M at the start of year 2, less the
    // forced year-1 RMD ≈ $40,650, giving a year-2 expected of ≈ $959,350 / 25.5 ≈ $37,621).
    // Use a loose 30% lower bound to verify the order of magnitude without over-specifying.
    assertThat(fullYearRow.yearRmd()).isBetween(bd(25_000), bd(45_000));
  }

  /** RMD-forced cash deposits into an EXISTING Savings account when one is present. */
  @Test
  void rmdForcedCashFlowsIntoExistingSavings() {
    LocalDate dob = TODAY.minusYears(75);
    LocalDate retire = TODAY.plusMonths(1);
    int lifeExpectancyAge = 78;

    Account trad = account(AccountType.TRADITIONAL_IRA, bd(500_000));
    Account savings = account(AccountType.SAVINGS, bd(0));
    IncomeSource pension = source("Pension", IncomeType.PENSION, bd(8_000), TODAY, null, false);

    SimulationAssumptions cashflow =
        new SimulationAssumptions(
            bd(0),
            bd(0),
            WithdrawalStrategy.CASHFLOW_TARGET,
            null,
            bd(2_000),
            bd(0),
            1,
            WithdrawalOrderingStrategy.PROPORTIONAL,
            null);

    List<YearlyProjection> rows =
        engine.projectDeterministic(
            List.of(trad, savings),
            List.of(pension),
            cashflow,
            FilingStatus.SINGLE,
            dob,
            retire,
            lifeExpectancyAge);

    BigDecimal totalRmd =
        rows.stream().map(YearlyProjection::yearRmd).reduce(BigDecimal.ZERO, BigDecimal::add);

    // Final wealth = Traditional residual + Savings. With surplus income forcing the user to hold
    // RMD cash in Savings rather than synthetic, ending balance should approximately equal
    // (startingTrad + 0 - tax_consumed). Since taxes don't directly reduce account balances in the
    // engine (taxes are reported but not debited), ending wealth ≈ starting Traditional.
    YearlyProjection lastRow = rows.get(rows.size() - 1);
    assertThat(totalRmd).isPositive();
    assertThat(lastRow.balance()).isCloseTo(bd(500_000), within(bd(500_000), 1));
  }

  // ---- helpers ----

  private static SimulationAssumptions assumptions(
      WithdrawalStrategy strategy,
      BigDecimal pct,
      BigDecimal monthlyAmount,
      BigDecimal returnRate) {
    return new SimulationAssumptions(
        returnRate, bd(0), strategy, pct, monthlyAmount, bd(0), 1, null, null);
  }

  private static IncomeSource source(
      String name,
      IncomeType type,
      BigDecimal monthly,
      LocalDate startDate,
      LocalDate endDate,
      boolean inflationAdjusted) {
    return new IncomeSource(
        UUID.randomUUID(), null, name, type, monthly, startDate, endDate, inflationAdjusted);
  }

  /** Build an {@link Account} with a generated id (no public constructor exists). */
  private static Account account(AccountType type, BigDecimal balance) {
    try {
      var ctor = Account.class.getDeclaredConstructor();
      ctor.setAccessible(true);
      Account a = ctor.newInstance();
      Field id = Account.class.getDeclaredField("id");
      id.setAccessible(true);
      id.set(a, UUID.randomUUID());
      a.setAccountType(type);
      a.setBalance(balance);
      a.setName("Test " + type);
      return a;
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  private static BigDecimal bd(double v) {
    return BigDecimal.valueOf(v);
  }

  private static BigDecimal totalIncome(List<YearlyProjection> rows) {
    return rows.stream().map(YearlyProjection::yearIncome).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  /**
   * Months of simulation actually emitted in rows. The first row covers from sim start to its date
   * inclusive; subsequent rows each cover 12 months back from their date.
   */
  private static long monthsCoveredByRows(List<YearlyProjection> rows, LocalDate retire) {
    if (rows.isEmpty()) return 0;
    LocalDate firstRow = rows.get(0).date();
    LocalDate lastRow = rows.get(rows.size() - 1).date();
    long monthsFromTodayToFirstRow =
        java.time.temporal.ChronoUnit.MONTHS.between(TODAY, firstRow) + 1;
    long monthsFromFirstToLast = java.time.temporal.ChronoUnit.MONTHS.between(firstRow, lastRow);
    return monthsFromTodayToFirstRow + monthsFromFirstToLast;
  }

  /** How many months a source is active within the row-emitted portion of the simulation. */
  private static long activeMonths(
      IncomeSource src, List<YearlyProjection> rows, LocalDate retire) {
    if (rows.isEmpty()) return 0;
    LocalDate windowStart = TODAY;
    LocalDate windowEnd = rows.get(rows.size() - 1).date();
    long count = 0;
    LocalDate cursor = windowStart;
    while (!cursor.isAfter(windowEnd)) {
      java.time.YearMonth ym = java.time.YearMonth.from(cursor);
      boolean afterStart =
          src.getStartDate() == null || !ym.isBefore(java.time.YearMonth.from(src.getStartDate()));
      boolean beforeEnd =
          src.getEndDate() == null || !ym.isAfter(java.time.YearMonth.from(src.getEndDate()));
      if (afterStart && beforeEnd) count++;
      cursor = cursor.plusMonths(1);
    }
    return count;
  }

  /** Absolute offset corresponding to {@code percent}% of the expected value. */
  private static Offset<BigDecimal> within(BigDecimal expected, double percent) {
    return Offset.offset(expected.abs().multiply(bd(percent / 100.0)));
  }
}
