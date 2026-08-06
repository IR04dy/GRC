# 20 - Frontend Architecture (Standalone UI Project)
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


## Overview

The TSS GRC UI is a **standalone static web application** — a separate project from the Spring Boot backend. It consists of plain HTML5, CSS3, and vanilla JavaScript that communicates with the backend exclusively through the REST API documented in [12-api-integration-architecture.md](12-api-integration-architecture.md).

There is **no server-side template engine** (Thymeleaf is removed), **no build step required**, and **no JavaScript framework dependency**. The frontend can be served by any static web server (Nginx, Apache, IIS, or even `npx live-server` in development).

---

## 1. Why Decoupled Frontend

| Concern | Decoupled HTML/CSS/JS | Thymeleaf SSR |
|---|---|---|
| **Team separation** | Frontend and backend developed, deployed, and versioned independently | Tightly coupled — frontend change requires backend redeployment |
| **Hosting flexibility** | Served from CDN, Nginx, or Azure Static Web Apps | Must run inside the Spring Boot JVM |
| **API reusability** | The same REST API serves the web UI, mobile apps, and third-party integrations | API is an afterthought; UI and API are coupled |
| **Cache performance** | Static assets (HTML/CSS/JS) served from CDN edge nodes with long TTLs | Pages generated on every request (unless aggressively cached) |
| **Design iteration** | UI changes deployed without touching the backend | Every template change requires a backend build + deploy cycle |
| **Testing** | UI and API tested independently; contract testing via OpenAPI | Integration tests mix concerns |
| **Security surface** | No template injection attack surface; CSRF not needed for JWT-based auth | CSRF tokens required on every form |

---

## 2. Project Structure

```
tss-grc-ui/                          ← Root of the standalone frontend project
│
├── index.html                       ← Entry point: redirects to login or dashboard
├── login.html                       ← Login page (Arabic / English)
├── dashboard.html                   ← Executive dashboard
│
├── risk/
│   ├── index.html                   ← Risk register list + filters
│   ├── form.html                    ← Create / edit risk
│   └── detail.html                  ← Risk detail, assessments, treatments
│
├── cyber/
│   ├── index.html                   ← Asset inventory / incident list
│   ├── incidents.html
│   └── vulnerabilities.html
│
├── bcm/
│   ├── index.html
│   ├── bia.html
│   └── plans.html
│
├── privacy/
│   ├── ropa.html
│   ├── assessments.html
│   └── breaches.html
│
├── compliance/
│   ├── assessments.html
│   ├── findings.html
│   └── evidence.html
│
├── reports/
│   ├── index.html
│   └── executive.html
│
├── admin/
│   ├── users.html
│   ├── roles.html
│   ├── organisations.html
│   ├── branding.html
│   └── system-health.html
│
├── profile/
│   └── index.html                   ← User profile, password change, MFA setup
│
├── error/
│   ├── 403.html
│   ├── 404.html
│   └── 500.html
│
└── assets/
    ├── css/
    │   ├── style.css                ← Design tokens, layout, components
    │   └── rtl.css                  ← RTL overrides for Arabic
    ├── js/
    │   ├── app.js                   ← Bootstrap init, sidebar toggle, global helpers
    │   ├── api.js                   ← Fetch wrapper (auth headers, error handling)
    │   ├── auth.js                  ← JWT storage, login/logout, route guard
    │   ├── i18n.js                  ← Client-side language / RTL switcher
    │   └── charts.js                ← Chart.js dashboard renderers
    └── images/
        ├── logo-white.svg
        └── logo-dark.svg
```

---

## 3. Authentication Flow (JWT)

The frontend uses **JWT Bearer tokens** stored in `sessionStorage` (cleared when tab closes). `localStorage` is not used to reduce XSS token theft exposure.

```
┌──────────────┐         POST /api/auth/login          ┌─────────────────┐
│  login.html  │  ──────────────────────────────────▶  │  Spring Boot    │
│              │  { username, password }                │  REST API       │
│              │  ◀──────────────────────────────────   │                 │
│              │  { accessToken, refreshToken,          │                 │
│              │    expiresIn, user: {...} }             │                 │
└──────────────┘                                        └─────────────────┘

1. On successful login:
   - sessionStorage.setItem('jwt', accessToken)
   - sessionStorage.setItem('user', JSON.stringify(user))
   - Redirect to dashboard.html

2. Every API call:
   - api.js appends  Authorization: Bearer <token>

3. On 401 response:
   - Clear sessionStorage → redirect to login.html

4. Token refresh (before expiry):
   - POST /api/auth/refresh with refreshToken (HttpOnly cookie)
   - Replace accessToken in sessionStorage
```

### Why Not HttpOnly Cookies for the Access Token

Access tokens are kept in `sessionStorage` (not localStorage) as a compromise:
- Cleared when the browser session ends (tab/window close)
- XSS cannot exfiltrate via `document.cookie`
- The **refresh token** is stored as an HttpOnly cookie (set by the server), never accessible to JavaScript

---

## 4. Page Template Pattern

Every HTML page follows the same self-contained structure — no server-side includes, no template engine. The sidebar and header are rendered by `app.js` from a shared JavaScript template.

```html
<!DOCTYPE html>
<html lang="en" dir="ltr">
<head>
    <meta charset="UTF-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
    <title>Risk Register — TSS GRC</title>

    <!-- Bootstrap 5 (LTR default; app.js swaps to RTL CSS if locale=ar) -->
    <link id="bootstrap-css"
          rel="stylesheet"
          href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css"/>

    <!-- Bootstrap Icons -->
    <link rel="stylesheet"
          href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css"/>

    <!-- Platform CSS -->
    <link rel="stylesheet" href="/assets/css/style.css"/>
    <link rel="stylesheet" href="/assets/css/rtl.css" id="rtl-css" disabled/>

    <!-- Page-specific styles (optional) -->
</head>
<body>

<!-- Shell: sidebar + header injected by app.js -->
<div id="app-shell"></div>

<!-- Page content -->
<main id="page-content" class="flex-grow-1 p-4">
    <!-- Page-specific HTML here -->
</main>

<!-- Bootstrap 5 JS -->
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
<!-- Chart.js 4 -->
<script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.3/dist/chart.umd.min.js"></script>

<!-- Platform JS (order matters) -->
<script src="/assets/js/auth.js"></script>
<script src="/assets/js/i18n.js"></script>
<script src="/assets/js/api.js"></script>
<script src="/assets/js/app.js"></script>

<!-- Page-specific scripts -->
<script src="/assets/js/risk-list.js"></script>

</body>
</html>
```

---

## 5. JavaScript Module Responsibilities

### `auth.js` — Authentication Guard

```javascript
// auth.js
const Auth = {
    getToken()    { return sessionStorage.getItem('jwt'); },
    getUser()     { return JSON.parse(sessionStorage.getItem('user') || 'null'); },
    isLoggedIn()  { return !!this.getToken(); },
    logout() {
        sessionStorage.clear();
        window.location.href = '/login.html';
    },
    // Called on every page load (except login.html)
    requireAuth() {
        if (!this.isLoggedIn()) {
            window.location.href = '/login.html?redirect=' + encodeURIComponent(location.pathname);
        }
    },
    hasPermission(module, action) {
        const user = this.getUser();
        return user?.permissions?.some(p => p.module === module && p.actions.includes(action)) ?? false;
    }
};
```

### `api.js` — Fetch Wrapper

```javascript
// api.js
const Api = {
    BASE: '/api',

    async request(method, path, body) {
        const headers = { 'Content-Type': 'application/json' };
        const token = Auth.getToken();
        if (token) headers['Authorization'] = `Bearer ${token}`;

        const res = await fetch(this.BASE + path, {
            method, headers,
            body: body ? JSON.stringify(body) : undefined
        });

        if (res.status === 401) { Auth.logout(); return; }
        if (!res.ok) throw await res.json();
        return res.status === 204 ? null : res.json();
    },

    get:    (path)        => Api.request('GET',    path),
    post:   (path, body)  => Api.request('POST',   path, body),
    put:    (path, body)  => Api.request('PUT',    path, body),
    delete: (path)        => Api.request('DELETE', path),
};
```

### `i18n.js` — Language & RTL Switcher

```javascript
// i18n.js
const I18n = {
    current: localStorage.getItem('lang') || 'en',

    set(lang) {
        this.current = lang;
        localStorage.setItem('lang', lang);
        document.documentElement.lang = lang;
        document.documentElement.dir  = lang === 'ar' ? 'rtl' : 'ltr';
        document.body.classList.toggle('rtl-layout', lang === 'ar');

        // Swap Bootstrap CSS
        const css = document.getElementById('bootstrap-css');
        css.href = lang === 'ar'
            ? 'https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.rtl.min.css'
            : 'https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css';

        // Toggle RTL stylesheet
        document.getElementById('rtl-css').disabled = lang !== 'ar';

        // Re-render labels from loaded translation bundle
        this.applyTranslations();
    },

    applyTranslations() {
        document.querySelectorAll('[data-i18n]').forEach(el => {
            const key = el.dataset.i18n;
            el.textContent = this.t(key);
        });
    },

    t(key) {
        return TRANSLATIONS[this.current]?.[key] ?? key;
    }
};

// Apply on page load
document.addEventListener('DOMContentLoaded', () => I18n.set(I18n.current));
```

### `app.js` — Shell Renderer & Global Helpers

Renders the sidebar and header HTML dynamically, sets the active nav item based on `location.pathname`, polls the notification count every 60 seconds, and wires the sidebar collapse toggle.

---

## 6. Bilingual / RTL Strategy (Client-Side)

Since there is no server-side rendering, translations are delivered as a JSON file loaded at startup:

```
GET /assets/js/translations.js
```

This file exports a `TRANSLATIONS` object with keys for both `en` and `ar`:

```javascript
const TRANSLATIONS = {
    en: {
        'nav.dashboard': 'Dashboard',
        'risk.register': 'Risk Register',
        // ...
    },
    ar: {
        'nav.dashboard': 'لوحة التحكم',
        'risk.register': 'سجل المخاطر',
        // ...
    }
};
```

HTML elements use `data-i18n` attributes:

```html
<span data-i18n="nav.dashboard">Dashboard</span>
```

`i18n.js` replaces text content on language switch without a page reload.

---

## 7. Development Workflow

### Starting the Frontend

```bash
# Option 1: npx live-server (auto-reloads on file save)
cd tss-grc-ui
npx live-server --port=3000

# Option 2: Python built-in server
python -m http.server 3000

# Option 3: VS Code Live Server extension (right-click index.html → Open with Live Server)
```

### Starting the Backend API

```bash
cd tss-grc-backend
mvn spring-boot:run -Dspring.profiles.active=dev
# API available at http://localhost:8080/api
```

### CORS in Development

`application-dev.yml` configures CORS to allow `http://localhost:3000`. In production, the frontend is served from the same origin as the API (Nginx reverse proxy), so CORS is disabled.

---

## 8. Production Deployment

```
┌─────────────────────────────────────────────────────┐
│                     Nginx                            │
│                                                      │
│   location / {                                       │
│       root /var/www/tss-grc-ui;                     │
│       try_files $uri $uri/ /index.html;             │
│   }                                                  │
│                                                      │
│   location /api/ {                                   │
│       proxy_pass http://localhost:8080;              │
│   }                                                  │
└─────────────────────────────────────────────────────┘
```

Both UI and API are served from the **same domain**, so:
- No CORS issues in production
- Cookies are same-site
- No complex CORS header management

The `tss-grc-ui/` folder is deployed as static files; the Spring Boot JAR runs as a systemd service.

---

## 9. Key Constraints

| Constraint | Decision |
|---|---|
| No build step | Vanilla JS only; no TypeScript, no bundler; ESModules via `<script type="module">` for code organisation |
| No SPA framework | Vanilla JS with lightweight helpers (`api.js`, `auth.js`, `i18n.js`); each page is a self-contained HTML file |
| Bootstrap 5 | LTR / RTL CSS swapped at runtime by `i18n.js` without page reload |
| JWT in sessionStorage | Access token cleared when tab closes; refresh token in HttpOnly cookie |
| Translations as JS | Served as a static JS file; no additional HTTP request needed after first load |
| Arabic fonts | Google Fonts CDN: `Noto Kufi Arabic` loaded in `style.css`; applied via `body.rtl-layout` |
