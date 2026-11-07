import { readFileSync } from 'node:fs';
import { describe, it, expect } from 'vitest';

const css = readFileSync('src/index.css', 'utf8');

describe('index.css', () => {
  it('keeps the unhealthy tile border rule whole', () => {
    expect(css).toMatch(/\.tile--unhealthy \.status-dot,\s*\.tile--unhealthy \.update-badge \{ border-color: var\(--bad-bg\); \}/);
  });

  it('styles the link globe on every tile, not only unhealthy ones', () => {
    expect(css).toMatch(/^\.link-icon-globe \{/m);
    expect(css).not.toMatch(/\.tile--unhealthy \.link-icon-globe/);
  });
});
