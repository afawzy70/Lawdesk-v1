import { api } from '../../api/http';

/** Screens that close the gaps of the requirements matrix: judgments list, archive limits, defence wording, responsiveness. */

const enc = encodeURIComponent;
const read = <T>(path: string, signal?: AbortSignal) => api<T>(path, signal === undefined ? {} : { signal });

// ---- judgments (section 9) -------------------------------------------------------------------------------------------

export interface JudgmentRow {
  id: string;
  caseId: string;
  caseReferenceNo: string;
  caseTitle: string;
  stage: string;
  courtName: string;
  courtCaseNumber: string | null;
  courtCaseYear: number | null;
  judgmentDate: string;
  outcome: string;
  finalJudgment: boolean;
  appealDeadlineId: string | null;
  appealDueOn: string | null;
}

export interface JudgmentPage {
  items: JudgmentRow[];
  page: number;
  totalPages: number;
  totalItems: number;
}

export const OUTCOMES = ['ACCEPTED', 'PARTIAL', 'REJECTED', 'INADMISSIBLE', 'OTHER'] as const;

export const listJudgments = (page: number, outcome: string, signal?: AbortSignal) =>
  read<JudgmentPage>(`/api/v1/judgments?page=${String(page)}&size=20${outcome === '' ? '' : `&outcome=${enc(outcome)}`}`, signal);

// ---- archive limits (section 13) --------------------------------------------------------------------------------------

export { getArchiveSettings, type ArchiveSettings, type ArchiveLimits } from '../archive/api';

export const saveArchiveSettings = (limits: object, enabledFormats: string[]) =>
  api<import('../archive/api').ArchiveSettings>('/api/v1/archive/settings', { method: 'PUT', body: { limits, enabledFormats } });

// ---- defence wording library (section 17) -----------------------------------------------------------------------------

export const TEMPLATE_STATUSES = ['DRAFT', 'REVIEWED', 'APPROVED'] as const;
export const DEFENSE_KINDS = ['FORMAL', 'PROCEDURAL', 'SUBSTANTIVE', 'OTHER'] as const;

export interface TemplateInput {
  title: string;
  kind: (typeof DEFENSE_KINDS)[number];
  text: string | null;
  legalBasis: string | null;
  status: (typeof TEMPLATE_STATUSES)[number];
  version?: number;
}

export interface WordingTemplate extends TemplateInput {
  id: string;
  version: number;
}

export const listWording = (q: string, signal?: AbortSignal) => read<WordingTemplate[]>(`/api/v1/defense-templates?q=${enc(q)}`, signal);
export const createWording = (input: TemplateInput) => api<WordingTemplate>('/api/v1/defense-templates', { method: 'POST', body: input });
export const updateWording = (id: string, input: TemplateInput) =>
  api<WordingTemplate>(`/api/v1/defense-templates/${enc(id)}`, { method: 'PUT', body: input });

// ---- responsiveness report (section 18) -------------------------------------------------------------------------------

export interface UnitReport {
  unitId: string | null;
  unitName: string | null;
  requests: number;
  replies: number;
  answeredRequests: number;
  overdue: number;
  incompleteReplies: number;
  affectedCases: number;
  averageReplyDays: number | null;
}

export const getResponsiveness = (signal?: AbortSignal) => read<{ units: UnitReport[] }>('/api/v1/info-requests/report', signal);

// ---- the stored copy of a library judgment (section 16) ---------------------------------------------------------------

export interface StoredCopy {
  id: string;
  fileName: string;
  mediaType: string;
  sizeBytes: number;
  sha256: string;
  uploadedAt: string;
}

export const copyUrl = (judgmentId: string) => `/api/v1/library/judgments/${enc(judgmentId)}/copy`;
export const getCopyInfo = (judgmentId: string, signal?: AbortSignal) => read<StoredCopy>(`${copyUrl(judgmentId)}/info`, signal);
export function storeCopy(judgmentId: string, file: File): Promise<StoredCopy> {
  const form = new FormData();
  form.append('file', file);
  return api<StoredCopy>(copyUrl(judgmentId), { method: 'POST', body: form });
}
export const removeCopy = (judgmentId: string) => api<unknown>(copyUrl(judgmentId), { method: 'DELETE' });

// ---- the precedents that support a defence (section 17) ---------------------------------------------------------------

export interface DefensePrecedent {
  id: string;
  judgmentId: string;
  courtName: string;
  caseNumber: string | null;
  caseYear: number | null;
  subject: string;
  note: string | null;
}

export const listPrecedents = (defenseId: string, signal?: AbortSignal) =>
  read<DefensePrecedent[]>(`/api/v1/defenses/${enc(defenseId)}/precedents`, signal);
export const linkPrecedent = (defenseId: string, judgmentId: string, note: string | null) =>
  api<DefensePrecedent>(`/api/v1/defenses/${enc(defenseId)}/precedents`, { method: 'POST', body: { judgmentId, note } });
export const unlinkPrecedent = (defenseId: string, linkId: string) =>
  api<unknown>(`/api/v1/defenses/${enc(defenseId)}/precedents/${enc(linkId)}`, { method: 'DELETE' });
export const searchLibrary = (q: string, signal?: AbortSignal) =>
  read<{ items: { id: string; courtName: string; caseNumber: string | null; caseYear: number | null; subject: string }[] }>(
    `/api/v1/library/judgments?q=${enc(q)}&size=10`,
    signal,
  );
