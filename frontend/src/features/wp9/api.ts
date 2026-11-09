import { api } from '../../api/http';
import type { Locale } from '../../i18n/locale';

/** LD-057 (WP9): managed lists, settlements, directives, legal templates and the unified search. */

export const blankToNull = (value: string): string | null => (value.trim() === '' ? null : value.trim());

// ---- managed lists (owner decision D10) ---------------------------------------------------------------------------

export type ListCode = 'SETTLEMENT_KIND' | 'DIRECTIVE_KIND' | 'TEMPLATE_KIND';

export interface ListValue {
  id: string;
  listCode: ListCode;
  code: string;
  labelAr: string;
  labelEn: string | null;
  origin: 'DOCUMENT' | 'PROPOSED';
  active: boolean;
  sortOrder: number;
  version: number;
  updatedAt: string;
}

export const valueLabel = (value: Pick<ListValue, 'labelAr' | 'labelEn'>, locale: Locale): string =>
  locale === 'ar' ? value.labelAr : (value.labelEn ?? value.labelAr);

/** The label of a code in a list, falling back to the code itself for a value the list no longer offers. */
export function labelOf(values: readonly ListValue[], code: string | null, locale: Locale): string {
  if (code === null) return '';
  const found = values.find((value) => value.code === code);
  return found === undefined ? code : valueLabel(found, locale);
}

export function listValues(list: ListCode, signal?: AbortSignal, includeInactive = false): Promise<ListValue[]> {
  const query = includeInactive ? '?includeInactive=true' : '';
  return api<ListValue[]>(`/api/v1/lists/${list}${query}`, signal === undefined ? {} : { signal });
}

export function addListValue(list: ListCode, input: { code: string; labelAr: string; labelEn: string }): Promise<ListValue> {
  return api<ListValue>(`/api/v1/lists/${list}`, {
    method: 'POST',
    body: { code: input.code.trim(), labelAr: input.labelAr.trim(), labelEn: blankToNull(input.labelEn) },
  });
}

// ---- settlements (section 23) -------------------------------------------------------------------------------------

export const SETTLEMENT_STATUSES = ['PROPOSED', 'UNDER_REVIEW', 'APPROVED', 'REJECTED', 'EXECUTED', 'WITHDRAWN'] as const;
export type SettlementStatus = (typeof SETTLEMENT_STATUSES)[number];

/** Where a settlement may go from each state; the server decides, this only offers the buttons. */
export const SETTLEMENT_NEXT: Record<SettlementStatus, readonly SettlementStatus[]> = {
  PROPOSED: ['UNDER_REVIEW', 'APPROVED', 'REJECTED', 'WITHDRAWN'],
  UNDER_REVIEW: ['PROPOSED', 'APPROVED', 'REJECTED', 'WITHDRAWN'],
  APPROVED: ['EXECUTED', 'WITHDRAWN'],
  REJECTED: ['PROPOSED'],
  EXECUTED: [],
  WITHDRAWN: [],
};

export interface SettlementDocument {
  id: string;
  title: string;
  fileName: string;
}

export interface Settlement {
  id: string;
  caseId: string;
  kind: string;
  status: SettlementStatus;
  offeringPartyId: string | null;
  offeringPartyName: string | null;
  offeringPartyText: string | null;
  /** KWD with three decimals, as text; the settlement's own amount, never mixed with another amount of the case. */
  settlementAmount: string | null;
  legalGrounds: string | null;
  financialImpactNote: string | null;
  legalOpinion: string | null;
  approvingAuthority: string | null;
  decisionReason: string | null;
  decidedBy: string | null;
  decidedAt: string | null;
  completedOn: string | null;
  documents: SettlementDocument[];
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface SettlementInput {
  kind: string;
  offeringPartyId: string;
  offeringPartyText: string;
  settlementAmount: string;
  legalGrounds: string;
  financialImpactNote: string;
  legalOpinion: string;
  approvingAuthority: string;
}

export const EMPTY_SETTLEMENT: SettlementInput = {
  kind: '',
  offeringPartyId: '',
  offeringPartyText: '',
  settlementAmount: '',
  legalGrounds: '',
  financialImpactNote: '',
  legalOpinion: '',
  approvingAuthority: '',
};

export function settlementInputOf(settlement: Settlement): SettlementInput {
  return {
    kind: settlement.kind,
    offeringPartyId: settlement.offeringPartyId ?? '',
    offeringPartyText: settlement.offeringPartyText ?? '',
    settlementAmount: settlement.settlementAmount ?? '',
    legalGrounds: settlement.legalGrounds ?? '',
    financialImpactNote: settlement.financialImpactNote ?? '',
    legalOpinion: settlement.legalOpinion ?? '',
    approvingAuthority: settlement.approvingAuthority ?? '',
  };
}

function settlementBody(input: SettlementInput) {
  return {
    kind: input.kind,
    offeringPartyId: blankToNull(input.offeringPartyId),
    offeringPartyText: blankToNull(input.offeringPartyText),
    settlementAmount: blankToNull(input.settlementAmount),
    legalGrounds: blankToNull(input.legalGrounds),
    financialImpactNote: blankToNull(input.financialImpactNote),
    legalOpinion: blankToNull(input.legalOpinion),
    approvingAuthority: blankToNull(input.approvingAuthority),
  };
}

const settlementPath = (id: string) => `/api/v1/settlements/${encodeURIComponent(id)}`;

export function listSettlements(caseId: string, signal?: AbortSignal): Promise<Settlement[]> {
  return api<Settlement[]>(`/api/v1/cases/${encodeURIComponent(caseId)}/settlements`, signal === undefined ? {} : { signal });
}

export function createSettlement(caseId: string, input: SettlementInput): Promise<Settlement> {
  return api<Settlement>(`/api/v1/cases/${encodeURIComponent(caseId)}/settlements`, {
    method: 'POST',
    body: settlementBody(input),
  });
}

export function updateSettlement(settlement: Settlement, input: SettlementInput): Promise<Settlement> {
  return api<Settlement>(settlementPath(settlement.id), {
    method: 'PUT',
    body: { ...settlementBody(input), version: settlement.version },
  });
}

export function changeSettlementStatus(
  settlement: Settlement,
  change: { status: SettlementStatus; reason: string; completedOn: string },
): Promise<Settlement> {
  return api<Settlement>(`${settlementPath(settlement.id)}/status`, {
    method: 'POST',
    body: {
      status: change.status,
      reason: blankToNull(change.reason),
      completedOn: change.status === 'EXECUTED' ? blankToNull(change.completedOn) : null,
      version: settlement.version,
    },
  });
}

export interface HistoryEntry {
  id: string;
  action: 'CREATED' | 'UPDATED' | 'STATUS_CHANGED' | 'DOCUMENT_LINKED' | 'DOCUMENT_UNLINKED';
  fromStatus: SettlementStatus | null;
  toStatus: SettlementStatus | null;
  changes: Record<string, { from: string | null; to: string | null }> | null;
  note: string | null;
  actorId: string;
  actorName: string | null;
  occurredAt: string;
}

export function settlementHistory(id: string, signal?: AbortSignal): Promise<HistoryEntry[]> {
  return api<HistoryEntry[]>(`${settlementPath(id)}/history`, signal === undefined ? {} : { signal });
}

export function linkSettlementDocument(id: string, documentId: string): Promise<Settlement> {
  return api<Settlement>(`${settlementPath(id)}/documents/${encodeURIComponent(documentId)}`, { method: 'PUT', body: {} });
}

export function unlinkSettlementDocument(id: string, documentId: string): Promise<Settlement> {
  return api<Settlement>(`${settlementPath(id)}/documents/${encodeURIComponent(documentId)}`, { method: 'DELETE' });
}

// ---- directives (section 25) --------------------------------------------------------------------------------------

export const DIRECTIVE_STATUSES = ['NEW', 'IN_PROGRESS', 'DONE', 'CANCELLED'] as const;
export type DirectiveStatus = (typeof DIRECTIVE_STATUSES)[number];

export interface Directive {
  id: string;
  directiveNo: string;
  caseId: string;
  caseReferenceNo: string;
  caseTitle: string;
  taskId: string | null;
  kind: string | null;
  title: string;
  details: string | null;
  directiveDate: string;
  source: string;
  sourceReferenceNo: string | null;
  addresseeUserId: string | null;
  addresseeText: string | null;
  addresseeName: string | null;
  dueDate: string | null;
  status: DirectiveStatus;
  statusNote: string | null;
  completedAt: string | null;
  overdue: boolean;
  version: number;
}

export interface DirectivePage {
  items: Directive[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface DirectiveFilter {
  q: string;
  status: string;
  kind: string;
  overdue: boolean;
  mine: boolean;
  caseId: string;
  page: number;
}

export const EMPTY_DIRECTIVE_FILTER: DirectiveFilter = { q: '', status: '', kind: '', overdue: false, mine: false, caseId: '', page: 0 };

export function searchDirectives(filter: DirectiveFilter, signal?: AbortSignal): Promise<DirectivePage> {
  const query = new URLSearchParams({ page: String(filter.page), size: '20' });
  if (filter.q.trim() !== '') query.set('q', filter.q.trim());
  if (filter.status !== '') query.set('status', filter.status);
  if (filter.kind !== '') query.set('kind', filter.kind);
  if (filter.overdue) query.set('overdue', 'true');
  if (filter.mine) query.set('addressee', 'me');
  if (filter.caseId !== '') query.set('caseId', filter.caseId);
  return api<DirectivePage>(`/api/v1/directives?${query.toString()}`, signal === undefined ? {} : { signal });
}

export interface DirectiveInput {
  caseId: string;
  kind: string;
  title: string;
  details: string;
  directiveDate: string;
  source: string;
  sourceReferenceNo: string;
  addresseeUserId: string;
  addresseeText: string;
  dueDate: string;
}

export function createDirective(input: DirectiveInput): Promise<Directive> {
  return api<Directive>('/api/v1/directives', {
    method: 'POST',
    body: {
      caseId: input.caseId,
      kind: blankToNull(input.kind),
      title: input.title.trim(),
      details: blankToNull(input.details),
      directiveDate: input.directiveDate,
      source: input.source.trim(),
      sourceReferenceNo: blankToNull(input.sourceReferenceNo),
      addresseeUserId: blankToNull(input.addresseeUserId),
      addresseeText: blankToNull(input.addresseeText),
      dueDate: blankToNull(input.dueDate),
    },
  });
}

export function changeDirectiveStatus(directive: Directive, status: DirectiveStatus, note: string): Promise<Directive> {
  return api<Directive>(`/api/v1/directives/${encodeURIComponent(directive.id)}/status`, {
    method: 'POST',
    body: { status, note: blankToNull(note), version: directive.version },
  });
}

// ---- legal templates (section 29) ---------------------------------------------------------------------------------

export interface TemplateSummary {
  id: string;
  kind: string;
  title: string;
  description: string | null;
  origin: 'DOCUMENT' | 'PROPOSED';
  active: boolean;
  versionCount: number;
  latestApprovedVersionNo: number | null;
  version: number;
}

export interface Placeholder {
  key: string;
  known: boolean;
  labelAr: string | null;
  labelEn: string | null;
}

export type VersionStatus = 'DRAFT' | 'REVIEWED' | 'APPROVED';

export interface TemplateVersion {
  id: string;
  templateId: string;
  versionNo: number;
  status: VersionStatus;
  body: string | null;
  changeNote: string | null;
  placeholders: Placeholder[];
  reviewedAt: string | null;
  approvedAt: string | null;
  version: number;
}

export interface Template extends Omit<TemplateSummary, 'versionCount'> {
  versions: TemplateVersion[];
}

export interface FieldInfo {
  key: string;
  labelAr: string;
  labelEn: string;
}

export function listTemplates(signal?: AbortSignal, includeInactive = false): Promise<TemplateSummary[]> {
  const query = includeInactive ? '?includeInactive=true' : '';
  return api<TemplateSummary[]>(`/api/v1/templates${query}`, signal === undefined ? {} : { signal });
}

export function getTemplate(id: string, signal?: AbortSignal): Promise<Template> {
  return api<Template>(`/api/v1/templates/${encodeURIComponent(id)}`, signal === undefined ? {} : { signal });
}

export function templateFields(signal?: AbortSignal): Promise<FieldInfo[]> {
  return api<FieldInfo[]>('/api/v1/templates/fields', signal === undefined ? {} : { signal });
}

export function createTemplate(input: { kind: string; title: string; description: string; body: string }): Promise<Template> {
  return api<Template>('/api/v1/templates', {
    method: 'POST',
    body: {
      kind: input.kind,
      title: input.title.trim(),
      description: blankToNull(input.description),
      body: blankToNull(input.body),
    },
  });
}

export function setTemplateActive(template: Template, active: boolean): Promise<Template> {
  return api<Template>(`/api/v1/templates/${encodeURIComponent(template.id)}`, {
    method: 'PUT',
    body: {
      kind: template.kind,
      title: template.title,
      description: template.description,
      active,
      version: template.version,
    },
  });
}

export function addTemplateVersion(templateId: string, body: string, changeNote: string): Promise<TemplateVersion> {
  return api<TemplateVersion>(`/api/v1/templates/${encodeURIComponent(templateId)}/versions`, {
    method: 'POST',
    body: { body: body.trim(), changeNote: blankToNull(changeNote) },
  });
}

export function editTemplateVersion(version: TemplateVersion, body: string, changeNote: string): Promise<TemplateVersion> {
  return api<TemplateVersion>(`/api/v1/template-versions/${encodeURIComponent(version.id)}`, {
    method: 'PUT',
    body: { body: body.trim(), changeNote: blankToNull(changeNote), version: version.version },
  });
}

export function stepTemplateVersion(version: TemplateVersion, step: 'review' | 'approve'): Promise<TemplateVersion> {
  return api<TemplateVersion>(`/api/v1/template-versions/${encodeURIComponent(version.id)}/${step}`, {
    method: 'POST',
    body: { version: version.version },
  });
}

export interface DraftField {
  key: string;
  labelAr: string;
  labelEn: string;
}

export interface Draft {
  templateId: string;
  templateTitle: string;
  kind: string;
  versionId: string;
  versionNo: number;
  versionStatus: VersionStatus;
  caseId: string;
  draft: boolean;
  final: boolean;
  text: string;
  filled: DraftField[];
  missing: DraftField[];
  unknown: string[];
  notice: string;
}

export function generateDraft(caseId: string, templateId: string): Promise<Draft> {
  return api<Draft>(`/api/v1/cases/${encodeURIComponent(caseId)}/template-drafts`, { method: 'POST', body: { templateId } });
}

// ---- unified search (section 28) ----------------------------------------------------------------------------------

export const SEARCH_TYPES = [
  'CASE',
  'PARTY',
  'CLAIM',
  'DEFENSE',
  'HEARING',
  'JUDGMENT',
  'DOCUMENT',
  'NOTE',
  'TASK',
  'LIBRARY',
  'SETTLEMENT',
  'DIRECTIVE',
] as const;
export type SearchType = (typeof SEARCH_TYPES)[number];

export interface SearchItem {
  id: string;
  type: SearchType;
  caseId: string | null;
  caseReferenceNo: string | null;
  title: string;
  detail: string | null;
  date: string | null;
  snippet: string | null;
  matchedIn: string[];
  rank: number;
  caseCount: number | null;
}

export interface SearchGroup {
  type: SearchType;
  total: number;
  page: number;
  size: number;
  items: SearchItem[];
}

export interface SearchResult {
  query: string;
  total: number;
  searchedTypes: SearchType[];
  groups: SearchGroup[];
}

export interface SearchFilters {
  q: string;
  types: SearchType[];
  caseType: string;
  from: string;
  to: string;
}

export function runSearch(
  filters: SearchFilters,
  paging: { page: number; size: number },
  signal?: AbortSignal,
): Promise<SearchResult> {
  const query = new URLSearchParams({ q: filters.q.trim(), page: String(paging.page), size: String(paging.size) });
  if (filters.types.length > 0) query.set('types', filters.types.join(','));
  if (filters.caseType !== '') query.set('caseType', filters.caseType);
  if (filters.from !== '') query.set('from', filters.from);
  if (filters.to !== '') query.set('to', filters.to);
  return api<SearchResult>(`/api/v1/search?${query.toString()}`, signal === undefined ? {} : { signal });
}
