// Tab: wraps a live page. Installs the snapshot script (init script + idempotent
// re-evaluate), captures snapshots, resolves refs to element handles.
// The ref→handle flow keeps playwright's actionability/auto-waiting intact:
// elementHandle.click()/fill() etc. — same two-layer idea as playwright's
// injected script + native input split (see playwright-main/packages/playwright-core/src/server/dom.ts).

import * as fs from 'node:fs';
import * as path from 'node:path';
import { fileURLToPath } from 'node:url';
import type { ElementHandle, Page } from 'playwright-core';
import type { PaSnapshot } from '../snapshot/types.js';

let cachedScript: string | null = null;

export function snapshotScriptText(): string {
  if (cachedScript !== null)
    return cachedScript;
  const file = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'snapshot-script.js');
  cachedScript = fs.readFileSync(file, 'utf8');
  return cachedScript;
}

export class RefError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'RefError';
  }
}

export class Tab {
  readonly page: Page;

  constructor(page: Page) {
    this.page = page;
  }

  // Idempotent: the bundled script itself no-ops when window.__pa__ exists.
  async ensureScript(): Promise<void> {
    await this.page.evaluate(snapshotScriptText());
  }

  async capture(opts: { full?: boolean } = {}): Promise<PaSnapshot> {
    await this.ensureScript();
    return await this.page.evaluate(({ full }) => (window as any).__pa__.capture({ full }), { full: opts.full === true }) as PaSnapshot;
  }

  async describe(ref: string): Promise<{ role: string; name?: string }> {
    await this.ensureScript();
    return await this.page.evaluate(r => (window as any).__pa__.describe(r), ref);
  }

  async genSelector(ref: string): Promise<{ selector: string; engine: string; css: string; ambiguous: boolean }> {
    await this.ensureScript();
    return await this.page.evaluate(r => (window as any).__pa__.genSelector(r), ref);
  }

  async find(text: string): Promise<Array<{ ref: string; role: string; name?: string; selector: string }>> {
    await this.ensureScript();
    return await this.page.evaluate(t => (window as any).__pa__.find(t), text);
  }

  async elementFor(target: string): Promise<ElementHandle<SVGElement | HTMLElement>> {
    await this.ensureScript();
    const isRef = /^(f\d+)?e\d+$/.test(target);
    let handle;
    try {
      handle = await this.page.evaluateHandle((target: string) => {
        // Refs (e123) resolve through the page's ref map; anything else is a CSS selector.
        if (/^(f\d+)?e\d+$/.test(target))
          return (window as any).__pa__.getElement(target);
        return document.querySelector(target) ?? null;
      }, target);
    } catch (error) {
      const message = String((error as Error)?.message ?? error);
      if (message.includes('__pa_unknown_ref__') || message.includes('__pa_stale_ref__'))
        throw new RefError(`Ref ${target} not found or stale — take a new snapshot.`);
      throw error;
    }
    const element = handle.asElement();
    if (!element) {
      await handle.dispose();
      if (isRef)
        throw new RefError(`Ref ${target} not found or stale — take a new snapshot.`);
      throw new Error(`No element matches selector: ${target}`);
    }
    return element as ElementHandle<SVGElement | HTMLElement>;
  }
}
