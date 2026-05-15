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
 *   <li>{@code yearRmd} (Phase 5) — total Required Minimum Distribution taken from Traditional
 *       accounts in the year. Zero pre-RMD-age. RMD-driven withdrawals are already included in
 *       {@code yearWithdrawals} and {@code yearOrdinaryIncome}; this field surfaces what portion
 *       was forced by the RMD rule rather than chosen by the withdrawal strategy.
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
    BigDecimal yearRmd,
    BigDecimal inflationFactor) {}
