import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { jsonResponse } from '../../test/http';
import { AssistantStatusPage } from './AssistantStatusPage';
import { INTEGRATIONS, mount, problem, serve, STATUS } from './testSupport';

/** LD-059, owner decision D8: the status page says in plain words that the assistant is not enabled, and why. */

describe('the assistant status page', () => {
  it('says the assistant is not enabled because no provider is approved, and that the test provider proves nothing operational', async () => {
    serve((r) => (r.path === '/api/v1/assistant/status' ? jsonResponse(STATUS) : problem(403)));
    mount(<AssistantStatusPage />, ['ASSISTANT_USE']);
    expect(await screen.findByText('Not enabled: no approved provider')).toBeInTheDocument();
    expect(screen.getByText('Reason').nextSibling).toHaveTextContent('No approved provider');
    expect(screen.getByText('Mode').nextSibling).toHaveTextContent('Non-production test');
    expect(screen.getByText('Operational').nextSibling).toHaveTextContent('No');
    expect(screen.getByText('Provider in use').nextSibling).toHaveTextContent('Local extractive test provider');
    expect(screen.getByText('Calls to outside services').nextSibling).toHaveTextContent('None');
    expect(screen.getByText('Case data leaving the system').nextSibling).toHaveTextContent('None');
    expect(screen.getByText(/not evidence that an operational assistant works/)).toBeInTheDocument();
    expect(screen.queryByText('Integrations with other systems')).not.toBeInTheDocument();
  });

  it('shows the Arabic wording of the same state', async () => {
    serve((r) => (r.path === '/api/v1/assistant/status' ? jsonResponse(STATUS) : problem(403)));
    mount(<AssistantStatusPage />, [], '/?lang=ar');
    expect(await screen.findByText('غير مفعّل: لا مزود معتمد')).toBeInTheDocument();
  });

  it('lists the future integrations, all disabled, for those who may see users', async () => {
    serve((r) => (r.path === '/api/v1/assistant/status' ? jsonResponse(STATUS) : r.path === '/api/v1/integrations' ? jsonResponse(INTEGRATIONS) : undefined));
    mount(<AssistantStatusPage />, ['USER_VIEW']);
    expect(await screen.findByRole('heading', { name: 'Integrations with other systems' })).toBeInTheDocument();
    const items = screen.getAllByRole('listitem');
    expect(items[0]).toHaveTextContent('Correspondence system — Disabled — not approved · Inbound and outbound');
    expect(items[0]).toHaveTextContent('registerIncomingLetter، getLetterStatus');
    expect(items[1]).toHaveTextContent('A newer system — Disabled — not approved');
  });

  it('is honest when the state cannot be loaded or says something unexpected', async () => {
    serve(() => problem(500));
    mount(<AssistantStatusPage />, ['USER_VIEW']);
    expect(await screen.findByRole('alert')).toHaveTextContent('The status could not be loaded.');
  });

  it('shows an approved provider count and a reason code it does not know as they are', async () => {
    serve((r) => (r.path === '/api/v1/assistant/status'
      ? jsonResponse({ ...STATUS, reason: 'SOMETHING', mode: 'OTHER', approvedProviders: 2, provider: { code: 'X', nonProduction: false, external: true }, externalCalls: true, caseDataLeavesTheSystem: true, operational: true })
      : undefined));
    mount(<AssistantStatusPage />, []);
    expect(await screen.findByText('SOMETHING')).toBeInTheDocument();
    expect(screen.getByText('OTHER')).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();
    expect(screen.getByText('X')).toBeInTheDocument();
    expect(screen.getByText('✓')).toBeInTheDocument();
    expect(screen.getAllByText('!')).toHaveLength(2);
  });
});
