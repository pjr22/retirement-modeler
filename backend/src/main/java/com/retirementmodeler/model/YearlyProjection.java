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
 *   <li>{@code yearCapitalGains} — gains realized from taxable-brokerage withdrawals AND home-sale
 *       gains above the §121 exclusion (Phase 5.2).
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
 *
 * <p>Property fields (Phase 5.2):
 *
 * <ul>
 *   <li>{@code yearMortgageInterest} — interest portion of mortgage P+I paid in the year (used in
 *       the itemized-deduction calculation).
 *   <li>{@code yearPropertyTaxPaid} — gross property tax paid (before SALT cap).
 *   <li>{@code yearHousingExpenses} — total housing outflow: mortgage P+I + property tax +
 *       insurance + HOA + maintenance. Post-sale, replaced by the user's replacement housing cost.
 *   <li>{@code yearSaleProceedsNet} — net cash from property sales in the year (gross × (1 −
 *       selling-cost-pct) − mortgage payoff). Deposited into Savings.
 *   <li>{@code yearSaleCapitalGains} — taxable gain from sales (after §121 exclusion for primary
 *       residence). Folded into {@code yearCapitalGains}.
 *   <li>{@code yearPropertyValueTotal} — sum of current values across not-yet-sold properties at
 *       row time (for net-worth display).
 *   <li>{@code yearDeduction} — actual deduction used by the tax calculator (max of standard and
 *       itemized — itemized = mortgage interest + SALT-capped property tax).
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
    BigDecimal yearMortgageInterest,
    BigDecimal yearPropertyTaxPaid,
    BigDecimal yearHousingExpenses,
    BigDecimal yearSaleProceedsNet,
    BigDecimal yearSaleCapitalGains,
    BigDecimal yearPropertyValueTotal,
    BigDecimal yearDeduction,
    BigDecimal inflationFactor) {}
