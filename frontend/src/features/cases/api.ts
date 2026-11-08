import { api } from '../../api/http';

/** Case API (docs/delivery/PHASE-3-ACCEPTANCE.md, LD-012, LD-013, LD-015); every call goes through `api()`. */

export const CASE_TYPES = ['CIVIL', 'CRIMINAL', 'COMMERCIAL', 'LABOR', 'FAMILY', 'ADMINISTRATIVE'] as const;
export type CaseType = (typeof CASE_TYPES)[number];

export const CASE_STATUSES = ['OPEN', 'ACTIVE', 'SUSPENDED', 'CLOSED', 'ARCHIVED'] as const;
export type CaseStatus = (typeof CASE_STATUSES)[number];

export const SIDES = ['PLAINTIFF', 'DEFENDANT'] as const;
export type Side = (typeof SIDES)[number];

export const FINANCIAL_IMPACTS = ['YES', 'NO'] as const;
export type FinancialImpact = (typeof FINANCIAL_IMPACTS)[number];

/** A member of the legal department assigned to a case (العضو المختص / العضو البديل). */
export interface MemberRef {
  id: string;
  displayName: string;
}

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
  /** LD-045 fields; absent on cases that predate them, which are "not specified", never invented. */
  automatedNo?: string | null;
  authorityPosition?: Side | null;
  financialImpact?: FinancialImpact | null;
  /** Dinars with three decimals, as text. */
  financialAmount?: string | null;
  primaryMember?: MemberRef | null;
  alternateMember?: MemberRef | null;
  /** LD-050: a restricted case is reachable only by its members and explicit grants. */
  confidentiality?: 'NORMAL' | 'RESTRICTED';
  owningUnitId?: string | null;
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

/** What an edit may also change (LD-045): the automated number and the financial impact; empty means unchanged. */
export interface CaseExtras {
  automatedNo: string;
  financialImpact: FinancialImpact | '';
  /** Only with "YES"; with "NO" the stored amount is cleared. */
  financialAmount: string;
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
  authority?: boolean;
}

export interface CaseParty {
  partyId: string;
  kind: PartyKind;
  name: string;
  role: PartyRole;
  addedAt: string;
  /** Plaintiff or defendant side; null for parties linked before sides existed. */
  side?: Side | null;
  /** True for the authority (الهيئة) itself. */
  authority?: boolean;
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

/** `side` is required on cases registered through the intake; the role then follows from it on the server. */
export function addCaseParty(id: string, partyId: string, role: PartyRole, side?: Side): Promise<CaseParty> {
  return api(`${BASE}/${encodeURIComponent(id)}/parties`, {
    method: 'POST',
    body: side === undefined ? { partyId, role } : { partyId, role, side },
  });
}

export function removeCaseParty(id: string, partyId: string): Promise<void> {
  return api(`${BASE}/${encodeURIComponent(id)}/parties/${encodeURIComponent(partyId)}`, { method: 'DELETE' });
}

/** The financial part of a request: an absent impact leaves it alone, "NO" carries no amount. */
export function financialBody(extras: Pick<CaseExtras, 'financialImpact' | 'financialAmount'>) {
  if (extras.financialImpact === '') return {};
  return extras.financialImpact === 'YES'
    ? { financialImpact: 'YES', financialAmount: extras.financialAmount.trim() }
    : { financialImpact: 'NO' };
}

export function updateCase(id: string, values: CaseFields, version: number, extras?: CaseExtras): Promise<LegalCase> {
  const automatedNo = extras?.automatedNo.trim() ?? '';
  return api(`${BASE}/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: {
      ...fields(values),
      version,
      ...(automatedNo === '' ? {} : { automatedNo }),
      ...(extras === undefined ? {} : financialBody(extras)),
    },
  });
}

/** LD-045: the active legal department members who can be assigned to a case. */
export function listCaseMembers(signal?: AbortSignal): Promise<MemberRef[]> {
  return api(`${BASE}/members`, signal === undefined ? {} : { signal });
}

/** Reassigns the case (case supervisors only); the server re-evaluates everyone's access at once. */
export function assignCase(
  id: string,
  primaryMemberId: string,
  alternateMemberId: string,
  version: number,
): Promise<LegalCase> {
  return api(`${BASE}/${encodeURIComponent(id)}/assignment`, {
    method: 'PUT',
    body: { primaryMemberId, alternateMemberId: alternateMemberId === '' ? null : alternateMemberId, version },
  });
}

export interface IntakeParty {
  /** A registered party, or the fields of a new one. */
  partyId?: string;
  newParty?: { kind: PartyKind; name: string; nationalId: string | null; registrationNo: string | null; phone: string | null };
  side: Side;
}

export interface IntakeInput {
  automatedNo: string;
  title: string;
  caseType: CaseType | '';
  subject: string;
  openedOn: string;
  courtId: string;
  courtCaseNumber: string;
  courtCaseYear: string;
  filingDate: string;
  authorityPosition: Side | '';
  parties: IntakeParty[];
  primaryMemberId: string;
  alternateMemberId: string;
  financialImpact: FinancialImpact | '';
  financialAmount: string;
}

/** Registers a case with its first stage, parties, members and financial impact in one request. */
export function registerCase(input: IntakeInput): Promise<LegalCase> {
  const empty = (value: string) => (value.trim() === '' ? null : value.trim());
  return api(`${BASE}/intake`, {
    method: 'POST',
    body: {
      automatedNo: empty(input.automatedNo),
      title: input.title,
      caseType: input.caseType === '' ? null : input.caseType,
      subject: empty(input.subject),
      openedOn: empty(input.openedOn),
      firstInstance: {
        courtId: empty(input.courtId),
        courtCaseNumber: empty(input.courtCaseNumber),
        courtCaseYear: empty(input.courtCaseYear) === null ? null : Number(input.courtCaseYear),
        filingDate: empty(input.filingDate),
      },
      authorityPosition: input.authorityPosition === '' ? null : input.authorityPosition,
      parties: input.parties,
      primaryMemberId: empty(input.primaryMemberId),
      alternateMemberId: empty(input.alternateMemberId),
      financialImpact: input.financialImpact === '' ? null : input.financialImpact,
      ...(input.financialImpact === 'YES' ? { financialAmount: empty(input.financialAmount) } : {}),
    },
  });
}
