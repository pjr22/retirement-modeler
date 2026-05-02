package com.retirementmodeler.tax;

import com.retirementmodeler.model.FilingStatus;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Returns federal tax brackets and standard deductions for a given projection year, scaled by a
 * cumulative inflation factor to approximate IRS bracket creep.
 *
 * <p>The bracket-tier rates and the relative shape of the brackets are taken from the 2026
 * baseline; only the dollar thresholds and the standard deduction are inflated. This matches IRS
 * behavior — bracket rates are set by Congress and changed rarely; thresholds drift each year via
 * Rev. Proc. inflation adjustments.
 */
@Component
public class TaxBracketProvider {

  private static final MathContext MC = new MathContext(16);

  public TaxBrackets bracketsForYear(int year, BigDecimal cumulativeInflationFactor) {
    if (cumulativeInflationFactor == null || cumulativeInflationFactor.signum() <= 0) {
      throw new IllegalArgumentException(
          "cumulativeInflationFactor must be positive, got: " + cumulativeInflationFactor);
    }
    TaxBrackets baseline = FederalTaxBrackets2026.BRACKETS;
    return new TaxBrackets(
        scaleBracketMap(baseline.ordinaryBrackets(), cumulativeInflationFactor),
        scaleBracketMap(baseline.longTermCapitalGainsBrackets(), cumulativeInflationFactor),
        scaleDeductions(baseline.standardDeductions(), cumulativeInflationFactor),
        year);
  }

  private static Map<FilingStatus, List<BracketTier>> scaleBracketMap(
      Map<FilingStatus, List<BracketTier>> source, BigDecimal factor) {
    Map<FilingStatus, List<BracketTier>> out = new EnumMap<>(FilingStatus.class);
    source.forEach((status, tiers) -> out.put(status, scaleTiers(tiers, factor)));
    return Map.copyOf(out);
  }

  private static List<BracketTier> scaleTiers(List<BracketTier> tiers, BigDecimal factor) {
    return tiers.stream()
        .map(t -> new BracketTier(scaleDollar(t.threshold(), factor), t.rate()))
        .toList();
  }

  private static Map<FilingStatus, BigDecimal> scaleDeductions(
      Map<FilingStatus, BigDecimal> source, BigDecimal factor) {
    Map<FilingStatus, BigDecimal> out = new EnumMap<>(FilingStatus.class);
    source.forEach((status, amount) -> out.put(status, scaleDollar(amount, factor)));
    return Map.copyOf(out);
  }

  /**
   * Scales a baseline-year dollar amount by the cumulative inflation factor. Thresholds are kept at
   * scale 2 (cents); the lowest-tier $0 threshold stays exactly $0 regardless of factor.
   */
  private static BigDecimal scaleDollar(BigDecimal baselineAmount, BigDecimal factor) {
    if (baselineAmount.signum() == 0) {
      return baselineAmount;
    }
    return baselineAmount.multiply(factor, MC).setScale(2, RoundingMode.HALF_UP);
  }
}
