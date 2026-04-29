import { Outlet, Link, useNavigate } from "react-router";
import { AppBar, Toolbar, Typography, Box, Button } from "@mui/material";
import SavingsRoundedIcon from "@mui/icons-material/SavingsRounded";
import HealthStatus from "./HealthStatus";
import { useAuth } from "./AuthContext";

export default function Layout() {
  const { email, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate("/login");
  };

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
          {email && (
            <Typography variant="body2" sx={{ mr: 2, color: "inherit" }}>
              {email}
            </Typography>
          )}
          <Button color="inherit" size="small" onClick={handleLogout} sx={{ mr: 1 }}>
            Logout
          </Button>
          <HealthStatus />
        </Toolbar>
      </AppBar>
      <Box component="main" sx={{ p: 3, maxWidth: 1200, mx: "auto" }}>
        <Outlet />
      </Box>
    </>
  );
}
