-- Rename withdrawal_fixed_amount to withdrawal_monthly_amount and convert
-- previously-stored annual values to their monthly equivalents.
ALTER TABLE scenarios RENAME COLUMN withdrawal_fixed_amount TO withdrawal_monthly_amount;
UPDATE scenarios
SET withdrawal_monthly_amount = withdrawal_monthly_amount / 12
WHERE withdrawal_monthly_amount IS NOT NULL;

-- The YearlyProjection JSON shape changes (cumulatives -> per-year deltas,
-- adds a date field). Old simulation_results rows can't be deserialized into
-- the new record, so drop them; users can re-run simulations.
DELETE FROM simulation_results;
