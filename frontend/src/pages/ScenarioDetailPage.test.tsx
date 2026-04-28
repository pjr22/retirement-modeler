import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithRouter } from "../test/helpers";
import ScenarioDetailPage from "./ScenarioDetailPage";

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

import { getScenario, listAccounts, createScenario, updateScenario, runSimulation } from "../api";

const sampleScenario = {
  id: "scen-1",
  userId: "prof-1",
  name: "Conservative",
  description: "Low risk approach",
  accountIds: ["acc-1"],
  assumptions: {
    expectedRateOfReturn: 0.05,
    inflationRate: 0.03,
    withdrawalStrategy: "FIXED_PERCENTAGE" as const,
    withdrawalPercentage: 0.04,
    withdrawalFixedAmount: null,
    standardDeviation: 0.12,
    monteCarloTrials: 1000,
    flatTaxRate: 0.22,
  },
};

const sampleAccounts = [
  {
    id: "acc-1",
    userId: "prof-1",
    name: "My 401k",
    accountType: "TRADITIONAL_401K" as const,
    balance: 50000,
    annualContribution: 23000,
    monthlyBenefit: null,
    benefitStartAge: null,
  },
  {
    id: "acc-2",
    userId: "prof-1",
    name: "Roth IRA",
    accountType: "ROTH_IRA" as const,
    balance: 30000,
    annualContribution: 7000,
    monthlyBenefit: null,
    benefitStartAge: null,
  },
];

describe("ScenarioDetailPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("loads and displays an existing scenario", async () => {
    vi.mocked(getScenario).mockResolvedValue({ data: sampleScenario });
    vi.mocked(listAccounts).mockResolvedValue({ data: sampleAccounts });

    renderWithRouter(<ScenarioDetailPage />, {
      route: "/scenarios/scen-1",
      path: "/scenarios/:scenarioId",
    });

    await waitFor(() => {
      expect(screen.getByDisplayValue("Conservative")).toBeInTheDocument();
      expect(screen.getByDisplayValue("Low risk approach")).toBeInTheDocument();
    });
  });

  it("shows accounts with checkboxes for selection", async () => {
    vi.mocked(getScenario).mockResolvedValue({ data: sampleScenario });
    vi.mocked(listAccounts).mockResolvedValue({ data: sampleAccounts });

    renderWithRouter(<ScenarioDetailPage />, {
      route: "/scenarios/scen-1",
      path: "/scenarios/:scenarioId",
    });

    await waitFor(() => {
      expect(screen.getByText(/My 401k/)).toBeInTheDocument();
      expect(screen.getByText(/Roth IRA/)).toBeInTheDocument();
    });

    expect(screen.getByRole("checkbox", { name: /My 401k/ })).toBeChecked();
    expect(screen.getByRole("checkbox", { name: /Roth IRA/ })).not.toBeChecked();
  });

  it("saves changes to an existing scenario", async () => {
    const user = userEvent.setup();
    vi.mocked(getScenario).mockResolvedValue({ data: sampleScenario });
    vi.mocked(listAccounts).mockResolvedValue({ data: sampleAccounts });
    vi.mocked(updateScenario).mockResolvedValue({ data: sampleScenario });

    renderWithRouter(<ScenarioDetailPage />, {
      route: "/scenarios/scen-1",
      path: "/scenarios/:scenarioId",
    });

    await waitFor(() => {
      expect(screen.getByDisplayValue("Conservative")).toBeInTheDocument();
    });

    const saveButtons = screen.getAllByRole("button").filter((b) => b.textContent === "Save");
    await user.click(saveButtons[0]);

    await waitFor(() => {
      expect(updateScenario).toHaveBeenCalledWith(
        "scen-1",
        expect.objectContaining({ name: "Conservative" }),
      );
    });
  });

  it("creates a new scenario when accessed via /scenarios/new", async () => {
    const user = userEvent.setup();
    vi.mocked(listAccounts).mockResolvedValue({ data: sampleAccounts });
    vi.mocked(createScenario).mockResolvedValue({ data: { ...sampleScenario, id: "scen-new" } });

    renderWithRouter(<ScenarioDetailPage />, {
      route: "/scenarios/new?profileId=prof-1",
      path: "/scenarios/:scenarioId",
    });

    await waitFor(() => {
      expect(screen.getByText("Create Scenario")).toBeInTheDocument();
    });

    const nameField = await screen.findByRole("textbox", { name: /scenario name/i });
    await user.type(nameField, "Test Scenario");
    const saveButtons = screen.getAllByRole("button").filter((b) => b.textContent === "Save");
    await user.click(saveButtons[0]);

    await waitFor(() => {
      expect(createScenario).toHaveBeenCalledWith(
        "prof-1",
        expect.objectContaining({ name: "Test Scenario" }),
      );
    });
  });

  it("shows Run Simulation button for existing scenarios and triggers simulation", async () => {
    const user = userEvent.setup();
    vi.mocked(getScenario).mockResolvedValue({ data: sampleScenario });
    vi.mocked(listAccounts).mockResolvedValue({ data: sampleAccounts });
    vi.mocked(runSimulation).mockResolvedValue({
      data: {
        id: "sim-1",
        scenarioId: "scen-1",
        userId: "prof-1",
        createdAt: "2026-01-01T00:00:00Z",
        deterministicProjection: [],
        monteCarloSummary: {
          trials: 0,
          successRate: 0,
          medianYearsOfSurvival: 0,
          percentileBalances: [],
        },
      },
    });

    renderWithRouter(<ScenarioDetailPage />, {
      route: "/scenarios/scen-1",
      path: "/scenarios/:scenarioId",
    });

    await waitFor(() => {
      expect(screen.getByText("Run Simulation")).toBeInTheDocument();
    });

    await user.click(screen.getByText("Run Simulation"));

    await waitFor(() => {
      expect(runSimulation).toHaveBeenCalledWith("scen-1");
    });
  });
});
