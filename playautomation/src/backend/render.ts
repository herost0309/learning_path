// Renders a PaSnapshot tree as playwright-mcp-style YAML text:
//   - banner:
//     - heading "Welcome" [ref=e1]
//     - textbox "Email" [ref=e2]
import type { PaNode, PaSnapshot } from '../snapshot/types.js';

function line(node: PaNode, indent: string): string {
  let text = `${indent}- ${node.role}`;
  if (node.name !== undefined)
    text += ` ${JSON.stringify(node.name)}`;
  if (node.value !== undefined)
    text += ` value=${JSON.stringify(node.value)}`;
  if (node.checked)
    text += ' [checked]';
  if (node.disabled)
    text += ' [disabled]';
  if (node.href)
    text += ` href=${JSON.stringify(node.href)}`;
  if (node.ref)
    text += ` [ref=${node.ref}]`;
  return text;
}

export function renderSnapshot(snapshot: PaSnapshot): string {
  const out: string[] = [];
  out.push(`# url: ${snapshot.url}`);
  if (snapshot.title)
    out.push(`# title: ${snapshot.title}`);
  const walk = (nodes: PaNode[], indent: string) => {
    for (const node of nodes) {
      if (node.children?.length) {
        out.push(line(node, indent) + ':');
        walk(node.children, indent + '  ');
      } else {
        out.push(line(node, indent));
      }
    }
  };
  walk(snapshot.tree, '');
  return out.join('\n');
}
