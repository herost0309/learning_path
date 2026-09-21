// Command table: single source of truth for CLI grammar.
// The client parses argv against it; the daemon validates and dispatches daemon-routed commands.
// Pattern follows playwright-main/packages/playwright-core/src/tools/cli-daemon/{commands,command}.ts.

export interface PositionalArg {
  name: string;
  required?: boolean;
  variadic?: boolean;
  description?: string;
}

export interface OptionArg {
  name: string; // long name without '--'
  alias?: string; // short name without '-'
  type: 'string' | 'boolean';
  default?: string;
  description?: string;
}

export interface CommandDef {
  name: string;
  description: string;
  routed: 'daemon' | 'local';
  positional?: PositionalArg[];
  options?: OptionArg[];
}

export const COMMANDS: CommandDef[] = [
  // Session management (local)
  {
    name: 'open',
    description: 'Start a browser session (spawns the daemon) and optionally navigate to a URL',
    routed: 'local',
    positional: [{ name: 'url', required: false }],
    options: [
      { name: 'headless', type: 'boolean', description: 'Run without a visible window (default: headed)' },
      { name: 'viewport', type: 'string', description: 'Viewport size, e.g. 1280x720' },
      { name: 'idle-timeout', type: 'string', description: 'Daemon idle shutdown in seconds (0 = never; default: 0 headed / 1800 headless)' },
      { name: 'channel', type: 'string', description: 'Browser channel: chromium | chrome | msedge' },
    ],
  },
  { name: 'list', description: 'List active sessions', routed: 'local' },
  { name: 'kill', description: 'Force-kill the session daemon', routed: 'local' },
  {
    name: 'serve',
    description: 'Serve a directory of static test pages (for local experimentation)',
    routed: 'local',
    positional: [{ name: 'dir', required: false }],
    options: [{ name: 'port', type: 'string', default: '8931' }],
  },

  // Browser control (daemon)
  { name: 'goto', description: 'Navigate to a URL and return a fresh snapshot', routed: 'daemon', positional: [{ name: 'url', required: true }] },
  { name: 'snapshot', description: 'Capture the accessibility snapshot with element refs', routed: 'daemon', options: [{ name: 'full', type: 'boolean', description: 'Include non-interactable nodes' }] },
  {
    name: 'click', description: 'Click an element (ref like e12, or a selector)',
    routed: 'daemon',
    positional: [{ name: 'target', required: true }],
    options: [{ name: 'force', type: 'boolean', description: 'Skip actionability checks' }],
  },
  { name: 'fill', description: 'Fill a text field (empty text clears it)', routed: 'daemon', positional: [{ name: 'target', required: true }, { name: 'text', required: false }] },
  { name: 'select', description: 'Select option(s) in a <select>', routed: 'daemon', positional: [{ name: 'target', required: true }, { name: 'values', required: true, variadic: true }] },
  { name: 'check', description: 'Check a checkbox/radio', routed: 'daemon', positional: [{ name: 'target', required: true }] },
  { name: 'uncheck', description: 'Uncheck a checkbox', routed: 'daemon', positional: [{ name: 'target', required: true }] },
  { name: 'press', description: 'Press a key (e.g. Enter, Control+a)', routed: 'daemon', positional: [{ name: 'key', required: true }] },
  { name: 'hover', description: 'Hover an element', routed: 'daemon', positional: [{ name: 'target', required: true }] },
  {
    name: 'wait', description: 'Wait for ms | selector | --url=pattern | --text=text',
    routed: 'daemon',
    positional: [{ name: 'what', required: false }],
    options: [
      { name: 'url', type: 'string', description: 'URL glob pattern to wait for' },
      { name: 'text', type: 'string', description: 'Text to wait for' },
      { name: 'timeout', type: 'string', default: '10000', description: 'Max wait in ms' },
    ],
  },
  { name: 'find', description: 'Find elements by text in the last snapshot (no action recorded)', routed: 'daemon', positional: [{ name: 'text', required: true }] },
  {
    name: 'screenshot', description: 'Take a screenshot (recorded as a flow step)',
    routed: 'daemon',
    options: [
      { name: 'path', type: 'string', description: 'Output file (default: session screenshots dir)' },
      { name: 'full-page', type: 'boolean' },
    ],
  },
  {
    name: 'assert', description: 'Assert and record: assert visible|hidden <target> | assert text <text> | assert url <substring> | assert value <target> <expected>',
    routed: 'daemon',
    positional: [{ name: 'kind', required: true }, { name: 'arg1', required: false }, { name: 'arg2', required: false }],
  },
  {
    name: 'save', description: 'Compile the recorded action log into a replayable flow.json + TS script',
    routed: 'daemon',
    options: [
      { name: 'name', type: 'string', description: 'Flow name (default: session name)' },
      { name: 'spec', type: 'string', description: 'Path to the source YAML spec (enables input hoisting)' },
      { name: 'out', type: 'string', description: 'Output directory (default: ./flows)' },
    ],
  },
  { name: 'close', description: 'Close the browser and stop the session daemon', routed: 'daemon' },

  // Replay (local)
  {
    name: 'run',
    description: 'Deterministically replay flow file(s), like `npx playwright test`',
    routed: 'local',
    positional: [{ name: 'flows', required: true, variadic: true }],
    options: [
      { name: 'cases', type: 'string', description: 'Cases file (default: <flow>.cases.yaml when present; "none" disables)' },
      { name: 'headed', type: 'boolean' },
      { name: 'report', type: 'string', description: 'Report base directory (default: ./results)' },
      { name: 'timeout', type: 'string', default: '10000', description: 'Per-step timeout in ms' },
    ],
  },
  {
    name: 'cases',
    description: 'Show (or save with --save) the case matrix for a flow',
    routed: 'local',
    positional: [{ name: 'flow', required: true }],
    options: [{ name: 'save', type: 'boolean', description: 'Write <flow>.cases.yaml with rule-generated variants' }],
  },
  { name: 'report', description: 'Regenerate report.html from a results directory', routed: 'local', positional: [{ name: 'resultsDir', required: true }] },
  { name: 'version', description: 'Print version', routed: 'local' },
  { name: 'help', description: 'Show help', routed: 'local', options: [{ name: 'command', type: 'string' }] },
];

export function findCommand(name: string): CommandDef | undefined {
  return COMMANDS.find(c => c.name === name);
}

export interface ParsedArgs {
  command: string;
  positionals: string[];
  flags: Record<string, string | boolean | undefined>;
}

// Parses argv against the command table; rejects unknown flags.
// (Hand-rolled minimist-style parsing, pattern from cli-client/minimist.ts.)
export function parseCommandArgs(def: CommandDef, argv: string[]): ParsedArgs {
  const positionals: string[] = [];
  const flags: Record<string, string | boolean | undefined> = {};
  const options = def.options ?? [];
  const knownOptions = new Set<string>();
  for (const opt of options) {
    knownOptions.add(opt.name);
    if (opt.default !== undefined) flags[opt.name] = opt.default;
  }

  let i = 0;
  const takeValue = (name: string, inline?: string): void => {
    if (inline !== undefined) {
      flags[name] = inline;
      return;
    }
    const next = argv[++i];
    if (next === undefined)
      throw new Error(`Missing value for --${name}`);
    flags[name] = next;
  };

  while (i < argv.length) {
    const arg = argv[i];
    if (arg === '--') {
      positionals.push(...argv.slice(i + 1));
      break;
    }
    if (arg.startsWith('--')) {
      const body = arg.slice(2);
      const eq = body.indexOf('=');
      const name = eq >= 0 ? body.slice(0, eq) : body;
      const inline = eq >= 0 ? body.slice(eq + 1) : undefined;
      const opt = options.find(o => o.name === name);
      if (!opt)
        throw new Error(`Unknown option --${name} for command "${def.name}"`);
      if (opt.type === 'boolean' && inline === undefined)
        flags[opt.name] = true;
      else
        takeValue(opt.name, inline);
    } else if (arg.startsWith('-') && arg.length > 1) {
      const alias = arg.slice(1);
      const eq = alias.indexOf('=');
      const name = eq >= 0 ? alias.slice(0, eq) : alias;
      const inline = eq >= 0 ? alias.slice(eq + 1) : undefined;
      const opt = options.find(o => o.alias === name);
      if (!opt)
        throw new Error(`Unknown option -${name} for command "${def.name}"`);
      if (opt.type === 'boolean' && inline === undefined)
        flags[opt.name] = true;
      else
        takeValue(opt.name, inline);
    } else {
      positionals.push(arg);
    }
    i++;
  }

  // Validate positional arity.
  const posDefs = def.positional ?? [];
  const required = posDefs.filter(p => p.required).length;
  if (posDefs.some(p => p.variadic)) {
    if (positionals.length < required)
      throw new Error(`Expected at least ${required} argument(s) for "${def.name}", got ${positionals.length}`);
  } else {
    const total = posDefs.length;
    if (positionals.length < required)
      throw new Error(`Expected ${required} argument(s) for "${def.name}", got ${positionals.length}`);
    if (positionals.length > total)
      throw new Error(`Too many arguments for "${def.name}" (expected at most ${total}); use -- to pass literal values`);
  }
  return { command: def.name, positionals, flags };
}

export function usageText(): string {
  const lines = ['playautomation — agent-driven browser automation', '', 'Usage: playautomation [options] <command> [args]', '', 'Options:', '  -s, --session <name>   Session name (default: "default")', '      --json             Machine-readable JSON output', '', 'Commands:'];
  for (const c of COMMANDS)
    lines.push(`  ${c.name.padEnd(12)} ${c.description}`);
  return lines.join('\n');
}

export function commandHelp(def: CommandDef): string {
  const lines = [`${def.name} — ${def.description}`, '', 'Usage:'];
  const pos = (def.positional ?? []).map(p => p.required ? `<${p.name}>` : `[${p.name}${p.variadic ? '...' : ''}]`).join(' ');
  lines.push(`  playautomation ${def.name}${pos ? ' ' + pos : ''} [options]`);
  if (def.options?.length) {
    lines.push('', 'Options:');
    for (const o of def.options)
      lines.push(`  --${o.name}${o.type === 'boolean' ? '' : ' <value>'}${o.default !== undefined ? ` (default: ${o.default})` : ''}${o.description ? `  ${o.description}` : ''}`);
  }
  return lines.join('\n');
}
