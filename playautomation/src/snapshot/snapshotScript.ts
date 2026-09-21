// The in-page snapshot script. esbuild bundles this file into a single IIFE
// (lib/snapshot-script.js) which the daemon installs via context.addInitScript
// and re-evaluates idempotently before each capture.
//
// Patterns ported (simplified) from playwright-main:
//   packages/injected/src/ariaSnapshot.ts     — ref generation + aria tree shape
//   packages/injected/src/roleSelectorEngine.ts + roleUtils.ts — implicit roles, accessible names
//   packages/injected/src/selectorGenerator.ts — scored selector candidates + uniqueness verification
//
// All state lives on window.__pa__ in the page's main world (public playwright API only).

(() => {
  const w = window as any;
  if (w.__pa__)
    return;

  let lastRef = 0;
  const refs = new Map<string, Element>();
  const elementsToNodes = new Map<Element, any>();
  let testIdAttribute = 'data-testid';

  // ---------- roles (simplified from roleUtils.ts) ----------
  const kRoleFromTag: Record<string, string> = {
    a: 'link', area: 'link', article: 'article', aside: 'complementary',
    button: 'button', caption: 'caption', datalist: 'listbox', dd: 'definition',
    details: 'group', dialog: 'dialog', dt: 'term', fieldset: 'group',
    footer: 'contentinfo', form: 'form', h1: 'heading', h2: 'heading', h3: 'heading',
    h4: 'heading', h5: 'heading', h6: 'heading', header: 'banner', hr: 'separator',
    img: 'img', input: 'textbox', li: 'listitem', main: 'main', menu: 'list',
    meter: 'meter', nav: 'navigation', ol: 'list', option: 'option', optgroup: 'group',
    output: 'status', progress: 'progressbar', search: 'search', section: 'region',
    select: 'combobox', table: 'table', tbody: 'rowgroup', td: 'cell', tfoot: 'rowgroup',
    th: 'columnheader', thead: 'rowgroup', tr: 'row', ul: 'list', textarea: 'textbox',
  };

  function getRole(el: Element): string | null {
    const explicit = el.getAttribute('role');
    if (explicit) {
      const first = explicit.trim().split(/\s+/)[0];
      if (first)
        return first;
    }
    const tag = el.tagName.toLowerCase();
    if (tag === 'input')
      return roleFromInputType((el as HTMLInputElement).type);
    if (tag === 'a')
      return el.getAttribute('href') !== null ? 'link' : 'generic';
    return kRoleFromTag[tag] ?? null;
  }

  function roleFromInputType(type: string): string {
    switch (type) {
      case 'button': case 'submit': case 'reset': case 'image': return 'button';
      case 'checkbox': return 'checkbox';
      case 'radio': return 'radio';
      case 'range': return 'slider';
      case 'number': return 'spinbutton';
      case 'search': return 'searchbox';
      case 'email': case 'tel': case 'text': case 'url': case 'password': return 'textbox';
      default: return 'textbox';
    }
  }

  const kInteractiveRoles = new Set([
    'link', 'button', 'textbox', 'searchbox', 'spinbutton', 'checkbox', 'radio',
    'switch', 'combobox', 'listbox', 'option', 'slider', 'tab', 'menuitem',
    'menuitemcheckbox', 'menuitemradio', 'treeitem',
  ]);
  const kRefEligibleRoles = new Set([...kInteractiveRoles, 'heading', 'img', 'separator', 'progressbar', 'meter', 'status', 'alert', 'dialog']);

  // ---------- accessible name (simplified from roleUtils.ts accname) ----------
  function accessibleName(el: Element): string | undefined {
    const labelledBy = el.getAttribute('aria-labelledby');
    if (labelledBy) {
      const parts = labelledBy.split(/\s+/).map(id => document.getElementById(id)?.textContent ?? '').filter(Boolean);
      if (parts.length)
        return normalizeText(parts.join(' '));
    }
    const ariaLabel = el.getAttribute('aria-label');
    if (ariaLabel !== null && ariaLabel.trim() !== '')
      return normalizeText(ariaLabel);
    const alt = el.getAttribute('alt');
    if (alt !== null && el.tagName.toLowerCase() === 'img' && alt.trim() !== '')
      return normalizeText(alt);
    const id = el.getAttribute('id');
    if (id) {
      const label = document.querySelector(`label[for="${cssEscape(id)}"]`);
      if (label?.textContent)
        return normalizeText(label.textContent);
    }
    const enclosingLabel = el.closest('label');
    if (enclosingLabel?.textContent)
      return normalizeText(enclosingLabel.textContent);
    const title = el.getAttribute('title');
    if (title !== null && title.trim() !== '')
      return normalizeText(title);
    const placeholder = (el as HTMLInputElement).placeholder;
    if (placeholder)
      return undefined; // placeholder is its own candidate engine, not the name
    // Leaf text content as last resort.
    const children = el.children;
    if (children.length === 0 || kInteractiveRoles.has(getRole(el) ?? '')) {
      const ownText = Array.from(el.childNodes)
        .filter(n => n.nodeType === Node.TEXT_NODE)
        .map(n => n.textContent ?? '')
        .join('');
      if (ownText.trim() !== '')
        return normalizeText(ownText);
    }
    return undefined;
  }

  function normalizeText(text: string): string {
    const trimmed = text.replace(/\s+/g, ' ').trim();
    return trimmed.length > 300 ? trimmed.slice(0, 300) + '…' : trimmed;
  }

  // ---------- visibility (heuristic from domUtils.ts) ----------
  function isVisible(el: Element): boolean {
    if (!(el instanceof HTMLElement) && !(el instanceof SVGElement))
      return false;
    const style = window.getComputedStyle(el as Element as HTMLElement);
    if (style.visibility === 'hidden' || style.display === 'none')
      return false;
    const rect = (el as Element as HTMLElement).getBoundingClientRect();
    return rect.width > 0 && rect.height > 0;
  }

  const kSkipTags = new Set(['script', 'style', 'noscript', 'template', 'br', 'head', 'title', 'meta', 'link']);

  // ---------- capture ----------
  function capture(opts: { full?: boolean } = {}): any {
    const tree: any[] = [];
    walk(document.body ?? document.documentElement, tree, opts.full === true, 0);
    return {
      url: location.href,
      title: document.title,
      timestamp: Date.now(),
      tree,
    };
  }

  const kMaxNodes = 5000;

  function walk(el: Element, out: any[], full: boolean, depth: number): void {
    if (out.length > kMaxNodes || depth > 40)
      return;
    for (const child of el.children) {
      const tag = child.tagName.toLowerCase();
      if (kSkipTags.has(tag))
        continue;
      const visible = isVisible(child);
      const role = getRole(child);
      if (!visible && !full) {
        // Invisible: skip the node, but still descend in case children re-appear via CSS.
        continue;
      }
      if (!role && !full) {
        // Container without semantics: descend without emitting a node.
        walk(child, out, full, depth + 1);
        continue;
      }
      const name = accessibleName(child);
      const node: any = { role };
      if (name)
        node.name = name;
      const refEligible = full || (role !== null && kRefEligibleRoles.has(role)) ||
        child.getAttribute('onclick') !== null || child.hasAttribute('tabindex');
      if (refEligible && (role !== null || full)) {
        const ref = 'e' + ++lastRef;
        refs.set(ref, child);
        elementsToNodes.set(child, node);
        node.ref = ref;
      }
      const value = fieldValue(child);
      if (value !== undefined)
        node.value = value;
      if ((child as HTMLInputElement).disabled || child.getAttribute('aria-disabled') === 'true')
        node.disabled = true;
      if (role === 'link')
        node.href = (child as HTMLAnchorElement).href ?? undefined;
      if ((child as HTMLInputElement).checked)
        node.checked = true;

      const children: any[] = [];
      walk(child, children, full, depth + 1);
      if (children.length)
        node.children = children;
      out.push(node);
    }
  }

  function fieldValue(el: Element): string | undefined {
    const anyEl = el as any;
    if (typeof anyEl.value === 'string' && anyEl.value !== '' &&
        ['textbox', 'searchbox', 'spinbutton', 'combobox', 'slider'].includes(getRole(el) ?? '')) {
      // Never expose password values in snapshots (they are echoed to terminals
      // and agent transcripts); show a mask so the field state stays visible.
      if (el instanceof HTMLInputElement && el.type === 'password')
        return '••••••••';
      const v = anyEl.value;
      return v.length > 200 ? v.slice(0, 200) + '…' : v;
    }
    return undefined;
  }

  // ---------- ref resolution ----------
  function getElement(ref: string): Element {
    const el = refs.get(ref);
    if (!el)
      throw new Error(`__pa_unknown_ref__: Ref ${ref} not found in this page. Take a new snapshot.`);
    if (!el.isConnected)
      throw new Error(`__pa_stale_ref__: Ref ${ref} is stale (element removed from DOM). Take a new snapshot.`);
    return el;
  }

  function describe(ref: string): { role: string; name?: string } {
    const el = getElement(ref);
    const node = elementsToNodes.get(el);
    return { role: node?.role ?? getRole(el) ?? 'generic', name: node?.name };
  }

  // ---------- selector generator (simplified from selectorGenerator.ts) ----------
  // Candidates in score order; each verified by actually querying the document.
  const kScore = { testid: 1, role: 100, placeholder: 120, label: 140, alt: 160, text: 180, css: 500, nth: 10000 };

  function genSelector(ref: string): any {
    const target = getElement(ref);
    const candidates: Array<{ engine: string; score: number; selector: string; matches: () => Element[] }> = [];

    const testid = target.getAttribute(testIdAttribute);
    if (testid)
      candidates.push({
        engine: 'testid', score: kScore.testid,
        selector: `testid=${quote(testid)}`,
        matches: () => Array.from(document.querySelectorAll(`[${testIdAttribute}=${cssQuote(testid)}]`)),
      });

    const role = getRole(target);
    const name = accessibleName(target);
    if (role && name)
      candidates.push({
        engine: 'role', score: kScore.role,
        selector: `role=${role}[name=${quote(name)}]`,
        matches: () => allInteractable().filter(el => getRole(el) === role && nameMatches(el, name)),
      });

    const placeholder = (target as HTMLInputElement).placeholder;
    if (placeholder)
      candidates.push({
        engine: 'placeholder', score: kScore.placeholder,
        selector: `placeholder=${quote(placeholder)}`,
        matches: () => allElements().filter(el => (el as HTMLInputElement).placeholder === placeholder),
      });

    const labelText = labelFor(target);
    if (labelText)
      candidates.push({
        engine: 'label', score: kScore.label,
        selector: `label=${quote(labelText)}`,
        matches: () => allElements().filter(el => labelFor(el) === labelText),
      });

    const ownText = ownTextOf(target);
    if (ownText && ownText.length <= 80)
      candidates.push({
        engine: 'text', score: kScore.text + (ownText === ownText.trim() ? 0 : 5),
        selector: `text=${quote(ownText)}`,
        matches: () => allElements().filter(el => ownTextOf(el) === ownText),
      });

    const css = cssSelectorFor(target);
    if (css)
      candidates.push({
        engine: 'css', score: kScore.css,
        selector: `css=${css}`,
        matches: () => Array.from(document.querySelectorAll(css)),
      });

    candidates.sort((a, b) => a.score - b.score);
    for (const candidate of candidates) {
      let matches: Element[];
      try {
        matches = candidate.matches();
      } catch {
        continue;
      }
      if (matches.length === 1 && matches[0] === target)
        return { selector: candidate.selector, engine: candidate.engine, css: css ?? '', ambiguous: false };
      const index = matches.indexOf(target);
      if (index >= 0 && index < 5)
        return { selector: `${candidate.selector} >> nth=${index}`, engine: candidate.engine, css: css ?? '', ambiguous: true };
    }
    // Last resort: chain nth-of-type css.
    const fallback = cssFallbackChain(target);
    return { selector: `css=${fallback}`, engine: 'css', css: fallback, ambiguous: true };
  }

  function allElements(): Element[] {
    return Array.from(document.querySelectorAll('*')).slice(0, 20000);
  }
  function allInteractable(): Element[] {
    return allElements().filter(isVisible);
  }

  function nameMatches(el: Element, expected: string): boolean {
    const actual = accessibleName(el);
    if (!actual)
      return false;
    return actual.toLowerCase().includes(expected.toLowerCase());
  }

  function labelFor(el: Element): string | undefined {
    const id = el.getAttribute('id');
    if (id) {
      const label = document.querySelector(`label[for="${cssEscape(id)}"]`);
      if (label?.textContent?.trim())
        return normalizeText(label.textContent);
    }
    const enclosing = el.closest('label');
    if (enclosing?.textContent?.trim())
      return normalizeText(enclosing.textContent);
    return undefined;
  }

  function ownTextOf(el: Element): string | undefined {
    const text = (el.textContent ?? '').replace(/\s+/g, ' ').trim();
    if (!text || text.length > 80)
      return undefined;
    return text;
  }

  function cssSelectorFor(el: Element): string | undefined {
    const id = el.getAttribute('id');
    if (id && !isGuidLike(id) && document.querySelectorAll(`#${cssEscape(id)}`).length === 1)
      return `#${cssEscape(id)}`;
    const tag = el.tagName.toLowerCase();
    const classes = Array.from(el.classList).slice(0, 3);
    if (classes.length)
      return `${tag}.${classes.map(cssEscape).join('.')}`;
    if (el.getAttribute(testIdAttribute))
      return `[${testIdAttribute}=${cssQuote(el.getAttribute(testIdAttribute)!)}]`;
    const name = el.getAttribute('name');
    if (name)
      return `${tag}[name=${cssQuote(name)}]`;
    return tag;
  }

  function cssFallbackChain(el: Element): string {
    const parts: string[] = [];
    let current: Element | null = el;
    while (current && current !== document.body) {
      const parent: Element | null = current.parentElement;
      if (!parent) {
        parts.unshift(`${current.tagName.toLowerCase()}:nth-of-type(1)`);
        break;
      }
      const siblings = Array.from(parent.children).filter(c => c.tagName === current!.tagName);
      const index = siblings.indexOf(current) + 1;
      parts.unshift(`${current.tagName.toLowerCase()}:nth-of-type(${index})`);
      current = parent;
      const id = current.getAttribute('id');
      if (id && !isGuidLike(id) && document.querySelectorAll(`#${cssEscape(id)}`).length === 1) {
        parts.unshift(`#${cssEscape(id)}`);
        break;
      }
    }
    return parts.join(' > ');
  }

  function isGuidLike(value: string): boolean {
    return value.length >= 32 && /^[0-9a-fA-F-]+$/.test(value);
  }

  function quote(value: string): string {
    return '"' + value.replace(/(["\\])/g, '\\$1') + '"';
  }
  function cssQuote(value: string): string {
    return '"' + value.replace(/(["\\])/g, '\\$1') + '"';
  }
  function cssEscape(value: string): string {
    return (window as any).CSS?.escape ? (window as any).CSS.escape(value) : value.replace(/([^a-zA-Z0-9_-])/g, '\\$1');
  }

  // ---------- find (search the live DOM, same rules as capture) ----------
  function find(text: string): any[] {
    const needle = text.toLowerCase();
    const results: any[] = [];
    for (const el of allElements()) {
      if (!isVisible(el))
        continue;
      const role = getRole(el);
      if (!role || !kRefEligibleRoles.has(role))
        continue;
      const name = accessibleName(el);
      const own = ownTextOf(el);
      const matched = (name && name.toLowerCase().includes(needle)) || (own && own.toLowerCase().includes(needle));
      if (!matched)
        continue;
      let ref: string | undefined = undefined;
      for (const [r, element] of refs)
        if (element === el) { ref = r; break; }
      if (!ref) {
        ref = 'e' + ++lastRef;
        refs.set(ref, el);
      }
      results.push({ ref, role, name: name ?? own, selector: genSelector(ref).selector });
      if (results.length >= 20)
        break;
    }
    return results;
  }

  w.__pa__ = {
    capture,
    getElement,
    describe,
    genSelector,
    find,
    setTestIdAttribute: (attr: string) => { testIdAttribute = attr; },
    ping: () => ({ v: 1 }),
  };
})();
