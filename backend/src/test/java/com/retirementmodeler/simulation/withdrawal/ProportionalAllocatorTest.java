package com.retirementmodeler.simulation.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;

import com.retirementmodeler.model.AccountType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProportionalAllocatorTest {

  private final ProportionalAllocator allocator = new ProportionalAllocator();

  @Test
  void emptyAccountsListReturnsEmptyMap() {
    Map<UUID, BigDecimal> result = allocator.allocate(List.of(), bd(1000));
    assertThat(result).isEmpty();
  }

  @Test
  void zeroAmountNeededReturnsEmptyMap() {
    AccountSnapshot a = snap(AccountType.TAXABLE_BROKERAGE, 1000);
    Map<UUID, BigDecimal> result = allocator.allocate(List.of(a), BigDecimal.ZERO);
    assertThat(result).isEmpty();
  }

  @Test
  void negativeAmountNeededReturnsEmptyMap() {
    AccountSnapshot a = snap(AccountType.TAXABLE_BROKERAGE, 1000);
    Map<UUID, BigDecimal> result = allocator.allocate(List.of(a), bd(-500));
    assertThat(result).isEmpty();
  }

  @Test
  void allZeroBalancesReturnsEmptyMap() {
    AccountSnapshot a = snap(AccountType.TAXABLE_BROKERAGE, 0);
    AccountSnapshot b = snap(AccountType.SAVINGS, 0);
    Map<UUID, BigDecimal> result = allocator.allocate(List.of(a, b), bd(500));
    assertThat(result).isEmpty();
  }

  @Test
  void equalBalancesSplitEvenly() {
    AccountSnapshot a = snap(AccountType.TAXABLE_BROKERAGE, 5000);
    AccountSnapshot b = snap(AccountType.SAVINGS, 5000);

    Map<UUID, BigDecimal> result = allocator.allocate(List.of(a, b), bd(1000));

    assertThat(result.get(a.id())).isEqualByComparingTo("500");
    assertThat(result.get(b.id())).isEqualByComparingTo("500");
  }

  @Test
  void unequalBalancesSplitProportionally() {
    AccountSnapshot a = snap(AccountType.TAXABLE_BROKERAGE, 2000);
    AccountSnapshot b = snap(AccountType.TRADITIONAL_IRA, 8000);

    Map<UUID, BigDecimal> result = allocator.allocate(List.of(a, b), bd(1000));

    // 2000/10000 = 20% → 200; 8000/10000 = 80% → 800.
    assertThat(result.get(a.id())).isEqualByComparingTo("200");
    assertThat(result.get(b.id())).isEqualByComparingTo("800");
  }

  @Test
  void zeroBalanceAccountsAreIgnored() {
    AccountSnapshot a = snap(AccountType.TAXABLE_BROKERAGE, 0);
    AccountSnapshot b = snap(AccountType.SAVINGS, 1000);

    Map<UUID, BigDecimal> result = allocator.allocate(List.of(a, b), bd(500));

    assertThat(result).doesNotContainKey(a.id());
    assertThat(result.get(b.id())).isEqualByComparingTo("500");
  }

  @Test
  void overdrawDrainsEverything() {
    AccountSnapshot a = snap(AccountType.TAXABLE_BROKERAGE, 1000);
    AccountSnapshot b = snap(AccountType.SAVINGS, 500);

    // Asking for 5000 against 1500 total → both drain to 0, sum = 1500 (shortfall).
    Map<UUID, BigDecimal> result = allocator.allocate(List.of(a, b), bd(5000));

    assertThat(result.get(a.id())).isEqualByComparingTo("1000");
    assertThat(result.get(b.id())).isEqualByComparingTo("500");
  }

  @Test
  void totalAllocationEqualsAmountNeededWhenSufficient() {
    AccountSnapshot a = snap(AccountType.TAXABLE_BROKERAGE, 3000);
    AccountSnapshot b = snap(AccountType.TRADITIONAL_IRA, 7000);

    Map<UUID, BigDecimal> result = allocator.allocate(List.of(a, b), bd(2500));

    BigDecimal total = result.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    assertThat(total).isEqualByComparingTo("2500");
  }

  // --- Test helpers ---

  private static AccountSnapshot snap(AccountType type, long balance) {
    return new AccountSnapshot(UUID.randomUUID(), type, new BigDecimal(balance));
  }

  private static BigDecimal bd(long amount) {
    return new BigDecimal(amount);
  }
}
