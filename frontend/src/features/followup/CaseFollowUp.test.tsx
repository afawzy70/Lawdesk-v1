import { screen, waitFor } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { jsonResponse } from '../../test/http';
import { CaseFollowUp } from './CaseFollowUp';
import { CASE, mount, page, serve } from './testSupport';

/** The WP3 sections of the case page appear according to the user's permissions and the case's state. */

function backend() {
  return serve((r) => {
    if (r.path.endsWith('/links')) return jsonResponse({ items: [], hiddenCount: 0 });
    if (r.path.endsWith('/unified-timeline')) return jsonResponse({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0, deadlinesAvailable: false });
    if (r.path.endsWith('/info-requests') || r.path.endsWith('/risk-assessments') || r.path.endsWith('/deadlines')) return page([]);
    return undefined;
  });
}

describe('WP3 sections of the case page', () => {
  it('shows only the sections the user may open, with forms for those they may change', async () => {
    backend();
    mount(<CaseFollowUp caseId={CASE} archived={false} generation={0} />, ['CASE_VIEW']);
    expect(await screen.findByRole('heading', { name: 'Linked cases' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Unified timeline' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: /Information requests/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Legal risk assessment' })).not.toBeInTheDocument();
    expect(screen.queryByRole('form')).not.toBeInTheDocument();
  });

  it('shows the legal deadlines section only to a holder of the deadline permission, and its form only to a manager', async () => {
    backend();
    const first = mount(<CaseFollowUp caseId={CASE} archived={false} generation={0} />, ['CASE_VIEW', 'DEADLINE_VIEW']);
    expect(await screen.findByRole('heading', { name: 'Legal deadlines' })).toBeInTheDocument();
    expect(screen.queryByRole('form', { name: 'Record a deadline' })).not.toBeInTheDocument();
    first.unmount();
    const second = mount(<CaseFollowUp caseId={CASE} archived={false} generation={0} />, ['CASE_VIEW', 'DEADLINE_VIEW', 'DEADLINE_MANAGE']);
    expect(await screen.findByRole('form', { name: 'Record a deadline' })).toBeInTheDocument();
    second.unmount();
    mount(<CaseFollowUp caseId={CASE} archived generation={0} />, ['CASE_VIEW', 'DEADLINE_VIEW', 'DEADLINE_MANAGE']);
    await screen.findByRole('heading', { name: 'Legal deadlines' });
    await waitFor(() => { expect(screen.queryByRole('form', { name: 'Record a deadline' })).not.toBeInTheDocument(); });
  });

  it('offers the forms to a user who manages them, and none for an archived case', async () => {
    backend();
    const all = ['CASE_VIEW', 'CASE_LINK_MANAGE', 'INFO_REQUEST_VIEW', 'INFO_REQUEST_MANAGE', 'INFO_REQUEST_ESCALATE', 'RISK_VIEW', 'RISK_MANAGE'] as const;
    const first = mount(<CaseFollowUp caseId={CASE} archived={false} generation={0} />, [...all]);
    expect(await screen.findByRole('form', { name: 'Add a link' })).toBeInTheDocument();
    expect(await screen.findByRole('form', { name: 'New request' })).toBeInTheDocument();
    expect(await screen.findByRole('form', { name: 'Record a new version' })).toBeInTheDocument();
    first.unmount();
    mount(<CaseFollowUp caseId={CASE} archived generation={0} />, [...all]);
    await screen.findByRole('heading', { name: 'Legal risk assessment' });
    await waitFor(() => { expect(screen.queryByRole('form')).not.toBeInTheDocument(); });
  });
});
