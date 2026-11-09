import type { Directive, Draft, ListValue, SearchItem, Settlement, Template, TemplateSummary, TemplateVersion } from './api';

/** Test-only fixtures of the WP9 screens; the scripted backend and the providers are those of the WP3 screens. */
export { CASE, mount, page, problem, serve, type Request } from '../followup/testSupport';

export function listValue(code: string, labelAr: string, labelEn: string | null, listCode: ListValue['listCode']): ListValue {
  return { id: `v-${code}`, listCode, code, labelAr, labelEn, origin: 'DOCUMENT', active: true, sortOrder: 1, version: 0, updatedAt: '2026-11-01T00:00:00Z' };
}

export const SETTLEMENT_KINDS = [
  listValue('SETTLEMENT', 'تسوية', 'Settlement', 'SETTLEMENT_KIND'),
  listValue('WAIVER', 'تنازل', 'Waiver', 'SETTLEMENT_KIND'),
];
export const DIRECTIVE_KINDS = [listValue('PREPARE_MEMO', 'إعداد مذكرة', 'Prepare a memorandum', 'DIRECTIVE_KIND')];
export const TEMPLATE_KINDS = [listValue('DEFENSE_MEMO', 'مذكرة دفاع', 'Defence memorandum', 'TEMPLATE_KIND')];

export function settlement(overrides: Partial<Settlement> = {}): Settlement {
  return {
    id: 's1',
    caseId: 'c0000000-0000-4000-8000-000000000001',
    kind: 'SETTLEMENT',
    status: 'PROPOSED',
    offeringPartyId: 'p1',
    offeringPartyName: 'Nile Trading',
    offeringPartyText: null,
    settlementAmount: '125000.500',
    legalGrounds: 'Avoids the cost of litigation',
    financialImpactNote: null,
    legalOpinion: null,
    approvingAuthority: null,
    decisionReason: null,
    decidedBy: null,
    decidedAt: null,
    completedOn: null,
    documents: [],
    version: 0,
    createdAt: '2026-11-01T08:00:00Z',
    updatedAt: '2026-11-01T08:00:00Z',
    ...overrides,
  };
}

export function directive(overrides: Partial<Directive> = {}): Directive {
  return {
    id: 'd1',
    directiveNo: 'DIR-2026-00001',
    caseId: 'c0000000-0000-4000-8000-000000000001',
    caseReferenceNo: 'LD-2026-00001',
    caseTitle: 'Ahmed v. Nile Trading',
    taskId: null,
    kind: 'PREPARE_MEMO',
    title: 'Prepare the reply memorandum',
    details: null,
    directiveDate: '2026-11-01',
    source: 'Department manager',
    sourceReferenceNo: null,
    addresseeUserId: 'u1',
    addresseeText: null,
    addresseeName: 'Mona Saleh',
    dueDate: '2026-11-10',
    status: 'NEW',
    statusNote: null,
    completedAt: null,
    overdue: false,
    version: 0,
    ...overrides,
  };
}

export function templateSummary(overrides: Partial<TemplateSummary> = {}): TemplateSummary {
  return {
    id: 't1',
    kind: 'DEFENSE_MEMO',
    title: 'Standard defence memorandum',
    description: null,
    origin: 'PROPOSED',
    active: true,
    versionCount: 1,
    latestApprovedVersionNo: 1,
    version: 0,
    ...overrides,
  };
}

export function templateVersion(overrides: Partial<TemplateVersion> = {}): TemplateVersion {
  return {
    id: 'tv1',
    templateId: 't1',
    versionNo: 1,
    status: 'DRAFT',
    body: 'Case {{case.referenceNo}}',
    changeNote: null,
    placeholders: [{ key: 'case.referenceNo', known: true, labelAr: 'الرقم الداخلي للقضية', labelEn: 'Internal case number' }],
    reviewedAt: null,
    approvedAt: null,
    version: 0,
    ...overrides,
  };
}

export function template(overrides: Partial<Template> = {}): Template {
  const { versionCount: _unused, ...summary } = { ...templateSummary(), versionCount: 0 };
  return { ...summary, versions: [templateVersion()], ...overrides };
}

export function draft(overrides: Partial<Draft> = {}): Draft {
  return {
    templateId: 't1',
    templateTitle: 'Standard defence memorandum',
    kind: 'DEFENSE_MEMO',
    versionId: 'tv1',
    versionNo: 1,
    versionStatus: 'APPROVED',
    caseId: 'c0000000-0000-4000-8000-000000000001',
    draft: true,
    final: false,
    text: 'Case LD-2026-00001 before {{case.chamber}}',
    filled: [{ key: 'case.referenceNo', labelAr: 'الرقم الداخلي للقضية', labelEn: 'Internal case number' }],
    missing: [{ key: 'case.chamber', labelAr: 'الدائرة', labelEn: 'Chamber' }],
    unknown: [],
    notice: 'draft',
    ...overrides,
  };
}

export function hit(overrides: Partial<SearchItem> = {}): SearchItem {
  return {
    id: 'h1',
    type: 'CASE',
    caseId: 'c0000000-0000-4000-8000-000000000001',
    caseReferenceNo: 'LD-2026-00001',
    title: 'Ahmed v. Nile Trading',
    detail: 'OPEN',
    date: '2026-03-01',
    snippet: '… unpaid invoices of the supplier …',
    matchedIn: ['SUBJECT'],
    rank: 0.4,
    caseCount: null,
    ...overrides,
  };
}
