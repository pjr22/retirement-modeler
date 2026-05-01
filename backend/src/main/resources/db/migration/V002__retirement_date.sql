-- Replace integer planned_retirement_age with planned_retirement_date.
-- Existing rows get a date computed as (date_of_birth + planned_retirement_age years).

ALTER TABLE user_profiles ADD COLUMN planned_retirement_date DATE;

UPDATE user_profiles
SET planned_retirement_date = date_of_birth + (planned_retirement_age || ' years')::interval
WHERE date_of_birth IS NOT NULL;

ALTER TABLE user_profiles ALTER COLUMN planned_retirement_date SET NOT NULL;

ALTER TABLE user_profiles DROP COLUMN planned_retirement_age;
