CREATE TABLE users (
    id UUID NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_users_email ON users (email);

CREATE TABLE user_profiles (
    id UUID NOT NULL,
    owner_id UUID NOT NULL,
    name VARCHAR(255),
    date_of_birth DATE,
    planned_retirement_age INTEGER NOT NULL,
    life_expectancy INTEGER NOT NULL,
    filing_status VARCHAR(255) NOT NULL,
    CONSTRAINT pk_user_profiles PRIMARY KEY (id),
    CONSTRAINT fk_user_profiles_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE income_sources (
    profile_id UUID NOT NULL,
    id UUID,
    name VARCHAR(255),
    annual_amount DECIMAL(19, 2),
    end_age INTEGER,
    CONSTRAINT fk_income_sources_profile FOREIGN KEY (profile_id) REFERENCES user_profiles (id) ON DELETE CASCADE
);

CREATE TABLE accounts (
    id UUID NOT NULL,
    user_profile_id UUID NOT NULL,
    name VARCHAR(255),
    account_type VARCHAR(255) NOT NULL,
    balance DECIMAL(19, 2),
    annual_contribution DECIMAL(19, 2),
    monthly_benefit DECIMAL(19, 2),
    benefit_start_age INTEGER,
    CONSTRAINT pk_accounts PRIMARY KEY (id),
    CONSTRAINT fk_accounts_user_profile FOREIGN KEY (user_profile_id) REFERENCES user_profiles (id) ON DELETE CASCADE
);

CREATE TABLE scenarios (
    id UUID NOT NULL,
    user_profile_id UUID NOT NULL,
    name VARCHAR(255),
    description TEXT,
    expected_rate_of_return DECIMAL(19, 6),
    inflation_rate DECIMAL(19, 6),
    withdrawal_strategy VARCHAR(255) NOT NULL,
    withdrawal_percentage DECIMAL(19, 6),
    withdrawal_fixed_amount DECIMAL(19, 6),
    standard_deviation DECIMAL(19, 6),
    monte_carlo_trials INTEGER,
    flat_tax_rate DECIMAL(19, 6),
    CONSTRAINT pk_scenarios PRIMARY KEY (id),
    CONSTRAINT fk_scenarios_user_profile FOREIGN KEY (user_profile_id) REFERENCES user_profiles (id) ON DELETE CASCADE
);

CREATE TABLE scenario_accounts (
    scenario_id UUID NOT NULL,
    account_id UUID NOT NULL,
    CONSTRAINT fk_scenario_accounts_scenario FOREIGN KEY (scenario_id) REFERENCES scenarios (id) ON DELETE CASCADE,
    CONSTRAINT fk_scenario_accounts_account FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE CASCADE
);

CREATE TABLE simulation_results (
    id UUID NOT NULL,
    scenario_id UUID NOT NULL,
    user_profile_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    deterministic_projection JSONB NOT NULL,
    monte_carlo_summary JSONB NOT NULL,
    CONSTRAINT pk_simulation_results PRIMARY KEY (id),
    CONSTRAINT fk_simulation_results_scenario FOREIGN KEY (scenario_id) REFERENCES scenarios (id) ON DELETE CASCADE,
    CONSTRAINT fk_simulation_results_user_profile FOREIGN KEY (user_profile_id) REFERENCES user_profiles (id) ON DELETE CASCADE
);
