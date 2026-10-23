import { useLayoutEffect } from 'react';
import { BrowserRouter, Route, Routes } from 'react-router';
import { ErrorBoundary } from './components/ErrorBoundary';
import { applyDocumentLocale, resolveLocale } from './i18n/locale';
import { AppLayout } from './layout/AppLayout';
import { HomePage } from './pages/HomePage';
import { NotFoundPage } from './pages/NotFoundPage';

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route index element={<HomePage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}

interface AppProps {
  /** BCP 47 language tag; unsupported values fall back to English. */
  locale?: string;
}

export function App({ locale }: AppProps) {
  const resolved = resolveLocale(locale);

  useLayoutEffect(() => {
    applyDocumentLocale(resolved);
  }, [resolved]);

  return (
    <ErrorBoundary>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </ErrorBoundary>
  );
}
