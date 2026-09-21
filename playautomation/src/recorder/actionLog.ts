// Per-session append-only action log (JSONL). Every daemon-routed action is
// recorded here; `save` compiles it into a flow.json.

import * as fs from 'node:fs';
import * as path from 'node:path';

export interface ActionEntry {
  seq: number;
  ts: number;
  action: string; // goto|click|fill|select|check|uncheck|press|hover|wait|screenshot|assert
  ref?: string;
  selector?: string;
  css?: string;
  params?: Record<string, unknown>;
  url: string;
}

export class ActionLog {
  readonly file: string;
  private seq = 0;
  constructor(dir: string) {
    fs.mkdirSync(dir, { recursive: true });
    this.file = path.join(dir, 'actions.jsonl');
    // The log records fill texts (including passwords) — owner-only from the start.
    touchOwnerOnly(this.file);
    // Continue the sequence after a daemon restart on the same session.
    try {
      const lines = fs.readFileSync(this.file, 'utf8').split('\n').filter(Boolean);
      const last = lines[lines.length - 1];
      if (last)
        this.seq = (JSON.parse(last) as ActionEntry).seq ?? 0;
    } catch {
      this.seq = 0;
    }
  }

  record(entry: Omit<ActionEntry, 'seq' | 'ts'>): ActionEntry {
    const full: ActionEntry = { seq: ++this.seq, ts: Date.now(), ...entry };
    fs.appendFileSync(this.file, JSON.stringify(full) + '\n', { mode: 0o600 });
    return full;
  }

  read(): ActionEntry[] {
    try {
      return fs.readFileSync(this.file, 'utf8').split('\n').filter(Boolean).map(l => JSON.parse(l) as ActionEntry);
    } catch {
      return [];
    }
  }

  screenshotDir(): string {
    return path.join(path.dirname(this.file), 'screenshots');
  }
}

// Create the file (empty) with owner-only permissions if it does not exist yet,
// so the first append never creates a world-readable log. No-op on Windows
// beyond creation (ACLs already scope the user profile to the user).
export function touchOwnerOnly(file: string): void {
  try {
    const fd = fs.openSync(file, 'a', 0o600);
    fs.closeSync(fd);
  } catch { /* best effort */ }
}
