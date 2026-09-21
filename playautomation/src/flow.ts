// The flow model: the deterministic, replayable artifact produced by `save`
// and consumed by `run`. flow.json stores stable selectors only — never refs.

import * as fs from 'node:fs';


export type StepKind = 'goto' | 'click' | 'fill' | 'select' | 'check' | 'uncheck' | 'press' | 'hover' | 'wait' | 'screenshot' | 'assert';

export interface FlowStep {
  id: string;
  kind: StepKind;
  selector?: string; // canonical DSL: role=button[name="Sign in"], css=#id, text=Hi >> nth=0
  ref?: string; // informational only (provenance from exploration)
  text?: string; // fill text; may be "{{inputName}}"
  url?: string; // goto url
  values?: string[]; // select values
  key?: string; // press key
  wait?: { kind: 'ms' | 'selector' | 'url' | 'text'; value: string; timeoutMs?: number };
  assert?: { kind: 'visible' | 'hidden' | 'text' | 'url' | 'value'; target?: string; expected?: string };
}

export interface Flow {
  version: 1;
  name: string;
  createdAt: string;
  baseUrl?: string;
  inputs: Record<string, string>;
  steps: FlowStep[];
  sourceSpec?: string;
}

export function resolveInputs(flow: Flow, overrides?: Record<string, string>): Record<string, string> {
  const merged = { ...flow.inputs, ...overrides };
  // Input values may be {{env:VAR}} references so secrets never need to be
  // stored in flow.json / cases.yaml — resolve them from the environment.
  return Object.fromEntries(Object.entries(merged).map(([k, v]) => [k, resolveEnvTemplates(v)]));
}

// Resolves {{inputName}} and {{env:VAR}} templates in a value against the case
// inputs and the process environment.
export function resolveTemplate(value: string | undefined, inputs: Record<string, string>): string {
  if (!value)
    return '';
  return value.replace(/\{\{\s*(env:[\w.-]+|[\w.-]+)\s*\}\}/g, (whole, name: string) => {
    if (name.startsWith('env:'))
      return envValue(name.slice('env:'.length), whole);
    if (!(name in inputs))
      throw new Error(`Unknown input "${name}" referenced by template ${whole}. Known inputs: ${Object.keys(inputs).join(', ') || '(none)'}`);
    return inputs[name];
  });
}

export function resolveEnvTemplates(value: string): string {
  return value.replace(/\{\{\s*env:([\w.-]+)\s*\}\}/g, (whole, name: string) => envValue(name, whole));
}

function envValue(name: string, whole: string): string {
  const value = process.env[name];
  if (value === undefined)
    throw new Error(`Environment variable "${name}" is not set (referenced by template ${whole}). Set it before replay so secrets can stay out of flow files.`);
  return value;
}

export function loadFlow(file: string): Flow {
  const raw = fs.readFileSync(file, 'utf8');
  const parsed = JSON.parse(raw) as Flow;
  if (parsed.version !== 1)
    throw new Error(`Unsupported flow version ${parsed.version} in ${file}`);
  return parsed;
}
