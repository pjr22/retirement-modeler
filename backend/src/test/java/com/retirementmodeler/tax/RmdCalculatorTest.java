package com.retirementmodeler.tax;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RmdCalculatorTest {

  private final RmdCalculator calculator = new RmdCalculator();

  // --- SECURE 2.0 RMD start age ---

  @Test
  void rmdStartAgeIs73ForBornBy1959() {
    assertThat(RmdCalculator.rmdStartAge(1953)).isEqualTo(73);
    assertThat(RmdCalculator.rmdStartAge(1959)).isEqualTo(73);
  }

  @Test
  void rmdStartAgeIs75ForBorn1960OrLater() {
    assertThat(RmdCalculator.rmdStartAge(1960)).isEqualTo(75);
    assertThat(RmdCalculator.rmdStartAge(1975)).isEqualTo(75);
  }

  // --- divisor table lookups ---

  @Test
  void divisorMatchesIrsUniformLifetimeTable() {
    // Spot-check published IRS values.
    assertThat(calculator.divisorFor(73)).isEqualByComparingTo("26.5");
    assertThat(calculator.divisorFor(75)).isEqualByComparingTo("24.6");
    assertThat(calculator.divisorFor(85)).isEqualByComparingTo("16.0");
    assertThat(calculator.divisorFor(95)).isEqualByComparingTo("8.9");
    assertThat(calculator.divisorFor(100)).isEqualByComparingTo("6.4");
  }

  @Test
  void divisorFloorAtAge120() {
    assertThat(calculator.divisorFor(119)).isEqualByComparingTo("2.3");
    assertThat(calculator.divisorFor(120)).isEqualByComparingTo("2.0");
    assertThat(calculator.divisorFor(150)).isEqualByComparingTo("2.0");
  }

  @Test
  void divisorBelow72FallsBackToAge72() {
    // Defensive — engine shouldn't query, but the value should be stable.
    assertThat(calculator.divisorFor(70)).isEqualByComparingTo("27.4");
  }

  // --- annual RMD computation ---

  @Test
  void rmdIsZeroBelowStartAge() {
    BigDecimal rmd = calculator.computeAnnualRmd(1955, 72, new BigDecimal("1000000"));
    assertThat(rmd).isEqualByComparingTo("0");
  }

  @Test
  void rmdIsZeroForZeroBalance() {
    BigDecimal rmd = calculator.computeAnnualRmd(1955, 75, BigDecimal.ZERO);
    assertThat(rmd).isEqualByComparingTo("0");
  }

  @Test
  void rmdIsZeroForNullBalance() {
    BigDecimal rmd = calculator.computeAnnualRmd(1955, 75, null);
    assertThat(rmd).isEqualByComparingTo("0");
  }

  @Test
  void rmdAt73OnMillionDollarBalanceIs37735() {
    // $1,000,000 / 26.5 = $37,735.85 (to the cent)
    BigDecimal rmd = calculator.computeAnnualRmd(1953, 73, new BigDecimal("1000000"));
    assertThat(rmd.doubleValue()).isCloseTo(37735.849, within(0.01));
  }

  @Test
  void rmdAt85OnHalfMillionIs31250() {
    // $500,000 / 16.0 = $31,250.00
    BigDecimal rmd = calculator.computeAnnualRmd(1940, 85, new BigDecimal("500000"));
    assertThat(rmd).isEqualByComparingTo("31250.00");
  }

  @Test
  void born1960NotSubjectToRmdUntil75() {
    // Born 1960 → RMD age 75. At 74, no RMD even on a large balance.
    assertThat(calculator.computeAnnualRmd(1960, 74, new BigDecimal("1000000")))
        .isEqualByComparingTo("0");
    assertThat(calculator.computeAnnualRmd(1960, 75, new BigDecimal("1000000")))
        .isNotEqualByComparingTo("0");
  }
}
