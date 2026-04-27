package com.retirementmodeler.model;

import java.util.List;
import java.util.UUID;

public record Scenario(
    UUID id,
    UUID userId,
    String name,
    String description,
    List<UUID> accountIds,
    SimulationAssumptions assumptions) {}
