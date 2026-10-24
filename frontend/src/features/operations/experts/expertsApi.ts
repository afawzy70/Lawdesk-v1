import { api } from '../../../api/http';
import type { Stage } from '../../proceedings/proceedingsApi';
import { blankToNull } from '../formErrors';

/** The expert registry, expert referrals and their sessions (LD-025, docs/delivery/PHASE-5-ACCEPTANCE.md). */
export const REFERRAL_STATUSES = ['OPEN', 'REPORT_SUBMITTED', 'CANCELLED'] as const;
export type ReferralStatus = (typeof REFERRAL_STATUSES)[number];

export const EXPERTS_PAGE_SIZE = 20;

export interface Expert {
  id: string;
  name: string;
  specialty: string;
  office: string | null;
  phone: string | null;
  active: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface ExpertPage {
  items: Expert[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface ExpertSession {
  id: string;
  sessionDate: string;
  sessionTime: string | null;
  location: string | null;
  notes: string | null;
  createdAt: string;
}

export interface Referral {
  id: string;
  caseId: string;
  caseReferenceNo: string;
  caseTitle: string;
  proceedingId: string;
  stage: Stage;
  courtCaseNumber: string;
  courtCaseYear: number;
  expertId: string;
  expertName: string;
  expertSpecialty: string;
  referralDate: string;
  mission: string | null;
  reportDueDate: string | null;
  status: ReferralStatus;
  reportSubmittedDate: string | null;
  reportOverdue: boolean;
  sessions: ExpertSession[];
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface ExpertFilter {
  active?: boolean | undefined;
  q?: string | undefined;
  page: number;
  size?: number | undefined;
}

export interface ExpertInput {
  name: string;
  specialty: string;
  office: string;
  phone: string;
}

export interface ReferralInput {
  expertId: string;
  referralDate: string;
  reportDueDate: string;
  mission: string;
}

export interface SessionInput {
  sessionDate: string;
  sessionTime: string;
  location: string;
  notes: string;
}

export function listExperts(filter: ExpertFilter, signal: AbortSignal): Promise<ExpertPage> {
  const query = new URLSearchParams({ page: String(filter.page), size: String(filter.size ?? EXPERTS_PAGE_SIZE) });
  if (filter.active !== undefined) query.set('active', String(filter.active));
  if (filter.q !== undefined && filter.q.trim() !== '') query.set('q', filter.q.trim());
  return api<ExpertPage>(`/api/v1/experts?${query.toString()}`, { signal });
}

function expertBody(input: ExpertInput) {
  return { name: input.name, specialty: input.specialty, office: blankToNull(input.office), phone: blankToNull(input.phone) };
}

export function createExpert(input: ExpertInput): Promise<Expert> {
  return api<Expert>('/api/v1/experts', { method: 'POST', body: expertBody(input) });
}

export function updateExpert(id: string, input: ExpertInput, active: boolean, version: number): Promise<Expert> {
  return api<Expert>(`/api/v1/experts/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: { ...expertBody(input), active, version },
  });
}

/** A case holds a handful of referrals; the answer holds them all, each with its sessions. */
export async function listCaseReferrals(caseId: string, signal: AbortSignal): Promise<Referral[]> {
  const path = `/api/v1/cases/${encodeURIComponent(caseId)}/expert-referrals`;
  return (await api<{ items: Referral[] }>(path, { signal })).items;
}

export function referToExpert(proceedingId: string, input: ReferralInput): Promise<Referral> {
  return api<Referral>(`/api/v1/proceedings/${encodeURIComponent(proceedingId)}/expert-referrals`, {
    method: 'POST',
    body: {
      expertId: input.expertId === '' ? null : input.expertId,
      referralDate: blankToNull(input.referralDate),
      reportDueDate: blankToNull(input.reportDueDate),
      mission: blankToNull(input.mission),
    },
  });
}

const referralPath = (id: string, what: string) => `/api/v1/expert-referrals/${encodeURIComponent(id)}/${what}`;

export function recordSession(referralId: string, input: SessionInput): Promise<Referral> {
  return api<Referral>(referralPath(referralId, 'sessions'), {
    method: 'POST',
    body: {
      sessionDate: blankToNull(input.sessionDate),
      sessionTime: blankToNull(input.sessionTime),
      location: blankToNull(input.location),
      notes: blankToNull(input.notes),
    },
  });
}

export function submitReport(referral: Referral, reportSubmittedDate: string): Promise<Referral> {
  return api<Referral>(referralPath(referral.id, 'report'), {
    method: 'POST',
    body: { reportSubmittedDate: blankToNull(reportSubmittedDate), version: referral.version },
  });
}

export function cancelReferral(referral: Referral): Promise<Referral> {
  return api<Referral>(referralPath(referral.id, 'cancel'), { method: 'POST', body: { version: referral.version } });
}
