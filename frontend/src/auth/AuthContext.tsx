import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { authApi, userApi } from '../api';
import { ApiError, decodeJwtPayload, getAccessToken, onAuthFailure } from '../api/client';
import type { Role, UserDto } from '../types';

interface AuthState {
  user: UserDto | null;
  roles: Role[];
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  signup: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  isManager: boolean;
  refreshProfile: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

function rolesFromToken(token: string | null): Role[] {
  if (!token) return [];
  const payload = decodeJwtPayload(token);
  const raw = payload?.roles;
  if (!Array.isArray(raw)) return [];
  return raw.map((r) => {
    if (typeof r === 'string') return r as Role;
    if (r && typeof r === 'object' && 'name' in r) {
      return String((r as { name: string }).name) as Role;
    }
    return String(r) as Role;
  });
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserDto | null>(null);
  const [roles, setRoles] = useState<Role[]>([]);
  const [loading, setLoading] = useState(true);

  const refreshProfile = useCallback(async () => {
    const token = getAccessToken();
    if (!token) {
      setUser(null);
      setRoles([]);
      return;
    }
    setRoles(rolesFromToken(token));
    try {
      const profile = await userApi.profile();
      setUser(profile);
      if (profile.roles?.length) setRoles(profile.roles);
    } catch (e) {
      setUser(null);
      // A 401 here means the token is genuinely dead, so the token-derived roles
      // must go too — otherwise the header shows manager links next to "Log in".
      // Any other failure is treated as transient.
      setRoles(e instanceof ApiError && e.status === 401 ? [] : rolesFromToken(token));
    }
  }, []);

  useEffect(() => {
    refreshProfile().finally(() => setLoading(false));
  }, [refreshProfile]);

  useEffect(
    () =>
      onAuthFailure(() => {
        setUser(null);
        setRoles([]);
      }),
    [],
  );

  const login = useCallback(
    async (email: string, password: string) => {
      await authApi.login(email, password);
      await refreshProfile();
    },
    [refreshProfile],
  );

  const signup = useCallback(async (email: string, password: string) => {
    await authApi.signup(email, password);
    await authApi.login(email, password);
    await refreshProfile();
  }, [refreshProfile]);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      setUser(null);
      setRoles([]);
    }
  }, []);

  const isManager = roles.includes('HOTEL_MANAGER') || roles.includes('ADMIN');

  const value = useMemo(
    () => ({ user, roles, loading, login, signup, logout, isManager, refreshProfile }),
    [user, roles, loading, login, signup, logout, isManager, refreshProfile],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth outside AuthProvider');
  return ctx;
}
