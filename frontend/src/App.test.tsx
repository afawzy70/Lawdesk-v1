import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import pkg from '../package.json';
import { App, AppRoutes } from './App';
import { PLANNED_AREAS } from './layout/navigation';
import { fakeApi } from './test/api';

/** Renders the shell at `path` and waits until the session has loaded. */
async function renderAt(path: string) {
  const result = render(
    <MemoryRouter initialEntries={[path]}>
      <AppRoutes />
    </MemoryRouter>,
  );
  await screen.findByRole('banner');
  // The page sets its title in an effect once the session has loaded.
  await waitFor(() => {
    expect(document.title).not.toBe('');
  });
  return result;
}

const BANNER = {
  en: 'UAT test environment — enter test data only, never real client data. The dashboard shows sample figures, and areas marked “coming later” are not built yet.',
  ar: 'بيئة اختبار UAT — أدخل بيانات تجريبية فقط، ولا تُدخل بيانات موكلين حقيقية. لوحة المعلومات تعرض أرقامًا نموذجية، والأقسام المعلَّمة «لاحقًا» لم تُبنَ بعد.',
} as const;

const NAV = [
  { path: '/', en: 'Dashboard', ar: 'لوحة المعلومات' },
  { path: '/cases', en: 'Cases', ar: 'القضايا' },
  { path: '/courts', en: 'Courts', ar: 'المحاكم' },
  { path: '/hearings', en: 'Hearings', ar: 'الجلسات' },
  { path: '/judgments', en: 'Judgments', ar: 'الأحكام' },
  { path: '/experts', en: 'Experts', ar: 'الخبراء' },
  { path: '/documents', en: 'Documents', ar: 'المستندات' },
  { path: '/reports', en: 'Reports', ar: 'التقارير' },
  { path: '/admin', en: 'Administration', ar: 'الإدارة' },
] as const;

/**
 * Areas still shown as preview placeholders: those `planned` in navigation. An area that becomes a working screen
 * (Administration in LD-009) leaves these checks by flipping its own status.
 */
const PLANNED = new Set(PLANNED_AREAS.filter((area) => area.status === 'planned').map((area) => area.path));
const PLACEHOLDERS = NAV.filter((item) => PLANNED.has(item.path));
const PREVIEW_ROUTES = [...NAV.map((n) => n.path).filter((path) => path === '/' || PLANNED.has(path)), '/no-such-page'];

function primaryNav(name: string | RegExp = 'Primary') {
  return screen.getByRole('navigation', { name });
}

beforeEach(() => {
  // Signed in: the shell needs a session since LD-007 (src/test/api.ts).
  vi.stubGlobal('fetch', fakeApi());
  window.history.replaceState(null, '', '/');
  document.title = '';
});

describe('application shell', () => {
  it('has banner, primary navigation, main and footer landmarks', async () => {
    await renderAt('/');

    const banner = screen.getByRole('banner');
    expect(within(banner).getByText('LawDesk')).toBeInTheDocument();
    expect(within(primaryNav()).getByRole('link', { name: 'Dashboard' })).toHaveAttribute('href', '/?lang=en');
    expect(screen.getAllByRole('main')).toHaveLength(1);
    expect(screen.getByRole('contentinfo')).toBeInTheDocument();
    expect(await screen.findByText('API online')).toBeInTheDocument();
  });

  it('shows the application version from package.json in the footer', async () => {
    await renderAt('/');
    expect(screen.getByRole('contentinfo')).toHaveTextContent(`LawDesk v${pkg.version}`);
  });

  it('lists every planned area in the primary navigation, in order', async () => {
    await renderAt('/');
    const links = within(primaryNav()).getAllByRole('link');
    expect(links.map((l) => l.textContent)).toEqual(NAV.map((n) => n.en));
    expect(links.map((l) => l.getAttribute('href'))).toEqual(NAV.map((n) => `${n.path}?lang=en`));
  });

  it('renders the dashboard at / and marks it as the current page', async () => {
    await renderAt('/');
    expect(screen.getByRole('heading', { level: 1, name: 'Dashboard' })).toBeInTheDocument();
    expect(within(primaryNav()).getByRole('link', { name: 'Dashboard' })).toHaveAttribute('aria-current', 'page');
  });

  it('renders an actionable not-found page for unknown routes', async () => {
    const user = userEvent.setup();
    await renderAt('/cases/does-not-exist');

    expect(screen.getByRole('heading', { level: 1, name: 'Page not found' })).toBeInTheDocument();
    expect(screen.getByRole('main')).toHaveTextContent('Check the address, or go back to the home page.');
    const homeLink = within(screen.getByRole('main')).getByRole('link', { name: 'Go to the home page' });

    await user.click(homeLink);

    expect(screen.getByRole('heading', { level: 1, name: 'Dashboard' })).toBeInTheDocument();
  });

  it('keeps the shell visible when a page is not found', async () => {
    await renderAt('/nope');
    expect(screen.getByRole('banner')).toBeInTheDocument();
    expect(primaryNav()).toBeInTheDocument();
    expect(document.title).toBe('Page not found — LawDesk (Preview)');
  });

  it('does not write to localStorage or sessionStorage, even when the language changes', async () => {
    const user = userEvent.setup();
    const setItem = vi.spyOn(Storage.prototype, 'setItem');
    render(<App />);
    expect(await screen.findByText('API online')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'العربية' }));
    expect(document.documentElement).toHaveAttribute('dir', 'rtl');

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
    expect(await screen.findByText('الواجهة البرمجية متصلة')).toBeInTheDocument();
  });

  it('offers a skip link as the first focusable element that targets <main>', async () => {
    const user = userEvent.setup();
    await renderAt('/');
    await user.tab();
    const skip = screen.getByRole('link', { name: 'Skip to main content' });
    expect(skip).toHaveFocus();
    const target = skip.getAttribute('href')?.slice(1) ?? '';
    expect(screen.getByRole('main')).toHaveAttribute('id', target);
    expect(screen.getByRole('main')).toHaveAttribute('tabindex', '-1');
  });
});

describe('locale switching', () => {
  it('switches the whole shell to Arabic, right-to-left, from the header toggle', async () => {
    const user = userEvent.setup();
    await renderAt('/');

    const toggle = within(screen.getByRole('banner')).getByRole('button', { name: 'العربية' });
    expect(toggle).toHaveAttribute('lang', 'ar');
    await user.click(toggle);

    expect(document.documentElement).toHaveAttribute('lang', 'ar');
    expect(document.documentElement).toHaveAttribute('dir', 'rtl');
    expect(screen.getByRole('heading', { level: 1, name: 'لوحة المعلومات' })).toBeInTheDocument();
    expect(within(primaryNav('التنقل الرئيسي')).getAllByRole('link').map((l) => l.textContent)).toEqual(
      NAV.map((n) => n.ar),
    );
    expect(document.title).toBe('لوحة المعلومات — LawDesk (معاينة)');

    const back = screen.getByRole('button', { name: 'English' });
    expect(back).toHaveAttribute('lang', 'en');
    await user.click(back);
    expect(document.documentElement).toHaveAttribute('dir', 'ltr');
    expect(screen.getByRole('heading', { level: 1, name: 'Dashboard' })).toBeInTheDocument();
  });

  it('reads the language from ?lang= and keeps it while navigating', async () => {
    const user = userEvent.setup();
    await renderAt('/?lang=ar');
    expect(document.documentElement).toHaveAttribute('dir', 'rtl');

    await user.click(within(primaryNav('التنقل الرئيسي')).getByRole('link', { name: 'القضايا' }));

    expect(screen.getByRole('heading', { level: 1, name: 'القضايا' })).toBeInTheDocument();
    expect(document.documentElement).toHaveAttribute('dir', 'rtl');
  });

  it('falls back to English for an unsupported ?lang= value', async () => {
    await renderAt('/?lang=<script>');
    expect(document.documentElement).toHaveAttribute('lang', 'en');
    expect(screen.getByRole('heading', { level: 1, name: 'Dashboard' })).toBeInTheDocument();
  });
});

describe('preview labelling', () => {
  const routes = [...NAV.map((n) => n.path), '/no-such-page'];

  it.each(routes.flatMap((path) => (['en', 'ar'] as const).map((lang) => [path, lang] as const)))(
    'shows the non-dismissible preview notice on %s in %s',
    async (path, lang) => {
      await renderAt(`${path}?lang=${lang}`);
      const notice = screen.getByRole('note', { name: lang === 'en' ? 'Preview notice' : 'تنبيه المعاينة' });
      expect(notice).toHaveTextContent(BANNER[lang]);
      expect(within(notice).queryByRole('button')).not.toBeInTheDocument();
      expect(document.title).toMatch(lang === 'en' ? / — LawDesk \(Preview\)$/ : / — LawDesk \(معاينة\)$/);
    },
  );

  it.each(PREVIEW_ROUTES)('offers no forms, inputs or save/create/edit/login actions on %s', async (path) => {
    const { container } = await renderAt(path);
    expect(container.querySelector('form, input, textarea, select')).toBeNull();
    const buttons = screen.queryAllByRole('button').map((b) => b.textContent);
    for (const label of buttons) {
      expect(label).not.toMatch(/save|create|new|add|edit|delete|log ?in|sign ?in|submit/i);
    }
  });
});

describe('responsive navigation', () => {
  it('has a menu button that controls the primary navigation and starts collapsed', async () => {
    await renderAt('/');
    const menu = screen.getByRole('button', { name: 'Menu' });
    expect(menu).toHaveAttribute('aria-expanded', 'false');
    const controlled = document.getElementById(menu.getAttribute('aria-controls') ?? '');
    expect(controlled).toBe(primaryNav());
    expect(controlled).toHaveAttribute('data-open', 'false');
  });

  it('expands and collapses with the keyboard', async () => {
    const user = userEvent.setup();
    await renderAt('/');
    const menu = screen.getByRole('button', { name: 'Menu' });

    menu.focus();
    await user.keyboard('{Enter}');
    expect(menu).toHaveAttribute('aria-expanded', 'true');
    expect(primaryNav()).toHaveAttribute('data-open', 'true');

    await user.keyboard(' ');
    expect(menu).toHaveAttribute('aria-expanded', 'false');
    expect(primaryNav()).toHaveAttribute('data-open', 'false');
  });

  it('closes with Escape and returns focus to the menu button', async () => {
    const user = userEvent.setup();
    await renderAt('/');
    const menu = screen.getByRole('button', { name: 'Menu' });
    await user.click(menu);
    await user.click(within(primaryNav()).getByRole('link', { name: 'Hearings' }));
    expect(menu).toHaveAttribute('aria-expanded', 'false');

    await user.click(menu);
    within(primaryNav()).getByRole('link', { name: 'Cases' }).focus();
    await user.keyboard('{Escape}');
    expect(menu).toHaveAttribute('aria-expanded', 'false');
    expect(menu).toHaveFocus();
  });

  it.each([
    { lang: 'en', label: 'Menu' },
    { lang: 'ar', label: 'القائمة' },
  ])('closes with Escape pressed right after opening, focus still on the toggle ($lang, QAD-014)', async ({ lang, label }) => {
    const user = userEvent.setup();
    await renderAt(`/?lang=${lang}`);
    const menu = screen.getByRole('button', { name: label });
    await user.click(menu);
    expect(menu).toHaveFocus();
    expect(menu).toHaveAttribute('aria-expanded', 'true');

    await user.keyboard('{Escape}');
    expect(menu).toHaveAttribute('aria-expanded', 'false');
    expect(document.getElementById(menu.getAttribute('aria-controls') ?? '')).toHaveAttribute('data-open', 'false');
    expect(menu).toHaveFocus();
  });

  it('closes with Escape from elsewhere on the page and returns focus to the toggle (QAD-014)', async () => {
    const user = userEvent.setup();
    await renderAt('/');
    const menu = screen.getByRole('button', { name: 'Menu' });
    await user.click(menu);
    screen.getByRole('main').focus();

    await user.keyboard('{Escape}');
    expect(menu).toHaveAttribute('aria-expanded', 'false');
    expect(menu).toHaveFocus();
  });

  it('leaves focus alone on Escape when the menu is already collapsed (desktop side navigation)', async () => {
    const user = userEvent.setup();
    await renderAt('/');
    const link = within(primaryNav()).getByRole('link', { name: 'Experts' });
    link.focus();
    await user.keyboard('{Escape}');
    expect(link).toHaveFocus();
    expect(screen.getByRole('button', { name: 'Menu' })).toHaveAttribute('aria-expanded', 'false');
  });

  it('collapses after choosing a destination', async () => {
    const user = userEvent.setup();
    await renderAt('/');
    const menu = screen.getByRole('button', { name: 'Menu' });
    await user.click(menu);
    await user.click(within(primaryNav()).getByRole('link', { name: 'Reports' }));
    expect(menu).toHaveAttribute('aria-expanded', 'false');
    expect(screen.getByRole('heading', { level: 1, name: 'Reports' })).toBeInTheDocument();
  });

  it('labels the menu button in Arabic', async () => {
    await renderAt('/?lang=ar');
    expect(screen.getByRole('button', { name: 'القائمة' })).toHaveAttribute('aria-expanded', 'false');
  });
});

describe('coming-soon placeholders', () => {
  it.each(PLACEHOLDERS)('$path says "$en" is not available yet (English)', async ({ path, en }) => {
    await renderAt(path);
    const main = screen.getByRole('main');
    expect(within(main).getByRole('heading', { level: 1, name: en })).toBeInTheDocument();
    expect(within(main).getByText('Coming in a later phase')).toBeInTheDocument();
    expect(main).toHaveTextContent('This feature is not available in this preview.');
    expect(within(primaryNav()).getByRole('link', { name: en })).toHaveAttribute('aria-current', 'page');
    expect(document.title).toBe(`${en} — LawDesk (Preview)`);
  });

  it.each(PLACEHOLDERS)('$path says "$ar" is not available yet (Arabic)', async ({ path, ar }) => {
    await renderAt(`${path}?lang=ar`);
    const main = screen.getByRole('main');
    expect(within(main).getByRole('heading', { level: 1, name: ar })).toBeInTheDocument();
    expect(within(main).getByText('قادمة في مرحلة لاحقة')).toBeInTheDocument();
    expect(main).toHaveTextContent('هذه الميزة غير متاحة في هذه المعاينة.');
  });
});

// QAD-023 / LD-015: the merged case list is a working screen, reachable from the navigation.
describe('working areas', () => {
  it('opens the case list from the navigation and loads cases', async () => {
    const api = fakeApi();
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const path = typeof input === 'string' ? input : input instanceof URL ? input.pathname : input.url;
      if (path.startsWith('/api/v1/cases')) {
        return Promise.resolve(new Response(JSON.stringify({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }));
      }
      return api(input, init);
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    await renderAt('/');

    await user.click(within(primaryNav()).getByRole('link', { name: 'Cases' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Cases' })).toBeInTheDocument();
    expect(screen.queryByText(/not available in this preview/)).not.toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([input]) => typeof input === 'string' && input.startsWith('/api/v1/cases'))).toBe(true);
  });

  it('links courts for users who may view them', async () => {
    await renderAt('/');

    expect(within(primaryNav()).getByRole('link', { name: 'Courts' })).toHaveAttribute('href', '/courts?lang=en');
  });
});
