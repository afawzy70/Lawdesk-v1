import { api } from '../../api/http';
import type { PageResponse } from '../cases/api';
import type { Stage } from '../proceedings/proceedingsApi';
import type { CourtRef } from './inventoryApi';

/**
 * Reports on Phase 5 legal operations (docs/delivery/PHASE-6-ACCEPTANCE.md, LD-032 hearings part, LD-033):
 * `GET /api/v1/reports/hearings`, `/workload` and `/experts`; every call goes through `api()`.
 */

export const HEARING_STATUSES = ['SCHEDULED', 'HELD', 'ADJOURNED', 'CANCELLED'] as const;
export type HearingStatus = (typeof HEARING_STATUSES)[number];
export type HearingPurpose = 'FIRST_SESSION' | 'PLEADING' | 'EVIDENCE' | 'EXPERT_REPORT' | 'JUDGMENT' | 'OTHER';
export const TASK_STATUSES = ['OPEN', 'IN_PROGRESS', 'DONE', 'CANCELLED'] as const;
export type TaskStatus = (typeof TASK_STATUSES)[number];
export const REFERRAL_STATUSES = ['OPEN', 'REPORT_SUBMITTED', 'CANCELLED'] as const;
export type ReferralStatus = (typeof REFERRAL_STATUSES)[number];

export interface HearingRow {
  id: string;
  hearingDate: string;
  /** `HH:mm:ss`, or null for a hearing without a time. */
  hearingTime: string | null;
  purpose: HearingPurpose;
  status: HearingStatus;
  awaitingResult: boolean;
  stage: Stage | null;
  court: CourtRef | null;
  courtCaseNumber: string | null;
  courtCaseYear: number | null;
  caseId: string;
  caseReferenceNo: string | null;
  caseTitle: string | null;
}

export interface HearingReport extends PageResponse<HearingRow> {
  from: string;
  to: string;
  summary: { total: number; byStatus: Record<HearingStatus, number>; awaitingResult: number };
}

export interface HearingFilter {
  from?: string;
  to?: string;
  status?: HearingStatus;
  page?: number;
}

export interface WorkloadRow {
  assigneeId: string;
  username: string | null;
  displayName: string | null;
  enabled: boolean | null;
  open: number;
  inProgress: number;
  overdue: number;
}

export interface WorkloadReport {
  items: WorkloadRow[];
  dueSoonDays: number;
  summary: { byStatus: Record<TaskStatus, number>; overdue: number; dueSoon: number };
}

export interface ExpertRow {
  expertId: string;
  name: string | null;
  specialty: string | null;
  active: boolean;
  open: number;
  overdue: number;
}

export interface ExpertsReport {
  items: ExpertRow[];
  summary: { byStatus: Record<ReferralStatus, number>; overdueReports: number };
}

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

/** Reads the hearing filters from an address or a submitted form; unknown values are dropped rather than sent. */
export function hearingFilterFrom(read: (name: string) => string | null): HearingFilter {
  const filter: HearingFilter = {};
  const from = read('from')?.trim() ?? '';
  if (ISO_DATE.test(from)) filter.from = from;
  const to = read('to')?.trim() ?? '';
  if (ISO_DATE.test(to)) filter.to = to;
  const status = HEARING_STATUSES.find((value) => value === read('status'));
  if (status !== undefined) filter.status = status;
  const page = Number.parseInt(read('page') ?? '', 10);
  if (page > 0) filter.page = page;
  return filter;
}

export function hearingQuery(filter: HearingFilter): URLSearchParams {
  const params = new URLSearchParams();
  if (filter.from !== undefined) params.set('from', filter.from);
  if (filter.to !== undefined) params.set('to', filter.to);
  if (filter.status !== undefined) params.set('status', filter.status);
  if (filter.page !== undefined && filter.page > 0) params.set('page', String(filter.page));
  return params;
}

function withQuery(path: string, params: URLSearchParams): string {
  const query = params.toString();
  return query === '' ? path : `${path}?${query}`;
}

export function getHearingReport(filter: HearingFilter, signal?: AbortSignal): Promise<HearingReport> {
  return api(withQuery('/api/v1/reports/hearings', hearingQuery(filter)), signal === undefined ? {} : { signal });
}

export function getWorkloadReport(signal?: AbortSignal): Promise<WorkloadReport> {
  return api('/api/v1/reports/workload', signal === undefined ? {} : { signal });
}

export function getExpertsReport(signal?: AbortSignal): Promise<ExpertsReport> {
  return api('/api/v1/reports/experts', signal === undefined ? {} : { signal });
}
