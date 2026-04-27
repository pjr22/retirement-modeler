package com.retirementmodeler.model;

import java.math.BigDecimal;
import java.util.UUID;

public record Account(
    UUID id,
    UUID userId,
    String name,
    AccountType accountType,
    BigDecimal balance,
    BigDecimal annualContribution,
    BigDecimal monthlyBenefit,
    Integer benefitStartAge) {}
