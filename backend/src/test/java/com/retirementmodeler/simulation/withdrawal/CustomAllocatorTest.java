package com.retirementmodeler.simulation.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;

import com.retirementmodeler.model.AccountType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomAllocatorTest {

  @Test
  void respectsUserSuppliedOrder() {
    // Unusual order: drain Roth first, then Traditional, then Taxable.
    CustomAllocator allocator =
        new CustomAllocator(
            List.of(
                AccountType.ROTH_IRA, AccountType.TRADITIONAL_IRA, AccountType.TAXABLE_BROKERAGE));

    AccountSnapshot taxable = snap(AccountType.TAXABLE_BROKERAGE, 10_000);
    AccountSnapshot trad = snap(AccountType.TRADITIONAL_IRA, 10_000);
    AccountSnapshot roth = snap(AccountType.ROTH_IRA, 10_000);

    Map<UUID, BigDecimal> result = allocator.allocate(List.of(taxable, trad, roth), bd(8_000));

    assertThat(result.get(roth.id())).isEqualByComparingTo("8000");
    assertThat(result).doesNotContainKey(trad.id());
    assertThat(result).doesNotContainKey(taxable.id());
  }

  @Test
  void overflowsToNextTypeInOrder() {
    CustomAllocator allocator =
        new CustomAllocator(List.of(AccountType.SAVINGS, AccountType.TRADITIONAL_401K));

    AccountSnapshot savings = snap(AccountType.SAVINGS, 2_000);
    AccountSnapshot trad = snap(AccountType.TRADITIONAL_401K, 50_000);

    Map<UUID, BigDecimal> result = allocator.allocate(List.of(savings, trad), bd(5_000));

    assertThat(result.get(savings.id())).isEqualByComparingTo("2000");
    assertThat(result.get(trad.id())).isEqualByComparingTo("3000");
  }

  @Test
  void missingAccountTypeFallsToFinalTier() {
    // User listed only TAXABLE; SAVINGS account is "forgotten" — should still drain after taxable
    // is exhausted rather than being silently skipped.
    CustomAllocator allocator = new CustomAllocator(List.of(AccountType.TAXABLE_BROKERAGE));

    AccountSnapshot taxable = snap(AccountType.TAXABLE_BROKERAGE, 1_000);
    AccountSnapshot savings = snap(AccountType.SAVINGS, 5_000);

    Map<UUID, BigDecimal> result = allocator.allocate(List.of(taxable, savings), bd(3_000));

    assertThat(result.get(taxable.id())).isEqualByComparingTo("1000");
    assertThat(result.get(savings.id())).isEqualByComparingTo("2000");
  }

  @Test
  void emptyOrderBehavesLikeProportionalAcrossAllAccounts() {
    // No order supplied → all accounts go into the fallback tier and split proportionally.
    CustomAllocator allocator = new CustomAllocator(List.of());

    AccountSnapshot a = snap(AccountType.TAXABLE_BROKERAGE, 6_000);
    AccountSnapshot b = snap(AccountType.TRADITIONAL_IRA, 4_000);

    Map<UUID, BigDecimal> result = allocator.allocate(List.of(a, b), bd(1_000));

    assertThat(result.get(a.id())).isEqualByComparingTo("600");
    assertThat(result.get(b.id())).isEqualByComparingTo("400");
  }

  @Test
  void duplicateTypesInOrderAreFolded() {
    // [TAXABLE, TAXABLE, TRADITIONAL] should behave like [TAXABLE, TRADITIONAL].
    CustomAllocator allocator =
        new CustomAllocator(
            List.of(
                AccountType.TAXABLE_BROKERAGE,
                AccountType.TAXABLE_BROKERAGE,
                AccountType.TRADITIONAL_IRA));

    AccountSnapshot taxable = snap(AccountType.TAXABLE_BROKERAGE, 1_000);
    AccountSnapshot trad = snap(AccountType.TRADITIONAL_IRA, 5_000);

    Map<UUID, BigDecimal> result = allocator.allocate(List.of(taxable, trad), bd(3_000));

    assertThat(result.get(taxable.id())).isEqualByComparingTo("1000");
    assertThat(result.get(trad.id())).isEqualByComparingTo("2000");
  }

  @Test
  void multipleAccountsOfSameTypeDrainProportionally() {
    CustomAllocator allocator = new CustomAllocator(List.of(AccountType.TRADITIONAL_IRA));

    AccountSnapshot tradA = snap(AccountType.TRADITIONAL_IRA, 3_000);
    AccountSnapshot tradB = snap(AccountType.TRADITIONAL_IRA, 7_000);

    Map<UUID, BigDecimal> result = allocator.allocate(List.of(tradA, tradB), bd(1_000));

    assertThat(result.get(tradA.id())).isEqualByComparingTo("300");
    assertThat(result.get(tradB.id())).isEqualByComparingTo("700");
  }

  @Test
  void nullOrderIsTreatedAsEmpty() {
    CustomAllocator allocator = new CustomAllocator(null);

    AccountSnapshot a = snap(AccountType.TAXABLE_BROKERAGE, 1_000);
    Map<UUID, BigDecimal> result = allocator.allocate(List.of(a), bd(500));

    assertThat(result.get(a.id())).isEqualByComparingTo("500");
  }

  // --- helpers ---

  private static AccountSnapshot snap(AccountType type, long balance) {
    return new AccountSnapshot(UUID.randomUUID(), type, new BigDecimal(balance));
  }

  private static BigDecimal bd(long amount) {
    return new BigDecimal(amount);
  }
}
