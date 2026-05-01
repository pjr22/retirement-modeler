export type FilingStatus =
  | "SINGLE"
  | "MARRIED_FILING_JOINTLY"
  | "MARRIED_FILING_SEPARATELY"
  | "HEAD_OF_HOUSEHOLD";

export type AccountType =
  | "TRADITIONAL_401K"
  | "TRADITIONAL_IRA"
  | "ROTH_401K"
  | "ROTH_IRA"
  | "TAXABLE_BROKERAGE"
  | "SAVINGS"
  | "HSA"
  | "PENSION"
  | "SOCIAL_SECURITY";

export type WithdrawalStrategy = "FIXED_PERCENTAGE" | "FIXED_DOLLAR";

export interface IncomeSource {
  id: string;
  name: string;
  annualAmount: number;
  endAge: number | null;
}

export interface UserProfile {
  id: string;
  name: string;
  dateOfBirth: string;
  plannedRetirementAge: number;
  lifeExpectancy: number;
  filingStatus: FilingStatus;
  incomeSources: IncomeSource[];
}

export interface Account {
  id: string;
  userProfileId: string;
  name: string;
  accountType: AccountType;
  balance: number;
  annualContribution: number | null;
  monthlyBenefit: number | null;
  benefitStartAge: number | null;
}

export interface SimulationAssumptions {
  expectedRateOfReturn: number;
  inflationRate: number;
  withdrawalStrategy: WithdrawalStrategy;
  withdrawalPercentage: number | null;
  withdrawalFixedAmount: number | null;
  standardDeviation: number;
  monteCarloTrials: number;
  flatTaxRate: number;
}

export interface Scenario {
  id: string;
  userProfileId: string;
  name: string;
  description: string | null;
  accountIds: string[];
  assumptions: SimulationAssumptions;
}

export interface YearlyProjection {
  age: number;
  year: number;
  totalBalance: number;
  totalContributions: number;
  totalWithdrawals: number;
  totalIncome: number;
  totalTax: number;
  inflationFactor: number;
}

export interface PercentilePoint {
  age: number;
  p10: number;
  p25: number;
  p50: number;
  p75: number;
  p90: number;
}

export interface MonteCarloSummary {
  trials: number;
  successRate: number;
  medianYearsOfSurvival: number;
  percentileBalances: PercentilePoint[];
}

export interface SimulationResult {
  id: string;
  scenarioId: string;
  userProfileId: string;
  createdAt: string;
  deterministicProjection: YearlyProjection[];
  monteCarloSummary: MonteCarloSummary;
}

export interface AuthResponse {
  token: string;
  userId: string;
  email: string;
}

export interface AuthState {
  token: string | null;
  userId: string | null;
  email: string | null;
  isAuthenticated: boolean;
}
