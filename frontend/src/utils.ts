// Parse a YYYY-MM-DD ISO date string as a local-time Date (avoids the
// browser's default UTC interpretation, which causes off-by-one display in
// timezones west of UTC).
export function parseLocalDate(iso: string): Date {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(y, m - 1, d);
}

// "22 October 2031"
export function formatLongDate(iso: string): string {
  if (!iso) return "";
  return parseLocalDate(iso).toLocaleDateString("en-US", {
    day: "numeric",
    month: "long",
    year: "numeric",
  });
}

// "OCT 2031"
export function formatMonthYear(iso: string): string {
  if (!iso) return "";
  return parseLocalDate(iso)
    .toLocaleDateString("en-US", { month: "short", year: "numeric" })
    .toUpperCase();
}

// Estimated payoff date for a mortgage given current balance, annual rate, and monthly P+I.
// Returns null if the payment cannot amortize the loan (interest-only or less than interest).
// Math: standard amortization closed-form. n = -log(1 - r*B/P) / log(1+r) where r = monthlyRate.
export function estimateMortgagePayoff(
  balance: number,
  annualRate: number,
  monthlyPI: number,
  from: Date = new Date(),
): Date | null {
  if (balance <= 0 || monthlyPI <= 0) return null;
  const r = annualRate / 12;
  if (r === 0) {
    const months = Math.ceil(balance / monthlyPI);
    const d = new Date(from);
    d.setMonth(d.getMonth() + months);
    return d;
  }
  const interestOnly = r * balance;
  if (monthlyPI <= interestOnly) return null;
  const n = -Math.log(1 - (r * balance) / monthlyPI) / Math.log(1 + r);
  const months = Math.ceil(n);
  const d = new Date(from);
  d.setMonth(d.getMonth() + months);
  return d;
}

// Compute the monthly P+I for a mortgage given balance, APR, and term in months.
// Standard amortization formula: P+I = B × r × (1+r)^n / ((1+r)^n - 1).
// Returns 0 if any input is non-positive.
export function calculateMonthlyPI(
  balance: number,
  annualRate: number,
  termMonths: number,
): number {
  if (balance <= 0 || termMonths <= 0) return 0;
  const r = annualRate / 12;
  if (r === 0) return balance / termMonths;
  const factor = Math.pow(1 + r, termMonths);
  return (balance * r * factor) / (factor - 1);
}

// Back-derive the remaining term (in months) from balance, APR, and current P+I.
// Inverse of calculateMonthlyPI; useful when loading an existing property where P+I is stored
// and we want to seed the "remaining term" UI field.
// Returns null if P+I can't amortize (interest-only or less).
export function deriveTermMonths(
  balance: number,
  annualRate: number,
  monthlyPI: number,
): number | null {
  if (balance <= 0 || monthlyPI <= 0) return null;
  const r = annualRate / 12;
  if (r === 0) return Math.ceil(balance / monthlyPI);
  if (monthlyPI <= r * balance) return null;
  return Math.ceil(-Math.log(1 - (r * balance) / monthlyPI) / Math.log(1 + r));
}

// Remaining months on a mortgage given its start date and original term (years), measured from
// "today" (or a caller-supplied reference date). Returns 0 if already past the original payoff.
// Used to feed calculateMonthlyPI when the user enters start-date + term-years instead of a
// raw remaining-term number.
export function remainingMortgageMonths(
  mortgageStartDateIso: string,
  termYears: number,
  from: Date = new Date(),
): number {
  if (!mortgageStartDateIso || termYears <= 0) return 0;
  const start = parseLocalDate(mortgageStartDateIso);
  const totalMonths = Math.round(termYears * 12);
  const elapsedMonths =
    (from.getFullYear() - start.getFullYear()) * 12 + (from.getMonth() - start.getMonth());
  return Math.max(0, totalMonths - elapsedMonths);
}
