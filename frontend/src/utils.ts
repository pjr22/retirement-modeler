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
