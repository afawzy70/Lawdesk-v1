import { describe, expect, it } from 'vitest';
import { ar } from '../../i18n/messages/ar';
import { en } from '../../i18n/messages/en';
import { eventLabel } from './timelineEvents';

/**
 * LD-014 / LD-029: the case timeline is the activity history, so every event type another module records through
 * CaseLookup#recordEvent has its own label instead of "Other event" (architecture/CONTRACTS.md §5, §8.3).
 */
const RECORDED_TYPES = [
  // Phase 4 (com.lawdesk.proceedings)
  'PROCEEDING_RECORDED',
  'JUDGMENT_RECORDED',
  'SPECIAL_DECISION_RECORDED',
  // Phase 5 (com.lawdesk.hearings, tasks, documents, experts, notes)
  'HEARING_SCHEDULED',
  'HEARING_RESCHEDULED',
  'HEARING_HELD',
  'HEARING_ADJOURNED',
  'HEARING_CANCELLED',
  'TASK_CREATED',
  'TASK_ASSIGNED',
  'TASK_COMPLETED',
  'TASK_CANCELLED',
  'TASK_REOPENED',
  'DOCUMENT_ATTACHED',
  'DOCUMENT_REMOVED',
  'EXPERT_REFERRED',
  'EXPERT_SESSION_RECORDED',
  'EXPERT_REPORT_SUBMITTED',
  'EXPERT_REFERRAL_CANCELLED',
  'NOTE_ADDED',
  'NOTE_REMOVED',
];

describe('case timeline event labels', () => {
  it('names every recorded event type in English and Arabic', () => {
    for (const type of RECORDED_TYPES) {
      const key = eventLabel(type);
      expect(key, type).toBe(`cases.event.${type}`);
      expect(en[key], type).toBeTruthy();
      expect(ar[key], type).toBeTruthy();
    }
  });

  it('keeps "Other event" for a type it does not know', () => {
    expect(eventLabel('SOMETHING_NEW')).toBe('cases.event.other');
  });
});
