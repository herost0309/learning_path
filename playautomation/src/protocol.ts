// Newline-framed JSON RPC over a named pipe (Windows) or unix socket.
// Pattern follows playwright-main/packages/playwright-core/src/tools/cli-daemon/daemon.ts
// (single request per connection: sendAndClose).

import * as net from 'node:net';

export interface DaemonRequest {
  id: number;
  method: 'run' | 'stop' | 'ping';
  params?: Record<string, unknown>;
}

export interface DaemonResponse {
  id: number;
  result?: ToolResultPayload;
  error?: string;
}

export interface ToolResultPayload {
  isError: boolean;
  text: string;
  meta?: Record<string, unknown>;
}

let nextMessageId = 1;

export function sendAndClose(socketPath: string, method: DaemonRequest['method'], params?: Record<string, unknown>, timeoutMs = 120_000): Promise<DaemonResponse> {
  const id = nextMessageId++;
  return new Promise((resolve, reject) => {
    const socket = net.createConnection(socketPath);
    let buffer = '';
    const finish = (fn: () => void) => {
      clearTimeout(timer);
      socket.destroy();
      fn();
    };
    const timer = setTimeout(() => finish(() => reject(new Error(`Timed out after ${timeoutMs}ms waiting for the playautomation daemon`))), timeoutMs);
    socket.on('connect', () => {
      const request: DaemonRequest = { id, method, params };
      socket.write(JSON.stringify(request) + '\n');
    });
    socket.on('data', data => {
      buffer += data.toString('utf8');
      const idx = buffer.indexOf('\n');
      if (idx < 0)
        return;
      const line = buffer.slice(0, idx);
      finish(() => {
        try {
          const parsed = JSON.parse(line) as DaemonResponse;
          if (parsed.error !== undefined && parsed.result === undefined)
            reject(new Error(String(parsed.error)));
          else
            resolve(parsed);
        } catch {
          reject(new Error(`Malformed daemon response: ${line.slice(0, 200)}`));
        }
      });
    });
    socket.on('error', err => finish(() => reject(err)));
    socket.on('close', () => finish(() => reject(new Error('Daemon connection closed before responding'))));
  });
}

// Daemon side: reads one request line, invokes `handle`, writes one response line, ends.
export function serveConnection(socket: net.Socket, handle: (request: DaemonRequest) => Promise<ToolResultPayload | null>): void {
  let buffer = '';
  socket.setEncoding('utf8');
  socket.on('data', async data => {
    buffer += data;
    const idx = buffer.indexOf('\n');
    if (idx < 0)
      return;
    const line = buffer.slice(0, idx);
    buffer = buffer.slice(idx + 1);
    let request: DaemonRequest;
    try {
      request = JSON.parse(line) as DaemonRequest;
    } catch {
      socket.end(JSON.stringify({ id: -1, error: 'Malformed request' }) + '\n');
      return;
    }
    try {
      const result = await handle(request);
      const response: DaemonResponse = result === null
        ? { id: request.id, result: { isError: false, text: '' } }
        : { id: request.id, result };
      socket.end(JSON.stringify(response) + '\n');
    } catch (error) {
      const response: DaemonResponse = { id: request.id, error: String((error as Error)?.message ?? error) };
      socket.end(JSON.stringify(response) + '\n');
    }
  });
  socket.on('error', () => socket.destroy());
}

export function canConnect(socketPath: string, timeoutMs = 1500): Promise<boolean> {
  return sendAndClose(socketPath, 'ping', undefined, timeoutMs)
    .then(() => true)
    .catch(() => false);
}
