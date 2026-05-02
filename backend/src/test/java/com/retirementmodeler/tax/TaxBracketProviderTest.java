package com.retirementmodeler.tax;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.retirementmodeler.model.FilingStatus;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaxBracketProviderTest {

  private final TaxBracketProvider provider = new TaxBracketProvider();

  @Test
  void baselineFactorReturns2026Thresholds() {
    TaxBrackets baseline = provider.bracketsForYear(2026, BigDecimal.ONE);

    List<BracketTier> singleOrdinary = baseline.ordinaryBracketsFor(FilingStatus.SINGLE);
    assertThat(singleOrdinary).hasSize(7);
    assertThat(singleOrdinary.get(0).threshold()).isEqualByComparingTo("0");
    assertThat(singleOrdinary.get(0).rate()).isEqualByComparingTo("0.10");
    assertThat(singleOrdinary.get(1).threshold()).isEqualByComparingTo("12400");
    assertThat(singleOrdinary.get(6).threshold()).isEqualByComparingTo("640600");
    assertThat(singleOrdinary.get(6).rate()).isEqualByComparingTo("0.37");

    assertThat(baseline.standardDeductionFor(FilingStatus.MARRIED_FILING_JOINTLY))
        .isEqualByComparingTo("32200");
    assertThat(baseline.baseTaxYear()).isEqualTo(2026);
  }

  @Test
  void inflationFactorScalesThresholdsAndDeduction() {
    BigDecimal factor = new BigDecimal("2.00");

    TaxBrackets inflated = provider.bracketsForYear(2050, factor);

    List<BracketTier> mfj = inflated.ordinaryBracketsFor(FilingStatus.MARRIED_FILING_JOINTLY);
    // 12% bracket starts at $24,800 baseline → $49,600 at 2x
    assertThat(mfj.get(1).threshold()).isEqualByComparingTo("49600");
    // 37% bracket starts at $768,700 baseline → $1,537,400 at 2x
    assertThat(mfj.get(6).threshold()).isEqualByComparingTo("1537400");

    // Standard deduction MFJ $32,200 → $64,400
    assertThat(inflated.standardDeductionFor(FilingStatus.MARRIED_FILING_JOINTLY))
        .isEqualByComparingTo("64400");

    // Year passes through to baseTaxYear so callers know which year these brackets represent
    assertThat(inflated.baseTaxYear()).isEqualTo(2050);
  }

  @Test
  void lowestTierZeroThresholdStaysZero() {
    TaxBrackets inflated = provider.bracketsForYear(2040, new BigDecimal("1.75"));

    for (FilingStatus status : FilingStatus.values()) {
      assertThat(inflated.ordinaryBracketsFor(status).get(0).threshold())
          .as("ordinary lowest tier for %s", status)
          .isEqualByComparingTo("0");
      assertThat(inflated.longTermCapitalGainsBracketsFor(status).get(0).threshold())
          .as("LTCG lowest tier for %s", status)
          .isEqualByComparingTo("0");
    }
  }

  @Test
  void ratesAreUnchangedByInflationFactor() {
    TaxBrackets baseline = provider.bracketsForYear(2025, BigDecimal.ONE);
    TaxBrackets inflated = provider.bracketsForYear(2045, new BigDecimal("1.85"));

    for (FilingStatus status : FilingStatus.values()) {
      List<BracketTier> baselineTiers = baseline.ordinaryBracketsFor(status);
      List<BracketTier> inflatedTiers = inflated.ordinaryBracketsFor(status);
      assertThat(inflatedTiers).hasSameSizeAs(baselineTiers);
      for (int i = 0; i < baselineTiers.size(); i++) {
        assertThat(inflatedTiers.get(i).rate())
            .as("ordinary tier %d rate for %s", i, status)
            .isEqualByComparingTo(baselineTiers.get(i).rate());
      }
    }
  }

  @Test
  void ltcgBracketsScaleWithInflation() {
    TaxBrackets inflated = provider.bracketsForYear(2030, new BigDecimal("1.50"));

    List<BracketTier> mfj =
        inflated.longTermCapitalGainsBracketsFor(FilingStatus.MARRIED_FILING_JOINTLY);
    assertThat(mfj).hasSize(3);
    // 0% tier 0 → 0 (zero stays zero)
    assertThat(mfj.get(0).threshold()).isEqualByComparingTo("0");
    assertThat(mfj.get(0).rate()).isEqualByComparingTo("0.00");
    // 15% tier $98,900 → $148,350
    assertThat(mfj.get(1).threshold()).isEqualByComparingTo("148350");
    assertThat(mfj.get(1).rate()).isEqualByComparingTo("0.15");
    // 20% tier $613,700 → $920,550
    assertThat(mfj.get(2).threshold()).isEqualByComparingTo("920550");
    assertThat(mfj.get(2).rate()).isEqualByComparingTo("0.20");
  }

  @Test
  void allFourFilingStatusesArePopulated() {
    TaxBrackets baseline = provider.bracketsForYear(2025, BigDecimal.ONE);
    for (FilingStatus status : FilingStatus.values()) {
      assertThat(baseline.ordinaryBracketsFor(status)).isNotEmpty();
      assertThat(baseline.longTermCapitalGainsBracketsFor(status)).isNotEmpty();
      assertThat(baseline.standardDeductionFor(status)).isPositive();
    }
  }

  @Test
  void rejectsNullFactor() {
    assertThatThrownBy(() -> provider.bracketsForYear(2030, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNonPositiveFactor() {
    assertThatThrownBy(() -> provider.bracketsForYear(2030, BigDecimal.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> provider.bracketsForYear(2030, new BigDecimal("-1.0")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
