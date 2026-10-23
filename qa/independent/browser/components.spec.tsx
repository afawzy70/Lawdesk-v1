// Adapter renders the REAL product form with real providers and test transport.
// Never replace the subject with a QA-owned fake component.
import { afterEach, describe, expect, test } from 'vitest';
import { cleanup, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
const adapterPath = process.env.LAWDESK_QA_COMPONENT_ADAPTER;
afterEach(cleanup);
describe.skipIf(!adapterPath)('LD-003/LD-037 real React form', () => {
  test('QA-0290: required-field error has accessible association', async () => {
    const adapter = await import(/* @vite-ignore */ adapterPath!);
    const c = await adapter.renderRequiredFieldForm();
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: c.submitLabel, exact: true }));
    const input = screen.getByRole('textbox', { name: c.requiredLabel, exact: true });
    expect(input.getAttribute('aria-invalid')).toBe('true');
    const descriptions = (input.getAttribute('aria-describedby') || '').split(/\s+/).filter(Boolean);
    expect(descriptions.some(id => document.getElementById(id)?.textContent?.includes(c.requiredError))).toBe(true);
    expect(c.successfulMutationCount()).toBe(0);
  });
});
