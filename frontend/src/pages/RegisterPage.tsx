import { useState } from "react";
import { useNavigate, Link } from "react-router";
import { Box, TextField, Button, Typography, Paper, Alert } from "@mui/material";
import { register as registerApi } from "../api";
import { useAuth } from "../components/AuthContext";

export default function RegisterPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const navigate = useNavigate();
  const { setAuth } = useAuth();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    if (password.length < 8) {
      setError("Password must be at least 8 characters");
      return;
    }
    try {
      const res = await registerApi(email, password);
      setAuth(res.data);
      navigate("/");
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { detail?: string } } })?.response?.data?.detail ||
        "Registration failed";
      setError(msg);
    }
  };

  return (
    <Box sx={{ maxWidth: 400, mx: "auto", mt: 8 }}>
      <Paper sx={{ p: 4 }}>
        <Typography variant="h5" gutterBottom>
          Register
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
            helperText="At least 8 characters"
            sx={{ mb: 2 }}
          />
          <Button type="submit" variant="contained" fullWidth sx={{ mb: 2 }}>
            Register
          </Button>
        </form>
        <Typography variant="body2" textAlign="center">
          Already have an account?{" "}
          <Link to="/login" style={{ textDecoration: "none" }}>
            Login
          </Link>
        </Typography>
      </Paper>
    </Box>
  );
}
