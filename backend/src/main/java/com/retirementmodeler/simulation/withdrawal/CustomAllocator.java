package com.retirementmodeler.simulation.withdrawal;

import com.retirementmodeler.model.AccountType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Drains accounts in the user-supplied {@link AccountType} order — one tier per type. Account types
 * not listed get drained last as a fallback tier, so a forgotten type doesn't silently suppress
 * withdrawals from those accounts. Within a tier (i.e. multiple accounts of the same type),
 * draining is proportional to balance.
 *
 * <p>Duplicates in the supplied order are folded — the first occurrence wins.
 */
public class CustomAllocator implements WithdrawalAllocator {

  private final List<AccountType> order;

  public CustomAllocator(List<AccountType> order) {
    this.order = order == null ? List.of() : List.copyOf(new LinkedHashSet<>(order));
  }

  @Override
  public Map<UUID, BigDecimal> allocate(List<AccountSnapshot> accounts, BigDecimal amountNeeded) {
    List<List<AccountSnapshot>> tiered = new ArrayList<>(order.size() + 1);
    Set<AccountType> covered = EnumSet.noneOf(AccountType.class);
    for (AccountType type : order) {
      tiered.add(accounts.stream().filter(a -> a.type() == type).toList());
      covered.add(type);
    }
    tiered.add(accounts.stream().filter(a -> !covered.contains(a.type())).toList());
    return TieredDrain.drain(tiered, amountNeeded);
  }
}
