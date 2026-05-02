package com.retirementmodeler.tax;

import java.math.BigDecimal;

/**
 * Result of a federal-tax computation for a single year. All monetary values are in dollars at
 * scale 2; rates are decimals at scale 4 (e.g. {@code 0.1234} for 12.34%).
 *
 * @param ordinaryTaxableIncome ordinary income after standard deduction (floored at 0)
 * @param ordinaryTax tax owed on ordinary income (progressive brackets)
 * @param capitalGainsTax tax owed on long-term capital gains (LTCG brackets, stacked on top of
 *     {@code ordinaryTaxableIncome})
 * @param totalTax sum of {@code ordinaryTax} and {@code capitalGainsTax}
 * @param effectiveRate {@code totalTax / (ordinaryIncome + longTermCapitalGains)}, or {@code 0}
 *     when there is no income
 * @param marginalRate the rate of the highest ordinary bracket reached by {@code
 *     ordinaryTaxableIncome}, i.e. the rate the next dollar of ordinary income would face
 */
public record TaxResult(
    BigDecimal ordinaryTaxableIncome,
    BigDecimal ordinaryTax,
    BigDecimal capitalGainsTax,
    BigDecimal totalTax,
    BigDecimal effectiveRate,
    BigDecimal marginalRate) {}
