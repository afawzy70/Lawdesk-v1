import { useContext } from 'react';
import type { Permission } from '../../api/permissions';
import { AuthContext } from '../auth/AuthContext';

/** The permissions of the signed-in user (empty without a session). The backend checks every request again. */
export function usePermissions(): ReadonlySet<Permission> {
  const state = useContext(AuthContext)?.state;
  return new Set(state?.status === 'signedIn' ? state.me.permissions : []);
}
