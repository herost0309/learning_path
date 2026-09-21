# playautomation — Agent Guide

This guide teaches an AI agent (opencode or similar) how to drive a browser through
the `playautomation` CLI: explore a page, expand high-level steps into concrete
actions, record assertions, save a replayable flow, and generate a case matrix.

## The core loop

Every browser interaction follows this pattern:

```
snapshot → find the element (ref e123) → act on it → read the fresh snapshot returned by the action
```

1. `playautomation open <url>` — starts a session (spawns the daemon) and prints the first snapshot.
2. `playautomation snapshot` — re-captures the page as a YAML accessibility tree. Each interactive element has a `ref` (`e7`).
3. Act using refs: `click e7`, `fill e7 "text"`, `select e7 value`, `check e7`, `press Enter`, `hover e7`.
4. Every action response contains: a summary line, the stable selector, a generated TS code line, and a **fresh snapshot**. Refs change after every capture — always use refs from the *latest* snapshot.
5. `assert` to verify outcomes (recorded into the flow): `assert text "Welcome"`, `assert visible <ref|selector>`, `assert url dashboard`, `assert value <ref|selector> <expected>`.
6. `save --name <flow> --spec <yaml> --out flows` — compiles everything into `flows/<flow>.flow.json` + `<flow>.ts`.

## Reference rules

- Refs (`e12`) are only valid until the page changes; after navigation the map resets completely.
- If an action fails with `stale` / `not found`, take a new `snapshot` and retry with fresh refs — never reuse refs across navigations.
- You may also act by selector instead of ref: `click "testid=\"submit\""`, `click "css=#submit"`, `assert visible "css=h1"`.
- `find <text>` searches the current snapshot without acting — cheaper than a full snapshot when you know the label.
- Consecutive `fill` calls on the same element merge automatically in the saved flow (typing corrections are fine).

## Executing a step spec (your main task)

The user provides a YAML spec:

```yaml
name: checkout
baseUrl: https://shop.example.com
inputs:
  email: demo@test.shop
  password: demo123
steps:
  - go to the login page
  - sign in with the demo credentials
  - add the first product to the cart
assert:
  - the cart shows 1 item
```

Your job: turn each prose step into concrete actions.

1. `playautomation open <baseUrl or step URL>`
2. For each step in order:
   - Read the latest snapshot.
   - Decide the smallest set of concrete actions (goto/fill/click/select/check/press/wait).
   - Use `find` to locate elements by visible text when the snapshot is large.
   - After actions that trigger navigation or async work, use `wait --text=...`, `wait --url=...` or another `snapshot` to confirm the outcome before moving on.
3. For each `assert:` in the spec, add matching `assert` commands (they must actually pass — retry with fresh snapshots if needed).
4. `playautomation save --name checkout --spec flows/checkout.yaml --out flows`
   The spec's `inputs` values you typed via `fill` become `{{templates}}` automatically.

## Exploring more cases (case matrix)

After saving a flow, explore input variations the user might care about:

- empty values for each text input
- boundary lengths (1 char, very long)
- invalid formats (bad email, letters in numeric fields)
- wrong credentials where auth is involved

Write them to `flows/<name>.cases.yaml`:

```yaml
cases:
  - name: happy-path
  - name: empty-email
    inputs:
      email: ""
    expectFail: true
  - name: wrong-password
    inputs:
      password: "wrong"
    expectFail: true
```

- `inputs` override the flow's inputs for that case.
- **Secrets**: input values (in flow.json or cases.yaml) may be `{{env:VAR_NAME}}`
  references — resolved from the environment at replay time, so passwords never
  need to be stored in flow files.
- `expectFail: true` marks a case that should fail (e.g. validation error) — the case passes when the flow fails.
- Explore interactively when unsure: drive the session with the variant input and observe the page's reaction before deciding the outcome.

## Deterministic replay

```
playautomation run flows/checkout.flow.json            # all cases, headless
playautomation run flows/checkout.flow.json --cases=none
playautomation run flows/checkout.flow.json --headed
playautomation cases flows/checkout.flow.json          # print the matrix
playautomation report <resultsDir>                     # regenerate report.html
```

`run` exits non-zero when any (non-expectFail) case fails, and writes
`results/<runId>/` with `results.json`, per-step screenshots and a self-contained
`report.html`.

## Session management

- `-s <name>` selects a session (default `default`). Use one session per flow to keep action logs clean.
- `playautomation list` — list sessions; `playautomation close -s <name>` — close cleanly; `playautomation kill -s <name>` — force-kill a stuck daemon.
- Sessions are headful by default during exploration (you can watch); `run` is headless by default.

## Full command reference

```
playautomation open [url] [--headless] [--viewport=WxH] [--channel=chrome|msedge]
playautomation goto <url>              # navigate + fresh snapshot
playautomation snapshot [--full]       # accessibility tree with refs
playautomation find <text>             # search current page, returns refs
playautomation click <ref|selector> [--force]
playautomation fill <ref|selector> <text>
playautomation select <ref|selector> <value...>
playautomation check|uncheck <ref|selector>
playautomation press <key>             # e.g. Enter, Control+a
playautomation hover <ref|selector>
playautomation wait <ms|selector> | --url=<glob> | --text=<text> [--timeout=ms]
playautomation screenshot [--path=...] [--full-page]
playautomation assert visible|hidden <ref|selector>
playautomation assert text <text>
playautomation assert url <substring>
playautomation assert value <ref|selector> <expected>
playautomation save [--name=] [--spec=] [--out=]
playautomation run <flow...> [--cases=file|none] [--headed] [--report=dir] [--timeout=ms]
playautomation cases <flow> [--save]
playautomation report <resultsDir>
playautomation list | close | kill | serve [dir] [--port=]
```

Add `--json` to any daemon command for machine-readable output
(`{ ok, isError, text, meta: { selector, code, snapshot, ... } }`).

## Error semantics

| Error | Meaning | Recovery |
|---|---|---|
| `Ref eN not found or stale` | element gone/never existed | `snapshot`, use fresh refs |
| `No element matches selector` | bad selector | `find` the right target |
| `Timed out ... waiting for` | element not actionable in time | check visibility; `wait` for the page to settle |
| `Too many arguments` | shell quoting | use `--` before literal values |
