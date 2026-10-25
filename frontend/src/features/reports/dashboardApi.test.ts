import { describe, expect, it, vi } from 'vitest';
import { jsonResponse } from '../../test/http';
import { getDashboard, isDashboard } from './dashboardApi';

describe('dashboard API (LD-030)', () => {
  it('reads the dashboard through the shared client', async () => {
    const fetchMock = vi.fn(() => Promise.resolve(jsonResponse({ cases: { total: 1 } })));
    vi.stubGlobal('fetch', fetchMock);
    await expect(getDashboard()).resolves.toEqual({ cases: { total: 1 } });
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/reports/dashboard',
      expect.objectContaining({ method: 'GET', credentials: 'same-origin' }),
    );
  });

  it('accepts only answers that carry every part the page reads', () => {
    const report = { cases: { total: 0 }, proceedings: { byStage: [] }, judgments: { recorded: 0 }, operations: { overdueTasks: 0 } };
    expect(isDashboard(report)).toBe(true);
    expect(isDashboard(null)).toBe(false);
    expect(isDashboard('figures')).toBe(false);
    expect(isDashboard({ ...report, operations: {} })).toBe(false);
    expect(isDashboard({ ...report, judgments: {} })).toBe(false);
    expect(isDashboard({ ...report, proceedings: {} })).toBe(false);
  });
});
