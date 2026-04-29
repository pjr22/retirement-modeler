import axios from "axios";
import type { UserProfile, Account, Scenario, SimulationResult, AuthResponse } from "./types";

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
