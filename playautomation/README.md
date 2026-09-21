# playautomation

Browser automation where **high-level steps meet a deterministic engine**:
you describe what to do in YAML, an AI agent (e.g. [opencode](https://opencode.ai))
explores the live browser through the `playautomation` CLI, and the tool compiles
everything into **replayable flows** with a **case matrix** and an HTML report.

```
flows/checkout.yaml ──agent explores──▶ live browser ──save──▶ checkout.flow.json
                                                                        │
                                              inputs + case variants ──▶ playautomation run
                                                                        │
                                                            results/<runId>/report.html
```

Built on [playwright-core](https://github.com/microsoft/playwright) (public APIs,
Chromium-first) and patterned after Playwright's own recorder, codegen and
cli-daemon architectures.

## Quickstart

```bash
cd playautomation
npm install
npm run build

# explore (headful) — the agent loop
node lib/cli.js open https://example.com
node lib/cli.js snapshot            # YAML accessibility tree with refs (e1, e2, ...)
node lib/cli.js click e5            # act by ref; response includes a fresh snapshot
node lib/cli.js fill e3 "hello"
node lib/cli.js assert text "Example"

# compile the recorded actions into a replayable flow
node lib/cli.js save --name demo --out flows

# replay deterministically (headless), with the case matrix
node lib/cli.js run flows/demo.flow.json
```

Global options: `-s <session>` session name · `--json` machine-readable output.

## Demos

Three runnable demos (explore loop, form controls, record → replay with case
matrix) live in [demos/](demos/README.md):

```bash
npm run demo:explore   # snapshot → ref → act → assert, the agent core loop
npm run demo:form      # select/check/fill + assert value
npm run demo:replay    # full lifecycle: record → save → cases → run → report
```

## How it works

- **CLI + daemon** (like `playwright cli`): `open` spawns a detached daemon that
  owns the browser; every later command is a one-shot RPC over a named pipe /
  unix socket. Sessions are keyed per workspace + name and survive across
  invocations.
- **Snapshot + refs** (like playwright-mcp): an injected script captures an
  accessibility-tree snapshot with element refs; the agent navigates by
  `snapshot → ref → action`. Actions run through real playwright
  actionability/auto-waiting.
- **Stable selectors**: every recorded action is resolved to a scored, verified
  selector (`testid` > `role+name` > `placeholder` > `label` > `text` > `css`),
  so saved flows never depend on ephemeral refs.
- **Flows**: `save` compiles the action log into `flows/<name>.flow.json`
  (+ a standalone `.ts` view). Input values matching the YAML spec become
  `{{templates}}`.
- **Cases**: `flows/<name>.cases.yaml` lists input variants (`expectFail: true`
  for cases that should fail). `run` executes the matrix headless with
  per-step screenshots and writes a self-contained HTML report.

## For agents

See [docs/agent-guide.md](docs/agent-guide.md) — the complete agent playbook
(loop, ref rules, case authoring, error semantics). [AGENTS.md](AGENTS.md) +
[docs/opencode.json](docs/opencode.json) wire it into opencode.

## Development

```bash
npm run build        # tsc + esbuild (in-page snapshot script bundle)
npm test             # unit + e2e (e2e drives the real daemon + browser)
npm run test:unit
npm run test:e2e
```

Browser: uses bundled Chromium if installed (`npx playwright install chromium`),
otherwise falls back to system Chrome, then Edge.

Layout: `src/snapshot/` in-page script · `src/backend/` daemon tools ·
`src/daemon/` daemon + backend dispatch · `src/client/` session client +
registry · `src/recorder/` action log → flow → TS codegen · `src/runner/`
replay + cases + assertions · `src/report/` HTML report.
