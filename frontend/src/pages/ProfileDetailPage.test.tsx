import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithRouter, axiosOk } from "../test/helpers";

const mockNavigate = vi.fn();
vi.mock("react-router", async () => {
  const actual = await vi.importActual<typeof import("react-router")>("react-router");
  return { ...actual, useNavigate: () => mockNavigate };
});

import ProfileDetailPage from "./ProfileDetailPage";

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
  getUserProfile,
  updateUserProfile,
  listAccounts,
  createAccount,
  deleteAccount,
  listScenarios,
  deleteScenario,
  runSimulation,
} from "../api";

const sampleProfile = {
  id: "prof-1",
  name: "Alice",
  dateOfBirth: "1990-01-01",
  plannedRetirementDate: "2055-01-01",
  lifeExpectancy: 90,
  filingStatus: "SINGLE" as const,
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
    name: "Brokerage",
    accountType: "TAXABLE_BROKERAGE" as const,
    balance: 100000,
    annualContribution: null,
  },
];

const sampleScenarios = [
  {
    id: "scen-1",
    userProfileId: "prof-1",
    name: "Baseline",
    description: "All-in plan",
    accountIds: ["acc-1"],
    assumptions: {
      expectedRateOfReturn: 0.07,
      inflationRate: 0.03,
      withdrawalStrategy: "PORTFOLIO_PERCENTAGE" as const,
      withdrawalPercentage: 0.04,
      withdrawalMonthlyAmount: null,
      standardDeviation: 0.15,
      monteCarloTrials: 1000,
      withdrawalOrderingStrategy: "PROPORTIONAL" as const,
      customWithdrawalOrder: [],
    },
  },
];

describe("ProfileDetailPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listAccounts).mockResolvedValue(axiosOk([]));
    vi.mocked(listScenarios).mockResolvedValue(axiosOk([]));
  });

  it("loads and displays the profile", async () => {
    vi.mocked(getUserProfile).mockResolvedValue(axiosOk(sampleProfile));
    renderWithRouter(<ProfileDetailPage />, {
      route: "/profiles/prof-1",
      path: "/profiles/:profileId",
    });

    await waitFor(() => {
      expect(screen.getByText("Alice")).toBeInTheDocument();
      expect(screen.getByDisplayValue("Alice")).toBeInTheDocument();
    });
  });

  it("does not show an Income Sources section (income is per-scenario)", async () => {
    vi.mocked(getUserProfile).mockResolvedValue(axiosOk(sampleProfile));
    renderWithRouter(<ProfileDetailPage />, {
      route: "/profiles/prof-1",
      path: "/profiles/:profileId",
    });

    await waitFor(() => {
      expect(screen.getByDisplayValue("Alice")).toBeInTheDocument();
    });
    expect(screen.queryByText("Income Sources")).not.toBeInTheDocument();
    expect(screen.queryByText("Add Income Source")).not.toBeInTheDocument();
  });

  it("enters edit mode and saves changes", async () => {
    const user = userEvent.setup();
    vi.mocked(getUserProfile).mockResolvedValue(axiosOk(sampleProfile));
    vi.mocked(updateUserProfile).mockResolvedValue(
      axiosOk({ ...sampleProfile, name: "Alice Updated" }),
    );

    renderWithRouter(<ProfileDetailPage />, {
      route: "/profiles/prof-1",
      path: "/profiles/:profileId",
    });

    await waitFor(() => {
      expect(screen.getByText("Edit")).toBeInTheDocument();
    });

    await user.click(screen.getByText("Edit"));

    const nameField = screen.getByDisplayValue("Alice");
    await user.clear(nameField);
    await user.type(nameField, "Alice Updated");

    await user.click(screen.getByText("Save"));

    await waitFor(() => {
      expect(updateUserProfile).toHaveBeenCalledWith(
        "prof-1",
        expect.objectContaining({ name: "Alice Updated" }),
      );
    });
  });

  it("lists accounts inline with type-specific details", async () => {
    vi.mocked(getUserProfile).mockResolvedValue(axiosOk(sampleProfile));
    vi.mocked(listAccounts).mockResolvedValue(axiosOk(sampleAccounts));

    renderWithRouter(<ProfileDetailPage />, {
      route: "/profiles/prof-1",
      path: "/profiles/:profileId",
    });

    await waitFor(() => {
      expect(screen.getByText("My 401k")).toBeInTheDocument();
      expect(screen.getByText("Brokerage")).toBeInTheDocument();
      expect(screen.getByText("$23,000")).toBeInTheDocument();
    });
  });

  it("shows the contribution field for contribution-type accounts when adding", async () => {
    const user = userEvent.setup();
    vi.mocked(getUserProfile).mockResolvedValue(axiosOk(sampleProfile));
    vi.mocked(createAccount).mockResolvedValue(axiosOk(sampleAccounts[0]));

    renderWithRouter(<ProfileDetailPage />, {
      route: "/profiles/prof-1",
      path: "/profiles/:profileId",
    });

    await waitFor(() => {
      expect(screen.getByText("Add Account")).toBeInTheDocument();
    });

    await user.click(screen.getByText("Add Account"));

    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByRole("textbox", { name: /account name/i }), "Test 401k");
    expect(
      within(dialog).getByRole("spinbutton", { name: /annual contribution/i }),
    ).toBeInTheDocument();

    const addBtn = within(dialog)
      .getAllByRole("button")
      .find((b) => b.textContent === "Add");
    await user.click(addBtn!);

    await waitFor(() => {
      expect(createAccount).toHaveBeenCalledWith(
        "prof-1",
        expect.objectContaining({ name: "Test 401k" }),
      );
    });
  });

  it("hides annual contribution field for non-contribution account types", async () => {
    const user = userEvent.setup();
    vi.mocked(getUserProfile).mockResolvedValue(axiosOk(sampleProfile));

    renderWithRouter(<ProfileDetailPage />, {
      route: "/profiles/prof-1",
      path: "/profiles/:profileId",
    });

    await waitFor(() => {
      expect(screen.getByText("Add Account")).toBeInTheDocument();
    });

    await user.click(screen.getByText("Add Account"));

    const dialog = await screen.findByRole("dialog");
    const typeSelect = within(dialog).getByRole("combobox", { name: /account type/i });
    await user.click(typeSelect);
    await user.click(screen.getByText("Taxable Brokerage"));

    expect(
      within(dialog).queryByRole("spinbutton", { name: /annual contribution/i }),
    ).not.toBeInTheDocument();
  });

  it("lists scenarios inline with name and description", async () => {
    vi.mocked(getUserProfile).mockResolvedValue(axiosOk(sampleProfile));
    vi.mocked(listScenarios).mockResolvedValue(axiosOk(sampleScenarios));

    renderWithRouter(<ProfileDetailPage />, {
      route: "/profiles/prof-1",
      path: "/profiles/:profileId",
    });

    await waitFor(() => {
      expect(screen.getByText("Baseline")).toBeInTheDocument();
      expect(screen.getByText("All-in plan")).toBeInTheDocument();
    });
  });

  it("deletes a scenario", async () => {
    const user = userEvent.setup();
    vi.mocked(getUserProfile).mockResolvedValue(axiosOk(sampleProfile));
    vi.mocked(listScenarios).mockResolvedValue(axiosOk(sampleScenarios));
    vi.mocked(deleteScenario).mockResolvedValue(axiosOk(undefined));

    renderWithRouter(<ProfileDetailPage />, {
      route: "/profiles/prof-1",
      path: "/profiles/:profileId",
    });

    await waitFor(() => {
      expect(screen.getByText("Baseline")).toBeInTheDocument();
    });

    const deleteBtn = screen
      .getAllByRole("button")
      .find((b) => b.querySelector('[data-testid="DeleteIcon"]'));
    expect(deleteBtn).toBeDefined();
    await user.click(deleteBtn!);

    await waitFor(() => {
      expect(deleteScenario).toHaveBeenCalledWith("scen-1");
    });
  });

  it("runs a scenario from the row action and navigates to the simulation result", async () => {
    const user = userEvent.setup();
    vi.mocked(getUserProfile).mockResolvedValue(axiosOk(sampleProfile));
    vi.mocked(listScenarios).mockResolvedValue(axiosOk(sampleScenarios));
    vi.mocked(runSimulation).mockResolvedValue(
      axiosOk({
        id: "sim-1",
        scenarioId: "scen-1",
        userProfileId: "prof-1",
        createdAt: "2026-05-03T00:00:00Z",
        deterministicProjection: [],
        monteCarloSummary: {
          trials: 0,
          successRate: 0,
          medianYearsOfSurvival: 0,
          percentileBalances: [],
        },
      }),
    );

    renderWithRouter(<ProfileDetailPage />, {
      route: "/profiles/prof-1",
      path: "/profiles/:profileId",
    });

    await waitFor(() => {
      expect(screen.getByText("Baseline")).toBeInTheDocument();
    });

    const runBtn = screen
      .getAllByRole("button")
      .find((b) => b.querySelector('[data-testid="PlayArrowIcon"]'));
    expect(runBtn).toBeDefined();
    await user.click(runBtn!);

    await waitFor(() => {
      expect(runSimulation).toHaveBeenCalledWith("scen-1");
      expect(mockNavigate).toHaveBeenCalledWith("/simulations/sim-1");
    });
  });

  it("disables the Run button when the scenario has no accounts", async () => {
    vi.mocked(getUserProfile).mockResolvedValue(axiosOk(sampleProfile));
    vi.mocked(listScenarios).mockResolvedValue(
      axiosOk([{ ...sampleScenarios[0], accountIds: [] }]),
    );

    renderWithRouter(<ProfileDetailPage />, {
      route: "/profiles/prof-1",
      path: "/profiles/:profileId",
    });

    await waitFor(() => {
      expect(screen.getByText("Baseline")).toBeInTheDocument();
    });

    const runBtn = screen
      .getAllByRole("button")
      .find((b) => b.querySelector('[data-testid="PlayArrowIcon"]'));
    expect(runBtn).toBeDefined();
    expect(runBtn).toBeDisabled();
  });

  it("clones a scenario by navigating to /scenarios/new with cloneFrom", async () => {
    const user = userEvent.setup();
    vi.mocked(getUserProfile).mockResolvedValue(axiosOk(sampleProfile));
    vi.mocked(listScenarios).mockResolvedValue(axiosOk(sampleScenarios));

    renderWithRouter(<ProfileDetailPage />, {
      route: "/profiles/prof-1",
      path: "/profiles/:profileId",
    });

    await waitFor(() => {
      expect(screen.getByText("Baseline")).toBeInTheDocument();
    });

    const cloneBtn = screen
      .getAllByRole("button")
      .find((b) => b.querySelector('[data-testid="ContentCopyIcon"]'));
    expect(cloneBtn).toBeDefined();
    await user.click(cloneBtn!);

    expect(mockNavigate).toHaveBeenCalledWith(
      "/scenarios/new?profileId=prof-1&cloneFrom=scen-1",
    );
  });

  it("clones an account via a prefilled dialog", async () => {
    const user = userEvent.setup();
    vi.mocked(getUserProfile).mockResolvedValue(axiosOk(sampleProfile));
    vi.mocked(listAccounts).mockResolvedValue(axiosOk([sampleAccounts[0]]));
    vi.mocked(createAccount).mockResolvedValue(
      axiosOk({ ...sampleAccounts[0], id: "acc-clone", name: "Copy of My 401k" }),
    );

    renderWithRouter(<ProfileDetailPage />, {
      route: "/profiles/prof-1",
      path: "/profiles/:profileId",
    });

    await waitFor(() => {
      expect(screen.getByText("My 401k")).toBeInTheDocument();
    });

    const cloneBtn = screen
      .getAllByRole("button")
      .find((b) => b.querySelector('[data-testid="ContentCopyIcon"]'));
    expect(cloneBtn).toBeDefined();
    await user.click(cloneBtn!);

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("Clone Account")).toBeInTheDocument();
    expect(within(dialog).getByDisplayValue("Copy of My 401k")).toBeInTheDocument();

    const submitBtn = within(dialog)
      .getAllByRole("button")
      .find((b) => b.textContent === "Clone");
    await user.click(submitBtn!);

    await waitFor(() => {
      expect(createAccount).toHaveBeenCalledWith(
        "prof-1",
        expect.objectContaining({
          name: "Copy of My 401k",
          accountType: "TRADITIONAL_401K",
          balance: 50000,
          annualContribution: 23000,
        }),
      );
    });
  });

  it("deletes an account", async () => {
    const user = userEvent.setup();
    vi.mocked(getUserProfile).mockResolvedValue(axiosOk(sampleProfile));
    vi.mocked(listAccounts).mockResolvedValue(axiosOk([sampleAccounts[0]]));
    vi.mocked(deleteAccount).mockResolvedValue(axiosOk(undefined));

    renderWithRouter(<ProfileDetailPage />, {
      route: "/profiles/prof-1",
      path: "/profiles/:profileId",
    });

    await waitFor(() => {
      expect(screen.getByText("My 401k")).toBeInTheDocument();
    });

    const deleteBtn = screen
      .getAllByRole("button")
      .find((b) => b.querySelector('[data-testid="DeleteIcon"]'));
    expect(deleteBtn).toBeDefined();
    await user.click(deleteBtn!);

    await waitFor(() => {
      expect(deleteAccount).toHaveBeenCalledWith("acc-1");
    });
  });
});
