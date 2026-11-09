import { api } from '../../api/http';

/** The judicial calendar (LD-056, requirements section 15). Days are Kuwait days; times are Kuwait wall-clock HH:mm. */

export const ENTRY_TYPES = [
  'HEARING',
  'EXPERT_SESSION',
  'INVESTIGATION',
  'TASK',
  'DEADLINE',
  'MEETING',
  'INFO_REQUEST_DUE',
  'MEMO_DUE',
  'APPEAL_DEADLINE',
  'ENFORCEMENT',
  'OTHER',
] as const;
export type EntryType = (typeof ENTRY_TYPES)[number];

export const MANUAL_KINDS = ['MEETING', 'INVESTIGATION', 'MEMO_DUE', 'APPEAL_DEADLINE', 'ENFORCEMENT', 'OTHER'] as const;
export type ManualKind = (typeof MANUAL_KINDS)[number];

export interface CalendarEntry {
  id: string;
  type: EntryType;
  refType: string;
  refId: string;
  caseId: string | null;
  caseReferenceNo: string | null;
  caseTitle: string | null;
  caseStatus: string | null;
  title: string;
  detail: string | null;
  stage: string | null;
  date: string;
  startTime: string | null;
  endTime: string | null;
  allDay: boolean;
  startsAt: string;
  endsAt: string | null;
  ownerId: string | null;
  ownerName: string | null;
  status: string | null;
  alternateMemberId: string | null;
  alternateMemberName: string | null;
  conflict: boolean;
  closedCaseFuture: boolean;
}

export interface Conflict {
  ownerId: string;
  ownerName: string | null;
  date: string;
  startsAt: string;
  entries: CalendarEntry[];
}

export interface CalendarView {
  view: 'DAY' | 'WEEK' | 'MONTH' | 'LIST' | 'CONFLICTS';
  from: string;
  to: string;
  zone: string;
  events: CalendarEntry[];
  conflicts: Conflict[];
  days: { date: string; count: number }[];
  truncated: boolean;
}

export type CalendarMode = 'MONTH' | 'WEEK' | 'DAY' | 'LIST';

export interface CalendarFilter {
  mode: CalendarMode;
  /** Any day of the period shown, `YYYY-MM-DD`. */
  anchor: string;
  /** `me` for the signed-in user's calendar; nothing for the general calendar. */
  user: 'me' | undefined;
  type: EntryType | undefined;
  caseId?: string | undefined;
}

/** The first and last day of the period of a mode around the anchor (weeks run Sunday to Saturday). */
export function periodOf(mode: CalendarMode, anchor: string): { from: string; to: string } {
  const day = new Date(`${anchor}T00:00:00Z`);
  const iso = (d: Date) => d.toISOString().slice(0, 10);
  switch (mode) {
    case 'DAY':
      return { from: anchor, to: anchor };
    case 'WEEK': {
      const start = new Date(day);
      start.setUTCDate(day.getUTCDate() - day.getUTCDay());
      const end = new Date(start);
      end.setUTCDate(start.getUTCDate() + 6);
      return { from: iso(start), to: iso(end) };
    }
    default: {
      const first = new Date(Date.UTC(day.getUTCFullYear(), day.getUTCMonth(), 1));
      const last = new Date(Date.UTC(day.getUTCFullYear(), day.getUTCMonth() + 1, 0));
      return { from: iso(first), to: iso(last) };
    }
  }
}

/** The anchor one period earlier or later. */
export function shift(mode: CalendarMode, anchor: string, direction: -1 | 1): string {
  const day = new Date(`${anchor}T00:00:00Z`);
  if (mode === 'DAY') {
    day.setUTCDate(day.getUTCDate() + direction);
  } else if (mode === 'WEEK') {
    day.setUTCDate(day.getUTCDate() + 7 * direction);
  } else {
    // Months move by their first day so that the 31st never skips a month.
    return new Date(Date.UTC(day.getUTCFullYear(), day.getUTCMonth() + direction, 1)).toISOString().slice(0, 10);
  }
  return day.toISOString().slice(0, 10);
}

export function loadCalendar(filter: CalendarFilter, signal: AbortSignal): Promise<CalendarView> {
  const { from, to } = periodOf(filter.mode, filter.anchor);
  const query = new URLSearchParams({ from, to });
  if (filter.user !== undefined) query.set('userId', filter.user);
  if (filter.type !== undefined) query.append('type', filter.type);
  if (filter.caseId !== undefined) query.set('caseId', filter.caseId);
  return api<CalendarView>(`/api/v1/calendar/events?${query.toString()}`, { signal });
}

/** The entries of one case between two days (both included), for the case page. */
export function loadCaseEntries(caseId: string, from: string, to: string, signal: AbortSignal): Promise<CalendarView> {
  const query = new URLSearchParams({ from, to, caseId });
  return api<CalendarView>(`/api/v1/calendar/events?${query.toString()}`, { signal });
}

export interface ManualDateInput {
  kind: ManualKind;
  title: string;
  notes: string;
  date: string;
  startTime: string;
  endTime: string;
}

export interface ManualDate {
  id: string;
  kind: ManualKind;
  title: string;
  caseId: string | null;
  date: string;
  status: 'ACTIVE' | 'DONE' | 'CANCELLED';
  version: number;
}

const orNull = (value: string) => (value.trim() === '' ? null : value.trim());

export function createManualDate(input: ManualDateInput, caseId: string | null): Promise<ManualDate> {
  return api<ManualDate>('/api/v1/calendar/dates', {
    method: 'POST',
    body: {
      kind: input.kind,
      title: input.title,
      notes: orNull(input.notes),
      caseId,
      date: input.date,
      startTime: orNull(input.startTime),
      endTime: orNull(input.endTime),
    },
  });
}

export function changeManualDateStatus(id: string, status: ManualDate['status'], version: number): Promise<ManualDate> {
  return api<ManualDate>(`/api/v1/calendar/dates/${encodeURIComponent(id)}/status`, {
    method: 'POST',
    body: { status, version },
  });
}

/** Hands a scheduled hearing to another member without moving it (the one-click answer to a conflict: the case's alternate). */
export function reassignHearing(hearingId: string, memberId: string): Promise<unknown> {
  return api(`/api/v1/hearings/${encodeURIComponent(hearingId)}/reassign`, { method: 'POST', body: { memberId } });
}
