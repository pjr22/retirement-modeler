package com.retirementmodeler.simulation.withdrawal;

import com.retirementmodeler.model.AccountType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Drains in a fixed tier order designed to minimize lifetime federal tax for a typical retiree:
 *
 * <ol>
 *   <li>Already-taxed money: {@link AccountType#TAXABLE_BROKERAGE}, {@link AccountType#SAVINGS}.
 *       Withdrawals are tax-free except for LTCG on realized gains.
 *   <li>Tax-deferred: {@link AccountType#TRADITIONAL_401K}, {@link AccountType#TRADITIONAL_IRA}.
 *       Withdrawals are taxed as ordinary income — defer as long as possible.
 *   <li>Tax-free: {@link AccountType#ROTH_401K}, {@link AccountType#ROTH_IRA}, {@link
 *       AccountType#HSA}. Withdrawals are tax-free; preserve for last so the largest tax-free
 *       compounding window is realized.
 * </ol>
 *
 * <p>Within a tier, accounts drain proportionally to balance — tax treatment is identical for
 * accounts of the same tier so there's no tax reason to favor one over another. Account types not
 * in the table above (none today, but defensive against future enum additions) get a final fallback
 * tier.
 *
 * <p>RMDs are <em>not</em> modeled here (Phase 5). A retiree subject to RMDs may need to pull from
 * tax-deferred accounts before this strategy would otherwise dictate; treat the strategy as a
 * best-effort heuristic until RMD logic lands.
 */
public class TaxOptimizedAllocator implements WithdrawalAllocator {

  private static final List<Set<AccountType>> TIERS =
      List.of(
          EnumSet.of(AccountType.TAXABLE_BROKERAGE, AccountType.SAVINGS),
          EnumSet.of(AccountType.TRADITIONAL_401K, AccountType.TRADITIONAL_IRA),
          EnumSet.of(AccountType.ROTH_401K, AccountType.ROTH_IRA, AccountType.HSA));

  @Override
  public Map<UUID, BigDecimal> allocate(List<AccountSnapshot> accounts, BigDecimal amountNeeded) {
    List<List<AccountSnapshot>> tiered = new ArrayList<>(TIERS.size() + 1);
    Set<AccountType> covered = EnumSet.noneOf(AccountType.class);
    for (Set<AccountType> tierTypes : TIERS) {
      tiered.add(accounts.stream().filter(a -> tierTypes.contains(a.type())).toList());
      covered.addAll(tierTypes);
    }
    // Defensive fallback: any AccountType not covered above gets drained last.
    tiered.add(accounts.stream().filter(a -> !covered.contains(a.type())).toList());
    return TieredDrain.drain(tiered, amountNeeded);
  }
}
