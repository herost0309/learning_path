#!/usr/bin/env node
// playautomation CLI entry.
// Local commands run here; browser commands are routed to the session daemon
// over a named pipe (pattern: playwright cli-client/program.ts).

import * as path from 'node:path';
import { commandHelp, findCommand, parseCommandArgs, usageText } from './commands.js';
import { runInSession, startSession, killSession } from './client/session.js';
import { listSessions } from './client/registry.js';
import { canConnect } from './protocol.js';
import { runFlows } from './runner/run.js';
import { loadCasesForFlow, writeCasesFile } from './runner/cases.js';
import { loadFlow } from './flow.js';
import { startTestServer } from './server/testServer.js';

const VERSION = '0.1.0';

interface GlobalOptions {
  session: string;
  json: boolean;
}

function printGlobalHelp(): never {
  console.log(usageText());
  process.exit(0);
}

async function main(): Promise<number> {
  const argv = process.argv.slice(2);
  const global: GlobalOptions = { session: 'default', json: false };
  const rest: string[] = [];
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === '-s' || arg === '--session') {
      const value = argv[++i];
      if (!value) {
        console.error('--session needs a name');
        return 2;
      }
      global.session = value;
    } else if (arg.startsWith('--session=')) {
      global.session = arg.slice('--session='.length);
    } else if (arg === '--json') {
      global.json = true;
    } else if (arg === '-h' || arg === '--help') {
      printGlobalHelp();
    } else if (arg === '-v' || arg === '--version') {
      console.log(VERSION);
      return 0;
    } else {
      rest.push(arg);
    }
  }

  if (!rest.length)
    printGlobalHelp();
  const commandName = rest[0];
  const def = findCommand(commandName);
  if (!def) {
    console.error(`Unknown command: ${commandName}\n`);
    printGlobalHelp();
  }
  const commandArgv = rest.slice(1);

  if (def.routed === 'daemon') {
    const result = await runInSession(global.session, [def.name, ...commandArgv]);
    return printResult(result, global);
  }

  // Local commands
  switch (def.name) {
    case 'help': {
      const topic = commandArgv[0];
      const topicDef = topic ? findCommand(topic) : undefined;
      console.log(topicDef ? commandHelp(topicDef) : usageText());
      return 0;
    }
    case 'version':
      console.log(VERSION);
      return 0;

    case 'open': {
      const parsed = parseCommandArgs(def, commandArgv);
      const url = parsed.positionals[0];
      const flagArgs = flagsToArgv(parsed, ['headless', 'viewport', 'channel']);
      // Default idle policy: never for headed, 30 min for headless.
      const idleTimeout = 'idle-timeout' in parsed.flags
        ? String(parsed.flags['idle-timeout'])
        : (parsed.flags.headless === true ? '1800' : '0');
      const launchArgs = [...flagArgs, `--idle-timeout=${idleTimeout}`];
      await startSession({ session: global.session, url, launchArgs });
      if (url) {
        const result = await runInSession(global.session, ['snapshot']);
        return printResult(result, global);
      }
      console.log(`Session "${global.session}" started. Use: playautomation goto <url> -s ${global.session}`);
      return 0;
    }

    case 'list': {
      const sessions = listSessions();
      if (!sessions.length) {
        console.log('No sessions.');
        return 0;
      }
      const lines: string[] = [];
      for (const { info } of sessions) {
        const alive = await canConnect(info.socketPath, 1000);
        lines.push(`${alive ? '●' : '○'} ${info.name}  pid=${info.pid}  ${info.socketPath}${alive ? '' : '  (dead)'}`);
      }
      console.log(lines.join('\n'));
      return 0;
    }

    case 'kill':
      await killSession(global.session);
      console.log(`Killed session "${global.session}".`);
      return 0;

    case 'serve': {
      const parsed = parseCommandArgs(def, commandArgv);
      const root = path.resolve(parsed.positionals[0] ?? 'test-pages');
      const port = parseInt(String(parsed.flags.port), 10) || 8931;
      const server = await startTestServer(root, port);
      console.log(`Serving ${root} at ${server.url('/')}`);
      await new Promise(() => {}); // until interrupted
      return 0;
    }

    case 'run': {
      const parsed = parseCommandArgs(def, commandArgv);
      const summary = await runFlows(parsed.positionals, {
        casesFlag: parsed.flags.cases ? String(parsed.flags.cases) : undefined,
        headed: parsed.flags.headed === true,
        reportBase: parsed.flags.report ? String(parsed.flags.report) : undefined,
        timeoutMs: parseInt(String(parsed.flags.timeout), 10) || 10_000,
      });
      const reportFile = path.join(summary.resultsDir, 'report.html');
      console.log(`\n${summary.passed}/${summary.totalCases} cases passed`);
      console.log(`Results: ${summary.resultsDir}`);
      console.log(`Report: ${reportFile}`);
      return summary.failed > 0 ? 1 : 0;
    }

    case 'cases': {
      const parsed = parseCommandArgs(def, commandArgv);
      const flowFile = path.resolve(parsed.positionals[0]);
      const flow = loadFlow(flowFile);
      const { cases, source } = loadCasesForFlow(flowFile, flow, undefined);
      if (parsed.flags.save === true && source === 'rules') {
        const file = writeCasesFile(flowFile, cases);
        console.log(`Saved ${cases.length} cases to ${file}`);
      }
      console.log(`Flow "${flow.name}" — ${cases.length} case(s) [source: ${source}]`);
      for (const testCase of cases) {
        const inputs = Object.entries(testCase.inputs).map(([k, v]) => `${k}=${JSON.stringify(v)}`).join(' ');
        console.log(`  - ${testCase.name}${inputs ? `  (${inputs})` : ''}${testCase.expectFail ? '  [expect fail]' : ''}`);
      }
      if (source === 'rules')
        console.log('\n(Edit the generated file or author <flow>.cases.yaml for custom cases.)');
      return 0;
    }

    case 'report': {
      const parsed = parseCommandArgs(def, commandArgv);
      const resultsDir = path.resolve(parsed.positionals[0]);
      const { writeHtmlReport } = await import('./report/htmlReport.js');
      const fs = await import('node:fs');
      const summary = JSON.parse(fs.readFileSync(path.join(resultsDir, 'results.json'), 'utf8'));
      const file = writeHtmlReport(resultsDir, summary);
      console.log(`Report: ${file}`);
      return 0;
    }

    default:
      console.error(`Command "${def.name}" is not implemented yet.`);
      return 2;
  }
}

function flagsToArgv(parsed: { flags: Record<string, string | boolean | undefined> }, names: string[]): string[] {
  const out: string[] = [];
  for (const name of names) {
    const value = parsed.flags[name];
    if (value === true)
      out.push(`--${name}`);
    else if (typeof value === 'string' && value !== '')
      out.push(`--${name}=${value}`);
  }
  return out;
}

function printResult(result: { isError: boolean; text: string; meta?: Record<string, unknown> }, global: GlobalOptions): number {
  if (global.json)
    console.log(JSON.stringify({ ok: !result.isError, ...result }, null, 2));
  else if (result.text)
    console.log(result.text);
  if (result.isError)
    console.error(`\nError: ${result.text}`);
  return result.isError ? 1 : 0;
}

void main().then(code => {
  // Set exitCode rather than process.exit so stdout can flush.
  process.exitCode = code;
}, error => {
  console.error(`Error: ${(error as Error).message ?? error}`);
  process.exitCode = 1;
});
