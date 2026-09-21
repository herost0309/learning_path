// Canonical selector DSL shared by codegen and the replay runner:
//   testid="login" | role=button[name="Sign in"] | placeholder="Email"
//   label="Email"  | text="Sign in"              | css=#id
// with optional ` >> nth=<index>` suffix.
// The runner maps engines to playwright getBy* APIs explicitly rather than
// relying on playwright-internal engine strings.

import type { Locator, Page } from 'playwright-core';

export type SelectorEngine = 'testid' | 'role' | 'placeholder' | 'label' | 'text' | 'css';

export interface ParsedSelector {
  engine: SelectorEngine;
  value: string; // css / text / placeholder / label / testid value
  role?: string; // for engine === 'role'
  nth?: number;
}

const kEngines: SelectorEngine[] = ['testid', 'role', 'placeholder', 'label', 'text', 'css'];

function unquote(text: string): { value: string; rest: string } {
  if (!text.startsWith('"'))
    return { value: text.trim(), rest: '' };
  let out = '';
  for (let i = 1; i < text.length; i++) {
    const ch = text[i];
    if (ch === '\\' && i + 1 < text.length) {
      out += text[i + 1];
      i++;
      continue;
    }
    if (ch === '"')
      return { value: out, rest: text.slice(i + 1) };
    out += ch;
  }
  return { value: out, rest: '' };
}

export function parseSelector(selector: string): ParsedSelector {
  let text = selector.trim();
  let nth: number | undefined;
  const parts = text.split(' >> ');
  if (parts.length > 1) {
    const last = parts[parts.length - 1].trim();
    if (/^nth=\d+$/.test(last)) {
      nth = parseInt(last.slice(4), 10);
      parts.pop();
      text = parts.join(' >> ').trim();
    }
  }

  const match = text.match(new RegExp(`^(${kEngines.join('|')})=`, 'i'));
  if (!match)
    return { engine: 'css', value: text, nth }; // bare selector = css
  const engine = match[1].toLowerCase() as SelectorEngine;
  const body = text.slice(match[0].length);

  if (engine === 'role') {
    const roleMatch = body.match(/^([\w-]+)(\[([\s\S]*)\])?$/);
    if (!roleMatch)
      throw new Error(`Cannot parse role selector: ${selector}`);
    const parsed: ParsedSelector = { engine, value: '', role: roleMatch[1], nth };
    const attrs = roleMatch[3] ?? '';
    const nameMatch = attrs.match(/^name\s*=\s*("[\s\S]*"|[\w-]+)\s*(exact)?$/);
    if (nameMatch)
      parsed.value = unquote(nameMatch[1]).value;
    return parsed;
  }
  return { engine, value: unquote(body).value, nth };
}

export function locate(page: Page, selector: string): Locator {
  const parsed = parseSelector(selector);
  let locator: Locator;
  switch (parsed.engine) {
    case 'testid':
      locator = page.getByTestId(parsed.value);
      break;
    case 'role':
      locator = page.getByRole(parsed.role as any, parsed.value ? { name: parsed.value } : undefined);
      break;
    case 'placeholder':
      locator = page.getByPlaceholder(parsed.value);
      break;
    case 'label':
      locator = page.getByLabel(parsed.value);
      break;
    case 'text':
      locator = page.getByText(parsed.value);
      break;
    case 'css':
      locator = page.locator(parsed.value);
      break;
  }
  if (parsed.nth !== undefined)
    locator = locator.nth(parsed.nth);
  return locator;
}

// Codegen counterpart: a static TS expression for the same selector.
export function locatorExpr(selector: string, pageVar = 'page'): string {
  const parsed = parseSelector(selector);
  const js = (s: string) => JSON.stringify(s);
  let expr: string;
  switch (parsed.engine) {
    case 'testid':
      expr = `${pageVar}.getByTestId(${js(parsed.value)})`;
      break;
    case 'role':
      expr = `${pageVar}.getByRole(${js(parsed.role ?? '')}${parsed.value ? `, { name: ${js(parsed.value)} }` : ''})`;
      break;
    case 'placeholder':
      expr = `${pageVar}.getByPlaceholder(${js(parsed.value)})`;
      break;
    case 'label':
      expr = `${pageVar}.getByLabel(${js(parsed.value)})`;
      break;
    case 'text':
      expr = `${pageVar}.getByText(${js(parsed.value)})`;
      break;
    case 'css':
      expr = `${pageVar}.locator(${js(parsed.value)})`;
      break;
  }
  if (parsed.nth !== undefined)
    expr += `.nth(${parsed.nth})`;
  return expr;
}
