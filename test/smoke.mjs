import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
const child = spawn("node", [join(here, "..", "dist", "index.js")], {
  stdio: ["pipe", "pipe", "inherit"],
  // Force "not running": point at an unused port with a dummy token and a bogus profile dir.
  env: { ...process.env, USERPROFILE: join(here, "nonexistent"), CRAFTPILOT_PORT: "1", CRAFTPILOT_TOKEN: "x" },
});

const pending = new Map();
let buf = "";
child.stdout.on("data", (d) => {
  buf += d.toString();
  let i;
  while ((i = buf.indexOf("\n")) >= 0) {
    const line = buf.slice(0, i).trim();
    buf = buf.slice(i + 1);
    if (!line) continue;
    const msg = JSON.parse(line);
    if (msg.id !== undefined && pending.has(msg.id)) pending.get(msg.id)(msg);
  }
});

let nextId = 1;
const send = (method, params) =>
  new Promise((resolve) => {
    const id = nextId++;
    pending.set(id, resolve);
    child.stdin.write(JSON.stringify({ jsonrpc: "2.0", id, method, params }) + "\n");
  });

const init = await send("initialize", {
  protocolVersion: "2025-03-26",
  capabilities: {},
  clientInfo: { name: "smoke", version: "0.0.0" },
});
console.log("server:", init.result.serverInfo);
child.stdin.write(JSON.stringify({ jsonrpc: "2.0", method: "notifications/initialized" }) + "\n");

const tools = await send("tools/list", {});
console.log("tools:", tools.result.tools.map((t) => t.name).join(", "));

const res = await send("tools/call", { name: "mc_status", arguments: {} });
console.log("mc_status:", JSON.stringify(res.result));
const ok = res.result.isError && res.result.content[0].text.includes("not running");
console.log(ok ? "SMOKE OK" : "SMOKE FAILED");
child.kill();
process.exit(ok ? 0 : 1);
