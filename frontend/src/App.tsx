import { BrowserRouter, Route, Routes } from 'react-router';
import { ErrorBoundary } from './components/ErrorBoundary';
import { AuthProvider } from './features/auth/AuthProvider';
import { SessionGate } from './features/auth/SessionGate';
import { NewUserPage } from './features/admin/NewUserPage';
import { UserDetailsPage } from './features/admin/UserDetailsPage';
import { UsersPage } from './features/admin/UsersPage';
import { CourtsPage } from './features/courts/CourtsPage';
import { LocaleProvider } from './i18n/LocaleProvider';
import { AppLayout } from './layout/AppLayout';
import { PLANNED_AREAS } from './layout/navigation';
import { ComingSoonPage } from './pages/ComingSoonPage';
import { DashboardPage } from './pages/DashboardPage';
import { NotFoundPage } from './pages/NotFoundPage';

interface LocaleProps {
  /** BCP 47 language tag used when the URL has no `?lang=`; unsupported values fall back to English. */
  locale?: string;
}

export function AppRoutes({ locale }: LocaleProps) {
  return (
    <LocaleProvider defaultLocale={locale}>
      <AuthProvider>
        <SessionGate>
          <Routes>
            <Route element={<AppLayout />}>
              <Route index element={<DashboardPage />} />
              {PLANNED_AREAS.filter((area) => area.status === 'planned').map((area) => (
                <Route key={area.path} path={area.path} element={<ComingSoonPage title={area.label} />} />
              ))}
              <Route path="/admin" element={<UsersPage />} />
              <Route path="/admin/users/new" element={<NewUserPage />} />
              <Route path="/admin/users/:id" element={<UserDetailsPage />} />
              <Route path="/courts" element={<CourtsPage />} />
              <Route path="*" element={<NotFoundPage />} />
            </Route>
          </Routes>
        </SessionGate>
      </AuthProvider>
    </LocaleProvider>
  );
}

export function App({ locale }: LocaleProps) {
  return (
    <ErrorBoundary>
      <BrowserRouter>
        <AppRoutes {...(locale === undefined ? {} : { locale })} />
      </BrowserRouter>
    </ErrorBoundary>
  );
}
