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
  | "HSA";

export type IncomeType =
  | "EMPLOYMENT"
  | "SELF_EMPLOYMENT"
  | "PENSION"
  | "SOCIAL_SECURITY"
  | "RENTAL"
  | "OTHER";

export type PropertyType = "PRIMARY_RESIDENCE" | "RENTAL" | "SECOND_HOME" | "LAND";

export type WithdrawalStrategy = "PORTFOLIO_PERCENTAGE" | "CASHFLOW_TARGET";

export type WithdrawalOrderingStrategy = "PROPORTIONAL" | "TAX_OPTIMIZED" | "CUSTOM";

export interface IncomeSource {
  id: string;
  scenarioId: string;
  name: string;
  type: IncomeType;
  monthlyAmount: number;
  startDate: string | null;
  endDate: string | null;
  inflationAdjusted: boolean;
}

export interface UserProfile {
  id: string;
  name: string;
  dateOfBirth: string;
  plannedRetirementDate: string;
  lifeExpectancy: number;
  filingStatus: FilingStatus;
}

export interface Account {
  id: string;
  userProfileId: string;
  name: string;
  accountType: AccountType;
  balance: number;
  annualContribution: number | null;
}

export interface Property {
  id: string;
  userProfileId: string;
  name: string;
  type: PropertyType;
  currentValue: number;
  costBasis: number;
  mortgageBalance: number;
  mortgageAnnualRate: number;
  mortgageMonthlyPi: number;
  mortgageStartDate: string | null;
  mortgageTermYears: number | null;
  plannedSaleDate: string | null;
  postSaleMonthlyHousingCost: number;
  annualPropertyTax: number;
  annualInsurance: number;
  monthlyHoa: number;
  annualMaintenancePct: number;
  sellingCostPct: number | null;
}

export interface SimulationAssumptions {
  expectedRateOfReturn: number;
  inflationRate: number;
  withdrawalStrategy: WithdrawalStrategy;
  withdrawalPercentage: number | null;
  withdrawalMonthlyAmount: number | null;
  standardDeviation: number;
  monteCarloTrials: number;
  withdrawalOrderingStrategy: WithdrawalOrderingStrategy;
  customWithdrawalOrder: AccountType[];
}

export interface Scenario {
  id: string;
  userProfileId: string;
  name: string;
  description: string | null;
  accountIds: string[];
  propertyIds: string[];
  assumptions: SimulationAssumptions;
}

export interface YearlyProjection {
  age: number;
  date: string; // ISO YYYY-MM-DD; the row anchor (retirement-month)
  balance: number;
  yearContributions: number;
  yearWithdrawals: number;
  yearIncome: number;
  yearTax: number;
  yearOrdinaryIncome: number;
  yearCapitalGains: number;
  yearSocialSecurityBenefit: number;
  yearTaxableSocialSecurity: number;
  yearOrdinaryTax: number;
  yearCapitalGainsTax: number;
  yearRmd: number;
  yearMortgageInterest: number;
  yearPropertyTaxPaid: number;
  yearHousingExpenses: number;
  yearSaleProceedsNet: number;
  yearSaleCapitalGains: number;
  yearPropertyValueTotal: number;
  yearDeduction: number;
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
