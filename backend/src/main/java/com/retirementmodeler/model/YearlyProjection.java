package com.retirementmodeler.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One year's projection row, anchored to the retirement-month of every year. Per-year delta fields
 * ({@code year*}) cover the 12 months ending at {@link #date}; {@link #balance} is the end-of-month
 * total across all accounts.
 *
 * <p>Tax breakdown fields (Phase 4):
 *
 * <ul>
 *   <li>{@code yearOrdinaryIncome} — the ordinary-tax base: non-SS income + traditional (pre-tax)
 *       withdrawals + the taxable portion of Social Security.
 *   <li>{@code yearCapitalGains} — gains realized from taxable-brokerage withdrawals (assumed 100%
 *       gains for MVP since cost basis isn't tracked).
 *   <li>{@code yearSocialSecurityBenefit} — gross SS paid in the year (after the SSA earnings-test
 *       withhold and any post-FRA recoup bonus, but pre-tax).
 *   <li>{@code yearTaxableSocialSecurity} — the portion of {@code yearSocialSecurityBenefit} that
 *       flows into {@code yearOrdinaryIncome} per the IRS provisional-income test.
 *   <li>{@code yearOrdinaryTax}, {@code yearCapitalGainsTax} — the two halves of {@code yearTax};
 *       their sum equals {@code yearTax}.
 * </ul>
 */
public record YearlyProjection(
    int age,
    LocalDate date,
    BigDecimal balance,
    BigDecimal yearContributions,
    BigDecimal yearWithdrawals,
    BigDecimal yearIncome,
    BigDecimal yearOrdinaryIncome,
    BigDecimal yearCapitalGains,
    BigDecimal yearSocialSecurityBenefit,
    BigDecimal yearTaxableSocialSecurity,
    BigDecimal yearOrdinaryTax,
    BigDecimal yearCapitalGainsTax,
    BigDecimal yearTax,
    BigDecimal inflationFactor) {}
