import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithRouter, axiosOk } from "../test/helpers";

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

import { getUserProfile, updateUserProfile } from "../api";

const sampleProfile = {
  id: "prof-1",
  name: "Alice",
  dateOfBirth: "1990-01-01",
  plannedRetirementDate: "2055-01-01",
  lifeExpectancy: 90,
  filingStatus: "SINGLE" as const,
  incomeSources: [
    {
      id: "inc-1",
      name: "Salary",
      annualAmount: 120000,
      endAge: 65,
      inflationAdjusted: true,
    },
  ],
};

describe("ProfileDetailPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
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

  it("shows income sources in a table", async () => {
    vi.mocked(getUserProfile).mockResolvedValue(axiosOk(sampleProfile));
    renderWithRouter(<ProfileDetailPage />, {
      route: "/profiles/prof-1",
      path: "/profiles/:profileId",
    });

    await waitFor(() => {
      expect(screen.getByText("Salary")).toBeInTheDocument();
      expect(screen.getByText("$120,000")).toBeInTheDocument();
    });
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
});
