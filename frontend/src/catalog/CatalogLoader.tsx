import { useEffect } from 'react';
import { useAuth } from '../features/auth/AuthContext';
import { loadCatalog, resetCatalog } from './catalogStore';

/** Loads the administrator-managed dropdown catalogue once the user is signed in (LD-DOC-13); renders nothing. */
export function CatalogLoader() {
  const { state } = useAuth();
  const signedIn = state.status === 'signedIn';
  useEffect(() => {
    if (!signedIn) {
      resetCatalog();
      return undefined;
    }
    const controller = new AbortController();
    void loadCatalog(controller.signal);
    return () => {
      controller.abort();
    };
  }, [signedIn]);
  return null;
}
