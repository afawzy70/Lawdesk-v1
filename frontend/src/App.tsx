import { BrowserRouter, Route, Routes } from 'react-router';
import { ErrorBoundary } from './components/ErrorBoundary';
import { AuthProvider } from './features/auth/AuthProvider';
import { SessionGate } from './features/auth/SessionGate';
import { CaseCreatePage } from './features/cases/CaseCreatePage';
import { CaseEditPage } from './features/cases/CaseEditPage';
import { CaseListPage } from './features/cases/CaseListPage';
import { CASES_PATH, EDIT_CASE_ROUTE, NEW_CASE_PATH } from './features/cases/paths';
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
              <Route path={CASES_PATH} element={<CaseListPage />} />
              <Route path={NEW_CASE_PATH} element={<CaseCreatePage />} />
              <Route path={EDIT_CASE_ROUTE} element={<CaseEditPage />} />
              {PLANNED_AREAS.filter((area) => area.status === 'planned').map((area) => (
                <Route key={area.path} path={area.path} element={<ComingSoonPage title={area.label} />} />
              ))}
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
