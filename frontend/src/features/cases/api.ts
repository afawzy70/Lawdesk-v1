import { api } from '../../api/http';

/** Case API (docs/delivery/PHASE-3-ACCEPTANCE.md, LD-012, LD-013, LD-015); every call goes through `api()`. */

export const CASE_TYPES = ['CIVIL', 'CRIMINAL', 'COMMERCIAL', 'LABOR', 'FAMILY', 'ADMINISTRATIVE'] as const;
export type CaseType = (typeof CASE_TYPES)[number];

export const CASE_STATUSES = ['OPEN', 'ACTIVE', 'SUSPENDED', 'CLOSED', 'ARCHIVED'] as const;
export type CaseStatus = (typeof CASE_STATUSES)[number];

export interface LegalCase {
  id: string;
  referenceNo: string;
  title: string;
  caseType: CaseType;
  subject?: string | null;
  openedOn: string;
  status: CaseStatus;
  version: number;
  createdAt: string;
  createdBy: string;
  updatedAt: string;
  updatedBy: string;
}

export interface CaseListItem {
  id: string;
  referenceNo: string;
  title: string;
  caseType: CaseType;
  status: CaseStatus;
  openedOn: string;
  updatedAt: string;
}

/** The shared list shape (architecture/CONTRACTS.md §4). */
export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface CaseSearch {
  q?: string;
  status?: CaseStatus;
  caseType?: CaseType;
  page?: number;
}

/** The editable fields as the form holds them: empty strings mean "not given". */
export interface CaseFields {
  title: string;
  caseType: CaseType | '';
  subject: string;
  openedOn: string;
}

export interface NewCase extends CaseFields {
  referenceNo: string;
}

const BASE = '/api/v1/cases';

export function caseQuery(search: CaseSearch): string {
  const params = new URLSearchParams();
  const q = search.q?.trim() ?? '';
  if (q !== '') params.set('q', q);
  if (search.status !== undefined) params.set('status', search.status);
  if (search.caseType !== undefined) params.set('caseType', search.caseType);
  if (search.page !== undefined && search.page > 0) params.set('page', String(search.page));
  const query = params.toString();
  return query === '' ? '' : `?${query}`;
}

/** Empty choices go out as null, so the server reports them as missing rather than malformed. */
function fields(values: CaseFields) {
  return {
    title: values.title,
    caseType: values.caseType === '' ? null : values.caseType,
    subject: values.subject === '' ? null : values.subject,
    openedOn: values.openedOn === '' ? null : values.openedOn,
  };
}

export function searchCases(search: CaseSearch, signal?: AbortSignal): Promise<PageResponse<CaseListItem>> {
  return api(`${BASE}${caseQuery(search)}`, signal === undefined ? {} : { signal });
}

export function getCase(id: string, signal?: AbortSignal): Promise<LegalCase> {
  return api(`${BASE}/${encodeURIComponent(id)}`, signal === undefined ? {} : { signal });
}

export function createCase(values: NewCase): Promise<LegalCase> {
  const referenceNo = values.referenceNo.trim();
  return api(BASE, { method: 'POST', body: { referenceNo: referenceNo === '' ? null : referenceNo, ...fields(values) } });
}

export function updateCase(id: string, values: CaseFields, version: number): Promise<LegalCase> {
  return api(`${BASE}/${encodeURIComponent(id)}`, { method: 'PUT', body: { ...fields(values), version } });
}
