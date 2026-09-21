// uix daemon — owns the browser process and serves the CLI over loopback HTTP.
//
//   uix CLI ──POST /rpc (bearer token)──▶ this daemon ──Playwright──▶ Chrome
//
// Design points borrowed from BrowserSkill:
// * dedicated persistent profile (~/.uix/profile) so login state survives
// * ref-store: observe() registers elements as @eN refs bound to lazy
//   Playwright locators; refs are invalidated on navigation
// * console/network ring buffers with monotonic seq cursors
import http from "node:http";
import crypto from "node:crypto";
import { chromium } from "playwright";
import {
  PROFILE_DIR,
  readRuntime,
  writeRuntime,
  clearRuntime,
  log,
  ensureHome,
} from "./home.js";
import { collectObservation, formatObservation } from "./observe.js";

const TOKEN = crypto.randomBytes(24).toString("hex");
const RING_MAX = 3000;

// ---------------------------------------------------------------------------
// state
// ---------------------------------------------------------------------------

let context = null; // Playwright BrowserContext (persistent)
let browserChannel = null; // 'chrome' | 'msedge' | undefined (bundled chromium)
let pages = new Map(); // pageId -> Page
let pageIds = new Map(); // Page -> pageId
let nextPageId = 1;
let activePageId = null;
let refs = new Map(); // pageId -> Map('@eN' -> { locator, describe })
let refCounter = 0; // global so refs stay unique across re-observes

const consoleBuf = [];
const networkBuf = [];
let consoleSeq = 0;
let networkSeq = 0;

// ---------------------------------------------------------------------------
// browser lifecycle
// ---------------------------------------------------------------------------

async function startBrowser({ headless = false } = {}) {
  if (context) return { alreadyRunning: true, channel: browserChannel };
  const channels = ["chrome", "msedge", undefined];
  let lastErr = null;
  for (const channel of channels) {
    try {
      context = await chromium.launchPersistentContext(PROFILE_DIR, {
        channel,
        headless,
        viewport: null, // use the real window size
        args: ["--start-maximized"],
      });
      browserChannel = channel ?? "chromium-bundled";
      break;
    } catch (err) {
      lastErr = err;
    }
  }
  if (!context) {
    throw new Error(
      "No usable Chromium browser found. Install Google Chrome or Edge, " +
        "or run `npx playwright install chromium` inside the uix folder. " +
        `Last error: ${String(lastErr).split("\n")[0]}`
    );
  }
  log("browser started, channel:", browserChannel);
  context.on("page", trackPage);
  // Browser process exit / window close must not leave ghost state behind —
  // otherwise `browserRunning` lies and every tool fails confusingly.
  context.on("close", () => {
    log("browser context closed");
    refs.clear();
    pages.clear();
    pageIds.clear();
    activePageId = null;
    context = null;
  });
  for (const p of context.pages()) trackPage(p);
  const first = context.pages()[0];
  if (first) activePageId = pageIds.get(first);
  return { alreadyRunning: false, channel: browserChannel, pages: context.pages().length };
}

async function stopBrowser() {
  refs.clear();
  pages.clear();
  pageIds.clear();
  activePageId = null;
  if (!context) return { wasRunning: false };
  try {
    await context.close();
  } catch {
    // browser window may already be gone
  }
  context = null;
  return { wasRunning: true };
}

function trackPage(page) {
  if (pageIds.has(page)) return;
  const id = nextPageId++;
  pages.set(id, page);
  pageIds.set(page, id);
  refs.set(id, new Map());
  if (!activePageId) activePageId = id;

  page.on("console", (msg) => {
    const loc = msg.location();
    consoleBuf.push({
      seq: ++consoleSeq,
      ts: Date.now(),
      kind: msg.type(), // 'error' | 'warning' | 'log' | ...
      text: msg.text().slice(0, 500),
      url: loc.url?.slice(0, 120),
      line: loc.lineNumber,
      page: page.url()?.slice(0, 120),
    });
    if (consoleBuf.length > RING_MAX) consoleBuf.shift();
  });
  page.on("pageerror", (err) => {
    consoleBuf.push({
      seq: ++consoleSeq,
      ts: Date.now(),
      kind: "pageerror",
      text: String(err.message || err).slice(0, 500),
      url: page.url()?.slice(0, 120),
      page: page.url()?.slice(0, 120),
    });
    if (consoleBuf.length > RING_MAX) consoleBuf.shift();
  });
  page.on("response", (res) => {
    const url = res.url();
    if (url.startsWith("data:") || url.startsWith("blob:")) return;
    networkBuf.push({
      seq: ++networkSeq,
      ts: Date.now(),
      event: "response",
      method: res.request().method(),
      status: res.status(),
      url: url.slice(0, 250),
      resourceType: res.request().resourceType(),
    });
    if (networkBuf.length > RING_MAX) networkBuf.shift();
  });
  page.on("requestfailed", (req) => {
    const url = req.url();
    if (url.startsWith("data:") || url.startsWith("blob:")) return;
    networkBuf.push({
      seq: ++networkSeq,
      ts: Date.now(),
      event: "failed",
      method: req.method(),
      status: 0,
      url: url.slice(0, 250),
      error: req.failure()?.errorText || "failed",
    });
    if (networkBuf.length > RING_MAX) networkBuf.shift();
  });
  page.on("framenavigated", (frame) => {
    if (frame === page.mainFrame()) {
      refs.set(pageIds.get(page), new Map()); // refs die on navigation
    }
  });
  page.on("close", () => {
    const pid = pageIds.get(page);
    if (pid !== undefined) {
      pages.delete(pid);
      refs.delete(pid);
    }
    pageIds.delete(page);
    if (activePageId === pid) activePageId = pages.keys().next().value ?? null;
  });
}

// ---------------------------------------------------------------------------
// helpers
// ---------------------------------------------------------------------------

// Self-healing browser access: relaunch if the browser died, open a tab if
// the user closed all tabs. Exploration sessions should never dead-end.
async function requirePage() {
  if (!context) {
    await startBrowser({ headless: process.env.UIX_HEADLESS === "1" });
  }
  let page = pages.get(activePageId) ?? pages.values().next().value;
  if (!page) {
    page = await context.newPage();
    activePageId = pageIds.get(page);
  }
  return page;
}

// Resolve a CLI target into a Playwright locator.
//   @eN            → ref-store lookup
//   text=Foo       → getByText
//   anything else  → CSS selector
function resolveTarget(page, target) {
  const pid = pageIds.get(page);
  if (target.startsWith("@")) {
    const ref = refs.get(pid)?.get(target);
    if (!ref) throw new Error(`Unknown or stale ref ${target}. Run: uix observe`);
    return ref.locator;
  }
  if (target.startsWith("text=")) {
    return page.getByText(target.slice(5)).first();
  }
  return page.locator(target).first();
}

function describeTarget(target) {
  return target.startsWith("@") || target.startsWith("text=") ? target : `css ${target}`;
}

function since(buf, cursor) {
  const idx = buf.findIndex((e) => e.seq > cursor);
  return idx === -1 ? [] : buf.slice(idx);
}

function briefError(err) {
  const msg = String(err?.message || err);
  const lines = msg.split("\n").filter(Boolean);
  return lines.slice(0, 4).join(" | ").slice(0, 400);
}

// ---------------------------------------------------------------------------
// tool handlers — one per RPC method
// ---------------------------------------------------------------------------

const handlers = {
  "status": () => ({
    browserRunning: !!context,
    channel: browserChannel,
    activePageId,
    pages: [...pages.entries()].map(([id, p]) => ({ id, url: p.url() })),
    consoleEntries: consoleBuf.length,
    networkEntries: networkBuf.length,
  }),

  "browser.start": (p) => startBrowser(p),

  "browser.stop": () => stopBrowser(),

  "navigate": async (p) => {
    const page = await requirePage();
    const resp = await page.goto(p.url, { waitUntil: "domcontentloaded", timeout: p.timeout ?? 30000 });
    return { url: page.url(), title: await page.title(), status: resp?.status() ?? null };
  },
  "back": async () => {
    const page = await requirePage();
    await page.goBack({ waitUntil: "domcontentloaded" });
    return { url: page.url() };
  },
  "forward": async () => {
    const page = await requirePage();
    await page.goForward({ waitUntil: "domcontentloaded" });
    return { url: page.url() };
  },
  "reload": async () => {
    const page = await requirePage();
    await page.reload({ waitUntil: "domcontentloaded" });
    return { url: page.url() };
  },
  "url": async () => {
    const page = await requirePage();
    return { url: page.url(), title: await page.title() };
  },

  "observe": async (p) => {
    const page = await requirePage();
    const obs = await page.evaluate(collectObservation);
    const pid = pageIds.get(page);
    const store = new Map();
    const from = refCounter + 1;
    obs.interactive.forEach((entry, i) => {
      const ref = `@e${from + i}`;
      store.set(ref, {
        locator: page.locator(entry.path).first(),
        describe: `${entry.role} "${entry.name || "(unnamed)"}"`,
      });
    });
    refCounter += obs.interactive.length;
    refs.set(pid, store);
    if (p?.format !== false) {
      return { text: formatObservation(obs, from), counts: obs.counts, refsAssigned: obs.interactive.length };
    }
    return obs;
  },

  "click": async (p) => {
    const page = await requirePage();
    const locator = resolveTarget(page, p.target);
    const opts = { timeout: p.timeout ?? 8000 };
    if (p.button) opts.button = p.button;
    if (p.modifiers) opts.modifiers = p.modifiers;
    if (p.double) await locator.dblclick(opts);
    else await locator.click(opts);
    return { clicked: describeTarget(p.target) };
  },

  "fill": async (p) => {
    const page = await requirePage();
    const locator = resolveTarget(page, p.target);
    await locator.fill(p.value, { timeout: p.timeout ?? 8000 });
    return { filled: describeTarget(p.target), value: p.value.length > 40 ? p.value.slice(0, 40) + "…" : p.value };
  },

  "press": async (p) => {
    const page = await requirePage();
    if (p.target) {
      const locator = resolveTarget(page, p.target);
      await locator.press(p.key, { timeout: p.timeout ?? 8000 });
    } else {
      await page.keyboard.press(p.key);
    }
    return { pressed: p.key, on: p.target ? describeTarget(p.target) : "page" };
  },

  "hover": async (p) => {
    const page = await requirePage();
    await resolveTarget(page, p.target).hover({ timeout: p.timeout ?? 8000 });
    return { hovered: describeTarget(p.target) };
  },

  "select": async (p) => {
    const page = await requirePage();
    const locator = resolveTarget(page, p.target);
    await locator.selectOption(p.value, { timeout: p.timeout ?? 8000 });
    return { selected: describeTarget(p.target), value: p.value };
  },

  "scrollTo": async (p) => {
    const page = await requirePage();
    await resolveTarget(page, p.target).scrollIntoViewIfNeeded({ timeout: p.timeout ?? 8000 });
    return { scrolledTo: describeTarget(p.target) };
  },

  "wheel": async (p) => {
    const page = await requirePage();
    await page.mouse.wheel(p.deltaX ?? 0, p.deltaY ?? 0);
    return { wheeled: `deltaY=${p.deltaY ?? 0}` };
  },

  "screenshot": async (p) => {
    const page = await requirePage();
    const opts = { path: p.out, timeout: p.timeout ?? 20000 };
    if (p.fullPage) opts.fullPage = true;
    if (p.target) {
      await resolveTarget(page, p.target).screenshot(opts);
    } else {
      await page.screenshot(opts);
    }
    return { saved: p.out, fullPage: !!p.fullPage, element: !!p.target };
  },

  "console": (p) => {
    const cursor = p?.cursor ?? 0;
    let entries = since(consoleBuf, cursor);
    if (p?.errorsOnly) entries = entries.filter((e) => e.kind === "error" || e.kind === "pageerror");
    return { entries, nextCursor: consoleSeq };
  },

  "network": (p) => {
    const cursor = p?.cursor ?? 0;
    let entries = since(networkBuf, cursor);
    if (p?.failuresOnly) entries = entries.filter((e) => e.event === "failed" || e.status >= 400);
    return { entries, nextCursor: networkSeq };
  },

  "evaluate": async (p) => {
    const page = await requirePage();
    // async wrapper supports both statement bodies (`return x`, top-level
    // await) and plain expressions; the plain-expression fast path keeps
    // objects serializable on error pages too.
    return { value: await page.evaluate(`(async () => { ${p.js} })()`) };
  },

  "tabs.list": () => ({
    activePageId,
    browserRunning: !!context,
    tabs: [...pages.entries()].map(([id, p]) => ({ id, url: p.url() })),
  }),
  "tabs.new": async (p) => {
    if (!context) await startBrowser({ headless: process.env.UIX_HEADLESS === "1" });
    const page = await context.newPage();
    if (p?.url) await page.goto(p.url, { waitUntil: "domcontentloaded", timeout: 30000 }).catch(() => {});
    activePageId = pageIds.get(page);
    return { id: activePageId, url: page.url() };
  },
  "tabs.use": (p) => {
    const page = pages.get(p.id);
    if (!page) throw new Error(`No tab with id ${p.id}. Run: uix tabs`);
    activePageId = p.id;
    page.bringToFront().catch(() => {});
    return { activePageId };
  },
  "tabs.close": (p) => {
    const id = p?.id ?? activePageId;
    const page = pages.get(id);
    if (!page) throw new Error(`No tab with id ${id}`);
    return page.close().then(() => ({ closed: id }));
  },

  "wait": (p) => new Promise((resolve) => setTimeout(() => resolve({ waited: p.ms }), Math.min(p.ms, 60000))),

  "daemon.shutdown": async () => {
    await stopBrowser();
    clearRuntime();
    setTimeout(() => process.exit(0), 150);
    return { shuttingDown: true };
  },
};

// ---------------------------------------------------------------------------
// HTTP server
// ---------------------------------------------------------------------------

export function serve() {
  ensureHome();
  const server = http.createServer((req, res) => {
    const auth = req.headers["authorization"] ?? "";
    if (auth !== `Bearer ${TOKEN}`) {
      res.writeHead(401, { "content-type": "application/json" });
      res.end(JSON.stringify({ ok: false, error: "unauthorized" }));
      return;
    }
    if (req.method === "GET" && req.url === "/status") {
      res.writeHead(200, { "content-type": "application/json" });
      res.end(JSON.stringify({ ok: true, pid: process.pid }));
      return;
    }
    if (req.method === "POST" && req.url === "/rpc") {
      let body = "";
      req.on("data", (c) => (body += c));
      req.on("end", async () => {
        try {
          const { method, params = {} } = JSON.parse(body || "{}");
          const handler = handlers[method];
          if (!handler) throw new Error(`Unknown method: ${method}`);
          const result = await handler(params);
          res.writeHead(200, { "content-type": "application/json" });
          res.end(JSON.stringify({ ok: true, result }));
        } catch (err) {
          log("rpc error:", briefError(err));
          res.writeHead(200, { "content-type": "application/json" });
          res.end(JSON.stringify({ ok: false, error: briefError(err) }));
        }
      });
      return;
    }
    res.writeHead(404, { "content-type": "application/json" });
    res.end(JSON.stringify({ ok: false, error: "not found" }));
  });

  server.listen(0, "127.0.0.1", () => {
    const { port } = server.address();
    writeRuntime({ port, pid: process.pid, token: TOKEN, startedAt: new Date().toISOString() });
    log(`daemon listening on 127.0.0.1:${port}`);
    // stdout marker for foreground mode
    console.log(`uix daemon ready on 127.0.0.1:${port}`);
  });

  const cleanup = async () => {
    clearRuntime();
    process.exit(0);
  };
  process.on("SIGINT", cleanup);
  process.on("SIGTERM", cleanup);
}

// Foreground mode: `node src/daemon.js --serve`
if (process.argv.includes("--serve")) {
  serve();
}
