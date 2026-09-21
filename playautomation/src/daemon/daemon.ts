// Daemon entry: a detached process owning the browser context.
// Pattern follows playwright-main/packages/playwright-core/src/tools/cli-daemon/daemon.ts:
// named pipe/unix socket server, .session registry file, stdout sentinel line,
// idle timer, context-close cleanup.

import * as crypto from 'node:crypto';
import * as fs from 'node:fs';
import * as net from 'node:net';
import * as path from 'node:path';
import { chromium } from 'playwright-core';
import { findCommand, parseCommandArgs } from '../commands.js';
import { serveConnection, type DaemonRequest, type ToolResultPayload } from '../protocol.js';
import { saveSession, sessionDir, socketPathFor } from '../client/registry.js';
import { snapshotScriptText } from '../backend/tab.js';
import { Backend } from './backend.js';

const SENTINEL = 'playautomation daemon listening on ';

const VERSION = '0.1.0';

interface DaemonOptions {
  session: string;
  headless: boolean;
  viewport?: { width: number; height: number };
  idleTimeoutSec: number; // 0 = never
  channel?: string;
}

function parseDaemonArgs(args: string[]): DaemonOptions {
  // Client spawns: node daemon.js <sessionName> [--flags...]
  const [session = 'default', ...rest] = args;
  const options: DaemonOptions = { session, headless: false, idleTimeoutSec: 0 };
  for (const arg of rest) {
    if (arg === '--headless')
      options.headless = true;
    else if (arg.startsWith('--viewport=')) {
      const match = /^(\d+)x(\d+)$/.exec(arg.slice('--viewport='.length));
      if (match)
        options.viewport = { width: parseInt(match[1], 10), height: parseInt(match[2], 10) };
    } else if (arg.startsWith('--idle-timeout=')) {
      const value = parseInt(arg.slice('--idle-timeout='.length), 10);
      if (!Number.isNaN(value))
        options.idleTimeoutSec = value;
    } else if (arg.startsWith('--channel='))
      options.channel = arg.slice('--channel='.length);
  }
  return options;
}

async function launchBrowser(options: DaemonOptions) {
  const launch = (channel?: string) => chromium.launch({ headless: options.headless, ...(channel ? { channel } : {}) });
  if (options.channel) {
    // Explicit channel: honor it directly.
    return { browser: await launch(options.channel === 'chromium' ? undefined : options.channel), channel: options.channel };
  }
  // Default: bundled chromium, then system Chrome, then Edge (always present on Windows).
  const attempts: Array<string | undefined> = [undefined, 'chrome', 'msedge'];
  const errors: string[] = [];
  for (const channel of attempts) {
    try {
      return { browser: await launch(channel), channel: channel ?? 'chromium' };
    } catch (error) {
      errors.push(`  ${channel ?? 'bundled chromium'}: ${(error as Error).message.split('\n')[0]}`);
    }
  }
  throw new Error(`Could not launch a Chromium browser.\n${errors.join('\n')}\nInstall one of:\n  npx playwright install chromium\n  (or install Google Chrome / Microsoft Edge)`);
}

async function main(): Promise<void> {
  const argv = process.argv.slice(2);
  const options = parseDaemonArgs(argv);
  const wsHash = process.env.PLAYAUTOMATION_WS_HASH ?? 'nohash';
  const workspaceDir = process.env.PLAYAUTOMATION_WS_DIR ?? process.cwd();
  const socketPath = socketPathFor(wsHash, options.session);

  // Stale unix socket file from a dead daemon would block listen.
  if (process.platform !== 'win32') {
    try {
      fs.rmSync(socketPath, { force: true });
    } catch { /* ignore */ }
  }

  const { browser, channel } = await launchBrowser(options);
  const context = await browser.newContext({
    viewport: options.viewport,
  });
  await context.addInitScript(snapshotScriptText());
  const backend = await Backend.create(context, sessionDir(wsHash, options.session));
  const sessionToken = crypto.randomBytes(24).toString('hex');

  let exiting = false;
  const cleanup = async () => {
    if (exiting)
      return;
    exiting = true;
    try {
      await context.close();
      await browser.close();
    } catch { /* already closed */ }
    // Grace period so pending socket responses (e.g. the `stop` ack) flush.
    setTimeout(() => process.exit(0), 150).unref();
    setTimeout(() => process.exit(0), 1000).unref();
  };

  // Idle shutdown (default: never for headed, 30 min headless — client passes explicit value).
  let idleTimer: NodeJS.Timeout | null = null;
  const resetIdle = () => {
    if (idleTimer)
      clearTimeout(idleTimer);
    if (options.idleTimeoutSec > 0)
      idleTimer = setTimeout(() => {
        console.error(`Idle for ${options.idleTimeoutSec}s — shutting down.`);
        void cleanup();
      }, options.idleTimeoutSec * 1000);
    if (idleTimer)
      idleTimer.unref();
  };
  resetIdle();

  const server = net.createServer(socket => {
    serveConnection(socket, async (request: DaemonRequest): Promise<ToolResultPayload | null> => {
      resetIdle();
      if (request.method === 'ping')
        return { isError: false, text: 'pong', meta: { version: VERSION } };
      if (request.method === 'stop') {
        void cleanup();
        return { isError: false, text: 'closed' };
      }
      // method === 'run' — the socket path is predictable, so require the
      // per-daemon token (delivered to clients via the 0600 .session file) to
      // keep other same-user processes from driving the browser.
      if (String(request.params?.token ?? '') !== sessionToken)
        return { isError: true, text: 'Unauthorized: session token missing or invalid (stale .session file? reopen the session).' };
      const args = (request.params?.args as string[]) ?? [];
      const commandName = args[0];
      const def = findCommand(commandName ?? '');
      if (!def || def.routed !== 'daemon')
        return { isError: true, text: `Unknown daemon command: ${commandName}` };
      const parsed = parseCommandArgs(def, args.slice(1));
      if (parsed.command === 'close') {
        void cleanup();
        return { isError: false, text: 'Session closed.' };
      }
      return await backend.dispatch(parsed);
    });
  });

  server.on('error', error => {
    console.error(`Daemon socket error: ${(error as Error).message}`);
    process.exit(1);
  });

  await new Promise<void>(resolve => server.listen(socketPath, resolve));
  // Owner-only socket: any local user could otherwise connect and drive the
  // browser when the process umask is permissive (e.g. 0).
  if (process.platform !== 'win32') {
    try {
      fs.chmodSync(socketPath, 0o600);
    } catch { /* best effort */ }
  }
  saveSession(wsHash, {
    name: options.session,
    version: VERSION,
    pid: process.pid,
    socketPath,
    workspaceDir,
    createdTs: Date.now(),
    browser: { headless: options.headless, channel },
    token: sessionToken,
  });

  context.on('close', () => void cleanup());

  // Sentinel: the client waits for this line before sending commands.
  // Initial navigation is driven client-side (`open <url>` sends a goto RPC).
  console.log(`${SENTINEL}${socketPath}`);
  void backend;
}

void main().catch(error => {
  console.error(error);
  process.exit(1);
});
