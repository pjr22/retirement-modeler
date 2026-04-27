import { Outlet, Link } from "react-router";
import { AppBar, Toolbar, Typography, Box } from "@mui/material";
import SavingsRoundedIcon from "@mui/icons-material/SavingsRounded";
import HealthStatus from "./HealthStatus";

export default function Layout() {
  return (
    <>
      <AppBar position="static">
        <Toolbar>
          <SavingsRoundedIcon sx={{ mr: 1.5 }} />
          <Typography
            variant="h6"
            component={Link}
            to="/"
            sx={{ flexGrow: 1, color: "inherit", textDecoration: "none" }}
          >
            Retirement Modeler
          </Typography>
          <HealthStatus />
        </Toolbar>
      </AppBar>
      <Box component="main" sx={{ p: 3, maxWidth: 1200, mx: "auto" }}>
        <Outlet />
      </Box>
    </>
  );
}
