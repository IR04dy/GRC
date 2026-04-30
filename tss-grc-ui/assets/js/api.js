/**
 * api.js — Fetch wrapper for TSS GRC REST API calls.
 *
 * Features:
 * - Automatically appends Authorization: Bearer <token>
 * - On 401: attempts one token refresh, then redirects to login
 * - On network error: throws ApiError with helpful message
 * - On 4xx/5xx: throws ApiError with server error body
 */

class ApiError extends Error {
    constructor(status, message, body) {
        super(message);
        this.status = status;
        this.body   = body;
    }
}

const Api = {

    // In standalone mode (UI on a different port than the backend),
    // set window.GRC_BACKEND_URL = 'http://localhost:8084' in a config script.
    // In production (same-origin deployment), leave it unset.
    get BASE_URL() {
        const backend = window.GRC_BACKEND_URL || '';
        return backend + '/api/v1';
    },

    /**
     * Core request method.
     * @param {string} method  HTTP method
     * @param {string} path    Path relative to BASE_URL (e.g. '/risk/risks')
     * @param {*}      body    Optional request body (will be JSON-serialised)
     * @param {object} params  Optional URL query params as key/value object
     */
    async request(method, path, body, params) {
        const baseUrl = this.BASE_URL;
        const url = baseUrl.startsWith('http')
            ? new URL(baseUrl + path)
            : new URL(baseUrl + path, window.location.origin);
        if (params) {
            Object.entries(params).forEach(([k, v]) => {
                if (v !== undefined && v !== null && v !== '') url.searchParams.set(k, v);
            });
        }

        const headers = {};
        if (body) headers['Content-Type'] = 'application/json';

        const token = Auth.getToken();
        if (token) headers['Authorization'] = 'Bearer ' + token;

        let response;
        try {
            response = await fetch(url.toString(), {
                method,
                headers,
                credentials: 'include',  // needed for refresh-token HttpOnly cookie
                body: body != null ? JSON.stringify(body) : undefined
            });
        } catch (networkError) {
            throw new ApiError(0, 'Network error — check connection', null);
        }

        // Token expired — try refresh then retry once
        if (response.status === 401) {
            const refreshed = await Auth.tryRefresh();
            if (refreshed) {
                return this.request(method, path, body, params);
            } else {
                Auth.clearSession();
                window.location.replace('/login.html');
                return;
            }
        }

        if (response.status === 204) return null;

        let responseBody;
        try {
            responseBody = await response.json();
        } catch {
            responseBody = null;
        }

        if (!response.ok) {
            const message = responseBody?.message || responseBody?.error || `HTTP ${response.status}`;
            throw new ApiError(response.status, message, responseBody);
        }

        return responseBody;
    },

    get:    (path, params)       => Api.request('GET',    path, null,  params),
    post:   (path, body)         => Api.request('POST',   path, body),
    put:    (path, body)         => Api.request('PUT',    path, body),
    patch:  (path, body)         => Api.request('PATCH',  path, body),
    delete: (path)               => Api.request('DELETE', path),

    // ── Pagination helper ───────────────────────────────────────────────────
    /**
     * GET a paginated resource.
     * @param {string} path
     * @param {object} params  Must include { page, size, sort? }
     * @returns {Promise<{content: [], totalElements: number, totalPages: number, ...}>}
     */
    page: (path, params) => Api.get(path, params),
};

window.Api      = Api;
window.ApiError = ApiError;
