# Retirement Modeler — Implementation Plan

A living plan. Update as work lands. `[x]` = done, `[ ]` = not done.

---

## Status

**Last updated:** 2026-05-01

**Current state:** Phases 0–3 + 3.5 + 3.6 complete. Phase 4 backend complete through 4.6 — federal bracket-based tax modeling, LTCG with stacking, IRS Pub. 915 SS taxability, three withdrawal-ordering strategies (PROPORTIONAL / TAX_OPTIMIZED / CUSTOM), engine integration, V007 migration, and round-trip API tests all green (127 backend tests). Frontend has a stopgap fix in `SimulationResultsPage` so the existing Tax column reads `row.yearTax` instead of the now-defunct `flatTaxRate × (income+withdrawals)` derivation. **Phase 4.7 (frontend rebuild — strategy picker, new tax columns, breakdown chart, lifetime-tax stat, drop flatTaxRate UI) is next.**

**Active work:** Phase 4.7 — frontend changes for the new tax model. See sub-plan below; HANDOFF.md has the open UI design questions.

**Deviations from original plan to keep in mind:**
- Phase 1's "in-memory storage" interim was skipped — went straight to Postgres + JPA + Flyway.
- Backend integration tests use H2 (`spring.jpa.hibernate.ddl-auto=create-drop`, Flyway disabled in tests). Original plan called for Testcontainers; this means our migrations are *not* exercised by the test suite. Plan to revisit in Phase 6 hardening.
- API paths still live under `/api/users/{profileId}/...` even though the entity is `UserProfile` (separate from the auth `User`). Internal Java/TypeScript names use `userProfileId`/`profileId`. Renaming the URL surface to `/api/profiles/...` is a noted follow-up; not blocking.

### Phase 3.5 — UX & correctness cleanup pass ✅ (May 2026)

Completed as a single sweep before starting Phase 4:

- [x] `UserProfile.plannedRetirementAge` (int) → `plannedRetirementDate` (LocalDate). Migration `V002` populates from `dateOfBirth + plannedRetirementAge years`. Profile detail UI shows full date ("22 October 2031"); other UI surfaces use month-year ("OCT 2031") and assume the 1st of the month.
- [x] Scenario editor: when creating a new scenario, all profile accounts are selected by default. Added "Select all" / "Deselect all" buttons.
- [x] Simulation engine refactored from yearly to **monthly** inner loop. Returns compound monthly (deterministic uses `(1+r)^(1/12)−1`; Monte Carlo samples monthly with `μ_m = μ_y/12`, `σ_m = σ_y/√12`). Withdrawals applied monthly; tax aggregated to year-deltas.
- [x] `withdrawalFixedAmount` (annual) → `withdrawalMonthlyAmount` (monthly). Migration `V003` renames column and divides existing values by 12 to preserve semantics.
- [x] `YearlyProjection` reshape: dropped `int year`, added `LocalDate date`; renamed cumulative `total*` fields to per-year delta `year*`; renamed `totalBalance → balance`. Rows are anchored to retirement month each year (e.g. `OCT 2026, OCT 2027, …`). Migration `V003` clears old `simulation_results` rows since the JSON shape changed.
- [x] Year-by-year table: added "Date" column (MMM YYYY format), shows per-year deltas instead of running totals.
- [x] Chart improvements: shades-of-green percentile bands (now visually distinct), readable legend with hover-tooltips per series and click-to-toggle, fixed Y-axis label positioning so axis values aren't overlapped.
- [x] All backend tests passing (`./gradlew backend:check` — 40 tests, including 11 isolation tests). Frontend `npm run build`, `npm run test` (24 tests), and `npm run lint` all green.
- [x] **JSR-310 fix for Hibernate JSON columns** — added `HibernateJsonFormatMapperConfig` to wire Spring's auto-configured Jackson `ObjectMapper` (which has `JavaTimeModule`) into Hibernate's `hibernate.type.json_format_mapper` setting. Without this, the JSONB-stored `YearlyProjection` round-trip fails on PostgreSQL because the private ObjectMapper Hibernate constructs lacks JSR-310 support. H2 tests didn't surface this — another reason the Phase 6 Testcontainers switch matters.
- [x] **BigDecimal scale-explosion fix in `SimulationEngine`** — Java's `MathContext` bounds *precision* (significant digits) but not *scale* (decimal places). For zero-valued operands, `multiply` preserves scale, so a zero-balance account multiplied by a high-scale growth factor each month accrues ~18 fractional digits per iteration. Over 360 monthly iterations × multiple accounts, scale eventually breaches PostgreSQL's `numeric` 16,383-fractional-digit limit and JSONB round-trip fails (manifests as a vague "Could not deserialize string" 400 on the simulate endpoint). Fix: enforce `setScale(8, HALF_UP)` after every BigDecimal arithmetic step on balances/aggregates/inflationFactor; output values are bounded to `setScale(2, HALF_UP)`. This both fixes the bug and produces cleaner output JSON.
- [x] **Withdrawal cap fix in `SimulationEngine`** — `yearWithdrawals` was tracking the *requested* monthly amount rather than the *actual* outflow. In depleted years a fixed-dollar strategy could request $15K against a $1K balance and the engine would record $15K, producing impossible-looking withdrawals against $0 balances in the year-by-year output. Fix: cap `monthWithdrawal = requested.min(totalBalanceNow)` before recording. The frontend table now also caps non-deterministic withdrawals at `previousSeriesBalance + income + contributions` so percentile-band displays match what would really happen if a trial followed that trajectory.
- [x] **Inflation-adjustment flag on benefit accounts and income sources** — pensions (private DB plans) typically have no COLA; Social Security does (~3% historical average, 2.8% for 2026). Added `inflationAdjusted` boolean on `Account` (only meaningful for `PENSION` / `SOCIAL_SECURITY`) and `IncomeSource`. Migration `V004` defaults SS to `true`, all other existing accounts to `false`, and existing income sources to `true`. `SimulationEngine` honors the flag — only multiplies by `inflationFactor` when set. Frontend account form shows a checkbox for benefit accounts (smart default: SS=true, pension=false); profile detail page exposes the flag per income source.
- [x] **Next-month transition rule** — retirement transitions and benefit-start ages now take effect at the start of the *next full month* after the trigger date (or that month if the trigger is exactly the 1st). So a planned retirement date of Oct 22 means retirement starts Nov 1; a pension that begins at age 60 first pays in November of the year the user turns 60. Replaces the prior `withDayOfMonth(1)`-rounding-down behavior, which made the retirement-month row look "half retired." End-of-month age display is unchanged — the row still says "60" in October of the user's 60th-birthday year, but the financial events for that month are pre-retirement.
- [x] **Income-first FIXED_DOLLAR + pension/SS as direct income** — pension and Social Security `monthlyBenefit` is no longer deposited into the account's balance. Instead it's paid as direct income to the user (tracked in `yearIncome`). The FIXED_DOLLAR withdrawal strategy is now cashflow-target semantics: the configured monthly amount is what the user wants to live on; savings only fill the gap between target × inflation and incoming income. If income meets or exceeds the target, savings withdrawal is zero (option (a) — surplus is unused, not banked). FIXED_PERCENTAGE remains "withdraw N% of savings" with income paid on top. Knock-on benefits: pension/SS account balances stay at zero forever (no fake accumulation from proportional-withdrawal underdraining); the year-by-year `yearWithdrawals` reports actual savings drain, not pension passthrough; tax base double-counting is eliminated. The frontend table re-derives non-deterministic `withdrawals` from the strategy + that series' balance + (for FIXED_DOLLAR) inflation factor and income, and caps at savings-only available cash (`previousSeriesValue + contributions`).

**Deferred (with explicit owners):**
- (none — Phase 3.6 below absorbs the income-source refactor)

---

## Phase 3.6 — Unified income streams + Social Security earnings test 🚧 (in progress)

**Goal:** Replace the dual "pension/SS as Account, salary as embedded IncomeSource" model with a single first-class `IncomeSource` entity (per scenario) that supports arbitrary start/end dates and types (employment, pension, Social Security, rental, etc.). Model the SSA earnings test (pre-FRA benefit reduction + actuarial recoup at FRA) on monthly boundaries. Rename withdrawal strategies for clarity.

### Why now (before Phase 4)
Phase 4's tax modeling needs accurate per-source income categorization (ordinary vs. SS-provisional vs. earned). The existing model conflates pension/SS with accounts and ignores any non-salary income post-retirement, so users can't model rental income or part-time work — and the empty "Income Sources" section on the profile detail page reflects this gap. Easier to fix the income model now than to layer tax logic on top of a broken one.

### Domain model
- [x] Promote `IncomeSource` to a top-level `@Entity` with its own table and PK (closes a Phase 6 cleanup item).
- [x] New `IncomeType` enum: `EMPLOYMENT`, `SELF_EMPLOYMENT`, `PENSION`, `SOCIAL_SECURITY`, `RENTAL`, `OTHER`. EMPLOYMENT/SELF_EMPLOYMENT are "earned income" for SS earnings-test purposes.
- [x] `IncomeSource` shape: `{id, scenarioId, name, type, monthlyAmount, startDate (nullable=from-now), endDate (nullable=until-death), inflationAdjusted}`. **Income sources belong to a scenario, not a profile** — varying start/end dates and amounts of pension / SS / part-time work is exactly what scenarios exist to compare ("what if SS at 62 vs 70?").
- [x] Drop from `Account`: `monthlyBenefit`, `benefitStartAge`, `inflationAdjusted`. Pension/SS are no longer accounts.
- [x] Drop from `AccountType` enum: `PENSION`, `SOCIAL_SECURITY`.
- [x] Rename `WithdrawalStrategy` values: `FIXED_PERCENTAGE` → `PORTFOLIO_PERCENTAGE` (clear: "% of current savings"), `FIXED_DOLLAR` → `CASHFLOW_TARGET` (clear: "monthly budget filled by income first, then savings").

### Migrations V005 + V006
This is a dev environment, no production data — wipe affected rows rather than write data-conversion logic.
- [x] V005: `accounts` — drop columns `monthly_benefit`, `benefit_start_age`, `inflation_adjusted`; delete rows with `account_type IN ('PENSION', 'SOCIAL_SECURITY')`.
- [x] V005: `income_sources` — drop `annual_amount`, `end_age`; add `id` (PK), `type`, `monthly_amount`, `start_date`, `end_date`; truncate.
- [x] V005: `scenarios` — rename strategy values (`FIXED_PERCENTAGE` → `PORTFOLIO_PERCENTAGE`, `FIXED_DOLLAR` → `CASHFLOW_TARGET`); truncate `simulation_results`.
- [x] V006: `income_sources` — drop `profile_id`, add `scenario_id` with FK to `scenarios` (`ON DELETE CASCADE`); drop the (V005-created and now-unused) `scenario_income_sources` join table. Wipe `income_sources` again — they get re-entered per scenario.

### API surface
Mirror the scenario hierarchy — IncomeSources are owned by a scenario; ownership validated through scenario → profile → owner.
- [x] `POST /api/scenarios/{scenarioId}/incomeSources` — create
- [x] `GET /api/scenarios/{scenarioId}/incomeSources` — list for scenario
- [x] `PUT /api/incomeSources/{id}` — update
- [x] `DELETE /api/incomeSources/{id}` — delete

### SimulationEngine changes

**Income loop**: for each `IncomeSource` belonging to the scenario, if `startDate ≤ currentMonth ≤ endDate` (NULL bounds = open), add `monthlyAmount × (inflationAdjusted ? inflationFactor : 1)` to `monthIncome`. No more pre-retirement-only restriction. No more reading `Account.monthlyBenefit`.

**SS earnings test** (modeled per-month, mirroring SSA mechanics):
- Compute Full Retirement Age (FRA) from DOB via standard SSA lookup table (1960+ → 67).
- Earnings-test threshold constants (2025): under-FRA = $23,400; year-of-FRA = $62,160. **Both inflated by simulation `inflationFactor` each year** (SSA wage-indexes them annually).
- At each calendar-year start (or simulation start mid-year):
  1. Project the year's earned income (sum over EMPLOYMENT + SELF_EMPLOYMENT sources active in that year, accounting for partial-year activity and per-month inflationFactor).
  2. Project the year's SS benefit (sum over SOCIAL_SECURITY sources active in that year).
  3. Determine year regime: under-FRA-all-year / year-of-FRA / at-or-after-FRA.
  4. Compute annual reduction: under-FRA → `max(0, earned − threshold) × 0.5`; year-of-FRA → only earnings before FRA-month count, against year-of-FRA threshold, at $1/$3. At/after FRA → 0.
  5. Cap reduction at year's projected SS. Set `ssWithholdRemaining = reduction`.
- Each month, when paying SS, withhold `min(ssWithholdRemaining, thisMonthSS)`; subtract from `monthIncome`; decrement queue; accumulate `cumulativeWithheldPreFRA`.
- At the FRA month, switch on a permanent monthly bonus = `cumulativeWithheldPreFRA / monthsRemainingToDeath`. (Actuarial recoup approximation.)

**Withdrawal strategies**: rename in switch statements; semantics unchanged (CASHFLOW_TARGET still nets income; PORTFOLIO_PERCENTAGE still draws % of savings with income on top).

### Frontend
- [x] `ProfileDetailPage`: no Income Sources section (income is per-scenario).
- [x] `AccountForm`: remove PENSION / SOCIAL_SECURITY from type dropdown; remove `monthlyBenefit`, `benefitStartAge`, `inflationAdjusted` fields.
- [x] `ScenarioDetailPage`: full IncomeSource CRUD section (table + add/edit dialog with type / monthly amount / start & end dates / inflation flag), tied to the scenario. Strategy dropdown shows PORTFOLIO_PERCENTAGE / CASHFLOW_TARGET with helper text.
- [x] `SimulationResultsPage`: updated all user-facing references to old strategy names.

### Tests
- [x] Backend: IncomeSourceController CRUD tests (auth isolation included).
- [x] Backend: SimulationEngine tests covering — income source applies between dates, doesn't apply outside dates; pension as IncomeSource flows correctly; SS earnings test reduces benefit pre-FRA when earned income exceeds threshold; FRA recoup fully repays the withholding over remaining lifetime; post-FRA earned income produces no reduction; CASHFLOW_TARGET vs PORTFOLIO_PERCENTAGE behavior.
- [x] Frontend: IncomeSource CRUD on ScenarioDetailPage; updated strategy dropdown labels.

### Known simplifications (documented, not fixed)
- SSA's actual recoup is a benefit recalc using delayed-retirement-credit-style tables; we approximate with `withheld / months_remaining_to_death`. Close enough for planning.
- First partial year of simulation uses the annual rule from sim start date (we don't apply SSA's "first year of retirement" monthly-test rule, which is itself an exception users rarely hit).
- Cloning a scenario (Phase 5) will need to deep-copy its income sources. Not implemented now.

---

## Phase 0 — Project Scaffolding ✅

**Goal:** Establish the monorepo structure, build tooling, and development workflow.

- [x] Backend Gradle subproject with Spring Boot 3.4.4, Java 21
- [x] Gradle dependency management via `gradle/libs.versions.toml`
- [x] Spotless + Google Java Format on the backend
- [x] Frontend scaffolded with Vite + React 19 + TypeScript + MUI v9
- [x] Prettier + ESLint on the frontend
- [x] CORS for local dev (backend on :8080, frontend on :5173) — note: only configured under the `dev` profile via `DevCorsConfig`
- [x] `.editorconfig`
- [x] Health check endpoint (`GET /api/health`) + frontend `<HealthStatus>` chip
- [ ] **Deferred to Phase 6:** make `frontend/` a true Gradle subproject (today it builds via npm/Vite, which is fine for now)
- [ ] **Deferred to Phase 6:** Testcontainers for backend integration tests (currently H2)

---

## Phase 1 — Domain Model & Core API ✅

**Goal:** Define the financial domain model and expose a REST API for scenario input.

### Domain model (backend) ✅
- [x] `User` (auth) — email, bcrypt-hashed password, createdAt
- [x] `UserProfile` — date of birth, planned retirement age, life expectancy, filing status, income sources
- [x] `Account` (single table with `accountType` discriminator) supporting Traditional 401k/IRA, Roth 401k/IRA, taxable brokerage, savings, HSA, pension, social security
- [x] `IncomeSource` (embedded in profile) — name, annualAmount, endAge
- [x] `Scenario` — name, description, account-id list, embedded `SimulationAssumptions`
- [x] `SimulationAssumptions` — expected return, inflation, withdrawal strategy + amount/percentage, std dev, MC trial count, flat tax rate

### REST API endpoints ✅

Auth:
- [x] `POST /api/auth/register`, `POST /api/auth/login`

Profiles (paths use `/api/users/...` for historical reasons; will rename in a later sweep):
- [x] `POST /api/users`, `GET /api/users/{id}`, `GET /api/users`, `PUT /api/users/{id}`, `DELETE /api/users/{id}`

Accounts:
- [x] `POST /api/users/{profileId}/accounts`, `GET /api/users/{profileId}/accounts`, `PUT /api/accounts/{id}`, `DELETE /api/accounts/{id}`

Scenarios:
- [x] `POST /api/users/{profileId}/scenarios`, `GET /api/users/{profileId}/scenarios`, `GET /api/scenarios/{id}`, `PUT /api/scenarios/{id}`, `DELETE /api/scenarios/{id}`

Simulations:
- [x] `POST /api/scenarios/{scenarioId}/simulate`, `GET /api/simulations/{id}`, `GET /api/users/{profileId}/simulations`

### Frontend ✅
- [x] MUI layout (AppBar, sidebar-style nav via Layout)
- [x] Profile create/list/edit/delete
- [x] Account CRUD with type-specific fields (contribution vs benefit)
- [x] Scenario editor (account selection + assumptions)

---

## Phase 2 — Simulation Engine ✅

**Goal:** Deterministic and Monte Carlo retirement projections.

- [x] Deterministic year-by-year projection (returns, inflation, contributions pre-retirement, withdrawals post-retirement, flat-rate tax)
- [x] Monte Carlo: configurable trial count (default 1,000, capped at 10,000), normal-distribution returns parameterized by mean + std dev
- [x] Aggregate output: success rate, median years of survival, P10/P25/P50/P75/P90 percentile balances per year
- [x] Withdrawal strategies: `FIXED_PERCENTAGE` (e.g. 4% rule), `FIXED_DOLLAR` (inflation-adjusted)
- [ ] Dynamic / guard-rails withdrawal — deferred (Phase 4 stretch or later)
- [x] `POST /api/scenarios/{id}/simulate`, `GET /api/simulations/{id}`, `GET /api/users/{profileId}/simulations`
- [x] Frontend results dashboard: stat cards, percentile-band chart, collapsible year-by-year table

---

## Phase 3 — Persistence & User Management ✅

**Goal:** Database + auth so users can save and return.

- [x] PostgreSQL (production) + H2 (test) configuration
- [x] Spring Data JPA entities for all domain objects
- [x] Flyway migration `V001__initial_schema.sql`
- [x] Spring Security with stateless JWT (`JwtUtil`, `JwtAuthFilter`, `CustomUserDetailsService`)
- [x] `POST /api/auth/register`, `POST /api/auth/login`
- [x] `@AuthenticationPrincipal` wired through controllers; ownership validated per request
- [x] Bean Validation on register/login DTOs (`@Email`, `@NotBlank`, `@Size`)
- [x] Frontend login/register pages, auth context, protected routes, JWT in localStorage, automatic 401-redirect
- [x] **Cleanup pass (post-original-Phase-3):** authorization closed on three formerly-unprotected read paths; `userId` (where it meant profile id) renamed to `userProfileId`/`profileId`; multi-user isolation tests added (`AuthorizationIsolationTest`, 11 cases); frontend `npm run build` fixed (axios mock typing, MUI Typography, Recharts formatter)

### Known issues to fix in Phase 6 (production hardening)
- JWT secret committed to source — needs to come from env / secret manager
- `SecurityConfig` requires a `CorsConfigurationSource` bean that only `DevCorsConfig` (`@Profile("dev")`) provides — non-dev profiles would fail to start
- JWT lives in localStorage (XSS-exposed) — switch to httpOnly cookie or in-memory + refresh
- `MonteCarloEngine.computeMedianYearsOfSurvival` would NPE on empty trial list

---

## Phase 4 — Enhanced Tax Modeling & Withdrawal Optimization 🚧 (4.1–4.6 done; 4.7 next)

**Goal:** Replace the flat-rate tax model with federal marginal brackets, capital-gains rates, and Social Security taxation rules. Add tax-aware withdrawal ordering and surface the resulting tax breakdown to users.

### Why this matters
The pre-Phase-4 engine used `flatTaxRate × (income + withdrawals)`, which ignored:
- progressive ordinary-income brackets,
- the lower long-term capital gains rate,
- tax-free Roth distributions,
- the IRS provisional-income test for Social Security taxability,
- the standard deduction.

Tax-aware withdrawal ordering can change a 30-year retirement's lifetime tax bill by tens of thousands of dollars, so the projection that drives user decisions needs to model it.

### Sub-phase 4.1 — Tax bracket data model ✅
- [x] `tax/` package under `com.retirementmodeler`.
- [x] `BracketTier` record `(BigDecimal threshold, BigDecimal rate)`.
- [x] `TaxBrackets` value object — ordinary brackets per `FilingStatus`, LTCG brackets per `FilingStatus`, standard deduction per `FilingStatus`, base tax year.
- [x] **`FederalTaxBrackets2026`** (renamed from the planned `2025`) — sourced from IRS Rev. Proc. 2025-32, which incorporates the One Big Beautiful Bill Act amendments that made the TCJA 7-tier rate structure permanent in July 2025. All four filing statuses populated.
- [x] `TaxBracketProvider` Spring bean with `bracketsForYear(int year, BigDecimal cumulativeInflationFactor)` — scales thresholds + standard deduction by the cumulative inflation factor; rates unchanged; lowest-tier $0 threshold preserved.
- [x] 8 unit tests.

### Sub-phase 4.2 — Tax calculator ✅
- [x] `TaxCalculator` Spring component with `compute(status, ordinaryIncome, longTermCapitalGains, brackets) → TaxResult`.
- [x] `TaxResult` record `(ordinaryTaxableIncome, ordinaryTax, capitalGainsTax, totalTax, effectiveRate, marginalRate)`.
- [x] Standard deduction applied to ordinary income first; **deduction "spills over" onto LTCG when ordinary alone is below deduction** (matches IRS Qualified Dividends & Capital Gains Tax Worksheet exactly — important for retirees in Roth+brokerage-heavy years).
- [x] LTCG stacked on top of taxable ordinary income.
- [x] 14 unit tests covering all four filing statuses, bracket edges, mid-bracket, LTCG split across tiers, deduction-absorbs-LTCG case.

### Sub-phase 4.3 — Social Security taxation ✅
- [x] `SocialSecurityTaxer` Spring component implementing IRS Pub. 915 Worksheet 1 with **tier-1 carryover** in the 85% formula (the part most implementations get wrong).
- [x] Thresholds: Single/HoH $25K/$34K; MFJ $32K/$44K. **MFS defaults to "lived apart all year" (= Single thresholds)**; the "lived together at any time" case isn't modeled.
- [x] Statutory thresholds — never inflation-adjusted.
- [x] 17 unit tests covering all tiers × all filing statuses + a property-style sweep verifying the [0, 0.85·SS] invariant.

### Sub-phase 4.4 — Withdrawal ordering strategies ✅
- [x] `WithdrawalOrderingStrategy { PROPORTIONAL, TAX_OPTIMIZED, CUSTOM }` enum in `model/`.
- [x] `simulation/withdrawal/` package with `AccountSnapshot` record, `WithdrawalAllocator` interface, and three implementations.
- [x] `ProportionalAllocator` — single-tier proportional split (preserves legacy behavior).
- [x] `TaxOptimizedAllocator` — fixed tiers `[TAXABLE_BROKERAGE, SAVINGS] → [TRADITIONAL_401K, TRADITIONAL_IRA] → [ROTH_401K, ROTH_IRA, HSA]` with a defensive fallback tier for any future enum additions. **RMDs not modeled — Phase 5.**
- [x] `CustomAllocator` — user-supplied `List<AccountType>` order; missing types fall to a final tier (so a forgotten type doesn't silently suppress withdrawals); duplicates folded.
- [x] Shared `TieredDrain` package-private helper.
- [x] 23 unit tests across the three allocators.

### Sub-phase 4.5 — Engine wiring ✅
- [x] **V007 migration** bundled here (rather than in 4.6) so the running app survives the schema change. Drops `flat_tax_rate`; adds `withdrawal_ordering_strategy` (backfilled `PROPORTIONAL`); creates `scenario_custom_withdrawal_order` join table; truncates `simulation_results`.
- [x] `SimulationAssumptions` — `flatTaxRate` removed entirely (no backwards-compat path per user direction); `withdrawalOrderingStrategy` + `customWithdrawalOrder` fields added with JPA mapping (`@ElementCollection` + `@OrderColumn` for the ordered list). Field-level defaults so Jackson's no-arg-constructor-then-setters deserialization gets PROPORTIONAL even when the JSON omits the field; setters null-coalesce.
- [x] `YearlyProjection` extended with `yearOrdinaryIncome`, `yearCapitalGains`, `yearSocialSecurityBenefit`, `yearTaxableSocialSecurity`, `yearOrdinaryTax`, `yearCapitalGainsTax`. `yearTax` now equals their sum.
- [x] `SimulationEngine` refactored: constructor-injects the three tax components; all public methods take `FilingStatus`; per-month tracking of non-SS income, gross SS, traditional-withdrawal ordinary, brokerage-withdrawal LTCG; year-end pipeline `SocialSecurityTaxer → TaxBracketProvider → TaxCalculator`.
- [x] **Withdrawal categorization**: `TRADITIONAL_*` → ordinary; `TAXABLE_BROKERAGE` → LTCG (100% gains assumed since basis isn't tracked); `ROTH_*`/`HSA`/`SAVINGS` → tax-free for projection purposes (savings interest is technically ordinary income; the simplification slightly under-taxes savings — acceptable for MVP given typical balances).
- [x] `MonteCarloEngine` and `SimulationService` thread `FilingStatus` from `UserProfile`.
- [x] 7 new engine integration tests (Roth tax-free, Traditional ordinary, Brokerage LTCG, Savings tax-free, SS 85% taxability, TAX_OPTIMIZED ordering, PROPORTIONAL vs TAX_OPTIMIZED diff).
- [x] **Year-alignment caveat documented**: rows are anchored to retirement month, so the 12-month aggregation window is e.g. Nov–Oct rather than Jan–Dec; brackets still looked up by calendar year. ≤3 months of income placed in the wrong calendar year — small relative to other approximations.

### Sub-phase 4.5 follow-up — frontend stopgap fix
- [x] `SimulationResultsPage.tsx` Tax column was deriving `(income+withdrawals) × flatTaxRate` client-side, which now resolves to 0 (flatTaxRate gone). **Fixed**: deterministic series reads `row.yearTax` directly; non-deterministic series scales by an effective rate derived from the deterministic row. **The "Flat Tax Rate" line in the assumptions table still shows "0%" — replaced properly in 4.7.**

### Sub-phase 4.6 — Persistence & API round-trip ✅
- [x] V007 migration (already done in 4.5).
- [x] `SimulationAssumptions` JPA mapping for new fields (already done in 4.5).
- [x] **Audit confirmed no DTO layer** — controllers take/return `Scenario` directly via Jackson; new fields surface via getters automatically.
- [x] Cleaned `flatTaxRate` from controller-test JSON (Jackson silently ignored it but it was misleading).
- [x] Added 3 round-trip tests in `ScenarioControllerTest`: TAX_OPTIMIZED creation, CUSTOM with `customWithdrawalOrder` list, default-to-PROPORTIONAL when client omits the field.
- [x] **Bug caught and fixed**: Jackson uses no-arg constructor + setters when deserializing, so the public constructor's "default to PROPORTIONAL" never fired for API requests. Fixed by initializing the fields at declaration and having the setters null-coalesce.

### Sub-phase 4.7 — Frontend changes (next session)
- [ ] Scenario editor: add "Withdrawal Ordering" section with strategy dropdown. When `CUSTOM`, show an account-type ordering picker (drag-and-drop vs simple up/down arrows — open UI decision).
- [ ] Remove the `flatTaxRate` input from the scenario form — it's now derived. Replace with a read-only "Filing status drives federal brackets" hint linking to the profile page.
- [ ] Replace the assumptions-table "Flat Tax Rate" row with a filing-status row.
- [ ] Update `frontend/src/types.ts`: drop `flatTaxRate` from `SimulationAssumptions`; add `withdrawalOrderingStrategy`, `customWithdrawalOrder`; extend `YearlyProjection` with the six new tax fields.
- [ ] `SimulationResultsPage`:
  - Replace the stopgap Tax column with separate Ordinary Tax + Capital Gains Tax columns (sourced from `row.yearOrdinaryTax` / `row.yearCapitalGainsTax`).
  - Possibly add Ordinary Income + Capital Gains breakdown columns.
  - New "Tax Breakdown" stacked-bar chart per year (ordinary + capital-gains tax).
  - Stat card: lifetime tax paid.
- [ ] Update frontend tests across `ScenarioDetailPage.test.tsx`, `ScenariosPage.test.tsx`, `SimulationResultsPage.test.tsx`, `ProfilesPage.test.tsx`, `ProfileDetailPage.test.tsx`, `AccountsPage.test.tsx` — anywhere that mocks API responses with `flatTaxRate`.
- [ ] Manual browser test against running backend.

### Sub-phase 4.8 — Test coverage and validation
- [ ] Backend integration test: realistic scenario (e.g. $1M traditional IRA, $500K Roth, $200K taxable, MFJ, $30K SS) → verify total tax differs meaningfully (>10%) between PROPORTIONAL and TAX_OPTIMIZED.
- [x] Backend unit tests for each tax component (4.1–4.4) listed above.
- [ ] Frontend test for the new scenario editor section and results columns.
- [ ] Manual sanity check: simulate, compare year-by-year output against a hand-calculated tax for one or two years.

### Out of scope for Phase 4 (intentional)
- State income tax (federal-only for now)
- Cost-basis tracking on taxable accounts (assume 100% gains; will be a known approximation)
- Required Minimum Distributions (RMDs) — Phase 5 candidate
- IRMAA / Medicare premium tiers — Phase 7
- Itemized deductions — only standard deduction in Phase 4
- Side-by-side strategy comparison UI — that's Phase 5

**Deliverable:** Simulation results show federal tax computed from real brackets, distinguishing ordinary vs capital-gains income and taxable Social Security; users can choose `TAX_OPTIMIZED` or `CUSTOM` withdrawal ordering; the year-by-year table and a new chart show the tax breakdown.

---

## Phase 5 — Scenario Comparison & Reporting

**Goal:** Allow users to compare multiple scenarios and export results.

### Scenario comparison
- [ ] Side-by-side comparison view for 2–4 scenarios
- [ ] Overlay charts: portfolio balance, success probability, lifetime tax burden
- [ ] "What-if" mode: clone a scenario, tweak one parameter, re-run
- [ ] Required Minimum Distribution (RMD) handling — withdrawals from pre-tax accounts at age 73+

### Reporting
- [ ] Export simulation results as PDF
- [ ] Export raw data as CSV
- [ ] Optional: shareable read-only link for advisor review

**Deliverable:** Users can compare scenarios visually and generate shareable reports.

---

## Phase 6 — Production Readiness

**Goal:** Harden for production deployment.

### Backend
- [ ] Containerize with Docker (multi-stage build)
- [ ] Structured JSON logging
- [ ] Micrometer + Prometheus endpoint
- [ ] Rate limiting on API endpoints
- [ ] CSRF protection / security headers
- [ ] Move JWT secret to env / secret manager
- [ ] Move JWT from localStorage to httpOnly cookie (or in-memory + refresh)
- [ ] Production-profile CORS bean (so app starts without `dev` profile)
- [ ] Switch backend integration tests from H2 to Testcontainers (so V001+ migrations are exercised)
- [ ] Fix `MonteCarloEngine.computeMedianYearsOfSurvival` empty-list NPE
- [ ] Database backups strategy
- [ ] CI/CD pipeline (GitHub Actions)

### Frontend
- [ ] Production build optimization (current bundle is ~930kB — code-split)
- [ ] Error boundaries
- [ ] WCAG 2.1 AA accessibility audit
- [ ] Responsive design review (mobile / tablet)
- [ ] E2E tests (Playwright)

### Infrastructure
- [ ] Kubernetes or Docker Compose deployment
- [ ] Environment-based configuration (dev / staging / prod)
- [ ] SSL termination, reverse proxy (nginx)
- [ ] Health checks / readiness probes

### Optional cleanup
- [ ] Rename URL paths from `/api/users/{profileId}/...` to `/api/profiles/{profileId}/...` for clarity (will require frontend update)

**Deliverable:** Application can be deployed to a cloud environment with monitoring, logging, and automated CI/CD.

---

## Phase 7 — Future Enhancements (Backlog)

Not scheduled. Prioritized based on user feedback.

- [ ] Plaid / financial-institution integration
- [ ] Advanced Monte Carlo: fat-tailed distributions, sequence-of-returns risk
- [ ] Social Security claiming-age optimization
- [ ] Healthcare cost modeling (Medicare premiums, IRMAA tiers, long-term care)
- [ ] Estate / inheritance modeling
- [ ] Multi-currency support
- [ ] Joint retirement planning for couples
- [ ] PWA / mobile
- [ ] Advisor portal (white-label, multi-client)

---

## Technology Stack (current)

| Layer            | Technology                                    |
|------------------|-----------------------------------------------|
| Language         | Java 21                                       |
| Framework        | Spring Boot 3.4.4                             |
| Build            | Gradle 8.10.2 (multi-project — backend only)  |
| Formatter        | Spotless + Google Java Format                 |
| Database         | PostgreSQL (prod), H2 (test)                  |
| ORM              | Spring Data JPA + Hibernate                   |
| Migrations       | Flyway                                        |
| Auth             | Spring Security + JWT (jjwt 0.12)             |
| Testing (BE)     | JUnit 5, Spring Boot Test, MockMvc            |
| Frontend         | React 19 + TypeScript 6                       |
| Build (FE)       | Vite 8                                        |
| UI Library       | MUI v9 + Emotion                              |
| Charts           | Recharts                                      |
| HTTP Client      | Axios                                         |
| Routing          | react-router 7                                |
| Testing (FE)     | Vitest 4, React Testing Library, jsdom        |
| Linting (FE)     | ESLint 10 + typescript-eslint                 |
| Formatter (FE)   | Prettier                                      |

---

## Phasing Timeline (revised estimate)

| Phase | Description                              | Status                | Estimate              |
|-------|------------------------------------------|-----------------------|-----------------------|
| 0     | Project Scaffolding                      | ✅ done                | —                     |
| 1     | Domain Model & Core API                  | ✅ done                | —                     |
| 2     | Simulation Engine                        | ✅ done                | —                     |
| 3     | Persistence & User Management            | ✅ done (cleanup done) | —                     |
| 4     | Enhanced Tax & Withdrawal Optimization   | 🚧 next                | 2–3 weeks             |
| 5     | Scenario Comparison & Reporting          | ⏳ planned             | 2–3 weeks             |
| 6     | Production Readiness                     | ⏳ planned             | 2–3 weeks             |
| 7+    | Future Enhancements                      | ♾  backlog             | ongoing               |

> **MVP gate (Phases 0–2):** users could model retirement and see projections without persistence. **Persistent product gate (Phase 3):** users can save and return. **Decision-grade tax gate (Phase 4):** projections are credible enough for users to make actual withdrawal-strategy decisions on. Phases 5–6 mature the product toward shareable, deployable software.
