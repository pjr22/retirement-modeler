package com.retirementmodeler.simulation.withdrawal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Decides which savings accounts a withdrawal comes from. Allocators are pure functions of (the
 * current account snapshots, the dollar amount needed) and do not mutate state — the caller is
 * responsible for applying the returned per-account amounts to its working balances.
 */
public interface WithdrawalAllocator {

  /**
   * @param accounts the snapshot of all candidate accounts at the moment of the withdrawal. Empty
   *     or zero-balance accounts may be present; the allocator filters them out as needed.
   * @param amountNeeded the dollar amount the caller would like to withdraw. The allocator returns
   *     up to this amount in total — if savings can't cover it, the sum of returned values will be
   *     less than {@code amountNeeded}, and the caller is responsible for handling the shortfall.
   * @return a map from account id to the dollar amount to withdraw from that account. Accounts not
   *     in the map (or mapped to zero) get no withdrawal. No returned amount exceeds the account's
   *     snapshot balance.
   */
  Map<UUID, BigDecimal> allocate(List<AccountSnapshot> accounts, BigDecimal amountNeeded);
}
