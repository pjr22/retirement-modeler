# Retirement Modeler — Implementation Plan

A living plan. Update as work lands. `[x]` = done, `[ ]` = not done.

---

## Status

**Last updated:** 2026-05-01

**Current state:** Phases 0–3 are functionally complete. Backend, frontend, and integration tests all green. Multi-user isolation is enforced and exercised by 11 dedicated tests.

**Active work:** Phase 4 (enhanced tax modeling & withdrawal optimization) — see plan below.

**Deviations from original plan to keep in mind:**
- Phase 1's "in-memory storage" interim was skipped — went straight to Postgres + JPA + Flyway.
- Backend integration tests use H2 (`spring.jpa.hibernate.ddl-auto=create-drop`, Flyway disabled in tests). Original plan called for Testcontainers; this means our V001 migration is *not* exercised by the test suite. Plan to revisit in Phase 6 hardening.
- API paths still live under `/api/users/{profileId}/...` even though the entity is `UserProfile` (separate from the auth `User`). Internal Java/TypeScript names use `userProfileId`/`profileId`. Renaming the URL surface to `/api/profiles/...` is a noted follow-up; not blocking.

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
- `income_sources` table has no primary key
- `MonteCarloEngine.computeMedianYearsOfSurvival` would NPE on empty trial list

---

## Phase 4 — Enhanced Tax Modeling & Withdrawal Optimization 🚧 (next up)

**Goal:** Replace the flat-rate tax model with federal marginal brackets, capital-gains rates, and Social Security taxation rules. Add tax-aware withdrawal ordering and surface the resulting tax breakdown to users.

### Why this matters
The current engine uses `flatTaxRate × (income + withdrawals)`, which ignores:
- progressive ordinary-income brackets,
- the lower long-term capital gains rate,
- tax-free Roth distributions,
- the IRS provisional-income test for Social Security taxability,
- the standard deduction.

Tax-aware withdrawal ordering can change a 30-year retirement's lifetime tax bill by tens of thousands of dollars, so the projection that drives user decisions needs to model it.

### Sub-phase 4.1 — Tax bracket data model
- [ ] Add a `tax/` package under `com.retirementmodeler`
- [ ] `BracketTier` record `(BigDecimal threshold, BigDecimal rate)` (threshold is the lower bound of the bracket)
- [ ] `TaxBrackets` value object: ordinary brackets per `FilingStatus`, LTCG brackets per `FilingStatus`, standard deduction per `FilingStatus`, base tax year
- [ ] Hard-code 2025 federal brackets, LTCG rates, and standard deductions as the baseline (single source of truth, as code, in `FederalTaxBrackets2025` constants class)
- [ ] `TaxBracketProvider` Spring bean with `bracketsForYear(int year, BigDecimal cumulativeInflationFactor)` that returns the baseline brackets with thresholds and the standard deduction inflated by the supplied factor (matches IRS bracket creep rules)

### Sub-phase 4.2 — Tax calculator
- [ ] `TaxCalculator` component with method:
  ```
  TaxResult compute(
      FilingStatus status,
      BigDecimal ordinaryIncome,        // wages, pension, taxable IRA/401k withdrawals, taxable SS
      BigDecimal longTermCapitalGains,  // gains realized on taxable account withdrawals
      TaxBrackets brackets)
  ```
- [ ] `TaxResult` record: `(BigDecimal ordinaryTaxableIncome, BigDecimal ordinaryTax, BigDecimal capitalGainsTax, BigDecimal totalTax, BigDecimal effectiveRate, BigDecimal marginalRate)`
- [ ] Apply standard deduction first (against ordinary income); LTCG fills the LTCG bracket *stacked on top of* taxable ordinary income (so high-ordinary-income retirees pay 15% LTCG, low-income retirees pay 0%)
- [ ] Unit tests covering: zero income, exactly-at-bracket-edge, mid-bracket, all four filing statuses, LTCG-stacking edge cases

### Sub-phase 4.3 — Social Security taxation
- [ ] `SocialSecurityTaxer` component implementing the IRS provisional-income test:
  - Provisional income = AGI (excluding SS) + tax-exempt interest + ½ × SS benefits
  - Below first threshold (`$25K` single, `$32K` MFJ) → 0% of SS taxable
  - Between thresholds → up to 50% taxable
  - Above second threshold (`$34K` / `$44K`) → up to 85% taxable
- [ ] These thresholds are *not* inflation-adjusted by law — keep them fixed
- [ ] Unit tests for each tier and each filing status

### Sub-phase 4.4 — Withdrawal ordering strategies
- [ ] New enum `WithdrawalOrderingStrategy { PROPORTIONAL, TAX_OPTIMIZED, CUSTOM }`
- [ ] `WithdrawalAllocator` interface with one method: `Map<UUID, BigDecimal> allocate(List<Account> accounts, BigDecimal amountNeeded)` — returns withdrawal-per-account
- [ ] `ProportionalAllocator` — current behavior, default for backwards compatibility on existing scenarios
- [ ] `TaxOptimizedAllocator` — drains in order: TAXABLE_BROKERAGE / SAVINGS → TRADITIONAL_401K / TRADITIONAL_IRA → ROTH_401K / ROTH_IRA / HSA. Pension and Social Security are not "withdrawal sources" — they're income that arrives independently.
- [ ] `CustomAllocator` — drains in user-supplied order (`List<AccountType>` in the scenario)
- [ ] Add `withdrawalOrderingStrategy` and `customWithdrawalOrder` to `SimulationAssumptions`
- [ ] Unit tests: balances drained correctly when one bucket runs out mid-year, custom ordering respects user list, proportional matches existing behavior

### Sub-phase 4.5 — Wire it into the simulation engine
- [ ] Refactor `SimulationEngine` to:
  - Track per-year `ordinaryIncome`, `longTermCapitalGains`, `socialSecurityBenefit`, `taxableSocialSecurity` separately rather than lumping into `yearIncome`
  - Use `WithdrawalAllocator` instead of the inline proportional distribution
  - Categorize each withdrawal: traditional pre-tax → ordinary income; Roth/HSA → tax-free; taxable brokerage → LTCG (assume 100% gains for MVP since we don't track basis; flag this assumption in the response)
  - Call `SocialSecurityTaxer` with the year's other ordinary income to compute taxable SS
  - Call `TaxCalculator` with the categorized totals
- [ ] Extend `YearlyProjection` record with: `ordinaryIncome`, `capitalGains`, `socialSecurityBenefit`, `taxableSocialSecurity`, `ordinaryTax`, `capitalGainsTax` (keep existing fields; the old `totalTax` becomes the sum of ordinary + capital gains)
- [ ] Backwards compatibility: if a scenario still has `flatTaxRate` populated and explicitly opts in (e.g. legacy `withdrawalOrderingStrategy = null` treated as PROPORTIONAL with flat tax), keep the old code path. Otherwise use the new path. Document this in the engine.
- [ ] Update `MonteCarloEngine` — should "just work" since it delegates to `SimulationEngine.projectSingleTrial`, but verify

### Sub-phase 4.6 — Persistence & API surface
- [ ] New Flyway migration `V002__phase4_tax_modeling.sql`:
  - Add `withdrawal_ordering_strategy VARCHAR(255)` to `scenarios` (nullable, default `PROPORTIONAL` for existing rows)
  - Add `custom_withdrawal_order` (probably an `@ElementCollection` join table `scenario_custom_withdrawal_order` with ordered position)
- [ ] Update `SimulationAssumptions` JPA mapping for new fields
- [ ] Update frontend `SimulationAssumptions` TypeScript type and `Scenario` shape to match
- [ ] Existing scenarios continue to work (default strategy = PROPORTIONAL preserves prior behavior)

### Sub-phase 4.7 — Frontend changes
- [ ] Scenario editor: add "Withdrawal Ordering" section with strategy dropdown. When `CUSTOM`, show a drag-and-drop (or simple up/down arrows) list of account types to order
- [ ] Remove the `flatTaxRate` input from the scenario form — it's now derived. Replace with a read-only "Filing status drives federal brackets" hint linking to the profile page
- [ ] `SimulationResultsPage`:
  - Add new columns to the year-by-year table: ordinary income, capital gains, ordinary tax, capital-gains tax
  - New "Tax Breakdown" stacked-bar chart: per year, stack of ordinary tax + capital gains tax (alongside the existing portfolio chart)
  - Stat card: lifetime tax paid (sum across all projection years)
- [ ] Update existing tests; add new tests for the strategy selector and tax-breakdown rendering

### Sub-phase 4.8 — Test coverage and validation
- [ ] Backend integration test: realistic scenario (e.g. $1M traditional IRA, $500K Roth, $200K taxable, MFJ, $30K SS) → verify total tax differs meaningfully (>10%) between PROPORTIONAL+flat-rate and TAX_OPTIMIZED+brackets
- [ ] Backend unit tests for each tax component (4.1–4.4) listed above
- [ ] Frontend test for the new scenario editor section and results columns
- [ ] Manual sanity check: simulate, compare year-by-year output against a hand-calculated tax for one or two years

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
- [ ] Add primary key to `income_sources` table (new migration)
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
