import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, Outlet, useNavigate } from 'react-router';
import { ApiStatus } from '../components/ApiStatus';
import { LocaleToggle } from '../components/LocaleToggle';
import { PreviewBanner } from '../components/PreviewBanner';
import { useI18n } from '../i18n/I18nContext';
import { withLang } from '../i18n/locale';
import { useAuth } from '../features/auth/AuthContext';
import { visibleNav } from './navigation';

const NAV_ID = 'primary-nav';
const MAIN_ID = 'main-content';

export function AppLayout() {
  const { locale, t } = useI18n();
  const { state, signOut } = useAuth();
  const navigate = useNavigate();
  const me = state.status === 'signedIn' ? state.me : null;
  const navItems = visibleNav(new Set(me?.permissions ?? []));
  const [menuOpen, setMenuOpen] = useState(false);
  const menuButton = useRef<HTMLButtonElement>(null);

  const closeMenu = () => {
    setMenuOpen(false);
  };

  // While the menu is open, Escape closes it from anywhere on the page, including the toggle itself (QAD-014),
  // and focus goes back to the toggle so keyboard users are not left inside a hidden menu.
  useEffect(() => {
    if (!menuOpen) {
      return undefined;
    }
    const onKeyDown = (event: globalThis.KeyboardEvent) => {
      if (event.key === 'Escape') {
        setMenuOpen(false);
        menuButton.current?.focus();
      }
    };
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [menuOpen]);

  return (
    <div className="app">
      <a className="skip-link" href={`#${MAIN_ID}`}>
        {t('shell.skipToContent')}
      </a>
      <header className="app-header">
        <PreviewBanner />
        <div className="app-bar">
          <button
            ref={menuButton}
            type="button"
            className="menu-button"
            aria-expanded={menuOpen}
            aria-controls={NAV_ID}
            onClick={() => {
              setMenuOpen((open) => !open);
            }}
          >
            <span className="menu-icon" aria-hidden="true" />
            {t('shell.menu')}
          </button>
          <p className="app-name">{t('app.name')}</p>
          {me?.permissions.includes('CASE_VIEW') && (
            <form
              className="app-case-search"
              role="search"
              aria-label={t('shell.caseSearch')}
              onSubmit={(event) => {
                event.preventDefault();
                const data = new FormData(event.currentTarget);
                const value = data.get('q');
                const q = typeof value === 'string' ? value.trim() : '';
                const params = new URLSearchParams({ lang: locale });
                if (q !== '') params.set('q', q);
                void navigate(`/cases?${params.toString()}`);
              }}
            >
              <label className="visually-hidden" htmlFor="app-case-search">{t('shell.caseSearch')}</label>
              <input id="app-case-search" name="q" type="search" dir="auto" maxLength={100} placeholder={t('shell.caseSearchPlaceholder')} />
              <button type="submit">{t('shell.search')}</button>
            </form>
          )}
          <ApiStatus />
          <LocaleToggle />
          {me !== null && (
            <div className="user-menu">
              <span className="user-name" title={t('auth.signedInAs', { name: me.displayName })}>
                {me.displayName}
              </span>
              <Link to={withLang('/profile', locale)}>{t('profile.link')}</Link>
              <button type="button" className="link-button" onClick={() => void signOut()}>
                {t('auth.signOut')}
              </button>
            </div>
          )}
        </div>
      </header>
      <div className="app-body">
        <nav
          id={NAV_ID}
          className="app-nav"
          aria-label={t('shell.primaryNav')}
          data-open={menuOpen}
        >
          <div className="nav-brand">
            <span className="nav-brand-mark" aria-hidden="true">
              <svg viewBox="0 0 32 32" focusable="false">
                <path d="M16 4v23M8 9h16M4 9l-3 7h6L4 9Zm24 0-3 7h6l-3-7ZM11 27h10M16 4l-2 3h4l-2-3Z" />
              </svg>
            </span>
            <span>{t('app.name')}</span>
          </div>
          <ul>
            {navItems.map((item) => (
              <li key={item.path}>
                <NavLink to={withLang(item.path, locale)} end onClick={closeMenu}>
                  {t(item.label)}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
        <main id={MAIN_ID} className="app-main" tabIndex={-1}>
          <Outlet />
        </main>
      </div>
      <footer className="app-footer">
        <p>{t('footer.version', { version: __APP_VERSION__ })}</p>
      </footer>
    </div>
  );
}
