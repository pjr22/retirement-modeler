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
import InfoOutlinedIcon from "@mui/icons-material/InfoOutlined";
import {
  getUserProfile,
  updateUserProfile,
  listAccounts,
  createAccount,
  updateAccount,
  deleteAccount,
  listProperties,
  createProperty,
  updateProperty,
  deleteProperty,
  cloneProperty,
  listScenarios,
  deleteScenario,
  runSimulation,
} from "../api";
import type {
  Account,
  AccountType,
  Property,
  PropertyType,
  Scenario,
  UserProfile,
  FilingStatus,
} from "../types";
import { formatLongDate, calculateMonthlyPI, remainingMortgageMonths } from "../utils";

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

const PROPERTY_TYPES: { value: PropertyType; label: string }[] = [
  { value: "PRIMARY_RESIDENCE", label: "Primary Residence" },
  { value: "RENTAL", label: "Rental" },
  { value: "SECOND_HOME", label: "Second Home" },
  { value: "LAND", label: "Land" },
];

const propertyTypeLabel = (t: PropertyType) =>
  PROPERTY_TYPES.find((p) => p.value === t)?.label ?? t;

// Fractional inputs (rate, maintenance pct) are stored as strings during editing so users can
// type "0.0285" naturally — Number()-converting per-keystroke and collapsing 0 to "" makes
// decimal entry impossible. Term is a whole number so it stays numeric.
interface PropertyForm {
  name: string;
  type: PropertyType;
  currentValue: number;
  costBasis: number;
  mortgageBalance: number;
  mortgageAnnualRate: string;
  mortgageStartDate: string;
  mortgageTermYears: number;
  plannedSaleDate: string;
  postSaleMonthlyHousingCost: number;
  annualPropertyTax: number;
  annualInsurance: number;
  monthlyHoa: number;
  annualMaintenancePct: string;
}

const DEFAULT_MORTGAGE_TERM_YEARS = 30;

const emptyPropertyForm: PropertyForm = {
  name: "",
  type: "PRIMARY_RESIDENCE",
  currentValue: 0,
  costBasis: 0,
  mortgageBalance: 0,
  mortgageAnnualRate: "",
  mortgageStartDate: "",
  mortgageTermYears: DEFAULT_MORTGAGE_TERM_YEARS,
  plannedSaleDate: "",
  postSaleMonthlyHousingCost: 0,
  annualPropertyTax: 0,
  annualInsurance: 0,
  monthlyHoa: 0,
  annualMaintenancePct: "0.01",
};

// Small info-icon adornment with a tooltip — replaces helper text under fields.
function HelpAdornment({ title }: { title: string }) {
  return (
    <Tooltip title={title} placement="top">
      <InfoOutlinedIcon fontSize="small" sx={{ color: "action.active", cursor: "help" }} />
    </Tooltip>
  );
}

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

  const [properties, setProperties] = useState<Property[]>([]);
  const [propertyDialogOpen, setPropertyDialogOpen] = useState(false);
  const [editingPropertyId, setEditingPropertyId] = useState<string | null>(null);
  const [cloningPropertyFrom, setCloningPropertyFrom] = useState<string | null>(null);
  const [propertyForm, setPropertyForm] = useState<PropertyForm>(emptyPropertyForm);

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

  const loadProperties = useCallback(async () => {
    if (!profileId) return;
    try {
      const res = await listProperties(profileId);
      setProperties(res.data);
    } catch {
      setError("Failed to load properties");
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
    loadProperties();
    loadScenarios();
  }, [loadProfile, loadAccounts, loadProperties, loadScenarios]);

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

  const openCreateProperty = () => {
    setEditingPropertyId(null);
    setCloningPropertyFrom(null);
    setPropertyForm(emptyPropertyForm);
    setPropertyDialogOpen(true);
  };

  const openEditProperty = (property: Property) => {
    setEditingPropertyId(property.id);
    setCloningPropertyFrom(null);
    setPropertyForm({
      name: property.name,
      type: property.type,
      currentValue: property.currentValue,
      costBasis: property.costBasis,
      mortgageBalance: property.mortgageBalance,
      mortgageAnnualRate: property.mortgageAnnualRate ? String(property.mortgageAnnualRate) : "",
      mortgageStartDate: property.mortgageStartDate ?? "",
      mortgageTermYears: property.mortgageTermYears ?? DEFAULT_MORTGAGE_TERM_YEARS,
      plannedSaleDate: property.plannedSaleDate ?? "",
      postSaleMonthlyHousingCost: property.postSaleMonthlyHousingCost ?? 0,
      annualPropertyTax: property.annualPropertyTax,
      annualInsurance: property.annualInsurance,
      monthlyHoa: property.monthlyHoa,
      annualMaintenancePct: property.annualMaintenancePct
        ? String(property.annualMaintenancePct)
        : "0",
    });
    setPropertyDialogOpen(true);
  };

  const handleCloneProperty = async (property: Property) => {
    setCloningPropertyFrom(property.id);
    try {
      await cloneProperty(property.id);
      await loadProperties();
    } catch {
      setError("Failed to clone property");
    } finally {
      setCloningPropertyFrom(null);
    }
  };

  const handleSaveProperty = async () => {
    if (!profileId) return;
    const rate = Number(propertyForm.mortgageAnnualRate) || 0;
    const maintenancePct = Number(propertyForm.annualMaintenancePct) || 0;
    const remainingMonths = propertyForm.mortgageStartDate
      ? remainingMortgageMonths(propertyForm.mortgageStartDate, propertyForm.mortgageTermYears)
      : propertyForm.mortgageTermYears * 12;
    const computedPi = calculateMonthlyPI(
      propertyForm.mortgageBalance,
      rate,
      remainingMonths,
    );
    const payload = {
      name: propertyForm.name,
      type: propertyForm.type,
      currentValue: propertyForm.currentValue,
      costBasis: propertyForm.costBasis,
      mortgageBalance: propertyForm.mortgageBalance,
      mortgageAnnualRate: rate,
      mortgageMonthlyPi: Math.round(computedPi * 100) / 100,
      mortgageStartDate: propertyForm.mortgageStartDate || null,
      mortgageTermYears: propertyForm.mortgageTermYears || null,
      plannedSaleDate: propertyForm.plannedSaleDate || null,
      postSaleMonthlyHousingCost: propertyForm.postSaleMonthlyHousingCost,
      annualPropertyTax: propertyForm.annualPropertyTax,
      annualInsurance: propertyForm.annualInsurance,
      monthlyHoa: propertyForm.monthlyHoa,
      annualMaintenancePct: maintenancePct,
      sellingCostPct: null,
    };
    try {
      if (editingPropertyId) {
        await updateProperty(editingPropertyId, payload);
      } else {
        await createProperty(profileId, payload);
      }
      setPropertyDialogOpen(false);
      await loadProperties();
    } catch {
      setError("Failed to save property");
    }
  };

  const handleDeleteProperty = async (id: string) => {
    try {
      await deleteProperty(id);
      await loadProperties();
    } catch {
      setError("Failed to delete property");
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
            Real Property
          </Typography>
          <Button startIcon={<AddIcon />} variant="outlined" onClick={openCreateProperty}>
            Add Property
          </Button>
        </Box>
        {properties.length === 0 ? (
          <Typography color="text.secondary">
            No properties yet. Add a primary residence, rental, second home, or land to model
            housing in your retirement projections.
          </Typography>
        ) : (
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Name</TableCell>
                <TableCell>Type</TableCell>
                <TableCell align="right">Value</TableCell>
                <TableCell align="right">Mortgage Balance</TableCell>
                <TableCell align="right">Equity</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {properties.map((p) => (
                <TableRow key={p.id}>
                  <TableCell>{p.name}</TableCell>
                  <TableCell>{propertyTypeLabel(p.type)}</TableCell>
                  <TableCell align="right">${p.currentValue.toLocaleString()}</TableCell>
                  <TableCell align="right">
                    {p.mortgageBalance > 0 ? `$${p.mortgageBalance.toLocaleString()}` : "—"}
                  </TableCell>
                  <TableCell align="right">
                    ${(p.currentValue - p.mortgageBalance).toLocaleString()}
                  </TableCell>
                  <TableCell align="right">
                    <Tooltip title="Clone">
                      <span>
                        <IconButton
                          size="small"
                          onClick={() => handleCloneProperty(p)}
                          disabled={cloningPropertyFrom === p.id}
                        >
                          {cloningPropertyFrom === p.id ? (
                            <CircularProgress size={16} />
                          ) : (
                            <ContentCopyIcon fontSize="small" />
                          )}
                        </IconButton>
                      </span>
                    </Tooltip>
                    <Tooltip title="Edit">
                      <IconButton size="small" onClick={() => openEditProperty(p)}>
                        <EditIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="Delete">
                      <IconButton
                        size="small"
                        color="error"
                        onClick={() => handleDeleteProperty(p.id)}
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
        {/* Inline `style` (rather than sx) for paddingTop because MUI's default
            `.MuiDialogTitle-root + .MuiDialogContent-root { padding-top: 0 }` rule has higher
            specificity than the class sx generates — only an inline style beats it. */}
        <DialogContent
          sx={{ display: "flex", flexDirection: "column", gap: 2 }}
          style={{ paddingTop: 10 }}
        >
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

      <Dialog
        open={propertyDialogOpen}
        onClose={() => setPropertyDialogOpen(false)}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>{editingPropertyId ? "Edit Property" : "Add Property"}</DialogTitle>
        {/* Inline `style` is required to beat MUI's `.MuiDialogTitle-root + .MuiDialogContent-root
            { padding-top: 0 }` rule — see the matching comment on the account dialog above. */}
        <DialogContent
          sx={{ display: "flex", flexDirection: "column", gap: 2 }}
          style={{ paddingTop: 10 }}
        >
          <Grid container spacing={2} sx={{ mt: 0 }}>
            <Grid size={{ xs: 12, sm: 8 }}>
              <TextField
                label="Property Name"
                value={propertyForm.name}
                onChange={(e) => setPropertyForm({ ...propertyForm, name: e.target.value })}
                fullWidth
                required
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <TextField
                label="Type"
                select
                value={propertyForm.type}
                onChange={(e) =>
                  setPropertyForm({ ...propertyForm, type: e.target.value as PropertyType })
                }
                fullWidth
              >
                {PROPERTY_TYPES.map((t) => (
                  <MenuItem key={t.value} value={t.value}>
                    {t.label}
                  </MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                label="Current Value"
                type="number"
                value={propertyForm.currentValue || ""}
                onChange={(e) =>
                  setPropertyForm({ ...propertyForm, currentValue: Number(e.target.value) })
                }
                fullWidth
                slotProps={{
                  input: {
                    endAdornment: <HelpAdornment title="Today's market value." />,
                  },
                }}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                label="Cost Basis"
                type="number"
                value={propertyForm.costBasis || ""}
                onChange={(e) =>
                  setPropertyForm({ ...propertyForm, costBasis: Number(e.target.value) })
                }
                fullWidth
                slotProps={{
                  input: {
                    endAdornment: (
                      <HelpAdornment title="Original purchase price plus capital improvements. Used for capital gains on sale." />
                    ),
                  },
                }}
              />
            </Grid>
          </Grid>

          <Typography variant="subtitle2" sx={{ mt: 1 }}>
            Mortgage
          </Typography>
          <Grid container spacing={2} sx={{ mt: 0 }}>
            <Grid size={{ xs: 12, sm: 4 }}>
              <TextField
                label="Mortgage Start Date"
                type="date"
                value={propertyForm.mortgageStartDate}
                onChange={(e) =>
                  setPropertyForm({ ...propertyForm, mortgageStartDate: e.target.value })
                }
                fullWidth
                slotProps={{
                  inputLabel: { shrink: true },
                  input: {
                    endAdornment: (
                      <HelpAdornment title="Date the current mortgage started. For refinanced loans, use the refi date, not the original purchase date." />
                    ),
                  },
                }}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <TextField
                label="Mortgage Term (years)"
                type="number"
                value={propertyForm.mortgageTermYears || ""}
                onChange={(e) =>
                  setPropertyForm({
                    ...propertyForm,
                    mortgageTermYears: Number(e.target.value) || 0,
                  })
                }
                fullWidth
                slotProps={{
                  input: {
                    endAdornment: (
                      <HelpAdornment title="Original total term of the mortgage in years (typically 30)." />
                    ),
                  },
                }}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <TextField
                label="Current Balance"
                type="number"
                value={propertyForm.mortgageBalance || ""}
                onChange={(e) =>
                  setPropertyForm({ ...propertyForm, mortgageBalance: Number(e.target.value) })
                }
                fullWidth
                slotProps={{
                  input: {
                    endAdornment: (
                      <HelpAdornment title="Outstanding principal on the mortgage today. Set to 0 if paid off." />
                    ),
                  },
                }}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                label="Interest Rate (APR)"
                type="number"
                value={propertyForm.mortgageAnnualRate}
                onChange={(e) =>
                  setPropertyForm({ ...propertyForm, mortgageAnnualRate: e.target.value })
                }
                fullWidth
                slotProps={{
                  // step gives the up/down spinner arrows their increment (5 basis points).
                  // String-typed form state avoids the Number()-on-change zero-collapse that
                  // would otherwise break decimal typing like "0.0285".
                  htmlInput: { step: 0.0005, inputMode: "decimal" },
                  input: {
                    endAdornment: (
                      <HelpAdornment title="Annual percentage rate as a decimal — e.g. 0.0625 for 6.25%." />
                    ),
                  },
                }}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                label="Principal and Interest"
                value={(() => {
                  const rate = Number(propertyForm.mortgageAnnualRate) || 0;
                  const remainingMonths = propertyForm.mortgageStartDate
                    ? remainingMortgageMonths(
                        propertyForm.mortgageStartDate,
                        propertyForm.mortgageTermYears,
                      )
                    : propertyForm.mortgageTermYears * 12;
                  const pi = calculateMonthlyPI(
                    propertyForm.mortgageBalance,
                    rate,
                    remainingMonths,
                  );
                  return pi > 0
                    ? pi.toLocaleString("en-US", {
                        style: "currency",
                        currency: "USD",
                        maximumFractionDigits: 2,
                      })
                    : "—";
                })()}
                fullWidth
                slotProps={{
                  input: {
                    readOnly: true,
                    endAdornment: (
                      <HelpAdornment title="Monthly principal and interest payment, calculated from current balance, interest rate, and remaining months on the mortgage (start date + term − today)." />
                    ),
                  },
                }}
              />
            </Grid>
          </Grid>

          <Typography variant="subtitle2" sx={{ mt: 1 }}>
            Sale
          </Typography>
          <Grid container spacing={2} sx={{ mt: 0 }}>
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                label="Planned Sale Date"
                type="date"
                value={propertyForm.plannedSaleDate}
                onChange={(e) =>
                  setPropertyForm({ ...propertyForm, plannedSaleDate: e.target.value })
                }
                fullWidth
                slotProps={{
                  inputLabel: { shrink: true },
                  input: {
                    endAdornment: (
                      <HelpAdornment title="When you plan to sell this property. Leave blank if no sale is planned. On sale, the mortgage is paid off, capital gains are computed (with §121 exclusion for a primary residence), and net proceeds are deposited to Savings." />
                    ),
                  },
                }}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                label="Replacement Housing (monthly, today's cost)"
                type="number"
                value={propertyForm.postSaleMonthlyHousingCost || ""}
                onChange={(e) =>
                  setPropertyForm({
                    ...propertyForm,
                    postSaleMonthlyHousingCost: Number(e.target.value),
                  })
                }
                fullWidth
                disabled={!propertyForm.plannedSaleDate}
                slotProps={{
                  input: {
                    endAdornment: (
                      <HelpAdornment title="Monthly cost of new housing after this property is sold (rent, long-term care, etc.) in today's dollars. The simulation automatically inflates this each year until the sale takes effect and during ongoing payment. Set to 0 if you plan to live with family or have another paid-off residence." />
                    ),
                  },
                }}
              />
            </Grid>
          </Grid>

          <Typography variant="subtitle2" sx={{ mt: 1 }}>
            Recurring Expenses
          </Typography>
          <Grid container spacing={2} sx={{ mt: 0 }}>
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                label="Annual Property Tax"
                type="number"
                value={propertyForm.annualPropertyTax || ""}
                onChange={(e) =>
                  setPropertyForm({
                    ...propertyForm,
                    annualPropertyTax: Number(e.target.value),
                  })
                }
                fullWidth
                slotProps={{
                  input: {
                    endAdornment: (
                      <HelpAdornment title="Annual property tax paid. Grows with inflation in projections." />
                    ),
                  },
                }}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                label="Annual Insurance"
                type="number"
                value={propertyForm.annualInsurance || ""}
                onChange={(e) =>
                  setPropertyForm({
                    ...propertyForm,
                    annualInsurance: Number(e.target.value),
                  })
                }
                fullWidth
                slotProps={{
                  input: {
                    endAdornment: (
                      <HelpAdornment title="Annual homeowner's insurance premium. Grows with inflation in projections." />
                    ),
                  },
                }}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                label="Monthly HOA"
                type="number"
                value={propertyForm.monthlyHoa || ""}
                onChange={(e) =>
                  setPropertyForm({ ...propertyForm, monthlyHoa: Number(e.target.value) })
                }
                fullWidth
                slotProps={{
                  input: {
                    endAdornment: (
                      <HelpAdornment title="Monthly homeowner's association dues. Grows with inflation in projections." />
                    ),
                  },
                }}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                label="Annual Maintenance % of Value"
                type="number"
                value={propertyForm.annualMaintenancePct}
                onChange={(e) =>
                  setPropertyForm({ ...propertyForm, annualMaintenancePct: e.target.value })
                }
                fullWidth
                slotProps={{
                  htmlInput: { step: 0.01, inputMode: "decimal" },
                  input: {
                    endAdornment: (
                      <HelpAdornment title="Annual maintenance estimate as a fraction of current value — e.g. 0.01 for 1%." />
                    ),
                  },
                }}
              />
            </Grid>
          </Grid>

          {propertyForm.costBasis > 0 && propertyForm.costBasis > propertyForm.currentValue && (
            <Alert severity="warning">
              Cost basis exceeds current value — capital gain on sale would be zero.
            </Alert>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPropertyDialogOpen(false)}>Cancel</Button>
          <Button onClick={handleSaveProperty} variant="contained" disabled={!propertyForm.name}>
            {editingPropertyId ? "Save" : "Add"}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
