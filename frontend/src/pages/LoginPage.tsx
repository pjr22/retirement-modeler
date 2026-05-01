import { useState } from "react";
import { useNavigate, Link } from "react-router";
import { Box, TextField, Button, Typography, Paper, Alert } from "@mui/material";
import { login as loginApi } from "../api";
import { useAuth } from "../components/AuthContext";

export default function LoginPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const navigate = useNavigate();
  const { setAuth } = useAuth();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    try {
      const res = await loginApi(email, password);
      setAuth(res.data);
      navigate("/");
    } catch {
      setError("Invalid email or password");
    }
  };

  return (
    <Box sx={{ maxWidth: 400, mx: "auto", mt: 8 }}>
      <Paper sx={{ p: 4 }}>
        <Typography variant="h5" gutterBottom>
          Login
        </Typography>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}
        <form onSubmit={handleSubmit}>
          <TextField
            label="Email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            fullWidth
            required
            sx={{ mb: 2 }}
          />
          <TextField
            label="Password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            fullWidth
            required
            sx={{ mb: 2 }}
          />
          <Button type="submit" variant="contained" fullWidth sx={{ mb: 2 }}>
            Login
          </Button>
        </form>
        <Typography variant="body2" sx={{ textAlign: "center" }}>
          Don&apos;t have an account?{" "}
          <Link to="/register" style={{ textDecoration: "none" }}>
            Register
          </Link>
        </Typography>
      </Paper>
    </Box>
  );
}
