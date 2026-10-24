/// <reference types="node" />
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { declarations } from '../../test/css';

/*
 * docs/delivery/PHASE-6-ACCEPTANCE.md, common rule 8: no horizontal page overflow at 375px. jsdom has no layout, so
 * the rules that keep the report screens inside the viewport are pinned here (as QAD-027 does for the case screens).
 */
const css = readFileSync(resolve(process.cwd(), 'src/features/reports/reports.css'), 'utf-8');

describe('report screen layout', () => {
  it('wraps long references and names inside table cells', () => {
    expect(declarations(css, '.rpt-table td')).toMatchObject({ 'overflow-wrap': 'anywhere' });
    expect(declarations(css, '.rpt-table th')).toMatchObject({ 'overflow-wrap': 'anywhere' });
  });

  it('lets filter fields and dashboard panels shrink to a phone width', () => {
    expect(declarations(css, '.rpt-filters')['grid-template-columns']).toContain('min(100%');
    expect(declarations(css, '.rpt-field select')).toMatchObject({ 'min-inline-size': '0', 'inline-size': '100%' });
    expect(declarations(css, '.rpt-dashboard')['grid-template-columns']).toContain('min(100%');
    expect(declarations(css, '.rpt-panel')).toMatchObject({ 'min-inline-size': '0' });
  });
});
