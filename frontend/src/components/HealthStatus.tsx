import { useEffect, useState } from "react";
import Tooltip from "@mui/material/Tooltip";
import Box from "@mui/material/Box";
import CloudDoneRoundedIcon from "@mui/icons-material/CloudDoneRounded";
import CloudOffRoundedIcon from "@mui/icons-material/CloudOffRounded";
import CloudQueueRoundedIcon from "@mui/icons-material/CloudQueueRounded";
import {
  checkHealth,
  subscribeToHealth,
  type HealthState,
} from "../healthMonitor";

export default function HealthStatus() {
  const [status, setStatus] = useState<HealthState>("checking");

  useEffect(() => {
    const unsubscribe = subscribeToHealth(setStatus);
    checkHealth();
    return unsubscribe;
  }, []);

  let icon;
  let tooltip;
  if (status === "up") {
    icon = <CloudDoneRoundedIcon sx={{ color: "#4caf50" }} aria-label="modeling-services-up" />;
    tooltip = "Modeling services available";
  } else if (status === "down") {
    icon = <CloudOffRoundedIcon sx={{ color: "#ef5350" }} aria-label="modeling-services-down" />;
    tooltip = "Modeling services not available";
  } else {
    icon = (
      <CloudQueueRoundedIcon
        sx={{ color: "rgba(255,255,255,0.7)" }}
        aria-label="modeling-services-checking"
      />
    );
    tooltip = "Checking modeling services...";
  }

  return (
    <Tooltip title={tooltip}>
      <Box sx={{ display: "flex", alignItems: "center" }}>{icon}</Box>
    </Tooltip>
  );
}
