import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { classification, jsonResponse, problem, renderLibrary, script } from './testSupport';

/** Owner decision D10: lists of values the document gives, apart from proposed ones; never deleted. */
beforeEach(() => {
  vi.unstubAllGlobals();
});

describe('classification lists', () => {
  it('lists a dimension with the origin of each value and adds a proposed one', async () => {
    let items = [
      classification({ id: 'k1', dimension: 'DEFENSE_TYPE', code: 'FORMAL', labelAr: 'شكلي', labelEn: 'Formal', origin: 'DOCUMENT' }),
    ];
    const { calls } = script((call) => {
      if (call.method === 'POST') {
        items = [...items, classification({ id: 'k2', code: 'TENDERS', labelAr: 'المناقصات', labelEn: null })];
        return jsonResponse(items[1], 201);
      }
      return jsonResponse(call.search.get('dimension') === 'LEGAL_TOPIC' ? [] : items);
    });
    const typing = userEvent.setup();
    renderLibrary('/library/classifications');
    expect(await screen.findByText('This list has no values yet.')).toBeInTheDocument();
    await typing.selectOptions(screen.getByLabelText('List'), 'DEFENSE_TYPE');
    expect(await screen.findByText('شكلي')).toBeInTheDocument();
    expect(screen.getByText('From the requirements document')).toBeInTheDocument();
    expect(calls.some((c) => c.search.get('dimension') === 'DEFENSE_TYPE' && c.search.get('includeInactive') === 'true')).toBe(true);

    await typing.click(screen.getByRole('button', { name: 'Add value' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Give a code');
    await typing.type(screen.getByLabelText('Code (capital letters, digits, underscore)'), 'tenders');
    await typing.type(screen.getByLabelText('Name in Arabic'), 'المناقصات');
    await typing.click(screen.getByRole('button', { name: 'Add value' }));
    expect(await screen.findByText('Value added.')).toBeInTheDocument();
    expect(calls.find((c) => c.method === 'POST')?.body).toEqual({
      dimension: 'DEFENSE_TYPE',
      code: 'TENDERS',
      labelAr: 'المناقصات',
      labelEn: null,
    });
    expect(await screen.findByText('Proposed')).toBeInTheDocument();
  });

  it('deactivates and reactivates a value and explains a duplicate', async () => {
    let active = true;
    let conflict = false;
    const { calls } = script((call) => {
      if (call.method === 'PUT') {
        if (conflict) return problem(409);
        active = !active;
        return jsonResponse(classification({ active }));
      }
      if (call.method === 'POST') return problem(409);
      return jsonResponse([classification({ active, labelEn: 'Liability' })]);
    });
    const typing = userEvent.setup();
    renderLibrary('/library/classifications');
    await typing.click(await screen.findByRole('button', { name: 'Deactivate' }));
    expect(await screen.findByText('inactive')).toBeInTheDocument();
    expect(calls.find((c) => c.method === 'PUT')?.body).toMatchObject({ active: false, version: 0 });
    await typing.click(screen.getByRole('button', { name: 'Activate' }));
    await waitFor(() => {
      expect(screen.queryByText('inactive')).not.toBeInTheDocument();
    });
    conflict = true;
    await typing.click(screen.getByRole('button', { name: 'Deactivate' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('already exists');
    await typing.type(screen.getByLabelText('Code (capital letters, digits, underscore)'), 'X');
    await typing.type(screen.getByLabelText('Name in Arabic'), 'ب');
    await typing.type(screen.getByLabelText('Name in English'), 'B');
    await typing.click(screen.getByRole('button', { name: 'Add value' }));
    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('already exists');
    });
  });

  it('shows the access-denied page, a load failure and a save failure', async () => {
    script(() => problem(403));
    const denied = renderLibrary('/library/classifications');
    expect(await screen.findByRole('heading', { name: /access/i })).toBeInTheDocument();
    denied.unmount();
    script(() => problem(500));
    const failed = renderLibrary('/library/classifications');
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be loaded');
    failed.unmount();
    script((call) => (call.method === 'GET' ? jsonResponse([classification()]) : problem(500)));
    const typing = userEvent.setup();
    renderLibrary('/library/classifications');
    await typing.click(await screen.findByRole('button', { name: 'Deactivate' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be saved');
  });
});
