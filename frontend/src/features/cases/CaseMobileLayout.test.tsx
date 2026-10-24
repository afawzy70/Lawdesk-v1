/// <reference types="node" />
import { readFileSync } from 'node:fs';
import { screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { declarations } from '../../test/css';
import type { CaseParty } from './api';
import { aCase, jsonResponse, problem, renderCases, scriptedFetch } from './testSupport';

/*
 * QAD-027: at 375px the case details page was 475px wide in both languages. Two causes, both measured in Chromium
 * (frontend/scripts/mobile-layout-check.mjs): long unbroken references and names cannot wrap, and the visually
 * hidden caption of the parties table, absolutely positioned, escaped its scroll container. jsdom has no layout,
 * so these tests pin the markup and the CSS rules that keep the page inside the viewport.
 */

const read = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf-8');
const globalCss = read('../../styles.css');
const casesCss = read('./cases.css');
const proceedingsCss = read('../proceedings/proceedings.css');

const LONG = 'QA261024121816D9653B';
const CASE = aCase({ referenceNo: LONG, title: `${LONG} edited`, subject: `${LONG} synthetic QA only` });
const API = `/api/v1/cases/${CASE.id}`;
const PARTY: CaseParty = {
  partyId: '11111111-1111-4111-8111-111111111111',
  kind: 'PERSON',
  name: `${LONG} طرف اختبار`,
  role: 'CLIENT',
  addedAt: '2026-10-24T12:18:16Z',
};
const PROCEEDING = {
  id: 'p1', caseId: CASE.id, stage: 'FIRST_INSTANCE', courtId: 'court-fi', courtCode: `${LONG}-0`,
  courtNameAr: `${LONG} محكمة`, courtNameEn: `${LONG} Court`, courtCaseNumber: `${LONG}-FI`, courtCaseYear: 2026,
  filingDate: '2026-01-02', parentProceedingId: null, hasJudgment: false, version: 0,
  createdAt: '2026-01-02T08:00:00Z', updatedAt: '2026-01-02T08:00:00Z',
};

function backend() {
  scriptedFetch((request) => {
    const url = new URL(request.url, 'http://localhost');
    if (url.pathname === API) return jsonResponse(CASE);
    if (url.pathname === `${API}/parties`) return jsonResponse([PARTY]);
    if (url.pathname === `${API}/timeline`) {
      const items = [
        { id: 'e1', type: 'PARTY_ADDED', summary: `${PARTY.name} added as CLIENT`, occurredAt: PARTY.addedAt, actorId: 'a' },
      ];
      return jsonResponse({ items, page: 0, size: 20, totalItems: 1, totalPages: 1 });
    }
    if ([`${API}/proceedings`, `${API}/judgments`, `${API}/special-decisions`].includes(url.pathname)) {
      const items = url.pathname === `${API}/proceedings` ? [PROCEEDING] : [];
      return jsonResponse({ items, page: 0, size: 100, totalItems: items.length, totalPages: 1 });
    }
    return problem(500);
  });
}

describe('case details on a narrow screen (QAD-027)', () => {
  it.each([
    { lang: 'en', dir: 'ltr', parties: 'Parties', timeline: 'Timeline', stages: 'Judicial stages', remove: 'Remove' },
    { lang: 'ar', dir: 'rtl', parties: 'أطراف القضية', timeline: 'السجل الزمني', stages: 'درجات التقاضي', remove: 'إزالة' },
  ])('contains the parties table in a named, focusable scroll region ($lang)', async (c) => {
    backend();
    renderCases(`/cases/${CASE.id}?lang=${c.lang}`);

    const table = await screen.findByRole('table', { name: c.parties });
    expect(document.documentElement).toHaveAttribute('dir', c.dir);
    expect(document.documentElement).toHaveAttribute('lang', c.lang);

    // The section and its scroll region are both named by the section heading.
    const region = screen.getAllByRole('region', { name: c.parties }).find((r) => r.classList.contains('table-scroll'));
    if (region === undefined) throw new Error('no scroll region');
    expect(region).toHaveAttribute('tabindex', '0');
    expect(region).toContainElement(table);
    // The long name sits in the first column, the one allowed to break anywhere; the row action stays in the region.
    expect(table.querySelector('tbody td:first-child')).toHaveTextContent(PARTY.name);
    expect(within(region).getByRole('button', { name: new RegExp(c.remove) })).toBeInTheDocument();

    // The visually hidden caption is positioned; it must stay inside the region that clips it.
    expect(region).toContainElement(table.querySelector('caption.visually-hidden'));

    const timeline = await screen.findByRole('list', { name: c.timeline });
    expect(timeline).toHaveClass('case-timeline');
    const stages = screen.getByRole('region', { name: c.stages });
    expect(await within(stages).findByRole('article')).toHaveClass('judicial-stage');
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(LONG);
    expect(document.querySelector('dl.case-facts')).toHaveTextContent(CASE.title);
  });
});

describe('narrow-screen CSS rules (QAD-027)', () => {
  it('scroll regions clip their positioned descendants, such as visually hidden captions', () => {
    expect(declarations(globalCss, '.table-scroll')).toMatchObject({ 'overflow-x': 'auto', position: 'relative' });
  });

  it('page headings wrap long references instead of widening the page', () => {
    expect(declarations(globalCss, '.app-main h1')).toMatchObject({ 'overflow-wrap': 'anywhere' });
  });

  it('case facts wrap long values inside a shrinkable column', () => {
    expect(declarations(casesCss, '.case-facts')).toMatchObject({
      'grid-template-columns': 'max-content minmax(0, 1fr)',
    });
    expect(declarations(casesCss, '.case-facts dd')).toMatchObject({ 'overflow-wrap': 'anywhere' });
  });

  it.each(['.case-table td:first-child', '.case-timeline', '.case-found'])('%s wraps long names', (selector) => {
    expect(declarations(casesCss, selector)).toMatchObject({ 'overflow-wrap': 'anywhere' });
  });

  it('row actions keep at least a 24px target', () => {
    expect(declarations(casesCss, '.case-table button')).toMatchObject({
      'min-block-size': '1.5rem',
      'min-inline-size': '1.5rem',
    });
  });

  it.each(['.judicial-stage', '.judicial-form'])('%s wraps long court references', (selector) => {
    expect(declarations(proceedingsCss, selector)).toMatchObject({ 'overflow-wrap': 'anywhere' });
  });
});

describe('the CSS rule reader', () => {
  it('ignores comments and media queries and reads every matching rule', () => {
    const css = '/* .a { color: red } */ .a, .b { color: blue; } @media (min-width: 1px) { .a { color: green; } } .a { margin: 0 }';
    expect(declarations(css, '.a')).toEqual({ color: 'blue', margin: '0' });
    expect(declarations(css, '.c')).toEqual({});
  });
});
