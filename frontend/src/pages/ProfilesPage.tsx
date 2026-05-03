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
  Tooltip,
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import DeleteIcon from "@mui/icons-material/Delete";
import ContentCopyIcon from "@mui/icons-material/ContentCopy";
import {
  listUserProfiles,
  createUserProfile,
  deleteUserProfile,
  cloneUserProfile,
} from "../api";
import type { FilingStatus, UserProfile } from "../types";
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

type DialogMode = { kind: "create" } | { kind: "clone"; sourceId: string };

export default function ProfilesPage() {
  const navigate = useNavigate();
  const [profiles, setProfiles] = useState<UserProfile[]>([]);
  const [error, setError] = useState("");
  const [dialogMode, setDialogMode] = useState<DialogMode | null>(null);
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

  const openCreate = () => {
    setForm(emptyForm);
    setDialogMode({ kind: "create" });
  };

  const openClone = (source: UserProfile) => {
    setForm({
      name: `Copy of ${source.name}`,
      dateOfBirth: source.dateOfBirth,
      plannedRetirementDate: source.plannedRetirementDate,
      lifeExpectancy: source.lifeExpectancy,
      filingStatus: source.filingStatus,
    });
    setDialogMode({ kind: "clone", sourceId: source.id });
  };

  const closeDialog = () => {
    setDialogMode(null);
    setForm(emptyForm);
  };

  const handleSubmit = async () => {
    if (!dialogMode) return;
    try {
      if (dialogMode.kind === "create") {
        await createUserProfile(form);
      } else {
        await cloneUserProfile(dialogMode.sourceId, form);
      }
      closeDialog();
      loadProfiles();
    } catch {
      setError(dialogMode.kind === "clone" ? "Failed to clone profile" : "Failed to create profile");
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

  const dialogTitle = dialogMode?.kind === "clone" ? "Clone Profile" : "Create Profile";
  const submitLabel = dialogMode?.kind === "clone" ? "Clone" : "Create";

  return (
    <Box>
      {error && (
        <Alert severity="error" onClose={() => setError("")} sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}
      <Box sx={{ display: "flex", justifyContent: "space-between", mb: 3 }}>
        <Typography variant="h4">Retirement Profiles</Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={openCreate}>
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
                <Box>
                  <Tooltip title="Clone">
                    <IconButton edge="end" onClick={() => openClone(p)} sx={{ mr: 1 }}>
                      <ContentCopyIcon />
                    </IconButton>
                  </Tooltip>
                  <Tooltip title="Delete">
                    <IconButton edge="end" onClick={() => handleDelete(p.id)} color="error">
                      <DeleteIcon />
                    </IconButton>
                  </Tooltip>
                </Box>
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

      <Dialog open={dialogMode !== null} onClose={closeDialog} maxWidth="sm" fullWidth>
        <DialogTitle>{dialogTitle}</DialogTitle>
        <DialogContent sx={{ display: "flex", flexDirection: "column", gap: 2, mt: 1 }}>
          {dialogMode?.kind === "clone" && (
            <Typography variant="body2" color="text.secondary">
              Accounts, scenarios, and income sources will be copied from the source profile.
            </Typography>
          )}
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
          <Button onClick={closeDialog}>Cancel</Button>
          <Button
            onClick={handleSubmit}
            variant="contained"
            disabled={!form.name || !form.dateOfBirth || !form.plannedRetirementDate}
          >
            {submitLabel}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
