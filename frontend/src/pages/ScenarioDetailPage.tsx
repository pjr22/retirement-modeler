import { useCallback, useEffect, useState } from "react";
import { useParams, useNavigate, useSearchParams } from "react-router";
import {
  Box,
  Typography,
  TextField,
  Button,
  MenuItem,
  Paper,
  Grid,
  Checkbox,
  FormControlLabel,
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
} from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import SaveIcon from "@mui/icons-material/Save";
import PlayArrowIcon from "@mui/icons-material/PlayArrow";
import AddIcon from "@mui/icons-material/Add";
import EditIcon from "@mui/icons-material/Edit";
import DeleteIcon from "@mui/icons-material/Delete";
import {
  getScenario,
  createScenario,
  updateScenario,
  listAccounts,
  listIncomeSources,
  createIncomeSource,
  updateIncomeSource,
  deleteIncomeSource,
  runSimulation,
} from "../api";
import type {
  Account,
  IncomeSource,
  IncomeType,
  SimulationAssumptions,
  WithdrawalStrategy,
} from "../types";
import CircularProgress from "@mui/material/CircularProgress";

const WITHDRAWAL_STRATEGIES: {
  value: WithdrawalStrategy;
  label: string;
  helperText: string;
}[] = [
  {
    value: "PORTFOLIO_PERCENTAGE",
    label: "Portfolio Percentage (e.g. 4% rule)",
    helperText: "Withdraw a fixed percentage of current savings each year. Income arrives on top.",
  },
  {
    value: "CASHFLOW_TARGET",
    label: "Cashflow Target (monthly budget)",
    helperText:
      "Target monthly spend. Income (pension, SS, etc.) is applied first; savings cover the gap.",
  },
];

const INCOME_TYPES: { value: IncomeType; label: string; helperText: string }[] = [
  {
    value: "EMPLOYMENT",
    label: "Employment (W-2)",
    helperText: "Earned income; counts toward SS earnings test.",
  },
  {
    value: "SELF_EMPLOYMENT",
    label: "Self-Employment",
    helperText: "Earned income; counts toward SS earnings test.",
  },
  { value: "PENSION", label: "Pension", helperText: "Ordinary income, not earned." },
  {
    value: "SOCIAL_SECURITY",
    label: "Social Security",
    helperText: "Subject to provisional-income tax test and earnings test.",
  },
  { value: "RENTAL", label: "Rental", helperText: "Passive ordinary income." },
  { value: "OTHER", label: "Other", helperText: "Ordinary income, no special handling." },
];

// Smart default for the inflation-adjusted flag: SS has a COLA, pensions usually don't,
// salaries and rentals typically track inflation.
function defaultInflationAdjusted(type: IncomeType): boolean {
  return type !== "PENSION";
}

interface IncomeForm {
  name: string;
  type: IncomeType;
  monthlyAmount: number;
  startDate: string | null;
  endDate: string | null;
  inflationAdjusted: boolean;
}

const emptyIncomeForm: IncomeForm = {
  name: "",
  type: "PENSION",
  monthlyAmount: 0,
  startDate: null,
  endDate: null,
  inflationAdjusted: false,
};

const defaultAssumptions: SimulationAssumptions = {
  expectedRateOfReturn: 0.07,
  inflationRate: 0.03,
  withdrawalStrategy: "PORTFOLIO_PERCENTAGE",
  withdrawalPercentage: 0.04,
  withdrawalMonthlyAmount: null,
  standardDeviation: 0.15,
  monteCarloTrials: 1000,
  flatTaxRate: 0.22,
};

export default function ScenarioDetailPage() {
  const { scenarioId } = useParams<{ scenarioId: string }>();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const isNew = scenarioId === "new";
  const profileId = searchParams.get("profileId");

  const [accounts, setAccounts] = useState<Account[]>([]);
  const [incomeSources, setIncomeSources] = useState<IncomeSource[]>([]);
  const [error, setError] = useState("");
  const [runningSim, setRunningSim] = useState(false);
  const [form, setForm] = useState({
    name: "",
    description: "",
    accountIds: [] as string[],
    assumptions: { ...defaultAssumptions },
  });

  // IncomeSource CRUD dialog state.
  const [incomeDialogOpen, setIncomeDialogOpen] = useState(false);
  const [editingIncomeId, setEditingIncomeId] = useState<string | null>(null);
  const [incomeForm, setIncomeForm] = useState<IncomeForm>(emptyIncomeForm);

  const loadIncomeSources = useCallback(async (sid: string) => {
    try {
      const res = await listIncomeSources(sid);
      setIncomeSources(res.data);
    } catch {
      setError("Failed to load income sources");
    }
  }, []);

  const loadScenario = useCallback(async () => {
    if (!scenarioId) return;
    try {
      const res = await getScenario(scenarioId);
      const s = res.data;
      setForm({
        name: s.name,
        description: s.description ?? "",
        accountIds: s.accountIds,
        assumptions: s.assumptions,
      });
      const accRes = await listAccounts(s.userProfileId);
      setAccounts(accRes.data);
      await loadIncomeSources(scenarioId);
    } catch {
      setError("Failed to load scenario");
    }
  }, [scenarioId, loadIncomeSources]);

  useEffect(() => {
    if (isNew && profileId) {
      listAccounts(profileId)
        .then((res) => {
          setAccounts(res.data);
          // For new scenarios, default to all accounts selected.
          setForm((prev) => ({ ...prev, accountIds: res.data.map((a) => a.id) }));
        })
        .catch(() => {});
    } else if (scenarioId) {
      loadScenario();
    }
  }, [scenarioId, isNew, profileId, loadScenario]);

  const handleSave = async () => {
    try {
      const payload = {
        name: form.name,
        description: form.description || null,
        accountIds: form.accountIds,
        assumptions: form.assumptions,
      };
      if (isNew && profileId) {
        const res = await createScenario(profileId, payload);
        navigate(`/scenarios/${res.data.id}`, { replace: true });
      } else if (scenarioId) {
        await updateScenario(scenarioId, payload);
        loadScenario();
      }
    } catch {
      setError("Failed to save scenario");
    }
  };

  const toggleAccount = (accountId: string) => {
    setForm((prev) => ({
      ...prev,
      accountIds: prev.accountIds.includes(accountId)
        ? prev.accountIds.filter((id) => id !== accountId)
        : [...prev.accountIds, accountId],
    }));
  };

  const selectAllAccounts = () => {
    setForm((prev) => ({ ...prev, accountIds: accounts.map((a) => a.id) }));
  };

  const deselectAllAccounts = () => {
    setForm((prev) => ({ ...prev, accountIds: [] }));
  };

  const updateAssumptions = (patch: Partial<SimulationAssumptions>) => {
    setForm((prev) => ({ ...prev, assumptions: { ...prev.assumptions, ...patch } }));
  };

  const handleRunSimulation = async () => {
    if (!scenarioId || scenarioId === "new") return;
    setRunningSim(true);
    try {
      const res = await runSimulation(scenarioId);
      navigate(`/simulations/${res.data.id}`);
    } catch {
      setError("Failed to run simulation");
    } finally {
      setRunningSim(false);
    }
  };

  const openCreateIncome = () => {
    setEditingIncomeId(null);
    setIncomeForm(emptyIncomeForm);
    setIncomeDialogOpen(true);
  };

  const openEditIncome = (source: IncomeSource) => {
    setEditingIncomeId(source.id);
    setIncomeForm({
      name: source.name,
      type: source.type,
      monthlyAmount: source.monthlyAmount,
      startDate: source.startDate,
      endDate: source.endDate,
      inflationAdjusted: source.inflationAdjusted,
    });
    setIncomeDialogOpen(true);
  };

  const handleSaveIncome = async () => {
    if (!scenarioId || isNew) return;
    try {
      if (editingIncomeId) {
        await updateIncomeSource(editingIncomeId, incomeForm);
      } else {
        await createIncomeSource(scenarioId, incomeForm);
      }
      setIncomeDialogOpen(false);
      await loadIncomeSources(scenarioId);
    } catch {
      setError("Failed to save income source");
    }
  };

  const handleDeleteIncome = async (id: string) => {
    if (!scenarioId) return;
    try {
      await deleteIncomeSource(id);
      await loadIncomeSources(scenarioId);
    } catch {
      setError("Failed to delete income source");
    }
  };

  const incomeTypeLabel = (t: IncomeType) =>
    INCOME_TYPES.find((it) => it.value === t)?.label ?? t;

  return (
    <Box>
      {error && (
        <Alert severity="error" onClose={() => setError("")} sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      <Box sx={{ display: "flex", alignItems: "center", mb: 3 }}>
        <IconButton
          onClick={() => navigate(profileId ? `/profiles/${profileId}/scenarios` : "/")}
          sx={{ mr: 1 }}
        >
          <ArrowBackIcon />
        </IconButton>
        <Typography variant="h4" sx={{ flexGrow: 1 }}>
          {isNew ? "Create Scenario" : form.name}
        </Typography>
        <Button
          startIcon={<SaveIcon />}
          variant="contained"
          onClick={handleSave}
          disabled={!form.name}
          sx={{ mr: 1 }}
        >
          Save
        </Button>
        {!isNew && (
          <Button
            startIcon={runningSim ? <CircularProgress size={20} /> : <PlayArrowIcon />}
            variant="outlined"
            onClick={handleRunSimulation}
            disabled={runningSim || form.accountIds.length === 0}
          >
            {runningSim ? "Running..." : "Run Simulation"}
          </Button>
        )}
      </Box>

      <Paper sx={{ p: 3, mb: 3 }}>
        <Typography variant="h6" gutterBottom>
          Scenario Details
        </Typography>
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField
              label="Scenario Name"
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              fullWidth
              required
            />
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField
              label="Description"
              value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
              fullWidth
              multiline
            />
          </Grid>
        </Grid>
      </Paper>

      <Paper sx={{ p: 3, mb: 3 }}>
        <Box sx={{ display: "flex", alignItems: "center", mb: 1 }}>
          <Typography variant="h6" sx={{ flexGrow: 1 }}>
            Accounts
          </Typography>
          {accounts.length > 0 && (
            <>
              <Button size="small" onClick={selectAllAccounts} sx={{ mr: 1 }}>
                Select all
              </Button>
              <Button size="small" onClick={deselectAllAccounts}>
                Deselect all
              </Button>
            </>
          )}
        </Box>
        {accounts.length === 0 ? (
          <Typography color="text.secondary">No accounts available.</Typography>
        ) : (
          accounts.map((account) => (
            <FormControlLabel
              key={account.id}
              control={
                <Checkbox
                  checked={form.accountIds.includes(account.id)}
                  onChange={() => toggleAccount(account.id)}
                />
              }
              label={`${account.name} (${account.accountType.replace(/_/g, " ")} — $${account.balance.toLocaleString()})`}
            />
          ))
        )}
      </Paper>

      <Paper sx={{ p: 3, mb: 3 }}>
        <Box sx={{ display: "flex", alignItems: "center", mb: 2 }}>
          <Typography variant="h6" sx={{ flexGrow: 1 }}>
            Income Sources
          </Typography>
          <Button
            startIcon={<AddIcon />}
            variant="outlined"
            onClick={openCreateIncome}
            disabled={isNew}
          >
            Add Income Source
          </Button>
        </Box>
        {isNew ? (
          <Typography color="text.secondary">
            Save the scenario first, then add income sources (pension, Social Security, employment,
            rental, etc.).
          </Typography>
        ) : incomeSources.length === 0 ? (
          <Typography color="text.secondary">
            No income sources yet. Add pension, Social Security, employment, rental, or other
            recurring income to model how it affects this scenario.
          </Typography>
        ) : (
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Name</TableCell>
                <TableCell>Type</TableCell>
                <TableCell align="right">Monthly Amount</TableCell>
                <TableCell>Start Date</TableCell>
                <TableCell>End Date</TableCell>
                <TableCell align="center">Inflation-Adjusted</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {incomeSources.map((src) => (
                <TableRow key={src.id}>
                  <TableCell>{src.name}</TableCell>
                  <TableCell>{incomeTypeLabel(src.type)}</TableCell>
                  <TableCell align="right">${src.monthlyAmount.toLocaleString()}</TableCell>
                  <TableCell>{src.startDate ?? "From now"}</TableCell>
                  <TableCell>{src.endDate ?? "Lifetime"}</TableCell>
                  <TableCell align="center">{src.inflationAdjusted ? "Yes" : "No"}</TableCell>
                  <TableCell align="right">
                    <IconButton size="small" onClick={() => openEditIncome(src)}>
                      <EditIcon fontSize="small" />
                    </IconButton>
                    <IconButton
                      size="small"
                      color="error"
                      onClick={() => handleDeleteIncome(src.id)}
                    >
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Paper>

      <Paper sx={{ p: 3 }}>
        <Typography variant="h6" gutterBottom>
          Simulation Assumptions
        </Typography>
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, sm: 6, md: 4 }}>
            <TextField
              label="Expected Rate of Return"
              type="number"
              value={form.assumptions.expectedRateOfReturn}
              onChange={(e) => updateAssumptions({ expectedRateOfReturn: Number(e.target.value) })}
              fullWidth
              helperText="e.g. 0.07 for 7%"
            />
          </Grid>
          <Grid size={{ xs: 12, sm: 6, md: 4 }}>
            <TextField
              label="Inflation Rate"
              type="number"
              value={form.assumptions.inflationRate}
              onChange={(e) => updateAssumptions({ inflationRate: Number(e.target.value) })}
              fullWidth
              helperText="e.g. 0.03 for 3%"
            />
          </Grid>
          <Grid size={{ xs: 12, sm: 6, md: 4 }}>
            <TextField
              label="Flat Tax Rate"
              type="number"
              value={form.assumptions.flatTaxRate}
              onChange={(e) => updateAssumptions({ flatTaxRate: Number(e.target.value) })}
              fullWidth
              helperText="e.g. 0.22 for 22%"
            />
          </Grid>
          <Grid size={{ xs: 12, sm: 6, md: 4 }}>
            <TextField
              label="Withdrawal Strategy"
              select
              value={form.assumptions.withdrawalStrategy}
              onChange={(e) =>
                updateAssumptions({
                  withdrawalStrategy: e.target.value as WithdrawalStrategy,
                })
              }
              fullWidth
              helperText={
                WITHDRAWAL_STRATEGIES.find(
                  (ws) => ws.value === form.assumptions.withdrawalStrategy,
                )?.helperText
              }
            >
              {WITHDRAWAL_STRATEGIES.map((ws) => (
                <MenuItem key={ws.value} value={ws.value}>
                  {ws.label}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          {form.assumptions.withdrawalStrategy === "PORTFOLIO_PERCENTAGE" && (
            <Grid size={{ xs: 12, sm: 6, md: 4 }}>
              <TextField
                label="Withdrawal Percentage"
                type="number"
                value={form.assumptions.withdrawalPercentage ?? ""}
                onChange={(e) =>
                  updateAssumptions({
                    withdrawalPercentage: Number(e.target.value) || null,
                    withdrawalMonthlyAmount: null,
                  })
                }
                fullWidth
                helperText="e.g. 0.04 for 4%"
              />
            </Grid>
          )}
          {form.assumptions.withdrawalStrategy === "CASHFLOW_TARGET" && (
            <Grid size={{ xs: 12, sm: 6, md: 4 }}>
              <TextField
                label="Monthly Cashflow Target"
                type="number"
                value={form.assumptions.withdrawalMonthlyAmount ?? ""}
                onChange={(e) =>
                  updateAssumptions({
                    withdrawalMonthlyAmount: Number(e.target.value) || null,
                    withdrawalPercentage: null,
                  })
                }
                fullWidth
                helperText="What you want to spend per month (income offsets the savings draw)"
              />
            </Grid>
          )}
          <Grid size={{ xs: 12, sm: 6, md: 4 }}>
            <TextField
              label="Standard Deviation"
              type="number"
              value={form.assumptions.standardDeviation}
              onChange={(e) => updateAssumptions({ standardDeviation: Number(e.target.value) })}
              fullWidth
              helperText="For Monte Carlo simulation"
            />
          </Grid>
          <Grid size={{ xs: 12, sm: 6, md: 4 }}>
            <TextField
              label="Monte Carlo Trials"
              type="number"
              value={form.assumptions.monteCarloTrials}
              onChange={(e) => updateAssumptions({ monteCarloTrials: Number(e.target.value) })}
              fullWidth
            />
          </Grid>
        </Grid>
      </Paper>

      <Dialog
        open={incomeDialogOpen}
        onClose={() => setIncomeDialogOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>{editingIncomeId ? "Edit Income Source" : "Add Income Source"}</DialogTitle>
        <DialogContent sx={{ display: "flex", flexDirection: "column", gap: 2, mt: 1 }}>
          <TextField
            label="Name"
            value={incomeForm.name}
            onChange={(e) => setIncomeForm({ ...incomeForm, name: e.target.value })}
            fullWidth
            required
          />
          <TextField
            label="Type"
            select
            value={incomeForm.type}
            onChange={(e) => {
              const newType = e.target.value as IncomeType;
              setIncomeForm({
                ...incomeForm,
                type: newType,
                inflationAdjusted: defaultInflationAdjusted(newType),
              });
            }}
            fullWidth
            helperText={INCOME_TYPES.find((it) => it.value === incomeForm.type)?.helperText}
          >
            {INCOME_TYPES.map((it) => (
              <MenuItem key={it.value} value={it.value}>
                {it.label}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            label="Monthly Amount"
            type="number"
            value={incomeForm.monthlyAmount || ""}
            onChange={(e) =>
              setIncomeForm({ ...incomeForm, monthlyAmount: Number(e.target.value) })
            }
            fullWidth
            required
          />
          <TextField
            label="Start Date"
            type="date"
            value={incomeForm.startDate ?? ""}
            onChange={(e) => setIncomeForm({ ...incomeForm, startDate: e.target.value || null })}
            fullWidth
            slotProps={{ inputLabel: { shrink: true } }}
            helperText="Leave blank to start from the beginning of the simulation"
          />
          <TextField
            label="End Date"
            type="date"
            value={incomeForm.endDate ?? ""}
            onChange={(e) => setIncomeForm({ ...incomeForm, endDate: e.target.value || null })}
            fullWidth
            slotProps={{ inputLabel: { shrink: true } }}
            helperText="Leave blank for lifetime income"
          />
          <FormControlLabel
            control={
              <Checkbox
                checked={incomeForm.inflationAdjusted}
                onChange={(e) =>
                  setIncomeForm({ ...incomeForm, inflationAdjusted: e.target.checked })
                }
              />
            }
            label="Adjust for inflation each year (e.g. Social Security COLA, salary growth)"
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setIncomeDialogOpen(false)}>Cancel</Button>
          <Button
            onClick={handleSaveIncome}
            variant="contained"
            disabled={!incomeForm.name || !incomeForm.monthlyAmount}
          >
            {editingIncomeId ? "Save" : "Add"}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
