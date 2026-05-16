-- Phase 5.2 — Real Property additions:
--   mortgage_start_date + mortgage_term_years replace the prior "remaining term" UI input
--   (purchase-date + original-term is more natural to enter than "X years left"); P+I is derived
--   from current balance + rate + remaining months (= start + term − today).
--   planned_sale_date and post_sale_monthly_housing_cost capture the user's default downsize
--   plan; per-scenario overrides will come with PropertyDecision in Slice 2.
ALTER TABLE properties ADD COLUMN mortgage_start_date DATE;
ALTER TABLE properties ADD COLUMN mortgage_term_years INTEGER;
ALTER TABLE properties ADD COLUMN planned_sale_date DATE;
ALTER TABLE properties ADD COLUMN post_sale_monthly_housing_cost DECIMAL(19, 2) DEFAULT 0;
