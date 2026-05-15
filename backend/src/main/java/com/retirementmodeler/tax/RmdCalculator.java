package com.retirementmodeler.tax;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Computes Required Minimum Distributions (RMDs) from tax-deferred accounts using the IRS Uniform
 * Lifetime Table.
 *
 * <p>Under SECURE 2.0 (effective 2023+), RMDs apply starting at age 73 for users born in 1959 or
 * earlier, and age 75 for users born 1960 or later. The earlier 70½ / 72 thresholds aren't modeled
 * — anyone subject to them is well past the threshold today and the engine is forward-looking.
 *
 * <p>Annual RMD for a year = {@code priorYearEndTraditionalBalance /
 * divisorFor(ageAttainedThisYear)}. The engine aggregates Traditional 401(k) and Traditional IRA
 * balances when computing the obligation (the IRS allows aggregation across all IRAs, and we extend
 * the simplification to 401(k) accounts — documented in the engine).
 *
 * <p>The Uniform Lifetime Table values below are from IRS Pub. 590-B, post-2022 update (effective
 * January 1, 2022). The Joint Life Table is not used — it would require modeling a spouse's age,
 * which the data model doesn't capture.
 */
@Component
public class RmdCalculator {

  private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);

  private static final Map<Integer, BigDecimal> UNIFORM_LIFETIME_DIVISORS =
      Map.ofEntries(
          Map.entry(72, new BigDecimal("27.4")),
          Map.entry(73, new BigDecimal("26.5")),
          Map.entry(74, new BigDecimal("25.5")),
          Map.entry(75, new BigDecimal("24.6")),
          Map.entry(76, new BigDecimal("23.7")),
          Map.entry(77, new BigDecimal("22.9")),
          Map.entry(78, new BigDecimal("22.0")),
          Map.entry(79, new BigDecimal("21.1")),
          Map.entry(80, new BigDecimal("20.2")),
          Map.entry(81, new BigDecimal("19.4")),
          Map.entry(82, new BigDecimal("18.5")),
          Map.entry(83, new BigDecimal("17.7")),
          Map.entry(84, new BigDecimal("16.8")),
          Map.entry(85, new BigDecimal("16.0")),
          Map.entry(86, new BigDecimal("15.2")),
          Map.entry(87, new BigDecimal("14.4")),
          Map.entry(88, new BigDecimal("13.7")),
          Map.entry(89, new BigDecimal("12.9")),
          Map.entry(90, new BigDecimal("12.2")),
          Map.entry(91, new BigDecimal("11.5")),
          Map.entry(92, new BigDecimal("10.8")),
          Map.entry(93, new BigDecimal("10.1")),
          Map.entry(94, new BigDecimal("9.5")),
          Map.entry(95, new BigDecimal("8.9")),
          Map.entry(96, new BigDecimal("8.4")),
          Map.entry(97, new BigDecimal("7.8")),
          Map.entry(98, new BigDecimal("7.3")),
          Map.entry(99, new BigDecimal("6.8")),
          Map.entry(100, new BigDecimal("6.4")),
          Map.entry(101, new BigDecimal("6.0")),
          Map.entry(102, new BigDecimal("5.6")),
          Map.entry(103, new BigDecimal("5.2")),
          Map.entry(104, new BigDecimal("4.9")),
          Map.entry(105, new BigDecimal("4.6")),
          Map.entry(106, new BigDecimal("4.3")),
          Map.entry(107, new BigDecimal("4.1")),
          Map.entry(108, new BigDecimal("3.9")),
          Map.entry(109, new BigDecimal("3.7")),
          Map.entry(110, new BigDecimal("3.5")),
          Map.entry(111, new BigDecimal("3.4")),
          Map.entry(112, new BigDecimal("3.3")),
          Map.entry(113, new BigDecimal("3.1")),
          Map.entry(114, new BigDecimal("3.0")),
          Map.entry(115, new BigDecimal("2.9")),
          Map.entry(116, new BigDecimal("2.8")),
          Map.entry(117, new BigDecimal("2.7")),
          Map.entry(118, new BigDecimal("2.5")),
          Map.entry(119, new BigDecimal("2.3")));

  // Ages 120+ all use the floor divisor of 2.0.
  private static final BigDecimal FLOOR_DIVISOR = new BigDecimal("2.0");

  /** RMD age under SECURE 2.0: 73 for DOB year ≤ 1959, 75 for DOB year ≥ 1960. */
  public static int rmdStartAge(int birthYear) {
    return birthYear <= 1959 ? 73 : 75;
  }

  /**
   * Uniform Lifetime Table divisor for the given age. Ages below the table return the age-72
   * divisor (the engine should not call below age 73, but be defensive); ages above 119 return 2.0.
   */
  public BigDecimal divisorFor(int age) {
    if (age < 72) {
      return UNIFORM_LIFETIME_DIVISORS.get(72);
    }
    if (age >= 120) {
      return FLOOR_DIVISOR;
    }
    return UNIFORM_LIFETIME_DIVISORS.get(age);
  }

  /**
   * Returns the annual RMD for a user attaining {@code ageAttainedThisYear} during the calendar
   * year, given the aggregate prior-year-end balance of all Traditional (tax-deferred) accounts.
   * Returns zero below RMD age or for a zero / negative balance.
   */
  public BigDecimal computeAnnualRmd(
      int birthYear, int ageAttainedThisYear, BigDecimal priorYearEndTraditionalBalance) {
    if (priorYearEndTraditionalBalance == null || priorYearEndTraditionalBalance.signum() <= 0) {
      return BigDecimal.ZERO;
    }
    if (ageAttainedThisYear < rmdStartAge(birthYear)) {
      return BigDecimal.ZERO;
    }
    BigDecimal divisor = divisorFor(ageAttainedThisYear);
    return priorYearEndTraditionalBalance.divide(divisor, MC);
  }
}
