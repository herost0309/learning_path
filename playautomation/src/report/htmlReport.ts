// Self-contained HTML report: single file, screenshots inlined as base64 data
// URLs so the report can be moved/shared freely. No JS framework.

import * as fs from 'node:fs';
import * as path from 'node:path';
import type { CaseResult, RunSummary, StepResult } from '../runner/run.js';

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function screenshotTag(resultsDir: string, screenshot: string | undefined): string {
  if (!screenshot)
    return '';
  const file = path.join(resultsDir, screenshot);
  try {
    const base64 = fs.readFileSync(file).toString('base64');
    return `<details><summary>screenshot</summary><img src="data:image/png;base64,${base64}" alt="step screenshot"></details>`;
  } catch {
    return `<div class="muted">screenshot missing: ${escapeHtml(screenshot)}</div>`;
  }
}

function stepHtml(resultsDir: string, step: StepResult): string {
  const badge = step.status === 'passed' ? '<span class="badge pass">PASS</span>' : '<span class="badge fail">FAIL</span>';
  const error = step.error ? `<pre class="error">${escapeHtml(step.error)}</pre>` : '';
  return `<li class="step">${badge}<span class="mono">${escapeHtml(step.stepId)}</span> <span class="kind">${escapeHtml(step.kind)}</span> ${escapeHtml(step.label)} <span class="muted">${step.durationMs}ms</span>${error}${screenshotTag(resultsDir, step.screenshot)}</li>`;
}

function caseHtml(resultsDir: string, testCase: CaseResult): string {
  const badge = testCase.status === 'passed' ? '<span class="badge pass">PASS</span>' : '<span class="badge fail">FAIL</span>';
  const expectFailNote = testCase.expectFail ? ' <span class="muted">(expected failure)</span>' : '';
  return `
    <section class="case ${testCase.status}">
      <h3>${badge} case: ${escapeHtml(testCase.caseName)}${expectFailNote} <span class="muted">${testCase.durationMs}ms</span></h3>
      <ol class="steps">${testCase.steps.map(s => stepHtml(resultsDir, s)).join('\n')}</ol>
    </section>`;
}

export function renderHtmlReport(summary: RunSummary): string {
  const flows = summary.flows.map(flow => `
    <section class="flow">
      <h2>flow: ${escapeHtml(flow.flowName)} <span class="muted">(${escapeHtml(flow.flowFile)}) · cases: ${flow.casesSource}</span></h2>
      ${flow.cases.map(c => caseHtml(summary.resultsDir, c)).join('\n')}
    </section>`).join('\n');

  return `<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<title>playautomation report — ${escapeHtml(summary.runId)}</title>
<style>
  body { font-family: system-ui, sans-serif; margin: 24px auto; max-width: 960px; color: #1a1a1a; }
  h1 { font-size: 22px; } h2 { font-size: 18px; margin-top: 32px; } h3 { font-size: 15px; }
  .summary span { margin-right: 16px; }
  .badge { display: inline-block; padding: 1px 8px; border-radius: 10px; font-size: 12px; font-weight: 600; color: #fff; }
  .badge.pass { background: #2e7d32; } .badge.fail { background: #c62828; }
  .case { border: 1px solid #e0e0e0; border-radius: 8px; padding: 12px 16px; margin: 12px 0; }
  .case.failed { border-color: #c62828; }
  .steps { list-style: none; padding-left: 0; }
  .step { padding: 4px 0; border-bottom: 1px solid #f0f0f0; }
  .step:last-child { border-bottom: none; }
  .mono { font-family: ui-monospace, monospace; color: #555; }
  .kind { font-family: ui-monospace, monospace; background: #f5f5f5; padding: 0 4px; border-radius: 4px; }
  .muted { color: #888; font-weight: normal; }
  .share-warning { color: #8a6d3b; background: #fcf8e3; border: 1px solid #faebcc; padding: 8px 12px; border-radius: 6px; font-size: 13px; }
  pre.error { background: #ffebee; color: #b71c1c; padding: 6px 10px; border-radius: 4px; overflow: auto; }
  img { max-width: 100%; border: 1px solid #ddd; border-radius: 4px; margin-top: 6px; }
  details summary { cursor: pointer; color: #555; font-size: 13px; }
</style>
</head>
<body>
<h1>playautomation report</h1>
<div class="summary">
  <span><b>run:</b> <span class="mono">${escapeHtml(summary.runId)}</span></span>
  <span><b>cases:</b> ${summary.totalCases}</span>
  <span><b>passed:</b> ${summary.passed}</span>
  <span><b>failed:</b> ${summary.failed}</span>
</div>
<p class="share-warning">This report inlines a screenshot of every step. Screenshots can contain page data
you would not want to share (session details, personal data) — review before distributing this file.</p>
${flows}
</body>
</html>
`;
}

export function writeHtmlReport(resultsDir: string, summary: RunSummary): string {
  const file = path.join(resultsDir, 'report.html');
  fs.writeFileSync(file, renderHtmlReport(summary));
  return file;
}
