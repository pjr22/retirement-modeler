import { render } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import { ThemeProvider, createTheme } from "@mui/material";
import { AuthProvider } from "../components/AuthContext";
import type { ReactElement } from "react";

const theme = createTheme({
  palette: {
    mode: "light",
    primary: { main: "#1565c0" },
    secondary: { main: "#2e7d32" },
  },
});

interface Options {
  route?: string;
  path?: string;
  authenticated?: boolean;
}

export function renderWithRouter(
  ui: ReactElement,
  { route = "/", path, authenticated = true }: Options = {},
) {
  if (authenticated) {
    localStorage.setItem("retirement_modeler_token", "test-token");
    localStorage.setItem("retirement_modeler_user_id", "test-user-id");
    localStorage.setItem("retirement_modeler_email", "test@test.com");
  } else {
    localStorage.removeItem("retirement_modeler_token");
    localStorage.removeItem("retirement_modeler_user_id");
    localStorage.removeItem("retirement_modeler_email");
  }

  return render(
    <ThemeProvider theme={theme}>
      <AuthProvider>
        <MemoryRouter initialEntries={[route]}>
          {path ? (
            <Routes>
              <Route path={path} element={ui} />
            </Routes>
          ) : (
            ui
          )}
        </MemoryRouter>
      </AuthProvider>
    </ThemeProvider>,
  );
}
