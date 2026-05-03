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
import ArrowUpwardIcon from "@mui/icons-material/ArrowUpward";
import ArrowDownwardIcon from "@mui/icons-material/ArrowDownward";
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
  getUserProfile,
} from "../api";
import type {
  Account,
  AccountType,
  IncomeSource,
  IncomeType,
  SimulationAssumptions,
  UserProfile,
  WithdrawalOrderingStrategy,
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

const ORDERING_STRATEGIES: {
  value: WithdrawalOrderingStrategy;
  label: string;
  helperText: string;
}[] = [
  {
    value: "PROPORTIONAL",
    label: "Proportional",
    helperText:
      "Split each withdrawal across all positive-balance accounts in proportion to their balance.",
  },
  {
    value: "TAX_OPTIMIZED",
    label: "Tax-Optimized",
    helperText:
      "Drain in tiers: taxable (brokerage, savings) → tax-deferred (Traditional) → tax-free (Roth, HSA).",
  },
  {
    value: "CUSTOM",
    label: "Custom Order",
    helperText: "Specify the exact draw order. Account types not listed are drawn last.",
  },
];

// All AccountTypes, in the order used as the default when first switching to CUSTOM
// (mirrors TAX_OPTIMIZED tier order).
const ALL_ACCOUNT_TYPES: AccountType[] = [
  "TAXABLE_BROKERAGE",
  "SAVINGS",
  "TRADITIONAL_401K",
  "TRADITIONAL_IRA",
  "ROTH_401K",
  "ROTH_IRA",
  "HSA",
];

const accountTypeLabel = (t: AccountType) => t.replace(/_/g, " ");

const FILING_STATUS_LABELS: Record<UserProfile["filingStatus"], string> = {
  SINGLE: "Single",
  MARRIED_FILING_JOINTLY: "Married Filing Jointly",
  MARRIED_FILING_SEPARATELY: "Married Filing Separately",
  HEAD_OF_HOUSEHOLD: "Head of Household",
};

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
  withdrawalOrderingStrategy: "PROPORTIONAL",
  customWithdrawalOrder: [],
};

export default function ScenarioDetailPage() {
  const { scenarioId } = useParams<{ scenarioId: string }>();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const isNew = scenarioId === "new";
  const profileId = searchParams.get("profileId");
  const cloneFrom = searchParams.get("cloneFrom");

  const [accounts, setAccounts] = useState<Account[]>([]);
  const [incomeSources, setIncomeSources] = useState<IncomeSource[]>([]);
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [error, setError] = useState("");
  const [runningSim, setRunningSim] = useState(false);
  const [form, setForm] = useState({
    name: "",
    description: "",
    accountIds: [] as string[],
    assumptions: { ...defaultAssumptions },
  });
  // When the user opened the editor via clone, the source's income sources are
  // staged here so they can be created on the new scenario after the first save.
  const [pendingIncomeCopies, setPendingIncomeCopies] = useState<IncomeSource[]>([]);

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
      const [accRes, profileRes] = await Promise.all([
        listAccounts(s.userProfileId),
        getUserProfile(s.userProfileId),
      ]);
      setAccounts(accRes.data);
      setProfile(profileRes.data);
      await loadIncomeSources(scenarioId);
    } catch {
      setError("Failed to load scenario");
    }
  }, [scenarioId, loadIncomeSources]);

  useEffect(() => {
    if (isNew && profileId) {
      if (cloneFrom) {
        // Deep-clone path: prefill from source scenario, stage its income sources
        // so they can be re-created against the new scenario after the first save.
        Promise.all([
          listAccounts(profileId),
          getUserProfile(profileId),
          getScenario(cloneFrom),
          listIncomeSources(cloneFrom),
        ])
          .then(([accRes, profileRes, scenarioRes, incomeRes]) => {
            setAccounts(accRes.data);
            setProfile(profileRes.data);
            const src = scenarioRes.data;
            setForm({
              name: `Copy of ${src.name}`,
              description: src.description ?? "",
              accountIds: src.accountIds,
              assumptions: src.assumptions,
            });
            setPendingIncomeCopies(incomeRes.data);
          })
          .catch(() => setError("Failed to load scenario to clone"));
      } else {
        Promise.all([listAccounts(profileId), getUserProfile(profileId)])
          .then(([accRes, profileRes]) => {
            setAccounts(accRes.data);
            setProfile(profileRes.data);
            // For new scenarios, default to all accounts selected.
            setForm((prev) => ({ ...prev, accountIds: accRes.data.map((a) => a.id) }));
          })
          .catch(() => {});
      }
    } else if (scenarioId) {
      loadScenario();
    }
  }, [scenarioId, isNew, profileId, cloneFrom, loadScenario]);

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
        const newId = res.data.id;
        if (pendingIncomeCopies.length > 0) {
          await Promise.all(
            pendingIncomeCopies.map((src) =>
              createIncomeSource(newId, {
                name: src.name,
                type: src.type,
                monthlyAmount: src.monthlyAmount,
                startDate: src.startDate,
                endDate: src.endDate,
                inflationAdjusted: src.inflationAdjusted,
              }),
            ),
          );
          setPendingIncomeCopies([]);
        }
        navigate(`/scenarios/${newId}`, { replace: true });
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

  // When switching to CUSTOM, prefill the order with all account types if it's empty
  // so the user has something to reorder rather than a blank list.
  const handleOrderingStrategyChange = (next: WithdrawalOrderingStrategy) => {
    setForm((prev) => {
      const currentOrder = prev.assumptions.customWithdrawalOrder;
      const nextOrder =
        next === "CUSTOM" && currentOrder.length === 0 ? [...ALL_ACCOUNT_TYPES] : currentOrder;
      return {
        ...prev,
        assumptions: {
          ...prev.assumptions,
          withdrawalOrderingStrategy: next,
          customWithdrawalOrder: nextOrder,
        },
      };
    });
  };

  const moveCustomOrderItem = (index: number, direction: -1 | 1) => {
    setForm((prev) => {
      const order = [...prev.assumptions.customWithdrawalOrder];
      const target = index + direction;
      if (target < 0 || target >= order.length) return prev;
      [order[index], order[target]] = [order[target], order[index]];
      return {
        ...prev,
        assumptions: { ...prev.assumptions, customWithdrawalOrder: order },
      };
    });
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
          onClick={() => {
            const backProfileId = profileId ?? profile?.id;
            navigate(backProfileId ? `/profiles/${backProfileId}` : "/");
          }}
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
            {pendingIncomeCopies.length > 0
              ? `${pendingIncomeCopies.length} income source(s) will be copied from the source scenario when you save.`
              : "Save the scenario first, then add income sources (pension, Social Security, employment, rental, etc.)."}
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
              slotProps={{ htmlInput: { step: 0.005 } }}
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
              slotProps={{ htmlInput: { step: 0.005 } }}
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
                slotProps={{ htmlInput: { step: 0.005 } }}
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
                slotProps={{ htmlInput: { step: 100 } }}
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
              slotProps={{ htmlInput: { step: 0.005 } }}
            />
          </Grid>
          <Grid size={{ xs: 12, sm: 6, md: 4 }}>
            <TextField
              label="Monte Carlo Trials"
              type="number"
              value={form.assumptions.monteCarloTrials}
              onChange={(e) => updateAssumptions({ monteCarloTrials: Number(e.target.value) })}
              fullWidth
              slotProps={{ htmlInput: { step: 100 } }}
            />
          </Grid>
          <Grid size={{ xs: 12 }}>
            <Typography variant="body2" color="text.secondary">
              Federal tax is computed from{" "}
              <strong>{profile ? FILING_STATUS_LABELS[profile.filingStatus] : "—"}</strong> brackets.
              Filing status lives on the profile —{" "}
              {profile ? (
                <a href={`/profiles/${profile.id}`}>change it there</a>
              ) : (
                "change it on the profile page"
              )}
              .
            </Typography>
          </Grid>
        </Grid>
      </Paper>

      <Paper sx={{ p: 3, mt: 3 }}>
        <Typography variant="h6" gutterBottom>
          Withdrawal Ordering
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          When savings have to cover a withdrawal, this controls which accounts are drained first.
          Distinct from the Withdrawal Strategy above (which decides <em>how much</em> to withdraw).
        </Typography>
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, sm: 6, md: 4 }}>
            <TextField
              label="Ordering Strategy"
              select
              value={form.assumptions.withdrawalOrderingStrategy}
              onChange={(e) =>
                handleOrderingStrategyChange(e.target.value as WithdrawalOrderingStrategy)
              }
              fullWidth
              helperText={
                ORDERING_STRATEGIES.find(
                  (os) => os.value === form.assumptions.withdrawalOrderingStrategy,
                )?.helperText
              }
            >
              {ORDERING_STRATEGIES.map((os) => (
                <MenuItem key={os.value} value={os.value}>
                  {os.label}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
        </Grid>
        {form.assumptions.withdrawalOrderingStrategy === "CUSTOM" && (
          <Box sx={{ mt: 2 }}>
            <Typography variant="subtitle2" sx={{ mb: 1 }}>
              Draw Order (top is drained first)
            </Typography>
            <Table size="small" sx={{ maxWidth: 480 }}>
              <TableBody>
                {form.assumptions.customWithdrawalOrder.map((acctType, idx) => (
                  <TableRow key={acctType}>
                    <TableCell sx={{ width: 40 }}>{idx + 1}.</TableCell>
                    <TableCell>{accountTypeLabel(acctType)}</TableCell>
                    <TableCell align="right" sx={{ width: 96 }}>
                      <IconButton
                        size="small"
                        onClick={() => moveCustomOrderItem(idx, -1)}
                        disabled={idx === 0}
                      >
                        <ArrowUpwardIcon fontSize="small" />
                      </IconButton>
                      <IconButton
                        size="small"
                        onClick={() => moveCustomOrderItem(idx, 1)}
                        disabled={idx === form.assumptions.customWithdrawalOrder.length - 1}
                      >
                        <ArrowDownwardIcon fontSize="small" />
                      </IconButton>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Box>
        )}
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
