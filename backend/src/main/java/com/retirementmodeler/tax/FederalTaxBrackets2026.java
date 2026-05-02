package com.retirementmodeler.tax;

import com.retirementmodeler.model.FilingStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Baseline federal tax parameters for tax year 2026, sourced from IRS Rev. Proc. 2025-32 (which
 * incorporates the One Big Beautiful Bill Act amendments — TCJA's 7-tier rate structure was made
 * permanent in July 2025). Bracket thresholds and the standard deduction are scaled by a cumulative
 * inflation factor in {@link TaxBracketProvider} to approximate IRS bracket creep for future
 * projection years.
 *
 * <p>LTCG bracket thresholds are also scaled with inflation. The provisional-income thresholds for
 * Social Security taxation are intentionally <em>not</em> defined here — those are fixed in statute
 * and never inflation-adjusted; they live with the SS-taxation logic.
 *
 * <p>Refresh procedure: when the IRS publishes the next year's Rev. Proc. (typically October), add
 * a new year-suffixed sibling class with that year's values and update {@link TaxBracketProvider}
 * to reference it.
 */
public final class FederalTaxBrackets2026 {

  public static final int BASE_TAX_YEAR = 2026;

  private FederalTaxBrackets2026() {}

  public static final TaxBrackets BRACKETS =
      new TaxBrackets(
          Map.of(
              FilingStatus.SINGLE, ordinarySingle(),
              FilingStatus.MARRIED_FILING_JOINTLY, ordinaryMarriedJointly(),
              FilingStatus.MARRIED_FILING_SEPARATELY, ordinaryMarriedSeparately(),
              FilingStatus.HEAD_OF_HOUSEHOLD, ordinaryHeadOfHousehold()),
          Map.of(
              FilingStatus.SINGLE, ltcgSingle(),
              FilingStatus.MARRIED_FILING_JOINTLY, ltcgMarriedJointly(),
              FilingStatus.MARRIED_FILING_SEPARATELY, ltcgMarriedSeparately(),
              FilingStatus.HEAD_OF_HOUSEHOLD, ltcgHeadOfHousehold()),
          Map.of(
              FilingStatus.SINGLE, new BigDecimal("16100"),
              FilingStatus.MARRIED_FILING_JOINTLY, new BigDecimal("32200"),
              FilingStatus.MARRIED_FILING_SEPARATELY, new BigDecimal("16100"),
              FilingStatus.HEAD_OF_HOUSEHOLD, new BigDecimal("24150")),
          BASE_TAX_YEAR);

  private static List<BracketTier> ordinarySingle() {
    return List.of(
        tier("0", "0.10"),
        tier("12400", "0.12"),
        tier("50400", "0.22"),
        tier("105700", "0.24"),
        tier("201775", "0.32"),
        tier("256225", "0.35"),
        tier("640600", "0.37"));
  }

  private static List<BracketTier> ordinaryMarriedJointly() {
    return List.of(
        tier("0", "0.10"),
        tier("24800", "0.12"),
        tier("100800", "0.22"),
        tier("211400", "0.24"),
        tier("403550", "0.32"),
        tier("512450", "0.35"),
        tier("768700", "0.37"));
  }

  // MFS shares Single's lower brackets; the 37% threshold is half of MFJ's, not Single's.
  private static List<BracketTier> ordinaryMarriedSeparately() {
    return List.of(
        tier("0", "0.10"),
        tier("12400", "0.12"),
        tier("50400", "0.22"),
        tier("105700", "0.24"),
        tier("201775", "0.32"),
        tier("256225", "0.35"),
        tier("384350", "0.37"));
  }

  private static List<BracketTier> ordinaryHeadOfHousehold() {
    return List.of(
        tier("0", "0.10"),
        tier("17700", "0.12"),
        tier("67450", "0.22"),
        tier("105700", "0.24"),
        tier("201775", "0.32"),
        tier("256200", "0.35"),
        tier("640600", "0.37"));
  }

  private static List<BracketTier> ltcgSingle() {
    return List.of(tier("0", "0.00"), tier("49450", "0.15"), tier("545500", "0.20"));
  }

  private static List<BracketTier> ltcgMarriedJointly() {
    return List.of(tier("0", "0.00"), tier("98900", "0.15"), tier("613700", "0.20"));
  }

  // MFS LTCG: 15% threshold matches Single; 20% threshold is half of MFJ's.
  private static List<BracketTier> ltcgMarriedSeparately() {
    return List.of(tier("0", "0.00"), tier("49450", "0.15"), tier("306850", "0.20"));
  }

  private static List<BracketTier> ltcgHeadOfHousehold() {
    return List.of(tier("0", "0.00"), tier("66200", "0.15"), tier("579600", "0.20"));
  }

  private static BracketTier tier(String threshold, String rate) {
    return new BracketTier(new BigDecimal(threshold), new BigDecimal(rate));
  }
}
