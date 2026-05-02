import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { I18nProvider } from './lib/i18n';
import { ToastProvider } from './lib/toast';
import { ThemeProvider } from './lib/theme';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { RiskRegisterPage } from './pages/risk/RiskRegisterPage';
import { SettingsPage } from './pages/SettingsPage';
import { PlaceholderPage } from './pages/PlaceholderPage';

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
              <Route path="/risk" element={<RiskRegisterPage />} />
              <Route path="/settings" element={<SettingsPage />} />

              {/* Other modules — placeholder until the design is approved on Risk. */}
              <Route path="/cyber"      element={<PlaceholderPage moduleKey="modCyber" />} />
              <Route path="/bcm"        element={<PlaceholderPage moduleKey="modBcm" />} />
              <Route path="/data"       element={<PlaceholderPage moduleKey="modData" />} />
              <Route path="/pdpl"       element={<PlaceholderPage moduleKey="modPdpl" />} />
              <Route path="/compliance" element={<PlaceholderPage moduleKey="modCompliance" />} />
              <Route path="/audit"      element={<PlaceholderPage moduleKey="modAudit" />} />
              <Route path="/tprm"       element={<PlaceholderPage moduleKey="modThirdParty" />} />
              <Route path="/policy"     element={<PlaceholderPage moduleKey="modPolicy" />} />
              <Route path="/about"      element={<PlaceholderPage moduleKey="itemAbout" />} />

              <Route path="*" element={<Navigate to="/dashboard" replace />} />
            </Routes>
          </BrowserRouter>
        </ToastProvider>
      </I18nProvider>
    </ThemeProvider>
  );
}
