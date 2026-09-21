import { describe, expect, it } from 'vitest';
import { findCommand, parseCommandArgs } from '../../lib/commands.js';

describe('parseCommandArgs', () => {
  it('parses positionals and string flags', () => {
    const def = findCommand('fill')!;
    const parsed = parseCommandArgs(def, ['e5', 'hello world']);
    expect(parsed.positionals).toEqual(['e5', 'hello world']);
  });

  it('parses --flag=value and boolean flags', () => {
    const def = findCommand('screenshot')!;
    const parsed = parseCommandArgs(def, ['--path=out.png', '--full-page']);
    expect(parsed.flags.path).toBe('out.png');
    expect(parsed.flags['full-page']).toBe(true);
  });

  it('applies flag defaults', () => {
    const def = findCommand('wait')!;
    const parsed = parseCommandArgs(def, ['500']);
    expect(parsed.flags.timeout).toBe('10000');
  });

  it('supports variadic positionals', () => {
    const def = findCommand('select')!;
    const parsed = parseCommandArgs(def, ['e3', 'pro', 'enterprise']);
    expect(parsed.positionals).toEqual(['e3', 'pro', 'enterprise']);
  });

  it('rejects unknown flags', () => {
    const def = findCommand('click')!;
    expect(() => parseCommandArgs(def, ['e1', '--nope'])).toThrow(/Unknown option --nope/);
  });

  it('rejects missing required positionals', () => {
    const def = findCommand('goto')!;
    expect(() => parseCommandArgs(def, [])).toThrow(/Expected 1 argument/);
  });

  it('rejects too many positionals', () => {
    const def = findCommand('click')!;
    expect(() => parseCommandArgs(def, ['e1', 'extra'])).toThrow(/Too many arguments/);
  });

  it('treats values after -- as literals', () => {
    const def = findCommand('fill')!;
    const parsed = parseCommandArgs(def, ['e1', '--', '-weird-value']);
    expect(parsed.positionals).toEqual(['e1', '-weird-value']);
  });
});
