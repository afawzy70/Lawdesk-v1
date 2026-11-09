import { api } from '../../api/http';
import { listAll } from '../../api/pagedList';

/**
 * LD-054 (WP4, requirements section 10, decision D6): legal deadlines, the rules and official holidays they may be computed
 * from, and the reminder lead times. The system states no period of its own: a rule exists only when a specialist entered
 * and a competent member approved it.
 */

export const blankToNull = (value: string): string | null => (value.trim() === '' ? null : value.trim());

export const SOURCE_TYPES = ['JUDGMENT', 'HEARING', 'PROCEEDING', 'TASK', 'TEXT'] as const;
export type SourceType = (typeof SOURCE_TYPES)[number];
export const DEADLINE_STATUSES = ['OPEN', 'COMPLETED', 'MISSED'] as const;
export type DeadlineStatus = (typeof DEADLINE_STATUSES)[number];
export const CALC_STATUSES = ['MANUAL', 'PROPOSED', 'APPROVED'] as const;
export type CalcStatus = (typeof CALC_STATUSES)[number];
export const WEEKDAYS = [1, 2, 3, 4, 5, 6, 7] as const;

export interface Calculation {
  ruleId: string;
  ruleCode: string;
  ruleLabel: string;
  periodDays: number;
  countMethod: 'CALENDAR' | 'WORKING';
  countsStartDay: boolean;
  rollForward: boolean;
  closedWeekdays: number[];
  legalSource: string;
  effectiveFrom: string;
  startsOn: string;
  firstCountedDay: string;
  rolledForwardFrom: string | null;
  dueOn: string;
  skipped: { date: string; kind: 'WEEKDAY' | 'HOLIDAY'; label: string | null }[];
}

export interface Deadline {
  id: string;
  caseId: string;
  caseReferenceNo: string;
  caseTitle: string;
  sourceType: SourceType;
  sourceId: string | null;
  sourceText: string | null;
  sourceLabel: string | null;
  startsOn: string | null;
  periodDays: number | null;
  dueOn: string | null;
  ruleId: string | null;
  ruleCode: string | null;
  calculation: Calculation | null;
  action: string;
  ownerId: string | null;
  ownerName: string | null;
  status: DeadlineStatus;
  completedOn: string | null;
  proofDocumentId: string | null;
  proofDocumentTitle: string | null;
  notes: string | null;
  calcStatus: CalcStatus;
  official: boolean;
  approvedById: string | null;
  approvedByName: string | null;
  approvedAt: string | null;
  changeReason: string | null;
  overdue: boolean;
  daysRemaining: number | null;
  version: number;
}

export interface DeadlinePage {
  items: Deadline[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface DeadlineFilter {
  status: string;
  calcStatus: string;
  ownerId: string;
  from: string;
  to: string;
  overdue: string;
}

export const NO_FILTER: DeadlineFilter = { status: '', calcStatus: '', ownerId: '', from: '', to: '', overdue: '' };

export async function listDeadlines(filter: DeadlineFilter, page: number, signal?: AbortSignal): Promise<DeadlinePage> {
  const query = new URLSearchParams({ page: String(page), size: '20' });
  const entries: [string, string][] = [
    ['status', filter.status],
    ['calcStatus', filter.calcStatus],
    ['ownerId', filter.ownerId],
    ['from', filter.from],
    ['to', filter.to],
    ['overdue', filter.overdue],
  ];
  for (const [key, value] of entries) {
    if (value !== '') query.set(key, value);
  }
  return api<DeadlinePage>(`/api/v1/deadlines?${query.toString()}`, signal === undefined ? {} : { signal });
}

export function listCaseDeadlines(caseId: string, signal: AbortSignal): Promise<Deadline[]> {
  return listAll<Deadline>(`/api/v1/cases/${encodeURIComponent(caseId)}/deadlines`, {}, signal);
}

export function listJudgmentDeadlines(judgmentId: string, signal: AbortSignal): Promise<Deadline[]> {
  return listAll<Deadline>(`/api/v1/judgments/${encodeURIComponent(judgmentId)}/deadlines`, {}, signal);
}

export interface NewDeadline {
  sourceType: SourceType;
  sourceId: string;
  sourceText: string;
  startsOn: string;
  periodDays: string;
  dueOn: string;
  action: string;
  ownerId: string;
  notes: string;
}

export const EMPTY_DEADLINE: NewDeadline = {
  sourceType: 'TEXT',
  sourceId: '',
  sourceText: '',
  startsOn: '',
  periodDays: '',
  dueOn: '',
  action: '',
  ownerId: '',
  notes: '',
};

function sourceBody(input: NewDeadline) {
  return {
    sourceType: input.sourceType,
    sourceId: input.sourceType === 'TEXT' ? null : blankToNull(input.sourceId),
    sourceText: input.sourceType === 'TEXT' ? blankToNull(input.sourceText) : null,
  };
}

export function createDeadline(caseId: string, input: NewDeadline): Promise<Deadline> {
  return api<Deadline>(`/api/v1/cases/${encodeURIComponent(caseId)}/deadlines`, {
    method: 'POST',
    body: {
      ...sourceBody(input),
      startsOn: blankToNull(input.startsOn),
      periodDays: input.periodDays.trim() === '' ? null : Number(input.periodDays),
      dueOn: blankToNull(input.dueOn),
      action: input.action.trim(),
      ownerId: blankToNull(input.ownerId),
      notes: blankToNull(input.notes),
    },
  });
}

export function proposeDeadline(caseId: string, input: NewDeadline, ruleId: string): Promise<Deadline> {
  return api<Deadline>(`/api/v1/cases/${encodeURIComponent(caseId)}/deadlines/propose`, {
    method: 'POST',
    body: {
      ...sourceBody(input),
      ruleId: blankToNull(ruleId),
      startsOn: input.startsOn,
      action: input.action.trim(),
      ownerId: blankToNull(input.ownerId),
      notes: blankToNull(input.notes),
    },
  });
}

export interface DeadlineEdit {
  startsOn: string;
  periodDays: string;
  dueOn: string;
  action: string;
  ownerId: string;
  notes: string;
  changeReason: string;
}

export function updateDeadline(deadline: Deadline, edit: DeadlineEdit): Promise<Deadline> {
  return api<Deadline>(`/api/v1/deadlines/${encodeURIComponent(deadline.id)}`, {
    method: 'PUT',
    body: {
      startsOn: blankToNull(edit.startsOn),
      periodDays: edit.periodDays.trim() === '' ? null : Number(edit.periodDays),
      dueOn: blankToNull(edit.dueOn),
      action: edit.action.trim(),
      ownerId: blankToNull(edit.ownerId),
      notes: blankToNull(edit.notes),
      changeReason: blankToNull(edit.changeReason),
      version: deadline.version,
    },
  });
}

export function approveDeadline(deadline: Deadline): Promise<Deadline> {
  return api<Deadline>(`/api/v1/deadlines/${encodeURIComponent(deadline.id)}/approve`, {
    method: 'POST',
    body: { version: deadline.version },
  });
}

export function setDeadlineStatus(
  deadline: Deadline,
  status: DeadlineStatus,
  extra: { completedOn?: string; proofDocumentId?: string } = {},
): Promise<Deadline> {
  return api<Deadline>(`/api/v1/deadlines/${encodeURIComponent(deadline.id)}/status`, {
    method: 'POST',
    body: {
      status,
      completedOn: blankToNull(extra.completedOn ?? ''),
      proofDocumentId: blankToNull(extra.proofDocumentId ?? ''),
      version: deadline.version,
    },
  });
}

// ---- rules, holidays and reminder lead times -------------------------------------------------------------------------

export type SettingStatus = 'DRAFT' | 'APPROVED' | 'RETIRED';

export interface DeadlineRule {
  id: string;
  code: string;
  label: string;
  periodDays: number;
  countMethod: 'CALENDAR' | 'WORKING';
  countsStartDay: boolean;
  rollForward: boolean;
  closedWeekdays: number[];
  legalSource: string;
  effectiveFrom: string;
  status: SettingStatus;
  approvedByName: string | null;
  approvedAt: string | null;
  version: number;
}

export interface NewRule {
  code: string;
  label: string;
  periodDays: string;
  countMethod: 'CALENDAR' | 'WORKING';
  countsStartDay: boolean;
  rollForward: boolean;
  closedWeekdays: number[];
  legalSource: string;
  effectiveFrom: string;
}

export const EMPTY_RULE: NewRule = {
  code: '',
  label: '',
  periodDays: '',
  countMethod: 'CALENDAR',
  countsStartDay: false,
  rollForward: false,
  closedWeekdays: [],
  legalSource: '',
  effectiveFrom: '',
};

export function listRules(signal: AbortSignal): Promise<DeadlineRule[]> {
  return listAll<DeadlineRule>('/api/v1/deadline-rules', {}, signal);
}

export function createRule(input: NewRule): Promise<DeadlineRule> {
  return api<DeadlineRule>('/api/v1/deadline-rules', {
    method: 'POST',
    body: { ...input, code: input.code.trim(), label: input.label.trim(), periodDays: Number(input.periodDays), legalSource: input.legalSource.trim() },
  });
}

export function approveRule(rule: DeadlineRule): Promise<DeadlineRule> {
  return api<DeadlineRule>(`/api/v1/deadline-rules/${encodeURIComponent(rule.id)}/approve`, {
    method: 'POST',
    body: { version: rule.version },
  });
}

export function retireRule(rule: DeadlineRule): Promise<DeadlineRule> {
  return api<DeadlineRule>(`/api/v1/deadline-rules/${encodeURIComponent(rule.id)}/retire`, {
    method: 'POST',
    body: { version: rule.version },
  });
}

export interface Holiday {
  id: string;
  holidayOn: string;
  label: string;
  legalSource: string;
  status: SettingStatus;
  approvedByName: string | null;
  approvedAt: string | null;
  version: number;
}

export function listHolidays(signal: AbortSignal): Promise<Holiday[]> {
  return listAll<Holiday>('/api/v1/official-holidays', {}, signal);
}

export function createHoliday(input: { holidayOn: string; label: string; legalSource: string }): Promise<Holiday> {
  return api<Holiday>('/api/v1/official-holidays', {
    method: 'POST',
    body: { holidayOn: input.holidayOn, label: input.label.trim(), legalSource: input.legalSource.trim() },
  });
}

export function approveHoliday(holiday: Holiday): Promise<Holiday> {
  return api<Holiday>(`/api/v1/official-holidays/${encodeURIComponent(holiday.id)}/approve`, {
    method: 'POST',
    body: { version: holiday.version },
  });
}

export function retireHoliday(holiday: Holiday): Promise<Holiday> {
  return api<Holiday>(`/api/v1/official-holidays/${encodeURIComponent(holiday.id)}/retire`, {
    method: 'POST',
    body: { version: holiday.version },
  });
}

export interface ReminderSetting {
  configured: boolean;
  leadDays: number[] | null;
}

export function getReminderSetting(signal: AbortSignal): Promise<ReminderSetting> {
  return api<ReminderSetting>('/api/v1/deadline-settings/reminders', { signal });
}

export function saveReminderSetting(leadDays: number[]): Promise<ReminderSetting> {
  return api<ReminderSetting>('/api/v1/deadline-settings/reminders', { method: 'PUT', body: { leadDays } });
}

/** Lead days typed as "7, 3, 1" (commas, Arabic commas or spaces); null when something is not a whole number of days. */
export function parseLeadDays(text: string): number[] | null {
  const parts = text.split(/[\s,،]+/).filter((p) => p !== '');
  const days = parts.map((p) => (/^\d{1,3}$/.test(p) ? Number(p) : Number.NaN));
  return days.some((d) => Number.isNaN(d) || d > 365) ? null : days;
}

// ---- what a deadline can run from ----------------------------------------------------------------------------------------

export interface SourceOption {
  id: string;
  /** A calendar date (`yyyy-MM-dd`), shown in the user's format, or plain text. */
  label: string;
  isDate: boolean;
}

/** The records of the case a deadline of this source type may run from; empty when the user may not list them. */
export async function listSourceOptions(caseId: string, type: SourceType, signal: AbortSignal): Promise<SourceOption[]> {
  const id = encodeURIComponent(caseId);
  switch (type) {
    case 'TEXT':
      return [];
    case 'JUDGMENT': {
      const rows = await listAll<{ id: string; judgmentDate: string }>(`/api/v1/cases/${id}/judgments`, {}, signal);
      return rows.map((r) => ({ id: r.id, label: r.judgmentDate, isDate: true }));
    }
    case 'HEARING': {
      const rows = await listAll<{ id: string; hearingDate: string }>(`/api/v1/cases/${id}/hearings`, {}, signal);
      return rows.map((r) => ({ id: r.id, label: r.hearingDate, isDate: true }));
    }
    case 'PROCEEDING': {
      const rows = await listAll<{ id: string; courtCaseNumber: string; courtCaseYear: number }>(
        `/api/v1/cases/${id}/proceedings`,
        {},
        signal,
      );
      return rows.map((r) => ({ id: r.id, label: `${r.courtCaseNumber}/${String(r.courtCaseYear)}`, isDate: false }));
    }
    case 'TASK': {
      const rows = await listAll<{ id: string; title: string }>('/api/v1/tasks', { caseId }, signal);
      return rows.map((r) => ({ id: r.id, label: r.title, isDate: false }));
    }
  }
}
