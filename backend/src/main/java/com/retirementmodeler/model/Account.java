package com.retirementmodeler.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {

  @Id @GeneratedValue private UUID id;

  @Column(name = "user_profile_id")
  private UUID userProfileId;

  private String name;

  @Enumerated(EnumType.STRING)
  private AccountType accountType;

  private BigDecimal balance;

  private BigDecimal annualContribution;

  private BigDecimal monthlyBenefit;

  private Integer benefitStartAge;

  protected Account() {}

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getUserProfileId() {
    return userProfileId;
  }

  public void setUserProfileId(UUID userProfileId) {
    this.userProfileId = userProfileId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public AccountType getAccountType() {
    return accountType;
  }

  public void setAccountType(AccountType accountType) {
    this.accountType = accountType;
  }

  public BigDecimal getBalance() {
    return balance;
  }

  public void setBalance(BigDecimal balance) {
    this.balance = balance;
  }

  public BigDecimal getAnnualContribution() {
    return annualContribution;
  }

  public void setAnnualContribution(BigDecimal annualContribution) {
    this.annualContribution = annualContribution;
  }

  public BigDecimal getMonthlyBenefit() {
    return monthlyBenefit;
  }

  public void setMonthlyBenefit(BigDecimal monthlyBenefit) {
    this.monthlyBenefit = monthlyBenefit;
  }

  public Integer getBenefitStartAge() {
    return benefitStartAge;
  }

  public void setBenefitStartAge(Integer benefitStartAge) {
    this.benefitStartAge = benefitStartAge;
  }
}
