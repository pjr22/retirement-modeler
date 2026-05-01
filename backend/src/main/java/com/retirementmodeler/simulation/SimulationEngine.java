package com.retirementmodeler.simulation;

import com.retirementmodeler.model.Account;
import com.retirementmodeler.model.AccountType;
import com.retirementmodeler.model.IncomeSource;
import com.retirementmodeler.model.IncomeType;
import com.retirementmodeler.model.SimulationAssumptions;
import com.retirementmodeler.model.YearlyProjection;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.Period;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
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
 * etc.). Pension and Social Security are no longer accounts; they are income streams that arrive
 * directly to the user. Social Security receives the SSA earnings-test treatment: pre-FRA earned
 * income causes a benefit reduction, and the cumulative withheld amount is recouped as a permanent
 * monthly bonus starting at FRA.
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
      LocalDate dateOfBirth,
      LocalDate plannedRetirementDate,
      int lifeExpectancy) {
    return project(
        accounts,
        incomeSources,
        assumptions,
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
      LocalDate dateOfBirth,
      LocalDate plannedRetirementDate,
      int lifeExpectancy,
      DoubleSupplier monthlyReturnSampler) {
    return project(
            accounts,
            incomeSources,
            assumptions,
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
      LocalDate dateOfBirth,
      LocalDate plannedRetirementDate,
      int lifeExpectancy,
      DoubleSupplier monthlyReturnSampler) {

    List<IncomeSource> sources = incomeSources != null ? incomeSources : List.of();

    LocalDate today = LocalDate.now().withDayOfMonth(1);
    LocalDate deathDate = dateOfBirth.plusYears(lifeExpectancy);
    // A retirement / benefit-start "trigger date" takes effect at the start of the next full
    // calendar month — except when the trigger is exactly the 1st of a month, in which case
    // that month is the first active month.
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

    // Per-account running balances.
    List<BigDecimal> balances = new ArrayList<>(accounts.size());
    for (Account a : accounts) {
      balances.add(scaled(a.getBalance() != null ? a.getBalance() : BigDecimal.ZERO));
    }

    BigDecimal inflationFactor = scaled(BigDecimal.ONE);
    BigDecimal yearContributions = scaled(BigDecimal.ZERO);
    BigDecimal yearWithdrawals = scaled(BigDecimal.ZERO);
    BigDecimal yearIncome = scaled(BigDecimal.ZERO);

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

      // 3. Income from all sources active this month. Aggregate SS separately so we can apply
      // the earnings-test withhold (pre-FRA) and the recoup bonus (post-FRA) once at the user
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

      // 4. Post-retirement withdrawals from savings. CASHFLOW_TARGET nets income; the
      // strategy may still request more than savings have available, so we cap at totalBalance.
      BigDecimal monthWithdrawal = BigDecimal.ZERO;
      if (isRetired) {
        BigDecimal totalBalanceNow = sum(balances);
        BigDecimal requested =
            computeMonthlyWithdrawal(totalBalanceNow, assumptions, inflationFactor, monthIncome);
        monthWithdrawal = requested.min(totalBalanceNow);
        if (monthWithdrawal.signum() > 0) {
          distributeWithdrawal(balances, monthWithdrawal);
        }
      }

      yearContributions = scaled(yearContributions.add(monthContrib, MC));
      yearWithdrawals = scaled(yearWithdrawals.add(monthWithdrawal, MC));
      yearIncome = scaled(yearIncome.add(monthIncome, MC));

      // 5. Emit a row at each retirement-anchor month.
      if (currentMonth.getMonth() == rowAnchorMonth) {
        BigDecimal totalBalance = sum(balances);
        BigDecimal flatTax =
            assumptions.getFlatTaxRate() != null ? assumptions.getFlatTaxRate() : BigDecimal.ZERO;
        BigDecimal yearTax = yearIncome.add(yearWithdrawals).multiply(flatTax, MC);

        rows.add(
            new YearlyProjection(
                ageThisMonth,
                currentMonth,
                output(totalBalance),
                output(yearContributions),
                output(yearWithdrawals),
                output(yearIncome),
                output(yearTax),
                scaled(inflationFactor)));

        // Reset year-deltas; advance inflation for the next year.
        yearContributions = scaled(BigDecimal.ZERO);
        yearWithdrawals = scaled(BigDecimal.ZERO);
        yearIncome = scaled(BigDecimal.ZERO);
        inflationFactor = scaled(inflationFactor.multiply(BigDecimal.ONE.add(inflationRate), MC));
      }

      currentMonth = currentMonth.plusMonths(1);
    }

    return rows;
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
   *
   * <p>Three regimes:
   *
   * <ul>
   *   <li>FRA month is at-or-before {@code yearStart}: no test, returns zero.
   *   <li>FRA month falls within this year ("year of FRA"): only earned income from months before
   *       FRA month counts, against the higher year-of-FRA threshold, at $1 withheld per $3 over.
   *   <li>FRA month is after this year ("under FRA all year"): full year's earned income against
   *       the under-FRA threshold, at $1 per $2 over.
   * </ul>
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
    LocalDate calendarYearEnd =
        LocalDate.of(yearStart.getYear(), 12, 1); // first of December (still 1st of month)
    LocalDate yearWindowEnd = !calendarYearEnd.isAfter(deathDate) ? calendarYearEnd : deathDate;

    boolean yearOfFra = !fraMonth.isAfter(yearWindowEnd) && !fraMonth.isBefore(yearStart);

    BigDecimal threshold;
    BigDecimal ratio;
    LocalDate earnedWindowEnd;
    if (yearOfFra) {
      threshold = YEAR_OF_FRA_EARNINGS_THRESHOLD.multiply(inflationFactor, MC);
      ratio = YEAR_OF_FRA_REDUCTION_RATIO;
      // Earnings before the FRA month count; FRA month and after don't.
      earnedWindowEnd = fraMonth.minusMonths(1);
      if (earnedWindowEnd.isBefore(yearStart)) {
        // FRA falls in or before yearStart's month — no earnings window
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

  /** Distribute a withdrawal across positive-balance accounts in proportion to their balance. */
  private void distributeWithdrawal(List<BigDecimal> balances, BigDecimal totalWithdrawal) {
    BigDecimal totalBalance = sum(balances);
    if (totalBalance.signum() <= 0) return;

    for (int i = 0; i < balances.size(); i++) {
      BigDecimal balance = balances.get(i);
      if (balance.signum() <= 0) continue;
      BigDecimal proportion = balance.divide(totalBalance, MC);
      BigDecimal share = totalWithdrawal.multiply(proportion, MC).min(balance);
      balances.set(i, scaled(balance.subtract(share, MC)));
    }
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
