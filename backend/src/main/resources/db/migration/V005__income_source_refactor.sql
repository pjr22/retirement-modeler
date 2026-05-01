-- Phase 3.6: unify pension/SS into IncomeSource entity, drop benefit-account fields,
-- rename withdrawal strategies for clarity, add scenario-level income source selection.
-- Dev environment, no production data — wipe affected rows rather than convert.

-- 1. Rename withdrawal strategy enum values stored in scenarios.
UPDATE scenarios SET withdrawal_strategy = 'PORTFOLIO_PERCENTAGE' WHERE withdrawal_strategy = 'FIXED_PERCENTAGE';
UPDATE scenarios SET withdrawal_strategy = 'CASHFLOW_TARGET'      WHERE withdrawal_strategy = 'FIXED_DOLLAR';

-- 2. Drop saved simulation results — strategy enums in their JSON are now stale.
DELETE FROM simulation_results;

-- 3. Delete pension / Social Security accounts (they become IncomeSource rows the
--    user re-enters via the new UI). scenario_accounts FK is ON DELETE CASCADE.
DELETE FROM accounts WHERE account_type IN ('PENSION', 'SOCIAL_SECURITY');

-- 4. Drop benefit-only fields from accounts.
ALTER TABLE accounts DROP COLUMN monthly_benefit;
ALTER TABLE accounts DROP COLUMN benefit_start_age;
ALTER TABLE accounts DROP COLUMN inflation_adjusted;

-- 5. Wipe and reshape income_sources. Promoted to a real entity with PK.
DELETE FROM income_sources;

ALTER TABLE income_sources DROP COLUMN annual_amount;
ALTER TABLE income_sources DROP COLUMN end_age;

ALTER TABLE income_sources ADD COLUMN type          VARCHAR(255)   NOT NULL;
ALTER TABLE income_sources ADD COLUMN monthly_amount DECIMAL(19, 2) NOT NULL;
ALTER TABLE income_sources ADD COLUMN start_date    DATE;
ALTER TABLE income_sources ADD COLUMN end_date      DATE;

ALTER TABLE income_sources ALTER COLUMN id SET NOT NULL;
ALTER TABLE income_sources ADD CONSTRAINT pk_income_sources PRIMARY KEY (id);

-- 6. Scenario-level income source selection (parallel to scenario_accounts).
CREATE TABLE scenario_income_sources (
    scenario_id       UUID NOT NULL,
    income_source_id  UUID NOT NULL,
    CONSTRAINT fk_scenario_income_sources_scenario      FOREIGN KEY (scenario_id)      REFERENCES scenarios      (id) ON DELETE CASCADE,
    CONSTRAINT fk_scenario_income_sources_income_source FOREIGN KEY (income_source_id) REFERENCES income_sources (id) ON DELETE CASCADE
);
