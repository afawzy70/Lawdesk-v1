import { api } from '../../api/http';

/** The electronic archive, memos and correspondence, and judgment enforcement (LD-055, requirement sections 11, 12, 13). */

const enc = encodeURIComponent;

/** A list answer as an array; anything else (a failed or still missing load) counts as empty. */
export const asList = <T>(value: readonly T[] | null | undefined): readonly T[] => (Array.isArray(value) ? (value as readonly T[]) : []);
const read = <T>(path: string, signal?: AbortSignal) => api<T>(path, signal === undefined ? {} : { signal });
const send = <T>(method: 'POST' | 'PUT' | 'DELETE', path: string, body?: unknown) =>
  api<T>(path, body === undefined ? { method } : { method, body });

// ---- settings and formats -------------------------------------------------------------------------------------------

export interface ArchiveLimits {
  maxFileBytes: number;
  maxDocumentsPerCase: number;
  maxVersionsPerDocument: number;
  maxOcrPages: number;
  maxTextChars: number;
}

export interface ArchiveFormat {
  code: string;
  family: 'PDF' | 'WORD' | 'IMAGE';
  mediaType: string;
  extension: string;
  enabled: boolean;
}

export interface OcrStatus {
  available: boolean;
  engine: string | null;
  languages: string | null;
  detail: string | null;
}

export interface ArchiveSettings {
  limits: ArchiveLimits;
  formats: ArchiveFormat[];
  ocr: OcrStatus;
}

/** What the archive assumes when the settings cannot be read (the defaults of the migration). */
export const DEFAULT_LIMITS: ArchiveLimits = {
  maxFileBytes: 10 * 1024 * 1024,
  maxDocumentsPerCase: 100,
  maxVersionsPerDocument: 20,
  maxOcrPages: 50,
  maxTextChars: 2_000_000,
};

export const getArchiveSettings = (signal?: AbortSignal) => read<ArchiveSettings>('/api/v1/archive/settings', signal);

/** The extensions a file picker should offer: those of the enabled formats. */
export function acceptAttribute(formats: readonly ArchiveFormat[]): string {
  return formats
    .filter((f) => f.enabled)
    .flatMap((f) => [f.mediaType, `.${f.extension}`])
    .join(',');
}

// ---- versions, text, links -------------------------------------------------------------------------------------------

export const TEXT_STATUSES = ['PENDING', 'EXTRACTED', 'OCR_DONE', 'OCR_UNAVAILABLE', 'NO_TEXT', 'UNSUPPORTED', 'FAILED'] as const;
export type TextStatus = (typeof TEXT_STATUSES)[number];

export interface TextState {
  versionId: string;
  documentId: string;
  status: TextStatus;
  method: string | null;
  engine: string | null;
  detail: string | null;
  pageCount: number | null;
  truncated: boolean;
  text: string | null;
  /** Words other recognition passes were sure of and the page text lacks (searched, not shown as the page). */
  alternateReadings?: string | null;
  wordsRead?: number | null;
  /** Words the engine was unsure of; above zero a search may miss a word and the text may hold a misreading. */
  wordsUncertain?: number | null;
}

export interface DocumentVersion {
  id: string;
  versionNo: number;
  fileName: string;
  mediaType: string;
  sizeBytes: number;
  sha256: string;
  changeNote: string | null;
  uploadedBy: string;
  uploadedAt: string;
  current: boolean;
  text: TextState | null;
}

export const listVersions = (documentId: string, signal?: AbortSignal) =>
  read<DocumentVersion[]>(`/api/v1/documents/${enc(documentId)}/versions`, signal);

export const versionContentUrl = (documentId: string, versionNo: number) =>
  `/api/v1/documents/${enc(documentId)}/versions/${String(versionNo)}/content`;

export function addVersion(documentId: string, file: File, note: string): Promise<DocumentVersion> {
  const form = new FormData();
  form.append('file', file);
  if (note.trim() !== '') form.append('note', note.trim());
  return api<DocumentVersion>(`/api/v1/documents/${enc(documentId)}/versions`, { method: 'POST', body: form });
}

export const reprocessText = (documentId: string) => send<TextState>('POST', `/api/v1/documents/${enc(documentId)}/text/reprocess`, {});

export interface Classification {
  title: string;
  categoryId: string | null;
  documentDate: string | null;
  proceedingId: string | null;
  description: string | null;
  version: number;
}

export const classifyDocument = (documentId: string, body: Classification) =>
  send<unknown>('PUT', `/api/v1/documents/${enc(documentId)}/classification`, body);

export const withdrawDocument = (documentId: string, reason: string, version: number) =>
  send<undefined>('POST', `/api/v1/documents/${enc(documentId)}/withdraw`, { reason, version });

export const restoreDocument = (documentId: string, version: number) =>
  send<undefined>('POST', `/api/v1/documents/${enc(documentId)}/restore`, { version });

export const purgeDocument = (documentId: string, reason: string) =>
  send<undefined>('POST', `/api/v1/documents/${enc(documentId)}/permanent-deletion`, { reason });

export const LINK_TYPES = ['CLAIM', 'DEFENSE', 'HEARING', 'JUDGMENT', 'TASK'] as const;
export type LinkType = (typeof LINK_TYPES)[number];

export interface DocumentLink {
  id: string;
  documentId: string;
  documentTitle: string;
  targetType: LinkType;
  targetId: string;
  targetLabel: string | null;
  note: string | null;
  createdAt: string;
  createdBy: string;
}

export const listDocumentLinks = (documentId: string, signal?: AbortSignal) =>
  read<DocumentLink[]>(`/api/v1/documents/${enc(documentId)}/links`, signal);

export const addDocumentLink = (documentId: string, targetType: LinkType, targetId: string, note: string) =>
  send<DocumentLink>('POST', `/api/v1/documents/${enc(documentId)}/links`, {
    targetType,
    targetId,
    note: note.trim() === '' ? null : note.trim(),
  });

export const removeDocumentLink = (linkId: string) => send<undefined>('DELETE', `/api/v1/document-links/${enc(linkId)}`);

// ---- search ----------------------------------------------------------------------------------------------------------

export interface SearchHit {
  documentId: string;
  caseId: string;
  caseReferenceNo: string | null;
  caseTitle: string | null;
  title: string;
  fileName: string;
  versionNo: number;
  categoryId: string | null;
  status: TextStatus;
  snippet: string | null;
}

export interface SearchPage {
  items: SearchHit[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export function searchDocuments(query: string, categoryId: string, page: number, signal?: AbortSignal) {
  const params = new URLSearchParams({ q: query, page: String(page), size: '20' });
  if (categoryId !== '') params.set('categoryId', categoryId);
  return read<SearchPage>(`/api/v1/documents/search?${params.toString()}`, signal);
}

// ---- lookups ---------------------------------------------------------------------------------------------------------

export interface CategoryValue {
  id: string;
  code: string;
  labelAr: string;
  labelEn: string | null;
  active: boolean;
  needsApproval: boolean;
}

export const listCategories = (signal?: AbortSignal) => read<CategoryValue[]>('/api/v1/lookups/DOCUMENT_CATEGORY', signal);

// ---- memos and correspondence --------------------------------------------------------------------------------------

export const LEGAL_STATUSES = ['DRAFT', 'IN_REVIEW', 'APPROVED', 'SENT', 'ARCHIVED'] as const;
export type LegalStatus = (typeof LEGAL_STATUSES)[number];

export interface LegalDocument {
  id: string;
  caseId: string;
  caseReferenceNo: string | null;
  caseTitle: string | null;
  categoryId: string | null;
  categoryLabelAr: string | null;
  categoryLabelEn: string | null;
  title: string;
  documentDate: string | null;
  issuer: string | null;
  addressee: string | null;
  responsibleMemberId: string | null;
  responsibleMemberName: string | null;
  description: string | null;
  documentId: string | null;
  documentFileName: string | null;
  documentVersionNo: number | null;
  status: LegalStatus;
  submittedBy: string | null;
  submittedAt: string | null;
  approvedBy: string | null;
  approvedByName: string | null;
  approvedAt: string | null;
  approvalNote: string | null;
  sentRecorded: boolean;
  sentOn: string | null;
  sentMethod: string | null;
  sentReference: string | null;
  sentRecordedBy: string | null;
  sentRecordedAt: string | null;
  sentViaIntegration: boolean;
  archivedAt: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface LegalDocumentBody {
  title: string;
  categoryId: string | null;
  documentDate: string | null;
  issuer: string | null;
  addressee: string | null;
  responsibleMemberId: string | null;
  description: string | null;
  documentId: string | null;
  version?: number;
}

export interface LegalEvent {
  id: string;
  fromStatus: LegalStatus | null;
  toStatus: LegalStatus;
  note: string | null;
  createdAt: string;
  createdBy: string;
  createdByName: string | null;
}

const legalPath = (id: string) => `/api/v1/legal-documents/${enc(id)}`;

export const listCaseLegalDocuments = (caseId: string, signal?: AbortSignal) =>
  read<LegalDocument[]>(`/api/v1/cases/${enc(caseId)}/legal-documents`, signal);

export const createLegalDocument = (caseId: string, body: LegalDocumentBody) =>
  send<LegalDocument>('POST', `/api/v1/cases/${enc(caseId)}/legal-documents`, body);

export const updateLegalDocument = (id: string, body: LegalDocumentBody) => send<LegalDocument>('PUT', legalPath(id), body);

export function attachLegalFile(id: string, file: File, note: string): Promise<LegalDocument> {
  const form = new FormData();
  form.append('file', file);
  if (note.trim() !== '') form.append('note', note.trim());
  return api<LegalDocument>(`${legalPath(id)}/file`, { method: 'POST', body: form });
}

export const legalEvents = (id: string, signal?: AbortSignal) => read<LegalEvent[]>(`${legalPath(id)}/events`, signal);

export type LegalStep = 'submit' | 'return' | 'approve' | 'reopen' | 'archive';

export const legalStep = (id: string, step: LegalStep, note: string, version: number) =>
  send<LegalDocument>('POST', `${legalPath(id)}/${step}`, { note: note.trim() === '' ? null : note.trim(), version });

export const recordSent = (id: string, sentOn: string, method: string, reference: string, version: number) =>
  send<LegalDocument>('POST', `${legalPath(id)}/record-sent`, {
    sentOn,
    method,
    reference: reference.trim() === '' ? null : reference.trim(),
    version,
  });

// ---- enforcement -----------------------------------------------------------------------------------------------------

export const ENFORCEMENT_STATUSES = ['NOT_STARTED', 'IN_PROGRESS', 'PARTIALLY_EXECUTED', 'EXECUTED', 'OBSTRUCTED', 'NOT_REQUIRED'] as const;
export type EnforcementStatus = (typeof ENFORCEMENT_STATUSES)[number];
export const ACTION_KINDS = ['PROCEDURE', 'CORRESPONDENCE', 'OBSTACLE', 'OTHER'] as const;
export type ActionKind = (typeof ACTION_KINDS)[number];

export interface JudgmentRef {
  id: string;
  caseId: string;
  caseReferenceNo: string | null;
  caseTitle: string | null;
  stage: string;
  courtCaseNumber: string | null;
  courtCaseYear: number | null;
  judgmentDate: string;
  operativeText: string | null;
  summary: string | null;
  finalJudgment: boolean;
}

export interface EnforcementFile {
  id: string;
  judgmentId: string;
  caseId: string;
  status: EnforcementStatus;
  responsibleMemberId: string | null;
  responsibleMemberName: string | null;
  executingParty: string | null;
  /** KWD with three decimals, as text. */
  amountRequired: string | null;
  obstacles: string | null;
  dueOn: string | null;
  completedOn: string | null;
  notes: string | null;
  late: boolean;
  version: number;
  updatedAt: string;
}

export interface EnforcementAction {
  id: string;
  enforcementId: string;
  seq: number;
  kind: ActionKind;
  actionDate: string;
  description: string;
  result: string | null;
  documentId: string | null;
  documentTitle: string | null;
  version: number;
  createdAt: string;
  createdBy: string;
}

export interface EnforcementOverview {
  judgment: JudgmentRef;
  file: EnforcementFile | null;
  actions: EnforcementAction[];
}

export interface EnforcementFileBody {
  status: EnforcementStatus;
  responsibleMemberId: string | null;
  executingParty: string | null;
  amountRequired: string | null;
  obstacles: string | null;
  dueOn: string | null;
  completedOn: string | null;
  notes: string | null;
  version?: number;
}

export interface EnforcementActionBody {
  kind: ActionKind;
  actionDate: string;
  description: string;
  result: string | null;
  documentId: string | null;
  version?: number;
}

export const getEnforcement = (judgmentId: string, signal?: AbortSignal) =>
  read<EnforcementOverview>(`/api/v1/judgments/${enc(judgmentId)}/enforcement`, signal);

export const createEnforcement = (judgmentId: string, body: EnforcementFileBody) =>
  send<EnforcementOverview>('POST', `/api/v1/judgments/${enc(judgmentId)}/enforcement`, body);

export const updateEnforcement = (fileId: string, body: EnforcementFileBody) =>
  send<EnforcementOverview>('PUT', `/api/v1/enforcement/${enc(fileId)}`, body);

export const addEnforcementAction = (fileId: string, body: EnforcementActionBody) =>
  send<EnforcementAction>('POST', `/api/v1/enforcement/${enc(fileId)}/actions`, body);

export const updateEnforcementAction = (id: string, body: EnforcementActionBody) =>
  send<EnforcementAction>('PUT', `/api/v1/enforcement-actions/${enc(id)}`, body);

export type EnforcementScope = 'UNEXECUTED' | 'LATE' | 'ALL';

export interface EnforcementListItem {
  judgmentId: string;
  caseId: string;
  caseReferenceNo: string | null;
  caseTitle: string | null;
  stage: string;
  courtCaseNumber: string | null;
  courtCaseYear: number | null;
  judgmentDate: string;
  status: EnforcementStatus | null;
  tracked: boolean;
  dueOn: string | null;
  late: boolean;
  responsibleMemberId: string | null;
  responsibleMemberName: string | null;
  executingParty: string | null;
  amountRequired: string | null;
}

export interface EnforcementPage {
  items: EnforcementListItem[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export function listEnforcement(scope: EnforcementScope, page: number, signal?: AbortSignal) {
  const params = new URLSearchParams({ scope, page: String(page), size: '20' });
  return read<EnforcementPage>(`/api/v1/enforcement/judgments?${params.toString()}`, signal);
}
