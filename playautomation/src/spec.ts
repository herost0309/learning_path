// User-facing YAML formats, parsed strictly with zod:
//  - step specs (flows/<name>.yaml): high-level prose steps + inputs + asserts
//  - case files (<flow>.cases.yaml): input variants for the case matrix

import * as fs from 'node:fs';
import { z } from 'zod';
import { parse as parseYaml } from 'yaml';

const stringRecord = z.record(z.string());

export const StepSpecSchema = z.object({
  name: z.string(),
  baseUrl: z.string().optional(),
  inputs: stringRecord.optional(),
  steps: z.array(z.string()),
  assert: z.array(z.string()).optional(),
});

export type StepSpec = z.infer<typeof StepSpecSchema>;

export const CasesFileSchema = z.object({
  cases: z.array(z.object({
    name: z.string(),
    inputs: stringRecord.optional(),
    expectFail: z.boolean().optional(),
  })),
});

export type CasesFile = z.infer<typeof CasesFileSchema>;

function yamlErrorContext(file: string, error: unknown): never {
  const message = error instanceof z.ZodError
    ? error.issues.map(i => `  ${i.path.join('.') || '(root)'}: ${i.message}`).join('\n')
    : String((error as Error)?.message ?? error);
  throw new Error(`Invalid YAML in ${file}:\n${message}`);
}

export function parseStepSpec(text: string, file = '<spec>'): StepSpec {
  let raw: unknown;
  try {
    raw = parseYaml(text);
  } catch (error) {
    throw new Error(`Cannot parse YAML in ${file}: ${(error as Error).message}`);
  }
  const result = StepSpecSchema.safeParse(raw);
  if (!result.success)
    yamlErrorContext(file, result.error);
  return { inputs: {}, assert: [], ...result.data };
}

export function loadStepSpec(file: string): StepSpec {
  return parseStepSpec(fs.readFileSync(file, 'utf8'), file);
}

export function loadCasesFile(file: string): CasesFile {
  let raw: unknown;
  try {
    raw = parseYaml(fs.readFileSync(file, 'utf8'));
  } catch (error) {
    throw new Error(`Cannot parse YAML in ${file}: ${(error as Error).message}`);
  }
  const result = CasesFileSchema.safeParse(raw);
  if (!result.success)
    yamlErrorContext(file, result.error);
  return result.data;
}
