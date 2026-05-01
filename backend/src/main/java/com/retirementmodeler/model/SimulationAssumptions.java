package com.retirementmodeler.model;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.math.BigDecimal;

@Embeddable
public class SimulationAssumptions {

  private BigDecimal expectedRateOfReturn;

  private BigDecimal inflationRate;

  @Enumerated(EnumType.STRING)
  private WithdrawalStrategy withdrawalStrategy;

  private BigDecimal withdrawalPercentage;

  private BigDecimal withdrawalMonthlyAmount;

  private BigDecimal standardDeviation;

  private Integer monteCarloTrials;

  private BigDecimal flatTaxRate;

  protected SimulationAssumptions() {}

  public SimulationAssumptions(
      BigDecimal expectedRateOfReturn,
      BigDecimal inflationRate,
      WithdrawalStrategy withdrawalStrategy,
      BigDecimal withdrawalPercentage,
      BigDecimal withdrawalMonthlyAmount,
      BigDecimal standardDeviation,
      Integer monteCarloTrials,
      BigDecimal flatTaxRate) {
    this.expectedRateOfReturn = expectedRateOfReturn;
    this.inflationRate = inflationRate;
    this.withdrawalStrategy = withdrawalStrategy;
    this.withdrawalPercentage = withdrawalPercentage;
    this.withdrawalMonthlyAmount = withdrawalMonthlyAmount;
    this.standardDeviation = standardDeviation;
    this.monteCarloTrials = monteCarloTrials != null ? monteCarloTrials : 1000;
    this.flatTaxRate = flatTaxRate;
  }

  public BigDecimal getExpectedRateOfReturn() {
    return expectedRateOfReturn;
  }

  public void setExpectedRateOfReturn(BigDecimal expectedRateOfReturn) {
    this.expectedRateOfReturn = expectedRateOfReturn;
  }

  public BigDecimal getInflationRate() {
    return inflationRate;
  }

  public void setInflationRate(BigDecimal inflationRate) {
    this.inflationRate = inflationRate;
  }

  public WithdrawalStrategy getWithdrawalStrategy() {
    return withdrawalStrategy;
  }

  public void setWithdrawalStrategy(WithdrawalStrategy withdrawalStrategy) {
    this.withdrawalStrategy = withdrawalStrategy;
  }

  public BigDecimal getWithdrawalPercentage() {
    return withdrawalPercentage;
  }

  public void setWithdrawalPercentage(BigDecimal withdrawalPercentage) {
    this.withdrawalPercentage = withdrawalPercentage;
  }

  public BigDecimal getWithdrawalMonthlyAmount() {
    return withdrawalMonthlyAmount;
  }

  public void setWithdrawalMonthlyAmount(BigDecimal withdrawalMonthlyAmount) {
    this.withdrawalMonthlyAmount = withdrawalMonthlyAmount;
  }

  public BigDecimal getStandardDeviation() {
    return standardDeviation;
  }

  public void setStandardDeviation(BigDecimal standardDeviation) {
    this.standardDeviation = standardDeviation;
  }

  public Integer getMonteCarloTrials() {
    return monteCarloTrials;
  }

  public void setMonteCarloTrials(Integer monteCarloTrials) {
    this.monteCarloTrials = monteCarloTrials;
  }

  public BigDecimal getFlatTaxRate() {
    return flatTaxRate;
  }

  public void setFlatTaxRate(BigDecimal flatTaxRate) {
    this.flatTaxRate = flatTaxRate;
  }
}
