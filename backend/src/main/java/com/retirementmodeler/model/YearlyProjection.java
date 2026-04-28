package com.retirementmodeler.model;

import java.math.BigDecimal;

public record YearlyProjection(
    int age,
    int year,
    BigDecimal totalBalance,
    BigDecimal totalContributions,
    BigDecimal totalWithdrawals,
    BigDecimal totalIncome,
    BigDecimal totalTax,
    BigDecimal inflationFactor) {}
