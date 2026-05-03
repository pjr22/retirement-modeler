import { useCallback, useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router";
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
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Tooltip,
} from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import EditIcon from "@mui/icons-material/Edit";
import SaveIcon from "@mui/icons-material/Save";
import CancelIcon from "@mui/icons-material/Cancel";
import AddIcon from "@mui/icons-material/Add";
import DeleteIcon from "@mui/icons-material/Delete";
import ContentCopyIcon from "@mui/icons-material/ContentCopy";
import PlayArrowIcon from "@mui/icons-material/PlayArrow";
import CircularProgress from "@mui/material/CircularProgress";
import {
  getUserProfile,
  updateUserProfile,
  listAccounts,
  createAccount,
  updateAccount,
  deleteAccount,
  listScenarios,
  deleteScenario,
  runSimulation,
} from "../api";
import type { Account, AccountType, Scenario, UserProfile, FilingStatus } from "../types";
import { formatLongDate } from "../utils";

const FILING_STATUSES: { value: FilingStatus; label: string }[] = [
  { value: "SINGLE", label: "Single" },
  { value: "MARRIED_FILING_JOINTLY", label: "Married Filing Jointly" },
  { value: "MARRIED_FILING_SEPARATELY", label: "Married Filing Separately" },
  { value: "HEAD_OF_HOUSEHOLD", label: "Head of Household" },
];

const ACCOUNT_TYPES: { value: AccountType; label: string }[] = [
  { value: "TRADITIONAL_401K", label: "Traditional 401(k)" },
  { value: "TRADITIONAL_IRA", label: "Traditional IRA" },
  { value: "ROTH_401K", label: "Roth 401(k)" },
  { value: "ROTH_IRA", label: "Roth IRA" },
  { value: "TAXABLE_BROKERAGE", label: "Taxable Brokerage" },
  { value: "SAVINGS", label: "Savings" },
  { value: "HSA", label: "HSA" },
];

const CONTRIBUTION_TYPES: AccountType[] = [
  "TRADITIONAL_401K",
  "TRADITIONAL_IRA",
  "ROTH_401K",
  "ROTH_IRA",
  "HSA",
];

const accountTypeLabel = (t: AccountType) =>
  ACCOUNT_TYPES.find((at) => at.value === t)?.label ?? t;

interface AccountForm {
  name: string;
  accountType: AccountType;
  balance: number;
  annualContribution: number | null;
}

const emptyAccountForm: AccountForm = {
  name: "",
  accountType: "TRADITIONAL_401K",
  balance: 0,
  annualContribution: null,
};

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

  const [accounts, setAccounts] = useState<Account[]>([]);
  const [accountDialogOpen, setAccountDialogOpen] = useState(false);
  const [editingAccountId, setEditingAccountId] = useState<string | null>(null);
  const [cloningAccount, setCloningAccount] = useState(false);
  const [accountForm, setAccountForm] = useState<AccountForm>(emptyAccountForm);

  const [scenarios, setScenarios] = useState<Scenario[]>([]);
  const [runningScenarioId, setRunningScenarioId] = useState<string | null>(null);

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

  const loadAccounts = useCallback(async () => {
    if (!profileId) return;
    try {
      const res = await listAccounts(profileId);
      setAccounts(res.data);
    } catch {
      setError("Failed to load accounts");
    }
  }, [profileId]);

  const loadScenarios = useCallback(async () => {
    if (!profileId) return;
    try {
      const res = await listScenarios(profileId);
      setScenarios(res.data);
    } catch {
      setError("Failed to load scenarios");
    }
  }, [profileId]);

  useEffect(() => {
    loadProfile();
    loadAccounts();
    loadScenarios();
  }, [loadProfile, loadAccounts, loadScenarios]);

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

  const openCreateAccount = () => {
    setEditingAccountId(null);
    setCloningAccount(false);
    setAccountForm(emptyAccountForm);
    setAccountDialogOpen(true);
  };

  const openEditAccount = (account: Account) => {
    setEditingAccountId(account.id);
    setCloningAccount(false);
    setAccountForm({
      name: account.name,
      accountType: account.accountType,
      balance: account.balance,
      annualContribution: account.annualContribution,
    });
    setAccountDialogOpen(true);
  };

  const openCloneAccount = (account: Account) => {
    setEditingAccountId(null);
    setCloningAccount(true);
    setAccountForm({
      name: `Copy of ${account.name}`,
      accountType: account.accountType,
      balance: account.balance,
      annualContribution: account.annualContribution,
    });
    setAccountDialogOpen(true);
  };

  const handleSaveAccount = async () => {
    if (!profileId) return;
    try {
      if (editingAccountId) {
        await updateAccount(editingAccountId, accountForm);
      } else {
        await createAccount(profileId, accountForm);
      }
      setAccountDialogOpen(false);
      await loadAccounts();
    } catch {
      setError("Failed to save account");
    }
  };

  const handleDeleteAccount = async (id: string) => {
    try {
      await deleteAccount(id);
      await loadAccounts();
    } catch {
      setError("Failed to delete account");
    }
  };

  const handleDeleteScenario = async (id: string) => {
    try {
      await deleteScenario(id);
      await loadScenarios();
    } catch {
      setError("Failed to delete scenario");
    }
  };

  const handleRunScenario = async (id: string) => {
    setRunningScenarioId(id);
    try {
      const res = await runSimulation(id);
      navigate(`/simulations/${res.data.id}`);
    } catch {
      setError("Failed to run simulation");
    } finally {
      setRunningScenarioId(null);
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

      <Paper sx={{ p: 3, mb: 3 }}>
        <Box sx={{ display: "flex", alignItems: "center", mb: 2 }}>
          <Typography variant="h6" sx={{ flexGrow: 1 }}>
            Accounts
          </Typography>
          <Button startIcon={<AddIcon />} variant="outlined" onClick={openCreateAccount}>
            Add Account
          </Button>
        </Box>
        {accounts.length === 0 ? (
          <Typography color="text.secondary">
            No accounts yet. Add 401(k), IRA, brokerage, savings, or HSA accounts to model
            balances and contributions.
          </Typography>
        ) : (
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Name</TableCell>
                <TableCell>Type</TableCell>
                <TableCell align="right">Balance</TableCell>
                <TableCell align="right">Annual Contribution</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {accounts.map((account) => (
                <TableRow key={account.id}>
                  <TableCell>{account.name}</TableCell>
                  <TableCell>{accountTypeLabel(account.accountType)}</TableCell>
                  <TableCell align="right">${account.balance.toLocaleString()}</TableCell>
                  <TableCell align="right">
                    {account.annualContribution != null
                      ? `$${account.annualContribution.toLocaleString()}`
                      : "—"}
                  </TableCell>
                  <TableCell align="right">
                    <Tooltip title="Clone">
                      <IconButton size="small" onClick={() => openCloneAccount(account)}>
                        <ContentCopyIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="Edit">
                      <IconButton size="small" onClick={() => openEditAccount(account)}>
                        <EditIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="Delete">
                      <IconButton
                        size="small"
                        color="error"
                        onClick={() => handleDeleteAccount(account.id)}
                      >
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Paper>

      <Paper sx={{ p: 3, mb: 3 }}>
        <Box sx={{ display: "flex", alignItems: "center", mb: 2 }}>
          <Typography variant="h6" sx={{ flexGrow: 1 }}>
            Scenarios
          </Typography>
          <Button
            startIcon={<AddIcon />}
            variant="outlined"
            onClick={() => navigate(`/scenarios/new?profileId=${profileId}`)}
          >
            Add Scenario
          </Button>
        </Box>
        {scenarios.length === 0 ? (
          <Typography color="text.secondary">
            No scenarios yet. Create one to start planning retirement projections.
          </Typography>
        ) : (
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Name</TableCell>
                <TableCell>Description</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {scenarios.map((s) => (
                <TableRow key={s.id}>
                  <TableCell>{s.name}</TableCell>
                  <TableCell>{s.description ?? ""}</TableCell>
                  <TableCell align="right">
                    <Tooltip
                      title={
                        s.accountIds.length === 0
                          ? "Add at least one account to run"
                          : "Run scenario"
                      }
                    >
                      <span>
                        <IconButton
                          size="small"
                          onClick={() => handleRunScenario(s.id)}
                          disabled={
                            runningScenarioId !== null || s.accountIds.length === 0
                          }
                        >
                          {runningScenarioId === s.id ? (
                            <CircularProgress size={16} />
                          ) : (
                            <PlayArrowIcon fontSize="small" />
                          )}
                        </IconButton>
                      </span>
                    </Tooltip>
                    <Tooltip title="Clone">
                      <IconButton
                        size="small"
                        onClick={() =>
                          navigate(`/scenarios/new?profileId=${profileId}&cloneFrom=${s.id}`)
                        }
                      >
                        <ContentCopyIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="Edit">
                      <IconButton size="small" onClick={() => navigate(`/scenarios/${s.id}`)}>
                        <EditIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="Delete">
                      <IconButton
                        size="small"
                        color="error"
                        onClick={() => handleDeleteScenario(s.id)}
                      >
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Paper>

      <Dialog
        open={accountDialogOpen}
        onClose={() => setAccountDialogOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>
          {editingAccountId ? "Edit Account" : cloningAccount ? "Clone Account" : "Add Account"}
        </DialogTitle>
        <DialogContent sx={{ display: "flex", flexDirection: "column", gap: 2, mt: 1 }}>
          <TextField
            label="Account Name"
            value={accountForm.name}
            onChange={(e) => setAccountForm({ ...accountForm, name: e.target.value })}
            fullWidth
            required
          />
          <TextField
            label="Account Type"
            select
            value={accountForm.accountType}
            onChange={(e) => {
              const newType = e.target.value as AccountType;
              setAccountForm({
                ...accountForm,
                accountType: newType,
                annualContribution: CONTRIBUTION_TYPES.includes(newType)
                  ? accountForm.annualContribution
                  : null,
              });
            }}
            fullWidth
            helperText="For pension or Social Security, add an Income Source on the scenario page instead."
          >
            {ACCOUNT_TYPES.map((t) => (
              <MenuItem key={t.value} value={t.value}>
                {t.label}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            label="Current Balance"
            type="number"
            value={accountForm.balance || ""}
            onChange={(e) => setAccountForm({ ...accountForm, balance: Number(e.target.value) })}
            fullWidth
          />
          {CONTRIBUTION_TYPES.includes(accountForm.accountType) && (
            <TextField
              label="Annual Contribution"
              type="number"
              value={accountForm.annualContribution ?? ""}
              onChange={(e) =>
                setAccountForm({
                  ...accountForm,
                  annualContribution: Number(e.target.value) || null,
                })
              }
              fullWidth
            />
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAccountDialogOpen(false)}>Cancel</Button>
          <Button onClick={handleSaveAccount} variant="contained" disabled={!accountForm.name}>
            {editingAccountId ? "Save" : cloningAccount ? "Clone" : "Add"}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
