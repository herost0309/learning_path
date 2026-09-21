// Minimal static file server for the bundled test pages.
// Used by e2e tests and `playautomation serve` for local experimentation.

import * as http from 'node:http';
import * as fs from 'node:fs';
import * as path from 'node:path';

const kContentTypes: Record<string, string> = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
};

export interface TestServer {
  server: http.Server;
  port: number;
  url: (pathname: string) => string;
  close: () => Promise<void>;
}

export function startTestServer(rootInput: string, port = 0): Promise<TestServer> {
  const root = path.resolve(rootInput);
  const server = http.createServer((request, response) => {
    try {
      const url = new URL(request.url ?? '/', 'http://localhost');
      let file = path.join(root, url.pathname.replace(/^\/+/, '') || 'index.html');
      // Containment via path.relative — a plain startsWith(root) would also
      // pass for sibling dirs sharing the prefix (root-foo vs root).
      const rel = path.relative(root, file);
      if (rel.startsWith('..') || path.isAbsolute(rel))
        throw new Error('path traversal');
      if (fs.statSync(file).isDirectory())
        file = path.join(file, 'index.html');
      const data = fs.readFileSync(file);
      response.writeHead(200, { 'content-type': kContentTypes[path.extname(file)] ?? 'application/octet-stream' });
      response.end(data);
    } catch {
      response.writeHead(404, { 'content-type': 'text/plain' });
      response.end('not found');
    }
  });
  return new Promise(resolve => {
    server.listen(port, '127.0.0.1', () => {
      const actual = (server.address() as { port: number }).port;
      resolve({
        server,
        port: actual,
        url: (pathname: string) => `http://127.0.0.1:${actual}${pathname}`,
        close: () => new Promise(done => server.close(() => done())),
      });
    });
  });
}
