import api from "./api";

export type HealthState = "checking" | "up" | "down";

let currentStatus: HealthState = "checking";
const listeners = new Set<(s: HealthState) => void>();
let pollIntervalId: ReturnType<typeof setInterval> | null = null;
let inFlight = false;

const POLL_INTERVAL_MS = 60_000;

function setStatus(next: HealthState) {
  if (currentStatus === next) return;
  currentStatus = next;
  listeners.forEach((cb) => cb(next));
}

export async function checkHealth(): Promise<void> {
  if (inFlight) return;
  inFlight = true;
  try {
    await api.get("/api/health");
    setStatus("up");
    if (pollIntervalId) {
      clearInterval(pollIntervalId);
      pollIntervalId = null;
    }
  } catch {
    setStatus("down");
    if (!pollIntervalId) {
      pollIntervalId = setInterval(() => {
        checkHealth();
      }, POLL_INTERVAL_MS);
    }
  } finally {
    inFlight = false;
  }
}

// Called by the axios response interceptor on errors that suggest the backend is unreachable
// (network error or 5xx). Triggers a fresh health probe so the indicator updates promptly.
export function markPossiblyDown(): void {
  if (currentStatus !== "down") {
    checkHealth();
  }
}

export function subscribeToHealth(cb: (s: HealthState) => void): () => void {
  listeners.add(cb);
  cb(currentStatus);
  return () => {
    listeners.delete(cb);
  };
}

// Test-only: reset module state between tests.
export function _resetHealthMonitorForTests(): void {
  currentStatus = "checking";
  listeners.clear();
  if (pollIntervalId) {
    clearInterval(pollIntervalId);
    pollIntervalId = null;
  }
  inFlight = false;
}
