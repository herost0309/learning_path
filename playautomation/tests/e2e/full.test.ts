// E2E: the full agent loop through the real CLI binary:
// open → snapshot → fill/click by ref → assert → save → run (replay) → report.
import { execFile } from 'node:child_process';
import * as fs from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';
import { promisify } from 'node:util';
import { fileURLToPath } from 'node:url';
import { afterAll, beforeAll, expect, it } from 'vitest';
import { startTestServer } from '../../lib/server/testServer.js';
import { runFlows } from '../../lib/runner/run.js';
import { killSession } from '../../lib/client/session.js';

const exec = promisify(execFile);

const packageRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const cli = path.join(packageRoot, 'lib', 'cli.js');
const session = 'e2e';
const home = fs.mkdtempSync(path.join(os.tmpdir(), 'pa-e2e-'));
const flowsDir = path.join(home, 'flows');
const resultsDir = path.join(home, 'results');

let server: Awaited<ReturnType<typeof startTestServer>>;

async function pa(...args: string[]): Promise<string> {
  const { stdout } = await exec(process.execPath, [cli, ...args, '-s', session], {
    cwd: packageRoot,
    env: { ...process.env, PLAYAUTOMATION_HOME: home },
  });
  return stdout;
}

// Extracts a ref for a snapshot line, e.g. 'textbox "Email" [ref=e7]' -> e7
function refFor(snapshot: string, pattern: RegExp): string {
  const line = snapshot.split('\n').find(l => pattern.test(l));
  if (!line)
    throw new Error(`No snapshot line matching ${pattern}:\n${snapshot}`);
  const match = line.match(/\[ref=(e\d+)\]/);
  if (!match)
    throw new Error(`Line has no ref: ${line}`);
  return match[1];
}

beforeAll(async () => {
  server = await startTestServer(path.join(packageRoot, 'test-pages'), 0);
});

afterAll(async () => {
  await killSession(session);
  await server.close();
});

it('runs the full explore → save → replay cycle', async () => {
  // --- explore ---
  const initial = await pa('open', `${server.url('/login.html')}`, '--headless');
  expect(initial).toContain('Sign in');
  expect(initial).toMatch(/\[ref=e\d+\]/);

  const snap1 = await pa('snapshot');
  const emailRef = refFor(snap1, /textbox "Email"/);
  const filled = await pa('fill', emailRef, 'demo@test.shop');
  expect(filled).toContain('selector: testid="login-email"');
  expect(filled).toContain('page.getByTestId("login-email")');

  const snap2 = await pa('snapshot');
  const passwordRef = refFor(snap2, /textbox "Password"/);
  const buttonRef = refFor(snap2, /button "Sign in"/);
  await pa('fill', passwordRef, 'demo123');
  await pa('click', buttonRef);
  await pa('assert', 'text', 'Dashboard');
  await pa('assert', 'url', 'welcome');

  // --- save ---
  const specFile = path.join(flowsDir, 'login.yaml');
  fs.mkdirSync(flowsDir, { recursive: true });
  fs.writeFileSync(specFile, [
    'name: login',
    `baseUrl: ${server.url('/')}`,
    'inputs:',
    '  email: demo@test.shop',
    '  password: demo123',
    'steps:',
    '  - sign in',
    'assert:',
    '  - dashboard visible',
    '',
  ].join('\n'));
  const saved = await pa('save', '--name', 'login', '--spec', specFile, '--out', flowsDir);
  expect(saved).toContain('Saved');
  const flow = JSON.parse(fs.readFileSync(path.join(flowsDir, 'login.flow.json'), 'utf8'));
  expect(flow.inputs).toEqual({ email: 'demo@test.shop', password: 'demo123' });
  const fillSteps = flow.steps.filter((s: any) => s.kind === 'fill');
  expect(fillSteps.map((s: any) => s.text)).toContain('{{email}}');

  // generated TS view is present and uses getBy* APIs
  const ts = fs.readFileSync(path.join(flowsDir, 'login.ts'), 'utf8');
  expect(ts).toContain('getByTestId("login-email")');
  expect(ts).toContain('resolveTemplate("{{email}}"');

  // --- cases ---
  fs.writeFileSync(path.join(flowsDir, 'login.cases.yaml'), [
    'cases:',
    '  - name: happy-path',
    '  - name: wrong-password',
    '    inputs:',
    `      password: nope`,
    '    expectFail: true',
    '',
  ].join('\n'));

  // --- replay (in-process runner, same code path as `playautomation run`) ---
  const summary = await runFlows([path.join(flowsDir, 'login.flow.json')], {
    reportBase: resultsDir,
    timeoutMs: 10_000,
  });
  expect(summary.totalCases).toBe(2);
  expect(summary.passed).toBe(2);
  expect(summary.failed).toBe(0);
  const report = path.join(summary.resultsDir, 'report.html');
  expect(fs.existsSync(report)).toBe(true);
  expect(fs.statSync(report).size).toBeGreaterThan(10_000);
}, 180_000);

it('reports stale refs with guidance', async () => {
  const error = await pa('click', 'e99999').catch((e: Error) => e.message);
  expect(error).toMatch(/stale|not found/i);
}, 30_000);
