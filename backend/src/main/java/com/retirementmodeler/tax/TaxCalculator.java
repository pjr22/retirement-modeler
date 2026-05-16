package com.retirementmodeler.tax;

import com.retirementmodeler.model.FilingStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Computes federal income tax for a single year given separated ordinary and long-term
 * capital-gains income, the filing status, and a {@link TaxBrackets} snapshot for that year.
 *
 * <p>Model:
 *
 * <ol>
 *   <li>Total taxable income = {@code max(0, ordinaryIncome + longTermCapitalGains -
 *       standardDeduction)}.
 *   <li>Taxable ordinary income = {@code max(0, ordinaryIncome - standardDeduction)}.
 *   <li>Taxable LTCG = {@code totalTaxableIncome - taxableOrdinary}. When ordinary income alone is
 *       smaller than the standard deduction, the unused portion of the deduction "spills over" and
 *       reduces taxable LTCG — matching the IRS Qualified Dividends and Capital Gains Tax
 *       Worksheet.
 *   <li>Ordinary tax = progressive application of ordinary brackets to taxable ordinary income.
 *   <li>LTCG tax = LTCG brackets applied to taxable LTCG <em>stacked on top of</em> taxable
 *       ordinary income (so the LTCG rate depends on the taxpayer's other income).
 * </ol>
 *
 * <p>Out of scope (Phase 4): NIIT, AMT, state tax, Social Security taxation (handled separately by
 * {@code SocialSecurityTaxer} in 4.3 — its result is folded into {@code ordinaryIncome} by the
 * caller), itemized deductions, qualified-business-income deduction.
 */
@Component
public class TaxCalculator {

  public TaxResult compute(
      FilingStatus status,
      BigDecimal ordinaryIncome,
      BigDecimal longTermCapitalGains,
      TaxBrackets brackets) {
    return compute(status, ordinaryIncome, longTermCapitalGains, brackets, null);
  }

  /**
   * Same as {@link #compute(FilingStatus, BigDecimal, BigDecimal, TaxBrackets)} but lets the caller
   * supply an itemized deduction amount. The actual deduction used is {@code max(standardDeduction,
   * itemizedDeduction)} — i.e. the IRS rule of taking whichever is larger. Pass {@code null} for
   * the standard-deduction-only path.
   */
  public TaxResult compute(
      FilingStatus status,
      BigDecimal ordinaryIncome,
      BigDecimal longTermCapitalGains,
      TaxBrackets brackets,
      BigDecimal itemizedDeduction) {
    BigDecimal ordinary = nonNegative(ordinaryIncome);
    BigDecimal ltcg = nonNegative(longTermCapitalGains);
    BigDecimal standardDeduction = brackets.standardDeductionFor(status);
    BigDecimal deduction =
        itemizedDeduction != null && itemizedDeduction.compareTo(standardDeduction) > 0
            ? itemizedDeduction
            : standardDeduction;

    BigDecimal totalTaxable = ordinary.add(ltcg).subtract(deduction).max(BigDecimal.ZERO);
    BigDecimal taxableOrdinary = ordinary.subtract(deduction).max(BigDecimal.ZERO);
    BigDecimal taxableLtcg = totalTaxable.subtract(taxableOrdinary).max(BigDecimal.ZERO);

    List<BracketTier> ordinaryTiers = brackets.ordinaryBracketsFor(status);
    List<BracketTier> ltcgTiers = brackets.longTermCapitalGainsBracketsFor(status);

    BigDecimal ordinaryTax = progressiveTax(taxableOrdinary, ordinaryTiers);
    BigDecimal capitalGainsTax = stackedLtcgTax(taxableOrdinary, taxableLtcg, ltcgTiers);
    BigDecimal totalTax = ordinaryTax.add(capitalGainsTax);

    BigDecimal grossIncome = ordinary.add(ltcg);
    BigDecimal effectiveRate =
        grossIncome.signum() == 0
            ? BigDecimal.ZERO.setScale(4)
            : totalTax.divide(grossIncome, 4, RoundingMode.HALF_UP);

    BigDecimal marginalRate = marginalOrdinaryRate(taxableOrdinary, ordinaryTiers);

    return new TaxResult(
        taxableOrdinary.setScale(2, RoundingMode.HALF_UP),
        ordinaryTax.setScale(2, RoundingMode.HALF_UP),
        capitalGainsTax.setScale(2, RoundingMode.HALF_UP),
        totalTax.setScale(2, RoundingMode.HALF_UP),
        effectiveRate,
        marginalRate);
  }

  /** Progressive tax: sums {@code rate × portion-in-tier} across all tiers up to {@code income}. */
  private static BigDecimal progressiveTax(BigDecimal income, List<BracketTier> tiers) {
    if (income.signum() <= 0) {
      return BigDecimal.ZERO;
    }
    BigDecimal tax = BigDecimal.ZERO;
    for (int i = 0; i < tiers.size(); i++) {
      BigDecimal lower = tiers.get(i).threshold();
      if (income.compareTo(lower) <= 0) {
        break;
      }
      BigDecimal upper = upperBound(tiers, i, income);
      BigDecimal portion = income.min(upper).subtract(lower);
      tax = tax.add(portion.multiply(tiers.get(i).rate()));
    }
    return tax;
  }

  /**
   * LTCG tax with stacking: the LTCG block sits between {@code stackBottom} (taxable ordinary) and
   * {@code stackBottom + ltcgAmount} on the LTCG bracket axis. We integrate {@code rate × overlap}
   * across each LTCG tier.
   */
  private static BigDecimal stackedLtcgTax(
      BigDecimal stackBottom, BigDecimal ltcgAmount, List<BracketTier> tiers) {
    if (ltcgAmount.signum() <= 0) {
      return BigDecimal.ZERO;
    }
    BigDecimal stackTop = stackBottom.add(ltcgAmount);
    BigDecimal tax = BigDecimal.ZERO;
    for (int i = 0; i < tiers.size(); i++) {
      BigDecimal lower = tiers.get(i).threshold();
      BigDecimal upper = upperBound(tiers, i, stackTop);
      if (stackTop.compareTo(lower) <= 0) {
        break;
      }
      if (stackBottom.compareTo(upper) >= 0) {
        continue;
      }
      BigDecimal overlap = stackTop.min(upper).subtract(stackBottom.max(lower));
      tax = tax.add(overlap.multiply(tiers.get(i).rate()));
    }
    return tax;
  }

  /** The rate of the highest ordinary tier whose threshold is &lt;= taxable ordinary income. */
  private static BigDecimal marginalOrdinaryRate(
      BigDecimal taxableOrdinary, List<BracketTier> tiers) {
    BigDecimal rate = tiers.get(0).rate();
    for (BracketTier tier : tiers) {
      if (taxableOrdinary.compareTo(tier.threshold()) >= 0) {
        rate = tier.rate();
      } else {
        break;
      }
    }
    return rate.setScale(4, RoundingMode.HALF_UP);
  }

  /**
   * For any tier index {@code i}, returns the upper bound of that tier — the next tier's threshold
   * if one exists, otherwise an effective infinity (we use {@code fallback}, which the caller sets
   * to a value at or above the income being computed, so {@code income.min(upper)} reduces to
   * {@code income}).
   */
  private static BigDecimal upperBound(List<BracketTier> tiers, int i, BigDecimal fallback) {
    return i + 1 < tiers.size() ? tiers.get(i + 1).threshold() : fallback;
  }

  private static BigDecimal nonNegative(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value.max(BigDecimal.ZERO);
  }
}
