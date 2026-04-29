import { type ReactNode } from "react";
import { Navigate } from "react-router";
import { useAuth } from "./AuthContext";

export default function ProtectedRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth();
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
}
