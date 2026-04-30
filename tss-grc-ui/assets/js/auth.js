/**
 * auth.js — JWT authentication helper for TSS GRC UI.
 *
 * Access token stored in sessionStorage (cleared when tab closes).
 * Refresh token stored as HttpOnly cookie (managed by the browser/server).
 */
const Auth = {

    _TOKEN_KEY: 'grc_jwt',
    _USER_KEY:  'grc_user',

    getToken() {
        return sessionStorage.getItem(this._TOKEN_KEY);
    },

    getUser() {
        try {
            return JSON.parse(sessionStorage.getItem(this._USER_KEY));
        } catch {
            return null;
        }
    },

    setSession(tokenResponse) {
        sessionStorage.setItem(this._TOKEN_KEY, tokenResponse.accessToken);
        sessionStorage.setItem(this._USER_KEY,  JSON.stringify(tokenResponse.user));
    },

    clearSession() {
        sessionStorage.removeItem(this._TOKEN_KEY);
        sessionStorage.removeItem(this._USER_KEY);
    },

    isLoggedIn() {
        return !!this.getToken();
    },

    /**
     * Call on every protected page load.
     * Redirects to login if no valid token found.
     */
    requireAuth() {
        if (!this.isLoggedIn()) {
            const redirect = encodeURIComponent(location.pathname + location.search);
            window.location.replace(`/login.html?redirect=${redirect}`);
        }
    },

    async logout() {
        const base = window.GRC_BACKEND_URL || '';
        try {
            // Tell the server to invalidate the refresh token cookie
            await fetch(base + '/api/auth/logout', {
                method: 'POST',
                headers: { 'Authorization': 'Bearer ' + this.getToken() }
            });
        } catch (_) { /* ignore network errors on logout */ }
        this.clearSession();
        window.location.replace('/login.html');
    },

    /**
     * Try to renew the access token using the refresh token cookie.
     * Returns true if successful, false if the refresh token is also expired.
     */
    async tryRefresh() {
        const base = window.GRC_BACKEND_URL || '';
        try {
            const res = await fetch(base + '/api/auth/refresh', { method: 'POST', credentials: 'include' });
            if (!res.ok) return false;
            const data = await res.json();
            sessionStorage.setItem(this._TOKEN_KEY, data.accessToken);
            return true;
        } catch {
            return false;
        }
    },

    /**
     * Returns true if the current user has the given permission.
     * PLATFORM_ADMIN and ORG_ADMIN are granted all module permissions.
     * @param {string} module  e.g. 'RISK', 'CYBER'
     * @param {string} action  e.g. 'READ', 'WRITE', 'DELETE'
     */
    hasPermission(module, action) {
        const user = this.getUser();
        if (!user) return false;
        // Admins have unrestricted access to all modules
        if (this.hasRole('ROLE_PLATFORM_ADMIN') || this.hasRole('ROLE_ORG_ADMIN')) return true;
        // Fall back to granular permissions array when present
        if (!user.permissions) return false;
        return user.permissions.some(
            p => p.module === module && p.actions.includes(action)
        );
    },

    hasRole(role) {
        const user = this.getUser();
        return user?.roles?.includes(role) ?? false;
    }
};

// Make available globally
window.Auth = Auth;
