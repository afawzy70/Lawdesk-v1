/// <reference types="node" />
import { readFileSync } from 'node:fs';
import { screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { declarations } from '../../test/css';
import { aCase, jsonResponse, problem, renderCases, scriptedFetch } from '../cases/testSupport';
import { hearing, proceeding } from './testSupport';

/*
 * The case details page hosts the legal operations through one component (architecture/CONTRACTS.md §8.1), and the
 * Phase 5 screens must fit a 375px phone in both languages. jsdom has no layout, so the CSS rules that keep long
 * references inside the viewport are pinned here; real-browser checks are in docs/evidence/PHASE-5.
 */

// A variable path keeps Vite from turning the URL into an asset import.
const read = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf-8');
const operationsCss = read('./operations.css');

const LONG = 'QA261024121816D9653B-MIGRATED-REFERENCE';
const CASE = aCase({ referenceNo: LONG });
const API = `/api/v1/cases/${CASE.id}`;

function backend() {
  scriptedFetch((request) => {
    const url = new URL(request.url, 'http://localhost');
    if (url.pathname === API) return jsonResponse(CASE);
    if (url.pathname === `${API}/parties`) return jsonResponse([]);
    if (url.pathname === `${API}/hearings`) {
      const items = [hearing({ caseId: CASE.id, caseReferenceNo: LONG, notes: `${LONG} notes` })];
      return jsonResponse({ items, page: 0, size: 100, totalItems: 1, totalPages: 1 });
    }
    if ([`${API}/timeline`, `${API}/proceedings`, `${API}/judgments`, `${API}/special-decisions`].includes(url.pathname)) {
      const items = url.pathname === `${API}/proceedings` ? [proceeding({ caseId: CASE.id })] : [];
      return jsonResponse({ items, page: 0, size: 100, totalItems: items.length, totalPages: items.length });
    }
    return problem(500);
  });
}

describe('legal operations on the case details page', () => {
  it.each([
    { lang: 'en', title: 'Legal operations', hearings: 'Hearings', schedule: 'Schedule hearing' },
    { lang: 'ar', title: 'العمليات القانونية', hearings: 'الجلسات', schedule: 'تحديد جلسة' },
  ])('shows the case hearings under their own heading ($lang)', async (c) => {
    backend();
    renderCases(`/cases/${CASE.id}?lang=${c.lang}`);
    const operations = await screen.findByRole('region', { name: c.title });
    expect(within(operations).getByRole('heading', { level: 2, name: c.title })).toBeInTheDocument();
    const hearings = within(operations).getByRole('region', { name: c.hearings });
    expect(await within(hearings).findByRole('listitem')).toHaveClass('ops-item');
    expect(within(hearings).getByRole('button', { name: c.schedule })).toBeInTheDocument();
  });
});

describe('narrow-screen CSS rules of the operations screens', () => {
  it.each(['.ops-wrap', '.ops-item', '.ops-form h3'])('%s wraps long references', (selector) => {
    expect(declarations(operationsCss, selector)).toMatchObject({ 'overflow-wrap': 'anywhere' });
  });

  it.each(['.ops-filters', '.ops-actions', '.ops-pager'])('%s wraps its controls onto new lines', (selector) => {
    expect(declarations(operationsCss, selector)).toMatchObject({ display: 'flex', 'flex-wrap': 'wrap' });
  });

  it('sections may shrink below their content inside the page grid', () => {
    expect(declarations(operationsCss, '.ops-section')).toMatchObject({ 'min-inline-size': '0' });
  });

  it('form controls never grow wider than their form, even a select with long options', () => {
    expect(declarations(operationsCss, '.ops-field input')).toMatchObject({ 'max-inline-size': '100%' });
    expect(declarations(operationsCss, '.ops-field select')).toMatchObject({ 'inline-size': '100%' });
    for (const selector of ['.ops-form', '.ops-field']) {
      expect(declarations(operationsCss, selector)).toMatchObject({ 'grid-template-columns': 'minmax(0, 1fr)' });
    }
  });

  it('row actions keep at least a 24px target', () => {
    expect(declarations(operationsCss, '.ops-actions button')).toMatchObject({
      'min-block-size': '1.5rem',
      'min-inline-size': '1.5rem',
    });
  });
});
