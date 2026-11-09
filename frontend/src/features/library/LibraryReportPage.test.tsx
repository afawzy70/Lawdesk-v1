import { screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Summary } from './api';
import { jsonResponse, problem, renderLibrary, script } from './testSupport';

/** LD-052: counts by court, year, direction, finality and classification, and the legal questions named most. */
beforeEach(() => {
  vi.unstubAllGlobals();
});

const summary = (overrides: Partial<Summary> = {}): Summary => ({
  total: 5,
  active: 4,
  withdrawn: 1,
  byCourt: [{ key: 'محكمه التمييز', label: 'محكمة التمييز', count: 3 }],
  byYear: [
    { key: '2021', label: '2021', count: 2 },
    { key: null, label: null, count: 1 },
  ],
  byDirection: [
    { key: 'FOR_AUTHORITY', label: 'FOR_AUTHORITY', count: 2 },
    { key: 'NOT_SPECIFIED', label: 'NOT_SPECIFIED', count: 1 },
  ],
  byFinality: [{ key: 'FINAL', label: 'FINAL', count: 3 }],
  byClassification: [
    { dimension: 'LEGAL_TOPIC', classificationId: 'k1', code: 'LIAB', labelAr: 'المسؤولية', labelEn: 'Liability', count: 2 },
  ],
  frequentIssues: [
    { topic: 'بدء التقادم', principleCount: 2, judgmentCount: 2, firstJudgmentDate: '2019-03-01', lastJudgmentDate: '2022-03-01' },
    { topic: 'الاختصاص', principleCount: 1, judgmentCount: 1, firstJudgmentDate: null, lastJudgmentDate: null },
  ],
  note: 'n',
  ...overrides,
});

describe('library report', () => {
  it('shows the counts, the principle reminder and the issues with their counts and dates, never a verdict', async () => {
    script(() => jsonResponse(summary()));
    renderLibrary('/library/reports');
    expect(await screen.findByRole('heading', { level: 1, name: 'Library reports' })).toBeInTheDocument();
    expect(screen.getByText('Judgments in the library: 4 active, 1 withdrawn.')).toBeInTheDocument();
    expect(screen.getByText('محكمة التمييز')).toBeInTheDocument();
    expect(screen.getByText('No date')).toBeInTheDocument();
    expect(screen.getByText('In favour of the authority')).toBeInTheDocument();
    expect(screen.getByText('Not specified')).toBeInTheDocument();
    expect(screen.getByText('Final')).toBeInTheDocument();
    expect(screen.getByText('Liability')).toBeInTheDocument();
    expect(screen.getByText('Legal topic')).toBeInTheDocument();
    expect(screen.getByText(/2 principles in 2 judgments, 01\/03\/2019 to 01\/03\/2022/)).toBeInTheDocument();
    expect(screen.getByText(/1 principles in 1 judgments, Not specified to Not specified/)).toBeInTheDocument();
    expect(screen.queryByText(/settled/i)).not.toBeInTheDocument();
  });

  it('says there is nothing to count in an empty library', async () => {
    script(() =>
      jsonResponse(summary({ total: 0, active: 0, withdrawn: 0, byCourt: [], byYear: [], byDirection: [], byFinality: [], byClassification: [], frequentIssues: [] })),
    );
    renderLibrary('/library/reports');
    expect((await screen.findAllByText('Nothing to count yet.')).length).toBe(6);
  });

  it('shows the access-denied page and the failure message', async () => {
    script(() => problem(403));
    const denied = renderLibrary('/library/reports');
    expect(await screen.findByRole('heading', { name: /access/i })).toBeInTheDocument();
    denied.unmount();
    script(() => problem(500));
    renderLibrary('/library/reports');
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be loaded');
  });
});
