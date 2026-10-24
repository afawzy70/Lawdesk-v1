import type { MessageKey } from '../../i18n/catalog';

const KNOWN_EVENTS: Record<string, MessageKey> = {
  CASE_CREATED: 'cases.event.CASE_CREATED',
  CASE_UPDATED: 'cases.event.CASE_UPDATED',
  CASE_STATUS_CHANGED: 'cases.event.CASE_STATUS_CHANGED',
  PARTY_ADDED: 'cases.event.PARTY_ADDED',
  PARTY_REMOVED: 'cases.event.PARTY_REMOVED',
  // Recorded by other modules through CaseLookup#recordEvent (Phase 4; Phase 5, LD-029 activity history).
  PROCEEDING_RECORDED: 'cases.event.PROCEEDING_RECORDED',
  JUDGMENT_RECORDED: 'cases.event.JUDGMENT_RECORDED',
  SPECIAL_DECISION_RECORDED: 'cases.event.SPECIAL_DECISION_RECORDED',
  HEARING_SCHEDULED: 'cases.event.HEARING_SCHEDULED',
  HEARING_RESCHEDULED: 'cases.event.HEARING_RESCHEDULED',
  HEARING_HELD: 'cases.event.HEARING_HELD',
  HEARING_ADJOURNED: 'cases.event.HEARING_ADJOURNED',
  HEARING_CANCELLED: 'cases.event.HEARING_CANCELLED',
  TASK_CREATED: 'cases.event.TASK_CREATED',
  TASK_ASSIGNED: 'cases.event.TASK_ASSIGNED',
  TASK_COMPLETED: 'cases.event.TASK_COMPLETED',
  TASK_CANCELLED: 'cases.event.TASK_CANCELLED',
  TASK_REOPENED: 'cases.event.TASK_REOPENED',
  DOCUMENT_ATTACHED: 'cases.event.DOCUMENT_ATTACHED',
  DOCUMENT_REMOVED: 'cases.event.DOCUMENT_REMOVED',
  EXPERT_REFERRED: 'cases.event.EXPERT_REFERRED',
  EXPERT_SESSION_RECORDED: 'cases.event.EXPERT_SESSION_RECORDED',
  EXPERT_REPORT_SUBMITTED: 'cases.event.EXPERT_REPORT_SUBMITTED',
  EXPERT_REFERRAL_CANCELLED: 'cases.event.EXPERT_REFERRAL_CANCELLED',
  NOTE_ADDED: 'cases.event.NOTE_ADDED',
  NOTE_REMOVED: 'cases.event.NOTE_REMOVED',
};

/** The message naming a timeline event type; types this screen does not know read "Other event". */
export function eventLabel(type: string): MessageKey {
  return KNOWN_EVENTS[type] ?? 'cases.event.other';
}
