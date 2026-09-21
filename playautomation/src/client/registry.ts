// Session registry: .session JSON files keyed by hash(workspaceDir) + sessionName.
// Pattern follows playwright-main/packages/playwright-core/src/tools/cli-client/registry.ts.

import * as crypto from 'node:crypto';
import * as fs from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';

export interface SessionInfo {
  name: string;
  version: string;
  pid: number;
  socketPath: string;
  workspaceDir: string;
  createdTs: number;
  browser: { headless: boolean; channel?: string };
  token?: string; // per-daemon auth token; the .session file (0600) is its store
}

export function daemonHome(): string {
  return process.env.PLAYAUTOMATION_HOME ?? path.join(os.homedir(), '.playautomation');
}

export function workspaceRoot(startDir: string = process.cwd()): string {
  let dir = path.resolve(startDir);
  while (true) {
    if (fs.existsSync(path.join(dir, 'playautomation.yaml')) || fs.existsSync(path.join(dir, '.git')))
      return dir;
    const parent = path.dirname(dir);
    if (parent === dir)
      return path.resolve(startDir);
    dir = parent;
  }
}

export function workspaceHash(dir: string): string {
  return crypto.createHash('sha1').update(path.resolve(dir)).digest('hex').slice(0, 16);
}

export function sanitizeSessionName(name: string): string {
  const clean = name.replace(/[^a-zA-Z0-9-_]/g, '-').slice(0, 48);
  return clean || 'default';
}

export function socketPathFor(hash: string, session: string): string {
  const name = sanitizeSessionName(session);
  if (process.platform === 'win32')
    return `\\\\.\\pipe\\playautomation-${hash}-${name}`;
  return path.join(daemonHome(), 'daemon', hash, `${name}.sock`);
}

export function sessionDir(hash: string, session: string): string {
  return path.join(daemonHome(), 'daemon', hash, sanitizeSessionName(session));
}

export function sessionFilePath(hash: string, session: string): string {
  return path.join(daemonHome(), 'daemon', hash, `${sanitizeSessionName(session)}.session`);
}

export function saveSession(hash: string, info: SessionInfo): void {
  const file = sessionFilePath(hash, info.name);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, JSON.stringify(info, null, 2), { mode: 0o600 });
}

export function loadSession(hash: string, session: string): SessionInfo | null {
  try {
    const file = sessionFilePath(hash, session);
    const raw = fs.readFileSync(file, 'utf8');
    return JSON.parse(raw) as SessionInfo;
  } catch {
    return null;
  }
}

export function deleteSession(hash: string, session: string): void {
  try {
    fs.rmSync(sessionFilePath(hash, session), { force: true });
  } catch {
    // already gone
  }
}

export function listSessions(): Array<{ hash: string; info: SessionInfo }> {
  const base = path.join(daemonHome(), 'daemon');
  const out: Array<{ hash: string; info: SessionInfo }> = [];
  let hashDirs: string[] = [];
  try {
    hashDirs = fs.readdirSync(base, { withFileTypes: true }).filter(d => d.isDirectory()).map(d => d.name);
  } catch {
    return out;
  }
  for (const hash of hashDirs) {
    for (const file of fs.readdirSync(path.join(base, hash)).filter(f => f.endsWith('.session'))) {
      try {
        const info = JSON.parse(fs.readFileSync(path.join(base, hash, file), 'utf8')) as SessionInfo;
        out.push({ hash, info });
      } catch {
        // skip corrupt files
      }
    }
  }
  return out;
}
