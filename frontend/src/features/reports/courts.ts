import { useEffect, useState } from 'react';
import { api } from '../../api/http';
import { useAuth } from '../auth/AuthContext';
import type { Court, CourtPage } from '../courts/courtsApi';

/**
 * The courts offered by the report filters: the first 100 by code (the API's page cap), active or not, because
 * reports cover past proceedings too. Needs COURT_VIEW.
 */
export function listReportCourts(signal: AbortSignal): Promise<CourtPage> {
  return api<CourtPage>('/api/v1/courts?page=0&size=100&sort=code,asc', { signal });
}

/** The courts for the court filter, for users who may read them; null while unknown or unavailable. */
export function useReportCourts(): Court[] | null {
  const { state } = useAuth();
  const allowed = state.status === 'signedIn' && state.me.permissions.includes('COURT_VIEW');
  const [courts, setCourts] = useState<Court[] | null>(null);
  useEffect(() => {
    if (!allowed) return;
    const controller = new AbortController();
    listReportCourts(controller.signal).then(
      (page) => {
        if (!controller.signal.aborted && Array.isArray(page.items)) setCourts(page.items);
      },
      () => undefined,
    );
    return () => {
      controller.abort();
    };
  }, [allowed]);
  return allowed ? courts : null;
}
