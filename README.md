# Retirement Modeler

A web app for modeling retirement plans. You set up one or more profiles, attach
your real accounts, and create scenarios that combine those accounts with
withdrawal strategies, expected returns, taxes, and other recurring income.
Each scenario can be simulated to see how long your savings are likely to last.

## What you can model

- **Profiles** — one per person you're planning for. Holds date of birth,
  planned retirement date, life expectancy, and federal filing status.
- **Accounts** — per profile. Traditional and Roth 401(k)/IRA, taxable
  brokerage, savings, and HSA. Each account has a balance and (where
  applicable) an annual contribution.
- **Scenarios** — per profile. Pick which accounts to include, set assumptions
  (expected return, inflation, standard deviation, Monte Carlo trial count),
  and choose how withdrawals work.
- **Income sources** — per scenario. Pensions, Social Security, employment,
  rental, etc. Each can be inflation-adjusted (e.g. SS COLA) or fixed.

### Withdrawal strategies

- **Portfolio Percentage** — withdraw a fixed % of current savings each year
  (e.g. the 4% rule). Income arrives on top of withdrawals.
- **Cashflow Target** — set a monthly spending target. Income (pension, SS,
  etc.) is applied first; savings cover the gap.

### Withdrawal ordering

When savings have to cover a withdrawal, this controls which accounts are
drained first:

- **Proportional** — split across all positive-balance accounts in proportion
  to balance.
- **Tax-Optimized** — drain in tiers: taxable → tax-deferred → tax-free.
- **Custom** — you specify the exact order by account type.

### Simulation results

Each run produces a deterministic projection (year-by-year balance,
contributions, withdrawals, income, tax breakdown) plus a Monte Carlo summary
across the trial count you set: success rate, median years of survival, and
percentile balance bands (p10/p25/p50/p75/p90) you can chart by metric.

## Quick start (using the app)

1. Register an account on the login page.
2. **Create a profile** with your date of birth, planned retirement date, life
   expectancy, and filing status.
3. **Add accounts** to the profile (balances and annual contributions).
4. **Create a scenario** on the profile. Pick the accounts to include, choose
   a withdrawal strategy, and tweak assumptions.
5. **Add income sources** to the scenario if you have any (pension, Social
   Security, employment, etc.).
6. **Run the scenario** from the row's run button (or from inside the scenario
   editor) to see the projection and Monte Carlo result.

You can clone profiles, accounts, and scenarios to compare variations.

---

## Running locally

### Prerequisites

- **Docker** — for the Postgres container.
- **Java 21** — the Gradle wrapper handles Gradle itself.
- **Node.js 20+** — for the frontend dev server.

### One-time setup

```bash
git clone <this-repo-url> retirement-modeler
cd retirement-modeler

# Start Postgres (creates the container the first time, idempotent thereafter)
./startDb.sh

# Frontend dependencies
cd frontend
npm install
cd ..
```

### Run the stack

In two terminals from the repo root:

```bash
# Terminal 1: backend (Spring Boot on http://localhost:8080)
./gradlew :backend:bootRun
```

```bash
# Terminal 2: frontend dev server (http://localhost:5173)
cd frontend
npm run dev
```

Then open <http://localhost:5173> in a browser. The connection indicator in
the top-right corner of the app shows whether the backend is reachable.

### Tests

```bash
# Backend (JUnit, runs against H2)
./gradlew :backend:test

# Frontend (Vitest)
cd frontend && npm test
```

### Stack

- **Backend** — Java 21, Spring Boot, Spring Security (JWT), JPA/Hibernate,
  Flyway migrations, Postgres in production / H2 for tests.
- **Frontend** — React 19, TypeScript, Vite, MUI, recharts.
- **Database** — Postgres 16 (via `startDb.sh`); schema managed by Flyway
  migrations under `backend/src/main/resources/db/migration`.

### Configuration

Backend defaults live in `backend/src/main/resources/application.properties`.
Database credentials match `startDb.sh`. The JWT signing secret and expiration
are also defined there; replace the secret before any non-local deployment.
The frontend's API base URL is hardcoded to `http://localhost:8080` in
`frontend/src/api.ts` — change it there for non-local deployments.
