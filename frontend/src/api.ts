import axios from "axios";
import type { UserProfile, Account, Scenario, SimulationResult } from "./types";

const api = axios.create({
  baseURL: "http://localhost:8080",
  headers: { "Content-Type": "application/json" },
});

export const listUserProfiles = () => api.get<UserProfile[]>("/api/users");
export const getUserProfile = (id: string) => api.get<UserProfile>(`/api/users/${id}`);
export const createUserProfile = (profile: Omit<UserProfile, "id">) =>
  api.post<UserProfile>("/api/users", profile);
export const updateUserProfile = (id: string, profile: Omit<UserProfile, "id">) =>
  api.put<UserProfile>(`/api/users/${id}`, profile);
export const deleteUserProfile = (id: string) => api.delete(`/api/users/${id}`);

export const listAccounts = (userId: string) => api.get<Account[]>(`/api/users/${userId}/accounts`);
export const createAccount = (userId: string, account: Omit<Account, "id" | "userId">) =>
  api.post<Account>(`/api/users/${userId}/accounts`, account);
export const updateAccount = (id: string, account: Omit<Account, "id" | "userId">) =>
  api.put<Account>(`/api/accounts/${id}`, account);
export const deleteAccount = (id: string) => api.delete(`/api/accounts/${id}`);

export const listScenarios = (userId: string) =>
  api.get<Scenario[]>(`/api/users/${userId}/scenarios`);
export const getScenario = (id: string) => api.get<Scenario>(`/api/scenarios/${id}`);
export const createScenario = (userId: string, scenario: Omit<Scenario, "id" | "userId">) =>
  api.post<Scenario>(`/api/users/${userId}/scenarios`, scenario);
export const updateScenario = (id: string, scenario: Omit<Scenario, "id" | "userId">) =>
  api.put<Scenario>(`/api/scenarios/${id}`, scenario);
export const deleteScenario = (id: string) => api.delete(`/api/scenarios/${id}`);

export const runSimulation = (scenarioId: string) =>
  api.post<SimulationResult>(`/api/scenarios/${scenarioId}/simulate`);
export const getSimulation = (id: string) => api.get<SimulationResult>(`/api/simulations/${id}`);
export const listSimulations = (userId: string) =>
  api.get<SimulationResult[]>(`/api/users/${userId}/simulations`);

export default api;
