import { useEffect, useRef, useState } from 'react';
import { NavLink, Outlet } from 'react-router';
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
          <ApiStatus />
          <LocaleToggle />
          {me !== null && (
            <div className="user-menu">
              <span className="user-name" title={t('auth.signedInAs', { name: me.displayName })}>
                {me.displayName}
              </span>
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
