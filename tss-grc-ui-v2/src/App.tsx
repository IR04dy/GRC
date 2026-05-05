import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { I18nProvider } from './lib/i18n';
import { ToastProvider } from './lib/toast';
import { ThemeProvider } from './lib/theme';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { SettingsPage } from './pages/SettingsPage';
import { PlaceholderPage } from './pages/PlaceholderPage';
import { RiskRegisterPage } from './pages/risk/RiskRegisterPage';
import { CyberPage } from './pages/cyber/CyberPage';
import { BcmPage } from './pages/bcm/BcmPage';
import { PrivacyPage } from './pages/privacy/PrivacyPage';
import { CompliancePage } from './pages/compliance/CompliancePage';
import { TprmPage } from './pages/tprm/TprmPage';
import { IncidentsPage } from './pages/incidents/IncidentsPage';
import { EvidencePage } from './pages/evidence/EvidencePage';
import { AdminPage } from './pages/admin/AdminPage';
import { ScmPage } from './pages/scm/ScmPage';
import { WorkflowsPage } from './pages/workflow/WorkflowsPage';
import { ChangesPage } from './pages/changes/ChangesPage';
import { EthicsPage } from './pages/ethics/EthicsPage';
import { ReportSchedulesPage } from './pages/reporting/ReportSchedulesPage';
import { RiskDetailPage } from './pages/risk/RiskDetailPage';
import { FindingDetailPage } from './pages/detail/FindingDetailPage';
import { ControlDetailPage } from './pages/detail/ControlDetailPage';
import { PolicyDetailPage } from './pages/detail/PolicyDetailPage';
import { VendorDetailPage } from './pages/detail/VendorDetailPage';
import { DpiaDetailPage } from './pages/detail/DpiaDetailPage';
import { AuthorityWorkspacePage } from './pages/authority/AuthorityWorkspacePage';
import { AuthoritiesIndexPage } from './pages/authority/AuthoritiesIndexPage';
import { AuditWorkspacePage } from './pages/audit/AuditWorkspacePage';
import { EngagementDetailPage } from './pages/audit/EngagementDetailPage';
import { CommunicationsPage } from './pages/comms/CommunicationsPage';
import { CapaPage } from './pages/capa/CapaPage';
import { DsrPortalPage } from './pages/portal/DsrPortalPage';
import { SpeakUpPortalPage } from './pages/portal/SpeakUpPortalPage';
import { CaseTrackerPage } from './pages/portal/CaseTrackerPage';
import { IntakeInboxPage } from './pages/intake/IntakeInboxPage';
import { IntakeCatalogPage } from './pages/intake/IntakeCatalogPage';
import { IntakeFormPage } from './pages/intake/IntakeFormPage';

export function App() {
  return (
    <ThemeProvider>
      <I18nProvider>
        <ToastProvider>
          <BrowserRouter>
            <Routes>
              <Route path="/" element={<Navigate to="/login" replace />} />
              <Route path="/login" element={<LoginPage />} />
              <Route path="/dashboard" element={<DashboardPage />} />
              <Route path="/settings" element={<SettingsPage />} />

              {/* Built modules. */}
              <Route path="/risk"       element={<RiskRegisterPage />} />
              <Route path="/cyber"      element={<CyberPage />} />
              <Route path="/bcm"        element={<BcmPage />} />
              <Route path="/privacy"    element={<PrivacyPage />} />
              <Route path="/pdpl"       element={<Navigate to="/privacy" replace />} />
              <Route path="/compliance" element={<CompliancePage />} />
              <Route path="/tprm"       element={<TprmPage />} />
              <Route path="/incidents"  element={<IncidentsPage />} />
              <Route path="/evidence"   element={<EvidencePage />} />
              <Route path="/admin"      element={<AdminPage />} />
              <Route path="/scm"        element={<ScmPage />} />
              <Route path="/workflows"  element={<WorkflowsPage />} />
              <Route path="/changes"    element={<ChangesPage />} />
              <Route path="/ethics"     element={<EthicsPage />} />
              <Route path="/reports/schedules" element={<ReportSchedulesPage />} />

              {/* Detail-page deep-drill routes (Tier 2) */}
              <Route path="/risk/:id"        element={<RiskDetailPage />} />
              <Route path="/findings/:id"    element={<FindingDetailPage />} />
              <Route path="/controls/:id"    element={<ControlDetailPage />} />
              <Route path="/policies/:id"    element={<PolicyDetailPage />} />
              <Route path="/vendors/:id"     element={<VendorDetailPage />} />
              <Route path="/dpias/:id"       element={<DpiaDetailPage />} />

              {/* Authority workspace (Phase B — auditor pivot view) */}
              <Route path="/authorities"        element={<AuthoritiesIndexPage />} />
              <Route path="/authority/:code"    element={<AuthorityWorkspacePage />} />

              {/* Auditor Workspace (Phase C — auditor's sign-off home) */}
              <Route path="/audit-workspace"    element={<AuditWorkspacePage />} />
              <Route path="/audit-workspace/engagement/:id" element={<EngagementDetailPage />} />

              {/* Communications + Stakeholder workspace (DD-22..26) */}
              <Route path="/communications"     element={<CommunicationsPage />} />

              {/* CAPA register (DD-42) */}
              <Route path="/capa"               element={<CapaPage />} />

              {/* Public-facing intake portals (DD-55..57) — no auth */}
              <Route path="/portal/dsr"         element={<DsrPortalPage />} />
              <Route path="/portal/speakup"     element={<SpeakUpPortalPage />} />
              <Route path="/portal/track"       element={<CaseTrackerPage />} />

              {/* Triager inbox (DD-58) */}
              <Route path="/intake-inbox"       element={<IntakeInboxPage />} />

              {/* Authenticated employee intake catalog + per-kind forms (DD-59) */}
              <Route path="/intake"             element={<IntakeCatalogPage />} />
              <Route path="/intake/:kind"       element={<IntakeFormPage />} />

              {/* Sidebar entries from the broader design vision that have no v1 equivalent — kept as placeholders. */}
              <Route path="/data"   element={<PlaceholderPage moduleKey="modData" />} />
              <Route path="/audit"  element={<PlaceholderPage moduleKey="modAudit" />} />
              <Route path="/policy" element={<PlaceholderPage moduleKey="modPolicy" />} />
              <Route path="/about"  element={<PlaceholderPage moduleKey="itemAbout" />} />

              <Route path="*" element={<Navigate to="/dashboard" replace />} />
            </Routes>
          </BrowserRouter>
        </ToastProvider>
      </I18nProvider>
    </ThemeProvider>
  );
}
