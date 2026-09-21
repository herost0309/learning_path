// Deterministic replay engine: runs flow.json files (headless by default) with
// per-step screenshots, assertion polling, and the case matrix.
// Owns its browser — independent of the explore daemon.

import * as fs from 'node:fs';
import * as path from 'node:path';
import { chromium, type Browser, type Page } from 'playwright-core';
import type { Flow, FlowStep } from '../flow.js';
import { loadFlow, resolveInputs, resolveTemplate } from '../flow.js';
import { locate } from './selectors.js';
import { runAssertion } from './assertions.js';
import { loadCasesForFlow, type CaseSpec } from './cases.js';
import { writeHtmlReport } from '../report/htmlReport.js';

export interface StepResult {
  stepId: string;
  kind: string;
  label: string;
  status: 'passed' | 'failed';
  durationMs: number;
  error?: string;
  screenshot?: string;
}

export interface CaseResult {
  flowName: string;
  caseName: string;
  expectFail: boolean;
  status: 'passed' | 'failed';
  steps: StepResult[];
  durationMs: number;
}

export interface FlowRunResult {
  flowFile: string;
  flowName: string;
  casesSource: string;
  cases: CaseResult[];
}

export interface RunSummary {
  runId: string;
  startedAt: string;
  resultsDir: string;
  flows: FlowRunResult[];
  totalCases: number;
  passed: number;
  failed: number;
}

export interface RunOptions {
  casesFlag?: string; // explicit --cases value ("none" or path)
  headed?: boolean;
  reportBase?: string;
  timeoutMs?: number;
}

async function launchBrowser(headless: boolean): Promise<Browser> {
  const attempts: Array<string | undefined> = [undefined, 'chrome', 'msedge'];
  const errors: string[] = [];
  for (const channel of attempts) {
    try {
      return await chromium.launch({ headless, ...(channel ? { channel } : {}) });
    } catch (error) {
      errors.push(`  ${channel ?? 'bundled chromium'}: ${(error as Error).message.split('\n')[0]}`);
    }
  }
  throw new Error(`Could not launch a Chromium browser.\n${errors.join('\n')}\nInstall one of:\n  npx playwright install chromium\n  (or install Google Chrome / Microsoft Edge)`);
}

export async function runFlows(flowFiles: string[], options: RunOptions): Promise<RunSummary> {
  const runId = `run-${new Date().toISOString().replace(/[:T]/g, '-').slice(0, 19)}`;
  const resultsDir = path.resolve(options.reportBase ?? 'results', runId);
  fs.mkdirSync(resultsDir, { recursive: true });
  const timeoutMs = options.timeoutMs ?? 10_000;

  const browser = await launchBrowser(!options.headed);
  const flows: FlowRunResult[] = [];
  try {
    for (const flowFile of flowFiles) {
      const flow = loadFlow(flowFile);
      const { cases, source } = loadCasesForFlow(flowFile, flow, options.casesFlag);
      const flowResult: FlowRunResult = { flowFile, flowName: flow.name, casesSource: source, cases: [] };
      for (const [index, testCase] of cases.entries()) {
        const result = await runCase(browser, flow, testCase, index, timeoutMs, resultsDir);
        flowResult.cases.push(result);
      }
      flows.push(flowResult);
    }
  } finally {
    await browser.close().catch(() => {});
  }

  const allCases = flows.flatMap(f => f.cases);
  const summary: RunSummary = {
    runId,
    startedAt: new Date().toISOString(),
    resultsDir,
    flows,
    totalCases: allCases.length,
    passed: allCases.filter(c => c.status === 'passed').length,
    failed: allCases.filter(c => c.status === 'failed').length,
  };
  fs.writeFileSync(path.join(resultsDir, 'results.json'), JSON.stringify(summary, null, 2));
  writeHtmlReport(resultsDir, summary);
  return summary;
}

async function runCase(browser: Browser, flow: Flow, testCase: CaseSpec, index: number, timeoutMs: number, resultsDir: string): Promise<CaseResult> {
  const inputs = resolveInputs(flow, testCase.inputs);
  const caseDirName = `case-${index + 1}-${testCase.name.replace(/[^\w-]/g, '_')}`;
  const caseDir = path.join(resultsDir, caseDirName);
  fs.mkdirSync(caseDir, { recursive: true });

  const startedAt = Date.now();
  const context = await browser.newContext();
  const page = await context.newPage();
  const steps: StepResult[] = [];
  let failed = false;

  for (const step of flow.steps) {
    const stepStarted = Date.now();
    const result: StepResult = { stepId: step.id, kind: step.kind, label: stepLabel(step), status: 'passed', durationMs: 0 };
    try {
      if (failed)
        throw new Error('skipped (previous step failed)');
      await executeStep(page, step, flow, inputs, timeoutMs, caseDir, result);
    } catch (error) {
      result.status = 'failed';
      result.error = (error as Error).message?.split('\n')[0] ?? String(error);
      failed = true;
    }
    result.durationMs = Date.now() - stepStarted;
    // Screenshot on success AND failure for the report timeline.
    if (step.kind !== 'screenshot') {
      try {
        const shot = path.join(caseDir, `${step.id}.png`);
        await page.screenshot({ path: shot });
        result.screenshot = path.join(caseDirName, `${step.id}.png`);
      } catch { /* page may be closed */ }
    }
    steps.push(result);
  }

  await context.close().catch(() => {});

  // A case that was expected to fail passes when it fails.
  const caseFailed = steps.some(s => s.status === 'failed');
  const status: 'passed' | 'failed' = testCase.expectFail ? (caseFailed ? 'passed' : 'failed') : (caseFailed ? 'failed' : 'passed');
  if (testCase.expectFail && !caseFailed)
    steps.push({ stepId: '-', kind: 'meta', label: 'expected failure', status: 'failed', durationMs: 0, error: 'Case was expected to fail but passed' });
  return { flowName: flow.name, caseName: testCase.name, expectFail: !!testCase.expectFail, status, steps, durationMs: Date.now() - startedAt };
}

function absoluteUrl(flow: Flow, url: string): string {
  if (/^[a-zA-Z][\w+.-]*:/.test(url))
    return url;
  if (!flow.baseUrl)
    return url;
  return new URL(url, flow.baseUrl).toString();
}

export async function executeStep(page: Page, step: FlowStep, flow: Flow, inputs: Record<string, string>, timeoutMs: number, caseDir: string, result: StepResult): Promise<void> {
  switch (step.kind) {
    case 'goto':
      await page.goto(absoluteUrl(flow, step.url!), { timeout: 30_000, waitUntil: 'domcontentloaded' });
      return;
    case 'click':
      await locate(page, step.selector!).click({ timeout: timeoutMs });
      return;
    case 'hover':
      await locate(page, step.selector!).hover({ timeout: timeoutMs });
      return;
    case 'check':
      await locate(page, step.selector!).check({ timeout: timeoutMs });
      return;
    case 'uncheck':
      await locate(page, step.selector!).uncheck({ timeout: timeoutMs });
      return;
    case 'fill': {
      const value = resolveTemplate(step.text, inputs);
      await locate(page, step.selector!).fill(value, { timeout: timeoutMs });
      return;
    }
    case 'select':
      await locate(page, step.selector!).selectOption(step.values!, { timeout: timeoutMs });
      return;
    case 'press':
      await page.keyboard.press(step.key!);
      return;
    case 'wait': {
      const wait = step.wait!;
      const waitTimeout = wait.timeoutMs ?? timeoutMs;
      if (wait.kind === 'ms')
        await page.waitForTimeout(parseInt(wait.value, 10) || 0);
      else if (wait.kind === 'url')
        await page.waitForURL(wait.value, { timeout: waitTimeout });
      else if (wait.kind === 'text')
        await page.getByText(wait.value).first().waitFor({ timeout: waitTimeout });
      else
        await locate(page, wait.value).first().waitFor({ timeout: waitTimeout });
      return;
    }
    case 'screenshot': {
      const shot = path.join(caseDir, `${step.id}.png`);
      await page.screenshot({ path: shot, fullPage: true });
      result.screenshot = path.relative(path.dirname(caseDir), shot);
      return;
    }
    case 'assert':
      await runAssertion(page, step.assert!, timeoutMs);
      return;
    default:
      throw new Error(`Unknown step kind: ${(step as any).kind}`);
  }
}

function stepLabel(step: FlowStep): string {
  switch (step.kind) {
    case 'goto':
      return step.url ?? '';
    case 'fill':
      return `${step.selector} ← ${step.text}`;
    case 'select':
      return `${step.selector} ← ${JSON.stringify(step.values)}`;
    case 'press':
      return step.key ?? '';
    case 'wait':
      return step.wait ? `${step.wait.kind}: ${step.wait.value}` : '';
    case 'assert':
      return step.assert ? `${step.assert.kind}${step.assert.target ? ` ${step.assert.target}` : ''}${step.assert.expected ? ` "${step.assert.expected}"` : ''}` : '';
    default:
      return step.selector ?? step.kind;
  }
}
