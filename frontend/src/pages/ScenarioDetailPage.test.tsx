import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithRouter, axiosOk } from "../test/helpers";

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
  listIncomeSources: vi.fn(),
  createIncomeSource: vi.fn(),
  updateIncomeSource: vi.fn(),
  deleteIncomeSource: vi.fn(),
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

import {
  getScenario,
  getUserProfile,
  listAccounts,
  listIncomeSources,
  createIncomeSource,
  deleteIncomeSource,
  createScenario,
  updateScenario,
  runSimulation,
} from "../api";

const sampleProfile = {
  id: "prof-1",
  name: "Test Profile",
  dateOfBirth: "1965-01-01",
  plannedRetirementDate: "2030-01-01",
  lifeExpectancy: 90,
  filingStatus: "MARRIED_FILING_JOINTLY" as const,
};

const sampleScenario = {
  id: "scen-1",
  userProfileId: "prof-1",
  name: "Conservative",
  description: "Low risk approach",
  accountIds: ["acc-1"],
  assumptions: {
    expectedRateOfReturn: 0.05,
    inflationRate: 0.03,
    withdrawalStrategy: "PORTFOLIO_PERCENTAGE" as const,
    withdrawalPercentage: 0.04,
    withdrawalMonthlyAmount: null,
    standardDeviation: 0.12,
    monteCarloTrials: 1000,
    withdrawalOrderingStrategy: "PROPORTIONAL" as const,
    customWithdrawalOrder: [],
  },
};

const sampleAccounts = [
  {
    id: "acc-1",
    userProfileId: "prof-1",
    name: "My 401k",
    accountType: "TRADITIONAL_401K" as const,
    balance: 50000,
    annualContribution: 23000,
  },
  {
    id: "acc-2",
    userProfileId: "prof-1",
    name: "Roth IRA",
    accountType: "ROTH_IRA" as const,
    balance: 30000,
    annualContribution: 7000,
  },
];

const samplePension = {
  id: "inc-1",
  scenarioId: "scen-1",
  name: "Pension",
  type: "PENSION" as const,
  monthlyAmount: 2500,
  startDate: null,
  endDate: null,
  inflationAdjusted: false,
};

describe("ScenarioDetailPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listIncomeSources).mockResolvedValue(axiosOk([]));
    vi.mocked(getUserProfile).mockResolvedValue(axiosOk(sampleProfile));
  });

  it("loads and displays an existing scenario", async () => {
    vi.mocked(getScenario).mockResolvedValue(axiosOk(sampleScenario));
    vi.mocked(listAccounts).mockResolvedValue(axiosOk(sampleAccounts));

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
    vi.mocked(getScenario).mockResolvedValue(axiosOk(sampleScenario));
    vi.mocked(listAccounts).mockResolvedValue(axiosOk(sampleAccounts));

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

  it("renders the scenario's income sources in a table", async () => {
    vi.mocked(getScenario).mockResolvedValue(axiosOk(sampleScenario));
    vi.mocked(listAccounts).mockResolvedValue(axiosOk(sampleAccounts));
    vi.mocked(listIncomeSources).mockResolvedValue(axiosOk([samplePension]));

    renderWithRouter(<ScenarioDetailPage />, {
      route: "/scenarios/scen-1",
      path: "/scenarios/:scenarioId",
    });

    await waitFor(() => {
      expect(screen.getByText("$2,500")).toBeInTheDocument();
    });
  });

  it("creates a new income source via the dialog", async () => {
    const user = userEvent.setup();
    vi.mocked(getScenario).mockResolvedValue(axiosOk(sampleScenario));
    vi.mocked(listAccounts).mockResolvedValue(axiosOk(sampleAccounts));
    vi.mocked(listIncomeSources).mockResolvedValue(axiosOk([]));
    vi.mocked(createIncomeSource).mockResolvedValue(axiosOk(samplePension));

    renderWithRouter(<ScenarioDetailPage />, {
      route: "/scenarios/scen-1",
      path: "/scenarios/:scenarioId",
    });

    await waitFor(() => {
      expect(screen.getByText("Add Income Source")).toBeInTheDocument();
    });

    await user.click(screen.getByText("Add Income Source"));

    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByRole("textbox", { name: /name/i }), "Pension");
    const monthlyField = within(dialog).getByRole("spinbutton", { name: /monthly amount/i });
    await user.clear(monthlyField);
    await user.type(monthlyField, "2500");

    const addBtn = within(dialog)
      .getAllByRole("button")
      .find((b) => b.textContent === "Add");
    await user.click(addBtn!);

    await waitFor(() => {
      expect(createIncomeSource).toHaveBeenCalledWith(
        "scen-1",
        expect.objectContaining({ name: "Pension", monthlyAmount: 2500 }),
      );
    });
  });

  it("deletes an income source", async () => {
    const user = userEvent.setup();
    vi.mocked(getScenario).mockResolvedValue(axiosOk(sampleScenario));
    vi.mocked(listAccounts).mockResolvedValue(axiosOk(sampleAccounts));
    vi.mocked(listIncomeSources).mockResolvedValue(axiosOk([samplePension]));
    vi.mocked(deleteIncomeSource).mockResolvedValue(axiosOk(undefined));

    renderWithRouter(<ScenarioDetailPage />, {
      route: "/scenarios/scen-1",
      path: "/scenarios/:scenarioId",
    });

    await waitFor(() => {
      expect(screen.getByText("$2,500")).toBeInTheDocument();
    });

    // Find the delete icon button inside the income-sources table row.
    const rows = screen.getAllByRole("row");
    const dataRow = rows.find((r) => within(r).queryByText("$2,500"));
    expect(dataRow).toBeDefined();
    const deleteBtn = within(dataRow!)
      .getAllByRole("button")
      .find((b) => b.querySelector('[data-testid="DeleteIcon"]'));
    await user.click(deleteBtn!);

    await waitFor(() => {
      expect(deleteIncomeSource).toHaveBeenCalledWith("inc-1");
    });
  });

  it("disables income-source CRUD when creating a new (unsaved) scenario", async () => {
    vi.mocked(listAccounts).mockResolvedValue(axiosOk(sampleAccounts));

    renderWithRouter(<ScenarioDetailPage />, {
      route: "/scenarios/new?profileId=prof-1",
      path: "/scenarios/:scenarioId",
    });

    await waitFor(() => {
      expect(screen.getByText("Create Scenario")).toBeInTheDocument();
    });

    expect(screen.getByText("Add Income Source")).toBeDisabled();
    expect(
      screen.getByText(/Save the scenario first, then add income sources/i),
    ).toBeInTheDocument();
  });

  it("saves changes to an existing scenario", async () => {
    const user = userEvent.setup();
    vi.mocked(getScenario).mockResolvedValue(axiosOk(sampleScenario));
    vi.mocked(listAccounts).mockResolvedValue(axiosOk(sampleAccounts));
    vi.mocked(updateScenario).mockResolvedValue(axiosOk(sampleScenario));

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
    vi.mocked(listAccounts).mockResolvedValue(axiosOk(sampleAccounts));
    vi.mocked(createScenario).mockResolvedValue(axiosOk({ ...sampleScenario, id: "scen-new" }));

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

  it("renders the Withdrawal Ordering dropdown and the filing-status hint", async () => {
    vi.mocked(getScenario).mockResolvedValue(axiosOk(sampleScenario));
    vi.mocked(listAccounts).mockResolvedValue(axiosOk(sampleAccounts));

    renderWithRouter(<ScenarioDetailPage />, {
      route: "/scenarios/scen-1",
      path: "/scenarios/:scenarioId",
    });

    await waitFor(() => {
      expect(screen.getByText("Withdrawal Ordering")).toBeInTheDocument();
    });

    // Default ordering is PROPORTIONAL — the dropdown shows its label.
    expect(screen.getByRole("combobox", { name: /ordering strategy/i })).toHaveTextContent(
      /Proportional/,
    );
    // Custom-order picker is hidden when strategy != CUSTOM.
    expect(screen.queryByText(/Draw Order/)).not.toBeInTheDocument();
    // Filing-status hint reflects the loaded profile.
    expect(screen.getByText(/Married Filing Jointly/)).toBeInTheDocument();
  });

  it("shows the custom-order picker when CUSTOM is selected and reorders with arrow buttons", async () => {
    const user = userEvent.setup();
    vi.mocked(getScenario).mockResolvedValue(axiosOk(sampleScenario));
    vi.mocked(listAccounts).mockResolvedValue(axiosOk(sampleAccounts));

    renderWithRouter(<ScenarioDetailPage />, {
      route: "/scenarios/scen-1",
      path: "/scenarios/:scenarioId",
    });

    await waitFor(() => {
      expect(screen.getByText("Withdrawal Ordering")).toBeInTheDocument();
    });

    // Open the ordering dropdown and pick Custom Order.
    await user.click(screen.getByRole("combobox", { name: /ordering strategy/i }));
    await user.click(screen.getByRole("option", { name: /Custom Order/ }));

    await waitFor(() => {
      expect(screen.getByText(/Draw Order/)).toBeInTheDocument();
    });

    // Picker is prefilled with all 7 account types in TAX_OPTIMIZED-like default order.
    const orderRows = screen
      .getAllByRole("row")
      .filter((r) => within(r).queryByText(/TAXABLE|SAVINGS|TRADITIONAL|ROTH|HSA/));
    expect(orderRows).toHaveLength(7);
    expect(within(orderRows[0]).getByText(/TAXABLE BROKERAGE/)).toBeInTheDocument();
    expect(within(orderRows[1]).getByText(/SAVINGS/)).toBeInTheDocument();

    // Move SAVINGS up — it should swap with TAXABLE BROKERAGE.
    const savingsRow = orderRows[1];
    const upButton = within(savingsRow)
      .getAllByRole("button")
      .find((b) => b.querySelector('[data-testid="ArrowUpwardIcon"]'));
    await user.click(upButton!);

    await waitFor(() => {
      const newRows = screen
        .getAllByRole("row")
        .filter((r) => within(r).queryByText(/TAXABLE|SAVINGS|TRADITIONAL|ROTH|HSA/));
      expect(within(newRows[0]).getByText(/SAVINGS/)).toBeInTheDocument();
      expect(within(newRows[1]).getByText(/TAXABLE BROKERAGE/)).toBeInTheDocument();
    });
  });

  it("shows Run Simulation button for existing scenarios and triggers simulation", async () => {
    const user = userEvent.setup();
    vi.mocked(getScenario).mockResolvedValue(axiosOk(sampleScenario));
    vi.mocked(listAccounts).mockResolvedValue(axiosOk(sampleAccounts));
    vi.mocked(runSimulation).mockResolvedValue(
      axiosOk({
        id: "sim-1",
        scenarioId: "scen-1",
        userProfileId: "prof-1",
        createdAt: "2026-01-01T00:00:00Z",
        deterministicProjection: [],
        monteCarloSummary: {
          trials: 0,
          successRate: 0,
          medianYearsOfSurvival: 0,
          percentileBalances: [],
        },
      }),
    );

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
