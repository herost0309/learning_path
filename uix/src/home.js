// Shared paths for uix (CLI + daemon). Home dir: ~/.uix
import os from "node:os";
import path from "node:path";
import fs from "node:fs";

export const UIX_HOME = path.join(os.homedir(), ".uix");
export const PROFILE_DIR = path.join(UIX_HOME, "profile");
export const RUNTIME_FILE = path.join(UIX_HOME, "runtime.json");
export const LOG_FILE = path.join(UIX_HOME, "daemon.log");

export function ensureHome() {
  fs.mkdirSync(UIX_HOME, { recursive: true });
}

export function log(...parts) {
  try {
    ensureHome();
    fs.appendFileSync(LOG_FILE, `[${new Date().toISOString()}] ${parts.join(" ")}\n`);
  } catch {
    // logging must never crash the daemon
  }
}

export function readRuntime() {
  try {
    return JSON.parse(fs.readFileSync(RUNTIME_FILE, "utf8"));
  } catch {
    return null;
  }
}

export function writeRuntime(info) {
  ensureHome();
  fs.writeFileSync(RUNTIME_FILE, JSON.stringify(info, null, 2));
}

export function clearRuntime() {
  try {
    fs.unlinkSync(RUNTIME_FILE);
  } catch {
    // already gone
  }
}
