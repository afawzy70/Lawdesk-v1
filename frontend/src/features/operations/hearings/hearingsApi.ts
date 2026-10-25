import { api } from '../../../api/http';
import { listAll } from '../../../api/pagedList';
import type { Stage } from '../../proceedings/proceedingsApi';
import { blankToNull } from '../formErrors';

/** Hearings of proceedings and the hearing calendar (LD-024, docs/delivery/PHASE-5-ACCEPTANCE.md). */
export const HEARING_PURPOSES = ['FIRST_SESSION', 'PLEADING', 'EVIDENCE', 'EXPERT_REPORT', 'JUDGMENT', 'OTHER'] as const;
export type HearingPurpose = (typeof HEARING_PURPOSES)[number];

export const HEARING_STATUSES = ['SCHEDULED', 'HELD', 'ADJOURNED', 'CANCELLED'] as const;
export type HearingStatus = (typeof HEARING_STATUSES)[number];

export const RESULT_STATUSES = ['HELD', 'ADJOURNED', 'CANCELLED'] as const;
export type ResultStatus = (typeof RESULT_STATUSES)[number];

export interface Hearing {
  id: string;
  caseId: string;
  caseReferenceNo: string;
  caseTitle: string;
  proceedingId: string;
  stage: Stage;
  courtId: string;
  courtCode: string;
  courtNameAr: string;
  courtNameEn: string;
  courtCaseNumber: string;
  courtCaseYear: number;
  hearingDate: string;
  hearingTime: string | null;
  purpose: HearingPurpose;
  notes: string | null;
  status: HearingStatus;
  decision: string | null;
  adjournedFromId: string | null;
  nextHearingId: string | null;
  awaitingResult: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface HearingPage {
  items: Hearing[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface CalendarFilter {
  from: string;
  to: string;
  status?: HearingStatus | undefined;
  page: number;
}

/** What the scheduling and rescheduling forms hold; empty strings are sent as `null`. */
export interface HearingInput {
  hearingDate: string;
  hearingTime: string;
  purpose: HearingPurpose | '';
  notes: string;
}

export interface ResultInput {
  status: ResultStatus;
  decision: string;
  nextHearingDate: string;
  nextHearingTime: string;
  /** Empty: the next hearing keeps this hearing's purpose. */
  nextHearingPurpose: HearingPurpose | '';
  version: number;
}

export const CALENDAR_PAGE_SIZE = 20;

const hearingPath = (id: string, suffix = '') => `/api/v1/hearings/${encodeURIComponent(id)}${suffix}`;

export function listCalendar(filter: CalendarFilter, signal: AbortSignal): Promise<HearingPage> {
  const query = new URLSearchParams({
    from: filter.from,
    to: filter.to,
    page: String(filter.page),
    size: String(CALENDAR_PAGE_SIZE),
  });
  if (filter.status !== undefined) query.set('status', filter.status);
  return api<HearingPage>(`/api/v1/hearings?${query.toString()}`, { signal });
}

/** Every hearing of the case, read page by page (QAD-044). */
export async function listCaseHearings(caseId: string, signal: AbortSignal): Promise<Hearing[]> {
  return listAll<Hearing>(`/api/v1/cases/${encodeURIComponent(caseId)}/hearings`, {}, signal);
}

function body(input: HearingInput) {
  return {
    hearingDate: blankToNull(input.hearingDate),
    hearingTime: blankToNull(input.hearingTime),
    purpose: input.purpose === '' ? null : input.purpose,
    notes: blankToNull(input.notes),
  };
}

export function scheduleHearing(proceedingId: string, input: HearingInput): Promise<Hearing> {
  return api<Hearing>(`/api/v1/proceedings/${encodeURIComponent(proceedingId)}/hearings`, {
    method: 'POST',
    body: body(input),
  });
}

export function rescheduleHearing(id: string, input: HearingInput, version: number): Promise<Hearing> {
  return api<Hearing>(hearingPath(id), { method: 'PUT', body: { ...body(input), version } });
}

export function recordResult(id: string, input: ResultInput): Promise<Hearing> {
  const adjourned = input.status === 'ADJOURNED';
  return api<Hearing>(hearingPath(id, '/result'), {
    method: 'POST',
    body: {
      status: input.status,
      decision: blankToNull(input.decision),
      nextHearingDate: adjourned ? blankToNull(input.nextHearingDate) : null,
      nextHearingTime: adjourned ? blankToNull(input.nextHearingTime) : null,
      nextHearingPurpose: adjourned && input.nextHearingPurpose !== '' ? input.nextHearingPurpose : null,
      version: input.version,
    },
  });
}
