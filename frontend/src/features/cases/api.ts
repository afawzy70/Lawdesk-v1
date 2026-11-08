import { api } from '../../api/http';

/** Case API (docs/delivery/PHASE-3-ACCEPTANCE.md, LD-012, LD-013, LD-015); every call goes through `api()`. */

export const CASE_TYPES = [
  'CIVIL',
  'CRIMINAL',
  'COMMERCIAL',
  'LABOR',
  'FAMILY',
  'ADMINISTRATIVE',
  'CONSTITUTIONAL',
  'OTHER',
] as const;
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
  /** Chamber, receipt date, referrer, facts, claimed amount, importance and internal notes (LD-050). */
  details?: CaseDetails;
}

export const IMPORTANCE_LEVELS = ['LOW', 'NORMAL', 'HIGH', 'CRITICAL'] as const;
export type Importance = (typeof IMPORTANCE_LEVELS)[number];

/** The case record beyond its numbers and parties as the server returns it; every value may be absent. */
export interface CaseDetails {
  chamber?: string | null;
  receivedOn?: string | null;
  referringUnitId?: string | null;
  referredBy?: string | null;
  facts?: string | null;
  /** KWD with three decimals, as text. */
  claimedAmount?: string | null;
  importance?: Importance | null;
  internalNotes?: string | null;
}

/** The same fields as a form holds them: empty strings mean "not given". */
export interface DetailsInput {
  chamber: string;
  receivedOn: string;
  referringUnitId: string;
  referredBy: string;
  facts: string;
  claimedAmount: string;
  importance: Importance | '';
  internalNotes: string;
}

export const EMPTY_DETAILS: DetailsInput = {
  chamber: '',
  receivedOn: '',
  referringUnitId: '',
  referredBy: '',
  facts: '',
  claimedAmount: '',
  importance: '',
  internalNotes: '',
};

export function detailsInputOf(details: CaseDetails | undefined): DetailsInput {
  return {
    chamber: details?.chamber ?? '',
    receivedOn: details?.receivedOn ?? '',
    referringUnitId: details?.referringUnitId ?? '',
    referredBy: details?.referredBy ?? '',
    facts: details?.facts ?? '',
    claimedAmount: details?.claimedAmount ?? '',
    importance: details?.importance ?? '',
    internalNotes: details?.internalNotes ?? '',
  };
}

/** The request form of the details: empty values go out as null, a comma decimal separator as a point. */
export function detailsBody(input: DetailsInput) {
  const empty = (value: string) => (value.trim() === '' ? null : value.trim());
  return {
    chamber: empty(input.chamber),
    receivedOn: empty(input.receivedOn),
    referringUnitId: empty(input.referringUnitId),
    referredBy: empty(input.referredBy),
    facts: empty(input.facts),
    claimedAmount: empty(input.claimedAmount)?.replace(',', '.') ?? null,
    importance: input.importance === '' ? null : input.importance,
    internalNotes: empty(input.internalNotes),
  };
}

/** Up to twelve digits and three decimals, as the server accepts; empty is allowed. */
export const CLAIMED_AMOUNT = /^\d{1,12}([.,]\d{1,3})?$/;

/** Whether the claimed amount is acceptable: empty, or up to twelve digits and three decimals. */
export function claimedAmountValid(value: string): boolean {
  return value.trim() === '' || CLAIMED_AMOUNT.test(value.trim());
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
  /** The case details; sent as a whole, so the form must start from the stored values. */
  details: DetailsInput;
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
      ...(extras === undefined ? {} : { details: detailsBody(extras.details) }),
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
  details: DetailsInput;
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
      details: detailsBody(input.details),
    },
  });
}

/** An administrative unit a case concerns, with the structure's names as they were when it was recorded (section 6). */
export interface Involvement {
  id: string;
  caseId: string;
  orgUnitId: string | null;
  primary: boolean;
  sectorName: string | null;
  departmentName: string | null;
  sectionName: string | null;
  contactName: string | null;
  contactInfo: string | null;
  referralDate: string | null;
  requestedFromUnit: string | null;
  notes: string | null;
  version: number;
}

export interface NewInvolvement {
  orgUnitId: string;
  sectorName: string;
  departmentName: string;
  sectionName: string;
  primary: boolean;
  contactName: string;
  contactInfo: string;
  referralDate: string;
  requestedFromUnit: string;
  notes: string;
}

export function listInvolvements(caseId: string, signal?: AbortSignal): Promise<Involvement[]> {
  return api(`${BASE}/${encodeURIComponent(caseId)}/involvements`, signal === undefined ? {} : { signal });
}

export function addInvolvement(caseId: string, value: NewInvolvement): Promise<Involvement> {
  const empty = (text: string) => (text.trim() === '' ? null : text.trim());
  return api(`${BASE}/${encodeURIComponent(caseId)}/involvements`, {
    method: 'POST',
    body: {
      orgUnitId: empty(value.orgUnitId),
      sectorName: empty(value.sectorName),
      departmentName: empty(value.departmentName),
      sectionName: empty(value.sectionName),
      primary: value.primary,
      contactName: empty(value.contactName),
      contactInfo: empty(value.contactInfo),
      referralDate: empty(value.referralDate),
      requestedFromUnit: empty(value.requestedFromUnit),
      notes: empty(value.notes),
    },
  });
}

export function removeInvolvement(caseId: string, id: string): Promise<void> {
  return api(`${BASE}/${encodeURIComponent(caseId)}/involvements/${encodeURIComponent(id)}`, { method: 'DELETE' });
}
