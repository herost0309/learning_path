// Backend: dispatches daemon-routed commands against the live browser context.
// Mirrors the shape of playwright-main/packages/playwright-core/src/tools/backend/:
// every action resolves a target (ref or css selector), executes with playwright
// actionability, records a stable selector + action entry, and returns a fresh
// snapshot so the agent loop always has current refs.

import * as fs from 'node:fs';
import * as path from 'node:path';
import type { BrowserContext, Page } from 'playwright-core';
import type { ParsedArgs } from '../commands.js';
import type { ToolResultPayload } from '../protocol.js';
import { Tab } from '../backend/tab.js';
import { renderSnapshot } from '../backend/render.js';
import { ActionLog } from '../recorder/actionLog.js';
import { compileFlow } from '../recorder/flow.js';
import { generateFlowScript } from '../recorder/codegen.js';
import { locate, locatorExpr } from '../runner/selectors.js';
import { loadStepSpec } from '../spec.js';

const ACTION_TIMEOUT_MS = 10_000;
const NAV_TIMEOUT_MS = 30_000;

export class Backend {
  readonly context: BrowserContext;
  readonly tab: Tab;
  readonly log: ActionLog;
  private screenshotCount = 0;

  private constructor(context: BrowserContext, page: Page, sessionDir: string) {
    this.context = context;
    this.tab = new Tab(page);
    this.log = new ActionLog(sessionDir);
  }

  static async create(context: BrowserContext, sessionDir: string): Promise<Backend> {
    const page = context.pages()[0] ?? await context.newPage();
    await page.goto('about:blank').catch(() => {});
    return new Backend(context, page, sessionDir);
  }

  get page() {
    return this.tab.page;
  }

  async dispatch(parsed: ParsedArgs): Promise<ToolResultPayload> {
    const flags = parsed.flags;
    const pos = parsed.positionals;
    switch (parsed.command) {
      case 'goto':
        return this.cmdGoto(pos[0]);
      case 'snapshot':
        return this.cmdSnapshot(flags.full === true);
      case 'find':
        return this.cmdFind(pos[0]);
      case 'click':
        return this.actOnElement('click', pos[0], { force: flags.force === true });
      case 'hover':
        return this.actOnElement('hover', pos[0], {});
      case 'check':
        return this.actOnElement('check', pos[0], {});
      case 'uncheck':
        return this.actOnElement('uncheck', pos[0], {});
      case 'fill':
        return this.cmdFill(pos[0], pos[1] ?? '');
      case 'select':
        return this.cmdSelect(pos[0], pos.slice(1));
      case 'press':
        return this.cmdPress(pos[0]);
      case 'wait':
        return this.cmdWait(pos[0], String(flags.url ?? ''), String(flags.text ?? ''), parseInt(String(flags.timeout), 10) || 10_000);
      case 'screenshot':
        return this.cmdScreenshot(flags.path ? String(flags.path) : undefined, flags['full-page'] === true);
      case 'assert':
        return this.cmdAssert(pos[0], pos[1], pos[2]);
      case 'save':
        return this.cmdSave(
          flags.name ? String(flags.name) : undefined,
          flags.spec ? String(flags.spec) : undefined,
          flags.out ? String(flags.out) : undefined,
        );
      default:
        return { isError: true, text: `Unknown daemon command: ${parsed.command}` };
    }
  }

  // ---------- commands ----------

  private async cmdGoto(url: string): Promise<ToolResultPayload> {
    const target = /^[a-zA-Z][\w+.-]*:/.test(url) ? url : `https://${url}`;
    await this.page.goto(target, { timeout: NAV_TIMEOUT_MS, waitUntil: 'domcontentloaded' });
    await this.page.waitForLoadState('load', { timeout: 5_000 }).catch(() => {});
    this.log.record({ action: 'goto', url: this.page.url(), params: { url: target } });
    const snapshot = await this.tab.capture();
    return ok(`Navigated to ${this.page.url()}`, {
      action: 'goto',
      url: this.page.url(),
      snapshot: renderSnapshot(snapshot),
    });
  }

  private async cmdSnapshot(full: boolean): Promise<ToolResultPayload> {
    const snapshot = await this.tab.capture({ full });
    return ok(renderSnapshot(snapshot), { action: 'snapshot', snapshot: renderSnapshot(snapshot) });
  }

  private async cmdFind(text: string): Promise<ToolResultPayload> {
    const matches = await this.tab.find(text);
    if (!matches.length)
      return ok(`No matches for ${JSON.stringify(text)}.`, { action: 'find', matches: [] });
    const lines = matches.map(m => `  ${m.ref}  ${m.role}${m.name ? ` "${m.name}"` : ''}  ${m.selector}`);
    return ok(`Found ${matches.length} match(es) for ${JSON.stringify(text)}:\n${lines.join('\n')}`, { action: 'find', matches });
  }

  private async cmdFill(target: string, text: string): Promise<ToolResultPayload> {
    const info = await this.resolveTarget(target);
    let isSecret = false;
    try {
      // Password fields: the real value is recorded for replay but masked in
      // the response (summary + code preview) so it never hits transcripts.
      isSecret = await info.element!.evaluate(el => el instanceof HTMLInputElement && el.type === 'password').catch(() => false);
      await info.element!.fill(text, { timeout: ACTION_TIMEOUT_MS });
    } finally {
      await info.element!.dispose().catch(() => {});
    }
    this.recordEntry('fill', info, { text });
    const shown = isSecret ? '••••••••' : text;
    return await this.actionResponse(`Filled ${info.description} with ${JSON.stringify(shown)}`, 'fill', info, { text: shown });
  }

  private async cmdSelect(target: string, values: string[]): Promise<ToolResultPayload> {
    const info = await this.resolveTarget(target);
    try {
      await info.element!.selectOption(values, { timeout: ACTION_TIMEOUT_MS });
    } finally {
      await info.element!.dispose().catch(() => {});
    }
    this.recordEntry('select', info, { values });
    return await this.actionResponse(`Selected ${JSON.stringify(values)} in ${info.description}`, 'select', info, { values });
  }

  private async cmdPress(key: string): Promise<ToolResultPayload> {
    await this.page.keyboard.press(key);
    const info = { ref: undefined, selector: undefined, description: `key ${key}` } as TargetInfo;
    this.recordEntry('press', info, { key });
    return await this.actionResponse(`Pressed ${key}`, 'press', info, { key });
  }

  private async cmdWait(what: string | undefined, urlPattern: string, text: string, timeoutMs: number): Promise<ToolResultPayload> {
    let wait: { kind: 'ms' | 'selector' | 'url' | 'text'; value: string; timeoutMs?: number };
    let description: string;
    if (urlPattern) {
      wait = { kind: 'url', value: urlPattern, timeoutMs };
      await this.page.waitForURL(urlPattern, { timeout: timeoutMs });
      description = `URL matching ${urlPattern}`;
    } else if (text) {
      wait = { kind: 'text', value: text, timeoutMs };
      await this.page.getByText(text).first().waitFor({ timeout: timeoutMs });
      description = `text ${JSON.stringify(text)}`;
    } else if (what && /^\d+$/.test(what)) {
      wait = { kind: 'ms', value: what };
      await this.page.waitForTimeout(parseInt(what, 10));
      description = `${what}ms`;
    } else if (what) {
      wait = { kind: 'selector', value: what, timeoutMs };
      await locate(this.page, what).first().waitFor({ timeout: timeoutMs });
      description = `selector ${what}`;
    } else {
      return { isError: true, text: 'wait needs <ms>, <selector>, --url= or --text=' };
    }
    this.log.record({ action: 'wait', url: this.page.url(), params: { wait } });
    return ok(`Waited for ${description}.`, { action: 'wait', wait });
  }

  private async cmdScreenshot(explicitPath: string | undefined, fullPage: boolean): Promise<ToolResultPayload> {
    const dir = this.log.screenshotDir();
    fs.mkdirSync(dir, { recursive: true });
    const file = explicitPath ?? path.join(dir, `shot-${++this.screenshotCount}.png`);
    await this.page.screenshot({ path: file, fullPage });
    this.log.record({ action: 'screenshot', url: this.page.url(), params: {} });
    return ok(`Screenshot saved: ${file}`, { action: 'screenshot', path: file });
  }

  private async cmdAssert(kind: string, arg1?: string, arg2?: string): Promise<ToolResultPayload> {
    const assert = await this.evaluateAssert(kind, arg1, arg2);
    this.log.record({ action: 'assert', url: this.page.url(), params: { assert } });
    return ok(`Assertion passed: ${describeAssert(assert)}`, { action: 'assert', assert });
  }

  private async evaluateAssert(kind: string, arg1?: string, arg2?: string): Promise<NonNullable<import('../flow.js').FlowStep['assert']>> {
    switch (kind) {
      case 'visible':
      case 'hidden': {
        if (!arg1)
          throw new Error(`assert ${kind} needs a target (ref or selector)`);
        const info = await this.resolveTargetForAssert(arg1);
        await locate(this.page, info.selector!).first().waitFor({ state: kind, timeout: ACTION_TIMEOUT_MS });
        return { kind, target: info.selector };
      }
      case 'text': {
        if (!arg1)
          throw new Error('assert text needs the expected text');
        await this.page.getByText(arg1).first().waitFor({ timeout: ACTION_TIMEOUT_MS });
        return { kind: 'text', expected: arg1 };
      }
      case 'url': {
        if (!arg1)
          throw new Error('assert url needs a substring');
        await this.page.waitForURL(url => url.href.includes(arg1!), { timeout: ACTION_TIMEOUT_MS });
        return { kind: 'url', expected: arg1 };
      }
      case 'value': {
        if (!arg1 || arg2 === undefined)
          throw new Error('assert value needs <target> <expected>');
        const info = await this.resolveTargetForAssert(arg1);
        const locator = locate(this.page, info.selector!).first();
        await locator.waitFor({ timeout: ACTION_TIMEOUT_MS });
        const actual = await locator.inputValue({ timeout: ACTION_TIMEOUT_MS });
        if (actual !== arg2)
          throw new Error(`assert value failed: expected ${JSON.stringify(arg2)}, got ${JSON.stringify(actual)}`);
        return { kind: 'value', target: info.selector, expected: arg2 };
      }
      default:
        throw new Error(`Unknown assert kind: ${kind}. Use visible|hidden|text|url|value.`);
    }
  }

  private async resolveTargetForAssert(target: string): Promise<TargetInfo> {
    const info = await this.resolveTarget(target);
    if (!info.selector)
      throw new Error('Could not derive a stable selector for the assert target');
    return info;
  }

  private async cmdSave(name: string | undefined, specPath: string | undefined, outDir: string | undefined): Promise<ToolResultPayload> {
    const flowName = name ?? 'flow';
    const out = path.resolve(outDir ?? 'flows');
    const spec = specPath ? loadStepSpec(specPath) : undefined;
    const entries = this.log.read();
    if (!entries.some(e => e.action === 'goto'))
      return { isError: true, text: 'Nothing recorded yet (no goto). Drive some steps first.' };
    const flow = compileFlow(entries, { name: flowName, spec });
    fs.mkdirSync(out, { recursive: true });
    const flowFile = path.join(out, `${flowName}.flow.json`);
    const scriptFile = path.join(out, `${flowName}.ts`);
    fs.writeFileSync(flowFile, JSON.stringify(flow, null, 2));
    fs.writeFileSync(scriptFile, generateFlowScript(flow));
    const ambiguous = flow.steps.filter(s => s.selector?.includes('nth='));
    const lines = [
      `Saved ${flow.steps.length} steps to ${flowFile}`,
      `TypeScript view: ${scriptFile}`,
      `Inputs: ${Object.keys(flow.inputs).join(', ') || '(none)'}`,
      ...flow.steps.map(s => `  ${s.id} ${s.kind}${s.selector ? ` ${s.selector}` : ''}${s.text ? ` "${s.text}"` : ''}`),
    ];
    if (ambiguous.length)
      lines.push(`\nWARNING: ${ambiguous.length} step(s) use positional (nth) selectors — brittle on replay. Consider data-testid attributes on the page.`);
    return ok(lines.join('\n'), { action: 'save', flowFile, scriptFile, stepCount: flow.steps.length });
  }

  // ---------- shared action plumbing ----------

  private async actOnElement(kind: 'click' | 'hover' | 'check' | 'uncheck', target: string, opts: { force?: boolean }): Promise<ToolResultPayload> {
    const info = await this.resolveTarget(target);
    const element = info.element!;
    try {
      switch (kind) {
        case 'click':
          await element.click({ timeout: ACTION_TIMEOUT_MS, force: opts.force });
          break;
        case 'hover':
          await element.hover({ timeout: ACTION_TIMEOUT_MS });
          break;
        case 'check':
          await element.check({ timeout: ACTION_TIMEOUT_MS });
          break;
        case 'uncheck':
          await element.uncheck({ timeout: ACTION_TIMEOUT_MS });
          break;
      }
    } finally {
      await element.dispose().catch(() => {});
    }
    this.recordEntry(kind, info, {});
    const verb = { click: 'Clicked', hover: 'Hovered', check: 'Checked', uncheck: 'Unchecked' }[kind];
    return await this.actionResponse(`${verb} ${info.description}`, kind, info, {});
  }

  private async actionResponse(summary: string, action: string, info: TargetInfo, params: Record<string, unknown>): Promise<ToolResultPayload> {
    await this.page.waitForLoadState('load', { timeout: 5_000 }).catch(() => {});
    const snapshot = await this.tab.capture();
    const code = codeLine(action, info.selector, params);
    const text = [summary, info.selector ? `selector: ${info.selector}` : '', code ? `code: ${code}` : '', '', renderSnapshot(snapshot)]
      .filter(Boolean)
      .join('\n');
    return ok(text, { action, selector: info.selector, snapshot: renderSnapshot(snapshot), code });
  }

  private recordEntry(action: string, info: TargetInfo, params: Record<string, unknown>): void {
    this.log.record({
      action,
      ref: info.ref,
      selector: info.selector,
      css: info.css,
      url: this.page.url(),
      params,
    });
  }

  // Resolve a CLI target: ref (e5) → element + generated stable selector;
  // otherwise a selector-DSL string (css=..., testid=..., role=..., or bare CSS).
  private async resolveTarget(target: string): Promise<TargetInfo> {
    const isRef = /^(f\d+)?e\d+$/.test(target);
    if (isRef) {
      const element = await this.tab.elementFor(target);
      const description = await this.tab.describe(target).then(d => `${d.role}${d.name ? ` "${d.name}"` : ''} (${target})`);
      let selector: string | undefined;
      let css: string | undefined;
      try {
        const generated = await this.tab.genSelector(target);
        selector = generated.selector;
        css = generated.css;
      } catch {
        selector = undefined; // stale between resolve and generate; action already has the element
      }
      return { ref: target, element, selector, css, description };
    }
    const locator = locate(this.page, target).first();
    await locator.waitFor({ state: 'attached', timeout: ACTION_TIMEOUT_MS });
    const element = await locator.elementHandle();
    if (!element)
      throw new Error(`No element matches selector: ${target}`);
    return { element, selector: target, css: undefined, description: `element matching ${target}` };
  }
}

interface TargetInfo {
  ref?: string;
  selector?: string;
  css?: string;
  description: string;
  element?: import('playwright-core').ElementHandle<SVGElement | HTMLElement>;
}

function ok(text: string, meta?: Record<string, unknown>): ToolResultPayload {
  return { isError: false, text, meta };
}

// One-line TS codegen preview for action responses (pattern: Response.addCode).
export function codeLine(action: string, selector: string | undefined, params: Record<string, unknown>): string | undefined {
  if (!selector)
    return undefined;
  if (action === 'fill')
    return `await ${locatorExpr(selector)}.fill(${JSON.stringify(String(params.text ?? ''))});`;
  if (action === 'select')
    return `await ${locatorExpr(selector)}.selectOption(${JSON.stringify(params.values)});`;
  if (action === 'click' || action === 'hover' || action === 'check' || action === 'uncheck')
    return `await ${locatorExpr(selector)}.${action}();`;
  return undefined;
}

function describeAssert(assert: { kind: string; target?: string; expected?: string }): string {
  switch (assert.kind) {
    case 'visible':
    case 'hidden':
      return `${assert.kind}: ${assert.target}`;
    case 'text':
      return `text contains ${JSON.stringify(assert.expected)}`;
    case 'url':
      return `url includes ${JSON.stringify(assert.expected)}`;
    case 'value':
      return `${assert.target} value = ${JSON.stringify(assert.expected)}`;
    default:
      return assert.kind;
  }
}
