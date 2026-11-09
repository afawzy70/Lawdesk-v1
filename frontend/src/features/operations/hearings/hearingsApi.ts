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

export const HEARING_KINDS = ['COURT', 'EXPERT', 'INVESTIGATION', 'OTHER'] as const;
export type HearingKind = (typeof HEARING_KINDS)[number];

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
  /** LD-053 (requirements section 7). */
  chamber: string | null;
  kind: HearingKind;
  judgeOrExpert: string | null;
  responsibleMemberId: string | null;
  responsibleMemberName: string | null;
  attendees: string | null;
  proceedingsSummary: string | null;
  opponentsRequests: string | null;
  requiredAction: string | null;
  adjournmentReason: string | null;
  /** The court set no next date: the hearing is on the follow-up list until one is scheduled. */
  noNextDate: boolean;
  noNextDateReason: string | null;
  followUp: boolean;
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
  chamber: string;
  kind: HearingKind;
  judgeOrExpert: string;
  responsibleMemberId: string;
  attendees: string;
  requiredAction: string;
}

export interface ResultInput {
  status: ResultStatus;
  decision: string;
  nextHearingDate: string;
  nextHearingTime: string;
  /** Empty: the next hearing keeps this hearing's purpose. */
  nextHearingPurpose: HearingPurpose | '';
  /** What happened at the hearing (LD-053). */
  attendees: string;
  proceedingsSummary: string;
  opponentsRequests: string;
  requiredAction: string;
  adjournmentReason: string;
  /** The court set no next date; the reason is then required and no date is given. */
  noNextDate: boolean;
  noNextDateReason: string;
  version: number;
}

/** An empty hearing form. */
export const BLANK_HEARING: HearingInput = {
  hearingDate: '',
  hearingTime: '',
  purpose: '',
  notes: '',
  chamber: '',
  kind: 'COURT',
  judgeOrExpert: '',
  responsibleMemberId: '',
  attendees: '',
  requiredAction: '',
};

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
    chamber: blankToNull(input.chamber),
    kind: input.kind,
    judgeOrExpert: blankToNull(input.judgeOrExpert),
    responsibleMemberId: blankToNull(input.responsibleMemberId),
    attendees: blankToNull(input.attendees),
    requiredAction: blankToNull(input.requiredAction),
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
  const withDate = adjourned && !input.noNextDate;
  return api<Hearing>(hearingPath(id, '/result'), {
    method: 'POST',
    body: {
      status: input.status,
      decision: blankToNull(input.decision),
      nextHearingDate: withDate ? blankToNull(input.nextHearingDate) : null,
      nextHearingTime: withDate ? blankToNull(input.nextHearingTime) : null,
      nextHearingPurpose: withDate && input.nextHearingPurpose !== '' ? input.nextHearingPurpose : null,
      adjournmentReason: adjourned ? blankToNull(input.adjournmentReason) : null,
      noNextDate: adjourned && input.noNextDate ? true : null,
      noNextDateReason: adjourned && input.noNextDate ? blankToNull(input.noNextDateReason) : null,
      attendees: blankToNull(input.attendees),
      proceedingsSummary: blankToNull(input.proceedingsSummary),
      opponentsRequests: blankToNull(input.opponentsRequests),
      requiredAction: blankToNull(input.requiredAction),
      version: input.version,
    },
  });
}

/** Adjourned hearings the court gave no next date, of the cases the user sees. */
export function listFollowUp(page: number, signal: AbortSignal): Promise<HearingPage> {
  const query = new URLSearchParams({ page: String(page), size: String(CALENDAR_PAGE_SIZE) });
  return api<HearingPage>(`/api/v1/hearings/follow-up?${query.toString()}`, { signal });
}

export interface NextHearingInput {
  hearingDate: string;
  hearingTime: string;
  version: number;
}

/** Schedules the next hearing of an adjourned hearing that had no date; the follow-up ends. */
export function scheduleNextHearing(id: string, input: NextHearingInput): Promise<Hearing> {
  return api<Hearing>(hearingPath(id, '/next-hearing'), {
    method: 'POST',
    body: {
      hearingDate: blankToNull(input.hearingDate),
      hearingTime: blankToNull(input.hearingTime),
      version: input.version,
    },
  });
}
