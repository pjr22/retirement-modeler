package com.retirementmodeler.model;

import java.math.BigDecimal;
import java.util.UUID;

public record IncomeSource(UUID id, String name, BigDecimal annualAmount, Integer endAge) {}
