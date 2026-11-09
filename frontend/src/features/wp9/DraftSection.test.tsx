import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, beforeEach } from 'vitest';
import { jsonResponse } from '../../test/http';
import { DraftSection } from './DraftSection';
import { CASE, draft, mount, problem, serve, TEMPLATE_KINDS, templateSummary, type Request } from './testSupport';

/** LD-057, requirements section 29 (LD-DOC-29-010): a draft filled from the case file. */

function backend(summaries: unknown, onOther?: (r: Request) => Response | undefined) {
  return serve((r) => {
    if (r.method === 'GET' && r.path === '/api/v1/templates') return summaries instanceof Response ? summaries : jsonResponse(summaries);
    if (r.method === 'GET' && r.path === '/api/v1/lists/TEMPLATE_KIND') return jsonResponse(TEMPLATE_KINDS);
    return onOther?.(r);
  });
}

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token';
});

describe('a draft from a ready template', () => {
  it('is not shown when no template has an approved version, or the user may not read templates', async () => {
    backend([templateSummary({ latestApprovedVersionNo: null })]);
    const first = mount(<DraftSection caseId={CASE} />);
    await waitFor(() => { expect(screen.queryByRole('heading', { name: 'Draft from a template' })).not.toBeInTheDocument(); });
    first.unmount();
    backend(problem(403));
    mount(<DraftSection caseId={CASE} />);
    await waitFor(() => { expect(screen.queryByRole('heading', { name: 'Draft from a template' })).not.toBeInTheDocument(); });
  });

  it('generates a draft that is not final, marks the placeholders left unfilled and lists the missing fields', async () => {
    const user = userEvent.setup();
    const { requests } = backend([templateSummary()], (r) => (r.method === 'POST' ? jsonResponse(draft({ unknown: ['case.nope'] })) : undefined));
    mount(<DraftSection caseId={CASE} />);
    const generate = await screen.findByRole('button', { name: 'Generate a draft' });
    expect(generate).toBeDisabled();
    await user.selectOptions(screen.getByLabelText('Template'), 'Standard defence memorandum — Defence memorandum');
    await user.click(generate);
    const text = await screen.findByTestId('draft-text');
    expect(text).toHaveTextContent('Case LD-2026-00001 before {{case.chamber}}');
    expect(within(text).getByText('{{case.chamber}}').tagName).toBe('MARK');
    expect(screen.getByRole('status')).toHaveTextContent('Not final');
    expect(screen.getByRole('heading', { name: /Fields the case file does not hold/ })).toBeInTheDocument();
    expect(screen.getByText('Chamber')).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('case.nope');
    expect(screen.getByText('From version 1 of the template (Approved).')).toBeInTheDocument();
    expect(requests.find((r) => r.method === 'POST')?.body).toEqual({ templateId: 't1' });
    // Choosing another template clears the draft.
    await user.selectOptions(screen.getByLabelText('Template'), 'Choose…');
    expect(screen.queryByTestId('draft-text')).not.toBeInTheDocument();
  });

  it('says when every field was filled, and shows the rule that refused a draft', async () => {
    const user = userEvent.setup();
    let refuse = false;
    backend([templateSummary()], (r) => (r.method === 'POST' ? (refuse ? problem(422, { rule: 'TEMPLATE_NO_APPROVED_VERSION' }) : jsonResponse(draft({ missing: [], text: 'All filled' }))) : undefined));
    mount(<DraftSection caseId={CASE} />);
    await user.selectOptions(await screen.findByLabelText('Template'), 'Standard defence memorandum — Defence memorandum');
    await user.click(screen.getByRole('button', { name: 'Generate a draft' }));
    expect(await screen.findByText('Every field was filled from the case file.')).toBeInTheDocument();
    refuse = true;
    await user.click(screen.getByRole('button', { name: 'Generate a draft' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('no approved version');
    expect(screen.queryByTestId('draft-text')).not.toBeInTheDocument();
  });
});
