import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { jsonResponse } from '../../test/http';
import type { RiskAssessment } from './api';
import { RiskSection } from './RiskSection';
import { assessment, CASE, mount, page, problem, serve, type Request } from './testSupport';

/** LD-053, requirements section 19 (S7): the risk assessment, its versions and the plan for a high risk. */

const LIST = `/api/v1/cases/${CASE}/risk-assessments`;

function backend(items: RiskAssessment[] | Response, write?: (r: Request) => Response | undefined) {
  return serve((r) => {
    if (r.method === 'GET' && r.path === LIST) return items instanceof Response ? items : page(items);
    return write?.(r);
  });
}

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token';
});

describe('risk assessment', () => {
  it('shows the fixed notice, the current level and every version with its elements and plan', async () => {
    backend([
      assessment({
        id: 'a2', versionNo: 2, level: 'HIGH', potentialLoss: '15000.500', trigger: 'NEW_DOCUMENT', triggerNote: 'Delivery record',
        elements: [{ code: 'EVIDENCE_STRENGTH', rating: 'HIGH', reason: null }],
        remediation: [{ id: 'm1', assessmentId: 'a2', cause: 'No proof', action: 'Get the record', documents: null, defenses: null, unitToAddress: 'Records', ownerName: null, targetDate: '2026-12-01', status: 'OPEN', version: 0 }],
      }),
      assessment(),
    ]);
    mount(<RiskSection caseId={CASE} editable={false} />);
    expect(await screen.findByText(/It is not a certain judicial forecast/)).toBeInTheDocument();
    expect(screen.getByText(/Version 2 is the current assessment/)).toBeInTheDocument();
    expect(screen.getAllByText(/potential loss 15,000.500 KWD/).length).toBeGreaterThan(0);
    const history = document.querySelector('ol.wp3-list') as HTMLElement;
    expect(within(history).getAllByText(/Version \d —/)).toHaveLength(2);
    expect(within(history).getByText('Get the record')).toBeInTheDocument();
    expect(within(history).getByText('Open')).toBeInTheDocument();
    expect(within(history).queryAllByText('Not assessed')).toHaveLength(0);
    expect(screen.queryByRole('form')).not.toBeInTheDocument();
  });

  it('says there is no assessment, reports a failure and is left out for someone who may not open it', async () => {
    backend([]);
    const a = mount(<RiskSection caseId={CASE} editable={false} />);
    expect(await screen.findByText('No assessment has been recorded.')).toBeInTheDocument();
    a.unmount();
    backend(problem(500));
    const b = mount(<RiskSection caseId={CASE} editable={false} />);
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be loaded');
    b.unmount();
    backend(problem(403));
    mount(<RiskSection caseId={CASE} editable />);
    await waitFor(() => { expect(screen.queryByRole('heading', { name: 'Legal risk assessment' })).not.toBeInTheDocument(); });
  });

  it('records a new version with its elements and the potential loss as its own amount', async () => {
    const user = userEvent.setup();
    const { requests } = backend([], (r) => (r.method === 'POST' ? jsonResponse(assessment(), 201) : undefined));
    mount(<RiskSection caseId={CASE} editable />);
    const form = await screen.findByRole('form', { name: 'Record a new version' });
    await user.selectOptions(within(form).getByLabelText('Strength of documents and evidence'), 'Low');
    await user.type(within(form).getByLabelText('Reason for: Strength of documents and evidence'), 'Complete file');
    await user.selectOptions(within(form).getByLabelText('Overall level'), 'Low');
    await user.type(within(form).getByLabelText('Reasons for the level'), ' Strong file ');
    await user.type(within(form).getByLabelText('Potential loss (KWD)'), '1500.250');
    await user.selectOptions(within(form).getByLabelText('What caused this version'), 'A new document');
    await user.type(within(form).getByLabelText('Note on the cause'), 'Delivery record');
    await user.click(within(form).getByRole('button', { name: 'Save the version' }));
    expect(await screen.findByRole('status')).toHaveTextContent('A new version of the assessment was recorded.');
    expect(requests.find((r) => r.method === 'POST')?.body).toEqual({
      level: 'LOW',
      levelReasons: 'Strong file',
      potentialLoss: '1500.250',
      trigger: 'NEW_DOCUMENT',
      triggerNote: 'Delivery record',
      elements: [{ code: 'EVIDENCE_STRENGTH', rating: 'LOW', reason: 'Complete file' }],
      remediation: [],
    });
  });

  it('asks for the reasons, and for the plan when the level is high', async () => {
    const user = userEvent.setup();
    const { requests } = backend([], (r) => (r.method === 'POST' ? jsonResponse(assessment({ level: 'HIGH' }), 201) : undefined));
    mount(<RiskSection caseId={CASE} editable />);
    const form = await screen.findByRole('form', { name: 'Record a new version' });
    await user.click(within(form).getByRole('button', { name: 'Save the version' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Give the reasons for the level');
    await user.selectOptions(within(form).getByLabelText('Overall level'), 'Very high');
    await user.type(within(form).getByLabelText('Reasons for the level'), 'Weak');
    await user.click(within(form).getByRole('button', { name: 'Save the version' }));
    expect(requests.some((r) => r.method === 'POST')).toBe(false);
    await user.type(within(form).getByLabelText('Cause of the risk'), 'No proof');
    await user.type(within(form).getByLabelText('Proposed action'), 'Get the record');
    await user.type(within(form).getByLabelText('Unit to address'), 'Records');
    await user.type(within(form).getByLabelText('Target date'), '2026-12-10');
    await user.click(within(form).getByRole('button', { name: 'Save the version' }));
    await waitFor(() => { expect(requests.some((r) => r.method === 'POST')).toBe(true); });
    expect(requests.find((r) => r.method === 'POST')?.body).toMatchObject({
      level: 'VERY_HIGH',
      remediation: [{ cause: 'No proof', action: 'Get the record', unitToAddress: 'Records', targetDate: '2026-12-10' }],
    });
  });

  it('explains a refusal of the server', async () => {
    const user = userEvent.setup();
    backend([], (r) => (r.method === 'POST' ? problem(422, { rule: 'REMEDIATION_REQUIRED' }) : undefined));
    mount(<RiskSection caseId={CASE} editable />);
    const form = await screen.findByRole('form', { name: 'Record a new version' });
    await user.type(within(form).getByLabelText('Reasons for the level'), 'Fine');
    await user.click(within(form).getByRole('button', { name: 'Save the version' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('A high risk needs a remediation plan.');
  });

  it('changes the state of a remediation step, or explains why it could not', async () => {
    const user = userEvent.setup();
    let refuse = false;
    const step = { id: 'm1', assessmentId: 'a1', cause: 'No proof', action: 'Get the record', documents: null, defenses: null, unitToAddress: null, ownerName: null, targetDate: null, status: 'OPEN' as const, version: 0 };
    const { requests } = backend([assessment({ level: 'HIGH', remediation: [step] })], (r) =>
      r.method === 'PUT' ? (refuse ? problem(409) : jsonResponse({ ...step, status: 'DONE', version: 1 })) : undefined,
    );
    mount(<RiskSection caseId={CASE} editable />);
    await user.click(await screen.findByText(/Version 1 —/));
    await user.selectOptions(await screen.findByLabelText('Status of the step: Get the record'), 'Done');
    expect(await screen.findByRole('status')).toHaveTextContent('The remediation step was updated.');
    expect(requests.find((r) => r.method === 'PUT')?.body).toMatchObject({ status: 'DONE', version: 0 });
    refuse = true;
    await user.selectOptions(await screen.findByLabelText('Status of the step: Get the record'), 'Cancelled');
    expect(await screen.findByRole('alert')).toHaveTextContent('Someone else changed this record');
  });
});
