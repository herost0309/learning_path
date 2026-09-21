// Compiles the raw action log into a deterministic flow.
// Mirrors the cleanup ideas of playwright's recorder signal processor:
// drop non-actions (snapshot/find), merge consecutive fills, hoist user text
// into named inputs when a source spec is provided.

import type { Flow, FlowStep } from '../flow.js';
import type { StepSpec } from '../spec.js';
import type { ActionEntry } from './actionLog.js';

const kRecordedActions = new Set(['goto', 'click', 'fill', 'select', 'check', 'uncheck', 'press', 'hover', 'wait', 'screenshot', 'assert']);

export interface CompileOptions {
  name: string;
  spec?: StepSpec;
  defaultBaseUrl?: string;
}

export function compileFlow(entries: ActionEntry[], opts: CompileOptions): Flow {
  const specInputs = opts.spec?.inputs ?? {};
  const flowInputs: Record<string, string> = {};
  const steps: FlowStep[] = [];
  let stepNum = 0;
  let firstUrl: string | undefined;

  for (const entry of entries) {
    if (!kRecordedActions.has(entry.action))
      continue;
    // Merge consecutive fills on the same target: only the final value matters.
    if (entry.action === 'fill') {
      const last = steps[steps.length - 1];
      if (last?.kind === 'fill' && last.selector === entry.selector) {
        last.text = hoistText(String(entry.params?.text ?? ''), specInputs, flowInputs);
        continue;
      }
    }
    const step = toStep(entry, specInputs, flowInputs);
    if (step.kind === 'goto' && !firstUrl)
      firstUrl = step.url;
    step.id = `s${++stepNum}`;
    steps.push(step);
  }

  const baseUrl = opts.spec?.baseUrl ?? opts.defaultBaseUrl ?? deriveBaseUrl(firstUrl);
  return {
    version: 1,
    name: opts.name,
    createdAt: new Date().toISOString(),
    baseUrl,
    inputs: flowInputs,
    steps,
    sourceSpec: opts.spec ? opts.name + '.yaml' : undefined,
  };
}

function toStep(entry: ActionEntry, specInputs: Record<string, string>, flowInputs: Record<string, string>): FlowStep {
  const params = entry.params ?? {};
  const base = { id: '', ref: entry.ref } as FlowStep;
  switch (entry.action) {
    case 'goto':
      return { ...base, id: '', kind: 'goto', url: String(params.url ?? '') };
    case 'click':
    case 'hover':
    case 'check':
    case 'uncheck':
      return { ...base, kind: entry.action, selector: entry.selector };
    case 'fill':
      return { ...base, kind: 'fill', selector: entry.selector, text: hoistText(String(params.text ?? ''), specInputs, flowInputs) };
    case 'select':
      return { ...base, kind: 'select', selector: entry.selector, values: params.values as string[] };
    case 'press':
      return { ...base, kind: 'press', key: String(params.key ?? '') };
    case 'wait':
      return { ...base, kind: 'wait', wait: params.wait as FlowStep['wait'] };
    case 'screenshot':
      return { ...base, kind: 'screenshot' };
    case 'assert':
      return { ...base, kind: 'assert', assert: params.assert as FlowStep['assert'] };
    default:
      throw new Error(`Unexpected action ${entry.action}`);
  }
}

// If the filled text equals one of the spec's input values, record it as a
// {{template}} so replay varies per case; otherwise keep the literal.
function hoistText(text: string, specInputs: Record<string, string>, flowInputs: Record<string, string>): string {
  for (const [key, value] of Object.entries(specInputs)) {
    if (value === text) {
      flowInputs[key] = value;
      return `{{${key}}}`;
    }
  }
  return text;
}

function deriveBaseUrl(url: string | undefined): string | undefined {
  if (!url)
    return undefined;
  try {
    return new URL(url).origin;
  } catch {
    return undefined;
  }
}
