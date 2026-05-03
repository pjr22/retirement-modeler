package com.retirementmodeler.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "user_profiles")
public class UserProfile {

  @Id @GeneratedValue private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owner_id")
  @JsonIgnore
  private User owner;

  private String name;

  private LocalDate dateOfBirth;

  private LocalDate plannedRetirementDate;

  private int lifeExpectancy;

  @Enumerated(EnumType.STRING)
  private FilingStatus filingStatus;

  public UserProfile() {}

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public User getOwner() {
    return owner;
  }

  public void setOwner(User owner) {
    this.owner = owner;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public LocalDate getDateOfBirth() {
    return dateOfBirth;
  }

  public void setDateOfBirth(LocalDate dateOfBirth) {
    this.dateOfBirth = dateOfBirth;
  }

  public LocalDate getPlannedRetirementDate() {
    return plannedRetirementDate;
  }

  public void setPlannedRetirementDate(LocalDate plannedRetirementDate) {
    this.plannedRetirementDate = plannedRetirementDate;
  }

  public int getLifeExpectancy() {
    return lifeExpectancy;
  }

  public void setLifeExpectancy(int lifeExpectancy) {
    this.lifeExpectancy = lifeExpectancy;
  }

  public FilingStatus getFilingStatus() {
    return filingStatus;
  }

  public void setFilingStatus(FilingStatus filingStatus) {
    this.filingStatus = filingStatus;
  }
}
