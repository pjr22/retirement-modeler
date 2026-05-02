package com.retirementmodeler.tax;

import static org.assertj.core.api.Assertions.assertThat;

import com.retirementmodeler.model.FilingStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SocialSecurityTaxerTest {

  private final SocialSecurityTaxer taxer = new SocialSecurityTaxer();

  // --- Below tier 1: no SS taxable ---

  @Test
  void singleBelowTier1IsUntaxed() {
    // PI = 0 + 0.5 × 30K = 15K, < $25K T1 → no SS taxable.
    BigDecimal taxable =
        taxer.computeTaxableAmount(FilingStatus.SINGLE, bd(30_000), BigDecimal.ZERO);
    assertThat(taxable).isEqualByComparingTo("0");
  }

  @Test
  void mfjBelowTier1IsUntaxed() {
    // PI = 10K + 0.5 × 40K = 30K, < $32K T1 → no SS taxable.
    BigDecimal taxable =
        taxer.computeTaxableAmount(FilingStatus.MARRIED_FILING_JOINTLY, bd(40_000), bd(10_000));
    assertThat(taxable).isEqualByComparingTo("0");
  }

  // --- Tier 1 (between T1 and T2): up to 50% taxable ---

  @Test
  void singleInTier1Has50PercentOfExcessTaxable() {
    // SS=30K, other=15K → PI = 30K. Tier 1 ($25K-$34K).
    // taxable = min(0.5 × 30K, 0.5 × (30K - 25K)) = min(15K, 2.5K) = 2.5K.
    BigDecimal taxable = taxer.computeTaxableAmount(FilingStatus.SINGLE, bd(30_000), bd(15_000));
    assertThat(taxable).isEqualByComparingTo("2500");
  }

  @Test
  void singleInTier1CapsAt50PercentOfSs() {
    // SS=2K, other=24K → PI = 25K + 0 = 25K. Wait: 24K + 1K = 25K which is = T1, untaxed.
    // Use other=24.5K → PI = 24.5K + 1K = 25.5K. Tier 1.
    // 0.5 × (25.5K - 25K) = 250. min(0.5 × 2K, 250) = min(1000, 250) = 250.
    BigDecimal taxable = taxer.computeTaxableAmount(FilingStatus.SINGLE, bd(2_000), bd(24_500));
    assertThat(taxable).isEqualByComparingTo("250");
  }

  @Test
  void mfjInTier1() {
    // SS=40K, other=20K → PI = 40K. Tier 1 ($32K-$44K).
    // taxable = min(0.5 × 40K, 0.5 × (40K - 32K)) = min(20K, 4K) = 4K.
    BigDecimal taxable =
        taxer.computeTaxableAmount(FilingStatus.MARRIED_FILING_JOINTLY, bd(40_000), bd(20_000));
    assertThat(taxable).isEqualByComparingTo("4000");
  }

  // --- Exactly at threshold edges ---

  @Test
  void singleExactlyAtT1IsUntaxed() {
    // PI = 25K exactly = T1 → untaxed (we use ≤, so the edge is in tier 0).
    BigDecimal taxable = taxer.computeTaxableAmount(FilingStatus.SINGLE, bd(20_000), bd(15_000));
    assertThat(taxable).isEqualByComparingTo("0");
  }

  @Test
  void singleExactlyAtT2UsesTier1Formula() {
    // SS=20K, other=24K → PI = 24K + 10K = 34K = T2 exactly. We use ≤, so still tier 1.
    // taxable = min(0.5 × 20K, 0.5 × (34K - 25K)) = min(10K, 4.5K) = 4.5K.
    BigDecimal taxable = taxer.computeTaxableAmount(FilingStatus.SINGLE, bd(20_000), bd(24_000));
    assertThat(taxable).isEqualByComparingTo("4500");
  }

  // --- Tier 2 (above T2): up to 85% taxable, with carryover from tier 1 ---

  @Test
  void singleJustOverTier2() {
    // SS=30K, other=25K → PI = 25K + 15K = 40K > T2 (34K).
    // tier1Carryover = min(15K, 0.5 × (34K - 25K)) = min(15K, 4.5K) = 4.5K.
    // 0.85 × (40K - 34K) = 5.1K. cap = 0.85 × 30K = 25.5K.
    // taxable = min(25.5K, 5.1K + 4.5K) = min(25.5K, 9.6K) = 9.6K.
    BigDecimal taxable = taxer.computeTaxableAmount(FilingStatus.SINGLE, bd(30_000), bd(25_000));
    assertThat(taxable).isEqualByComparingTo("9600");
  }

  @Test
  void singleHighIncomeCappedAt85Percent() {
    // SS=30K, other=200K → PI = 215K. Far above T2. 0.85 × 30K = 25.5K cap binds.
    BigDecimal taxable = taxer.computeTaxableAmount(FilingStatus.SINGLE, bd(30_000), bd(200_000));
    assertThat(taxable).isEqualByComparingTo("25500");
  }

  @Test
  void mfjInTier2() {
    // SS=40K, other=50K → PI = 70K > T2 (44K).
    // tier1Carryover = min(20K, 0.5 × (44K - 32K)) = min(20K, 6K) = 6K.
    // 0.85 × (70K - 44K) = 22.1K. cap = 0.85 × 40K = 34K.
    // taxable = min(34K, 22.1K + 6K) = min(34K, 28.1K) = 28.1K.
    BigDecimal taxable =
        taxer.computeTaxableAmount(FilingStatus.MARRIED_FILING_JOINTLY, bd(40_000), bd(50_000));
    assertThat(taxable).isEqualByComparingTo("28100");
  }

  @Test
  void mfjHighIncomeCappedAt85Percent() {
    // SS=40K, other=300K → PI = 320K. cap = 0.85 × 40K = 34K binds.
    BigDecimal taxable =
        taxer.computeTaxableAmount(FilingStatus.MARRIED_FILING_JOINTLY, bd(40_000), bd(300_000));
    assertThat(taxable).isEqualByComparingTo("34000");
  }

  // --- Filing-status equivalences ---

  @Test
  void headOfHouseholdUsesSingleThresholds() {
    // Same calc as singleJustOverTier2 → 9.6K.
    BigDecimal taxable =
        taxer.computeTaxableAmount(FilingStatus.HEAD_OF_HOUSEHOLD, bd(30_000), bd(25_000));
    assertThat(taxable).isEqualByComparingTo("9600");
  }

  @Test
  void marriedFilingSeparatelyDefaultsToLivedApartThresholds() {
    // We model the "lived apart all year" case → Single thresholds.
    // Same calc as singleJustOverTier2 → 9.6K.
    BigDecimal taxable =
        taxer.computeTaxableAmount(FilingStatus.MARRIED_FILING_SEPARATELY, bd(30_000), bd(25_000));
    assertThat(taxable).isEqualByComparingTo("9600");
  }

  // --- Edge cases ---

  @Test
  void zeroSsReturnsZero() {
    BigDecimal taxable =
        taxer.computeTaxableAmount(FilingStatus.SINGLE, BigDecimal.ZERO, bd(100_000));
    assertThat(taxable).isEqualByComparingTo("0");
  }

  @Test
  void negativeInputsAreClamped() {
    BigDecimal taxable =
        taxer.computeTaxableAmount(FilingStatus.SINGLE, new BigDecimal("-5000"), bd(100_000));
    assertThat(taxable).isEqualByComparingTo("0");
  }

  @Test
  void nullInputsTreatedAsZero() {
    BigDecimal taxable = taxer.computeTaxableAmount(FilingStatus.SINGLE, null, null);
    assertThat(taxable).isEqualByComparingTo("0");
  }

  // --- Result is always within [0, 0.85 × SS] ---

  @Test
  void resultNeverExceeds85PercentOfBenefits() {
    BigDecimal ss = bd(30_000);
    BigDecimal cap = bd(25_500); // 0.85 × 30K
    for (int otherK = 0; otherK <= 500; otherK += 5) {
      BigDecimal taxable = taxer.computeTaxableAmount(FilingStatus.SINGLE, ss, bd(otherK * 1000L));
      assertThat(taxable).as("other=%dK", otherK).isBetween(BigDecimal.ZERO, cap);
    }
  }

  private static BigDecimal bd(long amount) {
    return new BigDecimal(amount);
  }
}
