import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { CASE, problem, renderWith, serve } from '../pleadings/testSupport';
import { WithdrawnDocuments } from './WithdrawnDocuments';

/** LD-055 (requirement 13): documents taken out of the case file are kept; only an archive administrator deletes one, with a reason. */

const WITHDRAWN = `/api/v1/cases/${CASE}/documents/withdrawn`;
const doc = (extra = {}) => ({
  id: 'd1',
  caseId: CASE,
  title: 'Filed twice',
  fileName: 'x.pdf',
  mediaType: 'application/pdf',
  sizeBytes: 10,
  sha256: 'a',
  version: 7,
  createdAt: '',
  createdBy: 'u',
  withdrawnAt: '2026-05-03T08:00:00Z',
  withdrawnReason: 'filed in the wrong case',
  ...extra,
});

describe('withdrawn documents', () => {
  it('shows nothing when there are none', async () => {
    const backend = serve({ [`GET ${WITHDRAWN}`]: [] });
    const { container } = renderWith(<WithdrawnDocuments caseId={CASE} canAdminister generation={0} onRestored={() => undefined} />);
    await vi_waitFor(() => backend.to('GET', WITHDRAWN).length === 1);
    expect(container).toBeEmptyDOMElement();
  });

  it('restores a document into the case file', async () => {
    const user = userEvent.setup();
    let restored = 0;
    const backend = serve({ [`GET ${WITHDRAWN}`]: [doc()], 'POST /api/v1/documents/d1/restore': () => new Response(null, { status: 204 }) });
    renderWith(<WithdrawnDocuments caseId={CASE} canAdminister={false} generation={0} onRestored={() => (restored += 1)} />);
    expect(await screen.findByText(/Reason: filed in the wrong case/)).toBeVisible();
    expect(screen.queryByRole('button', { name: /Delete .* permanently/ })).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Restore Filed twice' }));
    expect(await screen.findByText('The document is back in the case file.')).toBeVisible();
    expect(restored).toBe(1);
    expect(backend.to('POST', '/api/v1/documents/d1/restore')[0]?.body).toEqual({ version: 7 });
  });

  it('deletes for good only after a reason is stated, and can be cancelled', async () => {
    const user = userEvent.setup();
    const backend = serve({ [`GET ${WITHDRAWN}`]: [doc()], 'POST /api/v1/documents/d1/permanent-deletion': () => new Response(null, { status: 204 }) });
    renderWith(<WithdrawnDocuments caseId={CASE} canAdminister generation={0} onRestored={() => undefined} />);
    await user.click(await screen.findByRole('button', { name: 'Delete Filed twice permanently' }));
    const form = screen.getByRole('form', { name: 'Delete Filed twice permanently' });
    expect(within(form).getByText(/cannot be undone/)).toBeVisible();
    expect(within(form).getByRole('button', { name: 'Delete for good' })).toBeDisabled();
    await user.click(within(form).getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByRole('form')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Delete Filed twice permanently' }));
    await user.type(screen.getByLabelText('Reason'), 'duplicate of another file');
    await user.click(screen.getByRole('button', { name: 'Delete for good' }));
    expect(await screen.findByText('The document was deleted. The deletion is logged.')).toBeVisible();
    expect(backend.to('POST', '/api/v1/documents/d1/permanent-deletion')[0]?.body).toEqual({ reason: 'duplicate of another file' });
  });

  it('explains a refused restore or deletion', async () => {
    const user = userEvent.setup();
    serve({
      [`GET ${WITHDRAWN}`]: [doc()],
      'POST /api/v1/documents/d1/restore': problem(422, { rule: 'DOCUMENT_LIMIT_REACHED' }),
      'POST /api/v1/documents/d1/permanent-deletion': problem(422, { rule: 'DOCUMENT_REFERENCED' }),
    });
    renderWith(<WithdrawnDocuments caseId={CASE} canAdminister generation={0} onRestored={() => undefined} />);
    await user.click(await screen.findByRole('button', { name: 'Restore Filed twice' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('already holds the most documents');
    await user.click(screen.getByRole('button', { name: 'Delete Filed twice permanently' }));
    await user.type(screen.getByLabelText('Reason'), 'duplicate');
    await user.click(screen.getByRole('button', { name: 'Delete for good' }));
    expect(await screen.findByText(/still refer to this document/)).toBeVisible();
  });
});

async function vi_waitFor(check: () => boolean): Promise<void> {
  for (let i = 0; i < 50 && !check(); i += 1) await new Promise((r) => setTimeout(r, 20));
}
