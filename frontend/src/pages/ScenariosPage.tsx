import { useCallback, useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router";
import {
  Box,
  Typography,
  Button,
  List,
  ListItem,
  ListItemText,
  ListItemButton,
  IconButton,
  Alert,
} from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import AddIcon from "@mui/icons-material/Add";
import DeleteIcon from "@mui/icons-material/Delete";
import { listScenarios, deleteScenario } from "../api";
import type { Scenario } from "../types";

export default function ScenariosPage() {
  const { profileId } = useParams<{ profileId: string }>();
  const navigate = useNavigate();
  const [scenarios, setScenarios] = useState<Scenario[]>([]);
  const [error, setError] = useState("");

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
    loadScenarios();
  }, [loadScenarios]);

  const handleDelete = async (id: string) => {
    try {
      await deleteScenario(id);
      loadScenarios();
    } catch {
      setError("Failed to delete scenario");
    }
  };

  return (
    <Box>
      {error && (
        <Alert severity="error" onClose={() => setError("")} sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      <Box sx={{ display: "flex", alignItems: "center", mb: 3 }}>
        <IconButton onClick={() => navigate(`/profiles/${profileId}`)} sx={{ mr: 1 }}>
          <ArrowBackIcon />
        </IconButton>
        <Typography variant="h4" sx={{ flexGrow: 1 }}>
          Scenarios
        </Typography>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => navigate(`/scenarios/new?profileId=${profileId}`)}
        >
          Create Scenario
        </Button>
      </Box>

      {scenarios.length === 0 ? (
        <Typography color="text.secondary" sx={{ textAlign: "center", mt: 4 }}>
          No scenarios yet. Create one to start planning.
        </Typography>
      ) : (
        <List>
          {scenarios.map((s) => (
            <ListItem
              key={s.id}
              secondaryAction={
                <IconButton edge="end" color="error" onClick={() => handleDelete(s.id)}>
                  <DeleteIcon />
                </IconButton>
              }
              disablePadding
            >
              <ListItemButton onClick={() => navigate(`/scenarios/${s.id}`)}>
                <ListItemText
                  primary={s.name}
                  secondary={
                    s.description
                      ? `${s.description} · ${s.accountIds.length} account(s)`
                      : `${s.accountIds.length} account(s)`
                  }
                />
              </ListItemButton>
            </ListItem>
          ))}
        </List>
      )}
    </Box>
  );
}
