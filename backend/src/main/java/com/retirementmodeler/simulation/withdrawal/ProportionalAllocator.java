package com.retirementmodeler.simulation.withdrawal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Distributes a withdrawal across all positive-balance accounts in proportion to their balance. The
 * legacy default — preserves the pre-Phase-4 simulation engine behavior.
 */
public class ProportionalAllocator implements WithdrawalAllocator {

  @Override
  public Map<UUID, BigDecimal> allocate(List<AccountSnapshot> accounts, BigDecimal amountNeeded) {
    return TieredDrain.drain(List.of(accounts), amountNeeded);
  }
}
