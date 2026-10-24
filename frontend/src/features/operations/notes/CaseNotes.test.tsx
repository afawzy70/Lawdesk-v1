import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { jsonResponse } from '../../../test/http';
import { CASE, caseNote, page, problem, renderOperations, scriptedFetch, type Request } from '../testSupport';

/** LD-029 legal notes in the case's operations section (docs/delivery/PHASE-5-ACCEPTANCE.md, QA-0232). */

const NOTES = `/api/v1/cases/${CASE}/notes`;

interface Backend {
  notes?: Response;
  onWrite?: (request: Request) => Response;
}

function serve(backend: Backend = {}) {
  return scriptedFetch((r) => {
    if (r.method !== 'GET') return backend.onWrite?.(r);
    if (r.path === NOTES) return (backend.notes ?? page([])).clone();
    if (r.path.startsWith(`/api/v1/cases/${CASE}/`) || r.path === '/api/v1/tasks') return page([]);
    return undefined;
  });
}

const section = async () => screen.findByRole('region', { name: 'Legal notes' });

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token';
});

describe('case legal notes', () => {
  it('lists notes newest first with their author, and offers changes on own notes only', async () => {
    const { requests } = serve({
      notes: page([
        caseNote({ edited: true }),
        caseNote({ id: 'n2', body: 'A colleague note', authorName: 'Omar Adel', editable: false }),
      ]),
    });
    renderOperations(`/cases/${CASE}`);
    const region = await section();
    const items = await within(region).findAllByRole('article');
    expect(items[0]).toHaveTextContent('Client prefers settlement before the appeal.');
    expect(items[0]).toHaveTextContent('Mona Saleh');
    expect(items[0]).toHaveTextContent('(edited)');
    expect(within(items[0] as HTMLElement).getByRole('button', { name: 'Edit' })).toBeVisible();
    expect(items[1]).toHaveTextContent('Omar Adel');
    expect(within(items[1] as HTMLElement).queryByRole('button')).not.toBeInTheDocument();
    expect(within(region).getByText('Notes are internal to the office. The case history records who added them, never their text.')).toBeVisible();
    expect(requests.find((r) => r.path === NOTES)?.query.get('size')).toBe('100');
  });

  it('writes a note and shows a refusal', async () => {
    const user = userEvent.setup();
    const answers = [
      problem(400, { errors: [{ field: 'body', code: 'NotBlank' }] }),
      problem(403),
      jsonResponse(caseNote(), 201),
    ];
    const { requests } = serve({ onWrite: () => answers.shift() ?? problem(500) });
    renderOperations(`/cases/${CASE}`);
    const region = await section();
    expect(await within(region).findByText('No legal notes for this case yet.')).toBeVisible();
    const form = within(region).getByRole('form', { name: 'Write a legal note' });
    const add = within(form).getByRole('button', { name: 'Add note' });
    await user.click(add);
    expect(await within(form).findByText('This field is required.')).toBeVisible();
    await user.type(within(form).getByLabelText('Note'), 'Client prefers settlement before the appeal.');
    await user.click(add);
    expect(await within(form).findByText('You are not permitted to make this change.')).toBeVisible();
    await user.click(add);
    expect(await within(region).findByRole('status')).toHaveTextContent('The note was saved.');
    expect(within(form).getByLabelText('Note')).toHaveValue('');
    expect(requests.filter((r) => r.method === 'POST').at(-1)).toMatchObject({
      path: NOTES,
      body: { body: 'Client prefers settlement before the appeal.' },
    });
  });

  it('edits and removes an own note with its version', async () => {
    const user = userEvent.setup();
    const answers = [
      problem(422, { rule: 'NOTE_AUTHOR_ONLY' }),
      jsonResponse(caseNote()),
      problem(409),
      new Response(null, { status: 204 }),
    ];
    const { requests } = serve({ notes: page([caseNote()]), onWrite: () => answers.shift() ?? problem(500) });
    renderOperations(`/cases/${CASE}`);
    const region = await section();
    let item = (await within(region).findAllByRole('article'))[0] as HTMLElement;
    await user.click(within(item).getByRole('button', { name: 'Edit' }));
    const form = within(item).getByRole('form', { name: 'Edit the note' });
    await user.clear(within(form).getByLabelText('Note'));
    await user.type(within(form).getByLabelText('Note'), 'Settlement preferred.');
    await user.click(within(form).getByRole('button', { name: 'Save' }));
    expect(await within(form).findByText('Only the author can change or remove a note.')).toBeVisible();
    await user.click(within(form).getByRole('button', { name: 'Save' }));
    expect(await within(region).findByRole('status')).toHaveTextContent('The note was saved.');

    item = (await within(region).findAllByRole('article'))[0] as HTMLElement;
    await user.click(within(item).getByRole('button', { name: 'Remove' }));
    await user.click(within(item).getByRole('button', { name: 'Keep it' }));
    await user.click(within(item).getByRole('button', { name: 'Remove' }));
    await user.click(within(item).getByRole('button', { name: 'Remove the note' }));
    expect(
      await within(region).findByText('Someone else changed this record meanwhile. Reload and try again.'),
    ).toBeVisible();
    await user.click(within(item).getByRole('button', { name: 'Remove' }));
    await user.click(within(item).getByRole('button', { name: 'Remove the note' }));
    expect(await within(region).findByRole('status')).toHaveTextContent('The note was removed.');

    expect(requests.filter((r) => r.method === 'PUT').at(-1)).toMatchObject({
      path: '/api/v1/notes/n1',
      body: { body: 'Settlement preferred.', version: 0 },
    });
    const removal = requests.filter((r) => r.method === 'DELETE').at(-1);
    expect(removal?.path).toBe('/api/v1/notes/n1');
    expect(removal?.query.get('version')).toBe('0');
  });

  it('explains a refused or failed list and speaks Arabic', async () => {
    serve({ notes: problem(403) });
    const first = renderOperations(`/cases/${CASE}`);
    expect(await within(await section()).findByText('You are not permitted to view the notes of this case.')).toBeVisible();
    first.unmount();

    const user = userEvent.setup();
    let notes = problem(500);
    serve({
      get notes() {
        return notes;
      },
    });
    renderOperations(`/cases/${CASE}?lang=ar`);
    const region = await screen.findByRole('region', { name: 'الملاحظات القانونية' });
    expect(await within(region).findByText('تعذّر تحميل البيانات. تحقّق من الاتصال وأعد المحاولة.')).toBeVisible();
    notes = page([caseNote()]);
    await user.click(within(region).getByRole('button', { name: 'أعد المحاولة' }));
    expect(await within(region).findByRole('article')).toHaveTextContent('Mona Saleh');
  });
});
