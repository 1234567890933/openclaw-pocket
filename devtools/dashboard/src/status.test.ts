import test from "node:test";
import assert from "node:assert/strict";
import { aggregate, formatBytes, verdict, type InstallSnapshot } from "./status.js";

const sample: InstallSnapshot = {
  deviceModel: "Pixel 7",
  apiLevel: 34,
  startedAt: "2026-11-12T12:00:00Z",
  components: [
    { id: "proot", label: "proot", bytes: 100, bytesRead: 100, state: "done" },
    { id: "rootfs", label: "rootfs", bytes: 900, bytesRead: 450, state: "running" },
    { id: "tools", label: "tools", bytes: 50, bytesRead: 0, state: "failed", error: "404" },
  ],
};

test("aggregate counts states and bytes", () => {
  const result = aggregate(sample);
  assert.equal(result.totalBytes, 1050);
  assert.equal(result.doneBytes, 550);
  assert.equal(result.byState.done, 1);
  assert.equal(result.byState.failed, 1);
  assert.equal(result.failures.length, 1);
  assert.equal(result.slowest?.id, "rootfs");
});

test("fraction never exceeds one", () => {
  const over: InstallSnapshot = {
    ...sample,
    components: [{ id: "x", label: "x", bytes: 10, bytesRead: 999, state: "running" }],
  };
  assert.equal(aggregate(over).fraction, 1);
});

test("formatBytes uses the right unit", () => {
  assert.equal(formatBytes(2048), "2 KB");
  assert.equal(formatBytes(2 * 1_048_576), "2.0 MB");
  assert.equal(formatBytes(2 * 1_073_741_824), "2.00 GB");
});

test("verdict names the broken component", () => {
  assert.match(verdict(sample), /^BROKEN/);
  assert.match(verdict(sample), /tools/);
});
