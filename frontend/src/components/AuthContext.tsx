import { createContext, useContext, useState, useEffect, useCallback, type ReactNode } from "react";
import type { AuthState } from "../types";
import { getStoredAuth, storeAuth, clearAuth } from "../api";
import type { AuthResponse } from "../types";

interface AuthContextType extends AuthState {
  setAuth: (auth: AuthResponse) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [authState, setAuthState] = useState<AuthState>(getStoredAuth);

  useEffect(() => {
    setAuthState(getStoredAuth());
  }, []);

  const setAuth = useCallback((auth: AuthResponse) => {
    storeAuth(auth);
    setAuthState({
      token: auth.token,
      userId: auth.userId,
      email: auth.email,
      isAuthenticated: true,
    });
  }, []);

  const logout = useCallback(() => {
    clearAuth();
    setAuthState({ token: null, userId: null, email: null, isAuthenticated: false });
  }, []);

  return (
    <AuthContext.Provider value={{ ...authState, setAuth, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}
