import { BrowserRouter, Route, Routes } from 'react-router';
import { ErrorBoundary } from './components/ErrorBoundary';
import { AuthProvider } from './features/auth/AuthProvider';
import { ProfilePage } from './features/auth/ProfilePage';
import { SessionGate } from './features/auth/SessionGate';
import { AuditPage } from './features/admin/AuditPage';
import { NewUserPage } from './features/admin/NewUserPage';
import { RoleDetailsPage } from './features/admin/RoleDetailsPage';
import { RolesPage } from './features/admin/RolesPage';
import { UserDetailsPage } from './features/admin/UserDetailsPage';
import { UsersPage } from './features/admin/UsersPage';
import { CaseCreatePage } from './features/cases/CaseCreatePage';
import { CaseDetailsPage } from './features/cases/CaseDetailsPage';
import { CaseEditPage } from './features/cases/CaseEditPage';
import { CaseListPage } from './features/cases/CaseListPage';
import { CASES_PATH, CASE_ROUTE, EDIT_CASE_ROUTE, NEW_CASE_PATH } from './features/cases/paths';
import { CourtsPage } from './features/courts/CourtsPage';
import { operationsRoutes } from './features/operations/routes';
import { reportsRoutes } from './features/reports/routes';
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
              <Route path={CASE_ROUTE} element={<CaseDetailsPage />} />
              <Route path={EDIT_CASE_ROUTE} element={<CaseEditPage />} />
              {PLANNED_AREAS.filter((area) => area.status === 'planned').map((area) => (
                <Route key={area.path} path={area.path} element={<ComingSoonPage title={area.label} />} />
              ))}
              <Route path="/admin" element={<UsersPage />} />
              <Route path="/admin/users/new" element={<NewUserPage />} />
              <Route path="/admin/users/:id" element={<UserDetailsPage />} />
              <Route path="/admin/roles" element={<RolesPage />} />
              <Route path="/admin/roles/new" element={<RoleDetailsPage />} />
              <Route path="/admin/roles/:id" element={<RoleDetailsPage />} />
              <Route path="/admin/audit" element={<AuditPage />} />
              <Route path="/profile" element={<ProfilePage />} />
              <Route path="/courts" element={<CourtsPage />} />
              {operationsRoutes()}
              {reportsRoutes()}
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
