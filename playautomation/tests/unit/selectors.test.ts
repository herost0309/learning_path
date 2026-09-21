import { describe, expect, it } from 'vitest';
import { locatorExpr, parseSelector } from '../../lib/runner/selectors.js';

describe('parseSelector', () => {
  it('parses testid selectors', () => {
    const parsed = parseSelector('testid="login-email"');
    expect(parsed).toMatchObject({ engine: 'testid', value: 'login-email' });
    expect(parsed.nth).toBeUndefined();
  });

  it('parses role selectors with names (including quotes and escapes)', () => {
    const parsed = parseSelector('role=button[name="Sign \\"in\\""]');
    expect(parsed).toMatchObject({ engine: 'role', role: 'button', value: 'Sign "in"' });
  });

  it('parses role selectors without names', () => {
    expect(parseSelector('role=heading')).toMatchObject({ engine: 'role', role: 'heading' });
  });

  it('parses text, placeholder, label and css engines', () => {
    expect(parseSelector('text=Welcome')).toMatchObject({ engine: 'text', value: 'Welcome' });
    expect(parseSelector('placeholder="you@example.com"')).toMatchObject({ engine: 'placeholder', value: 'you@example.com' });
    expect(parseSelector('label="Email"')).toMatchObject({ engine: 'label', value: 'Email' });
    expect(parseSelector('css=#submit')).toMatchObject({ engine: 'css', value: '#submit' });
  });

  it('parses bare selectors as css', () => {
    expect(parseSelector('#main .btn')).toMatchObject({ engine: 'css', value: '#main .btn' });
  });

  it('parses nth suffixes', () => {
    expect(parseSelector('css=li.item >> nth=2')).toMatchObject({ engine: 'css', value: 'li.item', nth: 2 });
    expect(parseSelector('role=listitem >> nth=0')).toMatchObject({ engine: 'role', role: 'listitem', nth: 0 });
  });

  it('does not treat >> inside nth value as another split', () => {
    expect(parseSelector('text="a >> b"')).toMatchObject({ engine: 'text', value: 'a >> b' });
  });
});

describe('locatorExpr', () => {
  it('maps engines to getBy* calls', () => {
    expect(locatorExpr('testid="x"')).toBe('page.getByTestId("x")');
    expect(locatorExpr('role=button[name="Go"]')).toBe('page.getByRole("button", { name: "Go" })');
    expect(locatorExpr('role=heading')).toBe('page.getByRole("heading")');
    expect(locatorExpr('placeholder="Email"')).toBe('page.getByPlaceholder("Email")');
    expect(locatorExpr('label="Email"')).toBe('page.getByLabel("Email")');
    expect(locatorExpr('text="Hi"')).toBe('page.getByText("Hi")');
    expect(locatorExpr('css=#id')).toBe('page.locator("#id")');
  });

  it('appends nth', () => {
    expect(locatorExpr('css=li >> nth=3')).toBe('page.locator("li").nth(3)');
  });
});
