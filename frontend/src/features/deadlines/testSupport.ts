import type { Calculation, Deadline, DeadlineRule, Holiday } from './api';

/** Test-only fixtures of the WP4 screens. Every rule and holiday is made up for the test; the application has none. */

export const CASE = 'c0000000-0000-4000-8000-000000000001';

export function deadline(overrides: Partial<Deadline> = {}): Deadline {
  return {
    id: 'd1',
    caseId: CASE,
    caseReferenceNo: 'LD-2026-00001',
    caseTitle: 'Test case',
    sourceType: 'TEXT',
    sourceId: null,
    sourceText: 'Notice served on the authority',
    sourceLabel: null,
    startsOn: '2026-11-01',
    periodDays: null,
    dueOn: '2026-12-01',
    ruleId: null,
    ruleCode: null,
    calculation: null,
    action: 'File the appeal',
    ownerId: 'm1',
    ownerName: 'Mona Specialist',
    status: 'OPEN',
    completedOn: null,
    proofDocumentId: null,
    proofDocumentTitle: null,
    notes: null,
    calcStatus: 'MANUAL',
    official: false,
    approvedById: null,
    approvedByName: null,
    approvedAt: null,
    changeReason: null,
    overdue: false,
    daysRemaining: 22,
    version: 0,
    ...overrides,
  };
}

export function calculation(overrides: Partial<Calculation> = {}): Calculation {
  return {
    ruleId: 'r1',
    ruleCode: 'TEST-WORKING',
    ruleLabel: 'Test-only rule',
    periodDays: 5,
    countMethod: 'WORKING',
    countsStartDay: false,
    rollForward: false,
    closedWeekdays: [6, 7],
    legalSource: 'Test-only source',
    effectiveFrom: '2000-01-01',
    startsOn: '2031-03-03',
    firstCountedDay: '2031-03-04',
    rolledForwardFrom: null,
    dueOn: '2031-03-11',
    skipped: [
      { date: '2031-03-05', kind: 'HOLIDAY', label: 'Test holiday' },
      { date: '2031-03-08', kind: 'WEEKDAY', label: null },
    ],
    ...overrides,
  };
}

export function rule(overrides: Partial<DeadlineRule> = {}): DeadlineRule {
  return {
    id: 'r1',
    code: 'TEST-WORKING',
    label: 'Test-only rule',
    periodDays: 5,
    countMethod: 'WORKING',
    countsStartDay: false,
    rollForward: false,
    closedWeekdays: [6, 7],
    legalSource: 'Test-only source',
    effectiveFrom: '2000-01-01',
    status: 'APPROVED',
    approvedByName: 'Hala Supervisor',
    approvedAt: '2026-11-02T08:00:00Z',
    version: 1,
    ...overrides,
  };
}

export function holiday(overrides: Partial<Holiday> = {}): Holiday {
  return {
    id: 'h1',
    holidayOn: '2031-03-05',
    label: 'Test holiday',
    legalSource: 'Test-only source',
    status: 'DRAFT',
    approvedByName: null,
    approvedAt: null,
    version: 0,
    ...overrides,
  };
}
