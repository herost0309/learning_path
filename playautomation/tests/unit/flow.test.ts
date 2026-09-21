import { describe, expect, it } from 'vitest';
import { compileFlow } from '../../lib/recorder/flow.js';
import { resolveInputs, resolveTemplate } from '../../lib/flow.js';
import type { ActionEntry } from '../../lib/recorder/actionLog.js';

function entry(partial: Partial<ActionEntry>): ActionEntry {
  return { seq: 0, ts: 0, action: 'click', url: 'http://x/', ...partial } as ActionEntry;
}

describe('compileFlow', () => {
  const spec = { name: 't', inputs: { email: 'a@b.c' }, steps: [], assert: [] };

  it('drops non-action entries (snapshot/find)', () => {
    const flow = compileFlow([
      entry({ action: 'snapshot' }),
      entry({ action: 'goto', params: { url: 'http://x/login' } }),
      entry({ action: 'find' }),
    ], { name: 't' });
    expect(flow.steps.map(s => s.kind)).toEqual(['goto']);
    expect(flow.baseUrl).toBe('http://x');
  });

  it('merges consecutive fills on the same selector', () => {
    const flow = compileFlow([
      entry({ action: 'fill', selector: 'css=#email', params: { text: 'partial' } }),
      entry({ action: 'fill', selector: 'css=#email', params: { text: 'full@example.com' } }),
      entry({ action: 'fill', selector: 'css=#name', params: { text: 'x' } }),
    ], { name: 't' });
    expect(flow.steps).toHaveLength(2);
    expect(flow.steps[0].text).toBe('full@example.com');
    expect(flow.steps[1].text).toBe('x');
  });

  it('does not merge fills separated by other steps', () => {
    const flow = compileFlow([
      entry({ action: 'fill', selector: 'css=#email', params: { text: 'a' } }),
      entry({ action: 'click', selector: 'css=#btn' }),
      entry({ action: 'fill', selector: 'css=#email', params: { text: 'b' } }),
    ], { name: 't' });
    expect(flow.steps).toHaveLength(3);
  });

  it('hoists spec input values into {{templates}}', () => {
    const flow = compileFlow([
      entry({ action: 'fill', selector: 'css=#email', params: { text: 'a@b.c' } }),
      entry({ action: 'fill', selector: 'css=#other', params: { text: 'literal' } }),
    ], { name: 't', spec });
    expect(flow.steps[0].text).toBe('{{email}}');
    expect(flow.steps[1].text).toBe('literal');
    expect(flow.inputs).toEqual({ email: 'a@b.c' });
  });

  it('keeps press/wait/assert/screenshot steps', () => {
    const flow = compileFlow([
      entry({ action: 'press', params: { key: 'Enter' } }),
      entry({ action: 'wait', params: { wait: { kind: 'text', value: 'Done' } } }),
      entry({ action: 'assert', params: { assert: { kind: 'text', expected: 'Done' } } }),
      entry({ action: 'screenshot' }),
    ], { name: 't' });
    expect(flow.steps.map(s => s.kind)).toEqual(['press', 'wait', 'assert', 'screenshot']);
  });

  it('derives baseUrl from the first goto', () => {
    const flow = compileFlow([
      entry({ action: 'goto', params: { url: 'https://shop.example.com/cart' } }),
    ], { name: 't' });
    expect(flow.baseUrl).toBe('https://shop.example.com');
  });
});

describe('resolveTemplate / resolveInputs ({{env:VAR}} secrets)', () => {
  it('resolves {{env:VAR}} in step text from the environment', () => {
    process.env.PA_TEST_SECRET = 'hunter2';
    try {
      expect(resolveTemplate('{{env:PA_TEST_SECRET}}', {})).toBe('hunter2');
      expect(resolveTemplate('user-{{env:PA_TEST_SECRET}}!', {})).toBe('user-hunter2!');
    } finally {
      delete process.env.PA_TEST_SECRET;
    }
  });

  it('resolves {{env:VAR}} input values via resolveInputs', () => {
    process.env.PA_TEST_SECRET = 'hunter2';
    try {
      const inputs = resolveInputs({ version: 1, name: 't', createdAt: '', inputs: { password: '{{env:PA_TEST_SECRET}}', email: 'a@b.c' }, steps: [] });
      expect(inputs.password).toBe('hunter2');
      expect(resolveTemplate('{{password}}', inputs)).toBe('hunter2');
      expect(inputs.email).toBe('a@b.c');
    } finally {
      delete process.env.PA_TEST_SECRET;
    }
  });

  it('throws a clear error when the environment variable is missing', () => {
    delete process.env.PA_TEST_MISSING_VAR;
    expect(() => resolveTemplate('{{env:PA_TEST_MISSING_VAR}}', {}))
      .toThrow(/PA_TEST_MISSING_VAR.*not set/s);
  });
});
