import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { CASE, problem, renderWith, serve } from '../pleadings/testSupport';
import { EnforcementListPage } from './EnforcementListPage';

/** LD-055 (requirement 11): the judgments not executed or late, in the cases the user may see. */

const row = (id: string, extra = {}) => ({
  judgmentId: id,
  caseId: CASE,
  caseReferenceNo: 'LD-2026-00007',
  caseTitle: 'Lease dispute',
  stage: 'FIRST_INSTANCE',
  courtCaseNumber: '1055',
  courtCaseYear: 2026,
  judgmentDate: '2026-04-01',
  status: 'IN_PROGRESS',
  tracked: true,
  dueOn: '2026-05-01',
  late: false,
  responsibleMemberId: 'u1',
  responsibleMemberName: 'Maha',
  executingParty: 'Execution Department',
  amountRequired: null,
  ...extra,
});

const page = (items: unknown[], extra = {}) => ({ items, page: 0, size: 20, totalItems: items.length, totalPages: 1, ...extra });

describe('judgments not yet executed', () => {
  it('lists them, marks the late ones and the ones without a file', async () => {
    serve({
      'GET /api/v1/enforcement/judgments': page([row('j1', { late: true }), row('j2', { status: null, tracked: false, dueOn: null, responsibleMemberName: null, executingParty: null })]),
    });
    renderWith(<EnforcementListPage />);
    expect(await screen.findByText('2 judgments')).toBeVisible();
    const table = screen.getByRole('table');
    expect(within(table).getByText('Late')).toBeVisible();
    expect(within(table).getByText('No file recorded')).toBeVisible();
    expect(within(table).getAllByText('Lease dispute', { exact: false }).length).toBe(2);
    expect(within(table).getAllByRole('link')[0]).toHaveAttribute('href', expect.stringContaining(CASE));
    expect(within(table).getAllByText('1055/2026')).toHaveLength(2);
  });

  it('switches between not executed, late and all judgments', async () => {
    const user = userEvent.setup();
    const backend = serve({ 'GET /api/v1/enforcement/judgments': page([]) });
    renderWith(<EnforcementListPage />);
    expect(await screen.findByText('No judgments in this list.')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Not executed' })).toHaveAttribute('aria-pressed', 'true');
    await user.click(screen.getByRole('button', { name: 'Late' }));
    await user.click(screen.getByRole('button', { name: 'All judgments' }));
    await screen.findByText('No judgments in this list.');
    const scopes = backend.to('GET', '/api/v1/enforcement/judgments').map((s) => new URLSearchParams(s.query).get('scope'));
    expect(scopes).toEqual(['UNEXECUTED', 'LATE', 'ALL']);
    expect(screen.getByRole('button', { name: 'All judgments' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('pages and retries after a failure', async () => {
    const user = userEvent.setup();
    let failing = true;
    serve({
      'GET /api/v1/enforcement/judgments': (sent) => {
        if (failing) return problem(500);
        const second = new URLSearchParams(sent.query).get('page') === '1';
        return page([row(second ? 'j2' : 'j1', { courtCaseNumber: second ? '2000' : '1055' })], { page: second ? 1 : 0, totalItems: 2, totalPages: 2 });
      },
    });
    renderWith(<EnforcementListPage />);
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be saved');
    failing = false;
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByText('1055/2026')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Next' }));
    expect(await screen.findByText('2000/2026')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Previous' }));
    expect(await screen.findByText('1055/2026')).toBeVisible();
  });

  it('shows the access page without the permission and works in Arabic', async () => {
    serve({ 'GET /api/v1/enforcement/judgments': problem(403) });
    const first = renderWith(<EnforcementListPage />);
    expect(await screen.findByRole('heading', { name: 'Access denied' })).toBeVisible();
    first.unmount();

    serve({ 'GET /api/v1/enforcement/judgments': page([row('j1')]) });
    renderWith(<EnforcementListPage />, { lang: 'ar' });
    expect(await screen.findByText('عدد الأحكام: ١')).toBeVisible();
    expect(screen.getByText('قيد التنفيذ')).toBeVisible();
  });
});
