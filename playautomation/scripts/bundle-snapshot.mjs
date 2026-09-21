// Bundles the in-page snapshot script into a single injectable IIFE string.
// The daemon evaluates this text in the page (context.addInitScript + idempotent page.evaluate).
import { build } from 'esbuild';
import { mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('..', import.meta.url));
mkdirSync(`${root}/lib`, { recursive: true });

await build({
  entryPoints: [`${root}/src/snapshot/snapshotScript.ts`],
  bundle: true,
  format: 'iife',
  target: 'es2022',
  outfile: `${root}/lib/snapshot-script.js`,
  minify: false,
  logLevel: 'info',
});
