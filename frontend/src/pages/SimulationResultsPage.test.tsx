import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithRouter, axiosOk } from "../test/helpers";

import SimulationResultsPage from "./SimulationResultsPage";

vi.mock("../api", () => ({
  default: { get: vi.fn() },
  listUserProfiles: vi.fn(),
  getUserProfile: vi.fn(),
  createUserProfile: vi.fn(),
  updateUserProfile: vi.fn(),
  deleteUserProfile: vi.fn(),
  listAccounts: vi.fn(),
  createAccount: vi.fn(),
  updateAccount: vi.fn(),
  deleteAccount: vi.fn(),
  listScenarios: vi.fn(),
  getScenario: vi.fn(),
  createScenario: vi.fn(),
  updateScenario: vi.fn(),
  deleteScenario: vi.fn(),
  runSimulation: vi.fn(),
  getSimulation: vi.fn(),
  listSimulations: vi.fn(),
  getStoredAuth: vi.fn(() => ({
    token: "test-token",
    userId: "test-user-id",
    email: "test@test.com",
    isAuthenticated: true,
  })),
  storeAuth: vi.fn(),
  clearAuth: vi.fn(),
  register: vi.fn(),
  login: vi.fn(),
}));

import { getSimulation, getScenario, getUserProfile } from "../api";

const sampleProfile = {
  id: "prof-1",
  name: "Test Profile",
  dateOfBirth: "1990-01-01",
  plannedRetirementDate: "2055-01-01",
  lifeExpectancy: 90,
  filingStatus: "MARRIED_FILING_JOINTLY" as const,
};

const sampleScenario = {
  id: "scen-1",
  userProfileId: "prof-1",
  name: "Test Scenario",
  description: null,
  accountIds: ["acc-1"],
  assumptions: {
    expectedRateOfReturn: 0.07,
    inflationRate: 0.03,
    withdrawalStrategy: "PORTFOLIO_PERCENTAGE" as const,
    withdrawalPercentage: 0.04,
    withdrawalMonthlyAmount: null,
    standardDeviation: 0.15,
    monteCarloTrials: 10,
    withdrawalOrderingStrategy: "PROPORTIONAL" as const,
    customWithdrawalOrder: [],
  },
};

const sampleSimulation = {
  id: "sim-1",
  scenarioId: "scen-1",
  userProfileId: "prof-1",
  createdAt: "2026-01-01T00:00:00Z",
  deterministicProjection: [
    {
      age: 36,
      date: "2026-10-01",
      balance: 500000,
      yearContributions: 0,
      yearWithdrawals: 0,
      yearIncome: 0,
      yearTax: 0,
      yearOrdinaryIncome: 0,
      yearCapitalGains: 0,
      yearSocialSecurityBenefit: 0,
      yearTaxableSocialSecurity: 0,
      yearOrdinaryTax: 0,
      yearCapitalGainsTax: 0,
      yearRmd: 0,
      inflationFactor: 1,
    },
    {
      age: 65,
      date: "2055-10-01",
      balance: 2500000,
      yearContributions: 690000,
      yearWithdrawals: 0,
      yearIncome: 0,
      yearTax: 0,
      yearOrdinaryIncome: 0,
      yearCapitalGains: 0,
      yearSocialSecurityBenefit: 0,
      yearTaxableSocialSecurity: 0,
      yearOrdinaryTax: 0,
      yearCapitalGainsTax: 0,
      yearRmd: 0,
      inflationFactor: 2.3,
    },
    {
      age: 90,
      date: "2080-10-01",
      balance: 800000,
      yearContributions: 690000,
      yearWithdrawals: 1200000,
      yearIncome: 0,
      yearTax: 264000,
      yearOrdinaryIncome: 800000,
      yearCapitalGains: 400000,
      yearSocialSecurityBenefit: 0,
      yearTaxableSocialSecurity: 0,
      yearOrdinaryTax: 200000,
      yearCapitalGainsTax: 64000,
      yearRmd: 42500,
      inflationFactor: 4.5,
    },
  ],
  monteCarloSummary: {
    trials: 10,
    successRate: 85.0,
    medianYearsOfSurvival: 55.0,
    percentileBalances: [
      { age: 36, p10: 450000, p25: 470000, p50: 500000, p75: 530000, p90: 560000 },
      { age: 65, p10: 1500000, p25: 2000000, p50: 2500000, p75: 3000000, p90: 3500000 },
      { age: 90, p10: 100000, p25: 300000, p50: 800000, p75: 1200000, p90: 1600000 },
    ],
  },
};

describe("SimulationResultsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getUserProfile).mockResolvedValue(axiosOk(sampleProfile));
  });

  it("loads and shows deterministic series stats by default", async () => {
    vi.mocked(getSimulation).mockResolvedValue(axiosOk(sampleSimulation));
    vi.mocked(getScenario).mockResolvedValue(axiosOk(sampleScenario));

    renderWithRouter(<SimulationResultsPage />, {
      route: "/simulations/sim-1",
      path: "/simulations/:simulationId",
    });

    await waitFor(() => {
      expect(screen.getByText("Simulation Results")).toBeInTheDocument();
    });

    // The deterministic series ends at $800,000 (positive), so it survives.
    expect(screen.getAllByText("$800,000").length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText("Survives")).toBeInTheDocument();
    expect(screen.getByText("Never")).toBeInTheDocument();
    // Overall MC success rate badge.
    expect(screen.getByText(/Overall MC success: 85\.0%/)).toBeInTheDocument();
    expect(screen.getByText("Test Scenario")).toBeInTheDocument();
  });

  it("switches metrics when a different series is selected", async () => {
    const user = userEvent.setup();
    vi.mocked(getSimulation).mockResolvedValue(axiosOk(sampleSimulation));
    vi.mocked(getScenario).mockResolvedValue(axiosOk(sampleScenario));

    renderWithRouter(<SimulationResultsPage />, {
      route: "/simulations/sim-1",
      path: "/simulations/:simulationId",
    });

    await waitFor(() => {
      expect(screen.getByText("Simulation Results")).toBeInTheDocument();
    });

    // Open the series selector and pick the 90th percentile.
    await user.click(screen.getByRole("combobox", { name: /show statistics for/i }));
    await user.click(screen.getByRole("option", { name: /90th percentile/i }));

    // 90th percentile last entry in the sample is 1,600,000.
    expect(screen.getAllByText("$1,600,000").length).toBeGreaterThanOrEqual(1);
  });

  it("shows error when simulation fails to load", async () => {
    vi.mocked(getSimulation).mockRejectedValue(new Error("Not found"));

    renderWithRouter(<SimulationResultsPage />, {
      route: "/simulations/sim-1",
      path: "/simulations/:simulationId",
    });

    await waitFor(() => {
      expect(screen.getByText("Failed to load simulation results")).toBeInTheDocument();
    });
  });

  it("toggles the year-by-year details table", async () => {
    const user = userEvent.setup();
    vi.mocked(getSimulation).mockResolvedValue(axiosOk(sampleSimulation));
    vi.mocked(getScenario).mockResolvedValue(axiosOk(sampleScenario));

    renderWithRouter(<SimulationResultsPage />, {
      route: "/simulations/sim-1",
      path: "/simulations/:simulationId",
    });

    await waitFor(() => {
      expect(screen.getByText("Simulation Results")).toBeInTheDocument();
    });

    // Year-by-year table is collapsed by default; the params section also has its own table.
    expect(screen.queryByRole("table")).not.toBeInTheDocument();

    await user.click(screen.getByText("Show Table"));

    await waitFor(() => {
      expect(screen.getByRole("table")).toBeInTheDocument();
      expect(screen.getByText("$500,000")).toBeInTheDocument();
    });

    await user.click(screen.getByText("Hide Table"));
  });

  it("displays the Lifetime Tax stat card from summed yearTax", async () => {
    vi.mocked(getSimulation).mockResolvedValue(axiosOk(sampleSimulation));
    vi.mocked(getScenario).mockResolvedValue(axiosOk(sampleScenario));

    renderWithRouter(<SimulationResultsPage />, {
      route: "/simulations/sim-1",
      path: "/simulations/:simulationId",
    });

    await waitFor(() => {
      expect(screen.getByText("Lifetime Tax")).toBeInTheDocument();
    });
    // Lifetime tax for the deterministic series is 0 + 0 + 264000 = $264,000.
    expect(screen.getByText("$264,000")).toBeInTheDocument();
  });

  it("shows the RMD column in the year-by-year table sourced from yearRmd", async () => {
    const user = userEvent.setup();
    vi.mocked(getSimulation).mockResolvedValue(axiosOk(sampleSimulation));
    vi.mocked(getScenario).mockResolvedValue(axiosOk(sampleScenario));

    renderWithRouter(<SimulationResultsPage />, {
      route: "/simulations/sim-1",
      path: "/simulations/:simulationId",
    });

    await waitFor(() => {
      expect(screen.getByText("Show Table")).toBeInTheDocument();
    });
    await user.click(screen.getByText("Show Table"));

    await waitFor(() => {
      expect(screen.getByRole("columnheader", { name: /RMD/ })).toBeInTheDocument();
    });
    // Row at age 90 carries yearRmd = $42,500 in the fixture.
    expect(screen.getByText("$42,500")).toBeInTheDocument();
  });

  it("shows separate Ordinary Tax and Capital Gains Tax columns in the year-by-year table", async () => {
    const user = userEvent.setup();
    vi.mocked(getSimulation).mockResolvedValue(axiosOk(sampleSimulation));
    vi.mocked(getScenario).mockResolvedValue(axiosOk(sampleScenario));

    renderWithRouter(<SimulationResultsPage />, {
      route: "/simulations/sim-1",
      path: "/simulations/:simulationId",
    });

    await waitFor(() => {
      expect(screen.getByText("Show Table")).toBeInTheDocument();
    });
    await user.click(screen.getByText("Show Table"));

    await waitFor(() => {
      expect(screen.getByRole("columnheader", { name: /Ordinary Tax/ })).toBeInTheDocument();
      expect(screen.getByRole("columnheader", { name: /Capital Gains Tax/ })).toBeInTheDocument();
    });
    // Row at age 90: ordinary tax $200,000, cap gains tax $64,000 (sourced directly).
    expect(screen.getByText("$200,000")).toBeInTheDocument();
    expect(screen.getByText("$64,000")).toBeInTheDocument();
  });

  it("shows Filing Status and Withdrawal Ordering rows in the assumptions table", async () => {
    const user = userEvent.setup();
    vi.mocked(getSimulation).mockResolvedValue(axiosOk(sampleSimulation));
    vi.mocked(getScenario).mockResolvedValue(axiosOk(sampleScenario));

    renderWithRouter(<SimulationResultsPage />, {
      route: "/simulations/sim-1",
      path: "/simulations/:simulationId",
    });

    await waitFor(() => {
      expect(screen.getByText("Scenario Parameters")).toBeInTheDocument();
    });

    const showButtons = screen.getAllByRole("button", { name: /^show$/i });
    await user.click(showButtons[0]);

    await waitFor(() => {
      expect(screen.getByText("Filing Status")).toBeInTheDocument();
      expect(screen.getByText("Withdrawal Ordering")).toBeInTheDocument();
      expect(screen.getByText(/Married Filing Jointly/)).toBeInTheDocument();
    });
    // Old "Flat Tax Rate" row is gone.
    expect(screen.queryByText("Flat Tax Rate")).not.toBeInTheDocument();
  });

  it("renders Scenario Parameters section with MC trial count", async () => {
    const user = userEvent.setup();
    vi.mocked(getSimulation).mockResolvedValue(axiosOk(sampleSimulation));
    vi.mocked(getScenario).mockResolvedValue(axiosOk(sampleScenario));

    renderWithRouter(<SimulationResultsPage />, {
      route: "/simulations/sim-1",
      path: "/simulations/:simulationId",
    });

    await waitFor(() => {
      expect(screen.getByText("Scenario Parameters")).toBeInTheDocument();
    });

    // Expand the params section (MUI Collapse keeps content mounted, so we
    // can't reliably assert on absence — just confirm content is present
    // after expanding).
    const showButtons = screen.getAllByRole("button", { name: /^show$/i });
    await user.click(showButtons[0]);

    await waitFor(() => {
      expect(screen.getByText("Monte Carlo Trials")).toBeInTheDocument();
      expect(screen.getByText("10")).toBeInTheDocument();
    });
  });
});
