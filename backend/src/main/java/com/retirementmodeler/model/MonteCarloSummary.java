package com.retirementmodeler.model;

import java.math.BigDecimal;
import java.util.List;

public record MonteCarloSummary(
    int trials,
    BigDecimal successRate,
    BigDecimal medianYearsOfSurvival,
    List<PercentilePoint> percentileBalances) {}
