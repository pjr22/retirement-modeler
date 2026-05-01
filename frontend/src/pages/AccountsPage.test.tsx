import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithRouter, axiosOk } from "../test/helpers";

import AccountsPage from "./AccountsPage";

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

import { listAccounts, createAccount, deleteAccount } from "../api";

const sampleAccounts = [
  {
    id: "acc-1",
    userProfileId: "prof-1",
    name: "My 401k",
    accountType: "TRADITIONAL_401K" as const,
    balance: 50000,
    annualContribution: 23000,
    monthlyBenefit: null,
    benefitStartAge: null,
  },
  {
    id: "acc-2",
    userProfileId: "prof-1",
    name: "Company Pension",
    accountType: "PENSION" as const,
    balance: 0,
    annualContribution: null,
    monthlyBenefit: 2500,
    benefitStartAge: 65,
  },
];

describe("AccountsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("lists accounts with type-specific details", async () => {
    vi.mocked(listAccounts).mockResolvedValue(axiosOk(sampleAccounts));
    renderWithRouter(<AccountsPage />, {
      route: "/profiles/prof-1/accounts",
      path: "/profiles/:profileId/accounts",
    });

    await waitFor(() => {
      expect(screen.getByText("My 401k")).toBeInTheDocument();
      expect(screen.getByText("Company Pension")).toBeInTheDocument();
      expect(screen.getByText(/Annual Contribution.*\$23,000/)).toBeInTheDocument();
      expect(screen.getByText(/Monthly Benefit.*\$2,500/)).toBeInTheDocument();
    });
  });

  it("shows contribution fields for contribution-type accounts", async () => {
    const user = userEvent.setup();
    vi.mocked(listAccounts).mockResolvedValue(axiosOk([]));
    vi.mocked(createAccount).mockResolvedValue(axiosOk(sampleAccounts[0]));

    renderWithRouter(<AccountsPage />, {
      route: "/profiles/prof-1/accounts",
      path: "/profiles/:profileId/accounts",
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

  it("shows benefit fields for pension/social security accounts", async () => {
    const user = userEvent.setup();
    vi.mocked(listAccounts).mockResolvedValue(axiosOk([]));
    vi.mocked(createAccount).mockResolvedValue(axiosOk(sampleAccounts[1]));

    renderWithRouter(<AccountsPage />, {
      route: "/profiles/prof-1/accounts",
      path: "/profiles/:profileId/accounts",
    });

    await waitFor(() => {
      expect(screen.getByText("Add Account")).toBeInTheDocument();
    });

    await user.click(screen.getByText("Add Account"));

    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByRole("textbox", { name: /account name/i }), "My Pension");

    const typeSelect = within(dialog).getByRole("combobox", { name: /account type/i });
    await user.click(typeSelect);
    await user.click(screen.getByText("Pension"));

    expect(
      within(dialog).getByRole("spinbutton", { name: /monthly benefit/i }),
    ).toBeInTheDocument();
    expect(
      within(dialog).getByRole("spinbutton", { name: /benefit start age/i }),
    ).toBeInTheDocument();
    expect(
      within(dialog).queryByRole("spinbutton", { name: /annual contribution/i }),
    ).not.toBeInTheDocument();
  });

  it("deletes an account", async () => {
    const user = userEvent.setup();
    vi.mocked(listAccounts).mockResolvedValue(axiosOk([sampleAccounts[0]]));
    vi.mocked(deleteAccount).mockResolvedValue(axiosOk(undefined));

    renderWithRouter(<AccountsPage />, {
      route: "/profiles/prof-1/accounts",
      path: "/profiles/:profileId/accounts",
    });

    await waitFor(() => {
      expect(screen.getByText("My 401k")).toBeInTheDocument();
    });

    const deleteButtons = screen.getAllByRole("button");
    const deleteBtn = deleteButtons.find((b) => b.querySelector('[data-testid="DeleteIcon"]'));
    if (deleteBtn) {
      await user.click(deleteBtn);
    }

    await waitFor(() => {
      expect(deleteAccount).toHaveBeenCalledWith("acc-1");
    });
  });
});
