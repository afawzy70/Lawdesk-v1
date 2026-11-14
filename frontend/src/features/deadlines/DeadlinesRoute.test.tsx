import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { describe, expect, it } from 'vitest';
import { deadlineRoutes } from './routes';
import { mount, serve, page } from '../followup/testSupport';
import { DeadlineSettingsPage } from './DeadlineSettingsPage';

/** The register of legal deadlines was removed: its old address leads to the calendar, the settings stay in the administration. */

function Where() {
  return <p data-testid="where">{useLocation().pathname}</p>;
}

describe('the removed deadlines register', () => {
  it('sends the old /deadlines address to the calendar instead of a missing page', () => {
    render(
      <MemoryRouter initialEntries={['/deadlines']}>
        <Routes>
          {deadlineRoutes()}
          <Route path="/calendar" element={<Where />} />
          <Route path="*" element={<p>not found</p>} />
        </Routes>
      </MemoryRouter>,
    );
    expect(screen.getByTestId('where')).toHaveTextContent('/calendar');
  });

  it('links the settings page to the calendar where the deadlines are read', async () => {
    serve(() => page([]));
    mount(<DeadlineSettingsPage />, ['DEADLINE_VIEW']);
    expect(await screen.findByRole('link', { name: 'Back to the calendar' })).toHaveAttribute('href', expect.stringContaining('/calendar'));
  });
});
