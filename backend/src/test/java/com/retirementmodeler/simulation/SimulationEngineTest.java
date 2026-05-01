package com.retirementmodeler.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import com.retirementmodeler.model.Account;
import com.retirementmodeler.model.AccountType;
import com.retirementmodeler.model.IncomeSource;
import com.retirementmodeler.model.IncomeType;
import com.retirementmodeler.model.SimulationAssumptions;
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
            dob,
            retire,
            lifeExpectancyAge);

    List<YearlyProjection> withoutEarned =
        engine.projectDeterministic(
            List.of(),
            List.of(ss),
            assumptions(WithdrawalStrategy.PORTFOLIO_PERCENTAGE, bd(0.04), null, bd(0)),
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
            dob,
            retire,
            lifeExpectancyAge);

    List<YearlyProjection> withoutEarned =
        engine.projectDeterministic(
            List.of(),
            List.of(ss),
            assumptions(WithdrawalStrategy.PORTFOLIO_PERCENTAGE, bd(0.04), null, bd(0)),
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
            dob,
            retire,
            lifeExpectancyAge);

    List<YearlyProjection> withoutIncome =
        engine.projectDeterministic(
            List.of(savings),
            List.of(),
            assumptions(WithdrawalStrategy.PORTFOLIO_PERCENTAGE, bd(0.04), null, bd(0)),
            dob,
            retire,
            lifeExpectancyAge);

    // Withdrawals depend only on savings balance and percentage, not income — should match.
    YearlyProjection firstWith = withIncome.get(1);
    YearlyProjection firstWithout = withoutIncome.get(1);
    assertThat(firstWith.yearWithdrawals())
        .isCloseTo(firstWithout.yearWithdrawals(), within(firstWithout.yearWithdrawals(), 1));
  }

  // ---- helpers ----

  private static SimulationAssumptions assumptions(
      WithdrawalStrategy strategy,
      BigDecimal pct,
      BigDecimal monthlyAmount,
      BigDecimal returnRate) {
    return new SimulationAssumptions(
        returnRate, bd(0), strategy, pct, monthlyAmount, bd(0), 1, bd(0));
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
