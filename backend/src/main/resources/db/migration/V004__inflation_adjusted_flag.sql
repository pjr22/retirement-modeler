-- Inflation-adjustment flag on benefit accounts and income sources.
-- Pensions (private defined-benefit plans, like the user's Raytheon pension) usually
-- have no COLA, so default false. Social Security does have a COLA — historical
-- average ~3.1% over the last decade, ~2.8% for 2026 — so existing SS accounts get
-- true. Income sources (salary, rental, side income) default true since most
-- nominal incomes track inflation; the user can uncheck for fixed streams.

ALTER TABLE accounts ADD COLUMN inflation_adjusted BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE accounts SET inflation_adjusted = TRUE WHERE account_type = 'SOCIAL_SECURITY';

ALTER TABLE income_sources ADD COLUMN inflation_adjusted BOOLEAN NOT NULL DEFAULT TRUE;
