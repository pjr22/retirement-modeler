package com.retirementmodeler.tax;

import com.retirementmodeler.model.FilingStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Snapshot of federal tax parameters for a single tax year, across all filing statuses. Each
 * bracket list is ordered ascending by threshold; the first tier always starts at {@code 0} and the
 * last tier extends to infinity.
 *
 * <p>Used by {@link TaxBracketProvider} to return either the baseline-year values or
 * inflation-adjusted values for a future projection year.
 */
public record TaxBrackets(
    Map<FilingStatus, List<BracketTier>> ordinaryBrackets,
    Map<FilingStatus, List<BracketTier>> longTermCapitalGainsBrackets,
    Map<FilingStatus, BigDecimal> standardDeductions,
    int baseTaxYear) {

  public List<BracketTier> ordinaryBracketsFor(FilingStatus status) {
    return require(ordinaryBrackets, status, "ordinary");
  }

  public List<BracketTier> longTermCapitalGainsBracketsFor(FilingStatus status) {
    return require(longTermCapitalGainsBrackets, status, "long-term capital gains");
  }

  public BigDecimal standardDeductionFor(FilingStatus status) {
    BigDecimal value = standardDeductions.get(status);
    if (value == null) {
      throw new IllegalArgumentException("No standard deduction defined for " + status);
    }
    return value;
  }

  private static <T> List<T> require(
      Map<FilingStatus, List<T>> map, FilingStatus status, String label) {
    List<T> tiers = map.get(status);
    if (tiers == null) {
      throw new IllegalArgumentException("No " + label + " brackets defined for " + status);
    }
    return tiers;
  }
}
