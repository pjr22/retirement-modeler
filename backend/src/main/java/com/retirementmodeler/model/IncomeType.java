package com.retirementmodeler.model;

/**
 * Classifies an {@link IncomeSource} for tax and Social-Security-earnings-test purposes.
 *
 * <ul>
 *   <li>{@code EMPLOYMENT} / {@code SELF_EMPLOYMENT} — earned income; counts toward the SSA
 *       earnings test that reduces SS benefits paid before Full Retirement Age.
 *   <li>{@code PENSION} — ordinary income; not earned (no SS earnings-test impact).
 *   <li>{@code SOCIAL_SECURITY} — special tax treatment (provisional-income test, Phase 4) and
 *       receives the earnings-test withholding & FRA recoup logic.
 *   <li>{@code RENTAL} — passive income; ordinary tax, no SS earnings-test impact.
 *   <li>{@code OTHER} — ordinary income; no special handling.
 * </ul>
 */
public enum IncomeType {
  EMPLOYMENT,
  SELF_EMPLOYMENT,
  PENSION,
  SOCIAL_SECURITY,
  RENTAL,
  OTHER;

  public boolean isEarned() {
    return this == EMPLOYMENT || this == SELF_EMPLOYMENT;
  }
}
