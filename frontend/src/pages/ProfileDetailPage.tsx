import { useCallback, useEffect, useState } from "react";
import { useParams, useNavigate, Link } from "react-router";
import {
  Box,
  Typography,
  TextField,
  Button,
  MenuItem,
  Paper,
  Grid,
  IconButton,
  Alert,
  Divider,
} from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import EditIcon from "@mui/icons-material/Edit";
import SaveIcon from "@mui/icons-material/Save";
import CancelIcon from "@mui/icons-material/Cancel";
import { getUserProfile, updateUserProfile } from "../api";
import type { UserProfile, FilingStatus } from "../types";
import { formatLongDate } from "../utils";

const FILING_STATUSES: { value: FilingStatus; label: string }[] = [
  { value: "SINGLE", label: "Single" },
  { value: "MARRIED_FILING_JOINTLY", label: "Married Filing Jointly" },
  { value: "MARRIED_FILING_SEPARATELY", label: "Married Filing Separately" },
  { value: "HEAD_OF_HOUSEHOLD", label: "Head of Household" },
];

export default function ProfileDetailPage() {
  const { profileId } = useParams<{ profileId: string }>();
  const navigate = useNavigate();
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [editing, setEditing] = useState(false);
  const [error, setError] = useState("");
  const [form, setForm] = useState({
    name: "",
    dateOfBirth: "",
    plannedRetirementDate: "",
    lifeExpectancy: 90,
    filingStatus: "SINGLE" as FilingStatus,
  });

  const loadProfile = useCallback(async () => {
    if (!profileId) return;
    try {
      const res = await getUserProfile(profileId);
      setProfile(res.data);
      setForm({
        name: res.data.name,
        dateOfBirth: res.data.dateOfBirth,
        plannedRetirementDate: res.data.plannedRetirementDate,
        lifeExpectancy: res.data.lifeExpectancy,
        filingStatus: res.data.filingStatus,
      });
    } catch {
      setError("Failed to load profile");
    }
  }, [profileId]);

  useEffect(() => {
    loadProfile();
  }, [loadProfile]);

  const handleSave = async () => {
    if (!profileId) return;
    try {
      await updateUserProfile(profileId, form);
      setEditing(false);
      loadProfile();
    } catch {
      setError("Failed to update profile");
    }
  };

  if (!profile) {
    return error ? <Alert severity="error">{error}</Alert> : <Typography>Loading...</Typography>;
  }

  return (
    <Box>
      {error && (
        <Alert severity="error" onClose={() => setError("")} sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      <Box sx={{ display: "flex", alignItems: "center", mb: 3 }}>
        <IconButton onClick={() => navigate("/")} sx={{ mr: 1 }}>
          <ArrowBackIcon />
        </IconButton>
        <Typography variant="h4" sx={{ flexGrow: 1 }}>
          {profile.name}
        </Typography>
        {!editing ? (
          <Button startIcon={<EditIcon />} onClick={() => setEditing(true)}>
            Edit
          </Button>
        ) : (
          <Box>
            <Button
              startIcon={<CancelIcon />}
              onClick={() => {
                setEditing(false);
                loadProfile();
              }}
              sx={{ mr: 1 }}
            >
              Cancel
            </Button>
            <Button startIcon={<SaveIcon />} variant="contained" onClick={handleSave}>
              Save
            </Button>
          </Box>
        )}
      </Box>

      <Paper sx={{ p: 3, mb: 3 }}>
        <Typography variant="h6" gutterBottom>
          Profile Details
        </Typography>
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField
              label="Name"
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              fullWidth
              disabled={!editing}
            />
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField
              label="Date of Birth"
              type="date"
              value={form.dateOfBirth}
              onChange={(e) => setForm({ ...form, dateOfBirth: e.target.value })}
              fullWidth
              disabled={!editing}
              slotProps={{ inputLabel: { shrink: true } }}
            />
          </Grid>
          <Grid size={{ xs: 12, sm: 4 }}>
            <TextField
              label="Planned Retirement Date"
              type="date"
              value={form.plannedRetirementDate}
              onChange={(e) => setForm({ ...form, plannedRetirementDate: e.target.value })}
              fullWidth
              disabled={!editing}
              slotProps={{ inputLabel: { shrink: true } }}
              helperText={!editing ? formatLongDate(form.plannedRetirementDate) : undefined}
            />
          </Grid>
          <Grid size={{ xs: 12, sm: 4 }}>
            <TextField
              label="Life Expectancy"
              type="number"
              value={form.lifeExpectancy}
              onChange={(e) => setForm({ ...form, lifeExpectancy: Number(e.target.value) })}
              fullWidth
              disabled={!editing}
            />
          </Grid>
          <Grid size={{ xs: 12, sm: 4 }}>
            <TextField
              label="Filing Status"
              select
              value={form.filingStatus}
              onChange={(e) => setForm({ ...form, filingStatus: e.target.value as FilingStatus })}
              fullWidth
              disabled={!editing}
            >
              {FILING_STATUSES.map((fs) => (
                <MenuItem key={fs.value} value={fs.value}>
                  {fs.label}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
        </Grid>
      </Paper>

      <Divider sx={{ my: 3 }} />

      <Box sx={{ display: "flex", gap: 2 }}>
        <Button variant="outlined" component={Link} to={`/profiles/${profileId}/accounts`}>
          Manage Accounts
        </Button>
        <Button variant="outlined" component={Link} to={`/profiles/${profileId}/scenarios`}>
          Manage Scenarios
        </Button>
      </Box>
    </Box>
  );
}
