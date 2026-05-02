-- Phase 4 — Enhanced tax modeling.
-- Replaces the flat-rate tax model with progressive federal brackets, capital-gains rates,
-- and Social Security taxation (computed at runtime). Adds withdrawal-ordering strategy so
-- the engine can drain accounts in tax-aware order.

-- 1. Drop the legacy flat tax rate. Per Phase 4 plan + user direction, no backwards
--    compatibility is maintained — every scenario uses the new bracket-based path.
ALTER TABLE scenarios DROP COLUMN flat_tax_rate;

-- 2. Add the new ordering strategy. Backfill existing scenarios with PROPORTIONAL — the
--    one-tier proportional drawdown matches the legacy distributeWithdrawal behavior.
ALTER TABLE scenarios ADD COLUMN withdrawal_ordering_strategy VARCHAR(255);
UPDATE scenarios SET withdrawal_ordering_strategy = 'PROPORTIONAL';
ALTER TABLE scenarios ALTER COLUMN withdrawal_ordering_strategy SET NOT NULL;

-- 3. Custom-order side table. One row per (scenario, position), holding the AccountType to
--    drain at that position. Only populated when withdrawal_ordering_strategy = 'CUSTOM'.
CREATE TABLE scenario_custom_withdrawal_order (
    scenario_id UUID NOT NULL,
    position INT NOT NULL,
    account_type VARCHAR(255) NOT NULL,
    PRIMARY KEY (scenario_id, position),
    CONSTRAINT fk_scwo_scenario FOREIGN KEY (scenario_id) REFERENCES scenarios (id) ON DELETE CASCADE
);

-- 4. The YearlyProjection JSON shape gains six new fields (ordinaryIncome, capitalGains,
--    socialSecurityBenefit, taxableSocialSecurity, ordinaryTax, capitalGainsTax). Old rows
--    can't be backfilled; clear them so users re-run on the new model.
DELETE FROM simulation_results;
