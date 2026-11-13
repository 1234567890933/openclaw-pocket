import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import { aggregate, formatBytes, verdict, type InstallSnapshot } from "./status.js";

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, "..");
const PORT = Number(process.env.PORT ?? 4310);

const server = createServer(async (req, res) => {
  const url = new URL(req.url ?? "/", `http://localhost:${PORT}`);

  if (url.pathname === "/") {
    const html = await readFile(join(root, "public", "index.html"), "utf8");
    res.writeHead(200, { "content-type": "text/html; charset=utf-8" });
    res.end(html);
    return;
  }

  if (url.pathname === "/styles.css") {
    const css = await readFile(join(root, "public", "styles.css"), "utf8");
    res.writeHead(200, { "content-type": "text/css; charset=utf-8" });
    res.end(css);
    return;
  }

  if (url.pathname === "/api/status") {
    try {
      const raw = await readFile(join(root, "snapshot.json"), "utf8");
      const snapshot = JSON.parse(raw) as InstallSnapshot;
      const result = aggregate(snapshot);
      res.writeHead(200, { "content-type": "application/json" });
      res.end(
        JSON.stringify({
          verdict: verdict(snapshot),
          device: `${snapshot.deviceModel} · API ${snapshot.apiLevel}`,
          total: formatBytes(result.totalBytes),
          done: formatBytes(result.doneBytes),
          fraction: result.fraction,
          components: snapshot.components,
          failures: result.failures.map((f) => ({ id: f.id, error: f.error ?? "unknown" })),
        }),
      );
    } catch (error) {
      res.writeHead(404, { "content-type": "application/json" });
      res.end(JSON.stringify({ error: "no snapshot.json yet" }));
    }
    return;
  }

  res.writeHead(404, { "content-type": "text/plain" });
  res.end("not found");
});

server.listen(PORT, "127.0.0.1", () => {
  console.log(`dashboard on http://127.0.0.1:${PORT}`);
});
