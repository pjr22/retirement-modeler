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
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
} from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import AddIcon from "@mui/icons-material/Add";
import DeleteIcon from "@mui/icons-material/Delete";
import EditIcon from "@mui/icons-material/Edit";
import SaveIcon from "@mui/icons-material/Save";
import CancelIcon from "@mui/icons-material/Cancel";
import { getUserProfile, updateUserProfile } from "../api";
import type { UserProfile, FilingStatus, IncomeSource } from "../types";

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
    plannedRetirementAge: 65,
    lifeExpectancy: 90,
    filingStatus: "SINGLE" as FilingStatus,
  });
  const [incomeSources, setIncomeSources] = useState<IncomeSource[]>([]);
  const [newIncome, setNewIncome] = useState({
    name: "",
    annualAmount: 0,
    endAge: null as number | null,
  });

  const loadProfile = useCallback(async () => {
    if (!profileId) return;
    try {
      const res = await getUserProfile(profileId);
      setProfile(res.data);
      setForm({
        name: res.data.name,
        dateOfBirth: res.data.dateOfBirth,
        plannedRetirementAge: res.data.plannedRetirementAge,
        lifeExpectancy: res.data.lifeExpectancy,
        filingStatus: res.data.filingStatus,
      });
      setIncomeSources(res.data.incomeSources);
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
      await updateUserProfile(profileId, { ...form, incomeSources });
      setEditing(false);
      loadProfile();
    } catch {
      setError("Failed to update profile");
    }
  };

  const addIncomeSource = () => {
    if (!newIncome.name) return;
    setIncomeSources([...incomeSources, { id: crypto.randomUUID(), ...newIncome }]);
    setNewIncome({ name: "", annualAmount: 0, endAge: null });
  };

  const removeIncomeSource = (id: string) => {
    setIncomeSources(incomeSources.filter((is) => is.id !== id));
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
              label="Planned Retirement Age"
              type="number"
              value={form.plannedRetirementAge}
              onChange={(e) => setForm({ ...form, plannedRetirementAge: Number(e.target.value) })}
              fullWidth
              disabled={!editing}
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

      <Paper sx={{ p: 3, mb: 3 }}>
        <Typography variant="h6" gutterBottom>
          Income Sources
        </Typography>
        {incomeSources.length > 0 && (
          <Table size="small" sx={{ mb: 2 }}>
            <TableHead>
              <TableRow>
                <TableCell>Name</TableCell>
                <TableCell align="right">Annual Amount</TableCell>
                <TableCell align="right">End Age</TableCell>
                {editing && <TableCell align="right">Actions</TableCell>}
              </TableRow>
            </TableHead>
            <TableBody>
              {incomeSources.map((is) => (
                <TableRow key={is.id}>
                  <TableCell>{is.name}</TableCell>
                  <TableCell align="right">${is.annualAmount.toLocaleString()}</TableCell>
                  <TableCell align="right">{is.endAge ?? "Ongoing"}</TableCell>
                  {editing && (
                    <TableCell align="right">
                      <IconButton
                        size="small"
                        color="error"
                        onClick={() => removeIncomeSource(is.id)}
                      >
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    </TableCell>
                  )}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
        {editing && (
          <Box sx={{ display: "flex", gap: 1, alignItems: "center" }}>
            <TextField
              label="Income Name"
              value={newIncome.name}
              onChange={(e) => setNewIncome({ ...newIncome, name: e.target.value })}
              size="small"
            />
            <TextField
              label="Annual Amount"
              type="number"
              value={newIncome.annualAmount || ""}
              onChange={(e) => setNewIncome({ ...newIncome, annualAmount: Number(e.target.value) })}
              size="small"
            />
            <TextField
              label="End Age"
              type="number"
              value={newIncome.endAge ?? ""}
              onChange={(e) =>
                setNewIncome({
                  ...newIncome,
                  endAge: e.target.value ? Number(e.target.value) : null,
                })
              }
              size="small"
              placeholder="Ongoing"
            />
            <Button startIcon={<AddIcon />} onClick={addIncomeSource} disabled={!newIncome.name}>
              Add
            </Button>
          </Box>
        )}
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
