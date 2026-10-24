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

/** LD-017: the statuses a case may move to; mirrors com.lawdesk.cases.CaseStatus#next. */
export const NEXT_STATUSES: Record<CaseStatus, readonly CaseStatus[]> = {
  OPEN: ['ACTIVE', 'CLOSED'],
  ACTIVE: ['SUSPENDED', 'CLOSED'],
  SUSPENDED: ['ACTIVE', 'CLOSED'],
  CLOSED: ['ACTIVE', 'ARCHIVED'],
  ARCHIVED: [],
};

export const PARTY_KINDS = ['PERSON', 'ORGANIZATION'] as const;
export type PartyKind = (typeof PARTY_KINDS)[number];

export const PARTY_ROLES = ['CLIENT', 'OPPONENT', 'OTHER'] as const;
export type PartyRole = (typeof PARTY_ROLES)[number];

export interface Party {
  id: string;
  kind: PartyKind;
  name: string;
  nationalId?: string | null;
  registrationNo?: string | null;
  version: number;
}

export interface CaseParty {
  partyId: string;
  kind: PartyKind;
  name: string;
  role: PartyRole;
  addedAt: string;
}

export interface CaseEvent {
  id: string;
  type: string;
  summary: string;
  occurredAt: string;
  actorId: string;
}

export interface NewParty {
  kind: PartyKind;
  name: string;
  /** Only the identifier of the chosen kind is sent. */
  identifier: string;
}

function blankToNull(value: string): string | null {
  const trimmed = value.trim();
  return trimmed === '' ? null : trimmed;
}

export function getTimeline(id: string, page: number, signal?: AbortSignal): Promise<PageResponse<CaseEvent>> {
  const query = page > 0 ? `?page=${String(page)}` : '';
  return api(`${BASE}/${encodeURIComponent(id)}/timeline${query}`, signal === undefined ? {} : { signal });
}

export function getCaseParties(id: string, signal?: AbortSignal): Promise<CaseParty[]> {
  return api(`${BASE}/${encodeURIComponent(id)}/parties`, signal === undefined ? {} : { signal });
}

export function changeStatus(id: string, status: CaseStatus | '', reason: string, version: number): Promise<LegalCase> {
  return api(`${BASE}/${encodeURIComponent(id)}/status`, {
    method: 'POST',
    body: { status: status === '' ? null : status, reason: blankToNull(reason), version },
  });
}

export function searchParties(q: string): Promise<PageResponse<Party>> {
  const query = new URLSearchParams({ q: q.trim() }).toString();
  return api(`/api/v1/parties?${query}`);
}

export function createParty(party: NewParty): Promise<Party> {
  const identifier = blankToNull(party.identifier);
  return api('/api/v1/parties', {
    method: 'POST',
    body: {
      kind: party.kind,
      name: party.name,
      nationalId: party.kind === 'PERSON' ? identifier : null,
      registrationNo: party.kind === 'ORGANIZATION' ? identifier : null,
    },
  });
}

export function addCaseParty(id: string, partyId: string, role: PartyRole): Promise<CaseParty> {
  return api(`${BASE}/${encodeURIComponent(id)}/parties`, { method: 'POST', body: { partyId, role } });
}

export function removeCaseParty(id: string, partyId: string): Promise<void> {
  return api(`${BASE}/${encodeURIComponent(id)}/parties/${encodeURIComponent(partyId)}`, { method: 'DELETE' });
}

export function updateCase(id: string, values: CaseFields, version: number): Promise<LegalCase> {
  return api(`${BASE}/${encodeURIComponent(id)}`, { method: 'PUT', body: { ...fields(values), version } });
}
