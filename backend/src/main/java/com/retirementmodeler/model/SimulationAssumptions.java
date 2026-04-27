package com.retirementmodeler.model;

import java.math.BigDecimal;

public record SimulationAssumptions(
    BigDecimal expectedRateOfReturn,
    BigDecimal inflationRate,
    WithdrawalStrategy withdrawalStrategy,
    BigDecimal withdrawalPercentage,
    BigDecimal withdrawalFixedAmount,
    BigDecimal standardDeviation,
    Integer monteCarloTrials,
    BigDecimal flatTaxRate) {
  public SimulationAssumptions {
    if (monteCarloTrials == null) {
      monteCarloTrials = 1000;
    }
  }
}
