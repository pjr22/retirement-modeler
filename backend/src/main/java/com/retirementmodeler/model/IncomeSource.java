package com.retirementmodeler.model;

import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.util.UUID;

@Embeddable
public class IncomeSource {

  private UUID id;

  private String name;

  private BigDecimal annualAmount;

  private Integer endAge;

  protected IncomeSource() {}

  public IncomeSource(UUID id, String name, BigDecimal annualAmount, Integer endAge) {
    this.id = id;
    this.name = name;
    this.annualAmount = annualAmount;
    this.endAge = endAge;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public BigDecimal getAnnualAmount() {
    return annualAmount;
  }

  public void setAnnualAmount(BigDecimal annualAmount) {
    this.annualAmount = annualAmount;
  }

  public Integer getEndAge() {
    return endAge;
  }

  public void setEndAge(Integer endAge) {
    this.endAge = endAge;
  }
}
