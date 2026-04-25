# Retirement Modeler - Implementation Plan

## Overview

Retirement Modeler is a full-stack retirement planning application with a Java 21 / Spring Boot backend and a React / TypeScript / MUI frontend. Users can create financial profiles, define retirement scenarios, and run both deterministic and Monte Carlo simulations to evaluate retirement readiness.

---

## Phase 0 — Project Scaffolding

**Goal:** Establish the monorepo structure, build tooling, and development workflow.

- [ ] Restructure Gradle into a multi-project build:
  - `backend/` — Spring Boot application (Java 21)
  - `frontend/` — Vite + React + TypeScript application
  - `model/` (future) — shared domain models / DTOs if needed
- [ ] Set up Spring Boot 3.x in the backend subproject with:
  - Spring Web, Spring Actuator
  - Gradle dependency management via `libs.versions.toml`
- [ ] Scaffold the frontend with Vite, TypeScript, and MUI
- [ ] Configure CORS for local development (backend on :8080, frontend on :5173)
- [ ] Add `.editorconfig`, code formatting rules (Google Java Format for backend, Prettier for frontend)
- [ ] Add a basic health check endpoint (`GET /api/health`) and a frontend landing page that calls it
- [ ] Set up integration test infrastructure (Testcontainers or embedded server)

**Deliverable:** Both projects build and run; frontend displays a status indicator from the backend health endpoint.

---

## Phase 1 — Domain Model & Core API

**Goal:** Define the financial domain model and expose a REST API for scenario input — no simulation logic yet.

### Domain model (backend)

- `UserProfile` — holds personal details (date of birth, planned retirement age, life expectancy, filing status)
- `Account` (abstract) with subtypes:
  - `PreTaxAccount` (Traditional 401k, Traditional IRA)
  - `RothAccount` (Roth 401k, Roth IRA)
  - `TaxableAccount` (brokerage, savings)
  - `HsaAccount`
  - `PensionAccount`
  - `SocialSecurityBenefit`
- `IncomeSource` — current salary, other income streams
- `Scenario` — a named scenario that ties together a user profile, accounts, assumptions, and simulation parameters
- `SimulationAssumptions` — rate of return, inflation rate, withdrawal strategy, tax assumptions (flat effective rate initially)

### REST API endpoints

```
POST   /api/users                    Create a user profile
GET    /api/users/{id}               Get user profile
PUT    /api/users/{id}               Update user profile

POST   /api/users/{id}/accounts      Add an account
GET    /api/users/{id}/accounts      List accounts
PUT    /api/accounts/{id}            Update account
DELETE /api/accounts/{id}            Remove account

POST   /api/users/{id}/scenarios     Create a scenario
GET    /api/users/{id}/scenarios     List scenarios
GET    /api/scenarios/{id}           Get scenario details
PUT    /api/scenarios/{id}           Update scenario
DELETE /api/scenarios/{id}           Delete scenario
```

### Frontend

- Basic page layout with MUI: sidebar navigation, app bar
- Forms for creating/editing a user profile
- Forms for adding/editing accounts (with account-type-specific fields)
- Scenario editor page (select accounts, set assumptions)
- All data stored in-memory on the backend (no database yet)

**Deliverable:** A user can fill out their profile, enter accounts, and configure a scenario via the UI. Data persists only for the lifetime of the server process.

---

## Phase 2 — Simulation Engine

**Goal:** Implement deterministic and Monte Carlo retirement projections.

### Deterministic projection

- Input: scenario (accounts, assumptions, retirement age, life expectancy)
- Year-by-year projection:
  - Apply rate of return to each account balance
  - Apply inflation adjustment to income and expenses
  - Apply annual contributions (pre-retirement) and withdrawals (post-retirement)
  - Apply minimal tax model: flat effective tax rate on taxable income
- Output: a time series of account balances, income, withdrawals, and taxes per year from current age through life expectancy

### Monte Carlo simulation

- Configurable number of trials (default: 1,000; max: 10,000)
- Each trial randomizes annual returns using a configurable distribution (start with normal distribution parameterized by mean return and standard deviation)
- Output per trial: same time series as deterministic
- Aggregate output:
  - Probability of portfolio survival at each year
  - Median / 10th / 25th / 75th / 90th percentile balances over time
  - Overall success rate (portfolio survives to life expectancy)

### Withdrawal strategies (initial)

- Fixed percentage (e.g., 4% rule)
- Fixed dollar amount (inflation-adjusted)
- Dynamic (reduce withdrawals in down years) — optional stretch goal

### New API endpoints

```
POST /api/scenarios/{id}/simulate    Run simulation with current scenario config
GET  /api/simulations/{id}           Get previous simulation results
GET  /api/users/{id}/simulations     List simulation runs for a user
```

### Frontend

- Simulation results dashboard:
  - Line chart showing portfolio balance over time (deterministic path + Monte Carlo percentile bands)
  - Key metrics cards: success rate, years of portfolio survival, final balance at life expectancy
  - Detailed year-by-year table (collapsible)
- "Run Simulation" button on scenario editor
- Loading state / progress indicator for Monte Carlo runs

**Deliverable:** Users can run both deterministic and Monte Carlo simulations and visualize results through interactive charts.

---

## Phase 3 — Persistence & User Management

**Goal:** Introduce a database and basic authentication so users can save and return to their data.

### Backend

- Choose and configure a database (PostgreSQL recommended; H2 for local dev)
- Spring Data JPA entities for all domain objects
- Database migration with Flyway or Liquibase
- Spring Security with JWT-based authentication
- User registration and login endpoints:
  ```
  POST /api/auth/register
  POST /api/auth/login
  ```
- Associate all data (profiles, accounts, scenarios, simulation results) with authenticated users
- Input validation with Bean Validation annotations

### Frontend

- Login and registration pages
- Auth token management (stored in memory or httpOnly cookie)
- Protected routes — redirect to login when unauthenticated
- Automatic session persistence: data survives page refresh and server restarts

**Deliverable:** Users can register, log in, and their data is persisted across sessions.

---

## Phase 4 — Enhanced Tax Modeling & Withdrawal Optimization

**Goal:** Upgrade the tax engine and add smarter withdrawal ordering.

### Tax modeling improvements

- Federal marginal tax brackets (updated annually)
- Standard deduction vs. itemized deduction toggle
- Tax-aware withdrawal ordering:
  - taxable accounts first (capital gains rates)
  - then pre-tax accounts (ordinary income)
  - then Roth accounts (tax-free)
  - Social Security taxation thresholds
- Estimated tax liability per year in projection output

### Withdrawal optimization

- Suggest withdrawal ordering that minimizes lifetime tax burden
- Allow user to override with custom ordering rules

### Frontend

- Tax breakdown visualization per year
- Side-by-side comparison of different withdrawal strategies

**Deliverable:** Simulation results include detailed tax projections and the system recommends tax-efficient withdrawal ordering.

---

## Phase 5 — Scenario Comparison & Reporting

**Goal:** Allow users to compare multiple scenarios and export results.

### Scenario comparison

- Save simulation results linked to a scenario
- Side-by-side comparison view for 2–4 scenarios
- Overlay charts: portfolio balance, success probability, tax burden
- "What-if" mode: clone a scenario, tweak one parameter, re-run

### Reporting

- Export simulation results as PDF report
- Export raw data as CSV
- Optional: shareable link (read-only) for financial advisor review

**Deliverable:** Users can compare scenarios visually and generate shareable reports.

---

## Phase 6 — Production Readiness

**Goal:** Harden the application for production deployment.

### Backend

- Containerize with Docker (multi-stage build)
- Structured logging (JSON format)
- Metrics with Micrometer + Prometheus endpoint
- Rate limiting on API endpoints
- CSRF protection, security headers
- Automated database backups strategy
- CI/CD pipeline (GitHub Actions)

### Frontend

- Production build optimization (code splitting, tree shaking)
- Error boundary components
- Accessibility audit (WCAG 2.1 AA)
- Responsive design review (mobile / tablet)
- E2E tests with Playwright or Cypress

### Infrastructure

- Kubernetes or Docker Compose deployment config
- Environment-based configuration (dev / staging / prod)
- SSL termination, reverse proxy config (nginx)
- Health checks and readiness probes

**Deliverable:** Application can be deployed to a cloud environment with monitoring, logging, and automated CI/CD.

---

## Phase 7 — Future Enhancements (Backlog)

These items are not scheduled and will be prioritized based on user feedback.

- **Financial institution integration** — Plaid or similar API for automatic account data import
- **Advanced Monte Carlo** — fat-tailed distributions, sequence-of-returns risk analysis
- **Social Security optimization** — optimal claiming age analysis
- **Healthcare cost modeling** — Medicare premiums, long-term care insurance
- **Estate planning** — inheritance modeling, beneficiary considerations
- **Multi-currency support** — for international users
- **Collaborative planning** — joint retirement planning for couples
- **Mobile application** — React Native or PWA
- **White-label / advisor portal** — financial advisor dashboard managing multiple clients

---

## Technology Stack Summary

| Layer          | Technology                                      |
|----------------|-------------------------------------------------|
| Language       | Java 21                                         |
| Framework      | Spring Boot 3.x                                 |
| Build          | Gradle 8.x (multi-project)                     |
| Database       | PostgreSQL (production), H2 (development)       |
| ORM            | Spring Data JPA + Hibernate                     |
| Migrations     | Flyway or Liquibase                             |
| Auth           | Spring Security + JWT                           |
| Testing (BE)   | JUnit 5, Mockito, Testcontainers                |
| Frontend       | React 18+ with TypeScript                       |
| Build (FE)     | Vite                                            |
| UI Library     | MUI (Material UI)                               |
| Charts         | Recharts or Chart.js (via react-chartjs-2)      |
| HTTP Client    | Axios or fetch API                              |
| Testing (FE)   | Vitest, React Testing Library, Playwright       |
| Containerization | Docker                                       |
| CI/CD          | GitHub Actions                                  |

---

## Phasing Timeline (Estimated)

| Phase | Description                          | Estimated Effort |
|-------|--------------------------------------|------------------|
| 0     | Project Scaffolding                  | 1–2 weeks        |
| 1     | Domain Model & Core API              | 2–3 weeks        |
| 2     | Simulation Engine                    | 3–4 weeks        |
| 3     | Persistence & User Management        | 2–3 weeks        |
| 4     | Enhanced Tax & Withdrawal Optimization | 2–3 weeks      |
| 5     | Scenario Comparison & Reporting      | 2–3 weeks        |
| 6     | Production Readiness                 | 2–3 weeks        |
| 7+    | Future Enhancements                  | Ongoing          |

> **Note:** Phases 0–2 are the MVP. By the end of Phase 2, users can model their retirement and see projections without needing to create accounts or persist data. Phases 3–6 mature the product toward production readiness.
