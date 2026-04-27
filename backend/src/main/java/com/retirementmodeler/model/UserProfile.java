package com.retirementmodeler.model;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record UserProfile(
    UUID id,
    String name,
    LocalDate dateOfBirth,
    int plannedRetirementAge,
    int lifeExpectancy,
    FilingStatus filingStatus,
    List<IncomeSource> incomeSources) {}
