import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { classification, jsonResponse, listItem, page, problem, renderLibrary, script, user } from './testSupport';

/** LD-052: the library list and its Arabic search (requirements section 16.3). */
beforeEach(() => {
  vi.unstubAllGlobals();
});

const lists = () => jsonResponse([classification({ id: 'k1' }), classification({ id: 'k2', labelAr: 'عقود', labelEn: null })]);

describe('library list and search', () => {
  it('lists the judgments with their citation, state badges and classification', async () => {
    script((call) => {
      if (call.path === '/api/v1/library/classifications') return lists();
      return jsonResponse(
        page([
          listItem({
            principleCount: 2,
            relationTypes: ['OVERTURNED'],
            direction: 'FOR_AUTHORITY',
            confidentiality: 'RESTRICTED',
            classifications: [classification()],
          }),
          listItem({ id: 'j2', subject: 'إيجار', status: 'WITHDRAWN', direction: null, caseNumber: null, judgmentDate: null }),
        ]),
      );
    });
    renderLibrary('/library');

    expect(await screen.findByRole('link', { name: 'تعويض عن قرار إداري معيب' })).toHaveAttribute('href', '/library/j1?lang=en');
    expect(screen.getByText('2 judgments')).toBeInTheDocument();
    expect(screen.getByText(/محكمة التمييز - 1234\/2020 - 02\/03\/2021/)).toBeInTheDocument();
    expect(screen.getByText('2 principles')).toBeInTheDocument();
    expect(screen.getByText(/Later events recorded: Overturned/)).toBeInTheDocument();
    // Once as a filter option and once as the badge of the first judgment.
    expect(screen.getAllByText('In favour of the authority')).toHaveLength(2);
    expect(screen.getByText('Restricted')).toBeInTheDocument();
    expect(screen.getByText('Withdrawn')).toBeInTheDocument();
    // Once as a filter option and once as the badge.
    expect(screen.getAllByText('Administrative liability')).toHaveLength(2);
  });

  it('says so when the library is empty and starts with no judgment', async () => {
    script((call) => (call.path.endsWith('classifications') ? jsonResponse([]) : jsonResponse(page([]))));
    renderLibrary('/library');
    expect(await screen.findByText(/No judgment in the library matches/)).toBeInTheDocument();
  });

  it('sends every filter, resets to the first page and shows where the words were found', async () => {
    const { calls } = script((call) => {
      if (call.path.endsWith('classifications')) return lists();
      return jsonResponse(page([listItem({ matchedIn: ['PRINCIPLE', 'SIMILAR_TEXT'] })]));
    });
    const typing = userEvent.setup();
    renderLibrary('/library');
    await screen.findByText(/Found in: principle, similar spelling/);

    await typing.type(screen.getByLabelText('Words to search for'), 'مسؤولية');
    await typing.type(screen.getByLabelText('Court'), 'التمييز');
    await typing.type(screen.getByLabelText('Year of judgment'), '2020');
    await typing.selectOptions(screen.getByLabelText('Type of dispute'), 'LABOR');
    await typing.selectOptions(screen.getByLabelText('Court level'), 'APPEAL');
    await typing.selectOptions(screen.getByLabelText('Direction'), 'FOR_AUTHORITY');
    await typing.selectOptions(screen.getByLabelText('Finality'), 'NOT_FINAL');
    await typing.selectOptions(screen.getByLabelText('Classification'), 'k2');
    await typing.click(screen.getByLabelText('Include withdrawn judgments'));
    await typing.click(screen.getByRole('button', { name: 'Search' }));

    await waitFor(() => {
      const last = calls.filter((c) => c.path === '/api/v1/library/judgments').at(-1);
      expect(Object.fromEntries(last?.search ?? [])).toEqual({
        q: 'مسؤولية',
        courtName: 'التمييز',
        year: '2020',
        caseType: 'LABOR',
        courtLevel: 'APPEAL',
        direction: 'FOR_AUTHORITY',
        finality: 'NOT_FINAL',
        classificationId: 'k2',
        includeWithdrawn: 'true',
        page: '0',
        size: '20',
      });
    });

    await typing.click(screen.getByRole('button', { name: 'Clear' }));
    await waitFor(() => {
      const last = calls.filter((c) => c.path === '/api/v1/library/judgments').at(-1);
      expect(Object.fromEntries(last?.search ?? [])).toEqual({ page: '0', size: '20' });
    });
  });

  it('pages through the results', async () => {
    const { calls } = script((call) => {
      if (call.path.endsWith('classifications')) return jsonResponse([]);
      const pageNo = Number(call.search.get('page'));
      return jsonResponse(page([listItem({ id: `j${String(pageNo)}` })], { page: pageNo, totalItems: 25, totalPages: 2 }));
    });
    const typing = userEvent.setup();
    renderLibrary('/library');
    expect(await screen.findByText('Page 1 of 2')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled();
    await typing.click(screen.getByRole('button', { name: 'Next' }));
    expect(await screen.findByText('Page 2 of 2')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled();
    await typing.click(screen.getByRole('button', { name: 'Previous' }));
    expect(await screen.findByText('Page 1 of 2')).toBeInTheDocument();
    expect(calls.some((c) => c.search.get('page') === '1')).toBe(true);
  });

  it('shows the add, reports and lists links only to those who may use them', async () => {
    script((call) => (call.path.endsWith('classifications') ? jsonResponse([]) : jsonResponse(page([]))));
    const manager = renderLibrary('/library');
    await screen.findByText(/No judgment/);
    expect(screen.getByRole('link', { name: 'Add a judgment' })).toHaveAttribute('href', '/library/new?lang=en');
    expect(screen.getByRole('link', { name: 'Classification lists' })).toBeInTheDocument();
    manager.unmount();

    renderLibrary('/library', user(['LIBRARY_VIEW']));
    await screen.findByText(/No judgment/);
    expect(screen.queryByRole('link', { name: 'Add a judgment' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Classification lists' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Reports' })).toBeInTheDocument();
  });

  it('explains a failed load and shows the access-denied page when the backend refuses', async () => {
    script((call) => (call.path.endsWith('classifications') ? jsonResponse([]) : problem(500)));
    const failed = renderLibrary('/library');
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be loaded');
    failed.unmount();

    script((call) => (call.path.endsWith('classifications') ? jsonResponse([]) : problem(403)));
    renderLibrary('/library');
    expect(await screen.findByRole('heading', { name: /access/i })).toBeInTheDocument();
  });

  it('survives a failed classification list', async () => {
    script((call) => (call.path.endsWith('classifications') ? problem(500) : jsonResponse(page([listItem()]))));
    renderLibrary('/library');
    expect(await screen.findByRole('link', { name: 'تعويض عن قرار إداري معيب' })).toBeInTheDocument();
    expect(screen.getByLabelText('Classification')).toBeInTheDocument();
  });
});
