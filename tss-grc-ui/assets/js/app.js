/**
 * app.js — Global shell: renders sidebar + header, wires toggle and notifications.
 *
 * Called on every protected page after auth.js, i18n.js, and api.js are loaded.
 * Renders the #app-shell div with the sidebar and top-header markup,
 * then marks the active nav item based on the current URL path.
 */

const App = {

    NAV_ITEMS: [
        // section: main
        { key: 'nav.dashboard',   href: '/dashboard.html',         icon: 'bi-grid-1x2-fill',      module: null,          section: 'main' },
        // section: modules
        { key: 'nav.risk',        href: '/risk/index.html',        icon: 'bi-shield-exclamation', module: 'RISK',        section: 'modules' },
        { key: 'nav.incidents',   href: '/incidents/index.html',   icon: 'bi-exclamation-octagon',module: 'INCIDENT',    section: 'modules' },
        { key: 'nav.compliance',  href: '/compliance/index.html',  icon: 'bi-check-circle-fill',  module: 'COMPLIANCE',  section: 'modules' },
        { key: 'nav.privacy',     href: '/privacy/index.html',     icon: 'bi-lock-fill',          module: 'PRIVACY',     section: 'modules' },
        { key: 'nav.bcm',         href: '/bcm/index.html',         icon: 'bi-arrow-repeat',       module: 'BCM',         section: 'modules' },
        { key: 'nav.cyber',       href: '/cyber/index.html',       icon: 'bi-bug-fill',           module: 'CYBER',       section: 'modules' },
        { key: 'nav.tprm',        href: '/tprm/index.html',        icon: 'bi-building',           module: 'TPRM',        section: 'modules' },
        { key: 'nav.scm',         href: '/scm/index.html',         icon: 'bi-truck',              module: 'SCM',         section: 'modules' },
        { key: 'nav.evidence',    href: '/evidence/index.html',    icon: 'bi-folder-check',       module: 'EVIDENCE',    section: 'modules' },
        { key: 'nav.policies',    href: '/policies/index.html',    icon: 'bi-file-text-fill',     module: 'POLICIES',    section: 'modules', comingSoon: true },
        { key: 'nav.workflow',    href: '/workflow/tasks.html',    icon: 'bi-diagram-3-fill',     module: 'WORKFLOW',    section: 'modules', comingSoon: true },
        // section: tools
        { key: 'nav.reports',     href: '/reports/index.html',     icon: 'bi-bar-chart-fill',     module: 'REPORTING',   section: 'tools',   comingSoon: true },
        { key: 'nav.training',    href: '/training/index.html',    icon: 'bi-mortarboard-fill',   module: 'TRAINING',    section: 'tools',   comingSoon: true },
        // section: settings / admin
        { key: 'nav.admin',       href: '/admin/users.html',       icon: 'bi-gear-fill',          module: 'ADMIN',       section: 'admin',   adminOnly: true },
    ],

    _notificationPollInterval: null,
    _initialized: false,

    /**
     * Render the full app shell (sidebar + top header) into #app-shell.
     * Call this once per page after Auth.requireAuth() passes.
     */
    init() {
        if (this._initialized) return;
        this._initialized = true;
        const shell = document.getElementById('app-shell');
        if (!shell) return;

        const user = Auth.getUser();
        shell.innerHTML = this._buildShell(user);

        // Move #page-content inside .main-content so it sits within the flex layout
        const pageContent = document.getElementById('page-content');
        const mainContent = shell.querySelector('.main-content');
        if (pageContent && mainContent) {
            mainContent.appendChild(pageContent);
        }

        this._markActiveNav();
        this._wireSidebarToggle();
        this._wireLogout();
        this._wireLanguageSwitcher();
        this._startNotificationPolling();
        // Re-apply translations after shell renders (in case language is Arabic)
        if (typeof I18n !== 'undefined') I18n.applyTranslations();
    },

    _buildShell(user) {
        const isAdmin = Auth.hasRole('ROLE_PLATFORM_ADMIN') || Auth.hasRole('ROLE_ORG_ADMIN');

        const _renderItem = item => {
            const cs = item.comingSoon
                ? `<span style="font-size:.55rem;font-weight:700;background:rgba(255,255,255,.15);color:rgba(255,255,255,.7);border-radius:4px;padding:1px 5px;letter-spacing:.2px;margin-left:auto">SOON</span>`
                : '';
            return `
                <li class="nav-item">
                    <a class="nav-link${item.comingSoon ? ' opacity-60' : ''}" href="${item.href}" data-path="${item.href}"
                       style="${item.comingSoon ? 'pointer-events:none;cursor:default' : ''}">
                        <i class="bi ${item.icon}"></i>
                        <span data-i18n="${item.key}">${I18n.t(item.key)}</span>
                        ${cs}
                    </a>
                </li>`;
        };

        const moduleItems = this.NAV_ITEMS
            .filter(item => item.section === 'modules' && !item.adminOnly)
            .filter(item => {
                if (!item.module) return true;
                if (item.comingSoon) return isAdmin || Auth.hasPermission(item.module, 'READ');
                return Auth.hasPermission(item.module, 'READ');
            })
            .map(_renderItem).join('');

        const toolItems = this.NAV_ITEMS
            .filter(item => item.section === 'tools' && !item.adminOnly)
            .map(_renderItem).join('');

        const adminItem = this.NAV_ITEMS.find(i => i.adminOnly);
        const adminNav  = (adminItem && isAdmin)
            ? `<div class="sidebar-section-title">Settings</div>
               <ul class="nav flex-column">
                   ${_renderItem(adminItem)}
               </ul>` : '';

        const displayName = user?.fullNameEn || user?.username || 'User';
        const initials    = displayName.split(' ').map(w => w[0]).join('').substring(0, 2).toUpperCase();

        return `
        <div class="wrapper d-flex">
            <nav id="sidebar" class="sidebar d-flex flex-column">
                <!-- Brand -->
                <div class="sidebar-header">
                    <div class="sidebar-brand-icon">
                        <i class="bi bi-shield-check" style="color:white;font-size:1.1rem"></i>
                    </div>
                    <span class="sidebar-brand" data-i18n="app.name">${I18n.t('app.name')}</span>
                </div>

                <!-- Nav: main page -->
                <ul class="nav flex-column pt-2">
                    <li class="nav-item">
                        <a class="nav-link" href="/dashboard.html" data-path="/dashboard.html">
                            <i class="bi bi-grid-1x2-fill"></i>
                            <span data-i18n="nav.dashboard">${I18n.t('nav.dashboard')}</span>
                        </a>
                    </li>
                </ul>

                <!-- Nav: modules -->
                <div class="sidebar-section-title">Modules</div>
                <ul class="nav flex-column">${moduleItems}</ul>

                <!-- Nav: tools -->
                ${toolItems ? `<div class="sidebar-section-title">Tools</div><ul class="nav flex-column">${toolItems}</ul>` : ''}

                ${adminNav}

                <!-- Sidebar footer: user info -->
                <div class="sidebar-footer mt-auto">
                    <div class="d-flex align-items-center gap-2">
                        <div class="avatar-initials" style="width:32px;height:32px;font-size:0.65rem">${initials}</div>
                        <div style="min-width:0">
                            <div style="color:white;font-size:0.78rem;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">${displayName}</div>
                            <div style="color:rgba(255,255,255,0.35);font-size:0.65rem">${user?.roles?.[0]?.replace('_', ' ') || ''}</div>
                        </div>
                    </div>
                </div>
            </nav>

            <div class="main-content flex-grow-1 d-flex flex-column">
                <header class="top-header">
                    <button id="sidebarToggle" class="btn btn-link me-3 p-1" style="color:var(--color-text-muted)">
                        <i class="bi bi-list" style="font-size:1.3rem"></i>
                    </button>
                    <h6 class="mb-0 flex-grow-1 fw-600" id="page-title" style="font-size:0.95rem;font-weight:600"></h6>
                    <div class="d-flex align-items-center gap-3">
                        <!-- Language switcher -->
                        <div class="language-switcher d-flex gap-1">
                            <button class="btn btn-sm btn-outline-secondary rounded-pill ${I18n.current === 'en' ? 'active' : ''}"
                                    onclick="I18n.set('en');App._syncLangButtons()">EN</button>
                            <button class="btn btn-sm btn-outline-secondary rounded-pill ${I18n.current === 'ar' ? 'active' : ''}"
                                    onclick="I18n.set('ar');App._syncLangButtons()">عربي</button>
                        </div>
                        <!-- Notification bell -->
                        <div class="position-relative">
                            <button class="btn btn-link p-1" style="color:var(--color-text-muted)" onclick="App._openNotifications()" title="Notifications">
                                <i class="bi bi-bell" style="font-size:1.1rem"></i>
                                <span class="badge bg-danger notification-count" id="notificationCount" style="display:none">0</span>
                            </button>
                        </div>
                        <!-- User menu -->
                        <div class="dropdown">
                            <button class="btn btn-sm d-flex align-items-center gap-2 border rounded-pill px-3"
                                    style="background:var(--color-bg);border-color:var(--color-border)!important"
                                    data-bs-toggle="dropdown">
                                <div class="avatar-initials" style="width:26px;height:26px;font-size:0.6rem">${initials}</div>
                                <span style="font-size:0.82rem;font-weight:500;color:var(--color-text)">${displayName.split(' ')[0]}</span>
                                <i class="bi bi-chevron-down" style="font-size:0.65rem;color:var(--color-text-muted)"></i>
                            </button>
                            <ul class="dropdown-menu dropdown-menu-end shadow-sm border-0 mt-1" style="min-width:180px">
                                <li class="px-3 py-2" style="border-bottom:1px solid var(--color-border)">
                                    <div style="font-size:0.78rem;font-weight:600;color:var(--color-text)">${displayName}</div>
                                    <div style="font-size:0.7rem;color:var(--color-text-muted)">${user?.email || ''}</div>
                                </li>
                                <li><a class="dropdown-item py-2" href="/profile/index.html" style="font-size:0.82rem">
                                    <i class="bi bi-person me-2" style="color:var(--color-accent)"></i>
                                    <span data-i18n="nav.profile">${I18n.t('nav.profile')}</span>
                                </a></li>
                                <li><hr class="dropdown-divider my-1"/></li>
                                <li><button class="dropdown-item py-2 text-danger" id="logoutBtn" style="font-size:0.82rem">
                                    <i class="bi bi-box-arrow-right me-2"></i>
                                    <span data-i18n="nav.logout">${I18n.t('nav.logout')}</span>
                                </button></li>
                            </ul>
                        </div>
                    </div>
                </header>
                <!-- Flash messages container -->
                <div id="flash-messages" class="px-4 pt-2"></div>
            </div>
        </div>`;
    },

    _markActiveNav() {
        const path = location.pathname;
        document.querySelectorAll('.nav-link[data-path]').forEach(link => {
            const linkPath = link.getAttribute('data-path');
            if (path.startsWith(linkPath.replace('index.html', '')) ||
                path === linkPath) {
                link.classList.add('active');
            }
        });
    },

    _wireSidebarToggle() {
        document.getElementById('sidebarToggle')?.addEventListener('click', () => {
            document.getElementById('sidebar')?.classList.toggle('collapsed');
        });
    },

    _wireLogout() {
        document.getElementById('logoutBtn')?.addEventListener('click', () => Auth.logout());
    },

    _wireLanguageSwitcher() {
        document.addEventListener('languageChanged', () => {
            this._syncLangButtons();
            this._markActiveNav();
        });
    },

    _syncLangButtons() {
        document.querySelectorAll('.language-switcher .btn').forEach(btn => {
            btn.classList.toggle('active',
                (btn.textContent.trim() === 'EN' && I18n.current === 'en') ||
                (btn.textContent.trim() !== 'EN' && I18n.current === 'ar'));
        });
    },

    async _startNotificationPolling() {
        await this._fetchNotificationCount();
        this._notificationPollInterval = setInterval(() => this._fetchNotificationCount(), 60_000);
    },

    async _fetchNotificationCount() {
        try {
            const data = await Api.get('/notifications/unread-count');
            const badge = document.getElementById('notificationCount');
            if (badge) {
                badge.textContent = data.count;
                badge.style.display = data.count > 0 ? 'flex' : 'none';
            }
        } catch (_) { /* silent fail */ }
    },

    _openNotifications() {
        window.location.href = '/profile/index.html#notifications';
    },

    /**
     * Show a dismissible flash message in the shell.
     * @param {'success'|'danger'|'warning'|'info'} type
     * @param {string} message
     */
    showFlash(type, message) {
        const container = document.getElementById('flash-messages');
        if (!container) return;
        const id = `flash-${Date.now()}`;
        container.insertAdjacentHTML('beforeend', `
            <div id="${id}" class="alert alert-${type} alert-dismissible fade show" role="alert">
                <span>${message}</span>
                <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
            </div>`);
        setTimeout(() => document.getElementById(id)?.remove(), 5000);
    }
};

// Auto-init after DOM is ready
document.addEventListener('DOMContentLoaded', () => {
    Auth.requireAuth();
    App.init();
});

window.App = App;
