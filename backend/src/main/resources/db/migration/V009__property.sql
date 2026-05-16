-- Phase 5.2 — Real Property (Slice 1: profile-level entity).
-- A property belongs to a user profile, mirroring the accounts table. Scenario-level decisions
-- (sale events, reverse mortgage activation) come in V010.
CREATE TABLE properties (
    id UUID NOT NULL,
    user_profile_id UUID NOT NULL,
    name VARCHAR(255),
    type VARCHAR(64) NOT NULL,
    current_value DECIMAL(19, 2),
    cost_basis DECIMAL(19, 2),
    mortgage_balance DECIMAL(19, 2),
    mortgage_annual_rate DECIMAL(19, 6),
    mortgage_monthly_pi DECIMAL(19, 2),
    annual_property_tax DECIMAL(19, 2),
    annual_insurance DECIMAL(19, 2),
    monthly_hoa DECIMAL(19, 2),
    annual_maintenance_pct DECIMAL(19, 6),
    CONSTRAINT pk_properties PRIMARY KEY (id),
    CONSTRAINT fk_properties_user_profile FOREIGN KEY (user_profile_id) REFERENCES user_profiles (id) ON DELETE CASCADE
);
