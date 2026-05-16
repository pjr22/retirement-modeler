import axios from "axios";
import type {
  UserProfile,
  Account,
  Property,
  IncomeSource,
  Scenario,
  SimulationResult,
  AuthResponse,
} from "./types";

const TOKEN_KEY = "retirement_modeler_token";
const USER_ID_KEY = "retirement_modeler_user_id";
const EMAIL_KEY = "retirement_modeler_email";

const api = axios.create({
  baseURL: "http://localhost:8080",
  headers: { "Content-Type": "application/json" },
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      clearAuth();
      window.location.href = "/login";
    } else if (!error.response || error.response.status >= 500) {
      // Network error or server error suggests the backend is unreachable. Dynamic import
      // sidesteps a circular dependency between this file and healthMonitor.
      import("./healthMonitor").then(({ markPossiblyDown }) => markPossiblyDown());
    }
    return Promise.reject(error);
  },
);

export function getStoredAuth() {
  const token = localStorage.getItem(TOKEN_KEY);
  const userId = localStorage.getItem(USER_ID_KEY);
  const email = localStorage.getItem(EMAIL_KEY);
  return { token, userId, email, isAuthenticated: !!token };
}

export function storeAuth(auth: AuthResponse) {
  localStorage.setItem(TOKEN_KEY, auth.token);
  localStorage.setItem(USER_ID_KEY, auth.userId);
  localStorage.setItem(EMAIL_KEY, auth.email);
}

export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_ID_KEY);
  localStorage.removeItem(EMAIL_KEY);
}

export const register = (email: string, password: string) =>
  api.post<AuthResponse>("/api/auth/register", { email, password });

export const login = (email: string, password: string) =>
  api.post<AuthResponse>("/api/auth/login", { email, password });

export const listUserProfiles = () => api.get<UserProfile[]>("/api/users");
export const getUserProfile = (id: string) => api.get<UserProfile>(`/api/users/${id}`);
export const createUserProfile = (profile: Omit<UserProfile, "id">) =>
  api.post<UserProfile>("/api/users", profile);
export const updateUserProfile = (id: string, profile: Omit<UserProfile, "id">) =>
  api.put<UserProfile>(`/api/users/${id}`, profile);
export const deleteUserProfile = (id: string) => api.delete(`/api/users/${id}`);
export const cloneUserProfile = (id: string, overrides: Omit<UserProfile, "id">) =>
  api.post<UserProfile>(`/api/users/${id}/clone`, overrides);

export const listAccounts = (profileId: string) =>
  api.get<Account[]>(`/api/users/${profileId}/accounts`);
export const createAccount = (profileId: string, account: Omit<Account, "id" | "userProfileId">) =>
  api.post<Account>(`/api/users/${profileId}/accounts`, account);
export const updateAccount = (id: string, account: Omit<Account, "id" | "userProfileId">) =>
  api.put<Account>(`/api/accounts/${id}`, account);
export const deleteAccount = (id: string) => api.delete(`/api/accounts/${id}`);

export const listProperties = (profileId: string) =>
  api.get<Property[]>(`/api/users/${profileId}/properties`);
export const createProperty = (
  profileId: string,
  property: Omit<Property, "id" | "userProfileId">,
) => api.post<Property>(`/api/users/${profileId}/properties`, property);
export const updateProperty = (id: string, property: Omit<Property, "id" | "userProfileId">) =>
  api.put<Property>(`/api/properties/${id}`, property);
export const deleteProperty = (id: string) => api.delete(`/api/properties/${id}`);
export const cloneProperty = (id: string, overrides?: Partial<Property>) =>
  api.post<Property>(`/api/properties/${id}/clone`, overrides ?? {});

export const listIncomeSources = (scenarioId: string) =>
  api.get<IncomeSource[]>(`/api/scenarios/${scenarioId}/incomeSources`);
export const createIncomeSource = (
  scenarioId: string,
  source: Omit<IncomeSource, "id" | "scenarioId">,
) => api.post<IncomeSource>(`/api/scenarios/${scenarioId}/incomeSources`, source);
export const updateIncomeSource = (id: string, source: Omit<IncomeSource, "id" | "scenarioId">) =>
  api.put<IncomeSource>(`/api/incomeSources/${id}`, source);
export const deleteIncomeSource = (id: string) => api.delete(`/api/incomeSources/${id}`);

export const listScenarios = (profileId: string) =>
  api.get<Scenario[]>(`/api/users/${profileId}/scenarios`);
export const getScenario = (id: string) => api.get<Scenario>(`/api/scenarios/${id}`);
export const createScenario = (
  profileId: string,
  scenario: Omit<Scenario, "id" | "userProfileId">,
) => api.post<Scenario>(`/api/users/${profileId}/scenarios`, scenario);
export const updateScenario = (id: string, scenario: Omit<Scenario, "id" | "userProfileId">) =>
  api.put<Scenario>(`/api/scenarios/${id}`, scenario);
export const deleteScenario = (id: string) => api.delete(`/api/scenarios/${id}`);

export const runSimulation = (scenarioId: string) =>
  api.post<SimulationResult>(`/api/scenarios/${scenarioId}/simulate`);
export const getSimulation = (id: string) => api.get<SimulationResult>(`/api/simulations/${id}`);
export const listSimulations = (profileId: string) =>
  api.get<SimulationResult[]>(`/api/users/${profileId}/simulations`);

export default api;
