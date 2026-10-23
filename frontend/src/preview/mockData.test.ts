import { describe, expect, it } from 'vitest';
import { PREVIEW_CASES_BY_STAGE, PREVIEW_HEARINGS, PREVIEW_KPIS, STAGES } from './mockData';

describe('preview mock data', () => {
  it('provides the four dashboard KPIs as non-negative whole numbers', () => {
    expect(PREVIEW_KPIS.map((k) => k.id)).toEqual([
      'openCases',
      'hearingsThisWeek',
      'pendingJudgments',
      'expertReportsDue',
    ]);
    for (const kpi of PREVIEW_KPIS) {
      expect(Number.isInteger(kpi.value) && kpi.value >= 0).toBe(true);
    }
  });

  it('covers each litigation stage once', () => {
    expect(STAGES).toEqual(['firstInstance', 'appeal', 'cassation']);
    expect(PREVIEW_CASES_BY_STAGE.map((s) => s.stage)).toEqual([...STAGES]);
  });

  it('keeps the stage breakdown consistent with the open-cases KPI', () => {
    const total = PREVIEW_CASES_BY_STAGE.reduce((sum, s) => sum + s.count, 0);
    expect(PREVIEW_KPIS.find((k) => k.id === 'openCases')?.value).toBe(total);
  });

  it('uses only obviously synthetic courts, labelled as samples in both languages', () => {
    expect(PREVIEW_HEARINGS.length).toBeGreaterThan(0);
    for (const hearing of PREVIEW_HEARINGS) {
      expect(hearing.court.en).toMatch(/^Sample Court of /);
      expect(hearing.court.ar).toContain('النموذجية');
    }
  });

  it('identifies cases by sample sequence number only (no real case numbers, parties or people)', () => {
    for (const hearing of PREVIEW_HEARINGS) {
      expect(Number.isInteger(hearing.sampleCaseNumber)).toBe(true);
      expect(Object.keys(hearing).sort()).toEqual(['court', 'id', 'sampleCaseNumber', 'stage', 'startsAt']);
    }
  });

  it('lists hearings in chronological order with valid ISO instants', () => {
    const times = PREVIEW_HEARINGS.map((h) => Date.parse(h.startsAt));
    for (const t of times) expect(Number.isNaN(t)).toBe(false);
    expect(times).toEqual([...times].sort((a, b) => a - b));
  });
});
