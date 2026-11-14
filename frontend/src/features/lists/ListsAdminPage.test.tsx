import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { PERMISSIONS } from '../../api/permissions';
import { problem, renderWith, serve } from '../pleadings/testSupport';
import { ListsAdminPage } from './ListsAdminPage';

const summary = (code: string, extra = {}) => ({
  code,
  source: 'LOOKUP',
  addable: true,
  titleAr: `قائمة ${code}`,
  titleEn: `List ${code}`,
  noteAr: '',
  noteEn: '',
  options: 2,
  ...extra,
});

const option = (id: string, code: string, extra = {}) => ({
  id,
  listCode: 'CASE_IMPORTANCE',
  code,
  labelAr: `اسم ${code}`,
  labelEn: `Name ${code}`,
  active: true,
  sortOrder: 1,
  version: 0,
  system: false,
  ...extra,
});

const CATALOGUE = [
  summary('CASE_IMPORTANCE'),
  summary('CASE_TYPE', { addable: false, noteEn: 'Only labels and order' }),
  summary('SETTLEMENT_KIND', { source: 'MANAGED' }),
  summary('COURTS', { source: 'COURT' }),
];

describe('administrator dropdown lists', () => {
  it('adds and edits an option of an addable list', async () => {
    const user = userEvent.setup();
    const backend = serve({
      'GET /api/v1/admin/lists': CATALOGUE,
      'GET /api/v1/admin/lists/CASE_IMPORTANCE/options': [option('o1', 'LOW'), option('o2', 'HIGH', { active: false, version: 4 })],
      'POST /api/v1/admin/lists/CASE_IMPORTANCE/options': option('o3', 'URGENT'),
      'PUT /api/v1/admin/lists/options/o1': option('o1', 'LOW'),
    });
    renderWith(<ListsAdminPage />);
    await screen.findByText('Name HIGH', { exact: false });
    expect(within(screen.getByRole('table')).getAllByRole('row')[2]).toHaveTextContent('Inactive');

    await user.click(screen.getByRole('button', { name: 'Edit option Name LOW' }));
    const edit = screen.getByRole('form', { name: 'Edit option LOW' });
    const english = within(edit).getByLabelText('English label');
    await user.clear(english);
    await user.type(english, 'Lowest');
    await user.click(within(edit).getByRole('button', { name: 'Save' }));
    expect(await screen.findByRole('status')).toHaveTextContent('The change was saved.');
    expect(backend.to('PUT', '/api/v1/admin/lists/options/o1')[0]?.body).toMatchObject({ labelEn: 'Lowest', active: true, version: 0 });

    const form = screen.getByRole('form', { name: 'Add option' });
    await user.type(within(form).getByLabelText('Code'), 'URGENT');
    await user.type(within(form).getByLabelText('Arabic label'), 'عاجلة');
    await user.click(within(form).getByRole('button', { name: 'Add option' }));
    expect(await screen.findByText('The option was added.')).toBeVisible();
    expect(backend.to('POST', '/api/v1/admin/lists/CASE_IMPORTANCE/options')[0]?.body).toMatchObject({ code: 'URGENT', labelAr: 'عاجلة' });
  });

  it('offers a system value no deactivation and a label-only list no new option', async () => {
    const user = userEvent.setup();
    serve({
      'GET /api/v1/admin/lists': CATALOGUE,
      'GET /api/v1/admin/lists/CASE_TYPE/options': [option('t1', 'CIVIL', { listCode: 'CASE_TYPE', system: true })],
    });
    renderWith(<ListsAdminPage />);
    await user.selectOptions(await screen.findByLabelText('List'), 'CASE_TYPE');
    expect(await screen.findByText('System value')).toBeVisible();
    expect(screen.queryByRole('form', { name: 'Add option' })).not.toBeInTheDocument();
    expect(screen.getByText(/no new options can be added/)).toBeVisible();
    await user.click(screen.getByRole('button', { name: 'Edit option Name CIVIL' }));
    expect(screen.queryByLabelText('Active')).not.toBeInTheDocument();
  });

  it('explains a refused rule, a stale version, and points the courts to their own screen', async () => {
    const user = userEvent.setup();
    serve({
      'GET /api/v1/admin/lists': CATALOGUE,
      'GET /api/v1/admin/lists/CASE_IMPORTANCE/options': [option('o1', 'LOW')],
      'PUT /api/v1/admin/lists/options/o1': problem(409),
    });
    renderWith(<ListsAdminPage />);
    await user.click(await screen.findByRole('button', { name: 'Edit option Name LOW' }));
    await user.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Someone else changed this option');
    await user.selectOptions(screen.getByLabelText('List'), 'COURTS');
    expect(await screen.findByRole('link', { name: 'Open the courts screen' })).toHaveAttribute('href', expect.stringContaining('/courts'));
  });

  it('shows the access-denied page without the administrator right', async () => {
    serve({ 'GET /api/v1/admin/lists': problem(403) });
    renderWith(<ListsAdminPage />, { permissions: PERMISSIONS.filter((p) => p !== 'LIST_ADMIN') });
    expect(await screen.findByRole('heading', { level: 1 })).not.toHaveTextContent('Dropdown lists');
  });
});
