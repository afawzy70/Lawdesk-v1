import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { aCase, jsonResponse, problem, renderCases, scriptedFetch } from './testSupport';

const CASE = aCase({ referenceNo: 'R-1', title: 'Original title', status: 'ACTIVE', version: 4 });
const EDIT = `/cases/${CASE.id}/edit`;

describe('edit case (LD-013)', () => {
  it('loads the case into the form', async () => {
    const { requests } = scriptedFetch(() => jsonResponse(CASE));
    renderCases(EDIT);

    expect(screen.getByRole('status')).toHaveTextContent('Loading case…');
    expect(await screen.findByRole('heading', { level: 1, name: 'Edit case R-1' })).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Title' })).toHaveValue('Original title');
    expect(screen.getByRole('combobox', { name: 'Case type' })).toHaveValue('COMMERCIAL');
    expect(screen.getByRole('textbox', { name: 'Subject' })).toHaveValue('Unpaid invoices');
    expect(screen.getByLabelText('Opened on')).toHaveValue('2026-03-01');
    expect(screen.queryByRole('textbox', { name: 'Reference number' })).not.toBeInTheDocument();
    expect(screen.getByText('Active')).toBeInTheDocument();
    expect(requests[0]?.url).toBe(`/api/v1/cases/${CASE.id}`);
    // The title follows the loaded case in an effect, which may run after the heading is on screen.
    await waitFor(() => {
      expect(document.title).toBe('Edit case R-1 — LawDesk (Preview)');
    });
  });

  it('saves with the version it was loaded at, then continues from the new version', async () => {
    const user = userEvent.setup();
    let version = CASE.version;
    const { requests } = scriptedFetch((request) => {
      if (request.method === 'PUT') {
        version += 1;
        return jsonResponse({ ...CASE, ...(request.body as object), version });
      }
      return jsonResponse(CASE);
    });
    renderCases(EDIT);
    const title = await screen.findByRole('textbox', { name: 'Title' });

    await user.clear(title);
    await user.type(title, 'New title');
    await user.click(screen.getByRole('button', { name: 'Save changes' }));

    expect(await screen.findByText('Changes saved.')).toHaveAttribute('role', 'status');
    expect(requests[1]).toEqual({
      method: 'PUT',
      url: `/api/v1/cases/${CASE.id}`,
      body: { title: 'New title', caseType: 'COMMERCIAL', subject: 'Unpaid invoices', openedOn: '2026-03-01', version: 4 },
    });
    expect(screen.getByRole('textbox', { name: 'Title' })).toHaveValue('New title');

    await user.click(screen.getByRole('button', { name: 'Save changes' }));
    await waitFor(() => {
      expect(requests).toHaveLength(3);
    });
    expect((requests[2]?.body as { version: number }).version).toBe(5);
  });

  it('on a stale version offers to reload the latest case', async () => {
    const user = userEvent.setup();
    let latest = CASE;
    const { requests } = scriptedFetch((request) => (request.method === 'PUT' ? problem(409) : jsonResponse(latest)));
    renderCases(EDIT);
    await screen.findByRole('textbox', { name: 'Title' });

    latest = { ...CASE, title: 'Changed by a colleague', version: 5 };
    await user.click(screen.getByRole('button', { name: 'Save changes' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Someone else changed this case. Reload it to see the latest version, then apply your changes again.',
    );
    await user.click(screen.getByRole('button', { name: 'Reload case' }));

    await waitFor(() => {
      expect(screen.getByRole('textbox', { name: 'Title' })).toHaveValue('Changed by a colleague');
    });
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(requests.filter((r) => r.method === 'GET')).toHaveLength(2);
  });

  it('explains that closed and archived cases are read-only', async () => {
    const user = userEvent.setup();
    scriptedFetch((request) =>
      request.method === 'PUT' ? problem(422, { rule: 'CASE_READ_ONLY' }) : jsonResponse(CASE),
    );
    renderCases(EDIT);
    await screen.findByRole('textbox', { name: 'Title' });

    await user.click(screen.getByRole('button', { name: 'Save changes' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Closed and archived cases cannot be edited.');
  });

  it('shows field errors from the server', async () => {
    const user = userEvent.setup();
    scriptedFetch((request) =>
      request.method === 'PUT' ? problem(400, { errors: [{ field: 'title', code: 'Size' }] }) : jsonResponse(CASE),
    );
    renderCases(EDIT);
    await screen.findByRole('textbox', { name: 'Title' });

    await user.click(screen.getByRole('button', { name: 'Save changes' }));

    await waitFor(() => {
      expect(screen.getByRole('textbox', { name: 'Title' })).toHaveAccessibleDescription('This value is too long.');
    });
  });

  it.each([
    [500, 'The case could not be saved. Try again later.'],
    [403, 'You do not have permission to do this.'],
  ])('explains a %i answer when saving', async (status, message) => {
    const user = userEvent.setup();
    scriptedFetch((request) => (request.method === 'PUT' ? problem(status) : jsonResponse(CASE)));
    renderCases(EDIT);
    await screen.findByRole('textbox', { name: 'Title' });

    await user.click(screen.getByRole('button', { name: 'Save changes' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(message);
    expect(screen.queryByRole('button', { name: 'Reload case' })).not.toBeInTheDocument();
  });

  it.each([
    [404, 'This case does not exist.'],
    [401, 'Sign in to continue.'],
    [500, 'The case could not be loaded. Try again later.'],
  ])('explains a %i answer when loading', async (status, message) => {
    scriptedFetch(() => problem(status));
    renderCases(EDIT);

    expect(await screen.findByRole('alert')).toHaveTextContent(message);
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to cases' })).toBeInTheDocument();
  });

  it('links back to the case', async () => {
    scriptedFetch(() => jsonResponse(CASE));
    renderCases(EDIT);

    expect(await screen.findByRole('link', { name: 'Back to the case' })).toHaveAttribute(
      'href',
      `/cases/${CASE.id}?lang=en`,
    );
  });

  it('shows the shared access-denied page without the permission to view', async () => {
    scriptedFetch(() => problem(403));
    renderCases(EDIT);

    expect(await screen.findByRole('heading', { level: 1, name: 'Access denied' })).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });

  it('renders in Arabic', async () => {
    scriptedFetch(() => jsonResponse(CASE));
    renderCases(`${EDIT}?lang=ar`);

    expect(await screen.findByRole('heading', { level: 1, name: 'تعديل القضية R-1' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'حفظ التغييرات' })).toBeInTheDocument();
    expect(screen.getByText('متداولة')).toBeInTheDocument();
  });
});
