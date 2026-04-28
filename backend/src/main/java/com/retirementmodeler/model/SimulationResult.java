package com.retirementmodeler.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SimulationResult(
    UUID id,
    UUID scenarioId,
    UUID userId,
    Instant createdAt,
    List<YearlyProjection> deterministicProjection,
    MonteCarloSummary monteCarloSummary) {}
