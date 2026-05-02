package com.retirementmodeler.simulation.withdrawal;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shared draining logic for the {@link WithdrawalAllocator} implementations: walk through a list of
 * tiers in order; within each tier, drain accounts proportionally to balance until the tier is
 * empty or the amount needed is satisfied; advance to the next tier with whatever is left over.
 */
final class TieredDrain {

  private static final MathContext MC = new MathContext(16);
  private static final int OUTPUT_SCALE = 8;

  private TieredDrain() {}

  static Map<UUID, BigDecimal> drain(List<List<AccountSnapshot>> tiers, BigDecimal amountNeeded) {
    Map<UUID, BigDecimal> result = new HashMap<>();
    BigDecimal remaining =
        amountNeeded == null ? BigDecimal.ZERO : amountNeeded.max(BigDecimal.ZERO);
    if (remaining.signum() == 0) {
      return result;
    }

    for (List<AccountSnapshot> tier : tiers) {
      if (remaining.signum() == 0) {
        break;
      }
      List<AccountSnapshot> active = tier.stream().filter(a -> a.balance().signum() > 0).toList();
      if (active.isEmpty()) {
        continue;
      }
      BigDecimal tierTotal =
          active.stream().map(AccountSnapshot::balance).reduce(BigDecimal.ZERO, BigDecimal::add);

      if (tierTotal.compareTo(remaining) <= 0) {
        for (AccountSnapshot a : active) {
          result.merge(a.id(), scale(a.balance()), BigDecimal::add);
        }
        remaining = remaining.subtract(tierTotal);
      } else {
        for (AccountSnapshot a : active) {
          BigDecimal share = remaining.multiply(a.balance(), MC).divide(tierTotal, MC);
          result.merge(a.id(), scale(share), BigDecimal::add);
        }
        remaining = BigDecimal.ZERO;
      }
    }
    return result;
  }

  private static BigDecimal scale(BigDecimal value) {
    return value.setScale(OUTPUT_SCALE, RoundingMode.HALF_UP);
  }
}
