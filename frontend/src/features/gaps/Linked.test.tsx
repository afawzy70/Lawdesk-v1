import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { jsonResponse } from '../../test/http';
import { mount, problem, serve } from '../followup/testSupport';
import { DefensePrecedents } from './DefensePrecedents';
import { LibraryCopySection } from './LibraryCopySection';

/** The library copy and the defence precedents (synthetic data). */

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token';
});

const COPY = { id: 'f1', fileName: 'ruling.pdf', mediaType: 'application/pdf', sizeBytes: 120, sha256: 'a', uploadedAt: '2026-11-01T00:00:00Z' };

describe('the stored copy of a library judgment', () => {
  it('shows the copy, replaces it and takes it out; a refusal is shown', async () => {
    let refuse = false;
    const { requests } = serve((r) => {
      if (r.path.endsWith('/copy/info')) return jsonResponse(COPY);
      if (r.path.endsWith('/copy') && (r.method === 'POST' || r.method === 'DELETE')) return refuse ? problem(400) : r.method === 'POST' ? jsonResponse(COPY, 201) : new Response(null, { status: 204 });
      return undefined;
    });
    mount(<LibraryCopySection judgmentId="j1" canManage />, ['LIBRARY_MANAGE']);
    expect(await screen.findByRole('link', { name: 'ruling.pdf' })).toHaveAttribute('href', '/api/v1/library/judgments/j1/copy');
    const file = new File(['%PDF-1.4'], 'new.pdf', { type: 'application/pdf' });
    await userEvent.upload(screen.getByLabelText('Replace the copy'), file);
    await waitFor(() => { expect(requests.some((r) => r.method === 'POST' && r.form !== null)).toBe(true); });
    await userEvent.click(screen.getByRole('button', { name: 'Take the copy out' }));
    await waitFor(() => { expect(requests.some((r) => r.method === 'DELETE')).toBe(true); });
    refuse = true;
    await userEvent.click(screen.getByRole('button', { name: 'Take the copy out' }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    refuse = true;
    await userEvent.upload(screen.getByLabelText('Replace the copy'), file);
    await waitFor(() => { expect(requests.filter((r) => r.method === 'POST').length).toBe(2); });
  });

  it('says there is no copy, and lets a reader only look', async () => {
    serve(() => problem(404));
    mount(<LibraryCopySection judgmentId="j1" canManage={false} />, ['LIBRARY_VIEW']);
    expect(await screen.findByText('No copy is stored in the archive.')).toBeInTheDocument();
    expect(screen.queryByLabelText('Store a copy')).not.toBeInTheDocument();
  });

  it('stores a first copy', async () => {
    const { requests } = serve((r) => (r.method === 'POST' ? jsonResponse(COPY, 201) : problem(404)));
    mount(<LibraryCopySection judgmentId="j1" canManage />, ['LIBRARY_MANAGE']);
    await userEvent.upload(await screen.findByLabelText('Store a copy'), new File(['%PDF-1.4'], 'a.pdf', { type: 'application/pdf' }));
    await waitFor(() => { expect(requests.some((r) => r.method === 'POST')).toBe(true); });
  });
});

const LINK = { id: 'l1', judgmentId: 'j1', courtName: 'SYNTH-DOC court', caseNumber: '5', caseYear: 2026, subject: 'SYNTH-DOC subject', note: null };

describe('the precedents of a defence', () => {
  it('lists them, searches the library, links and removes; failures are shown', async () => {
    let fail = false;
    const { requests } = serve((r) => {
      if (r.path === '/api/v1/defenses/d1/precedents' && r.method === 'GET') return jsonResponse([LINK, { ...LINK, id: 'l2', caseNumber: null, caseYear: null }]);
      if (r.path === '/api/v1/library/judgments') return fail ? problem(500) : jsonResponse({ items: [{ id: 'j9', courtName: 'C', caseNumber: null, caseYear: null, subject: 'Found' }] });
      if (r.path.startsWith('/api/v1/defenses/d1/precedents')) return fail ? problem(409) : r.method === 'POST' ? jsonResponse(LINK, 201) : new Response(null, { status: 204 });
      return undefined;
    });
    mount(<DefensePrecedents defenseId="d1" canManage />, ['DEFENSE_MANAGE']);
    expect(await screen.findByText(/SYNTH-DOC court 5\/2026 - SYNTH-DOC subject/)).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Find a judgment in the library'), 'x');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Link' }));
    await waitFor(() => { expect(requests.find((r) => r.method === 'POST')?.body).toEqual({ judgmentId: 'j9', note: null }); });
    await userEvent.click((await screen.findAllByRole('button', { name: 'Remove' }))[0] as HTMLElement);
    await waitFor(() => { expect(requests.some((r) => r.method === 'DELETE')).toBe(true); });
    fail = true;
    await userEvent.click((await screen.findAllByRole('button', { name: 'Remove' }))[0] as HTMLElement);
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));
    await waitFor(() => { expect(requests.filter((r) => r.path === '/api/v1/library/judgments').length).toBe(2); });
  });

  it('shows none for a reader and a failure of linking', async () => {
    serve(() => problem(404));
    const a = mount(<DefensePrecedents defenseId="d1" canManage={false} />, ['DEFENSE_VIEW']);
    expect(await screen.findByText('No supporting judgment linked.')).toBeInTheDocument();
    expect(screen.queryByRole('search')).not.toBeInTheDocument();
    a.unmount();
    serve((r) => (r.path === '/api/v1/library/judgments' ? jsonResponse({ items: [{ id: 'j9', courtName: 'C', caseNumber: null, caseYear: null, subject: 'Found' }] }) : problem(409)));
    mount(<DefensePrecedents defenseId="d1" canManage />, ['DEFENSE_MANAGE']);
    await userEvent.click(await screen.findByRole('button', { name: 'Search' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Link' }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });
});
