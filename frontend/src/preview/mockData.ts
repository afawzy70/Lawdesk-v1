/**
 * ALL mock data for the early visual preview lives in this one module (see docs/delivery/PREVIEW.md).
 *
 * Every value here is invented. Cases are identified only by a sample sequence number ("Sample Case 001"), courts
 * are fictional ("Sample Court of …"), and there are no parties, lawyers, judges, firms or real case numbers.
 * Nothing in this file may be replaced with real or realistic personal data.
 */
import type { Locale } from '../i18n/locale';

export type LocalizedText = Record<Locale, string>;

export const STAGES = ['firstInstance', 'appeal', 'cassation'] as const;
export type Stage = (typeof STAGES)[number];

export type KpiId = 'openCases' | 'hearingsThisWeek' | 'pendingJudgments' | 'expertReportsDue';

export interface PreviewKpi {
  id: KpiId;
  value: number;
}

export interface PreviewHearing {
  id: string;
  sampleCaseNumber: number;
  court: LocalizedText;
  stage: Stage;
  /** ISO 8601 instant. */
  startsAt: string;
}

export interface PreviewStageCount {
  stage: Stage;
  count: number;
}

const COURTS: Record<Stage, LocalizedText> = {
  firstInstance: { en: 'Sample Court of First Instance', ar: 'محكمة الدرجة الأولى النموذجية' },
  appeal: { en: 'Sample Court of Appeal', ar: 'محكمة الاستئناف النموذجية' },
  cassation: { en: 'Sample Court of Cassation', ar: 'محكمة النقض النموذجية' },
};

export const PREVIEW_CASES_BY_STAGE: readonly PreviewStageCount[] = [
  { stage: 'firstInstance', count: 21 },
  { stage: 'appeal', count: 14 },
  { stage: 'cassation', count: 7 },
];

export const PREVIEW_KPIS: readonly PreviewKpi[] = [
  { id: 'openCases', value: 42 },
  { id: 'hearingsThisWeek', value: 5 },
  { id: 'pendingJudgments', value: 9 },
  { id: 'expertReportsDue', value: 3 },
];

function hearing(sampleCaseNumber: number, stage: Stage, startsAt: string): PreviewHearing {
  return { id: `sample-hearing-${String(sampleCaseNumber)}`, sampleCaseNumber, court: COURTS[stage], stage, startsAt };
}

export const PREVIEW_HEARINGS: readonly PreviewHearing[] = [
  hearing(1, 'firstInstance', '2026-11-02T07:00:00Z'),
  hearing(2, 'appeal', '2026-11-02T09:30:00Z'),
  hearing(3, 'firstInstance', '2026-11-03T08:00:00Z'),
  hearing(4, 'cassation', '2026-11-04T10:00:00Z'),
  hearing(5, 'appeal', '2026-11-05T07:30:00Z'),
];
