import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithRouter } from "../test/helpers";
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
}));

import { getSimulation, getScenario } from "../api";

const sampleScenario = {
  id: "scen-1",
  userId: "prof-1",
  name: "Test Scenario",
  description: null,
  accountIds: ["acc-1"],
  assumptions: {
    expectedRateOfReturn: 0.07,
    inflationRate: 0.03,
    withdrawalStrategy: "FIXED_PERCENTAGE",
    withdrawalPercentage: 0.04,
    withdrawalFixedAmount: null,
    standardDeviation: 0.15,
    monteCarloTrials: 10,
    flatTaxRate: 0.22,
  },
};

const sampleSimulation = {
  id: "sim-1",
  scenarioId: "scen-1",
  userId: "prof-1",
  createdAt: "2026-01-01T00:00:00Z",
  deterministicProjection: [
    {
      age: 36,
      year: 2026,
      totalBalance: 500000,
      totalContributions: 0,
      totalWithdrawals: 0,
      totalIncome: 0,
      totalTax: 0,
      inflationFactor: 1,
    },
    {
      age: 65,
      year: 2055,
      totalBalance: 2500000,
      totalContributions: 690000,
      totalWithdrawals: 0,
      totalIncome: 0,
      totalTax: 0,
      inflationFactor: 2.3,
    },
    {
      age: 90,
      year: 2080,
      totalBalance: 800000,
      totalContributions: 690000,
      totalWithdrawals: 1200000,
      totalIncome: 0,
      totalTax: 264000,
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
  });

  it("loads and displays simulation results", async () => {
    vi.mocked(getSimulation).mockResolvedValue({ data: sampleSimulation });
    vi.mocked(getScenario).mockResolvedValue({ data: sampleScenario });

    renderWithRouter(<SimulationResultsPage />, {
      route: "/simulations/sim-1",
      path: "/simulations/:simulationId",
    });

    await waitFor(() => {
      expect(screen.getByText("Simulation Results")).toBeInTheDocument();
    });

    expect(screen.getByText("85.0%")).toBeInTheDocument();
    expect(screen.getByText("55.0")).toBeInTheDocument();
    expect(screen.getAllByText("$800,000").length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText("10")).toBeInTheDocument();
    expect(screen.getByText("Test Scenario")).toBeInTheDocument();
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
    vi.mocked(getSimulation).mockResolvedValue({ data: sampleSimulation });
    vi.mocked(getScenario).mockResolvedValue({ data: sampleScenario });

    renderWithRouter(<SimulationResultsPage />, {
      route: "/simulations/sim-1",
      path: "/simulations/:simulationId",
    });

    await waitFor(() => {
      expect(screen.getByText("Simulation Results")).toBeInTheDocument();
    });

    expect(screen.queryByRole("table")).not.toBeInTheDocument();

    await user.click(screen.getByText("Show Table"));

    await waitFor(() => {
      expect(screen.getByRole("table")).toBeInTheDocument();
      expect(screen.getByText("$500,000")).toBeInTheDocument();
    });

    await user.click(screen.getByText("Hide Table"));
  });
});
