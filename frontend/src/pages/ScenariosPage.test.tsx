import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import { renderWithRouter, axiosOk } from "../test/helpers";
import ScenariosPage from "./ScenariosPage";

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

import { listScenarios } from "../api";

describe("ScenariosPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("shows empty state when no scenarios exist", async () => {
    vi.mocked(listScenarios).mockResolvedValue(axiosOk([]));
    renderWithRouter(<ScenariosPage />, {
      route: "/profiles/prof-1/scenarios",
      path: "/profiles/:profileId/scenarios",
    });

    await waitFor(() => {
      expect(
        screen.getByText("No scenarios yet. Create one to start planning."),
      ).toBeInTheDocument();
    });
  });

  it("lists scenarios with account count", async () => {
    vi.mocked(listScenarios).mockResolvedValue(
      axiosOk([
        {
          id: "scen-1",
          userProfileId: "prof-1",
          name: "Conservative",
          description: "Low risk",
          accountIds: ["acc-1", "acc-2"],
          assumptions: {
            expectedRateOfReturn: 0.05,
            inflationRate: 0.03,
            withdrawalStrategy: "FIXED_PERCENTAGE",
            withdrawalPercentage: 0.04,
            withdrawalMonthlyAmount: null,
            standardDeviation: 0.12,
            monteCarloTrials: 1000,
            flatTaxRate: 0.22,
          },
        },
      ]),
    );
    renderWithRouter(<ScenariosPage />, {
      route: "/profiles/prof-1/scenarios",
      path: "/profiles/:profileId/scenarios",
    });

    await waitFor(() => {
      expect(screen.getByText("Conservative")).toBeInTheDocument();
      expect(screen.getByText(/Low risk.*2 account/)).toBeInTheDocument();
    });
  });
});
