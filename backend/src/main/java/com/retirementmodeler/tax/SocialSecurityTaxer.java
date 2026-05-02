package com.retirementmodeler.tax;

import com.retirementmodeler.model.FilingStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * Computes the taxable portion of Social Security benefits using the IRS provisional-income test
 * from Pub. 915 Worksheet 1. The thresholds are set in statute (1983/1993) and are <em>not</em>
 * inflation-adjusted; they remain at their original nominal values forever, which is why an
 * increasing fraction of recipients hit the 85% tier each year.
 *
 * <h2>Algorithm</h2>
 *
 * <pre>
 * provisionalIncome = otherIncome + 0.5 × ssBenefits
 *
 * if provisionalIncome ≤ T1:           taxableSS = 0
 * else if provisionalIncome ≤ T2:      taxableSS = min(0.5 × ssBenefits, 0.5 × (PI − T1))
 * else:                                taxableSS = min(0.85 × ssBenefits,
 *                                                     0.85 × (PI − T2) + tier1Carryover)
 *   where tier1Carryover = min(0.5 × ssBenefits, 0.5 × (T2 − T1))
 * </pre>
 *
 * <h2>Filing-status thresholds</h2>
 *
 * <ul>
 *   <li>Single / Head of Household: T1 = $25,000, T2 = $34,000
 *   <li>Married Filing Jointly: T1 = $32,000, T2 = $44,000
 *   <li>Married Filing Separately: defaults to the "lived apart all year" case (T1 = $25,000, T2 =
 *       $34,000). The "lived together at any time" case (T1 = T2 = $0, all SS taxable up to 85%) is
 *       not modeled — add it via an explicit flag on the profile if needed.
 * </ul>
 *
 * <p>{@code otherIncome} is the caller's responsibility to assemble: AGI excluding SS, plus
 * tax-exempt interest, plus any other items that count toward provisional income. Both ordinary
 * income and the otherwise-taxed taxable portion of LTCG go into this number with full weight.
 */
@Component
public class SocialSecurityTaxer {

  private static final BigDecimal HALF = new BigDecimal("0.5");
  private static final BigDecimal EIGHTY_FIVE_PERCENT = new BigDecimal("0.85");

  private static final BigDecimal T1_SINGLE = new BigDecimal("25000");
  private static final BigDecimal T2_SINGLE = new BigDecimal("34000");
  private static final BigDecimal T1_MFJ = new BigDecimal("32000");
  private static final BigDecimal T2_MFJ = new BigDecimal("44000");

  /**
   * @return the dollar amount of {@code ssBenefits} that is includable in the taxpayer's ordinary
   *     income for the year. Always between {@code 0} and {@code 0.85 × ssBenefits}.
   */
  public BigDecimal computeTaxableAmount(
      FilingStatus status, BigDecimal ssBenefits, BigDecimal otherIncome) {
    BigDecimal ss = nonNegative(ssBenefits);
    if (ss.signum() == 0) {
      return scale(BigDecimal.ZERO);
    }
    BigDecimal other = nonNegative(otherIncome);
    BigDecimal[] thresholds = thresholdsFor(status);
    BigDecimal t1 = thresholds[0];
    BigDecimal t2 = thresholds[1];

    BigDecimal provisionalIncome = other.add(ss.multiply(HALF));

    if (provisionalIncome.compareTo(t1) <= 0) {
      return scale(BigDecimal.ZERO);
    }

    BigDecimal halfOfSs = ss.multiply(HALF);

    if (provisionalIncome.compareTo(t2) <= 0) {
      BigDecimal halfOver = provisionalIncome.subtract(t1).multiply(HALF);
      return scale(halfOfSs.min(halfOver));
    }

    BigDecimal tier1Carryover = halfOfSs.min(t2.subtract(t1).multiply(HALF));
    BigDecimal eightyFiveOver = provisionalIncome.subtract(t2).multiply(EIGHTY_FIVE_PERCENT);
    BigDecimal eightyFiveCap = ss.multiply(EIGHTY_FIVE_PERCENT);
    return scale(eightyFiveCap.min(eightyFiveOver.add(tier1Carryover)));
  }

  private static BigDecimal[] thresholdsFor(FilingStatus status) {
    return switch (status) {
      case MARRIED_FILING_JOINTLY -> new BigDecimal[] {T1_MFJ, T2_MFJ};
      case SINGLE, HEAD_OF_HOUSEHOLD, MARRIED_FILING_SEPARATELY ->
          new BigDecimal[] {T1_SINGLE, T2_SINGLE};
    };
  }

  private static BigDecimal nonNegative(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value.max(BigDecimal.ZERO);
  }

  private static BigDecimal scale(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
  }
}
