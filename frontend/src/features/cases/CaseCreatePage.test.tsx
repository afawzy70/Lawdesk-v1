import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { aCase, jsonResponse, problem, renderCases, scriptedFetch } from './testSupport';

function today() {
  const now = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${String(now.getFullYear())}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

async function fillValidCase(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByRole('textbox', { name: 'Title' }), 'Ahmed v. Nile Trading');
  await user.selectOptions(screen.getByRole('combobox', { name: 'Case type' }), 'COMMERCIAL');
  await user.type(screen.getByRole('textbox', { name: 'Subject' }), 'Unpaid invoices');
}

describe('create case (LD-012)', () => {
  it('offers labelled fields, generates the reference by default and opens on today', () => {
    scriptedFetch(() => jsonResponse({}));
    renderCases('/cases/new');

    expect(screen.getByRole('heading', { level: 1, name: 'New case' })).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Reference number' })).toHaveAccessibleDescription(
      'Leave empty to generate one (LD-YYYY-NNNNN).',
    );
    expect(screen.getByRole('textbox', { name: 'Title' })).toHaveAttribute('aria-required', 'true');
    expect(screen.getByRole('combobox', { name: 'Case type' })).toHaveValue('');
    expect(screen.getByLabelText('Opened on')).toHaveValue(today());
    expect(screen.getByRole('link', { name: 'Back to cases' })).toHaveAttribute('href', '/cases?lang=en');
    expect(document.title).toBe('New case — LawDesk (Preview)');
  });

  it('creates the case and opens its details with a confirmation', async () => {
    const user = userEvent.setup();
    const created = aCase();
    const { requests } = scriptedFetch((request) =>
      request.method === 'POST' ? jsonResponse(created, 201) : jsonResponse(created),
    );
    const { url } = renderCases('/cases/new');

    await fillValidCase(user);
    await user.click(screen.getByRole('button', { name: 'Create case' }));

    expect(await screen.findByText('Case LD-2026-00001 created.')).toHaveAttribute('role', 'status');
    expect(url()).toBe(`/cases/${created.id}?lang=en`);
    expect(requests[0]).toEqual({
      method: 'POST',
      url: '/api/v1/cases',
      body: {
        referenceNo: null,
        title: 'Ahmed v. Nile Trading',
        caseType: 'COMMERCIAL',
        subject: 'Unpaid invoices',
        openedOn: today(),
      },
    });
  });

  it('shows each field error next to its field and announces a summary', async () => {
    const user = userEvent.setup();
    scriptedFetch(() =>
      problem(400, {
        errors: [
          { field: 'title', code: 'NotBlank' },
          { field: 'caseType', code: 'NotNull' },
          { field: 'referenceNo', code: 'Pattern' },
          { field: 'subject', code: 'Size' },
          { field: 'openedOn', code: 'PastOrPresent' },
          { field: 'unknown', code: 'Whatever' },
        ],
      }),
    );
    renderCases('/cases/new');

    await user.click(screen.getByRole('button', { name: 'Create case' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Please correct the highlighted fields.');
    const title = screen.getByRole('textbox', { name: 'Title' });
    expect(title).toHaveAttribute('aria-invalid', 'true');
    expect(title).toHaveAccessibleDescription('This field is required.');
    expect(screen.getByRole('combobox', { name: 'Case type' })).toHaveAccessibleDescription(
      'This field is required.',
    );
    expect(screen.getByRole('textbox', { name: 'Reference number' })).toHaveAccessibleDescription(
      'This value has an invalid format. Leave empty to generate one (LD-YYYY-NNNNN).',
    );
    expect(screen.getByRole('textbox', { name: 'Subject' })).toHaveAccessibleDescription('This value is too long.');
    expect(screen.getByLabelText('Opened on')).toHaveAccessibleDescription('The date cannot be in the future.');
  });

  it('falls back to a generic message for an unknown constraint', async () => {
    const user = userEvent.setup();
    scriptedFetch(() => problem(400, { errors: [{ field: 'title', code: 'Email' }] }));
    renderCases('/cases/new');

    await user.click(screen.getByRole('button', { name: 'Create case' }));

    await waitFor(() => {
      expect(screen.getByRole('textbox', { name: 'Title' })).toHaveAccessibleDescription('This value is not valid.');
    });
  });

  it('marks a duplicate or reserved reference number on the field', async () => {
    const user = userEvent.setup();
    let answer = problem(409);
    scriptedFetch(() => answer);
    renderCases('/cases/new');
    await fillValidCase(user);
    await user.type(screen.getByRole('textbox', { name: 'Reference number' }), 'Cairo-55');

    await user.click(screen.getByRole('button', { name: 'Create case' }));
    await waitFor(() => {
      expect(screen.getByRole('textbox', { name: 'Reference number' })).toHaveAccessibleDescription(
        'This reference number is already used by another case. Leave empty to generate one (LD-YYYY-NNNNN).',
      );
    });

    answer = problem(422, { rule: 'REFERENCE_NO_RESERVED' });
    await user.click(screen.getByRole('button', { name: 'Create case' }));
    await waitFor(() => {
      expect(screen.getByRole('textbox', { name: 'Reference number' })).toHaveAccessibleDescription(
        'Reference numbers starting with "LD-" and a digit are reserved for generated numbers. ' +
          'Leave empty to generate one (LD-YYYY-NNNNN).',
      );
    });
  });

  it.each([
    [403, 'You do not have permission to do this.'],
    [401, 'Sign in to continue.'],
    [500, 'The case could not be saved. Try again later.'],
    [422, 'The case could not be saved. Try again later.'],
  ])('explains a %i answer', async (status, message) => {
    const user = userEvent.setup();
    scriptedFetch(() => problem(status));
    renderCases('/cases/new');

    await fillValidCase(user);
    await user.click(screen.getByRole('button', { name: 'Create case' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(message);
  });

  it('explains an unreachable server', async () => {
    const user = userEvent.setup();
    scriptedFetch(() => Promise.reject(new TypeError('Failed to fetch')));
    renderCases('/cases/new');

    await user.click(screen.getByRole('button', { name: 'Create case' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('The case could not be saved. Try again later.');
  });

  it('prevents a second submission while saving', async () => {
    const user = userEvent.setup();
    const { requests } = scriptedFetch(() => new Promise<Response>(() => undefined));
    renderCases('/cases/new');
    await fillValidCase(user);

    await user.click(screen.getByRole('button', { name: 'Create case' }));

    const saving = await screen.findByRole('button', { name: 'Saving…' });
    expect(saving).toBeDisabled();
    await user.click(saving);
    expect(requests).toHaveLength(1);
  });

  it('renders in Arabic', () => {
    scriptedFetch(() => jsonResponse({}));
    renderCases('/cases/new?lang=ar');

    expect(screen.getByRole('heading', { level: 1, name: 'قضية جديدة' })).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'عنوان القضية' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'عمالي' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'إنشاء القضية' })).toBeInTheDocument();
  });
});
