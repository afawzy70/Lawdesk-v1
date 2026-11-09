import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { jsonResponse } from '../../test/http';
import { CaseLinksSection } from './CaseLinksSection';
import { CASE, link, mount, page, problem, serve, type Request } from './testSupport';

/** LD-053, requirements section 8 (S10): linked cases on the case page. */

const LINKS = `/api/v1/cases/${CASE}/links`;
const OTHER = { id: 'c0000000-0000-4000-8000-000000000002', referenceNo: 'LD-2026-00002', title: 'Second case', caseType: 'CIVIL', status: 'OPEN', openedOn: '2026-01-01', updatedAt: '2026-01-01T00:00:00Z' };

function backend(links: unknown, onWrite?: (r: Request) => Response | undefined) {
  return serve((r) => {
    if (r.method === 'GET' && r.path === LINKS) return links instanceof Response ? links : jsonResponse(links);
    if (r.method === 'GET' && r.path === '/api/v1/cases') return page([OTHER]);
    return onWrite?.(r);
  });
}

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token';
});

describe('linked cases', () => {
  it('shows each link with its kind, status, direction, decision and a link to the other case', async () => {
    backend({
      items: [
        link({ courtDecision: true, decisionDate: '2026-11-03', operative: 'Joined', requestedBy: 'The plaintiff', requestedOn: '2026-11-01', kind: 'JOINDER_DECISION' }),
        link({ id: 'l2', direction: 'INCOMING', otherCaseId: 'x', otherCaseReferenceNo: 'LD-2026-00003', kind: 'REFERRAL', status: 'ENDED' }),
      ],
      hiddenCount: 2,
    });
    mount(<CaseLinksSection caseId={CASE} editable={false} />);
    const items = await screen.findAllByRole('listitem');
    expect(items[0]).toHaveTextContent('Decision to join');
    expect(items[0]).toHaveTextContent('LD-2026-00002 Second case');
    expect(items[0]).toHaveTextContent('recorded from this case');
    expect(items[0]).toHaveTextContent('Court decision');
    expect(items[0]).toHaveTextContent('Joined');
    expect(items[0]).toHaveTextContent('Requested by The plaintiff');
    expect(within(items[0] as HTMLElement).getByRole('link')).toHaveAttribute('href', expect.stringContaining('/cases/c0000000-0000-4000-8000-000000000002'));
    expect(items[1]).toHaveTextContent('Referral');
    expect(items[1]).toHaveTextContent('recorded from the other case');
    expect(items[2]).toHaveTextContent('2 more link(s) to cases you may not see.');
    expect(screen.queryByRole('form')).not.toBeInTheDocument();
  });

  it('says there are no links, and hides the section from someone who may not open it', async () => {
    backend({ items: [], hiddenCount: 0 });
    const { unmount } = mount(<CaseLinksSection caseId={CASE} editable={false} />);
    expect(await screen.findByText('No linked cases.')).toBeInTheDocument();
    unmount();
    backend(problem(403));
    mount(<CaseLinksSection caseId={CASE} editable />);
    await waitFor(() => { expect(screen.queryByRole('heading', { name: 'Linked cases' })).not.toBeInTheDocument(); });
  });

  it('reports a failed load', async () => {
    backend(problem(500));
    mount(<CaseLinksSection caseId={CASE} editable={false} />);
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be loaded');
  });

  it('adds a link to a case chosen by search, with the court decision when there is one', async () => {
    const user = userEvent.setup();
    let links: unknown = { items: [], hiddenCount: 0 };
    const { requests } = serve((r) => {
      if (r.method === 'GET' && r.path === LINKS) return jsonResponse(links);
      if (r.method === 'GET' && r.path === '/api/v1/cases') return page([OTHER]);
      if (r.method === 'POST' && r.path === LINKS) {
        links = { items: [link()], hiddenCount: 0 };
        return jsonResponse(link(), 201);
      }
      return undefined;
    });
    mount(<CaseLinksSection caseId={CASE} editable />);
    const form = await screen.findByRole('form', { name: 'Add a link' });
    await user.selectOptions(await within(form).findByLabelText('Other case'), 'LD-2026-00002 — Second case');
    await user.selectOptions(within(form).getByLabelText('Kind of link'), 'Request to join');
    await user.type(within(form).getByLabelText('Reason'), ' Same facts ');
    await user.type(within(form).getByLabelText('Who asked for the link'), 'The plaintiff');
    await user.click(within(form).getByLabelText('A court decision was issued'));
    await user.type(within(form).getByLabelText('Date of the decision'), '2026-11-04');
    await user.type(within(form).getByLabelText('Operative part of the decision'), 'Joined');
    await user.click(within(form).getByRole('button', { name: 'Add a link' }));
    expect(await screen.findByText('The link was recorded.')).toBeInTheDocument();
    const post = requests.find((r) => r.method === 'POST');
    expect(post?.body).toEqual({
      otherCaseId: OTHER.id,
      kind: 'JOINDER_REQUEST',
      reason: 'Same facts',
      requestedBy: 'The plaintiff',
      requestedOn: null,
      courtDecision: true,
      decisionDate: '2026-11-04',
      operative: 'Joined',
    });
    expect(await screen.findByText('Same decision challenged')).toBeInTheDocument();
  });

  it('asks for the other case and the reason before sending, and explains a refusal', async () => {
    const user = userEvent.setup();
    const { requests } = backend({ items: [], hiddenCount: 0 }, (r) =>
      r.method === 'POST' ? problem(409) : undefined,
    );
    mount(<CaseLinksSection caseId={CASE} editable />);
    const form = await screen.findByRole('form', { name: 'Add a link' });
    await user.click(within(form).getByRole('button', { name: 'Add a link' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Choose the other case and give the reason.');
    expect(requests.some((r) => r.method === 'POST')).toBe(false);
    await user.selectOptions(await within(form).findByLabelText('Other case'), 'LD-2026-00002 — Second case');
    await user.type(within(form).getByLabelText('Reason'), 'x');
    await user.click(within(form).getByRole('button', { name: 'Add a link' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Someone else changed this record');
  });

  it('changes the status of a link, or explains why it could not', async () => {
    const user = userEvent.setup();
    let fail = false;
    const { requests } = backend({ items: [link()], hiddenCount: 0 }, (r) => {
      if (r.method !== 'PUT') return undefined;
      return fail ? problem(403) : jsonResponse(link({ status: 'ENDED', version: 1 }));
    });
    mount(<CaseLinksSection caseId={CASE} editable />);
    await user.selectOptions(await screen.findByLabelText('Status of the link'), 'Ended');
    expect(await screen.findByText('The link was updated.')).toBeInTheDocument();
    expect(requests.find((r) => r.method === 'PUT')?.body).toMatchObject({ status: 'ENDED', version: 0, reason: 'Same decision challenged' });
    fail = true;
    await user.selectOptions(await screen.findByLabelText('Status of the link'), 'Rejected');
    expect(await screen.findByRole('alert')).toHaveTextContent('You do not have the right');
  });
});
