import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { jsonResponse } from '../../test/http';
import type { InfoRequest } from './api';
import { InfoRequestsSection } from './InfoRequestsSection';
import { CASE, infoRequest, mount, page, problem, serve, type Request } from './testSupport';

/** LD-053, requirements section 18 (S6): requests to the authority's units, their replies and follow-up. */

const LIST = `/api/v1/cases/${CASE}/info-requests`;
const MEMBERS = [{ id: 'm1', displayName: 'Hala Supervisor' }];

function backend(requests: InfoRequest[] | Response, write?: (r: Request) => Response | undefined) {
  return serve((r) => {
    if (r.method === 'GET' && r.path === LIST) return requests instanceof Response ? requests : page(requests);
    if (r.method === 'GET' && r.path === '/api/v1/org-units') return jsonResponse([{ id: 'unit-1', tree: 'AUTHORITY', parentId: null, level: 'SECTOR', nameAr: 'إدارة', nameEn: 'Administration', active: true, version: 0 }]);
    if (r.method === 'GET' && r.path === '/api/v1/cases/members') return jsonResponse(MEMBERS);
    return write?.(r);
  });
}

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token';
});

describe('information requests', () => {
  it('shows a request with its state, dates, replies, follow-up and an overdue mark', async () => {
    backend([
      infoRequest({
        status: 'PARTIAL', overdue: true, dueOn: '2026-11-01', sentOn: '2026-10-20', letterNo: 'L-9', responsibleMemberName: 'Mona',
        details: 'The file', requiredDocuments: 'The decision', delayReason: 'The unit is busy',
        replies: [{ id: 'p1', requestId: 'r1', receivedOn: '2026-11-02', completeness: 'PARTIAL', summary: 'First part', documentId: 'd1', documentTitle: 'reply.pdf', createdByName: 'Mona' }],
        followUps: [{ id: 'f1', requestId: 'r1', kind: 'ESCALATION', onDate: '2026-11-03', note: 'Late', escalatedTo: 'm1', escalatedToName: 'Hala', byName: 'Mona' }],
      }),
      infoRequest({ id: 'r2', requestNo: 'IR-2026-00002', status: 'CLOSED', closeReason: 'Withdrawn', unitName: null }),
    ]);
    mount(<InfoRequestsSection caseId={CASE} editable={false} canEscalate={false} />);
    await screen.findByText('IR-2026-00001');
    const items = Array.from(document.querySelectorAll('li.wp3-card'));
    expect(items[0]).toHaveTextContent('IR-2026-00001');
    expect(items[0]).toHaveTextContent('Partial reply received');
    expect(items[0]).toHaveTextContent('Overdue');
    expect(items[0]).toHaveTextContent('reply due 01/11/2026');
    expect(items[0]).toHaveTextContent('sent 20/10/2026');
    expect(items[0]).toHaveTextContent('letter L-9');
    expect(items[0]).toHaveTextContent('Documents required: The decision');
    expect(items[0]).toHaveTextContent('Reason for the delay: The unit is busy');
    expect(items[0]).toHaveTextContent('First part');
    expect(items[0]).toHaveTextContent('archived: reply.pdf');
    expect(items[0]).toHaveTextContent('Escalation');
    expect(items[0]).toHaveTextContent('→ Hala');
    expect(items[1]).toHaveTextContent('Reason for closing: Withdrawn');
    expect(items[1]).toHaveTextContent('Not specified');
    expect(screen.queryByRole('form')).not.toBeInTheDocument();
  });

  it('says there are none, reports a failure, and is left out for someone who may not open it', async () => {
    backend([]);
    const first = mount(<InfoRequestsSection caseId={CASE} editable={false} canEscalate={false} />);
    expect(await screen.findByText('No requests for this case.')).toBeInTheDocument();
    first.unmount();
    backend(problem(500));
    const second = mount(<InfoRequestsSection caseId={CASE} editable={false} canEscalate={false} />);
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be loaded');
    second.unmount();
    backend(problem(403));
    mount(<InfoRequestsSection caseId={CASE} editable canEscalate />);
    await waitFor(() => { expect(screen.queryByRole('heading', { name: /Information requests/ })).not.toBeInTheDocument(); });
  });

  it('creates a draft request with the chosen unit, and asks for a subject first', async () => {
    const user = userEvent.setup();
    const { requests } = backend([], (r) => (r.method === 'POST' ? jsonResponse(infoRequest(), 201) : undefined));
    mount(<InfoRequestsSection caseId={CASE} editable canEscalate={false} />);
    const form = await screen.findByRole('form', { name: 'New request' });
    await user.click(within(form).getByRole('button', { name: 'New request' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Give the subject of the request.');
    await user.type(within(form).getByLabelText('Subject of the request'), ' Send the file ');
    await user.selectOptions(await within(form).findByRole('combobox', { name: 'Unit asked to reply' }), 'Administration');
    await user.type(within(form).getByLabelText('Information required'), 'All of it');
    await user.type(within(form).getByLabelText('Documents required'), 'The decision');
    await user.type(within(form).getByLabelText('Date to reply by'), '2026-12-01');
    await user.type(within(form).getByLabelText('Person addressed'), 'Mr. Ali');
    await user.click(within(form).getByRole('button', { name: 'New request' }));
    expect(await screen.findByRole('status')).toHaveTextContent('recorded as a draft');
    expect(requests.find((r) => r.method === 'POST')?.body).toMatchObject({
      subject: 'Send the file', unitId: 'unit-1', details: 'All of it', requiredDocuments: 'The decision', dueOn: '2026-12-01', addressee: 'Mr. Ali',
    });
  });

  it('explains a refused creation', async () => {
    const user = userEvent.setup();
    backend([], (r) => (r.method === 'POST' ? problem(422, { rule: 'CASE_ARCHIVED' }) : undefined));
    mount(<InfoRequestsSection caseId={CASE} editable canEscalate={false} />);
    const form = await screen.findByRole('form', { name: 'New request' });
    await user.type(within(form).getByLabelText('Subject of the request'), 'x');
    await user.click(within(form).getByRole('button', { name: 'New request' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('An archived case is read-only.');
  });

  it('moves a draft through approval and sending, with the version it read', async () => {
    const user = userEvent.setup();
    const { requests } = backend([infoRequest()], (r) => (r.method === 'POST' ? jsonResponse(infoRequest({ status: 'PENDING_APPROVAL', version: 1 })) : undefined));
    mount(<InfoRequestsSection caseId={CASE} editable canEscalate={false} />);
    await user.click(await screen.findByRole('button', { name: 'Submit for approval' }));
    expect(await screen.findByRole('status')).toHaveTextContent('The state of the request was changed.');
    expect(requests.find((r) => r.method === 'POST')).toMatchObject({ path: '/api/v1/info-requests/r1/status', body: { status: 'PENDING_APPROVAL', version: 0 } });
  });

  it('offers only the moves the flow allows', async () => {
    backend([infoRequest({ status: 'SENT' }), infoRequest({ id: 'r2', status: 'COMPLETE' }), infoRequest({ id: 'r3', status: 'CLOSED' })]);
    mount(<InfoRequestsSection caseId={CASE} editable canEscalate={false} />);
    const items = await screen.findAllByRole('listitem');
    expect(within(items[0] as HTMLElement).getByRole('button', { name: 'Mark as awaiting reply' })).toBeInTheDocument();
    expect(within(items[0] as HTMLElement).getByRole('button', { name: 'Record a reply' })).toBeInTheDocument();
    expect(within(items[1] as HTMLElement).queryByRole('button', { name: 'Mark as awaiting reply' })).not.toBeInTheDocument();
    expect(within(items[2] as HTMLElement).getByRole('button', { name: 'Mark as awaiting reply' })).toBeInTheDocument();
    expect(within(items[2] as HTMLElement).queryByRole('button', { name: 'Close the request' })).not.toBeInTheDocument();
    expect(within(items[2] as HTMLElement).queryByRole('button', { name: 'Record a reply' })).not.toBeInTheDocument();
  });

  it('records a reply with its document as a multipart upload', async () => {
    const user = userEvent.setup();
    const { requests } = backend([infoRequest({ status: 'AWAITING' })], (r) => (r.method === 'POST' ? jsonResponse({ id: 'p1' }, 201) : undefined));
    mount(<InfoRequestsSection caseId={CASE} editable canEscalate={false} />);
    await user.click(await screen.findByRole('button', { name: 'Record a reply' }));
    const form = screen.getByRole('form', { name: 'Record a reply' });
    await user.selectOptions(within(form).getByLabelText('How complete the reply is'), 'Complete');
    await user.type(within(form).getByLabelText('Summary of the reply'), 'All attached');
    const file = new File(['%PDF-1.4'], 'reply.pdf', { type: 'application/pdf' });
    await user.upload(within(form).getByLabelText('Reply document (PDF)'), file);
    await user.click(within(form).getByRole('button', { name: 'Save the reply' }));
    expect(await screen.findByRole('status')).toHaveTextContent('The reply was recorded.');
    const post = requests.find((r) => r.method === 'POST');
    expect(post?.path).toBe('/api/v1/info-requests/r1/replies');
    expect(post?.form?.get('completeness')).toBe('COMPLETE');
    expect(post?.form?.get('summary')).toBe('All attached');
    expect((post?.form?.get('file') as File).name).toBe('reply.pdf');
  });

  it('records a reply without a document as JSON, and explains a refusal', async () => {
    const user = userEvent.setup();
    let refuse = true;
    const { requests } = backend([infoRequest({ status: 'SENT' })], (r) =>
      r.method === 'POST' ? (refuse ? problem(422, { rule: 'INFO_REQUEST_CLOSED' }) : jsonResponse({ id: 'p1' }, 201)) : undefined,
    );
    mount(<InfoRequestsSection caseId={CASE} editable canEscalate={false} />);
    await user.click(await screen.findByRole('button', { name: 'Record a reply' }));
    const form = screen.getByRole('form', { name: 'Record a reply' });
    await user.click(within(form).getByRole('button', { name: 'Save the reply' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('The request is closed.');
    refuse = false;
    await user.click(within(screen.getByRole('form', { name: 'Record a reply' })).getByRole('button', { name: 'Save the reply' }));
    await waitFor(() => { expect(requests.filter((r) => r.method === 'POST')).toHaveLength(2); });
    expect(requests.filter((r) => r.method === 'POST')[1]?.body).toMatchObject({ completeness: 'PARTIAL', summary: null });
  });

  it('records a reminder, and an escalation only for someone who may escalate', async () => {
    const user = userEvent.setup();
    const { requests } = backend([infoRequest({ status: 'SENT' })], (r) => (r.method === 'POST' ? jsonResponse({ id: 'f1' }, 201) : undefined));
    const first = mount(<InfoRequestsSection caseId={CASE} editable canEscalate={false} />);
    await user.click(await screen.findByRole('button', { name: 'Record a follow-up' }));
    const kind = screen.getByLabelText('Kind of follow-up');
    expect(within(kind).queryByRole('option', { name: 'Escalation' })).not.toBeInTheDocument();
    await user.type(screen.getByLabelText('Note'), 'Phoned');
    await user.click(screen.getByRole('button', { name: 'Save the follow-up' }));
    expect(await screen.findByRole('status')).toHaveTextContent('The follow-up was recorded.');
    expect(requests.find((r) => r.method === 'POST')?.body).toEqual({ kind: 'REMINDER', note: 'Phoned', escalatedTo: null });
    first.unmount();

    mount(<InfoRequestsSection caseId={CASE} editable canEscalate />);
    await user.click(await screen.findByRole('button', { name: 'Record a follow-up' }));
    await user.selectOptions(screen.getByLabelText('Kind of follow-up'), 'Escalation');
    await user.selectOptions(await screen.findByRole('option', { name: 'Hala Supervisor' }).then((o) => o.parentElement as HTMLElement), 'Hala Supervisor');
    await user.click(screen.getByRole('button', { name: 'Save the follow-up' }));
    await waitFor(() => { expect(requests.filter((r) => r.method === 'POST')).toHaveLength(2); });
    expect(requests.filter((r) => r.method === 'POST')[1]?.body).toMatchObject({ kind: 'ESCALATION', escalatedTo: 'm1' });
  });

  it('closes a request with a reason and records why a reply is late', async () => {
    const user = userEvent.setup();
    const { requests } = backend([infoRequest({ status: 'OVERDUE', dueOn: '2026-10-01', delayReason: 'Busy' })], (r) => (r.method === 'POST' || r.method === 'PUT' ? jsonResponse(infoRequest()) : undefined));
    mount(<InfoRequestsSection caseId={CASE} editable canEscalate={false} />);
    await user.click(await screen.findByRole('button', { name: 'Reason for the delay' }));
    let form = screen.getByRole('form', { name: 'Reason for the delay' });
    expect(within(form).getByLabelText('Reason the reply is late')).toHaveValue('Busy');
    await user.clear(within(form).getByLabelText('Reason the reply is late'));
    await user.type(within(form).getByLabelText('Reason the reply is late'), 'The unit asked for time');
    fireEvent.change(within(form).getByLabelText('New date to reply by'), { target: { value: '2026-12-15' } });
    await user.click(within(form).getByRole('button', { name: 'Save' }));
    await waitFor(() => { expect(requests.some((r) => r.method === 'PUT')).toBe(true); });
    expect(requests.find((r) => r.method === 'PUT')?.body).toMatchObject({ delayReason: 'The unit asked for time', dueOn: '2026-12-15', version: 0 });

    await user.click(await screen.findByRole('button', { name: 'Close the request' }));
    form = screen.getByRole('form', { name: 'Close the request' });
    await user.type(within(form).getByLabelText('Why the request is closed'), 'No longer needed');
    await user.click(within(form).getByRole('button', { name: 'Close the request' }));
    await waitFor(() => { expect(requests.some((r) => r.method === 'POST')).toBe(true); });
    expect(requests.find((r) => r.method === 'POST')?.body).toMatchObject({ status: 'CLOSED', closeReason: 'No longer needed' });
  });

  it('toggles a panel closed when its button is pressed again', async () => {
    const user = userEvent.setup();
    backend([infoRequest({ status: 'SENT' })]);
    mount(<InfoRequestsSection caseId={CASE} editable canEscalate={false} />);
    const button = await screen.findByRole('button', { name: 'Record a reply' });
    await user.click(button);
    expect(screen.getByRole('form', { name: 'Record a reply' })).toBeInTheDocument();
    await user.click(button);
    expect(screen.queryByRole('form', { name: 'Record a reply' })).not.toBeInTheDocument();
  });
});
