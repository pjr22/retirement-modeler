package com.retirementmodeler.model;

import java.math.BigDecimal;

public record PercentilePoint(
    int age, BigDecimal p10, BigDecimal p25, BigDecimal p50, BigDecimal p75, BigDecimal p90) {}
