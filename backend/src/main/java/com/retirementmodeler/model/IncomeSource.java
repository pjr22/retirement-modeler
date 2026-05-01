package com.retirementmodeler.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "income_sources")
public class IncomeSource {

  @Id @GeneratedValue private UUID id;

  @Column(name = "scenario_id")
  private UUID scenarioId;

  private String name;

  @Enumerated(EnumType.STRING)
  private IncomeType type;

  private BigDecimal monthlyAmount;

  /** Inclusive lower bound; {@code null} means "active from the start of the simulation." */
  private LocalDate startDate;

  /** Inclusive upper bound; {@code null} means "active until end of life." */
  private LocalDate endDate;

  /**
   * Whether the {@code monthlyAmount} grows with inflation each year. Most nominal income (salary,
   * rental, Social Security) does; private pensions usually don't.
   */
  private boolean inflationAdjusted = true;

  protected IncomeSource() {}

  public IncomeSource(
      UUID id,
      UUID scenarioId,
      String name,
      IncomeType type,
      BigDecimal monthlyAmount,
      LocalDate startDate,
      LocalDate endDate,
      boolean inflationAdjusted) {
    this.id = id;
    this.scenarioId = scenarioId;
    this.name = name;
    this.type = type;
    this.monthlyAmount = monthlyAmount;
    this.startDate = startDate;
    this.endDate = endDate;
    this.inflationAdjusted = inflationAdjusted;
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

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public IncomeType getType() {
    return type;
  }

  public void setType(IncomeType type) {
    this.type = type;
  }

  public BigDecimal getMonthlyAmount() {
    return monthlyAmount;
  }

  public void setMonthlyAmount(BigDecimal monthlyAmount) {
    this.monthlyAmount = monthlyAmount;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public void setStartDate(LocalDate startDate) {
    this.startDate = startDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public void setEndDate(LocalDate endDate) {
    this.endDate = endDate;
  }

  public boolean isInflationAdjusted() {
    return inflationAdjusted;
  }

  public void setInflationAdjusted(boolean inflationAdjusted) {
    this.inflationAdjusted = inflationAdjusted;
  }
}
