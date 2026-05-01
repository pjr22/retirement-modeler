import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithRouter, axiosOk } from "../test/helpers";

import ProfilesPage from "./ProfilesPage";

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

import { listUserProfiles, createUserProfile, deleteUserProfile } from "../api";

const sampleProfile = {
  id: "prof-1",
  name: "Alice",
  dateOfBirth: "1990-01-01",
  plannedRetirementAge: 65,
  lifeExpectancy: 90,
  filingStatus: "SINGLE" as const,
  incomeSources: [],
};

describe("ProfilesPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listUserProfiles).mockResolvedValue(axiosOk([]));
  });

  it("shows empty state when no profiles exist", async () => {
    renderWithRouter(<ProfilesPage />);
    await waitFor(() => {
      expect(screen.getByText("No profiles yet. Create one to get started.")).toBeInTheDocument();
    });
  });

  it("lists profiles from the API", async () => {
    vi.mocked(listUserProfiles).mockResolvedValue(axiosOk([sampleProfile]));
    renderWithRouter(<ProfilesPage />);
    await waitFor(() => {
      expect(screen.getByText("Alice")).toBeInTheDocument();
      expect(screen.getByText(/Retire at 65/)).toBeInTheDocument();
    });
  });

  it("creates a profile via the dialog form", async () => {
    const user = userEvent.setup();
    vi.mocked(listUserProfiles).mockResolvedValue(axiosOk([]));
    vi.mocked(createUserProfile).mockResolvedValue(
      axiosOk({ ...sampleProfile, id: "prof-2", name: "Bob" }),
    );

    renderWithRouter(<ProfilesPage />);
    await waitFor(() => {
      expect(screen.getByText("No profiles yet. Create one to get started.")).toBeInTheDocument();
    });

    await user.click(screen.getByText("Create Profile"));

    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByRole("textbox", { name: /name/i }), "Bob");
    const dateInput = dialog.querySelector('input[type="date"]') as HTMLInputElement;
    await user.type(dateInput, "1990-01-15");

    const dialogCreateBtn = within(dialog)
      .getAllByRole("button")
      .find((b) => b.textContent === "Create");
    await user.click(dialogCreateBtn!);

    await waitFor(() => {
      expect(createUserProfile).toHaveBeenCalledWith(
        expect.objectContaining({
          name: "Bob",
          dateOfBirth: "1990-01-15",
          incomeSources: [],
        }),
      );
    });
  });

  it("deletes a profile", async () => {
    const user = userEvent.setup();
    vi.mocked(listUserProfiles).mockResolvedValue(axiosOk([sampleProfile]));
    vi.mocked(deleteUserProfile).mockResolvedValue(axiosOk(undefined));

    renderWithRouter(<ProfilesPage />);
    await waitFor(() => {
      expect(screen.getByText("Alice")).toBeInTheDocument();
    });

    const deleteButtons = screen.getAllByRole("button");
    const deleteBtn = deleteButtons.find((b) => b.querySelector('[data-testid="DeleteIcon"]'));
    if (deleteBtn) {
      await user.click(deleteBtn);
    } else {
      await user.click(screen.getByRole("button", { name: /delete/i }));
    }

    await waitFor(() => {
      expect(deleteUserProfile).toHaveBeenCalledWith("prof-1");
    });
  });
});
