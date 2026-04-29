import { BrowserRouter, Routes, Route } from "react-router";
import { ThemeProvider, createTheme, CssBaseline } from "@mui/material";
import { AuthProvider } from "./components/AuthContext";
import Layout from "./components/Layout";
import ProtectedRoute from "./components/ProtectedRoute";
import LoginPage from "./pages/LoginPage";
import RegisterPage from "./pages/RegisterPage";
import ProfilesPage from "./pages/ProfilesPage";
import ProfileDetailPage from "./pages/ProfileDetailPage";
import AccountsPage from "./pages/AccountsPage";
import ScenariosPage from "./pages/ScenariosPage";
import ScenarioDetailPage from "./pages/ScenarioDetailPage";
import SimulationResultsPage from "./pages/SimulationResultsPage";

const theme = createTheme({
  palette: {
    mode: "light",
    primary: { main: "#1565c0" },
    secondary: { main: "#2e7d32" },
  },
});

function App() {
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <BrowserRouter>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route
              element={
                <ProtectedRoute>
                  <Layout />
                </ProtectedRoute>
              }
            >
              <Route path="/" element={<ProfilesPage />} />
              <Route path="/profiles/:profileId" element={<ProfileDetailPage />} />
              <Route path="/profiles/:profileId/accounts" element={<AccountsPage />} />
              <Route path="/profiles/:profileId/scenarios" element={<ScenariosPage />} />
              <Route path="/scenarios/:scenarioId" element={<ScenarioDetailPage />} />
              <Route path="/simulations/:simulationId" element={<SimulationResultsPage />} />
            </Route>
          </Routes>
        </AuthProvider>
      </BrowserRouter>
    </ThemeProvider>
  );
}

export default App;
