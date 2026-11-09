import { screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import { mount, page, problem, serve } from '../followup/testSupport';
import { JudgmentDeadlines } from './JudgmentDeadlines';
import { deadline } from './testSupport';

/** LD-054: the deadlines that run from one judgment, in place of the empty "appeal deadline" field. */

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token';
});

describe('judgment deadlines', () => {
  it('lists the deadlines of the judgment with their approval state', async () => {
    const { requests } = serve((r) => (r.path === '/api/v1/judgments/j1/deadlines' ? page([
      deadline({ action: 'Appeal', sourceType: 'JUDGMENT' }),
      deadline({ id: 'd2', action: 'Grievance', calcStatus: 'PROPOSED', dueOn: null }),
      deadline({ id: 'd3', action: 'Cassation', calcStatus: 'APPROVED', official: true }),
    ]) : undefined));
    mount(<JudgmentDeadlines judgmentId="j1" />, ['DEADLINE_VIEW']);
    await screen.findByText('Appeal');
    expect(screen.getByText('Grievance').closest('li')).toHaveTextContent('Not specified');
    expect(screen.getByText('Grievance').closest('li')).toHaveTextContent('proposed from a rule');
    expect(screen.getByText('Cassation').closest('li')).not.toHaveTextContent('not an official deadline');
    expect(requests[0]?.path).toBe('/api/v1/judgments/j1/deadlines');
  });

  it('says none is recorded, and shows nothing to someone without the permission or when it cannot be read', async () => {
    serve(() => page([]));
    const first = mount(<JudgmentDeadlines judgmentId="j1" />, ['DEADLINE_VIEW']);
    expect(await screen.findByText(/No deadline is recorded from this judgment/)).toBeInTheDocument();
    first.unmount();
    const { requests } = serve(() => page([]));
    const second = mount(<JudgmentDeadlines judgmentId="j1" />, []);
    await waitFor(() => { expect(screen.queryByText('Deadlines from this judgment')).not.toBeInTheDocument(); });
    expect(requests).toHaveLength(0);
    second.unmount();
    serve(() => problem(403));
    mount(<JudgmentDeadlines judgmentId="j1" />, ['DEADLINE_VIEW']);
    await waitFor(() => { expect(screen.queryByText('Deadlines from this judgment')).not.toBeInTheDocument(); });
  });
});
