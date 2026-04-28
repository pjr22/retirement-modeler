import { useCallback, useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router";
import {
  Box,
  Typography,
  Button,
  Card,
  CardContent,
  CardActions,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  MenuItem,
  Grid,
  Alert,
} from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import AddIcon from "@mui/icons-material/Add";
import EditIcon from "@mui/icons-material/Edit";
import DeleteIcon from "@mui/icons-material/Delete";
import { listAccounts, createAccount, updateAccount, deleteAccount } from "../api";
import type { Account, AccountType } from "../types";

const ACCOUNT_TYPES: { value: AccountType; label: string }[] = [
  { value: "TRADITIONAL_401K", label: "Traditional 401(k)" },
  { value: "TRADITIONAL_IRA", label: "Traditional IRA" },
  { value: "ROTH_401K", label: "Roth 401(k)" },
  { value: "ROTH_IRA", label: "Roth IRA" },
  { value: "TAXABLE_BROKERAGE", label: "Taxable Brokerage" },
  { value: "SAVINGS", label: "Savings" },
  { value: "HSA", label: "HSA" },
  { value: "PENSION", label: "Pension" },
  { value: "SOCIAL_SECURITY", label: "Social Security" },
];

const CONTRIBUTION_TYPES: AccountType[] = [
  "TRADITIONAL_401K",
  "TRADITIONAL_IRA",
  "ROTH_401K",
  "ROTH_IRA",
  "HSA",
];

const BENEFIT_TYPES: AccountType[] = ["PENSION", "SOCIAL_SECURITY"];

interface AccountForm {
  name: string;
  accountType: AccountType;
  balance: number;
  annualContribution: number | null;
  monthlyBenefit: number | null;
  benefitStartAge: number | null;
}

const emptyForm: AccountForm = {
  name: "",
  accountType: "TRADITIONAL_401K",
  balance: 0,
  annualContribution: null,
  monthlyBenefit: null,
  benefitStartAge: null,
};

export default function AccountsPage() {
  const { profileId } = useParams<{ profileId: string }>();
  const navigate = useNavigate();
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [error, setError] = useState("");
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<AccountForm>(emptyForm);

  const loadAccounts = useCallback(async () => {
    if (!profileId) return;
    try {
      const res = await listAccounts(profileId);
      setAccounts(res.data);
    } catch {
      setError("Failed to load accounts");
    }
  }, [profileId]);

  useEffect(() => {
    loadAccounts();
  }, [loadAccounts]);

  const openCreate = () => {
    setEditingId(null);
    setForm(emptyForm);
    setDialogOpen(true);
  };

  const openEdit = (account: Account) => {
    setEditingId(account.id);
    setForm({
      name: account.name,
      accountType: account.accountType,
      balance: account.balance,
      annualContribution: account.annualContribution,
      monthlyBenefit: account.monthlyBenefit,
      benefitStartAge: account.benefitStartAge,
    });
    setDialogOpen(true);
  };

  const handleSave = async () => {
    if (!profileId) return;
    try {
      if (editingId) {
        await updateAccount(editingId, form);
      } else {
        await createAccount(profileId, form);
      }
      setDialogOpen(false);
      loadAccounts();
    } catch {
      setError("Failed to save account");
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await deleteAccount(id);
      loadAccounts();
    } catch {
      setError("Failed to delete account");
    }
  };

  const formatCurrency = (val: number | null) => (val != null ? `$${val.toLocaleString()}` : "—");

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
          Accounts
        </Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={openCreate}>
          Add Account
        </Button>
      </Box>

      {accounts.length === 0 ? (
        <Typography color="text.secondary" sx={{ textAlign: "center", mt: 4 }}>
          No accounts yet. Add one to get started.
        </Typography>
      ) : (
        <Grid container spacing={2}>
          {accounts.map((account) => (
            <Grid key={account.id} size={{ xs: 12, sm: 6, md: 4 }}>
              <Card variant="outlined">
                <CardContent>
                  <Typography variant="subtitle2" color="text.secondary">
                    {ACCOUNT_TYPES.find((t) => t.value === account.accountType)?.label ??
                      account.accountType}
                  </Typography>
                  <Typography variant="h6">{account.name}</Typography>
                  <Typography variant="body2" sx={{ mt: 1 }}>
                    Balance: {formatCurrency(account.balance)}
                  </Typography>
                  {account.annualContribution != null && (
                    <Typography variant="body2">
                      Annual Contribution: {formatCurrency(account.annualContribution)}
                    </Typography>
                  )}
                  {account.monthlyBenefit != null && (
                    <Typography variant="body2">
                      Monthly Benefit: {formatCurrency(account.monthlyBenefit)}
                      {account.benefitStartAge != null &&
                        ` starting at age ${account.benefitStartAge}`}
                    </Typography>
                  )}
                </CardContent>
                <CardActions>
                  <IconButton size="small" onClick={() => openEdit(account)}>
                    <EditIcon fontSize="small" />
                  </IconButton>
                  <IconButton size="small" color="error" onClick={() => handleDelete(account.id)}>
                    <DeleteIcon fontSize="small" />
                  </IconButton>
                </CardActions>
              </Card>
            </Grid>
          ))}
        </Grid>
      )}

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{editingId ? "Edit Account" : "Add Account"}</DialogTitle>
        <DialogContent sx={{ display: "flex", flexDirection: "column", gap: 2, mt: 1 }}>
          <TextField
            label="Account Name"
            value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })}
            fullWidth
            required
          />
          <TextField
            label="Account Type"
            select
            value={form.accountType}
            onChange={(e) => {
              const newType = e.target.value as AccountType;
              setForm({
                ...form,
                accountType: newType,
                balance: BENEFIT_TYPES.includes(newType) ? 0 : form.balance,
                annualContribution: CONTRIBUTION_TYPES.includes(newType)
                  ? form.annualContribution
                  : null,
                monthlyBenefit: BENEFIT_TYPES.includes(newType) ? form.monthlyBenefit : null,
                benefitStartAge: BENEFIT_TYPES.includes(newType) ? form.benefitStartAge : null,
              });
            }}
            fullWidth
          >
            {ACCOUNT_TYPES.map((t) => (
              <MenuItem key={t.value} value={t.value}>
                {t.label}
              </MenuItem>
            ))}
          </TextField>
          {!BENEFIT_TYPES.includes(form.accountType) && (
            <TextField
              label="Current Balance"
              type="number"
              value={form.balance || ""}
              onChange={(e) => setForm({ ...form, balance: Number(e.target.value) })}
              fullWidth
            />
          )}
          {CONTRIBUTION_TYPES.includes(form.accountType) && (
            <TextField
              label="Annual Contribution"
              type="number"
              value={form.annualContribution ?? ""}
              onChange={(e) =>
                setForm({ ...form, annualContribution: Number(e.target.value) || null })
              }
              fullWidth
            />
          )}
          {BENEFIT_TYPES.includes(form.accountType) && (
            <>
              <TextField
                label="Monthly Benefit"
                type="number"
                value={form.monthlyBenefit ?? ""}
                onChange={(e) =>
                  setForm({ ...form, monthlyBenefit: Number(e.target.value) || null })
                }
                fullWidth
              />
              <TextField
                label="Benefit Start Age"
                type="number"
                value={form.benefitStartAge ?? ""}
                onChange={(e) =>
                  setForm({
                    ...form,
                    benefitStartAge: e.target.value ? Number(e.target.value) : null,
                  })
                }
                fullWidth
              />
            </>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>Cancel</Button>
          <Button onClick={handleSave} variant="contained" disabled={!form.name}>
            {editingId ? "Save" : "Add"}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
