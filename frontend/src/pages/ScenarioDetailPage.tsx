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
} from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import SaveIcon from "@mui/icons-material/Save";
import { getScenario, createScenario, updateScenario, listAccounts } from "../api";
import type { Account, SimulationAssumptions, WithdrawalStrategy } from "../types";

const WITHDRAWAL_STRATEGIES: { value: WithdrawalStrategy; label: string }[] = [
  { value: "FIXED_PERCENTAGE", label: "Fixed Percentage (e.g. 4% rule)" },
  { value: "FIXED_DOLLAR", label: "Fixed Dollar Amount (inflation-adjusted)" },
];

const defaultAssumptions: SimulationAssumptions = {
  expectedRateOfReturn: 0.07,
  inflationRate: 0.03,
  withdrawalStrategy: "FIXED_PERCENTAGE",
  withdrawalPercentage: 0.04,
  withdrawalFixedAmount: null,
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
  const [error, setError] = useState("");
  const [form, setForm] = useState({
    name: "",
    description: "",
    accountIds: [] as string[],
    assumptions: { ...defaultAssumptions },
  });

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
      const accRes = await listAccounts(s.userId);
      setAccounts(accRes.data);
    } catch {
      setError("Failed to load scenario");
    }
  }, [scenarioId]);

  useEffect(() => {
    if (isNew && profileId) {
      listAccounts(profileId)
        .then((res) => setAccounts(res.data))
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

  const updateAssumptions = (patch: Partial<SimulationAssumptions>) => {
    setForm((prev) => ({ ...prev, assumptions: { ...prev.assumptions, ...patch } }));
  };

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
        >
          Save
        </Button>
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
        <Typography variant="h6" gutterBottom>
          Accounts
        </Typography>
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
            >
              {WITHDRAWAL_STRATEGIES.map((ws) => (
                <MenuItem key={ws.value} value={ws.value}>
                  {ws.label}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          {form.assumptions.withdrawalStrategy === "FIXED_PERCENTAGE" && (
            <Grid size={{ xs: 12, sm: 6, md: 4 }}>
              <TextField
                label="Withdrawal Percentage"
                type="number"
                value={form.assumptions.withdrawalPercentage ?? ""}
                onChange={(e) =>
                  updateAssumptions({
                    withdrawalPercentage: Number(e.target.value) || null,
                    withdrawalFixedAmount: null,
                  })
                }
                fullWidth
                helperText="e.g. 0.04 for 4%"
              />
            </Grid>
          )}
          {form.assumptions.withdrawalStrategy === "FIXED_DOLLAR" && (
            <Grid size={{ xs: 12, sm: 6, md: 4 }}>
              <TextField
                label="Annual Withdrawal Amount"
                type="number"
                value={form.assumptions.withdrawalFixedAmount ?? ""}
                onChange={(e) =>
                  updateAssumptions({
                    withdrawalFixedAmount: Number(e.target.value) || null,
                    withdrawalPercentage: null,
                  })
                }
                fullWidth
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
    </Box>
  );
}
