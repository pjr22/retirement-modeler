import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import { renderWithRouter, axiosOk } from "../test/helpers";
import HealthStatus from "./HealthStatus";
import api from "../api";

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

describe("HealthStatus", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("shows checking message while loading", () => {
    vi.mocked(api.get).mockReturnValue(new Promise(() => {}));
    renderWithRouter(<HealthStatus />);
    expect(screen.getByText("Checking backend...")).toBeInTheDocument();
  });

  it("shows connected when health check succeeds", async () => {
    vi.mocked(api.get).mockResolvedValue(axiosOk({ status: "UP", timestamp: "2025-01-01" }));
    renderWithRouter(<HealthStatus />);
    await waitFor(() => {
      expect(screen.getByText("Backend connected")).toBeInTheDocument();
    });
  });

  it("shows unavailable when health check fails", async () => {
    vi.mocked(api.get).mockRejectedValue(new Error("Network error"));
    renderWithRouter(<HealthStatus />);
    await waitFor(() => {
      expect(screen.getByText("Backend unavailable")).toBeInTheDocument();
    });
  });
});
