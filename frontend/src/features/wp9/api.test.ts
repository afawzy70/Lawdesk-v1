import { afterEach, describe, expect, it, vi } from 'vitest';
import { jsonResponse } from '../../test/http';
import {
  addListValue,
  changeSettlementStatus,
  createDirective,
  createSettlement,
  createTemplate,
  EMPTY_DIRECTIVE_FILTER,
  labelOf,
  listValues,
  runSearch,
  searchDirectives,
  settlementInputOf,
  EMPTY_SETTLEMENT,
  valueLabel,
} from './api';
import { listValue, settlement } from './testSupport';

afterEach(() => {
  vi.unstubAllGlobals();
});

function stub() {
  const fetchMock = vi.fn(() => Promise.resolve(jsonResponse([])));
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

const lastCall = (fetchMock: ReturnType<typeof stub>) => {
  const [url, init] = fetchMock.mock.calls.at(-1) as unknown as [string, RequestInit | undefined];
  return { url, body: typeof init?.body === 'string' ? (JSON.parse(init.body) as Record<string, unknown>) : null };
};

describe('labels of managed lists', () => {
  const waiver = listValue('WAIVER', 'تنازل', 'Waiver', 'SETTLEMENT_KIND');

  it('uses the Arabic label in Arabic and the English one otherwise, falling back to Arabic and to the code', () => {
    expect(valueLabel(waiver, 'ar')).toBe('تنازل');
    expect(valueLabel(waiver, 'en')).toBe('Waiver');
    expect(valueLabel({ labelAr: 'صلح', labelEn: null }, 'en')).toBe('صلح');
    expect(labelOf([waiver], 'WAIVER', 'en')).toBe('Waiver');
    expect(labelOf([waiver], 'GONE', 'en')).toBe('GONE');
    expect(labelOf([waiver], null, 'en')).toBe('');
  });
});

describe('requests', () => {
  it('reads a list with or without the inactive values, and adds a value with the English label optional', async () => {
    const fetchMock = stub();
    await listValues('DIRECTIVE_KIND');
    expect(lastCall(fetchMock).url).toBe('/api/v1/lists/DIRECTIVE_KIND');
    await listValues('DIRECTIVE_KIND', undefined, true);
    expect(lastCall(fetchMock).url).toBe('/api/v1/lists/DIRECTIVE_KIND?includeInactive=true');
    await addListValue('TEMPLATE_KIND', { code: 'X', labelAr: ' نوع ', labelEn: ' ' });
    expect(lastCall(fetchMock).body).toEqual({ code: 'X', labelAr: 'نوع', labelEn: null });
  });

  it('sends the settlement amount as text and blank fields as null, and the completion date only when executing', async () => {
    const fetchMock = stub();
    await createSettlement('c1', { ...EMPTY_SETTLEMENT, kind: 'SETTLEMENT', settlementAmount: '10.500', legalGrounds: ' grounds ' });
    expect(lastCall(fetchMock).body).toMatchObject({ kind: 'SETTLEMENT', settlementAmount: '10.500', legalGrounds: 'grounds', legalOpinion: null });
    await changeSettlementStatus(settlement(), { status: 'EXECUTED', reason: '', completedOn: '2026-11-05' });
    expect(lastCall(fetchMock).body).toEqual({ status: 'EXECUTED', reason: null, completedOn: '2026-11-05', version: 0 });
    await changeSettlementStatus(settlement(), { status: 'APPROVED', reason: 'ok', completedOn: '2026-11-05' });
    expect(lastCall(fetchMock).body).toEqual({ status: 'APPROVED', reason: 'ok', completedOn: null, version: 0 });
  });

  it('turns a settlement back into form values with empty strings for what is not specified', () => {
    expect(settlementInputOf(settlement({ offeringPartyId: null, settlementAmount: null, legalGrounds: null }))).toMatchObject({
      offeringPartyId: '',
      settlementAmount: '',
      legalGrounds: '',
      kind: 'SETTLEMENT',
    });
  });

  it('builds the register query from the filter', async () => {
    const fetchMock = stub();
    await searchDirectives(EMPTY_DIRECTIVE_FILTER);
    expect(lastCall(fetchMock).url).toBe('/api/v1/directives?page=0&size=20');
    await searchDirectives({ q: ' memo ', status: 'DONE', kind: 'PREPARE_MEMO', overdue: true, mine: true, caseId: 'c1', page: 2 });
    const url = new URL(lastCall(fetchMock).url, 'http://localhost');
    expect(Object.fromEntries(url.searchParams)).toEqual({ page: '2', size: '20', q: 'memo', status: 'DONE', kind: 'PREPARE_MEMO', overdue: 'true', addressee: 'me', caseId: 'c1' });
  });

  it('creates a directive and a template with blank optional fields as null', async () => {
    const fetchMock = stub();
    await createDirective({ caseId: 'c1', kind: '', title: ' t ', details: '', directiveDate: '2026-11-01', source: ' s ', sourceReferenceNo: '', addresseeUserId: '', addresseeText: 'x', dueDate: '' });
    expect(lastCall(fetchMock).body).toEqual({ caseId: 'c1', kind: null, title: 't', details: null, directiveDate: '2026-11-01', source: 's', sourceReferenceNo: null, addresseeUserId: null, addresseeText: 'x', dueDate: null });
    await createTemplate({ kind: 'DEFENSE_MEMO', title: ' T ', description: '', body: '' });
    expect(lastCall(fetchMock).body).toEqual({ kind: 'DEFENSE_MEMO', title: 'T', description: null, body: null });
  });

  it('builds the search query from the filters and the page', async () => {
    const fetchMock = stub();
    await runSearch({ q: ' words ', types: [], caseType: '', from: '', to: '' }, { page: 0, size: 5 });
    expect(lastCall(fetchMock).url).toBe('/api/v1/search?q=words&page=0&size=5');
    await runSearch({ q: 'w', types: ['CASE', 'NOTE'], caseType: 'LABOR', from: '2026-01-01', to: '2026-02-01' }, { page: 1, size: 5 });
    const url = new URL(lastCall(fetchMock).url, 'http://localhost');
    expect(Object.fromEntries(url.searchParams)).toEqual({ q: 'w', page: '1', size: '5', types: 'CASE,NOTE', caseType: 'LABOR', from: '2026-01-01', to: '2026-02-01' });
  });
});
