-- Phase 3.6 follow-up: income sources are per-scenario, not per-profile.
-- Tweaking start dates, end dates, and amounts of pension / SS / employment / rental
-- streams is exactly what scenarios are for ("what if SS at 62 vs 70?"), so they belong
-- to a scenario, not the profile. Dev environment, no production data — wipe.

DELETE FROM income_sources;

DROP TABLE IF EXISTS scenario_income_sources;

ALTER TABLE income_sources DROP COLUMN profile_id;

ALTER TABLE income_sources ADD COLUMN scenario_id UUID NOT NULL;
ALTER TABLE income_sources
    ADD CONSTRAINT fk_income_sources_scenario
    FOREIGN KEY (scenario_id) REFERENCES scenarios (id) ON DELETE CASCADE;
