package com.retirementmodeler.tax;

import static org.assertj.core.api.Assertions.assertThat;

import com.retirementmodeler.model.FilingStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TaxCalculatorTest {

  private final TaxCalculator calculator = new TaxCalculator();
  private final TaxBrackets brackets2026 =
      new TaxBracketProvider().bracketsForYear(2026, BigDecimal.ONE);

  // --- zero income / below-deduction edge cases ---

  @Test
  void zeroIncomeProducesZeroTax() {
    TaxResult result =
        calculator.compute(
            FilingStatus.MARRIED_FILING_JOINTLY, BigDecimal.ZERO, BigDecimal.ZERO, brackets2026);

    assertThat(result.totalTax()).isEqualByComparingTo("0");
    assertThat(result.ordinaryTax()).isEqualByComparingTo("0");
    assertThat(result.capitalGainsTax()).isEqualByComparingTo("0");
    assertThat(result.ordinaryTaxableIncome()).isEqualByComparingTo("0");
    assertThat(result.effectiveRate()).isEqualByComparingTo("0");
    // No income → marginal rate is the lowest bracket rate (next $1 of ordinary income at 10%).
    assertThat(result.marginalRate()).isEqualByComparingTo("0.10");
  }

  @Test
  void ordinaryBelowStandardDeductionIsUntaxed() {
    TaxResult result =
        calculator.compute(
            FilingStatus.MARRIED_FILING_JOINTLY, bd(20_000), BigDecimal.ZERO, brackets2026);
    // MFJ deduction $32,200 > $20,000 ordinary → taxable ordinary = 0
    assertThat(result.ordinaryTaxableIncome()).isEqualByComparingTo("0");
    assertThat(result.totalTax()).isEqualByComparingTo("0");
  }

  @Test
  void unusedDeductionAbsorbsLtcg() {
    // Ordinary $5K, LTCG $50K, MFJ deduction $32,200.
    // Total taxable = 5K + 50K - 32.2K = $22,800; taxable ordinary = max(0, 5K - 32.2K) = 0;
    // taxable LTCG = $22,800 (deduction "spilled over" onto LTCG, matching the IRS QDCG worksheet).
    // $22,800 sits entirely within MFJ 0% LTCG bracket ($0-$98,900) → LTCG tax = $0.
    TaxResult result =
        calculator.compute(
            FilingStatus.MARRIED_FILING_JOINTLY, bd(5_000), bd(50_000), brackets2026);

    assertThat(result.ordinaryTaxableIncome()).isEqualByComparingTo("0");
    assertThat(result.ordinaryTax()).isEqualByComparingTo("0");
    assertThat(result.capitalGainsTax()).isEqualByComparingTo("0");
    assertThat(result.totalTax()).isEqualByComparingTo("0");
  }

  // --- bracket-edge and mid-bracket ordinary income ---

  @Test
  void atExactBracketEdgeUsesLowerTier() {
    // MFJ: $57,000 ordinary - $32,200 deduction = $24,800 taxable, exactly the 10/12 boundary.
    // 10% × $24,800 = $2,480. Marginal is 0.12 — next $1 crosses into the 12% bracket.
    TaxResult result =
        calculator.compute(
            FilingStatus.MARRIED_FILING_JOINTLY, bd(57_000), BigDecimal.ZERO, brackets2026);

    assertThat(result.ordinaryTaxableIncome()).isEqualByComparingTo("24800");
    assertThat(result.ordinaryTax()).isEqualByComparingTo("2480");
    assertThat(result.marginalRate()).isEqualByComparingTo("0.12");
  }

  @Test
  void midBracketMfjOrdinary() {
    // MFJ: $100,000 - $32,200 = $67,800 taxable.
    // 10% × $24,800 = $2,480
    // 12% × ($67,800 - $24,800) = 12% × $43,000 = $5,160
    // Total = $7,640. Marginal at $67,800: between $24,800 and $100,800 → 0.12.
    TaxResult result =
        calculator.compute(
            FilingStatus.MARRIED_FILING_JOINTLY, bd(100_000), BigDecimal.ZERO, brackets2026);

    assertThat(result.ordinaryTaxableIncome()).isEqualByComparingTo("67800");
    assertThat(result.ordinaryTax()).isEqualByComparingTo("7640");
    assertThat(result.totalTax()).isEqualByComparingTo("7640");
    assertThat(result.marginalRate()).isEqualByComparingTo("0.12");
  }

  @Test
  void singleFilerMidBracket() {
    // Single: $80,000 - $16,100 = $63,900 taxable.
    // 10% × $12,400 = $1,240
    // 12% × ($50,400 - $12,400) = 12% × $38,000 = $4,560
    // 22% × ($63,900 - $50,400) = 22% × $13,500 = $2,970
    // Total = $8,770.
    TaxResult result =
        calculator.compute(FilingStatus.SINGLE, bd(80_000), BigDecimal.ZERO, brackets2026);

    assertThat(result.ordinaryTaxableIncome()).isEqualByComparingTo("63900");
    assertThat(result.ordinaryTax()).isEqualByComparingTo("8770");
    assertThat(result.marginalRate()).isEqualByComparingTo("0.22");
  }

  @Test
  void headOfHouseholdMidBracket() {
    // HoH: $80,000 - $24,150 = $55,850 taxable.
    // 10% × $17,700 = $1,770
    // 12% × ($55,850 - $17,700) = 12% × $38,150 = $4,578
    // Total = $6,348.
    TaxResult result =
        calculator.compute(
            FilingStatus.HEAD_OF_HOUSEHOLD, bd(80_000), BigDecimal.ZERO, brackets2026);

    assertThat(result.ordinaryTaxableIncome()).isEqualByComparingTo("55850");
    assertThat(result.ordinaryTax()).isEqualByComparingTo("6348");
    assertThat(result.marginalRate()).isEqualByComparingTo("0.12");
  }

  @Test
  void marriedFilingSeparatelyTopBracket() {
    // MFS: $500,000 - $16,100 = $483,900 taxable.
    // 10% × 12,400  =    1,240.00
    // 12% × 38,000  =    4,560.00
    // 22% × 55,300  =   12,166.00
    // 24% × 96,075  =   23,058.00
    // 32% × 54,450  =   17,424.00
    // 35% × 128,125 =   44,843.75
    // 37% × 99,550  =   36,833.50
    //                 ----------
    //                 140,125.25
    // Confirms MFS 37% bracket starts at $384,350, not Single's $640,600.
    TaxResult result =
        calculator.compute(
            FilingStatus.MARRIED_FILING_SEPARATELY, bd(500_000), BigDecimal.ZERO, brackets2026);

    assertThat(result.ordinaryTaxableIncome()).isEqualByComparingTo("483900");
    assertThat(result.ordinaryTax()).isEqualByComparingTo("140125.25");
    assertThat(result.marginalRate()).isEqualByComparingTo("0.37");
  }

  // --- LTCG stacking ---

  @Test
  void ltcgFitsEntirelyIn0PercentBracket() {
    // MFJ: ordinary $50K - $32.2K = $17,800 taxable ordinary.
    // LTCG stacks from $17,800 to $47,800. MFJ 0% bracket ends at $98,900 → all $30K at 0%.
    TaxResult result =
        calculator.compute(
            FilingStatus.MARRIED_FILING_JOINTLY, bd(50_000), bd(30_000), brackets2026);

    assertThat(result.ordinaryTax()).isEqualByComparingTo("1780"); // 10% × $17,800
    assertThat(result.capitalGainsTax()).isEqualByComparingTo("0");
    assertThat(result.totalTax()).isEqualByComparingTo("1780");
  }

  @Test
  void ltcgStacksAboveOrdinaryIntoMiddleBracket() {
    // MFJ: ordinary $200K - $32.2K = $167,800 taxable ordinary.
    // Ordinary tax:
    //   10% × 24,800 = 2,480
    //   12% × 76,000 = 9,120
    //   22% × 67,000 = 14,740
    //   total       = 26,340
    // LTCG stacks $167,800 → $217,800. Above MFJ 0% ($98,900), entirely within 15%
    // ($98,900-$613,700).
    // LTCG tax = 15% × 50,000 = 7,500. Total = 33,840.
    TaxResult result =
        calculator.compute(
            FilingStatus.MARRIED_FILING_JOINTLY, bd(200_000), bd(50_000), brackets2026);

    assertThat(result.ordinaryTax()).isEqualByComparingTo("26340");
    assertThat(result.capitalGainsTax()).isEqualByComparingTo("7500");
    assertThat(result.totalTax()).isEqualByComparingTo("33840");
    // Effective = 33840 / 250000 = 0.13536 → 0.1354 at scale 4 HALF_UP.
    assertThat(result.effectiveRate()).isEqualByComparingTo("0.1354");
  }

  @Test
  void ltcgSpansAll20PercentBracket() {
    // MFJ: ordinary $700K - $32.2K = $667,800 taxable ordinary (already past LTCG 20% threshold
    // $613,700).
    // LTCG $500K stacks from $667,800 to $1,167,800 → entirely in the 20% LTCG tier.
    // LTCG tax = 20% × 500,000 = 100,000.
    TaxResult result =
        calculator.compute(
            FilingStatus.MARRIED_FILING_JOINTLY, bd(700_000), bd(500_000), brackets2026);

    assertThat(result.capitalGainsTax()).isEqualByComparingTo("100000");
  }

  @Test
  void ltcgSplitAcross0And15PercentBrackets() {
    // MFJ: ordinary $80K - $32.2K = $47,800 taxable ordinary (still in MFJ 0% LTCG bracket).
    // LTCG $80K stacks from $47,800 to $127,800. MFJ 0% ends at $98,900.
    //   Portion at 0%: 98,900 - 47,800 = 51,100 → tax = 0
    //   Portion at 15%: 127,800 - 98,900 = 28,900 → tax = 4,335
    // Ordinary tax: 10% × 24,800 + 12% × (47,800 - 24,800) = 2,480 + 2,760 = 5,240.
    TaxResult result =
        calculator.compute(
            FilingStatus.MARRIED_FILING_JOINTLY, bd(80_000), bd(80_000), brackets2026);

    assertThat(result.ordinaryTax()).isEqualByComparingTo("5240");
    assertThat(result.capitalGainsTax()).isEqualByComparingTo("4335");
    assertThat(result.totalTax()).isEqualByComparingTo("9575");
  }

  // --- defensive ---

  @Test
  void negativeIncomeIsClamped() {
    TaxResult result =
        calculator.compute(
            FilingStatus.SINGLE, new BigDecimal("-1000"), new BigDecimal("-500"), brackets2026);

    assertThat(result.totalTax()).isEqualByComparingTo("0");
    assertThat(result.effectiveRate()).isEqualByComparingTo("0");
  }

  @Test
  void nullIncomeIsTreatedAsZero() {
    TaxResult result = calculator.compute(FilingStatus.SINGLE, null, null, brackets2026);
    assertThat(result.totalTax()).isEqualByComparingTo("0");
  }

  private static BigDecimal bd(long amount) {
    return new BigDecimal(amount);
  }
}
