package com.retirementmodeler.simulation;

import com.retirementmodeler.model.Account;
import com.retirementmodeler.model.AccountType;
import com.retirementmodeler.model.IncomeSource;
import com.retirementmodeler.model.SimulationAssumptions;
import com.retirementmodeler.model.YearlyProjection;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;
import org.springframework.stereotype.Component;

/**
 * Simulates retirement projections at monthly granularity. The result is still emitted as one row
 * per year, anchored to the retirement month — i.e., if a user retires in October, every emitted
 * {@link YearlyProjection} is dated October of some year. Each row's per-year fields ({@code
 * yearContributions}, {@code yearWithdrawals}, etc.) cover the 12 months ending at that row's date.
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

    LocalDate today = LocalDate.now().withDayOfMonth(1);
    LocalDate deathDate = dateOfBirth.plusYears(lifeExpectancy);
    // A retirement / benefit-start "trigger date" takes effect at the start of the next full
    // calendar month — except when the trigger is exactly the 1st of a month, in which case
    // that month is the first active month. So a planned retirement date of Oct 22 means
    // retirement starts Nov 1; pension at age 60 with a birthday on Oct 22 first pays in
    // November of the year you turn 60.
    LocalDate retirementStart = transitionStart(plannedRetirementDate);
    Month rowAnchorMonth = plannedRetirementDate.getMonth();

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

    List<YearlyProjection> rows = new ArrayList<>();
    LocalDate currentMonth = today;

    while (!currentMonth.isAfter(deathDate)) {
      // Age at *end* of the current month — so a user born on Oct 22 is reported
      // as 57 (not 56) in October of the year they turn 57. Used for benefit
      // start-age checks and the row's reported age.
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

      // 2. Per-account contributions (pre-retirement). Pension / SS benefits are *not*
      // added to account balances — they are paid as direct income to the user (tracked
      // in monthIncome below). Treating them as account deposits caused several model
      // distortions: pension accounts accumulating unspent benefit, proportional
      // withdrawals reporting pension passthrough as savings withdrawal, tax bases
      // double-counting the benefit.
      BigDecimal monthContrib = BigDecimal.ZERO;
      BigDecimal monthIncome = BigDecimal.ZERO;
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

        if (account.getMonthlyBenefit() != null && account.getBenefitStartAge() != null) {
          LocalDate benefitStart =
              transitionStart(dateOfBirth.plusYears(account.getBenefitStartAge()));
          if (!currentMonth.isBefore(benefitStart)) {
            BigDecimal monthly =
                account.isInflationAdjusted()
                    ? account.getMonthlyBenefit().multiply(inflationFactor, MC)
                    : account.getMonthlyBenefit();
            monthIncome = scaled(monthIncome.add(monthly, MC));
          }
        }
      }

      // 3. Income sources (salary, side income, etc.). Currently pre-retirement only
      // (the broader date-based refactor is queued separately).
      if (!isRetired && incomeSources != null) {
        for (IncomeSource src : incomeSources) {
          boolean stillActive =
              src.getEndAge() == null
                  || currentMonth.isBefore(transitionStart(dateOfBirth.plusYears(src.getEndAge())));
          if (stillActive) {
            BigDecimal baseMonthly = src.getAnnualAmount().divide(TWELVE, MC);
            BigDecimal monthly =
                src.isInflationAdjusted() ? baseMonthly.multiply(inflationFactor, MC) : baseMonthly;
            monthIncome = scaled(monthIncome.add(monthly, MC));
          }
        }
      }

      // 4. Post-retirement withdrawals from savings. For FIXED_DOLLAR, the configured
      // monthly amount is the user's *cashflow target* — savings only fill the gap
      // between target and incoming pension/SS/income that month. (If income exceeds
      // target, savings withdrawal is zero; surplus is unused, not banked.) Strategy
      // may still request more than savings have available, so we cap at totalBalance.
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
      case FIXED_PERCENTAGE -> {
        // Conventional 4%-rule semantics: withdraw a percentage of savings. Income
        // (pension / SS) is supplemental, not netted out.
        BigDecimal pct =
            assumptions.getWithdrawalPercentage() != null
                ? assumptions.getWithdrawalPercentage()
                : BigDecimal.valueOf(0.04);
        yield totalBalance.multiply(pct, MC).divide(TWELVE, MC);
      }
      case FIXED_DOLLAR -> {
        // Cashflow-target semantics: the configured monthly amount is what the user
        // wants to live on. Savings only cover the gap between target and incoming
        // income. If income meets or exceeds the target, savings withdrawal is zero.
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
