-- Phase 5 — Required Minimum Distributions.
-- The YearlyProjection JSON shape gains a new field (yearRmd) holding the per-year RMD amount
-- taken from Traditional accounts. Old rows can't be backfilled meaningfully, so clear them and
-- users re-run their scenarios on the new model. Mirrors the V003 / V005 / V007 pattern of
-- truncating simulation_results whenever the projection JSON shape changes.
DELETE FROM simulation_results;
