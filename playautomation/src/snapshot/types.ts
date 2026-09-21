// Shared types between the injected snapshot script and the daemon.
// The script itself (snapshotScript.ts) must stay self-contained (esbuild IIFE),
// so it re-declares these shapes inline; keep them in sync.

export interface PaNode {
  role: string;
  name?: string;
  ref?: string;
  value?: string;
  checked?: boolean;
  disabled?: boolean;
  href?: string;
  children?: PaNode[];
}

export interface PaSnapshot {
  url: string;
  title: string;
  timestamp: number;
  tree: PaNode[];
}

export interface PaSelector {
  selector: string; // canonical DSL, e.g. role=button[name="Sign in"], css=#id, text=Hello >> nth=0
  engine: string;
  css: string; // css-only fallback for debugging
  ambiguous: boolean; // nth fallback was used
}

export const REF_PATTERN = /^(f\d+)?e\d+$/;
