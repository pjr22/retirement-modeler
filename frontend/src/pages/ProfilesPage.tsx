import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router";
import {
  Box,
  Typography,
  Button,
  List,
  ListItem,
  ListItemText,
  ListItemButton,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  MenuItem,
  Alert,
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import DeleteIcon from "@mui/icons-material/Delete";
import { listUserProfiles, createUserProfile, deleteUserProfile } from "../api";
import type { FilingStatus } from "../types";
import { formatMonthYear } from "../utils";

const FILING_STATUSES: { value: FilingStatus; label: string }[] = [
  { value: "SINGLE", label: "Single" },
  { value: "MARRIED_FILING_JOINTLY", label: "Married Filing Jointly" },
  { value: "MARRIED_FILING_SEPARATELY", label: "Married Filing Separately" },
  { value: "HEAD_OF_HOUSEHOLD", label: "Head of Household" },
];

const emptyForm = {
  name: "",
  dateOfBirth: "",
  plannedRetirementDate: "",
  lifeExpectancy: 90,
  filingStatus: "SINGLE" as FilingStatus,
};

export default function ProfilesPage() {
  const navigate = useNavigate();
  const [profiles, setProfiles] = useState<
    Awaited<ReturnType<typeof listUserProfiles>>["data"][number][]
  >([]);
  const [error, setError] = useState("");
  const [dialogOpen, setDialogOpen] = useState(false);
  const [form, setForm] = useState(emptyForm);

  const loadProfiles = useCallback(async () => {
    try {
      const res = await listUserProfiles();
      setProfiles(res.data);
    } catch {
      setError("Failed to load profiles");
    }
  }, []);

  useEffect(() => {
    loadProfiles();
  }, [loadProfiles]);

  const handleCreate = async () => {
    try {
      await createUserProfile(form);
      setDialogOpen(false);
      setForm(emptyForm);
      loadProfiles();
    } catch {
      setError("Failed to create profile");
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await deleteUserProfile(id);
      loadProfiles();
    } catch {
      setError("Failed to delete profile");
    }
  };

  return (
    <Box>
      {error && (
        <Alert severity="error" onClose={() => setError("")} sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}
      <Box sx={{ display: "flex", justifyContent: "space-between", mb: 3 }}>
        <Typography variant="h4">Retirement Profiles</Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setDialogOpen(true)}>
          Create Profile
        </Button>
      </Box>

      {profiles.length === 0 ? (
        <Typography color="text.secondary" sx={{ mt: 4, textAlign: "center" }}>
          No profiles yet. Create one to get started.
        </Typography>
      ) : (
        <List>
          {profiles.map((p) => (
            <ListItem
              key={p.id}
              secondaryAction={
                <IconButton edge="end" onClick={() => handleDelete(p.id)} color="error">
                  <DeleteIcon />
                </IconButton>
              }
              disablePadding
            >
              <ListItemButton onClick={() => navigate(`/profiles/${p.id}`)}>
                <ListItemText
                  primary={p.name}
                  secondary={`Retire ${formatMonthYear(p.plannedRetirementDate)} · Life expectancy ${p.lifeExpectancy} · ${p.filingStatus.replace(/_/g, " ")}`}
                />
              </ListItemButton>
            </ListItem>
          ))}
        </List>
      )}

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Create Profile</DialogTitle>
        <DialogContent sx={{ display: "flex", flexDirection: "column", gap: 2, mt: 1 }}>
          <TextField
            label="Name"
            value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })}
            fullWidth
            required
          />
          <TextField
            label="Date of Birth"
            type="date"
            value={form.dateOfBirth}
            onChange={(e) => setForm({ ...form, dateOfBirth: e.target.value })}
            fullWidth
            required
            slotProps={{ inputLabel: { shrink: true } }}
          />
          <TextField
            label="Planned Retirement Date"
            type="date"
            value={form.plannedRetirementDate}
            onChange={(e) => setForm({ ...form, plannedRetirementDate: e.target.value })}
            fullWidth
            required
            slotProps={{ inputLabel: { shrink: true } }}
          />
          <TextField
            label="Life Expectancy"
            type="number"
            value={form.lifeExpectancy}
            onChange={(e) => setForm({ ...form, lifeExpectancy: Number(e.target.value) })}
            fullWidth
          />
          <TextField
            label="Filing Status"
            select
            value={form.filingStatus}
            onChange={(e) => setForm({ ...form, filingStatus: e.target.value as FilingStatus })}
            fullWidth
          >
            {FILING_STATUSES.map((fs) => (
              <MenuItem key={fs.value} value={fs.value}>
                {fs.label}
              </MenuItem>
            ))}
          </TextField>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>Cancel</Button>
          <Button
            onClick={handleCreate}
            variant="contained"
            disabled={!form.name || !form.dateOfBirth || !form.plannedRetirementDate}
          >
            Create
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
