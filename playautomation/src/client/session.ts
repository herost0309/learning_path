// Client side: connect to a session daemon, or spawn one detached (for `open`).
// Pattern follows playwright-main/packages/playwright-core/src/tools/cli-client/session.ts:
// detached spawn, stdout sentinel line as readiness signal, one-shot sendAndClose RPC.

import { spawn, spawnSync } from 'node:child_process';
import * as fs from 'node:fs';
import * as net from 'node:net';
import * as path from 'node:path';
import { fileURLToPath } from 'node:url';
import { canConnect, sendAndClose, type ToolResultPayload } from '../protocol.js';
import {
  daemonHome, loadSession, saveSession, socketPathFor, workspaceRoot, workspaceHash,
  type SessionInfo,
} from './registry.js';

const SENTINEL = 'playautomation daemon listening on ';

export interface SpawnOptions {
  session: string;
  url?: string; // optional initial navigation (applied via RPC after spawn)
  launchArgs: string[]; // browser flags only (--headless, --viewport=..., --idle-timeout=..., --channel=...)
}

export function libDir(): string {
  return path.dirname(fileURLToPath(import.meta.url));
}

function daemonEntry(): string {
  // libDir() is lib/client; the daemon entry is lib/daemon/daemon.js.
  return path.join(libDir(), '..', 'daemon', 'daemon.js');
}

export function errFilePath(hash: string, session: string): string {
  return path.join(daemonHome(), 'daemon', hash, `${session}.err`);
}

async function spawnDaemon(opts: SpawnOptions): Promise<SessionInfo> {
  const workspaceDir = workspaceRoot();
  const hash = workspaceHash(workspaceDir);
  const entry = daemonEntry();
  if (!fs.existsSync(entry))
    throw new Error(`Daemon entry not found: ${entry}. Run \`npm run build\` first.`);

  const errFile = errFilePath(hash, opts.session);
  fs.mkdirSync(path.dirname(errFile), { recursive: true });
  const errFd = fs.openSync(errFile, 'w', 0o600);

  const child = spawn(process.execPath, [entry, opts.session, ...opts.launchArgs], {
    detached: true,
    stdio: ['ignore', 'pipe', errFd],
    windowsHide: true,
    env: { ...process.env, PLAYAUTOMATION_WS_DIR: workspaceDir, PLAYAUTOMATION_WS_HASH: hash },
  });

  const socketPath = await new Promise<string>((resolve, reject) => {
    let out = '';
    const timer = setTimeout(() => {
      let errTail = '';
      try {
        errTail = fs.readFileSync(errFile, 'utf8').split('\n').slice(-5).join('\n');
      } catch { /* ignore */ }
      reject(new Error(`Daemon did not start within 90s. Last stderr:\n${errTail}`));
    }, 90_000);
    child.stdout!.setEncoding('utf8');
    child.stdout!.on('data', (chunk: string) => {
      out += chunk;
      const line = out.split('\n').find(l => l.startsWith(SENTINEL));
      if (line) {
        clearTimeout(timer);
        resolve(line.slice(SENTINEL.length).trim());
      }
    });
    child.on('exit', code => {
      clearTimeout(timer);
      let errTail = '';
      try {
        errTail = fs.readFileSync(errFile, 'utf8').split('\n').slice(-8).join('\n');
      } catch { /* ignore */ }
      reject(new Error(`Daemon exited during startup (code ${code}). Stderr:\n${errTail}`));
    });
  });

  child.stdout!.destroy();
  child.unref();
  fs.closeSync(errFd);

  // The daemon writes its own .session file; wait briefly for it.
  const info = await waitForSessionInfo(hash, opts.session, socketPath);
  return info;
}

async function waitForSessionInfo(hash: string, session: string, socketPath: string): Promise<SessionInfo> {
  for (let i = 0; i < 100; i++) {
    const info = loadSession(hash, session);
    if (info && info.socketPath === socketPath)
      return info;
    await new Promise(r => setTimeout(r, 100));
  }
  // Session file is a cache; the live pipe is the source of truth.
  return { name: session, version: '', pid: -1, socketPath, workspaceDir: '', createdTs: Date.now(), browser: { headless: false } };
}

// A live daemon owns the deterministic socket path even if the .session file
// was deleted; probe the path itself before spawning (avoids EADDRINUSE races).
export async function connectOrReuse(session: string): Promise<SessionInfo | null> {
  const workspaceDir = workspaceRoot();
  const hash = workspaceHash(workspaceDir);
  const socketPath = socketPathFor(hash, session);
  if (!await canConnect(socketPath))
    return null;
  return loadSession(hash, session) ?? {
    name: session,
    version: '',
    pid: -1,
    socketPath,
    workspaceDir,
    createdTs: 0,
    browser: { headless: true },
  };
}

export async function ensureSession(session: string): Promise<SessionInfo> {
  const existing = await connectOrReuse(session);
  if (existing)
    return existing;
  throw new Error(`No active session "${session}". Start one with: playautomation open [url] -s ${session}`);
}

// Runs one command in the session daemon; respawns once if the session is dead
// and the command is `open` (the only command allowed to cold-start a daemon).
export async function runInSession(session: string, argv: string[]): Promise<ToolResultPayload> {
  const hash = workspaceHash(workspaceRoot());

  const attempt = async (): Promise<ToolResultPayload> => {
    const info = loadSession(hash, session);
    const socketPath = info?.socketPath ?? socketPathFor(hash, session);
    const response = await sendAndClose(socketPath, 'run', { args: argv, cwd: process.cwd(), token: info?.token });
    return response.result!;
  };

  try {
    return await attempt();
  } catch (error) {
    const message = String((error as Error)?.message ?? error);
    const dead = /ECONNREFUSED|ENOENT|EPIPE|closed before responding|Timed out/.test(message);
    if (!dead)
      throw error;
    if (argv[0] === 'open') {
      await spawnDaemon({ session, launchArgs: argv.slice(1) });
      return attempt();
    }
    throw new Error(`Session "${session}" is not running (${message}). Start it with: playautomation open -s ${session}`);
  }
}

export async function startSession(opts: SpawnOptions): Promise<SessionInfo> {
  const hash = workspaceHash(workspaceRoot());
  const existing = await connectOrReuse(opts.session);
  if (existing) {
    if (opts.url)
      await sendAndClose(existing.socketPath, 'run', { args: ['goto', opts.url], cwd: process.cwd(), token: existing.token });
    return existing;
  }
  const info = await spawnDaemon(opts);
  saveSession(hash, info);
  if (opts.url)
    await sendAndClose(info.socketPath, 'run', { args: ['goto', opts.url], cwd: process.cwd(), token: info.token });
  return info;
}

// Close (RPC when possible, then force-kill) and wait for the daemon to die.
export async function killSession(session: string): Promise<boolean> {
  const workspaceDir = workspaceRoot();
  const hash = workspaceHash(workspaceDir);
  const info = loadSession(hash, session);
  const socketPath = info?.socketPath ?? socketPathFor(hash, session);

  await sendAndClose(socketPath, 'stop', undefined, 3000).catch(() => {});
  // Give the daemon a moment to exit cleanly, then verify.
  for (let i = 0; i < 20; i++) {
    if (!await canConnect(socketPath, 500))
      break;
    await new Promise(r => setTimeout(r, 100));
  }
  if (await canConnect(socketPath, 500) && info && info.pid > 0) {
    try {
      if (process.platform === 'win32')
        spawnSync('taskkill', ['/PID', String(info.pid), '/T', '/F'], { windowsHide: true });
      else
        process.kill(info.pid, 'SIGKILL');
    } catch { /* best effort */ }
  }
  try {
    fs.rmSync(path.join(daemonHome(), 'daemon', hash, `${session}.session`), { force: true });
  } catch { /* ignore */ }
  return true;
}

// Used by tests and `list` to probe sessions.
export { canConnect, net };
