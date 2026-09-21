# playautomation — agent instructions

When the user asks you to automate, explore, or test a website with `playautomation`:

1. Read `docs/agent-guide.md` first — it defines the snapshot → ref → act loop and all commands.
2. The user provides step specs in `flows/<name>.yaml` (steps + inputs + asserts in prose).
3. Drive the browser only through the `playautomation` CLI (build first: `npm run build` in `playautomation/`).
4. Always use refs from the latest snapshot; on `stale`/`not found` errors, take a new snapshot.
5. Record `assert` commands for every outcome worth verifying.
6. Finish with `save --spec flows/<name>.yaml`, author `flows/<name>.cases.yaml` variants (including `expectFail: true` cases), and run `playautomation run flows/<name>.flow.json`.
7. Report results from `results/<runId>/report.html`.
