package com.retirementmodeler.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "simulation_results")
public class SimulationResult {

  @Id @GeneratedValue private UUID id;

  private UUID scenarioId;

  @Column(name = "user_profile_id")
  private UUID userId;

  private Instant createdAt;

  @JdbcTypeCode(SqlTypes.JSON)
  private List<YearlyProjection> deterministicProjection;

  @JdbcTypeCode(SqlTypes.JSON)
  private MonteCarloSummary monteCarloSummary;

  protected SimulationResult() {}

  public SimulationResult(
      UUID id,
      UUID scenarioId,
      UUID userId,
      Instant createdAt,
      List<YearlyProjection> deterministicProjection,
      MonteCarloSummary monteCarloSummary) {
    this.id = id;
    this.scenarioId = scenarioId;
    this.userId = userId;
    this.createdAt = createdAt;
    this.deterministicProjection = deterministicProjection;
    this.monteCarloSummary = monteCarloSummary;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getScenarioId() {
    return scenarioId;
  }

  public void setScenarioId(UUID scenarioId) {
    this.scenarioId = scenarioId;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public List<YearlyProjection> getDeterministicProjection() {
    return deterministicProjection;
  }

  public void setDeterministicProjection(List<YearlyProjection> deterministicProjection) {
    this.deterministicProjection = deterministicProjection;
  }

  public MonteCarloSummary getMonteCarloSummary() {
    return monteCarloSummary;
  }

  public void setMonteCarloSummary(MonteCarloSummary monteCarloSummary) {
    this.monteCarloSummary = monteCarloSummary;
  }
}
