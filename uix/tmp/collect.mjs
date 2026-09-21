// Driver: visit all 20 problems, collect UI checks + top solution evidence.
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";

const ROOT = "C:/software/cc/team/browser-skill";
const EVIDENCE = path.join(ROOT, "evidence");
const UIX = path.join(ROOT, "uix");
const bin = path.join(UIX, "bin/uix.js");
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const uiChecksJs = fs.readFileSync(path.join(UIX, "tmp/ui-checks.js"), "utf8");
const extractJs = fs.readFileSync(path.join(UIX, "tmp/extract-solutions.js"), "utf8");

function uix(...args) {
  const r = spawnSync(process.execPath, [bin, ...args], {
    encoding: "utf8",
    timeout: 120000,
    maxBuffer: 64 * 1024 * 1024,
  });
  if (r.status !== 0) throw new Error(`uix ${args.join(" ")} -> ${r.stdout} ${r.stderr}`);
  return r.stdout.trim();
}

// CLI --json double-encodes object results; unwrap both layers.
function parseJson(txt) {
  let v = JSON.parse(txt);
  if (typeof v === "string") v = JSON.parse(v);
  return v;
}

const sanitize = (s) => s.replace(/[\\/:*?"<>|]/g, "_").replace(/\s+/g, " ").trim().slice(0, 60);

const { top, bottom } = JSON.parse(fs.readFileSync(path.join(UIX, "tmp/top-bottom.json"), "utf8"));
const all = [...top.map((q) => ({ ...q, group: "top" })), ...bottom.map((q) => ({ ...q, group: "bottom" }))];

const results = [];
fs.mkdirSync(EVIDENCE, { recursive: true });

for (const q of all) {
  const folder = path.join(EVIDENCE, sanitize(`${q.frontendQuestionId}-${q.titleCn || q.title}`));
  fs.mkdirSync(folder, { recursive: true });
  const meta = { problem: { ...q }, ui: null, topSolution: null, errors: [] };
  console.log(`\n=== [${q.group}] ${q.frontendQuestionId} ${q.titleCn || q.title} (acRate ${(q.acRate * 100).toFixed(1)}%) ===`);

  try {
    // ---- problem page: UI consistency ----
    const preConsole = parseJson(uix("console", "--json")).nextCursor;
    const preNetwork = parseJson(uix("network", "--json")).nextCursor;
    uix("navigate", `https://leetcode.cn/problems/${q.titleSlug}/`);
    await sleep(2800);
    meta.ui = parseJson(uix("evaluate", uiChecksJs, "--json"));
    uix("screenshot", "--out", path.join(folder, "01-problem.png"));
    const postConsole = parseJson(uix("console", "--json", "--cursor", String(preConsole)));
    const postNetwork = parseJson(uix("network", "--json", "--cursor", String(preNetwork)));
    meta.ui.consoleErrors = postConsole.entries.filter((e) => e.kind === "error" || e.kind === "pageerror").length;
    meta.ui.errorSamples = postConsole.entries.filter((e) => e.kind === "error" || e.kind === "pageerror").slice(0, 3).map((e) => e.text.slice(0, 150));
    meta.ui.networkFailures = postNetwork.entries.filter((e) => e.event === "failed" || e.status >= 400).length;
    meta.ui.failureSamples = postNetwork.entries.filter((e) => e.event === "failed" || e.status >= 400).slice(0, 3).map((e) => `${e.method} ${e.status} ${e.url.slice(0, 100)}`);
    console.log(`  ui: difficulty=${meta.ui.difficulty} editor=${meta.ui.hasEditor} descLen=${meta.ui.descLen} consoleErr=${meta.ui.consoleErrors} netFail=${meta.ui.networkFailures}`);

    // ---- solutions page: scroll-load, extract, pick max likes ----
    uix("navigate", `https://leetcode.cn/problems/${q.titleSlug}/solutions/`);
    await sleep(2800);
    for (let i = 0; i < 3; i++) {
      uix("evaluate", "window.scrollTo(0, document.body.scrollHeight)");
      await sleep(900);
    }
    const sols = parseJson(uix("evaluate", extractJs, "--json"));
    const ranked = sols.solutions.filter((s) => s.likes !== null).sort((a, b) => b.likes - a.likes);
    if (!ranked.length) throw new Error("no solution cards extracted");
    const best = ranked[0];
    meta.topSolution = { ...best, rankedFrom: sols.count, runnerUps: ranked.slice(1, 3).map((s) => ({ title: s.title, likes: s.likes, author: s.author })) };
    console.log(`  solutions: ${sols.count} cards, top = "${best.title}" by ${best.author} likes=${best.likes} (official=${best.official})`);

    // ---- solution detail page: screenshot ----
    uix("navigate", `https://leetcode.cn${best.href}`);
    await sleep(3000);
    uix("evaluate", "window.scrollTo(0, 0)");
    await sleep(400);
    uix("screenshot", "--out", path.join(folder, "02-top-solution.png"));
  } catch (err) {
    meta.errors.push(String(err.message || err));
    console.log(`  ERROR: ${meta.errors[0]}`);
  }

  fs.writeFileSync(path.join(folder, "meta.json"), JSON.stringify(meta, null, 2));
  results.push({ folder: path.basename(folder), ...meta });
  await sleep(600);
}

fs.writeFileSync(path.join(UIX, "tmp/collected.json"), JSON.stringify(results, null, 2));
const ok = results.filter((r) => !r.errors.length).length;
console.log(`\nDone: ${ok}/${results.length} problems collected cleanly.`);
