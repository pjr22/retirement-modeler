import { render } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import { ThemeProvider, createTheme } from "@mui/material";
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
}

export function renderWithRouter(ui: ReactElement, { route = "/", path }: Options = {}) {
  return render(
    <ThemeProvider theme={theme}>
      <MemoryRouter initialEntries={[route]}>
        {path ? (
          <Routes>
            <Route path={path} element={ui} />
          </Routes>
        ) : (
          ui
        )}
      </MemoryRouter>
    </ThemeProvider>,
  );
}
