import { api } from '../../api/http';
import { STAGES, type Stage } from '../proceedings/proceedingsApi';

/** Claims, judgments' results, defences, facts and evidence of a case (LD-051, requirements sections 5, 9, 17, 20). */

export const CLAIM_KINDS = ['PRIMARY', 'ALTERNATIVE', 'INCIDENTAL', 'ANCILLARY', 'URGENT', 'OTHER'] as const;
export type ClaimKind = (typeof CLAIM_KINDS)[number];
export const CLAIM_STATUSES = ['STANDING', 'AMENDED', 'WAIVED', 'ABANDONED', 'OTHER'] as const;
export type ClaimStatus = (typeof CLAIM_STATUSES)[number];
export const CLAIM_RESULTS = [
  'ACCEPTED',
  'PARTIAL',
  'REJECTED',
  'INADMISSIBLE',
  'NO_JURISDICTION',
  'REFERRED',
  'EXTINGUISHED',
  'WAIVER_RECORDED',
  'ABANDONMENT_RECORDED',
  'NOT_DECIDED',
  'DEFERRED',
  'OTHER',
] as const;
export type ClaimResult = (typeof CLAIM_RESULTS)[number];
export const DEFENSE_KINDS = ['FORMAL', 'PROCEDURAL', 'SUBSTANTIVE', 'OTHER'] as const;
export type DefenseKind = (typeof DEFENSE_KINDS)[number];
export const DEFENSE_POSITIONS = ['ACCEPTED', 'REJECTED', 'PARTIAL', 'NOT_ADDRESSED', 'MOOT', 'DEFERRED', 'OTHER'] as const;
export type DefensePosition = (typeof DEFENSE_POSITIONS)[number];
export const ASSERTION_STATUSES = ['RAISED', 'RE_ASSERTED', 'NOT_ASSERTED'] as const;
export type AssertionStatus = (typeof ASSERTION_STATUSES)[number];
export const EVIDENCE_ROLES = ['SUPPORTING', 'OPPONENT', 'DISPUTED', 'REQUIRED', 'UNAVAILABLE', 'SUPPLEMENTARY'] as const;
export type EvidenceRole = (typeof EVIDENCE_ROLES)[number];
export const EVIDENCE_IMPORTANCE = ['LOW', 'MEDIUM', 'HIGH', 'DECISIVE'] as const;
export type EvidenceImportance = (typeof EVIDENCE_IMPORTANCE)[number];
export const JUDGMENT_STATUSES = ['ISSUED', 'NOTIFIED', 'CHALLENGED', 'OTHER'] as const;
export type JudgmentStatus = (typeof JUDGMENT_STATUSES)[number];
export { STAGES };
export type StageCode = Stage;

const casePath = (caseId: string, what: string) => `/api/v1/cases/${encodeURIComponent(caseId)}/${what}`;
const enc = encodeURIComponent;
const send = <T>(method: 'POST' | 'PUT', path: string, body: unknown) => api<T>(path, { method, body });
/** A GET; an aborted effect passes its signal, a one-off call none. */
const read = <T>(path: string, signal?: AbortSignal) => api<T>(path, signal === undefined ? {} : { signal });

// ---- claims -------------------------------------------------------------------------------------------------------

export interface Claim {
  id: string;
  caseId: string;
  seq: number;
  text: string;
  kind: ClaimKind;
  status: ClaimStatus;
  submittedByPartyId: string | null;
  submittedByPartyName: string | null;
  submittedOn: string | null;
  stageId: string | null;
  sourceDocumentId: string | null;
  sourceDocumentTitle: string | null;
  notes: string | null;
  revision: number;
  version: number;
}

export interface ClaimInput {
  text: string;
  kind: ClaimKind;
  status?: ClaimStatus;
  submittedByPartyId: string | null;
  submittedOn: string | null;
  stageId: string | null;
  sourceDocumentId: string | null;
  notes: string | null;
  reason?: string | null;
  version?: number;
}

export interface ClaimRevision {
  rev: number;
  text: string;
  kind: ClaimKind;
  status: ClaimStatus;
  reason: string | null;
  changedBy: string;
  changedAt: string;
}

export const listClaims = (caseId: string, signal?: AbortSignal) => read<Claim[]>(casePath(caseId, 'claims'), signal);
export const createClaim = (caseId: string, input: ClaimInput) => send<Claim>('POST', casePath(caseId, 'claims'), input);
export const updateClaim = (id: string, input: ClaimInput) => send<Claim>('PUT', `/api/v1/claims/${enc(id)}`, input);
export const listRevisions = (id: string, signal?: AbortSignal) =>
  read<ClaimRevision[]>(`/api/v1/claims/${enc(id)}/revisions`, signal);

// ---- results of judgments -----------------------------------------------------------------------------------------

export interface CaseClaimResult {
  judgmentId: string;
  proceedingId: string;
  stage: StageCode;
  judgmentDate: string;
  claimId: string;
  claimSeq: number;
  result: ClaimResult;
  awardedAmount: string | null;
}

export interface ClaimResultRow {
  claimId: string;
  claimSeq: number;
  claimText: string;
  claimStatus: ClaimStatus;
  result: ClaimResult | null;
  operative: string | null;
  reasons: string | null;
  awardedAmount: string | null;
  notes: string | null;
}

export interface ClaimResultInput {
  claimId: string;
  result: ClaimResult;
  operative: string | null;
  reasons: string | null;
  awardedAmount: string | null;
  notes: string | null;
}

export interface DefensePositionRow {
  defenseId: string;
  defenseSeq: number;
  defenseTitle: string;
  position: DefensePosition | null;
  reasons: string | null;
  operative: string | null;
}

export interface DefensePositionInput {
  defenseId: string;
  position: DefensePosition;
  reasons: string | null;
  operative: string | null;
}

export interface CaseDefensePosition {
  judgmentId: string;
  proceedingId: string;
  stage: StageCode;
  judgmentDate: string;
  defenseId: string;
  defenseSeq: number;
  position: DefensePosition;
}

export const listCaseClaimResults = (caseId: string, signal?: AbortSignal) =>
  read<CaseClaimResult[]>(casePath(caseId, 'claim-results'), signal);
export const listJudgmentClaimResults = (judgmentId: string, signal?: AbortSignal) =>
  read<ClaimResultRow[]>(`/api/v1/judgments/${enc(judgmentId)}/claim-results`, signal);
export const saveClaimResults = (judgmentId: string, results: ClaimResultInput[]) =>
  send<ClaimResultRow[]>('PUT', `/api/v1/judgments/${enc(judgmentId)}/claim-results`, { results });
export const listJudgmentDefensePositions = (judgmentId: string, signal?: AbortSignal) =>
  read<DefensePositionRow[]>(`/api/v1/judgments/${enc(judgmentId)}/defense-positions`, signal);
export const saveDefensePositions = (judgmentId: string, positions: DefensePositionInput[]) =>
  send<DefensePositionRow[]>('PUT', `/api/v1/judgments/${enc(judgmentId)}/defense-positions`, { positions });

// ---- defences -----------------------------------------------------------------------------------------------------

export interface Defense {
  id: string;
  seq: number;
  title: string;
  kind: DefenseKind;
  argument: string | null;
  legalBasis: string | null;
  facts: string | null;
  legalTexts: string | null;
  memoDocumentId: string | null;
  memoDocumentTitle: string | null;
  raisedOn: string | null;
  raisedStageId: string | null;
  preparedByName: string | null;
  notes: string | null;
  templateId: string | null;
  evidenceCount: number;
  version: number;
}

export interface DefenseInput {
  title: string;
  kind: DefenseKind;
  argument: string | null;
  legalBasis: string | null;
  facts: string | null;
  legalTexts: string | null;
  memoDocumentId: string | null;
  raisedOn: string | null;
  raisedStageId: string | null;
  notes: string | null;
  templateId: string | null;
  version?: number;
}

export interface Assertion {
  defenseId: string;
  defenseSeq: number;
  proceedingId: string;
  stage: StageCode;
  status: AssertionStatus;
  onDate: string | null;
  memoDocumentTitle: string | null;
}

export interface LinkedEvidence {
  evidenceId: string;
  name: string;
  role: EvidenceRole;
  importance: EvidenceImportance | null;
  documentId: string | null;
  submittedToCourt: boolean;
}

export interface DefenseDetail {
  defense: Defense;
  assertions: Assertion[];
  positions: CaseDefensePosition[];
  evidence: LinkedEvidence[];
}

export interface DefenseTemplate {
  id: string;
  title: string;
  kind: DefenseKind;
  text: string | null;
  legalBasis: string | null;
  status: 'DRAFT' | 'REVIEWED' | 'APPROVED';
}

export const listDefenses = (caseId: string, signal?: AbortSignal) => read<Defense[]>(casePath(caseId, 'defenses'), signal);
export const getDefense = (id: string, signal?: AbortSignal) => read<DefenseDetail>(`/api/v1/defenses/${enc(id)}`, signal);
export const createDefense = (caseId: string, input: DefenseInput) => send<Defense>('POST', casePath(caseId, 'defenses'), input);
export const updateDefense = (id: string, input: DefenseInput) => send<Defense>('PUT', `/api/v1/defenses/${enc(id)}`, input);
export const assertDefense = (id: string, proceedingId: string, status: AssertionStatus, onDate: string | null) =>
  send<Assertion>('PUT', `/api/v1/defenses/${enc(id)}/assertions/${enc(proceedingId)}`, { status, onDate });
export const listTemplates = (q: string, signal?: AbortSignal) =>
  read<DefenseTemplate[]>(`/api/v1/defense-templates?q=${enc(q)}`, signal);

// ---- facts and evidence -------------------------------------------------------------------------------------------

export interface Fact {
  id: string;
  seq: number;
  text: string;
  factDate: string | null;
  evidenceCount: number;
  version: number;
}

export interface EvidenceLink {
  id: string;
  kind: 'FACT' | 'DEFENSE' | 'CLAIM';
  targetId: string;
  label: string;
}

export interface Followup {
  id: string;
  taskId: string | null;
  taskTitle: string | null;
  taskStatus: string | null;
}

export interface EvidenceItem {
  id: string;
  documentId: string | null;
  documentTitle: string | null;
  name: string;
  docType: string | null;
  issuedOn: string | null;
  issuer: string | null;
  receivedOn: string | null;
  source: string | null;
  role: EvidenceRole;
  importance: EvidenceImportance | null;
  submittedToCourt: boolean;
  submittedOn: string | null;
  submittedIn: string | null;
  opponentResponded: boolean | null;
  opponentResponse: string | null;
  courtPosition: string | null;
  notes: string | null;
  version: number;
  links: EvidenceLink[];
  followups: Followup[];
  gap: 'NOT_OBTAINED' | 'NOT_SUBMITTED' | null;
}

export interface EvidenceInput {
  name: string;
  docType: string | null;
  issuedOn: string | null;
  issuer: string | null;
  receivedOn: string | null;
  source: string | null;
  role: EvidenceRole;
  importance: EvidenceImportance | null;
  documentId: string | null;
  submittedToCourt: boolean;
  submittedOn: string | null;
  submittedIn: string | null;
  opponentResponded: boolean | null;
  opponentResponse: string | null;
  courtPosition: string | null;
  notes: string | null;
  version?: number;
}

export interface DocumentUse {
  documentId: string;
  type: 'EVIDENCE' | 'CLAIM_SOURCE' | 'DEFENSE_MEMO' | 'ASSERTION_MEMO' | 'JUDGMENT_COPY';
  refId: string;
  label: string;
  links: EvidenceLink[];
}

export const listFacts = (caseId: string, signal?: AbortSignal) => read<Fact[]>(casePath(caseId, 'facts'), signal);
export const createFact = (caseId: string, text: string, factDate: string | null) =>
  send<Fact>('POST', casePath(caseId, 'facts'), { text, factDate });
export const listEvidence = (caseId: string, signal?: AbortSignal) =>
  read<EvidenceItem[]>(casePath(caseId, 'evidence'), signal);
export const createEvidence = (caseId: string, input: EvidenceInput) => send<EvidenceItem>('POST', casePath(caseId, 'evidence'), input);
export const updateEvidence = (id: string, input: EvidenceInput) => send<EvidenceItem>('PUT', `/api/v1/evidence/${enc(id)}`, input);
export const linkEvidence = (id: string, target: { factId?: string; defenseId?: string; claimId?: string }) =>
  send<EvidenceLink>('POST', `/api/v1/evidence/${enc(id)}/links`, target);
export const unlinkEvidence = (id: string, linkId: string) =>
  api<undefined>(`/api/v1/evidence/${enc(id)}/links/${enc(linkId)}`, { method: 'DELETE' });
export const followUpEvidence = (id: string, taskId: string) =>
  send<Followup>('POST', `/api/v1/evidence/${enc(id)}/followups`, { taskId });
export const listDocumentUsage = (caseId: string, signal?: AbortSignal) =>
  read<DocumentUse[]>(casePath(caseId, 'document-usage'), signal);

// ---- managed lists and statistics ---------------------------------------------------------------------------------

export interface LookupValue {
  id: string;
  listCode: string;
  code: string;
  labelAr: string;
  labelEn: string | null;
  origin: 'DOCUMENT' | 'PROPOSED';
  needsApproval: boolean;
  active: boolean;
  version: number;
}

export interface LookupHistory {
  rev: number;
  labelAr: string;
  labelEn: string | null;
  active: boolean;
  change: 'CREATED' | 'EDITED' | 'APPROVED' | 'ACTIVATED' | 'DEACTIVATED';
  changedAt: string;
}

export const LOOKUP_LISTS = ['JUDGMENT_TYPE', 'JUDGMENT_NATURE'] as const;
export type LookupList = (typeof LOOKUP_LISTS)[number];

export const listLookup = (list: string, includeInactive = false, signal?: AbortSignal) =>
  read<LookupValue[]>(`/api/v1/lookups/${enc(list)}?includeInactive=${String(includeInactive)}`, signal);
export const createLookup = (list: string, input: { code: string; labelAr: string; labelEn: string | null }) =>
  send<LookupValue>('POST', `/api/v1/lookups/${enc(list)}`, input);
export const updateLookup = (id: string, input: { labelAr: string; labelEn: string | null; active: boolean; version: number }) =>
  send<LookupValue>('PUT', `/api/v1/lookups/values/${enc(id)}`, input);
export const approveLookup = (id: string) => send<LookupValue>('POST', `/api/v1/lookups/values/${enc(id)}/approve`, {});
export const lookupHistory = (id: string, signal?: AbortSignal) =>
  read<LookupHistory[]>(`/api/v1/lookups/values/${enc(id)}/history`, signal);

export interface ClaimStatistics {
  total: number;
  byResult: { result: ClaimResult; count: number; awardedTotal: string }[];
  byStage: { stage: StageCode; count: number }[];
  byStageAndResult: { stage: StageCode; result: ClaimResult; count: number }[];
}

export interface DefenseStatistics {
  total: number;
  byPosition: { position: DefensePosition; count: number }[];
  byStage: { stage: StageCode; count: number }[];
  mostUsed: { title: string; count: number }[];
}

export interface StatisticsFilter {
  stage: string;
  caseType: string;
  from: string;
  to: string;
}

function query(filter: StatisticsFilter): string {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(filter as unknown as Record<string, string>)) if (value !== '') params.set(key, value);
  return params.toString();
}

export const claimStatistics = (filter: StatisticsFilter, signal?: AbortSignal) =>
  read<ClaimStatistics>(`/api/v1/reports/claim-results?${query(filter)}`, signal);
export const defenseStatistics = (filter: StatisticsFilter, signal?: AbortSignal) =>
  read<DefenseStatistics>(`/api/v1/reports/defense-positions?${query(filter)}`, signal);

/** Details of a judgment that the extended form sends (all optional on the wire). */
export interface JudgmentDetailsInput {
  chamber: string | null;
  judgmentTypeId: string | null;
  natureId: string | null;
  operativeText: string | null;
  reasons: string | null;
  costsAmount: string | null;
  lawyerFeesAmount: string | null;
  appealable: boolean | null;
  notifiedOn: string | null;
  status: JudgmentStatus | null;
  copyDocumentId: string | null;
}
