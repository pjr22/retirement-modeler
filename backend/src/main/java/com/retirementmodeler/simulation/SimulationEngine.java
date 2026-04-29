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
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;
import org.springframework.stereotype.Component;

@Component
public class SimulationEngine {

  private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);

  public List<YearlyProjection> projectDeterministic(
      List<Account> accounts,
      List<IncomeSource> incomeSources,
      SimulationAssumptions assumptions,
      LocalDate dateOfBirth,
      int retirementAge,
      int lifeExpectancy) {
    return project(
        accounts, incomeSources, assumptions, dateOfBirth, retirementAge, lifeExpectancy, null);
  }

  public List<BigDecimal> projectSingleTrial(
      List<Account> accounts,
      List<IncomeSource> incomeSources,
      SimulationAssumptions assumptions,
      LocalDate dateOfBirth,
      int retirementAge,
      int lifeExpectancy,
      DoubleSupplier returnSampler) {
    List<YearlyProjection> projections =
        project(
            accounts,
            incomeSources,
            assumptions,
            dateOfBirth,
            retirementAge,
            lifeExpectancy,
            returnSampler);
    return projections.stream().map(YearlyProjection::totalBalance).toList();
  }

  private List<YearlyProjection> project(
      List<Account> accounts,
      List<IncomeSource> incomeSources,
      SimulationAssumptions assumptions,
      LocalDate dateOfBirth,
      int retirementAge,
      int lifeExpectancy,
      DoubleSupplier returnSampler) {

    int currentAge = Period.between(dateOfBirth, LocalDate.now()).getYears();
    int startYear = LocalDate.now().getYear();
    int projectionYears = lifeExpectancy - currentAge;

    List<BigDecimal> balances =
        accounts.stream()
            .map(a -> a.getBalance() != null ? a.getBalance() : BigDecimal.ZERO)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

    BigDecimal inflationFactor = BigDecimal.ONE;
    BigDecimal cumulativeContributions = BigDecimal.ZERO;
    BigDecimal cumulativeWithdrawals = BigDecimal.ZERO;
    BigDecimal cumulativeIncome = BigDecimal.ZERO;
    BigDecimal cumulativeTax = BigDecimal.ZERO;

    List<YearlyProjection> projections = new ArrayList<>();

    for (int year = 0; year <= projectionYears; year++) {
      int age = currentAge + year;
      if (age > lifeExpectancy) break;

      boolean isRetired = age >= retirementAge;
      BigDecimal yearContributions = BigDecimal.ZERO;
      BigDecimal yearWithdrawals = BigDecimal.ZERO;
      BigDecimal yearIncome = BigDecimal.ZERO;

      for (int i = 0; i < accounts.size(); i++) {
        Account account = accounts.get(i);
        BigDecimal balance = balances.get(i);

        BigDecimal rate =
            returnSampler != null
                ? BigDecimal.valueOf(returnSampler.getAsDouble())
                : assumptions.getExpectedRateOfReturn();
        balance = balance.multiply(BigDecimal.ONE.add(rate), MC);

        if (!isRetired
            && account.getAnnualContribution() != null
            && isContributionType(account.getAccountType())) {
          BigDecimal contrib = account.getAnnualContribution().multiply(inflationFactor, MC);
          balance = balance.add(contrib, MC);
          yearContributions = yearContributions.add(contrib, MC);
        }

        if (isRetired
            && account.getMonthlyBenefit() != null
            && account.getBenefitStartAge() != null
            && age >= account.getBenefitStartAge()) {
          BigDecimal annualBenefit =
              account
                  .getMonthlyBenefit()
                  .multiply(BigDecimal.valueOf(12), MC)
                  .multiply(inflationFactor, MC);
          balance = balance.add(annualBenefit, MC);
          yearIncome = yearIncome.add(annualBenefit, MC);
        }

        balances.set(i, balance);
      }

      if (!isRetired && incomeSources != null) {
        for (IncomeSource src : incomeSources) {
          if (src.getEndAge() == null || age < src.getEndAge()) {
            BigDecimal annualIncome = src.getAnnualAmount().multiply(inflationFactor, MC);
            yearIncome = yearIncome.add(annualIncome, MC);
          }
        }
      }

      if (isRetired) {
        BigDecimal totalBalance = balances.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal withdrawal = calculateWithdrawal(totalBalance, assumptions, inflationFactor);
        distributeWithdrawal(balances, withdrawal);
        yearWithdrawals = yearWithdrawals.add(withdrawal, MC);
      }

      BigDecimal taxableIncome = yearIncome.add(yearWithdrawals);
      BigDecimal yearTax = taxableIncome.multiply(assumptions.getFlatTaxRate(), MC);

      cumulativeContributions = cumulativeContributions.add(yearContributions, MC);
      cumulativeWithdrawals = cumulativeWithdrawals.add(yearWithdrawals, MC);
      cumulativeIncome = cumulativeIncome.add(yearIncome, MC);
      cumulativeTax = cumulativeTax.add(yearTax, MC);

      BigDecimal totalBalance = balances.stream().reduce(BigDecimal.ZERO, BigDecimal::add);

      inflationFactor =
          inflationFactor.multiply(BigDecimal.ONE.add(assumptions.getInflationRate()), MC);

      projections.add(
          new YearlyProjection(
              age,
              startYear + year,
              totalBalance,
              cumulativeContributions,
              cumulativeWithdrawals,
              cumulativeIncome,
              cumulativeTax,
              inflationFactor));
    }

    return projections;
  }

  private BigDecimal calculateWithdrawal(
      BigDecimal totalBalance, SimulationAssumptions assumptions, BigDecimal inflationFactor) {
    if (totalBalance.compareTo(BigDecimal.ZERO) <= 0) {
      return BigDecimal.ZERO;
    }
    return switch (assumptions.getWithdrawalStrategy()) {
      case FIXED_PERCENTAGE -> {
        BigDecimal pct = assumptions.getWithdrawalPercentage();
        yield totalBalance.multiply(pct != null ? pct : BigDecimal.valueOf(0.04), MC);
      }
      case FIXED_DOLLAR -> {
        BigDecimal amount = assumptions.getWithdrawalFixedAmount();
        yield (amount != null ? amount : BigDecimal.ZERO).multiply(inflationFactor, MC);
      }
    };
  }

  private void distributeWithdrawal(List<BigDecimal> balances, BigDecimal totalWithdrawal) {
    BigDecimal totalBalance = balances.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    if (totalBalance.compareTo(BigDecimal.ZERO) <= 0) return;

    BigDecimal remaining = totalWithdrawal;
    for (int i = 0; i < balances.size(); i++) {
      if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
      BigDecimal balance = balances.get(i);
      if (balance.compareTo(BigDecimal.ZERO) <= 0) continue;

      BigDecimal proportion = balance.divide(totalBalance, MC);
      BigDecimal withdrawal = totalWithdrawal.multiply(proportion, MC);
      withdrawal = withdrawal.min(balance);
      balances.set(i, balance.subtract(withdrawal, MC));
      remaining = remaining.subtract(withdrawal, MC);
    }
  }

  private boolean isContributionType(AccountType type) {
    return type == AccountType.TRADITIONAL_401K
        || type == AccountType.TRADITIONAL_IRA
        || type == AccountType.ROTH_401K
        || type == AccountType.ROTH_IRA
        || type == AccountType.HSA;
  }
}
