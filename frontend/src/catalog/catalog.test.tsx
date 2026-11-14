/// <reference types="node" />
import { act, screen, within } from '@testing-library/react';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { en } from '../i18n/messages/en';
import { CaseDetailsFields } from '../features/cases/intake/CaseDetailsFields';
import { EMPTY_DETAILS } from '../features/cases/api';
import { CourtForm } from '../features/courts/CourtForm';
import { ClaimStatisticsPage } from '../features/pleadings/ClaimStatisticsPage';
import { renderWith, serve } from '../features/pleadings/testSupport';
import { catalogCodes, catalogLabel, setCatalog } from './catalogStore';
import { CATALOG_LISTS } from './registry';

/** LD-DOC-13: the administrator's labels, order and deactivations reach the screens; every catalogued list is wired. */

const option = (code: string, labelAr: string, labelEn: string | null, sortOrder: number, active = true) => ({
  code,
  labelAr,
  labelEn,
  active,
  sortOrder,
});

const optionTexts = (select: HTMLElement) => within(select).getAllByRole('option').map((o) => o.textContent);

describe('catalogue store', () => {
  it('relabels the keys of a list, in the active language, and leaves the rest alone', () => {
    setCatalog({ CASE_TYPE: [option('CIVIL', 'قضايا مدنية', 'Civil cases', 1)] });
    expect(catalogLabel('cases.type.CIVIL', 'en')).toBe('Civil cases');
    expect(catalogLabel('cases.type.CIVIL', 'ar')).toBe('قضايا مدنية');
    expect(catalogLabel('cases.type.LABOR', 'en')).toBeUndefined();
    expect(catalogLabel('cases.status.OPEN', 'en')).toBeUndefined();
  });

  it('orders by the catalogue, hides a deactivated option unless it is the current one, and falls back without a catalogue', () => {
    expect(catalogCodes('CASE_TYPE', ['CIVIL', 'LABOR', 'FAMILY'])).toEqual(['CIVIL', 'LABOR', 'FAMILY']);
    setCatalog({
      CASE_TYPE: [option('FAMILY', 'ا', 'Family', 1), option('CIVIL', 'م', 'Civil', 2, false), option('LABOR', 'ع', 'Labour', 3)],
    });
    expect(catalogCodes('CASE_TYPE', ['CIVIL', 'LABOR', 'FAMILY'])).toEqual(['FAMILY', 'LABOR']);
    expect(catalogCodes('CASE_TYPE', ['CIVIL', 'LABOR', 'FAMILY'], 'CIVIL')).toEqual(['FAMILY', 'CIVIL', 'LABOR']);
  });

  it('offers a class-a list its own active options, including ones the screen never had', () => {
    setCatalog({ CLAIM_KIND: [option('NEW_KIND', 'نوع', 'New kind', 0), option('PRIMARY', 'أصلي', 'Primary', 1), option('OTHER', 'أخرى', 'Other', 2, false)] });
    expect(catalogCodes('CLAIM_KIND', ['PRIMARY', 'ALTERNATIVE', 'OTHER'])).toEqual(['NEW_KIND', 'PRIMARY', 'ALTERNATIVE']);
  });
});

describe('screens show the administrator-managed labels, order and availability', () => {
  it('class a: the importance dropdown of the case form (new option, relabelled, reordered, deactivated hidden, current kept)', () => {
    serve({});
    const importance = (value: string) => (
      <CaseDetailsFields value={{ ...EMPTY_DETAILS, importance: value }} onChange={() => undefined} />
    );
    const view = renderWith(importance(''));
    expect(optionTexts(screen.getByLabelText('Importance'))).toEqual(['Not specified', 'Low', 'Normal', 'High', 'Critical']);

    act(() => {
      setCatalog({
        CASE_IMPORTANCE: [
          option('EXTREME', 'قصوى', 'Extreme', 0),
          option('LOW', 'دنيا', 'Minor', 1),
          option('NORMAL', 'عادية', 'Normal', 2),
          option('HIGH', 'مرتفعة', 'High', 3, false),
          option('CRITICAL', 'حرجة', 'Critical', 4),
        ],
      });
    });
    expect(optionTexts(screen.getByLabelText('Importance'))).toEqual(['Not specified', 'Extreme', 'Minor', 'Normal', 'Critical']);

    // A case that already carries the deactivated option still shows it.
    view.rerender(importance('HIGH'));
    expect(optionTexts(screen.getByLabelText('Importance'))).toContain('High');
  });

  it('class b: the court level dropdown takes the edited label and order', () => {
    serve({});
    renderWith(<CourtForm onSaved={() => undefined} onCancel={() => undefined} />);
    const level = screen.getByLabelText('Level');
    expect(optionTexts(level).slice(1, 3)).toEqual(['First instance', 'Appeal']);
    act(() => {
      setCatalog({
        COURT_LEVEL: [
          option('APPEAL', 'استئناف', 'Court of appeal', 1),
          option('FIRST_INSTANCE', 'ابتدائي', 'Trial court', 2),
          option('CASSATION', 'نقض', 'Cassation', 3),
        ],
      });
    });
    expect(optionTexts(screen.getByLabelText('Level')).slice(1, 3)).toEqual(['Court of appeal', 'Trial court']);
  });

  it('a filter of a report page (stage and case type) follows the catalogue too, in Arabic', () => {
    serve({ 'GET /api/v1/reports/claims': {}, 'GET /api/v1/reports/defenses': {} });
    setCatalog({
      STAGE: [option('APPEAL', 'محكمة الاستئناف', 'Appeal court', 1), option('FIRST_INSTANCE', 'درجة أولى', 'Trial', 2)],
      CASE_TYPE: [option('CIVIL', 'مدني (معدّل)', 'Civil (edited)', 1)],
    });
    renderWith(<ClaimStatisticsPage />, { lang: 'ar' });
    const texts = screen.getAllByRole('option').map((o) => o.textContent);
    expect(texts).toContain('محكمة الاستئناف');
    expect(texts).toContain('مدني (معدّل)');
    expect(texts.indexOf('محكمة الاستئناف')).toBeLessThan(texts.indexOf('درجة أولى'));
  });
});

const SRC = resolve(process.cwd(), 'src');

function sources(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return sources(path);
    return /\.tsx?$/.test(name) && !/\.test\.tsx?$/.test(name) && !path.includes(join('src', 'catalog')) ? [path] : [];
  });
}

/** Lists that appear on screens as read-only labels, not in a dropdown: nothing to order or hide. */
const LABEL_ONLY_DISPLAY: Record<string, string> = {
  LEGAL_DOCUMENT_STATUS: 'shown as the status of a legal document and in its confirmation messages (relabelled through t())',
};

describe('every catalogued list is wired', () => {
  const text = sources(SRC).map((path) => readFileSync(path, 'utf-8')).join('\n');

  it.each(CATALOG_LISTS.map((l) => [l.code]))('%s: its dropdowns take catalogue order/availability, or it is a label-only display', (code) => {
    const wired = new RegExp(`catalogCodes(?:<[^>]*>)?\\(\\s*'${code}'`).test(text);
    expect(wired || code in LABEL_ONLY_DISPLAY).toBe(true);
  });

  it.each(CATALOG_LISTS.map((l) => [l.code, l.prefixes] as const))('%s: its message prefixes exist, so the relabelling reaches the screens', (_code, prefixes) => {
    for (const prefix of prefixes) {
      expect(Object.keys(en).some((key) => key.startsWith(prefix))).toBe(true);
    }
  });
});
