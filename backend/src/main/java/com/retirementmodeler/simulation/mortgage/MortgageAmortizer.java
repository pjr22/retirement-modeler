package com.retirementmodeler.simulation.mortgage;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Standard fixed-rate mortgage amortization. Stateless — caller manages the running balance and
 * passes the current balance, APR, and contractual monthly P+I to {@link #step}.
 *
 * <p>Math: monthly rate {@code r = APR / 12}; monthly interest {@code interest = balance × r};
 * principal portion {@code principal = monthlyPI − interest}, clamped to remaining balance so the
 * final payment doesn't overshoot.
 *
 * <p>Negative-amortization protection: when {@code interest > monthlyPI} (the payment can't cover
 * even the interest), {@link #step} returns the payment as pure interest and leaves the balance
 * unchanged rather than letting it grow. The engine still records the payment as paid; the loan
 * just never amortizes. This matches a UI-side warning users see on the property form.
 */
public final class MortgageAmortizer {

  private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);
  private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

  private MortgageAmortizer() {}

  /** One month's amortization step. All BigDecimals are guaranteed non-null and non-negative. */
  public record MonthlyStep(
      BigDecimal interest, BigDecimal principal, BigDecimal newBalance, BigDecimal paymentMade) {}

  public static MonthlyStep step(BigDecimal balance, BigDecimal annualRate, BigDecimal monthlyPI) {
    if (balance == null || balance.signum() <= 0 || monthlyPI == null || monthlyPI.signum() <= 0) {
      return new MonthlyStep(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
    BigDecimal rate = annualRate != null ? annualRate : BigDecimal.ZERO;
    BigDecimal r = rate.divide(TWELVE, MC);
    BigDecimal interest = balance.multiply(r, MC).max(BigDecimal.ZERO);
    BigDecimal principalRaw = monthlyPI.subtract(interest, MC);
    if (principalRaw.signum() <= 0) {
      // Payment can't cover interest. Pay only what's due (interest), balance unchanged.
      return new MonthlyStep(interest, BigDecimal.ZERO, balance, interest);
    }
    BigDecimal principal = principalRaw.min(balance);
    BigDecimal newBalance = balance.subtract(principal, MC);
    BigDecimal payment = principal.add(interest, MC);
    return new MonthlyStep(interest, principal, newBalance, payment);
  }

  /**
   * Closed-form monthly P+I for a freshly-issued mortgage. Used by tests and the frontend; the
   * engine doesn't call it — it uses the stored {@code mortgageMonthlyPi} from the entity.
   */
  public static BigDecimal monthlyPayment(
      BigDecimal balance, BigDecimal annualRate, int termMonths) {
    if (balance == null || balance.signum() <= 0 || termMonths <= 0) {
      return BigDecimal.ZERO;
    }
    BigDecimal rate = annualRate != null ? annualRate : BigDecimal.ZERO;
    BigDecimal r = rate.divide(TWELVE, MC);
    if (r.signum() == 0) {
      return balance.divide(BigDecimal.valueOf(termMonths), MC);
    }
    BigDecimal one = BigDecimal.ONE;
    BigDecimal factor = one.add(r).pow(termMonths, MC);
    return balance.multiply(r, MC).multiply(factor, MC).divide(factor.subtract(one, MC), MC);
  }
}
