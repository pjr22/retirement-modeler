package com.retirementmodeler.simulation.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;

import com.retirementmodeler.model.AccountType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaxOptimizedAllocatorTest {

  private final TaxOptimizedAllocator allocator = new TaxOptimizedAllocator();

  @Test
  void drainsTaxableBeforeTraditionalAndRoth() {
    AccountSnapshot taxable = snap(AccountType.TAXABLE_BROKERAGE, 10_000);
    AccountSnapshot traditional = snap(AccountType.TRADITIONAL_IRA, 50_000);
    AccountSnapshot roth = snap(AccountType.ROTH_IRA, 20_000);

    Map<UUID, BigDecimal> result =
        allocator.allocate(List.of(traditional, roth, taxable), bd(5_000));

    // Need is well within taxable tier alone → only taxable touched.
    assertThat(result.get(taxable.id())).isEqualByComparingTo("5000");
    assertThat(result).doesNotContainKey(traditional.id());
    assertThat(result).doesNotContainKey(roth.id());
  }

  @Test
  void drainsTaxableProportionallyWithSavings() {
    AccountSnapshot taxable = snap(AccountType.TAXABLE_BROKERAGE, 6_000);
    AccountSnapshot savings = snap(AccountType.SAVINGS, 4_000);
    AccountSnapshot traditional = snap(AccountType.TRADITIONAL_401K, 100_000);

    // Need $2,000, well within tier 1 ($10,000 total): 60/40 split.
    Map<UUID, BigDecimal> result =
        allocator.allocate(List.of(taxable, savings, traditional), bd(2_000));

    assertThat(result.get(taxable.id())).isEqualByComparingTo("1200");
    assertThat(result.get(savings.id())).isEqualByComparingTo("800");
    assertThat(result).doesNotContainKey(traditional.id());
  }

  @Test
  void overflowsToTraditionalWhenTaxableExhausted() {
    AccountSnapshot taxable = snap(AccountType.TAXABLE_BROKERAGE, 3_000);
    AccountSnapshot savings = snap(AccountType.SAVINGS, 2_000);
    AccountSnapshot trad401k = snap(AccountType.TRADITIONAL_401K, 30_000);
    AccountSnapshot tradIra = snap(AccountType.TRADITIONAL_IRA, 20_000);
    AccountSnapshot roth = snap(AccountType.ROTH_IRA, 50_000);

    // Tier 1 = $5,000. Need $9,000 → drain tier 1 fully, $4,000 left for tier 2.
    // Tier 2 total $50,000 → 30k/50k = 60%, 20k/50k = 40% of $4,000 → $2,400 / $1,600.
    Map<UUID, BigDecimal> result =
        allocator.allocate(List.of(taxable, savings, trad401k, tradIra, roth), bd(9_000));

    assertThat(result.get(taxable.id())).isEqualByComparingTo("3000");
    assertThat(result.get(savings.id())).isEqualByComparingTo("2000");
    assertThat(result.get(trad401k.id())).isEqualByComparingTo("2400");
    assertThat(result.get(tradIra.id())).isEqualByComparingTo("1600");
    assertThat(result).doesNotContainKey(roth.id());
  }

  @Test
  void rothPreservedUntilLowerTiersExhausted() {
    AccountSnapshot taxable = snap(AccountType.TAXABLE_BROKERAGE, 1_000);
    AccountSnapshot trad = snap(AccountType.TRADITIONAL_IRA, 1_000);
    AccountSnapshot roth = snap(AccountType.ROTH_IRA, 100_000);

    // Need $5,000 → both lower tiers drain ($2,000), $3,000 falls to Roth.
    Map<UUID, BigDecimal> result = allocator.allocate(List.of(taxable, trad, roth), bd(5_000));

    assertThat(result.get(taxable.id())).isEqualByComparingTo("1000");
    assertThat(result.get(trad.id())).isEqualByComparingTo("1000");
    assertThat(result.get(roth.id())).isEqualByComparingTo("3000");
  }

  @Test
  void hsaIsInRothTier() {
    AccountSnapshot trad = snap(AccountType.TRADITIONAL_IRA, 1_000);
    AccountSnapshot roth = snap(AccountType.ROTH_IRA, 1_000);
    AccountSnapshot hsa = snap(AccountType.HSA, 1_000);

    // No taxable. Need $1,500 → drain $1,000 traditional, $500 from tier 3 split 50/50.
    Map<UUID, BigDecimal> result = allocator.allocate(List.of(trad, roth, hsa), bd(1_500));

    assertThat(result.get(trad.id())).isEqualByComparingTo("1000");
    assertThat(result.get(roth.id())).isEqualByComparingTo("250");
    assertThat(result.get(hsa.id())).isEqualByComparingTo("250");
  }

  @Test
  void overdrawDrainsAllAccounts() {
    AccountSnapshot taxable = snap(AccountType.TAXABLE_BROKERAGE, 1_000);
    AccountSnapshot trad = snap(AccountType.TRADITIONAL_IRA, 2_000);
    AccountSnapshot roth = snap(AccountType.ROTH_IRA, 3_000);

    // Need $100,000 against $6,000 total → everything drains.
    Map<UUID, BigDecimal> result = allocator.allocate(List.of(taxable, trad, roth), bd(100_000));

    assertThat(result.get(taxable.id())).isEqualByComparingTo("1000");
    assertThat(result.get(trad.id())).isEqualByComparingTo("2000");
    assertThat(result.get(roth.id())).isEqualByComparingTo("3000");
  }

  @Test
  void zeroBalanceTaxableSkipsToTraditional() {
    AccountSnapshot taxable = snap(AccountType.TAXABLE_BROKERAGE, 0);
    AccountSnapshot trad = snap(AccountType.TRADITIONAL_IRA, 5_000);

    Map<UUID, BigDecimal> result = allocator.allocate(List.of(taxable, trad), bd(2_000));

    assertThat(result).doesNotContainKey(taxable.id());
    assertThat(result.get(trad.id())).isEqualByComparingTo("2000");
  }

  // --- helpers ---

  private static AccountSnapshot snap(AccountType type, long balance) {
    return new AccountSnapshot(UUID.randomUUID(), type, new BigDecimal(balance));
  }

  private static BigDecimal bd(long amount) {
    return new BigDecimal(amount);
  }
}
