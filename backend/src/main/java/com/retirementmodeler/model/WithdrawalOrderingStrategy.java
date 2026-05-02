package com.retirementmodeler.model;

/**
 * The order in which the simulator drains savings accounts to meet a monthly withdrawal target.
 * Distinct from {@link WithdrawalStrategy} (which decides <em>how much</em> to withdraw) — this
 * decides <em>where the money comes from</em>.
 *
 * <ul>
 *   <li>{@code PROPORTIONAL} — split proportionally across all positive-balance accounts. Tax
 *       effect: a roughly stable mix of pre-tax/post-tax/Roth income each year, but no attempt to
 *       minimize lifetime tax.
 *   <li>{@code TAX_OPTIMIZED} — drain in fixed tiers: taxable (brokerage, savings) → tax-deferred
 *       (Traditional 401k/IRA) → tax-free (Roth, HSA). The conventional retirement-planner
 *       heuristic for minimizing lifetime tax: spends already-taxed money first, lets Roth keep
 *       compounding, defers ordinary-income tax events.
 *   <li>{@code CUSTOM} — user supplies an explicit list of {@link AccountType}s in draw order.
 *       Account types not listed get drained last as a safety net.
 * </ul>
 */
public enum WithdrawalOrderingStrategy {
  PROPORTIONAL,
  TAX_OPTIMIZED,
  CUSTOM
}
