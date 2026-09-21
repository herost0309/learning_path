import { describe, expect, it } from 'vitest';
import { parseStepSpec } from '../../lib/spec.js';

describe('parseStepSpec', () => {
  it('parses a full spec with defaults for optional fields', () => {
    const spec = parseStepSpec(`
name: checkout
inputs:
  city: Shanghai
steps:
  - go to cart
  - checkout
assert:
  - confirmation shows
`);
    expect(spec.name).toBe('checkout');
    expect(spec.inputs).toEqual({ city: 'Shanghai' });
    expect(spec.steps).toEqual(['go to cart', 'checkout']);
    expect(spec.assert).toEqual(['confirmation shows']);
  });

  it('defaults inputs and assert when missing', () => {
    const spec = parseStepSpec('name: x\nsteps:\n  - one\n');
    expect(spec.inputs).toEqual({});
    expect(spec.assert).toEqual([]);
  });

  it('rejects missing name', () => {
    expect(() => parseStepSpec('steps:\n  - one\n')).toThrow(/Invalid YAML/);
  });

  it('rejects non-string steps', () => {
    expect(() => parseStepSpec('name: x\nsteps:\n  - 42\n')).toThrow(/Invalid YAML/);
  });

  it('rejects malformed YAML with context', () => {
    expect(() => parseStepSpec('name: [unclosed')).toThrow(/Cannot parse YAML/);
  });
});
