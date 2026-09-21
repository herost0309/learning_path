// Page-side observation script. This function is serialized and evaluated in
// the page by Playwright, so it must be fully self-contained (no imports,
// no closure captures). Mirrors BrowserSkill's `observe` in miniature:
// semantics-first listing of interactive elements, each with a unique CSS
// path the daemon later turns into a ref → locator binding.

export function collectObservation() {
  const INTERACTIVE =
    'a[href],button,input,select,textarea,summary,[contenteditable="true"],[onclick],[role="button"],[role="link"],[role="tab"],[role="checkbox"],[role="menuitem"],[role="switch"],[role="textbox"],[tabindex]:not([tabindex="-1"])';

  const isInteractive = (el) => el.matches(INTERACTIVE);

  // a11y nesting rule: an interactive element inside another interactive
  // element is presented by its outer control — skip the inner one.
  const isNested = (el) => {
    for (let p = el.parentElement; p; p = p.parentElement) {
      if (isInteractive(p)) return true;
    }
    return false;
  };

  const isVisible = (el) => {
    const s = getComputedStyle(el);
    if (s.visibility === "hidden" || s.display === "none") return false;
    const r = el.getBoundingClientRect();
    return r.width > 0 && r.height > 0;
  };

  const inViewport = (el) => {
    const r = el.getBoundingClientRect();
    return r.bottom > 0 && r.right > 0 && r.top < innerHeight && r.left < innerWidth;
  };

  const roleOf = (el) => {
    const explicit = el.getAttribute("role");
    if (explicit) return explicit;
    const tag = el.tagName.toLowerCase();
    if (tag === "a") return "link";
    if (tag === "button" || tag === "summary") return "button";
    if (tag === "select") return "combobox";
    if (tag === "textarea") return "textbox";
    if (tag === "input") {
      const t = (el.getAttribute("type") || "text").toLowerCase();
      if (t === "checkbox") return "checkbox";
      if (t === "radio") return "radio";
      if (t === "submit" || t === "button" || t === "reset") return "button";
      return "textbox";
    }
    if (el.isContentEditable) return "textbox";
    return tag;
  };

  const clean = (s, n = 70) => (s || "").replace(/\s+/g, " ").trim().slice(0, n);

  const nameOf = (el) => {
    const labelledBy = el.getAttribute("aria-labelledby");
    if (labelledBy) {
      const label = labelledBy
        .split(/\s+/)
        .map((id) => document.getElementById(id))
        .filter(Boolean)
        .map((n) => n.textContent)
        .join(" ");
      if (label.trim()) return clean(label);
    }
    const aria = el.getAttribute("aria-label");
    if (aria) return clean(aria);
    if (el.labels && el.labels.length) return clean(el.labels[0].textContent);
    const placeholder = el.getAttribute("placeholder");
    if (placeholder) return clean(placeholder);
    const title = el.getAttribute("title");
    if (title) return clean(title);
    if (el.value && el.type !== "password") return clean(String(el.value));
    const alt = el.getAttribute("alt");
    if (alt) return clean(alt);
    return clean(el.textContent);
  };

  // Unique CSS path (tag + nth-of-type chain). Good enough for a ref-store:
  // refs are invalidated on navigation anyway, exactly like BrowserSkill.
  const cssPath = (el) => {
    if (el.id && !/^\d/.test(el.id)) return `#${CSS.escape(el.id)}`;
    const parts = [];
    for (let n = el; n && n.nodeType === 1; n = n.parentElement) {
      let part = n.tagName.toLowerCase();
      if (n.id) {
        parts.unshift(`#${CSS.escape(n.id)}`);
        break;
      }
      const parent = n.parentElement;
      if (parent) {
        const sameTag = Array.from(parent.children).filter((c) => c.tagName === n.tagName);
        if (sameTag.length > 1) part += `:nth-of-type(${sameTag.indexOf(n) + 1})`;
      }
      parts.unshift(part);
      if (parts.length > 12) break;
    }
    return parts.join(" > ");
  };

  const interactive = [];
  for (const el of document.querySelectorAll(INTERACTIVE)) {
    if (isNested(el)) continue;
    const style = getComputedStyle(el);
    if (style.display === "none" || style.visibility === "hidden") continue;
    const rect = el.getBoundingClientRect();
    if (rect.width < 1 || rect.height < 1) {
      // allow visually-hidden but focusable elements? no — keep the listing clean
      continue;
    }
    const entry = {
      kind: "interactive",
      role: roleOf(el),
      name: nameOf(el),
      path: cssPath(el),
      tag: el.tagName.toLowerCase(),
      viewport: inViewport(el) ? "viewport" : "offscreen",
    };
    if (el instanceof HTMLInputElement) {
      const t = (el.type || "text").toLowerCase();
      if (t === "checkbox" || t === "radio") entry.state = el.checked ? "checked" : "unchecked";
      else if (t === "password") entry.state = "[password]";
      else if (el.value) entry.state = `[value: ${clean(String(el.value), 30)}]`;
      if (el.disabled) entry.state = (entry.state ? entry.state + " " : "") + "[disabled]";
    }
    if (el instanceof HTMLSelectElement) {
      const opt = el.selectedOptions[0];
      entry.state = opt ? `[selected: ${clean(opt.textContent || opt.value, 30)}]` : "[none selected]";
    }
    if (el.getAttribute("aria-expanded")) entry.state = (entry.state ? entry.state + " " : "") + `[expanded: ${el.getAttribute("aria-expanded")}]`;
    if (el.tagName === "A" && el.href && el.href !== "javascript:void(0)") entry.href = el.href.slice(0, 100);
    interactive.push(entry);
  }

  const headings = [];
  for (const el of document.querySelectorAll("h1,h2,h3,h4,h5,h6")) {
    const text = clean(el.textContent);
    if (!text) continue;
    headings.push({ kind: "heading", level: el.tagName.toLowerCase(), name: text, viewport: inViewport(el) ? "viewport" : "offscreen" });
  }

  return {
    url: location.href,
    title: clean(document.title, 100),
    interactive,
    headings,
    counts: { interactive: interactive.length, headings: headings.length },
  };
}

export function formatObservation(obs, from = 1) {
  const lines = [];
  lines.push(`Page: ${obs.url}`);
  lines.push(`Title: ${obs.title || "(none)"}`);
  lines.push("");
  lines.push(`== Interactive (${obs.counts.interactive}) ==`);
  if (!obs.interactive.length) lines.push("(none found)");
  obs.interactive.forEach((e, i) => {
    const bits = [`@e${from + i}`, e.role, e.name ? `"${e.name}"` : "(unnamed)"];
    if (e.state) bits.push(e.state);
    if (e.href) bits.push(`→ ${e.href}`);
    if (e.viewport === "offscreen") bits.push("[below fold]");
    lines.push(bits.join(" "));
  });
  lines.push("");
  lines.push(`== Headings (${obs.counts.headings}) ==`);
  if (!obs.headings.length) lines.push("(none)");
  obs.headings.forEach((h) => lines.push(`${h.level} "${h.name}"${h.viewport === "offscreen" ? " [below fold]" : ""}`));
  return lines.join("\n");
}
