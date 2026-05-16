-- Phase 5.2 Slice 2 — engine integration plumbing.
-- selling_cost_pct: fraction of gross sale price lost to realtor + closing costs (default 6%).
--   Stored per-property so the user can override (low-cost FSBO, expensive luxury market, etc).
-- scenario_properties: which of a profile's properties are included in a given scenario.
--   Mirrors scenario_accounts; ON DELETE CASCADE so removing a scenario or a property cleans up.
-- simulation_results truncated because YearlyProjection gains 7 new fields (mortgage interest,
--   property tax paid, housing expenses, sale proceeds, sale capital gains, property-value total,
--   itemized deduction). Same pattern as V003 / V005 / V007 / V008.
ALTER TABLE properties ADD COLUMN selling_cost_pct DECIMAL(19, 6) DEFAULT 0.06;

CREATE TABLE scenario_properties (
    scenario_id UUID NOT NULL,
    property_id UUID NOT NULL,
    CONSTRAINT fk_scenario_properties_scenario FOREIGN KEY (scenario_id) REFERENCES scenarios (id) ON DELETE CASCADE,
    CONSTRAINT fk_scenario_properties_property FOREIGN KEY (property_id) REFERENCES properties (id) ON DELETE CASCADE
);

DELETE FROM simulation_results;
