// Token + user state. Access token is stored in sessionStorage (matches the v1 frontend).
// The refresh token is set by the backend as an HttpOnly cookie at /api/auth/refresh — we never see it.

const TOKEN_KEY = 'grc.accessToken';
const USER_KEY  = 'grc.user';

export interface UserInfo {
  userId: number;
  username: string;
  organisationId: number;
  roles: string[];
}

export function getAccessToken(): string | null {
  return sessionStorage.getItem(TOKEN_KEY);
}

export function getUser(): UserInfo | null {
  const raw = sessionStorage.getItem(USER_KEY);
  if (!raw) return null;
  try { return JSON.parse(raw) as UserInfo; } catch { return null; }
}

export function setSession(accessToken: string, user: UserInfo): void {
  sessionStorage.setItem(TOKEN_KEY, accessToken);
  sessionStorage.setItem(USER_KEY, JSON.stringify(user));
}

/** Update only the access token + organisationId — used by the org switcher. */
export function updateAccessTokenAndOrg(accessToken: string, organisationId: number): void {
  sessionStorage.setItem(TOKEN_KEY, accessToken);
  const user = getUser();
  if (user) {
    user.organisationId = organisationId;
    sessionStorage.setItem(USER_KEY, JSON.stringify(user));
  }
}

export function clearSession(): void {
  sessionStorage.removeItem(TOKEN_KEY);
  sessionStorage.removeItem(USER_KEY);
}

export function isAuthenticated(): boolean {
  return !!getAccessToken();
}

/**
 * Role check used by UI gating. Matches Spring Security's `hasAnyRole(...)`
 * semantics: the user has at least one of the roles in the supplied list.
 *
 * Spring Security and the auth backend embed the `ROLE_` prefix on
 * persisted roles (e.g. `ROLE_PLATFORM_ADMIN`) and authority-style
 * permissions without the prefix (e.g. `KB_WRITE`). To keep call-sites
 * clean (`hasAnyRole('PLATFORM_ADMIN', 'KB_WRITE')`), we normalise both
 * sides — strip the prefix from stored roles and from the supplied list,
 * then compare the bare names.
 */
export function hasAnyRole(...roles: string[]): boolean {
  const user = getUser();
  if (!user || !Array.isArray(user.roles)) return false;
  const strip = (s: string) => s.startsWith('ROLE_') ? s.slice(5) : s;
  const wanted = roles.map(strip);
  return user.roles.some(r => wanted.includes(strip(r)));
}
