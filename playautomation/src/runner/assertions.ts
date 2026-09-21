// Replay-side assertion evaluation with expect-style polling.
// Deliberately hand-rolled (no @playwright/test dependency): locator.waitFor
// already polls for visible/hidden; text/url ride the same primitives.

import type { Page } from 'playwright-core';
import type { FlowStep } from '../flow.js';
import { locate } from './selectors.js';

export async function runAssertion(page: Page, assert: NonNullable<FlowStep['assert']>, timeoutMs: number): Promise<void> {
  switch (assert.kind) {
    case 'visible':
    case 'hidden': {
      if (!assert.target)
        throw new Error('assert visible/hidden requires a selector');
      await locate(page, assert.target).first().waitFor({ state: assert.kind, timeout: timeoutMs });
      return;
    }
    case 'text': {
      await page.getByText(assert.expected ?? '').first().waitFor({ timeout: timeoutMs });
      return;
    }
    case 'url': {
      await page.waitForURL(url => url.href.includes(assert.expected ?? ''), { timeout: timeoutMs });
      return;
    }
    case 'value': {
      if (!assert.target)
        throw new Error('assert value requires a selector');
      const locator = locate(page, assert.target).first();
      await locator.waitFor({ timeout: timeoutMs });
      const deadline = Date.now() + timeoutMs;
      while (true) {
        const actual = await locator.inputValue();
        if (actual === assert.expected)
          return;
        if (Date.now() > deadline)
          throw new Error(`assert value failed: expected ${JSON.stringify(assert.expected)}, got ${JSON.stringify(actual)}`);
        await page.waitForTimeout(100);
      }
    }
    default:
      throw new Error(`Unknown assert kind: ${(assert as any).kind}`);
  }
}
