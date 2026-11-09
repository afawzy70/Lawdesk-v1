import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { jsonResponse } from '../../test/http';
import { TemplatesPage } from './TemplatesPage';
import { mount, problem, serve, template, TEMPLATE_KINDS, templateSummary, templateVersion, type Request } from './testSupport';

/** LD-057, requirements section 29: the library of ready legal templates. */

const FIELDS = [
  { key: 'case.referenceNo', labelAr: 'الرقم الداخلي للقضية', labelEn: 'Internal case number' },
  { key: 'court.name', labelAr: 'المحكمة', labelEn: 'Court' },
];

function backend(summaries: unknown[], detail: unknown, onOther?: (r: Request) => Response | undefined) {
  return serve((r) => {
    if (r.method === 'GET' && r.path === '/api/v1/templates') return jsonResponse(summaries);
    if (r.method === 'GET' && r.path === '/api/v1/templates/fields') return jsonResponse(FIELDS);
    if (r.method === 'GET' && r.path === '/api/v1/lists/TEMPLATE_KIND') return jsonResponse(TEMPLATE_KINDS);
    if (r.method === 'GET' && r.path === '/api/v1/templates/t1') return jsonResponse(detail);
    return onOther?.(r);
  });
}

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token';
});

describe('the template library', () => {
  it('lists templates with their kind, use, origin and approval, and tells a reader what is not there', async () => {
    backend([templateSummary(), templateSummary({ id: 't2', title: 'Old memo', active: false, origin: 'DOCUMENT', latestApprovedVersionNo: null, versionCount: 3 })], template());
    mount(<TemplatesPage />, ['CASE_VIEW']);
    const items = await screen.findAllByRole('listitem');
    expect(items[0]).toHaveTextContent('Standard defence memorandum');
    expect(items[0]).toHaveTextContent('Defence memorandum');
    expect(items[0]).toHaveTextContent('In use');
    expect(items[0]).toHaveTextContent('Added by a member');
    expect(items[0]).toHaveTextContent('Approved up to version 1');
    expect(items[1]).toHaveTextContent('Not in use');
    expect(items[1]).toHaveTextContent('From the requirements document');
    expect(items[1]).toHaveTextContent('No approved version yet');
    expect(items[1]).toHaveTextContent('3 version(s)');
    expect(screen.queryByRole('form', { name: 'New template' })).not.toBeInTheDocument();
  });

  it('says there are no templates, reports a failed load and refuses someone who may not open it', async () => {
    backend([], template());
    const first = mount(<TemplatesPage />, ['CASE_VIEW']);
    expect(await screen.findByText(/No template yet/)).toBeInTheDocument();
    first.unmount();
    serve((r) => (r.path === '/api/v1/templates' ? problem(500) : undefined));
    const second = mount(<TemplatesPage />, ['CASE_VIEW']);
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be loaded');
    second.unmount();
    serve((r) => (r.path === '/api/v1/templates' ? problem(403) : undefined));
    mount(<TemplatesPage />, []);
    expect(await screen.findByRole('heading', { name: 'Access denied' })).toBeInTheDocument();
  });

  it('opens a template to read its versions, and reports one that cannot be opened', async () => {
    const user = userEvent.setup();
    backend([templateSummary()], template({ versions: [templateVersion({ status: 'APPROVED', body: null, changeNote: 'First' })] }));
    mount(<TemplatesPage />, ['CASE_VIEW']);
    await user.click(await screen.findByRole('button', { name: 'Open Standard defence memorandum' }));
    const version = await screen.findByRole('listitem', { name: 'Version 1' });
    expect(version).toHaveTextContent('Approved');
    expect(version).toHaveTextContent('First');
    expect(version).toHaveTextContent('Uses: Internal case number');
    // A reader sees no text and no buttons that change anything.
    expect(within(version).queryByRole('button')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Close' }));
    expect(screen.queryByRole('listitem', { name: 'Version 1' })).not.toBeInTheDocument();
    serve((r) => (r.path === '/api/v1/templates' ? jsonResponse([templateSummary()]) : undefined));
    await user.click(screen.getByRole('button', { name: 'Open Standard defence memorandum' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be loaded');
  });

  it('creates a template of a kind from the managed list with its first version as a draft', async () => {
    const user = userEvent.setup();
    const { requests } = backend([], template(), (r) => (r.method === 'POST' && r.path === '/api/v1/templates' ? jsonResponse(template(), 201) : undefined));
    mount(<TemplatesPage />, ['CASE_VIEW', 'TEMPLATE_MANAGE']);
    const form = await screen.findByRole('form', { name: 'New template' });
    await user.click(within(form).getByRole('button', { name: 'Create the template' }));
    expect(await within(form).findByRole('alert')).toHaveTextContent('Choose the kind');
    await user.selectOptions(await within(form).findByLabelText('Kind'), 'Defence memorandum');
    await user.type(within(form).getByLabelText('Title'), 'Standard defence memorandum');
    await user.type(within(form).getByLabelText('Text (Arabic)'), 'Case {{{{case.referenceNo}}');
    await user.click(within(form).getByRole('button', { name: 'Create the template' }));
    expect(await screen.findByText('The template was created.')).toBeInTheDocument();
    expect(requests.find((r) => r.method === 'POST')?.body).toEqual({ kind: 'DEFENSE_MEMO', title: 'Standard defence memorandum', description: null, body: 'Case {{case.referenceNo}}' });
    expect(await screen.findByRole('listitem', { name: 'Version 1' })).toBeInTheDocument();
    // The fields the case file can fill are listed for the author.
    expect(screen.getByText('{{court.name}}')).toBeInTheDocument();
  });

  it('reviews and then approves a draft version, and a reviewed version offers no edit', async () => {
    const user = userEvent.setup();
    let version = templateVersion();
    const { requests } = backend([templateSummary()], null, (r) => {
      if (r.method === 'POST' && r.path === '/api/v1/template-versions/tv1/review') {
        version = templateVersion({ status: 'REVIEWED', version: 1 });
        return jsonResponse(version);
      }
      if (r.method === 'POST' && r.path === '/api/v1/template-versions/tv1/approve') {
        version = templateVersion({ status: 'APPROVED', version: 2 });
        return jsonResponse(version);
      }
      return undefined;
    });
    serve((r) => {
      if (r.method === 'GET' && r.path === '/api/v1/templates') return jsonResponse([templateSummary()]);
      if (r.method === 'GET' && r.path === '/api/v1/templates/fields') return jsonResponse(FIELDS);
      if (r.method === 'GET' && r.path === '/api/v1/lists/TEMPLATE_KIND') return jsonResponse(TEMPLATE_KINDS);
      if (r.method === 'GET' && r.path === '/api/v1/templates/t1') return jsonResponse(template({ versions: [version] }));
      if (r.method === 'POST' && r.path === '/api/v1/template-versions/tv1/review') {
        version = templateVersion({ status: 'REVIEWED', version: 1 });
        return jsonResponse(version);
      }
      if (r.method === 'POST' && r.path === '/api/v1/template-versions/tv1/approve') {
        version = templateVersion({ status: 'APPROVED', version: 2 });
        return jsonResponse(version);
      }
      return undefined;
    });
    expect(requests).toBeDefined();
    mount(<TemplatesPage />, ['CASE_VIEW', 'TEMPLATE_MANAGE']);
    await user.click(await screen.findByRole('button', { name: 'Open Standard defence memorandum' }));
    let row = await screen.findByRole('listitem', { name: 'Version 1' });
    expect(within(row).getByText('Case {{case.referenceNo}}')).toBeInTheDocument();
    await user.click(within(row).getByRole('button', { name: 'Mark version 1 as reviewed' }));
    expect(await screen.findByText('The version was marked as reviewed.')).toBeInTheDocument();
    row = await screen.findByRole('listitem', { name: 'Version 1' });
    await waitFor(() => { expect(within(row).getByText('Reviewed')).toBeInTheDocument(); });
    expect(within(row).queryByRole('button', { name: 'Edit version 1' })).not.toBeInTheDocument();
    await user.click(within(row).getByRole('button', { name: 'Approve version 1' }));
    expect(await screen.findByText('The version was approved.')).toBeInTheDocument();
    await waitFor(() => { expect(within(screen.getByRole('listitem', { name: 'Version 1' })).getByText('Approved')).toBeInTheDocument(); });
  });

  it('edits a draft version in place and adds a new version', async () => {
    const user = userEvent.setup();
    const { requests } = backend([templateSummary()], template(), (r) => {
      if (r.method === 'PUT' && r.path === '/api/v1/template-versions/tv1') return jsonResponse(templateVersion({ version: 1 }));
      if (r.method === 'POST' && r.path === '/api/v1/templates/t1/versions') return jsonResponse(templateVersion({ id: 'tv2', versionNo: 2 }), 201);
      return undefined;
    });
    mount(<TemplatesPage />, ['CASE_VIEW', 'TEMPLATE_MANAGE']);
    await user.click(await screen.findByRole('button', { name: 'Open Standard defence memorandum' }));
    const row = await screen.findByRole('listitem', { name: 'Version 1' });
    await user.click(within(row).getByRole('button', { name: 'Edit version 1' }));
    const text = within(row).getByLabelText('Text (Arabic)');
    await user.clear(text);
    await user.type(text, 'Changed');
    await user.click(within(row).getByRole('button', { name: 'Save the text' }));
    await waitFor(() => { expect(requests.some((r) => r.method === 'PUT')).toBe(true); });
    expect(requests.find((r) => r.method === 'PUT')?.body).toEqual({ body: 'Changed', changeNote: null, version: 0 });
    const form = await screen.findByRole('form', { name: 'Add a version' });
    await user.click(within(form).getByRole('button', { name: 'Add the version' }));
    expect(requests.some((r) => r.method === 'POST')).toBe(false);
    await user.type(within(form).getByLabelText('Text (Arabic)'), 'Second text');
    await user.type(within(form).getByLabelText('What changed'), 'New paragraph');
    await user.click(within(form).getByRole('button', { name: 'Add the version' }));
    expect(await screen.findByText('A new version was added as a draft.')).toBeInTheDocument();
    expect(requests.find((r) => r.method === 'POST')?.body).toEqual({ body: 'Second text', changeNote: 'New paragraph' });
  });

  it('warns about a field the case file does not have, puts a template out of use, and reports a refusal', async () => {
    const user = userEvent.setup();
    const bad = templateVersion({ placeholders: [{ key: 'case.nope', known: false, labelAr: null, labelEn: null }] });
    const { requests } = backend([templateSummary()], template({ versions: [bad] }), (r) => {
      if (r.method === 'PUT' && r.path === '/api/v1/templates/t1') return jsonResponse(template({ active: false }));
      if (r.method === 'POST' && r.path.endsWith('/review')) return problem(422, { rule: 'TEMPLATE_VERSION_TRANSITION' });
      return undefined;
    });
    mount(<TemplatesPage />, ['CASE_VIEW', 'TEMPLATE_MANAGE']);
    await user.click(await screen.findByRole('button', { name: 'Open Standard defence memorandum' }));
    const row = await screen.findByRole('listitem', { name: 'Version 1' });
    expect(row).toHaveTextContent('unknown field case.nope');
    expect(within(row).getByRole('alert')).toHaveTextContent('fix it before approval');
    await user.click(within(row).getByRole('button', { name: 'Mark version 1 as reviewed' }));
    expect(await screen.findByText('The version cannot take that step from its current state.')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Take out of use' }));
    await waitFor(() => { expect(requests.find((r) => r.method === 'PUT')?.body).toMatchObject({ active: false, version: 0 }); });
  });

  it('offers a template that is not in use to be put back in use, and shows a template with no version', async () => {
    const user = userEvent.setup();
    backend([templateSummary({ active: false })], template({ active: false, versions: [] }));
    mount(<TemplatesPage />, ['CASE_VIEW', 'TEMPLATE_MANAGE']);
    await user.click(await screen.findByRole('button', { name: 'Open Standard defence memorandum' }));
    expect(await screen.findByText('This template has no version yet.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Put in use' })).toBeInTheDocument();
  });
});
