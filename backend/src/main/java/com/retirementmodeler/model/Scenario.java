package com.retirementmodeler.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "scenarios")
public class Scenario {

  @Id @GeneratedValue private UUID id;

  @Column(name = "user_profile_id")
  private UUID userId;

  private String name;

  private String description;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "scenario_accounts", joinColumns = @JoinColumn(name = "scenario_id"))
  @Column(name = "account_id")
  private List<UUID> accountIds = new ArrayList<>();

  @Embedded private SimulationAssumptions assumptions;

  protected Scenario() {}

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public List<UUID> getAccountIds() {
    return accountIds;
  }

  public void setAccountIds(List<UUID> accountIds) {
    this.accountIds = accountIds;
  }

  public SimulationAssumptions getAssumptions() {
    return assumptions;
  }

  public void setAssumptions(SimulationAssumptions assumptions) {
    this.assumptions = assumptions;
  }
}
