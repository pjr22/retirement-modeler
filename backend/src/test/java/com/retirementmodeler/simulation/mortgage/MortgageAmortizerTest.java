package com.retirementmodeler.simulation.mortgage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.retirementmodeler.simulation.mortgage.MortgageAmortizer.MonthlyStep;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MortgageAmortizerTest {

  @Test
  void monthlyPayment_30yr_500k_at_6_percent_matches_published_value() {
    // Industry-standard reference: $500K @ 6% / 360 months ≈ $2,997.75.
    BigDecimal pi =
        MortgageAmortizer.monthlyPayment(new BigDecimal("500000"), new BigDecimal("0.06"), 360);
    assertThat(pi.doubleValue()).isCloseTo(2997.75, within(0.01));
  }

  @Test
  void monthlyPayment_15yr_300k_at_5_percent() {
    // $300K @ 5% / 180 months ≈ $2,372.38.
    BigDecimal pi =
        MortgageAmortizer.monthlyPayment(new BigDecimal("300000"), new BigDecimal("0.05"), 180);
    assertThat(pi.doubleValue()).isCloseTo(2372.38, within(0.01));
  }

  @Test
  void monthlyPayment_zero_balance_returns_zero() {
    assertThat(MortgageAmortizer.monthlyPayment(BigDecimal.ZERO, new BigDecimal("0.06"), 360))
        .isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void monthlyPayment_zero_rate_splits_evenly() {
    // $120K interest-free / 12 months = $10K/mo.
    BigDecimal pi = MortgageAmortizer.monthlyPayment(new BigDecimal("120000"), BigDecimal.ZERO, 12);
    assertThat(pi.doubleValue()).isCloseTo(10000.0, within(0.01));
  }

  @Test
  void step_first_month_of_30yr_500k_at_6_percent() {
    // First month: interest = 500_000 × 0.06/12 = $2,500; principal ≈ $497.75; new balance ≈
    // $499,502.25.
    MonthlyStep step =
        MortgageAmortizer.step(
            new BigDecimal("500000"), new BigDecimal("0.06"), new BigDecimal("2997.75"));
    assertThat(step.interest().doubleValue()).isCloseTo(2500.0, within(0.01));
    assertThat(step.principal().doubleValue()).isCloseTo(497.75, within(0.01));
    assertThat(step.newBalance().doubleValue()).isCloseTo(499502.25, within(0.01));
    assertThat(step.paymentMade().doubleValue()).isCloseTo(2997.75, within(0.01));
  }

  @Test
  void step_last_payment_caps_principal_at_balance() {
    // Balance below normal principal portion — last payment trues up. Balance $100, monthly
    // P+I $2,997.75 → principal = min(2997.75 - 100*0.005, 100) = 100; payment = 100 + 0.50.
    MonthlyStep step =
        MortgageAmortizer.step(
            new BigDecimal("100"), new BigDecimal("0.06"), new BigDecimal("2997.75"));
    assertThat(step.newBalance().doubleValue()).isCloseTo(0.0, within(0.01));
    assertThat(step.principal().doubleValue()).isCloseTo(100.0, within(0.01));
    assertThat(step.paymentMade().doubleValue()).isCloseTo(100.50, within(0.01));
  }

  @Test
  void step_negative_amortization_keeps_balance_unchanged() {
    // Balance $500K @ 6%: monthly interest $2,500. Payment $1,000 < interest.
    // No principal applied, balance unchanged.
    MonthlyStep step =
        MortgageAmortizer.step(
            new BigDecimal("500000"), new BigDecimal("0.06"), new BigDecimal("1000"));
    assertThat(step.principal()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(step.newBalance()).isEqualByComparingTo(new BigDecimal("500000"));
    assertThat(step.interest().doubleValue()).isCloseTo(2500.0, within(0.01));
  }

  @Test
  void step_zero_balance_is_no_op() {
    MonthlyStep step =
        MortgageAmortizer.step(BigDecimal.ZERO, new BigDecimal("0.06"), new BigDecimal("2997"));
    assertThat(step.interest()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(step.principal()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(step.newBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(step.paymentMade()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void full_schedule_360_months_pays_off_exactly() {
    // Run the full 30-year schedule, verify ending balance hits zero (within rounding).
    BigDecimal balance = new BigDecimal("500000");
    BigDecimal rate = new BigDecimal("0.06");
    BigDecimal pi = MortgageAmortizer.monthlyPayment(balance, rate, 360);
    for (int month = 0; month < 360; month++) {
      MonthlyStep step = MortgageAmortizer.step(balance, rate, pi);
      balance = step.newBalance();
    }
    assertThat(balance.doubleValue()).isCloseTo(0.0, within(0.50));
  }
}
