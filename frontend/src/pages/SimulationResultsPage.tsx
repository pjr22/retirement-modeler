import { useCallback, useEffect, useState } from "react";
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
} from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import ExpandLessIcon from "@mui/icons-material/ExpandLess";
import {
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  Line,
  ComposedChart,
} from "recharts";
import { getSimulation, getScenario } from "../api";
import type { SimulationResult, Scenario } from "../types";

export default function SimulationResultsPage() {
  const { simulationId } = useParams<{ simulationId: string }>();
  const navigate = useNavigate();
  const [result, setResult] = useState<SimulationResult | null>(null);
  const [scenario, setScenario] = useState<Scenario | null>(null);
  const [error, setError] = useState("");
  const [showTable, setShowTable] = useState(false);

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

  if (!result) {
    return error ? (
      <Alert severity="error">{error}</Alert>
    ) : (
      <Typography>Loading simulation results...</Typography>
    );
  }

  const formatCurrency = (val: number) =>
    new Intl.NumberFormat("en-US", {
      style: "currency",
      currency: "USD",
      maximumFractionDigits: 0,
    }).format(val);

  const finalProjection = result.deterministicProjection[result.deterministicProjection.length - 1];
  const mc = result.monteCarloSummary;

  const chartData = result.deterministicProjection.map((dp) => {
    const mcPoint = mc.percentileBalances.find((p) => p.age === dp.age);
    return {
      age: dp.age,
      Deterministic: dp.totalBalance,
      "10th %": mcPoint?.p10 ?? 0,
      "25th %": mcPoint?.p25 ?? 0,
      Median: mcPoint?.p50 ?? 0,
      "75th %": mcPoint?.p75 ?? 0,
      "90th %": mcPoint?.p90 ?? 0,
    };
  });

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

      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <Paper sx={{ p: 2, textAlign: "center" }}>
            <Typography variant="overline" color="text.secondary">
              Success Rate
            </Typography>
            <Typography
              variant="h4"
              color={
                mc.successRate >= 90
                  ? "success.main"
                  : mc.successRate >= 70
                    ? "warning.main"
                    : "error.main"
              }
            >
              {mc.successRate.toFixed(1)}%
            </Typography>
          </Paper>
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <Paper sx={{ p: 2, textAlign: "center" }}>
            <Typography variant="overline" color="text.secondary">
              Median Years of Survival
            </Typography>
            <Typography variant="h4">{mc.medianYearsOfSurvival.toFixed(1)}</Typography>
          </Paper>
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <Paper sx={{ p: 2, textAlign: "center" }}>
            <Typography variant="overline" color="text.secondary">
              Final Balance (Deterministic)
            </Typography>
            <Typography variant="h4">
              {formatCurrency(finalProjection?.totalBalance ?? 0)}
            </Typography>
          </Paper>
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <Paper sx={{ p: 2, textAlign: "center" }}>
            <Typography variant="overline" color="text.secondary">
              Monte Carlo Trials
            </Typography>
            <Typography variant="h4">{mc.trials}</Typography>
          </Paper>
        </Grid>
      </Grid>

      <Paper sx={{ p: 3, mb: 3 }}>
        <Typography variant="h6" gutterBottom>
          Portfolio Balance Over Time
        </Typography>
        <ResponsiveContainer width="100%" height={400}>
          <ComposedChart data={chartData}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="age" label={{ value: "Age", position: "insideBottom", offset: -5 }} />
            <YAxis
              tickFormatter={(v: number) => `$${(v / 1000).toFixed(0)}k`}
              label={{ value: "Balance", angle: -90, position: "insideLeft" }}
            />
            <Tooltip formatter={(value: number) => formatCurrency(value)} />
            <Legend />
            <Area type="monotone" dataKey="10th %" stroke="none" fill="#ffcdd2" fillOpacity={0.3} />
            <Area type="monotone" dataKey="90th %" stroke="none" fill="#c8e6c9" fillOpacity={0.3} />
            <Area type="monotone" dataKey="25th %" stroke="none" fill="#ffe0b2" fillOpacity={0.3} />
            <Area type="monotone" dataKey="75th %" stroke="none" fill="#c8e6c9" fillOpacity={0.2} />
            <Line type="monotone" dataKey="Median" stroke="#ff9800" dot={false} strokeWidth={2} />
            <Line
              type="monotone"
              dataKey="Deterministic"
              stroke="#1565c0"
              dot={false}
              strokeWidth={2}
            />
          </ComposedChart>
        </ResponsiveContainer>
      </Paper>

      <Paper sx={{ p: 3, mb: 3 }}>
        <Box sx={{ display: "flex", alignItems: "center", mb: 1 }}>
          <Typography variant="h6" sx={{ flexGrow: 1 }}>
            Year-by-Year Details
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
                <TableCell>Age</TableCell>
                <TableCell align="right">Balance</TableCell>
                <TableCell align="right">Contributions</TableCell>
                <TableCell align="right">Withdrawals</TableCell>
                <TableCell align="right">Income</TableCell>
                <TableCell align="right">Tax</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {result.deterministicProjection.map((row) => (
                <TableRow key={row.age}>
                  <TableCell>{row.age}</TableCell>
                  <TableCell align="right">{formatCurrency(row.totalBalance)}</TableCell>
                  <TableCell align="right">{formatCurrency(row.totalContributions)}</TableCell>
                  <TableCell align="right">{formatCurrency(row.totalWithdrawals)}</TableCell>
                  <TableCell align="right">{formatCurrency(row.totalIncome)}</TableCell>
                  <TableCell align="right">{formatCurrency(row.totalTax)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Collapse>
      </Paper>
    </Box>
  );
}
