import { type ReactNode, useCallback, useEffect, useMemo, useState } from 'react';
import { ApiError, onUnauthorized } from '../../api/http';
import { AuthContext, type AuthContextValue, type AuthState } from './AuthContext';
import * as auth from './me';

/**
 * Holds the session state of the SPA (LD-007). The session itself is the HttpOnly cookie the browser keeps; the SPA
 * only knows who is signed in from `/api/v1/me`, and stores nothing in browser storage.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: 'loading' });

  const load = useCallback(
    (signal?: AbortSignal) =>
      auth
        .loadCsrfToken()
        .then(() => auth.fetchMe(signal))
        .then(
          (me) => {
            setState({ status: 'signedIn', me });
          },
          (error: unknown) => {
            if (!(error instanceof DOMException && error.name === 'AbortError')) {
              setState({ status: 'signedOut', expired: false });
            }
          },
        ),
    [],
  );

  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal);
    return () => {
      controller.abort();
    };
  }, [load]);

  useEffect(
    () =>
      onUnauthorized(() => {
        setState((current) => (current.status === 'signedIn' ? { status: 'signedOut', expired: true } : current));
      }),
    [],
  );

  const value = useMemo<AuthContextValue>(
    () => ({
      state,
      signIn: async (login, password) => {
        try {
          const me = await auth.signIn(login, password);
          setState({ status: 'signedIn', me });
          return true;
        } catch (error) {
          if (error instanceof ApiError && error.status === 401) {
            return false;
          }
          throw error;
        }
      },
      signOut: async () => {
        try {
          await auth.signOut();
        } finally {
          setState({ status: 'signedOut', expired: false });
          void auth.loadCsrfToken().catch(() => undefined);
        }
      },
      refresh: () => load(),
    }),
    [state, load],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
