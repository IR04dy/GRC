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
