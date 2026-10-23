import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import pkg from '../package.json';
import { App, AppRoutes } from './App';
import { jsonResponse } from './test/http';

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AppRoutes />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ status: 'UP' })));
});

describe('application shell', () => {
  it('has banner, primary navigation, main and footer landmarks', async () => {
    renderAt('/');

    const banner = screen.getByRole('banner');
    expect(within(banner).getByText('LawDesk')).toBeInTheDocument();
    const nav = screen.getByRole('navigation', { name: 'Primary' });
    expect(within(nav).getByRole('link', { name: 'Home' })).toHaveAttribute('href', '/');
    expect(screen.getByRole('main')).toBeInTheDocument();
    expect(screen.getByRole('contentinfo')).toBeInTheDocument();
    expect(await screen.findByText('API online')).toBeInTheDocument();
  });

  it('shows the application version from package.json in the footer', () => {
    renderAt('/');
    expect(screen.getByRole('contentinfo')).toHaveTextContent(`LawDesk v${pkg.version}`);
  });

  it('renders the home page at /', () => {
    renderAt('/');
    expect(screen.getByRole('heading', { level: 1, name: 'Welcome to LawDesk' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Home' })).toHaveAttribute('aria-current', 'page');
  });

  it('renders an actionable not-found page for unknown routes', async () => {
    const user = userEvent.setup();
    renderAt('/cases/does-not-exist');

    expect(screen.getByRole('heading', { level: 1, name: 'Page not found' })).toBeInTheDocument();
    expect(screen.getByRole('main')).toHaveTextContent('Check the address, or go back to the home page.');
    const homeLink = within(screen.getByRole('main')).getByRole('link', { name: 'Go to the home page' });

    await user.click(homeLink);

    expect(screen.getByRole('heading', { level: 1, name: 'Welcome to LawDesk' })).toBeInTheDocument();
  });

  it('keeps the shell visible when a page is not found', () => {
    renderAt('/nope');
    expect(screen.getByRole('banner')).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Primary' })).toBeInTheDocument();
  });

  it('does not write to localStorage or sessionStorage', async () => {
    const setItem = vi.spyOn(Storage.prototype, 'setItem');
    render(<App />);
    expect(await screen.findByText('API online')).toBeInTheDocument();
    expect(setItem).not.toHaveBeenCalled();
    expect(window.localStorage).toHaveLength(0);
    expect(window.sessionStorage).toHaveLength(0);
  });

  it('applies the default locale to <html> (lang="en", dir="ltr")', async () => {
    document.documentElement.removeAttribute('lang');
    document.documentElement.removeAttribute('dir');
    render(<App />);
    expect(document.documentElement).toHaveAttribute('lang', 'en');
    expect(document.documentElement).toHaveAttribute('dir', 'ltr');
    expect(await screen.findByText('API online')).toBeInTheDocument();
  });

  it('applies an Arabic locale to <html> as right-to-left', async () => {
    render(<App locale="ar" />);
    expect(document.documentElement).toHaveAttribute('lang', 'ar');
    expect(document.documentElement).toHaveAttribute('dir', 'rtl');
    expect(await screen.findByText('API online')).toBeInTheDocument();
  });
});
