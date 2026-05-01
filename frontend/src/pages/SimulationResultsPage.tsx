import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams, useNavigate } from "react-router";
import {
  Box,
  Typography,
  Button,
  Paper,
  Grid,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  IconButton,
  Alert,
  Chip,
  Collapse,
  MenuItem,
  TextField,
  Tooltip as MuiTooltip,
} from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import ExpandLessIcon from "@mui/icons-material/ExpandLess";
import {
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Line,
  LineChart,
} from "recharts";
import { getSimulation, getScenario } from "../api";
import type { SimulationResult, Scenario } from "../types";
import { formatMonthYear } from "../utils";

type SeriesKey = "Deterministic" | "Median" | "p10" | "p25" | "p75" | "p90";

interface SeriesDef {
  key: SeriesKey;
  label: string;
  color: string;
  description: string;
}

const SERIES: SeriesDef[] = [
  {
    key: "Deterministic",
    label: "Deterministic",
    color: "#1565c0",
    description:
      "Single projection assuming returns equal the expected rate every year — no variability.",
  },
  {
    key: "Median",
    label: "Median (50th percentile)",
    color: "#ef6c00",
    description: "Half of Monte Carlo trials ended above this line, half below.",
  },
  {
    key: "p10",
    label: "10th percentile (pessimistic)",
    color: "#c62828",
    description: "Only 10% of Monte Carlo trials performed worse than this line.",
  },
  {
    key: "p25",
    label: "25th percentile",
    color: "#f9a825",
    description: "A quarter of Monte Carlo trials performed worse than this line.",
  },
  {
    key: "p75",
    label: "75th percentile",
    color: "#558b2f",
    description: "Three-quarters of Monte Carlo trials performed worse than this line.",
  },
  {
    key: "p90",
    label: "90th percentile (optimistic)",
    color: "#2e7d32",
    description: "Only 10% of Monte Carlo trials performed better than this line.",
  },
];

const formatCurrency = (val: number) =>
  new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
    maximumFractionDigits: 0,
  }).format(val);

const formatPercent = (val: number, digits = 1) => `${(val * 100).toFixed(digits)}%`;

interface SeriesPoint {
  age: number;
  value: number;
}

export default function SimulationResultsPage() {
  const { simulationId } = useParams<{ simulationId: string }>();
  const navigate = useNavigate();
  const [result, setResult] = useState<SimulationResult | null>(null);
  const [scenario, setScenario] = useState<Scenario | null>(null);
  const [error, setError] = useState("");
  const [showTable, setShowTable] = useState(false);
  const [showParams, setShowParams] = useState(false);
  const [selectedKey, setSelectedKey] = useState<SeriesKey>("Deterministic");

  const loadData = useCallback(async () => {
    if (!simulationId) return;
    try {
      const simRes = await getSimulation(simulationId);
      setResult(simRes.data);
      const scenRes = await getScenario(simRes.data.scenarioId);
      setScenario(scenRes.data);
    } catch {
      setError("Failed to load simulation results");
    }
  }, [simulationId]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const selectedSeries = SERIES.find((s) => s.key === selectedKey)!;

  // Build the data points for the currently-selected series.
  const seriesPoints: SeriesPoint[] = useMemo(() => {
    if (!result) return [];
    if (selectedKey === "Deterministic") {
      return result.deterministicProjection.map((dp) => ({ age: dp.age, value: dp.balance }));
    }
    const field = selectedKey === "Median" ? "p50" : selectedKey;
    return result.monteCarloSummary.percentileBalances.map((p) => ({
      age: p.age,
      value: p[field as "p10" | "p25" | "p50" | "p75" | "p90"],
    }));
  }, [result, selectedKey]);

  // Per-series metrics: final balance, depletion year/age, outcome.
  const metrics = useMemo(() => {
    if (seriesPoints.length === 0) return null;
    const last = seriesPoints[seriesPoints.length - 1];
    const depletionIdx = seriesPoints.findIndex((p) => p.value <= 0);
    const survives = depletionIdx === -1;
    return {
      finalBalance: last.value,
      finalAge: last.age,
      survives,
      depletionAge: survives ? null : seriesPoints[depletionIdx].age,
      yearsToDepletion: survives ? null : depletionIdx,
    };
  }, [seriesPoints]);

  if (!result) {
    return error ? (
      <Alert severity="error">{error}</Alert>
    ) : (
      <Typography>Loading simulation results...</Typography>
    );
  }

  const overallSuccessRate = result.monteCarloSummary.successRate;
  const trials = result.monteCarloSummary.trials;

  return (
    <Box>
      {error && (
        <Alert severity="error" onClose={() => setError("")} sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      <Box sx={{ display: "flex", alignItems: "center", mb: 3 }}>
        <IconButton
          onClick={() => (scenario ? navigate(`/scenarios/${scenario.id}`) : navigate("/"))}
          sx={{ mr: 1 }}
        >
          <ArrowBackIcon />
        </IconButton>
        <Typography variant="h4" sx={{ flexGrow: 1 }}>
          Simulation Results
        </Typography>
        {scenario && <Chip label={scenario.name} color="primary" />}
      </Box>

      {/* Series selector. The cards and chart below all reflect the selected series. */}
      <Paper sx={{ p: 2, mb: 2 }}>
        <Box sx={{ display: "flex", alignItems: "center", gap: 2, flexWrap: "wrap" }}>
          <TextField
            label="Show statistics for"
            select
            size="small"
            value={selectedKey}
            onChange={(e) => setSelectedKey(e.target.value as SeriesKey)}
            sx={{ minWidth: 280 }}
          >
            {SERIES.map((s) => (
              <MenuItem key={s.key} value={s.key}>
                <Box
                  component="span"
                  sx={{
                    display: "inline-block",
                    width: 12,
                    height: 12,
                    borderRadius: "50%",
                    backgroundColor: s.color,
                    mr: 1,
                    verticalAlign: "middle",
                  }}
                />
                {s.label}
              </MenuItem>
            ))}
          </TextField>
          <Typography variant="body2" color="text.secondary" sx={{ flexGrow: 1 }}>
            {selectedSeries.description}
          </Typography>
          <Chip
            size="small"
            label={`Overall MC success: ${overallSuccessRate.toFixed(1)}%`}
            color={
              overallSuccessRate >= 90
                ? "success"
                : overallSuccessRate >= 70
                  ? "warning"
                  : "error"
            }
          />
        </Box>
      </Paper>

      {metrics && (
        <Grid container spacing={2} sx={{ mb: 3 }}>
          <Grid size={{ xs: 12, sm: 4 }}>
            <MuiTooltip
              title={`The portfolio balance at age ${metrics.finalAge} (life expectancy) for the "${selectedSeries.label}" projection.`}
              arrow
            >
              <Paper sx={{ p: 2, textAlign: "center", cursor: "help" }}>
                <Typography variant="overline" color="text.secondary">
                  Final Balance (age {metrics.finalAge})
                </Typography>
                <Typography
                  variant="h4"
                  color={metrics.finalBalance > 0 ? "text.primary" : "error.main"}
                >
                  {formatCurrency(metrics.finalBalance)}
                </Typography>
              </Paper>
            </MuiTooltip>
          </Grid>
          <Grid size={{ xs: 12, sm: 4 }}>
            <MuiTooltip
              title={`Years from today until the "${selectedSeries.label}" balance first reaches zero. "Never" means the projection survives all the way to life expectancy.`}
              arrow
            >
              <Paper sx={{ p: 2, textAlign: "center", cursor: "help" }}>
                <Typography variant="overline" color="text.secondary">
                  Years until Depletion
                </Typography>
                <Typography
                  variant="h4"
                  color={metrics.survives ? "success.main" : "error.main"}
                >
                  {metrics.survives ? "Never" : metrics.yearsToDepletion}
                </Typography>
              </Paper>
            </MuiTooltip>
          </Grid>
          <Grid size={{ xs: 12, sm: 4 }}>
            <MuiTooltip
              title={`Whether the "${selectedSeries.label}" projection ends with a positive balance at life expectancy. The "Overall MC success" badge above is a separate, aggregate statistic across all ${trials} trials.`}
              arrow
            >
              <Paper sx={{ p: 2, textAlign: "center", cursor: "help" }}>
                <Typography variant="overline" color="text.secondary">
                  Outcome
                </Typography>
                <Typography
                  variant="h4"
                  color={metrics.survives ? "success.main" : "error.main"}
                >
                  {metrics.survives ? "Survives" : `Depleted at ${metrics.depletionAge}`}
                </Typography>
              </Paper>
            </MuiTooltip>
          </Grid>
        </Grid>
      )}

      <Paper sx={{ p: 3, mb: 3 }}>
        <Typography variant="h6" gutterBottom>
          Portfolio Balance Over Time — {selectedSeries.label}
        </Typography>
        <ResponsiveContainer width="100%" height={400}>
          <LineChart
            data={seriesPoints}
            margin={{ top: 10, right: 30, left: 30, bottom: 30 }}
          >
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="age" label={{ value: "Age", position: "insideBottom", offset: -10 }} />
            <YAxis
              tickFormatter={(v: number) => `$${(v / 1000).toFixed(0)}k`}
              label={{
                value: "Balance",
                angle: -90,
                position: "insideLeft",
                offset: -15,
                style: { textAnchor: "middle" },
              }}
              width={80}
            />
            <Tooltip formatter={(value) => formatCurrency(Number(value))} />
            <Line
              type="monotone"
              dataKey="value"
              name={selectedSeries.label}
              stroke={selectedSeries.color}
              strokeWidth={2.5}
              dot={false}
              isAnimationActive={false}
            />
          </LineChart>
        </ResponsiveContainer>
      </Paper>

      <Paper sx={{ p: 3, mb: 3 }}>
        <Box sx={{ display: "flex", alignItems: "center", mb: 1 }}>
          <Typography variant="h6" sx={{ flexGrow: 1 }}>
            Year-by-Year Details — {selectedSeries.label}
            {selectedKey !== "Deterministic" && scenario && (
              <Box
                component="sup"
                sx={{ ml: 0.5, fontSize: "0.7em", color: "text.secondary" }}
              >
                *
              </Box>
            )}
          </Typography>
          <Button
            onClick={() => setShowTable(!showTable)}
            endIcon={showTable ? <ExpandLessIcon /> : <ExpandMoreIcon />}
          >
            {showTable ? "Hide" : "Show"} Table
          </Button>
        </Box>
        <Collapse in={showTable}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Date</TableCell>
                <TableCell>Age</TableCell>
                <TableCell align="right">Balance</TableCell>
                <TableCell align="right">Contributions</TableCell>
                <TableCell align="right">Withdrawals</TableCell>
                <TableCell align="right">Income</TableCell>
                <TableCell align="right">Tax</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {result.deterministicProjection.map((row, idx) => {
                const seriesValue =
                  selectedKey === "Deterministic"
                    ? row.balance
                    : (seriesPoints[idx]?.value ?? 0);

                const previousSeriesValue =
                  idx > 0
                    ? selectedKey === "Deterministic"
                      ? result.deterministicProjection[idx - 1].balance
                      : (seriesPoints[idx - 1]?.value ?? 0)
                    : 0;

                // Contributions and income are series-independent (deterministic schedules).
                const contributions = row.yearContributions;
                const income = row.yearIncome;

                // Withdrawals: deterministic uses the engine's value (income-first for
                // FIXED_DOLLAR, savings-only for FIXED_PERCENTAGE). For non-deterministic
                // series we re-derive from the strategy + that series' balance, then cap at
                // savings actually available (previous series balance + contributions). Income
                // is paid directly to the user and never enters savings, so it's not part of
                // the cap.
                const flatTaxRate = scenario?.assumptions.flatTaxRate ?? 0;
                let withdrawals: number;
                if (selectedKey === "Deterministic") {
                  withdrawals = row.yearWithdrawals;
                } else if (row.yearWithdrawals === 0) {
                  withdrawals = 0;
                } else {
                  let requested: number;
                  if (scenario?.assumptions.withdrawalStrategy === "FIXED_PERCENTAGE") {
                    requested = seriesValue * (scenario.assumptions.withdrawalPercentage ?? 0);
                  } else {
                    // FIXED_DOLLAR: configured monthly target × 12 × inflation, less income
                    // (cashflow-target semantics — savings only fill the gap).
                    const monthlyTarget = scenario?.assumptions.withdrawalMonthlyAmount ?? 0;
                    const annualTarget = monthlyTarget * 12 * row.inflationFactor;
                    requested = Math.max(0, annualTarget - income);
                  }
                  const availableSavings = Math.max(0, previousSeriesValue + contributions);
                  withdrawals = Math.min(requested, availableSavings);
                }

                const tax = (income + withdrawals) * flatTaxRate;

                return (
                  <TableRow key={row.date}>
                    <TableCell>{formatMonthYear(row.date)}</TableCell>
                    <TableCell>{row.age}</TableCell>
                    <TableCell align="right">{formatCurrency(seriesValue)}</TableCell>
                    <TableCell align="right">{formatCurrency(contributions)}</TableCell>
                    <TableCell align="right">{formatCurrency(withdrawals)}</TableCell>
                    <TableCell align="right">{formatCurrency(income)}</TableCell>
                    <TableCell align="right">{formatCurrency(tax)}</TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
          {selectedKey !== "Deterministic" && scenario && (
            <Typography
              variant="caption"
              color="text.secondary"
              sx={{ display: "block", mt: 1.5 }}
            >
              <Box component="span" sx={{ mr: 0.5 }}>
                *
              </Box>
              Contributions and Income are deterministic schedules — identical across all Monte
              Carlo trials.
              <br />
              {scenario.assumptions.withdrawalStrategy === "FIXED_PERCENTAGE"
                ? "Withdrawals on this series are derived as (percentage × that series' balance), capped at savings available that year (previous balance + contributions). Income is paid on top — not netted from withdrawals. Tax is (withdrawals + income) × flat tax rate."
                : "Withdrawals on this series cover the gap between the configured monthly cashflow target × inflation and incoming income that month, capped at savings available (previous balance + contributions). If income meets the target, savings withdrawal is zero. Tax is (withdrawals + income) × flat tax rate."}
            </Typography>
          )}
        </Collapse>
      </Paper>

      {scenario && (
        <Paper sx={{ p: 3, mb: 3 }}>
          <Box sx={{ display: "flex", alignItems: "center", mb: 1 }}>
            <Typography variant="h6" sx={{ flexGrow: 1 }}>
              Scenario Parameters
            </Typography>
            <Button
              onClick={() => setShowParams(!showParams)}
              endIcon={showParams ? <ExpandLessIcon /> : <ExpandMoreIcon />}
            >
              {showParams ? "Hide" : "Show"}
            </Button>
          </Box>
          <Collapse in={showParams}>
            <Table size="small">
              <TableBody>
                <TableRow>
                  <TableCell sx={{ fontWeight: 500 }}>Expected Rate of Return</TableCell>
                  <TableCell>{formatPercent(scenario.assumptions.expectedRateOfReturn)}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: 500 }}>Inflation Rate</TableCell>
                  <TableCell>{formatPercent(scenario.assumptions.inflationRate)}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: 500 }}>
                    Standard Deviation (return volatility)
                  </TableCell>
                  <TableCell>{formatPercent(scenario.assumptions.standardDeviation)}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: 500 }}>Withdrawal Strategy</TableCell>
                  <TableCell>
                    {scenario.assumptions.withdrawalStrategy === "FIXED_PERCENTAGE"
                      ? `Fixed Percentage — ${formatPercent(
                          scenario.assumptions.withdrawalPercentage ?? 0,
                        )} of balance per year`
                      : `Fixed Dollar — ${formatCurrency(
                          scenario.assumptions.withdrawalMonthlyAmount ?? 0,
                        )}/month (inflation-adjusted)`}
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: 500 }}>Flat Tax Rate</TableCell>
                  <TableCell>{formatPercent(scenario.assumptions.flatTaxRate)}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: 500 }}>Monte Carlo Trials</TableCell>
                  <TableCell>{scenario.assumptions.monteCarloTrials.toLocaleString()}</TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </Collapse>
        </Paper>
      )}
    </Box>
  );
}
