import { api } from '../../api/http';
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

interface Page<T> {
  items: T[];
}

/** A case holds a handful of records; one page of the maximum size holds them all. */
const ALL = 'size=100';

const casePath = (caseId: string, what: string) => `/api/v1/cases/${encodeURIComponent(caseId)}/${what}?${ALL}`;
const proceedingPath = (id: string, what: string) => `/api/v1/proceedings/${encodeURIComponent(id)}/${what}`;

export async function listProceedings(caseId: string, signal: AbortSignal): Promise<Proceeding[]> {
  return (await api<Page<Proceeding>>(casePath(caseId, 'proceedings'), { signal })).items;
}

export async function listJudgments(caseId: string, signal: AbortSignal): Promise<Judgment[]> {
  return (await api<Page<Judgment>>(casePath(caseId, 'judgments'), { signal })).items;
}

export async function listSpecialDecisions(caseId: string, signal: AbortSignal): Promise<SpecialDecision[]> {
  return (await api<Page<SpecialDecision>>(casePath(caseId, 'special-decisions'), { signal })).items;
}

/** Active courts of the level that hears `stage`. */
export async function listCourtsFor(stage: Stage, signal: AbortSignal): Promise<Court[]> {
  const query = new URLSearchParams({ active: 'true', level: stage, size: '100', sort: 'code,asc' });
  return (await api<Page<Court>>(`/api/v1/courts?${query.toString()}`, { signal })).items;
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
