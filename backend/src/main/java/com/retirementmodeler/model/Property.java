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

/**
 * A real-property asset owned by a {@link UserProfile}. Mirrors the {@link Account} pattern: facts
 * that don't change between scenarios (value, mortgage, recurring expenses, type) live here.
 * Scenario-level decisions like "sell at age 70" or "start a reverse mortgage at 65" live on a
 * separate {@code PropertyDecision} entity.
 *
 * <p>Mortgage input shape is over-constrained by design (balance, rate, P+I, payoff). We store
 * balance + rate + P+I and derive the payoff date in the UI — P+I is on every mortgage statement,
 * but user-supplied payoff dates often diverge from the amortized truth (escrow, extra principal,
 * etc.).
 *
 * <p>Value, property tax, insurance, HOA, and maintenance all grow at the simulation's general
 * inflation rate. No per-property growth override in v1.
 */
@Entity
@Table(name = "properties")
public class Property {

  @Id @GeneratedValue private UUID id;

  @Column(name = "user_profile_id")
  private UUID userProfileId;

  private String name;

  @Enumerated(EnumType.STRING)
  private PropertyType type;

  private BigDecimal currentValue;

  /** Original purchase price plus capital improvements. Used for §121 / capital-gains on sale. */
  private BigDecimal costBasis;

  private BigDecimal mortgageBalance;

  /** Annual percentage rate as a decimal (e.g. 0.0625 for 6.25%). */
  private BigDecimal mortgageAnnualRate;

  /**
   * Principal + interest portion of the monthly mortgage payment, excluding tax/insurance escrow.
   * Derived in the UI from {@code mortgageBalance + mortgageAnnualRate + remaining-term}, where
   * remaining term = ({@code mortgageStartDate} + {@code mortgageTermYears}) − today.
   */
  private BigDecimal mortgageMonthlyPi;

  /** Date the current mortgage started (works for original purchase loan and later refis). */
  private LocalDate mortgageStartDate;

  /** Original term of the mortgage in years (typically 30). */
  private Integer mortgageTermYears;

  /** Optional date when the user plans to sell this property. {@code null} = no sale planned. */
  private LocalDate plannedSaleDate;

  /**
   * Monthly housing cost (rent, long-term care, etc.) that begins when this property is sold. Can
   * be 0 if the user expects to live with a relative or has another paid-off residence.
   * Inflation-adjusted in projections.
   */
  private BigDecimal postSaleMonthlyHousingCost;

  private BigDecimal annualPropertyTax;

  private BigDecimal annualInsurance;

  private BigDecimal monthlyHoa;

  /** Maintenance as a fraction of {@code currentValue} per year. Default 0.01 (1%). */
  private BigDecimal annualMaintenancePct;

  /**
   * Fraction of gross sale price lost to realtor + closing costs when the property is sold. Default
   * 0.06 (6% — typical full-service realtor commission + closing). Only used if {@code
   * plannedSaleDate} is set.
   */
  private BigDecimal sellingCostPct;

  public Property() {}

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

  public PropertyType getType() {
    return type;
  }

  public void setType(PropertyType type) {
    this.type = type;
  }

  public BigDecimal getCurrentValue() {
    return currentValue;
  }

  public void setCurrentValue(BigDecimal currentValue) {
    this.currentValue = currentValue;
  }

  public BigDecimal getCostBasis() {
    return costBasis;
  }

  public void setCostBasis(BigDecimal costBasis) {
    this.costBasis = costBasis;
  }

  public BigDecimal getMortgageBalance() {
    return mortgageBalance;
  }

  public void setMortgageBalance(BigDecimal mortgageBalance) {
    this.mortgageBalance = mortgageBalance;
  }

  public BigDecimal getMortgageAnnualRate() {
    return mortgageAnnualRate;
  }

  public void setMortgageAnnualRate(BigDecimal mortgageAnnualRate) {
    this.mortgageAnnualRate = mortgageAnnualRate;
  }

  public BigDecimal getMortgageMonthlyPi() {
    return mortgageMonthlyPi;
  }

  public void setMortgageMonthlyPi(BigDecimal mortgageMonthlyPi) {
    this.mortgageMonthlyPi = mortgageMonthlyPi;
  }

  public LocalDate getMortgageStartDate() {
    return mortgageStartDate;
  }

  public void setMortgageStartDate(LocalDate mortgageStartDate) {
    this.mortgageStartDate = mortgageStartDate;
  }

  public Integer getMortgageTermYears() {
    return mortgageTermYears;
  }

  public void setMortgageTermYears(Integer mortgageTermYears) {
    this.mortgageTermYears = mortgageTermYears;
  }

  public LocalDate getPlannedSaleDate() {
    return plannedSaleDate;
  }

  public void setPlannedSaleDate(LocalDate plannedSaleDate) {
    this.plannedSaleDate = plannedSaleDate;
  }

  public BigDecimal getPostSaleMonthlyHousingCost() {
    return postSaleMonthlyHousingCost;
  }

  public void setPostSaleMonthlyHousingCost(BigDecimal postSaleMonthlyHousingCost) {
    this.postSaleMonthlyHousingCost = postSaleMonthlyHousingCost;
  }

  public BigDecimal getAnnualPropertyTax() {
    return annualPropertyTax;
  }

  public void setAnnualPropertyTax(BigDecimal annualPropertyTax) {
    this.annualPropertyTax = annualPropertyTax;
  }

  public BigDecimal getAnnualInsurance() {
    return annualInsurance;
  }

  public void setAnnualInsurance(BigDecimal annualInsurance) {
    this.annualInsurance = annualInsurance;
  }

  public BigDecimal getMonthlyHoa() {
    return monthlyHoa;
  }

  public void setMonthlyHoa(BigDecimal monthlyHoa) {
    this.monthlyHoa = monthlyHoa;
  }

  public BigDecimal getAnnualMaintenancePct() {
    return annualMaintenancePct;
  }

  public void setAnnualMaintenancePct(BigDecimal annualMaintenancePct) {
    this.annualMaintenancePct = annualMaintenancePct;
  }

  public BigDecimal getSellingCostPct() {
    return sellingCostPct;
  }

  public void setSellingCostPct(BigDecimal sellingCostPct) {
    this.sellingCostPct = sellingCostPct;
  }
}
