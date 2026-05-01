package com.retirementmodeler.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record YearlyProjection(
    int age,
    LocalDate date,
    BigDecimal balance,
    BigDecimal yearContributions,
    BigDecimal yearWithdrawals,
    BigDecimal yearIncome,
    BigDecimal yearTax,
    BigDecimal inflationFactor) {}
