package com.retirementmodeler.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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

  // Field-level initialization so Jackson (no-arg constructor + setters) gets the default
  // even when the JSON omits the field. The setters null-coalesce to preserve the same
  // semantics when the JSON sends an explicit null.
  @Enumerated(EnumType.STRING)
  @Column(name = "withdrawal_ordering_strategy")
  private WithdrawalOrderingStrategy withdrawalOrderingStrategy =
      WithdrawalOrderingStrategy.PROPORTIONAL;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "scenario_custom_withdrawal_order",
      joinColumns = @JoinColumn(name = "scenario_id"))
  @OrderColumn(name = "position")
  @Column(name = "account_type")
  @Enumerated(EnumType.STRING)
  private List<AccountType> customWithdrawalOrder = new ArrayList<>();

  protected SimulationAssumptions() {}

  public SimulationAssumptions(
      BigDecimal expectedRateOfReturn,
      BigDecimal inflationRate,
      WithdrawalStrategy withdrawalStrategy,
      BigDecimal withdrawalPercentage,
      BigDecimal withdrawalMonthlyAmount,
      BigDecimal standardDeviation,
      Integer monteCarloTrials,
      WithdrawalOrderingStrategy withdrawalOrderingStrategy,
      List<AccountType> customWithdrawalOrder) {
    this.expectedRateOfReturn = expectedRateOfReturn;
    this.inflationRate = inflationRate;
    this.withdrawalStrategy = withdrawalStrategy;
    this.withdrawalPercentage = withdrawalPercentage;
    this.withdrawalMonthlyAmount = withdrawalMonthlyAmount;
    this.standardDeviation = standardDeviation;
    this.monteCarloTrials = monteCarloTrials != null ? monteCarloTrials : 1000;
    this.withdrawalOrderingStrategy =
        withdrawalOrderingStrategy != null
            ? withdrawalOrderingStrategy
            : WithdrawalOrderingStrategy.PROPORTIONAL;
    this.customWithdrawalOrder =
        customWithdrawalOrder != null ? new ArrayList<>(customWithdrawalOrder) : new ArrayList<>();
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

  public WithdrawalOrderingStrategy getWithdrawalOrderingStrategy() {
    return withdrawalOrderingStrategy;
  }

  public void setWithdrawalOrderingStrategy(WithdrawalOrderingStrategy withdrawalOrderingStrategy) {
    this.withdrawalOrderingStrategy =
        withdrawalOrderingStrategy != null
            ? withdrawalOrderingStrategy
            : WithdrawalOrderingStrategy.PROPORTIONAL;
  }

  public List<AccountType> getCustomWithdrawalOrder() {
    return customWithdrawalOrder;
  }

  public void setCustomWithdrawalOrder(List<AccountType> customWithdrawalOrder) {
    this.customWithdrawalOrder =
        customWithdrawalOrder != null ? new ArrayList<>(customWithdrawalOrder) : new ArrayList<>();
  }
}
