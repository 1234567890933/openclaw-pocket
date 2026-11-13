/**
 * Status aggregation for the local dashboard.
 *
 * The dashboard exists because the first-boot install is the hardest thing to
 * debug from a phone. This reads the JSON the app can export and turns it into
 * something you can look at on a laptop while the phone is plugged in.
 */

export type ComponentState = "pending" | "running" | "verifying" | "done" | "failed";

export interface ComponentStatus {
  id: string;
  label: string;
  bytes: number;
  bytesRead: number;
  state: ComponentState;
  error?: string;
}

export interface InstallSnapshot {
  deviceModel: string;
  apiLevel: number;
  startedAt: string;
  components: ComponentStatus[];
}

export interface Aggregate {
  totalBytes: number;
  doneBytes: number;
  fraction: number;
  byState: Record<ComponentState, number>;
  failures: ComponentStatus[];
  slowest: ComponentStatus | null;
}

const STATES: ComponentState[] = ["pending", "running", "verifying", "done", "failed"];

export function aggregate(snapshot: InstallSnapshot): Aggregate {
  const byState = STATES.reduce(
    (acc, state) => ({ ...acc, [state]: 0 }),
    {} as Record<ComponentState, number>,
  );

  let totalBytes = 0;
  let doneBytes = 0;

  for (const component of snapshot.components) {
    byState[component.state] += 1;
    totalBytes += component.bytes;
    if (component.state === "done") {
      doneBytes += component.bytes;
    } else {
      doneBytes += Math.min(component.bytesRead, component.bytes);
    }
  }

  const failures = snapshot.components.filter((c) => c.state === "failed");

  // "Slowest" is the largest component that is not finished: that is the one the
  // user is actually waiting on.
  const unfinished = snapshot.components
    .filter((c) => c.state !== "done")
    .sort((a, b) => b.bytes - a.bytes);

  return {
    totalBytes,
    doneBytes,
    fraction: totalBytes === 0 ? 0 : Math.min(1, doneBytes / totalBytes),
    byState,
    failures,
    slowest: unfinished[0] ?? null,
  };
}

export function formatBytes(bytes: number): string {
  if (bytes >= 1_073_741_824) return `${(bytes / 1_073_741_824).toFixed(2)} GB`;
  if (bytes >= 1_048_576) return `${(bytes / 1_048_576).toFixed(1)} MB`;
  return `${Math.round(bytes / 1024)} KB`;
}

export function verdict(snapshot: InstallSnapshot): string {
  const { fraction, failures } = aggregate(snapshot);
  if (failures.length > 0) {
    return `BROKEN — ${failures.length} component(s) failed: ${failures.map((f) => f.id).join(", ")}`;
  }
  if (fraction >= 1) return "READY";
  return `INCOMPLETE — ${Math.round(fraction * 100)}%`;
}
