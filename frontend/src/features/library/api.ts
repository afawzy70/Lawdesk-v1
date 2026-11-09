import { api } from '../../api/http';

/** The judgments and precedents library (LD-052, requirements section 16): `/api/v1/library` and the case routes. */
export const COURT_LEVELS = ['FIRST_INSTANCE', 'APPEAL', 'CASSATION', 'CONSTITUTIONAL', 'OTHER'] as const;
export type LibraryCourtLevel = (typeof COURT_LEVELS)[number];
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
export const DIRECTIONS = ['FOR_AUTHORITY', 'AGAINST_AUTHORITY', 'OTHER'] as const;
export type Direction = (typeof DIRECTIONS)[number];
export const FINALITIES = ['FINAL', 'NOT_FINAL', 'NOT_SPECIFIED'] as const;
export type Finality = (typeof FINALITIES)[number];
export const CONFIDENTIALITIES = ['NORMAL', 'RESTRICTED'] as const;
export type LibraryConfidentiality = (typeof CONFIDENTIALITIES)[number];
export const RELATION_TYPES = ['APPEALED', 'UPHELD', 'MODIFIED', 'OVERTURNED', 'LATER', 'DEPARTED_FROM'] as const;
export type RelationType = (typeof RELATION_TYPES)[number];
export const DIMENSIONS = ['CASE_KIND', 'LEGAL_TOPIC', 'REQUEST_TYPE', 'DEFENSE_TYPE', 'OUTCOME', 'SECTOR'] as const;
export type Dimension = (typeof DIMENSIONS)[number];

export interface Classification {
  id: string;
  dimension: Dimension;
  code: string;
  labelAr: string;
  labelEn: string | null;
  origin: 'DOCUMENT' | 'PROPOSED';
  active: boolean;
  sortOrder: number;
  version: number;
  updatedAt: string;
}

export interface ListItem {
  id: string;
  courtName: string;
  courtLevel: LibraryCourtLevel | null;
  chamber: string | null;
  caseNumber: string | null;
  caseYear: number | null;
  judgmentDate: string | null;
  caseType: string | null;
  subject: string;
  direction: Direction | null;
  finality: Finality;
  confidentiality: LibraryConfidentiality;
  status: 'ACTIVE' | 'WITHDRAWN';
  classifications: Classification[];
  principleCount: number;
  relationTypes: RelationType[];
  score: number;
  matchedIn: string[];
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface Principle {
  id: string;
  judgmentId: string;
  principleText: string;
  legalTopic: string | null;
  legalBasis: string | null;
  factsApplied: string | null;
  scopeNote: string | null;
  memberNotes: string | null;
  reviewStatus: 'DRAFT' | 'REVIEWED';
  reviewedBy: string | null;
  reviewedAt: string | null;
  version: number;
  courtName: string;
  caseNumber: string | null;
  caseYear: number | null;
  judgmentDate: string | null;
}

export interface Relation {
  id: string;
  direction: 'OUTGOING' | 'INCOMING';
  relationType: RelationType;
  judgmentId: string;
  otherJudgmentId: string | null;
  otherSubject: string | null;
  otherCourtName: string | null;
  otherCaseNumber: string | null;
  otherCaseYear: number | null;
  otherJudgmentDate: string | null;
  relatedCitation: string | null;
  relationDate: string | null;
  sourceReference: string;
  notes: string | null;
}

export interface Validity {
  judgmentId: string;
  finality: Finality;
  status: 'ACTIVE' | 'WITHDRAWN';
  relationCount: number;
  relationTypes: RelationType[];
  warnings: string[];
  legalValidityDecided: boolean;
  note: string;
}

export interface LinkedCase {
  linkId: string;
  caseId: string;
  referenceNo: string;
  title: string;
  reason: string;
}

export interface Judgment {
  id: string;
  courtName: string;
  courtLevel: LibraryCourtLevel | null;
  chamber: string | null;
  caseNumber: string | null;
  caseYear: number | null;
  judgmentDate: string | null;
  caseType: string | null;
  subject: string;
  partiesDescription: string | null;
  partiesMasked: boolean;
  summary: string | null;
  operativeText: string | null;
  reasons: string | null;
  legalTexts: string | null;
  requestsAddressed: string | null;
  requestResults: string | null;
  direction: Direction | null;
  keywords: string[];
  finality: Finality;
  confidentiality: LibraryConfidentiality;
  sourceReference: string;
  verificationNote: string;
  copyReference: string | null;
  status: 'ACTIVE' | 'WITHDRAWN';
  withdrawnReason: string | null;
  classifications: Classification[];
  principles: Principle[];
  relations: { outgoing: Relation[]; incoming: Relation[] };
  validity: Validity;
  linkedCases: LinkedCase[];
  version: number;
  createdAt: string;
  createdBy: string;
  updatedAt: string;
}

/** The fields of the add/edit form; empty text is sent as null (not specified). */
export interface JudgmentInput {
  courtName: string;
  courtLevel: LibraryCourtLevel | null;
  chamber: string | null;
  caseNumber: string | null;
  caseYear: number | null;
  judgmentDate: string | null;
  caseType: string | null;
  subject: string;
  partiesDescription: string | null;
  summary: string | null;
  operativeText: string | null;
  reasons: string | null;
  legalTexts: string | null;
  requestsAddressed: string | null;
  requestResults: string | null;
  direction: Direction | null;
  keywords: string[];
  finality: Finality;
  confidentiality: LibraryConfidentiality;
  sourceReference: string;
  verificationNote: string;
  copyReference: string | null;
  classificationIds: string[];
  allowDuplicate?: boolean;
  version?: number;
}

export interface SearchFilters {
  q: string;
  courtName: string;
  year: string;
  caseType: string;
  courtLevel: string;
  direction: string;
  finality: string;
  classificationId: string;
  includeWithdrawn: boolean;
  page: number;
}

export const EMPTY_FILTERS: SearchFilters = {
  q: '',
  courtName: '',
  year: '',
  caseType: '',
  courtLevel: '',
  direction: '',
  finality: '',
  classificationId: '',
  includeWithdrawn: false,
  page: 0,
};

const BASE = '/api/v1/library';

/** Request options carrying an abort signal when there is one. */
const signalOf = (signal?: AbortSignal): { signal?: AbortSignal } => (signal === undefined ? {} : { signal });

export function searchQuery(filters: SearchFilters): string {
  const params = new URLSearchParams();
  const text: [string, string][] = [
    ['q', filters.q.trim()],
    ['courtName', filters.courtName.trim()],
    ['year', filters.year.trim()],
    ['caseType', filters.caseType],
    ['courtLevel', filters.courtLevel],
    ['direction', filters.direction],
    ['finality', filters.finality],
    ['classificationId', filters.classificationId],
  ];
  for (const [key, value] of text) {
    if (value !== '') params.set(key, value);
  }
  if (filters.includeWithdrawn) params.set('includeWithdrawn', 'true');
  params.set('page', String(filters.page));
  params.set('size', '20');
  return params.toString();
}

export const searchJudgments = (filters: SearchFilters, signal?: AbortSignal): Promise<Page<ListItem>> =>
  api(`${BASE}/judgments?${searchQuery(filters)}`, signalOf(signal));

export const getJudgment = (id: string, signal?: AbortSignal): Promise<Judgment> =>
  api(`${BASE}/judgments/${id}`, signalOf(signal));

export const registerJudgment = (body: JudgmentInput): Promise<Judgment> =>
  api(`${BASE}/judgments`, { method: 'POST', body });

export const replaceJudgment = (id: string, body: JudgmentInput): Promise<Judgment> =>
  api(`${BASE}/judgments/${id}`, { method: 'PUT', body });

export const withdrawJudgment = (id: string, reason: string, version: number): Promise<Judgment> =>
  api(`${BASE}/judgments/${id}/withdraw`, { method: 'POST', body: { reason, version } });

export const reinstateJudgment = (id: string, version: number): Promise<Judgment> =>
  api(`${BASE}/judgments/${id}/reinstate`, { method: 'POST', body: { version } });

export interface PrincipleInput {
  principleText: string;
  legalTopic: string | null;
  legalBasis: string | null;
  factsApplied: string | null;
  scopeNote: string | null;
  memberNotes: string | null;
  version?: number;
}

export const addPrinciple = (judgmentId: string, body: PrincipleInput): Promise<Principle> =>
  api(`${BASE}/judgments/${judgmentId}/principles`, { method: 'POST', body });

export const replacePrinciple = (id: string, body: PrincipleInput): Promise<Principle> =>
  api(`${BASE}/principles/${id}`, { method: 'PUT', body });

export const reviewPrinciple = (id: string, version: number): Promise<Principle> =>
  api(`${BASE}/principles/${id}/review`, { method: 'POST', body: { version } });

export interface RelationInput {
  relationType: RelationType;
  relatedJudgmentId: string | null;
  relatedCitation: string | null;
  relationDate: string | null;
  sourceReference: string;
  notes: string | null;
}

export const addRelation = (judgmentId: string, body: RelationInput): Promise<unknown> =>
  api(`${BASE}/judgments/${judgmentId}/relations`, { method: 'POST', body });

export const removeRelation = (id: string): Promise<void> => api(`${BASE}/relations/${id}`, { method: 'DELETE' });

export const listClassifications = (dimension?: Dimension, includeInactive = false): Promise<Classification[]> =>
  api(
    `${BASE}/classifications?${dimension === undefined ? '' : `dimension=${dimension}&`}includeInactive=${String(includeInactive)}`,
  );

export const addClassification = (body: {
  dimension: Dimension;
  code: string;
  labelAr: string;
  labelEn: string | null;
}): Promise<Classification> => api(`${BASE}/classifications`, { method: 'POST', body });

export const replaceClassification = (
  item: Classification,
  changes: Partial<Pick<Classification, 'labelAr' | 'labelEn' | 'active'>>,
): Promise<Classification> =>
  api(`${BASE}/classifications/${item.id}`, {
    method: 'PUT',
    body: { labelAr: item.labelAr, labelEn: item.labelEn, active: item.active, ...changes, version: item.version },
  });

export interface Count {
  key: string | null;
  label: string | null;
  count: number;
}

export interface Summary {
  total: number;
  active: number;
  withdrawn: number;
  byCourt: Count[];
  byYear: Count[];
  byDirection: Count[];
  byFinality: Count[];
  byClassification: {
    dimension: Dimension;
    classificationId: string;
    code: string;
    labelAr: string;
    labelEn: string | null;
    count: number;
  }[];
  frequentIssues: {
    topic: string;
    principleCount: number;
    judgmentCount: number;
    firstJudgmentDate: string | null;
    lastJudgmentDate: string | null;
  }[];
  note: string;
}

export const getSummary = (signal?: AbortSignal): Promise<Summary> =>
  api(`${BASE}/reports/summary`, signalOf(signal));

// ---- from a case ---------------------------------------------------------------------------------------------------

export interface JudgmentRef {
  id: string;
  courtName: string;
  courtLevel: LibraryCourtLevel | null;
  caseNumber: string | null;
  caseYear: number | null;
  judgmentDate: string | null;
  caseType: string | null;
  subject: string;
  finality: Finality;
  status: 'ACTIVE' | 'WITHDRAWN';
  sourceReference: string;
  hasCopy: boolean;
}

export interface Precedent {
  id: string;
  caseId: string;
  judgment: JudgmentRef;
  reason: string;
  similarityNote: string | null;
  origin: 'MANUAL' | 'SUGGESTION';
  relationTypes: RelationType[];
}

export interface Suggestion {
  judgment: JudgmentRef;
  score: number;
  reasons: { code: string; points: number; terms: string[] }[];
  differences: { code: string; detail: string }[];
  relationTypes: RelationType[];
  warnings: string[];
  alreadyLinked: boolean;
}

export interface Suggestions {
  caseId: string;
  basis: { terms: string[]; caseType: string | null; courtLevels: string[]; defensesIncluded: boolean };
  items: Suggestion[];
  note: string;
}

export const listPrecedents = (caseId: string, signal?: AbortSignal): Promise<Precedent[]> =>
  api(`/api/v1/cases/${caseId}/precedents`, signalOf(signal));

export const linkPrecedent = (
  caseId: string,
  body: { judgmentId: string; reason: string; similarityNote: string | null; origin: 'MANUAL' | 'SUGGESTION' },
): Promise<Precedent> => api(`/api/v1/cases/${caseId}/precedents`, { method: 'POST', body });

export const unlinkPrecedent = (caseId: string, linkId: string): Promise<void> =>
  api(`/api/v1/cases/${caseId}/precedents/${linkId}`, { method: 'DELETE' });

export const similarJudgments = (caseId: string, signal?: AbortSignal): Promise<Suggestions> =>
  api(`/api/v1/cases/${caseId}/similar-judgments`, signalOf(signal));
