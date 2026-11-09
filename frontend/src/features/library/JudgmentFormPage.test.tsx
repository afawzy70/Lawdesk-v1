import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { classification, jsonResponse, judgment, problem, renderLibrary, script } from './testSupport';

/** LD-052: adding and editing a judgment; only the court, the subject, the source and its verification are required. */
beforeEach(() => {
  vi.unstubAllGlobals();
});

const LISTS = [
  classification({ id: 'k1' }),
  classification({ id: 'k2', dimension: 'OUTCOME', code: 'REJECTED', labelAr: 'رفض الطلب', labelEn: null, origin: 'DOCUMENT' }),
  classification({ id: 'k3', active: false, code: 'OLD', labelAr: 'قديم', labelEn: null }),
];

function api(overrides: (call: { method: string; path: string }) => Response | undefined = () => undefined) {
  return script((call) => {
    const special = overrides(call);
    if (special) return special;
    if (call.path === '/api/v1/library/classifications') return jsonResponse(LISTS);
    if (call.method === 'GET' && call.path === '/api/v1/library/judgments/j1') return jsonResponse(judgment({ classifications: [LISTS[2] ?? classification()] }));
    if (call.method === 'POST' && call.path === '/api/v1/library/judgments') return jsonResponse(judgment({ id: 'j9' }), 201);
    if (call.method === 'PUT') return jsonResponse(judgment());
    if (call.path === '/api/v1/library/judgments/j9' || call.path === '/api/v1/library/judgments/j1') return jsonResponse(judgment());
    return undefined;
  });
}

async function fillRequired(typing: ReturnType<typeof userEvent.setup>) {
  await typing.type(screen.getByLabelText('Court'), 'محكمة التمييز');
  await typing.type(screen.getByLabelText('Subject of the dispute'), 'تعويض');
  await typing.type(screen.getByLabelText('Source of the judgment'), 'مجموعة الأحكام');
  await typing.type(screen.getByLabelText('How it was verified'), 'قوبلت بالنسخة الرسمية');
}

describe('judgment form', () => {
  it('requires the court, subject, source and verification, then registers and opens the new judgment', async () => {
    const { calls } = api();
    const typing = userEvent.setup();
    renderLibrary('/library/new');
    await screen.findByText('Administrative liability');
    await typing.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Fill in the court');
    expect(calls.some((c) => c.method === 'POST')).toBe(false);

    await fillRequired(typing);
    await typing.type(screen.getByLabelText('Case number'), '55');
    await typing.type(screen.getByLabelText('Case year'), '2019');
    await typing.selectOptions(screen.getByLabelText('Court level'), 'CASSATION');
    await typing.selectOptions(screen.getByLabelText('Type of dispute'), 'LABOR');
    await typing.selectOptions(screen.getByLabelText('Direction of the judgment'), 'FOR_AUTHORITY');
    await typing.selectOptions(screen.getByLabelText('Final or not'), 'FINAL');
    await typing.selectOptions(screen.getByLabelText('Confidentiality'), 'RESTRICTED');
    await typing.type(screen.getByLabelText('Keywords (one per line)'), 'تعويض{Enter}  {Enter}فصل');
    await typing.click(screen.getByLabelText('Administrative liability'));
    await typing.click(screen.getByLabelText('رفض الطلب'));
    await typing.click(screen.getByLabelText('رفض الطلب'));
    await typing.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => {
      expect(screen.getByTestId('where')).toHaveTextContent('/library/j9?lang=en');
    });
    const sent = calls.find((c) => c.method === 'POST')?.body;
    expect(sent).toMatchObject({
      courtName: 'محكمة التمييز',
      courtLevel: 'CASSATION',
      caseNumber: '55',
      caseYear: 2019,
      caseType: 'LABOR',
      direction: 'FOR_AUTHORITY',
      finality: 'FINAL',
      confidentiality: 'RESTRICTED',
      keywords: ['تعويض', 'فصل'],
      classificationIds: ['k1'],
      chamber: null,
      summary: null,
      judgmentDate: null,
    });
    // An inactive value is not offered for new judgments.
    expect(screen.queryByLabelText('قديم')).not.toBeInTheDocument();
  }, 30_000);

  it('refuses a future date without calling the server', async () => {
    const { calls } = api();
    const typing = userEvent.setup();
    renderLibrary('/library/new');
    await screen.findByText('Administrative liability');
    await fillRequired(typing);
    await typing.type(screen.getByLabelText('Date of judgment'), '2999-01-01');
    await typing.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('cannot be in the future');
    expect(calls.some((c) => c.method === 'POST')).toBe(false);
  });

  it('offers to confirm a suspected duplicate and then saves it as another judgment', async () => {
    let duplicate = true;
    const { calls } = api((call) => {
      if (call.method === 'POST') {
        const refused = duplicate;
        duplicate = false;
        return refused ? problem(422, { rule: 'LIBRARY_DUPLICATE_SUSPECTED' }) : undefined;
      }
      return undefined;
    });
    const typing = userEvent.setup();
    renderLibrary('/library/new');
    await screen.findByText('Administrative liability');
    await fillRequired(typing);
    await typing.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('is already in the library');
    await typing.click(screen.getByRole('button', { name: 'Save as another judgment' }));
    await waitFor(() => {
      expect(screen.getByTestId('where')).toHaveTextContent('/library/j9');
    });
    expect(calls.filter((c) => c.method === 'POST').at(-1)?.body).toMatchObject({ allowDuplicate: true });
  });

  it('explains an unavailable classification, a server date refusal and a plain failure', async () => {
    let rule: string | null = 'LIBRARY_CLASSIFICATION_UNKNOWN';
    api((call) => {
      if (call.method !== 'POST') return undefined;
      return rule === null ? problem(500) : problem(422, { rule });
    });
    const typing = userEvent.setup();
    renderLibrary('/library/new');
    await screen.findByText('Administrative liability');
    await fillRequired(typing);
    await typing.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('no longer available');
    rule = 'LIBRARY_DATE_IN_FUTURE';
    await typing.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('cannot be in the future');
    });
    rule = null;
    await typing.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('could not be saved');
    });
  });

  it('loads a judgment for editing, keeps an inactive value it already has, and saves with the version', async () => {
    const { calls } = api();
    const typing = userEvent.setup();
    renderLibrary('/library/j1/edit');
    expect(await screen.findByDisplayValue('تعويض عن قرار إداري معيب')).toBeInTheDocument();
    expect(screen.getByLabelText('قديم')).toBeChecked();
    expect(screen.getByLabelText('Keywords (one per line)')).toHaveValue('تعويض\nقرار إداري');
    await typing.clear(screen.getByLabelText('Subject of the dispute'));
    await typing.type(screen.getByLabelText('Subject of the dispute'), 'موضوع معدل');
    await typing.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() => {
      expect(screen.getByTestId('where')).toHaveTextContent('/library/j1?lang=en');
    });
    expect(calls.find((c) => c.method === 'PUT')?.body).toMatchObject({ subject: 'موضوع معدل', version: 3 });
  });

  it('edits a judgment of which nothing but the required facts is known', async () => {
    const { calls } = api((call) =>
      call.method === 'GET' && call.path === '/api/v1/library/judgments/j1'
        ? jsonResponse(
            judgment({
              courtLevel: null,
              chamber: null,
              caseNumber: null,
              caseYear: null,
              judgmentDate: null,
              caseType: null,
              partiesDescription: null,
              summary: null,
              operativeText: null,
              reasons: null,
              legalTexts: null,
              requestsAddressed: null,
              requestResults: null,
              direction: null,
              copyReference: null,
              keywords: [],
              classifications: [],
              finality: 'NOT_SPECIFIED',
            }),
          )
        : undefined,
    );
    const typing = userEvent.setup();
    renderLibrary('/library/j1/edit');
    expect(await screen.findByDisplayValue('تعويض عن قرار إداري معيب')).toBeInTheDocument();
    expect(screen.getByLabelText('Case year')).toHaveValue(null);
    await typing.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() => {
      expect(calls.some((c) => c.method === 'PUT')).toBe(true);
    });
    expect(calls.find((c) => c.method === 'PUT')?.body).toMatchObject({
      courtLevel: null,
      caseYear: null,
      judgmentDate: null,
      keywords: [],
      classificationIds: [],
      direction: null,
      finality: 'NOT_SPECIFIED',
    });
  });

  it('tells a conflict on edit and a failed load', async () => {
    api((call) => (call.method === 'PUT' ? problem(409) : undefined));
    const typing = userEvent.setup();
    const first = renderLibrary('/library/j1/edit');
    await screen.findByDisplayValue('تعويض عن قرار إداري معيب');
    await typing.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Someone changed this record meanwhile');
    first.unmount();

    script((call) => (call.path === '/api/v1/library/classifications' ? jsonResponse([]) : problem(500)));
    renderLibrary('/library/j1/edit');
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be loaded');
  });

  it('survives a failed classification list', async () => {
    script((call) => (call.path === '/api/v1/library/classifications' ? problem(500) : undefined));
    renderLibrary('/library/new');
    expect((await screen.findAllByText('No values in this list yet.')).length).toBeGreaterThan(0);
  });
});
