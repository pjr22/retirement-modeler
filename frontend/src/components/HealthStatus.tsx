import { useEffect, useState } from "react";
import Chip from "@mui/material/Chip";
import CheckCircleOutlineRoundedIcon from "@mui/icons-material/CheckCircleOutlineRounded";
import ErrorOutlineRoundedIcon from "@mui/icons-material/ErrorOutlineRounded";
import api from "../api";

interface HealthResponse {
  status: string;
  timestamp: string;
}

export default function HealthStatus() {
  const [healthy, setHealthy] = useState<boolean | null>(null);

  useEffect(() => {
    api
      .get<HealthResponse>("/api/health")
      .then(() => setHealthy(true))
      .catch(() => setHealthy(false));
  }, []);

  if (healthy === null) {
    return <Chip label="Checking backend..." size="small" sx={{ color: "#fff" }} />;
  }

  if (healthy) {
    return (
      <Chip
        icon={<CheckCircleOutlineRoundedIcon sx={{ color: "#4caf50 !important" }} />}
        label="Backend connected"
        size="small"
        variant="outlined"
        sx={{ color: "#fff", borderColor: "rgba(255,255,255,0.5)" }}
      />
    );
  }

  return (
    <Chip
      icon={<ErrorOutlineRoundedIcon sx={{ color: "#ef5350 !important" }} />}
      label="Backend unavailable"
      size="small"
      variant="outlined"
      sx={{ color: "#fff", borderColor: "rgba(255,255,255,0.5)" }}
    />
  );
}
