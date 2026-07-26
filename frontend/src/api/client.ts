const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080/api/v1';

const TOKEN_KEY = 'stayline_access_token';

export function getAccessToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setAccessToken(token: string | null) {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  else localStorage.removeItem(TOKEN_KEY);
}

export class ApiError extends Error {
  status: number;
  subErrors?: string[];

  constructor(status: number, message: string, subErrors?: string[]) {
    super(message);
    this.status = status;
    this.subErrors = subErrors;
  }
}

interface ApiResponse<T> {
  timeStamp?: string;
  data?: T;
  error?: {
    status?: string | number;
    message?: string;
    subErrors?: string[];
  };
}

let refreshing: Promise<boolean> | null = null;

async function tryRefresh(): Promise<boolean> {
  if (!refreshing) {
    refreshing = (async () => {
      try {
        const res = await fetch(`${API_BASE}/auth/refresh`, {
          method: 'POST',
          credentials: 'include',
        });
        if (!res.ok) {
          setAccessToken(null);
          return false;
        }
        const body = (await res.json()) as ApiResponse<{ accessToken: string }>;
        const token = body.data?.accessToken;
        if (token) {
          setAccessToken(token);
          return true;
        }
        return false;
      } catch {
        setAccessToken(null);
        return false;
      } finally {
        refreshing = null;
      }
    })();
  }
  return refreshing;
}

export async function apiFetch<T>(
  path: string,
  options: RequestInit = {},
  retry = true,
): Promise<T> {
  const headers = new Headers(options.headers);
  if (!headers.has('Content-Type') && options.body) {
    headers.set('Content-Type', 'application/json');
  }
  const token = getAccessToken();
  if (token) headers.set('Authorization', `Bearer ${token}`);

  const res = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
    credentials: 'include',
  });

  if (res.status === 401 && retry && !path.startsWith('/auth/')) {
    const ok = await tryRefresh();
    if (ok) return apiFetch<T>(path, options, false);
  }

  if (res.status === 204) {
    return undefined as T;
  }

  const body = (await res.json().catch(() => ({}))) as ApiResponse<T> & T;

  if (!res.ok) {
    const err = (body as ApiResponse<T>).error;
    throw new ApiError(
      res.status,
      err?.message || res.statusText || 'Request failed',
      err?.subErrors,
    );
  }

  if (body && typeof body === 'object' && 'data' in body) {
    return (body as ApiResponse<T>).data as T;
  }
  return body as T;
}

export function decodeJwtPayload(token: string): Record<string, unknown> | null {
  try {
    const part = token.split('.')[1];
    const json = atob(part.replace(/-/g, '+').replace(/_/g, '/'));
    return JSON.parse(json);
  } catch {
    return null;
  }
}
