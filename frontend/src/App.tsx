import { ThemeProvider, createTheme, CssBaseline, AppBar, Toolbar, Typography, Box, Chip } from "@mui/material";
import SavingsRoundedIcon from "@mui/icons-material/SavingsRounded";
import HealthStatus from "./components/HealthStatus";

const theme = createTheme({
  palette: {
    mode: "light",
    primary: {
      main: "#1565c0",
    },
    secondary: {
      main: "#2e7d32",
    },
  },
});

function App() {
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <AppBar position="static">
        <Toolbar>
          <SavingsRoundedIcon sx={{ mr: 1.5 }} />
          <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
            Retirement Modeler
          </Typography>
          <HealthStatus />
        </Toolbar>
      </AppBar>
      <Box sx={{ p: 4, textAlign: "center", mt: 8 }}>
        <Typography variant="h3" gutterBottom>
          Welcome to Retirement Modeler
        </Typography>
        <Typography variant="h6" color="text.secondary" sx={{ maxWidth: 600, mx: "auto" }}>
          Plan your financial future with deterministic projections and Monte Carlo
          simulations. More features coming soon.
        </Typography>
        <Chip
          label="Phase 0 — Project Scaffolding"
          variant="outlined"
          sx={{ mt: 3 }}
        />
      </Box>
    </ThemeProvider>
  );
}

export default App;
