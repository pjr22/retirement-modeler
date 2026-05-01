package com.retirementmodeler.model;

/**
 * How the simulator decides what to withdraw from savings each month post-retirement.
 *
 * <ul>
 *   <li>{@code PORTFOLIO_PERCENTAGE} — drains a fixed percentage of current savings per year (the
 *       classic "4% rule" mental model). Income from {@link IncomeSource} arrives separately and
 *       does NOT reduce the savings draw.
 *   <li>{@code CASHFLOW_TARGET} — the user's monthly budget. Income fills the budget first; savings
 *       only cover the remaining gap. If income meets or exceeds the budget, savings withdrawal is
 *       zero (surplus is unused, not banked).
 * </ul>
 */
public enum WithdrawalStrategy {
  PORTFOLIO_PERCENTAGE,
  CASHFLOW_TARGET
}
