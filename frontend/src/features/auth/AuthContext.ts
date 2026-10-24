import { createContext, useContext } from 'react';
import type { Me } from './me';

export type AuthState =
  | { status: 'loading' }
  | { status: 'signedOut'; expired: boolean }
  | { status: 'signedIn'; me: Me };

export interface AuthContextValue {
  state: AuthState;
  /** Signs in; resolves to false when the server refused the credentials. Other failures reject. */
  signIn: (login: string, password: string) => Promise<boolean>;
  signOut: () => Promise<void>;
  /** Reloads the profile, for example after the password was changed. */
  refresh: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (value === null) {
    throw new Error('useAuth must be used inside <AuthProvider>');
  }
  return value;
}
