import { NavLink, Outlet } from 'react-router';
import { ApiStatus } from '../components/ApiStatus';

export function AppLayout() {
  return (
    <div className="app">
      <header className="app-header">
        <p className="app-name">LawDesk</p>
        <nav aria-label="Primary">
          <ul>
            <li>
              <NavLink to="/" end>
                Home
              </NavLink>
            </li>
          </ul>
        </nav>
        <ApiStatus />
      </header>
      <main className="app-main">
        <Outlet />
      </main>
      <footer className="app-footer">
        <p>LawDesk v{__APP_VERSION__}</p>
      </footer>
    </div>
  );
}
