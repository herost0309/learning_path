// uix CLI client — talks to the daemon over loopback HTTP, auto-starting the
// daemon (detached) when needed. Zero-dependency arg parsing.
import { spawn } from "node:child_process";
import path from "node:path";
import fs from "node:fs";
import { fileURLToPath } from "node:url";
import { RUNTIME_FILE, readRuntime } from "./home.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const DAEMON_JS = path.join(__dirname, "daemon.js");

const BOOL_FLAGS = new Set([
  "json", "full-page", "headless", "errors", "failures", "double",
  "alt", "ctrl", "shift", "meta",
]);

const HELP = `uix — browser exploration CLI (Playwright-based, no extension)

Session
  uix start [--headless]        launch the browser (persistent profile ~/.uix/profile)
  uix stop                      close browser + shut down the daemon
  uix status                    daemon/browser/tab status
Navigate & read
  uix navigate <url>            go to URL
  uix back | forward | reload   history control
  uix url                       current URL + title
  uix observe                   semantic element listing with @eN refs
  uix screenshot [--full-page] [--ref <t>] [--out <file>]
Interact (target = @eN | css-selector | text=Foo)
  uix click <target> [--double] [--button l|m|r] [--ctrl|--alt|--shift|--meta]
  uix fill <target> <value>
  uix press <key> [--on <target>]
  uix hover <target>
  uix select <target> <value>
  uix scroll-to <target>
  uix wheel --delta-y <n>
Diagnostics
  uix console [--errors]        buffered console output / JS errors
  uix network [--failures]      buffered network responses / failures
  uix evaluate <js>             run JS in the page, print result
Tabs
  uix tabs | tab-new [url] | tab-use <id> | tab-close [id]
Misc
  uix wait <ms>                 sleep daemon-side
  global: --json                raw JSON output
Refs die on navigation — re-run uix observe after navigating.`;

// ---------------------------------------------------------------------------
// arg parsing
// ---------------------------------------------------------------------------

export function parseArgs(argv) {
  const positionals = [];
  const flags = {};
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === "--") {
      positionals.push(...argv.slice(i + 1));
      break;
    }
    if (a.startsWith("--")) {
      const key = a.slice(2);
      if (BOOL_FLAGS.has(key)) flags[key] = true;
      else if (i + 1 < argv.length && !argv[i + 1].startsWith("--")) flags[key] = argv[++i];
      else flags[key] = true;
    } else {
      positionals.push(a);
    }
  }
  return { positionals, flags };
}

// ---------------------------------------------------------------------------
// daemon plumbing
// ---------------------------------------------------------------------------

async function pingDaemon(runtime, timeoutMs = 2000) {
  if (!runtime) return false;
  try {
    const ac = new AbortController();
    const t = setTimeout(() => ac.abort(), timeoutMs);
    const res = await fetch(`http://127.0.0.1:${runtime.port}/status`, {
      headers: { authorization: `Bearer ${runtime.token}` },
      signal: ac.signal,
    });
    clearTimeout(t);
    return res.ok;
  } catch {
    return false;
  }
}

async function ensureDaemon() {
  let runtime = readRuntime();
  if (await pingDaemon(runtime)) return runtime;
  // stale runtime file from a dead daemon
  try {
    fs.unlinkSync(RUNTIME_FILE);
  } catch {}
  const child = spawn(process.execPath, [DAEMON_JS, "--serve"], {
    detached: true,
    stdio: "ignore",
    windowsHide: true,
  });
  child.unref();
  const deadline = Date.now() + 20000;
  while (Date.now() < deadline) {
    await new Promise((r) => setTimeout(r, 250));
    runtime = readRuntime();
    if (await pingDaemon(runtime)) return runtime;
  }
  throw new Error("Daemon did not become ready in 20s. Check ~/.uix/daemon.log");
}

async function rpc(method, params = {}) {
  const runtime = await ensureDaemon();
  const ac = new AbortController();
  const t = setTimeout(() => ac.abort(), 120000);
  try {
    const res = await fetch(`http://127.0.0.1:${runtime.port}/rpc`, {
      method: "POST",
      headers: { "content-type": "application/json", authorization: `Bearer ${runtime.token}` },
      body: JSON.stringify({ method, params }),
      signal: ac.signal,
    });
    const body = await res.json();
    if (!body.ok) throw new Error(body.error || "rpc failed");
    return body.result;
  } finally {
    clearTimeout(t);
  }
}

// ---------------------------------------------------------------------------
// output formatting
// ---------------------------------------------------------------------------

const fmtConsole = (entries) =>
  entries.map((e) => {
    const at = e.url ? ` (${e.url}${e.line ? ":" + e.line : ""})` : "";
    return `[${e.seq}] ${e.kind.toUpperCase()} ${e.text}${at}`;
  }).join("\n") || "(no entries)";

const fmtNetwork = (entries) =>
  entries.map((e) => {
    if (e.event === "failed") return `[${e.seq}] ${e.method} FAILED ${e.url} (${e.error})`;
    return `[${e.seq}] ${e.method} ${e.status} ${e.url}`;
  }).join("\n") || "(no entries)";

const fmtTabs = (r) => {
  const rows = r.tabs.map(
    (t) => `  [${t.id}${t.id === r.activePageId ? "*" : " "}] ${t.url}`
  );
  return `tabs (active *):\n${rows.join("\n")}`;
};

// ---------------------------------------------------------------------------
// command dispatch
// ---------------------------------------------------------------------------

async function run(argv) {
  const { positionals, flags } = parseArgs(argv);
  const cmd = positionals.shift();
  const p = positionals;
  const json = (v) => (flags.json ? JSON.stringify(v, null, 2) : v);

  switch (cmd) {
    case undefined:
    case "help":
      console.log(HELP);
      return;

    case "start": {
      const r = await rpc("browser.start", { headless: !!flags.headless });
      console.log(json(r.alreadyRunning ? `Browser already running (${r.channel})` : `Browser started (${r.channel}) — profile: ~/.uix/profile`));
      return;
    }
    case "stop": {
      await rpc("daemon.shutdown");
      console.log("Stopped browser and daemon.");
      return;
    }
    case "status":
      console.log(json(await rpc("status")));
      return;

    case "navigate": {
      if (!p[0]) throw new Error("Usage: uix navigate <url>");
      const r = await rpc("navigate", { url: p[0] });
      console.log(json(`${r.status ? `[${r.status}] ` : ""}${r.url} — ${r.title}`));
      return;
    }
    case "back": console.log(json(await rpc("back"))); return;
    case "forward": console.log(json(await rpc("forward"))); return;
    case "reload": console.log(json(await rpc("reload"))); return;
    case "url": {
      const r = await rpc("url");
      console.log(json(`${r.url} — ${r.title}`));
      return;
    }

    case "observe": {
      const r = await rpc("observe", { format: !flags.json });
      console.log(json(flags.json ? r : r.text));
      return;
    }

    case "click": {
      if (!p[0]) throw new Error("Usage: uix click <target>");
      const modifiers = ["ctrl", "alt", "shift", "meta"].filter((m) => flags[m]);
      const r = await rpc("click", {
        target: p[0],
        double: !!flags.double,
        button: flags.button ? { l: "left", m: "middle", r: "right" }[flags.button] : undefined,
        modifiers: modifiers.length ? modifiers : undefined,
        timeout: flags.timeout ? +flags.timeout : undefined,
      });
      console.log(json(r));
      return;
    }
    case "fill": {
      if (!p[0] || p.length < 2) throw new Error("Usage: uix fill <target> <value>");
      const r = await rpc("fill", { target: p[0], value: p.slice(1).join(" ") });
      console.log(json(r));
      return;
    }
    case "press": {
      if (!p[0]) throw new Error("Usage: uix press <key> [--on <target>]");
      const r = await rpc("press", { key: p[0], target: flags.on });
      console.log(json(r));
      return;
    }
    case "hover": {
      if (!p[0]) throw new Error("Usage: uix hover <target>");
      console.log(json(await rpc("hover", { target: p[0] })));
      return;
    }
    case "select": {
      if (p.length < 2) throw new Error("Usage: uix select <target> <value>");
      console.log(json(await rpc("select", { target: p[0], value: p[1] })));
      return;
    }
    case "scroll-to": {
      if (!p[0]) throw new Error("Usage: uix scroll-to <target>");
      console.log(json(await rpc("scrollTo", { target: p[0] })));
      return;
    }
    case "wheel": {
      console.log(json(await rpc("wheel", { deltaY: +(flags["delta-y"] ?? 600) })));
      return;
    }

    case "screenshot": {
      const out = path.resolve(flags.out ?? `uix-shot-${new Date().toISOString().replace(/[-:.TZ]/g, "").slice(0, 14)}.png`);
      const r = await rpc("screenshot", {
        out,
        fullPage: !!flags["full-page"],
        target: flags.ref,
        timeout: flags.timeout ? +flags.timeout : undefined,
      });
      console.log(json(`Saved ${r.fullPage ? "(full page) " : ""}${out}`));
      return;
    }

    case "console": {
      const r = await rpc("console", { cursor: +(flags.cursor ?? flags.since ?? 0), errorsOnly: !!flags.errors });
      console.log(json(flags.json ? r : fmtConsole(r.entries)));
      return;
    }
    case "network": {
      const r = await rpc("network", { cursor: +(flags.cursor ?? flags.since ?? 0), failuresOnly: !!flags.failures });
      console.log(json(flags.json ? r : fmtNetwork(r.entries)));
      return;
    }

    case "evaluate": {
      if (!p[0]) throw new Error("Usage: uix evaluate <js>");
      const r = await rpc("evaluate", { js: p.join(" ") });
      console.log(json(r.value === undefined ? "(undefined)" : typeof r.value === "string" ? r.value : JSON.stringify(r.value, null, 2)));
      return;
    }

    case "tabs":
      console.log(json(flags.json ? await rpc("tabs.list") : fmtTabs(await rpc("tabs.list"))));
      return;
    case "tab-new": {
      const r = await rpc("tabs.new", { url: p[0] });
      console.log(json(`New tab [${r.id}] ${r.url}`));
      return;
    }
    case "tab-use": {
      if (!p[0]) throw new Error("Usage: uix tab-use <id>");
      console.log(json(await rpc("tabs.use", { id: +p[0] })));
      return;
    }
    case "tab-close":
      console.log(json(await rpc("tabs.close", { id: p[0] ? +p[0] : undefined })));
      return;

    case "wait":
      if (!p[0]) throw new Error("Usage: uix wait <ms>");
      console.log(json(await rpc("wait", { ms: +p[0] })));
      return;

    default:
      throw new Error(`Unknown command: ${cmd}. Run: uix help`);
  }
}

export async function runCli(argv) {
  try {
    await run(argv);
  } catch (err) {
    console.error(`error: ${String(err.message || err)}`);
    process.exitCode = 1;
  }
}
