import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { jsonResponse } from '../../test/http';
import { mount, problem, serve } from '../followup/testSupport';
import { ArchiveSettingsPage } from './ArchiveSettingsPage';
import { DefenseWordingPage } from './DefenseWordingPage';
import { JudgmentsPage } from './JudgmentsPage';
import { ResponsivenessPage } from './ResponsivenessPage';

/** The screens that close the gaps of the requirements matrix. Synthetic data only. */

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token';
});

const ROW = {
  id: 'j1', caseId: 'c0000000-0000-4000-8000-000000000001', caseReferenceNo: 'LD-2026-00001', caseTitle: 'SYNTH-DOC case',
  stage: 'FIRST_INSTANCE', courtName: 'SYNTH-DOC court', courtCaseNumber: '12', courtCaseYear: 2026, judgmentDate: '2026-02-01',
  outcome: 'PARTIAL', finalJudgment: true, appealDeadlineId: 'd1', appealDueOn: '2026-03-05',
};

describe('the judgments list', () => {
  it('lists judgments with their appeal deadline, filters by outcome and pages', async () => {
    const { requests } = serve((r) => {
      if (r.path !== '/api/v1/judgments') return undefined;
      return jsonResponse({ items: r.query.get('outcome') === 'REJECTED' ? [] : [ROW, { ...ROW, id: 'j2', finalJudgment: false, appealDeadlineId: null, appealDueOn: null, courtCaseYear: null, courtCaseNumber: null }],
        page: Number(r.query.get('page')), totalPages: 2, totalItems: 2 });
    });
    mount(<JudgmentsPage />, ['JUDGMENT_VIEW']);
    expect((await screen.findAllByText('SYNTH-DOC case', { exact: false })).length).toBe(2);
    expect(screen.getAllByRole('link', { name: 'LD-2026-00001' })[0]).toHaveAttribute('href', expect.stringContaining('/cases/c0000000'));
    expect(screen.getByText('Not specified')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    await waitFor(() => { expect(requests.some((r) => r.query.get('page') === '1')).toBe(true); });
    await userEvent.click(await screen.findByRole('button', { name: 'Previous' }));
    await userEvent.selectOptions(screen.getByLabelText('Outcome'), 'REJECTED');
    expect(await screen.findByText('No judgments in the cases you can see.')).toBeInTheDocument();
  });

  it('says plainly when the list cannot be read and when it is refused', async () => {
    serve(() => problem(500));
    const { unmount } = mount(<JudgmentsPage />, ['JUDGMENT_VIEW']);
    expect(await screen.findByRole('alert')).toHaveTextContent('The request failed');
    unmount();
    serve(() => problem(403));
    mount(<JudgmentsPage />, ['JUDGMENT_VIEW']);
    await waitFor(() => { expect(screen.queryByText('Loading…')).not.toBeInTheDocument(); });
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });
});

const SETTINGS = {
  limits: { maxFileBytes: 1000, maxDocumentsPerCase: 100, maxVersionsPerDocument: 20, maxOcrPages: 50, maxTextChars: 2000 },
  formats: [{ code: 'PDF', family: 'PDF', mediaType: 'application/pdf', extension: 'pdf', enabled: true }, { code: 'PNG', family: 'IMAGE', mediaType: 'image/png', extension: 'png', enabled: false }],
  ocr: { available: false, engine: null, languages: null, detail: null },
};

describe('the archive settings', () => {
  it('lets an archive administrator change a limit and a format, and reports a refused value', async () => {
    let fail = false;
    const { requests } = serve((r) => {
      if (r.path !== '/api/v1/archive/settings') return undefined;
      if (r.method === 'PUT') return fail ? problem(400) : jsonResponse(SETTINGS);
      return jsonResponse(SETTINGS);
    });
    mount(<ArchiveSettingsPage />, ['DOCUMENT_ARCHIVE_ADMIN']);
    const field = await screen.findByLabelText('Documents per case');
    expect(screen.getByText(/Text recognition is not available/)).toBeInTheDocument();
    await userEvent.clear(field);
    await userEvent.type(field, '150');
    await userEvent.click(screen.getByRole('checkbox', { name: /PNG/ }));
    await userEvent.click(screen.getByRole('checkbox', { name: /PDF/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByRole('status')).toHaveTextContent('Saved.');
    const put = requests.find((r) => r.method === 'PUT');
    expect(put?.body).toEqual({ limits: { ...SETTINGS.limits, maxDocumentsPerCase: 150 }, enabledFormats: ['PNG'] });
    fail = true;
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('outside the allowed range');
    fail = false;
    await userEvent.click(screen.getByRole('checkbox', { name: /PDF/ }));
    expect(screen.getByRole('checkbox', { name: /PDF/ })).toBeChecked();
  });

  it('is read-only without the administration right, shows recognition when present, and fails visibly', async () => {
    serve(() => jsonResponse({ ...SETTINGS, ocr: { available: true, engine: 'x', languages: 'ara', detail: null } }));
    const { unmount } = mount(<ArchiveSettingsPage />, ['DOCUMENT_VIEW']);
    expect(await screen.findByText(/You can read these settings/)).toBeInTheDocument();
    expect(screen.getByText('Text recognition is available on this server.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
    unmount();
    serve(() => problem(500));
    mount(<ArchiveSettingsPage />, ['DOCUMENT_VIEW']);
    expect(await screen.findByText('The request failed. Try again.')).toBeInTheDocument();
  });

  it('is refused for a user who may not read the archive', async () => {
    serve(() => problem(403));
    mount(<ArchiveSettingsPage />, []);
    await waitFor(() => { expect(screen.queryByText('Loading…')).not.toBeInTheDocument(); });
    expect(screen.queryByRole('form')).not.toBeInTheDocument();
  });
});

const WORDING = { id: 't1', title: 'SYNTH-DOC prescription', kind: 'SUBSTANTIVE', text: 'The claim is time-barred', legalBasis: 'SYNTH-DOC basis', status: 'DRAFT', version: 0 };

describe('the defence wording library', () => {
  it('lists wording, searches, adds and edits it for a manager', { timeout: 20000 }, async () => {
    let saveFails = false;
    const { requests } = serve((r) => {
      if (r.path === '/api/v1/defense-templates' && r.method === 'GET') return jsonResponse(r.query.get('q') === 'none' ? [] : [WORDING, { ...WORDING, id: 't2', title: 'SYNTH-DOC second', text: null, legalBasis: null }]);
      if (r.path.startsWith('/api/v1/defense-templates')) return saveFails ? problem(409) : jsonResponse(WORDING, 201);
      return undefined;
    });
    mount(<DefenseWordingPage />, ['DEFENSE_VIEW', 'DEFENSE_MANAGE']);
    expect(await screen.findByText('The claim is time-barred')).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Title'), 'SYNTH-DOC new');
    await userEvent.selectOptions(screen.getByLabelText('Kind'), 'FORMAL');
    await userEvent.type(screen.getByLabelText('Wording'), 'text');
    await userEvent.type(screen.getByLabelText('Legal basis'), 'basis');
    await userEvent.selectOptions(screen.getByLabelText('Review status'), 'REVIEWED');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() => { expect(requests.some((r) => r.method === 'POST')).toBe(true); });
    expect(requests.find((r) => r.method === 'POST')?.body).toMatchObject({ title: 'SYNTH-DOC new', kind: 'FORMAL', status: 'REVIEWED', text: 'text', legalBasis: 'basis' });
    await userEvent.click((await screen.findAllByRole('button', { name: 'Edit' }))[0] as HTMLElement);
    expect(screen.getByRole('heading', { name: 'Edit wording' })).toBeInTheDocument();
    await userEvent.clear(screen.getByLabelText('Wording'));
    await userEvent.clear(screen.getByLabelText('Legal basis'));
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() => { expect(requests.some((r) => r.method === 'PUT')).toBe(true); });
    expect(requests.find((r) => r.method === 'PUT')?.body).toMatchObject({ text: null, legalBasis: null, version: 0 });
    await userEvent.click((await screen.findAllByRole('button', { name: 'Edit' }))[0] as HTMLElement);
    saveFails = true;
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('The request failed');
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    await userEvent.type(screen.getByLabelText('Search wording'), 'none');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));
    expect(await screen.findByText('No wording saved yet.')).toBeInTheDocument();
  });

  it('is read-only without the manage right and reports refusal and failure', async () => {
    serve(() => jsonResponse([WORDING]));
    const { unmount } = mount(<DefenseWordingPage />, ['DEFENSE_VIEW']);
    expect(await screen.findByText('SYNTH-DOC prescription')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument();
    unmount();
    serve(() => problem(500));
    const failed = mount(<DefenseWordingPage />, ['DEFENSE_VIEW']);
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    failed.unmount();
    serve(() => problem(403));
    mount(<DefenseWordingPage />, ['DEFENSE_VIEW']);
    await waitFor(() => { expect(screen.queryByRole('search')).not.toBeInTheDocument(); });
  });
});

describe('the responsiveness report', () => {
  it('shows each administration with its counts and average days', async () => {
    serve(() => jsonResponse({ units: [
      { unitId: 'u1', unitName: 'SYNTH-DOC unit', requests: 4, replies: 3, answeredRequests: 2, overdue: 1, incompleteReplies: 1, affectedCases: 2, averageReplyDays: 3.25 },
      { unitId: null, unitName: null, requests: 1, replies: 0, answeredRequests: 0, overdue: 0, incompleteReplies: 0, affectedCases: 1, averageReplyDays: null },
    ] }));
    mount(<ResponsivenessPage />, ['INFO_REQUEST_VIEW']);
    expect(await screen.findByText('SYNTH-DOC unit')).toBeInTheDocument();
    expect(screen.getByText('3.3')).toBeInTheDocument();
    expect(screen.getAllByText('Not specified')).toHaveLength(2);
  });

  it('says so when there are no requests, when it fails and when it is refused', async () => {
    serve(() => jsonResponse({ units: [] }));
    const a = mount(<ResponsivenessPage />, ['INFO_REQUEST_VIEW']);
    expect(await screen.findByText('No information requests yet.')).toBeInTheDocument();
    a.unmount();
    serve(() => problem(500));
    const b = mount(<ResponsivenessPage />, ['INFO_REQUEST_VIEW']);
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    b.unmount();
    serve(() => problem(403));
    mount(<ResponsivenessPage />, []);
    await waitFor(() => { expect(screen.queryByRole('table')).not.toBeInTheDocument(); });
  });
});

