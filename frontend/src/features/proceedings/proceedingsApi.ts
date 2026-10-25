import { api } from '../../api/http';
import { LIST_PAGE_SIZE, listAll } from '../../api/pagedList';
import type { PagedResult } from '../../components/PagedSelect';
import type { Court } from '../courts/courtsApi';

/** Proceedings, judgments and special decisions of a case (LD-019…LD-023, docs/delivery/PHASE-4-ACCEPTANCE.md). */
export const STAGES = ['FIRST_INSTANCE', 'APPEAL', 'CASSATION'] as const;
export type Stage = (typeof STAGES)[number];

export const OUTCOMES = ['IN_FAVOUR', 'AGAINST', 'PARTIAL', 'DISMISSED', 'OTHER'] as const;
export type Outcome = (typeof OUTCOMES)[number];

export const DECISION_TYPES = ['ENFORCEMENT_SUSPENSION', 'URGENT_MEASURE', 'OTHER'] as const;
export type DecisionType = (typeof DECISION_TYPES)[number];

export const DECISION_EFFECTS = ['GRANTED', 'PARTIALLY_GRANTED', 'REFUSED'] as const;
export type DecisionEffect = (typeof DECISION_EFFECTS)[number];

export interface Proceeding {
  id: string;
  caseId: string;
  stage: Stage;
  courtId: string;
  courtCode: string;
  courtNameAr: string;
  courtNameEn: string;
  courtCaseNumber: string;
  courtCaseYear: number;
  filingDate: string;
  parentProceedingId: string | null;
  hasJudgment: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface Judgment {
  id: string;
  proceedingId: string;
  caseId: string;
  judgmentDate: string;
  outcome: Outcome;
  summary: string | null;
  final: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface SpecialDecision {
  id: string;
  proceedingId: string;
  caseId: string;
  decisionType: DecisionType;
  decisionDate: string;
  effect: DecisionEffect;
  summary: string | null;
  judgmentId: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

const casePath = (caseId: string, what: string) => `/api/v1/cases/${encodeURIComponent(caseId)}/${what}`;
const proceedingPath = (id: string, what: string) => `/api/v1/proceedings/${encodeURIComponent(id)}/${what}`;

/** The lists of a case are read in full, page by page (QAD-044), never cut at the first page. */
export function listProceedings(caseId: string, signal: AbortSignal): Promise<Proceeding[]> {
  return listAll<Proceeding>(casePath(caseId, 'proceedings'), {}, signal);
}

export function listJudgments(caseId: string, signal: AbortSignal): Promise<Judgment[]> {
  return listAll<Judgment>(casePath(caseId, 'judgments'), {}, signal);
}

export function listSpecialDecisions(caseId: string, signal: AbortSignal): Promise<SpecialDecision[]> {
  return listAll<SpecialDecision>(casePath(caseId, 'special-decisions'), {}, signal);
}

/** One page of the active courts of the level that hears `stage`, in code order, matching `q` on code and names. */
export function listCourtsPage(stage: Stage, page: number, q: string, signal: AbortSignal): Promise<PagedResult<Court>> {
  const query = new URLSearchParams({ active: 'true', level: stage, page: String(page), size: String(LIST_PAGE_SIZE), sort: 'code,asc' });
  if (q.trim() !== '') query.set('q', q.trim());
  return api<PagedResult<Court>>(`/api/v1/courts?${query.toString()}`, { signal });
}

export interface ProceedingInput {
  stage: Stage;
  courtId: string | null;
  courtCaseNumber: string;
  courtCaseYear: number | null;
  filingDate: string | null;
  parentProceedingId: string | null;
}

export function createProceeding(caseId: string, input: ProceedingInput): Promise<Proceeding> {
  return api<Proceeding>(`/api/v1/cases/${encodeURIComponent(caseId)}/proceedings`, { method: 'POST', body: input });
}

export interface JudgmentInput {
  judgmentDate: string | null;
  outcome: Outcome | null;
  final: boolean;
  summary: string | null;
}

export function createJudgment(proceedingId: string, input: JudgmentInput): Promise<Judgment> {
  return api<Judgment>(proceedingPath(proceedingId, 'judgment'), { method: 'POST', body: input });
}

export interface SpecialDecisionInput {
  decisionType: DecisionType;
  decisionDate: string | null;
  effect: DecisionEffect | null;
  judgmentId: string | null;
  summary: string | null;
}

export function createSpecialDecision(proceedingId: string, input: SpecialDecisionInput): Promise<SpecialDecision> {
  return api<SpecialDecision>(proceedingPath(proceedingId, 'special-decisions'), { method: 'POST', body: input });
}
