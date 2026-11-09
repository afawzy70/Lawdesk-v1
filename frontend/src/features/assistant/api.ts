import { api } from '../../api/http';

/** LD-059 (WP10): the legal assistant, a NON-PRODUCTION test mode; no provider is approved and nothing leaves the system (D8). */

export const TASKS = ['QUESTION', 'CASE_SUMMARY', 'DOCUMENT_ANALYSIS', 'PRECEDENTS', 'MEMO_DRAFT', 'COMPARISON'] as const;
export type Task = (typeof TASKS)[number];

export interface ProviderInfo {
  code: string;
  nonProduction: boolean;
  external: boolean;
}

export interface Status {
  operational: boolean;
  reason: string;
  mode: string;
  provider: ProviderInfo;
  approvedProviders: number;
  configuredProviders: number;
  externalCalls: boolean;
  caseDataLeavesTheSystem: boolean;
  tasks: string[];
}

export interface Field {
  code: string;
  value: string;
}

/** A passage an answer rests on. `excerpt` is stored text, `fields` a stored record; `page` is null (stored text keeps no pages). */
export interface Source {
  n: number;
  type: string;
  id: string | null;
  caseId: string | null;
  caseReferenceNo: string | null;
  title: string | null;
  date: string | null;
  page: number | null;
  excerpt: string | null;
  fields: Field[];
  suspectedInstruction: boolean;
}

/** HEADING and NOTE are fixed messages by code (the assistant's own words); QUOTE is stored text from the numbered sources. */
export interface Block {
  kind: 'HEADING' | 'QUOTE' | 'NOTE';
  code: string | null;
  text: string | null;
  fields: Field[] | null;
  sources: number[];
}

export interface Answer {
  queryId: number | null;
  sessionId: string;
  task: Task;
  outcome: 'ANSWERED' | 'INSUFFICIENT_SOURCES';
  refusal: string | null;
  operational: boolean;
  reason: string;
  mode: string;
  provider: ProviderInfo;
  caseId: string | null;
  blocks: Block[];
  sources: Source[];
  outputId: string | null;
  isFinal: boolean;
}

export interface Output {
  id: string;
  caseId: string | null;
  kind: string;
  title: string;
  status: 'DRAFT' | 'USER_APPROVED' | 'DISCARDED';
  isFinal: boolean;
  nonProduction: boolean;
  reviewedAt: string | null;
  createdAt: string;
}

export interface Integration {
  code: string;
  nameAr: string;
  nameEn: string;
  direction: string;
  operations: string[];
  status: string;
  reason: string;
  hasCredentials: boolean;
  hasEndpoint: boolean;
}

export interface AskInput {
  task: Task;
  caseId?: string;
  question?: string;
  documentId?: string;
  otherDocumentId?: string;
  saveDraft?: boolean;
}

export function getStatus(signal?: AbortSignal): Promise<Status> {
  return api<Status>('/api/v1/assistant/status', signal === undefined ? {} : { signal });
}

export function ask(input: AskInput): Promise<Answer> {
  return api<Answer>('/api/v1/assistant/ask', { method: 'POST', body: input });
}

export function listOutputs(caseId: string, signal?: AbortSignal): Promise<Output[]> {
  return api<Output[]>(`/api/v1/assistant/outputs?caseId=${encodeURIComponent(caseId)}`, signal === undefined ? {} : { signal });
}

export function reviewOutput(id: string, decision: 'APPROVED' | 'DISCARDED'): Promise<Output> {
  return api<Output>(`/api/v1/assistant/outputs/${encodeURIComponent(id)}/review`, { method: 'POST', body: { decision } });
}

export function listIntegrations(signal?: AbortSignal): Promise<Integration[]> {
  return api<Integration[]>('/api/v1/integrations', signal === undefined ? {} : { signal });
}
