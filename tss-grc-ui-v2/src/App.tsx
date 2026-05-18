import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { I18nProvider } from './lib/i18n';
import { ToastProvider } from './lib/toast';
import { ThemeProvider } from './lib/theme';
import { DialogProvider } from './lib/dialog';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { SettingsPage } from './pages/SettingsPage';
import { RiskRegisterPage } from './pages/risk/RiskRegisterPage';
import { CyberPage } from './pages/cyber/CyberPage';
import { VulnerabilitiesV2Page } from './pages/cyber/VulnerabilitiesV2Page';
import { KbHomePage } from './pages/knowledge-base/KbHomePage';
import { KbArticlePage } from './pages/knowledge-base/KbArticlePage';
import { KbEditorPage } from './pages/knowledge-base/KbEditorPage';
import { BcmPage } from './pages/bcm/BcmPage';
import { BcmWorkspacePage } from './pages/bcm/BcmWorkspacePage';
import { PrivacyPage } from './pages/privacy/PrivacyPage';
import { CompliancePage } from './pages/compliance/CompliancePage';
import { RegulatoryLibraryPage } from './pages/regulatory/RegulatoryLibraryPage';
import { ControlLibraryPage } from './pages/controls/ControlLibraryPage';
import { PosturePage } from './pages/compliance/PosturePage';
import { BcmDashboardPage } from './pages/dashboards/BcmDashboardPage';
import { BcmReadinessPage } from './pages/bcm/BcmReadinessPage';
import { BcmReportsPage } from './pages/bcm/BcmReportsPage';
import { CrisisRoomPage } from './pages/bcm/CrisisRoomPage';
import { BiaWizardPage } from './pages/bcm/BiaWizardPage';
import { BcmRemediationPage } from './pages/bcm/BcmRemediationPage';
import { BcmSettingsPage } from './pages/bcm/BcmSettingsPage';
import { BcmComplianceMappingPage } from './pages/bcm/BcmComplianceMappingPage';
import { ScmDashboardPage } from './pages/dashboards/ScmDashboardPage';
import { ErmDashboardPage } from './pages/dashboards/ErmDashboardPage';
import { CyberDashboardPage } from './pages/dashboards/CyberDashboardPage';
import { PdplDashboardPage } from './pages/dashboards/PdplDashboardPage';
import { TprmPage } from './pages/tprm/TprmPage';
import { IncidentsPage } from './pages/incidents/IncidentsPage';
import { EvidencePage } from './pages/evidence/EvidencePage';
import { AdminPage } from './pages/admin/AdminPage';
import { LicensingPage } from './pages/admin/LicensingPage';
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
import { TrustCenterPage } from './pages/portal/TrustCenterPage';
import { IntakeInboxPage } from './pages/intake/IntakeInboxPage';
import { IntakeCatalogPage } from './pages/intake/IntakeCatalogPage';
import { IntakeFormPage } from './pages/intake/IntakeFormPage';
import { ConnectorsPage } from './pages/intake/ConnectorsPage';
import { ApprovalInboxPage } from './pages/approvals/ApprovalInboxPage';
import { SignaturesPage } from './pages/signatures/SignaturesPage';
import { AssetRegisterPage } from './pages/assets/AssetRegisterPage';
import { BoardPackPage } from './pages/board-pack/BoardPackPage';
import { RegulatoryChangePage } from './pages/regulatory-change/RegulatoryChangePage';
import { VendorPortalPage } from './pages/portal/VendorPortalPage';
import { NcaEccExportPage } from './pages/nca-ecc/NcaEccExportPage';
import { NotificationDispatchPage } from './pages/notifications/NotificationDispatchPage';
import { ControlCrosswalkPage } from './pages/control-crosswalk/ControlCrosswalkPage';
// ─── Sprint 4 §3.3 — Culled pages (imports removed; folders kept on disk
//    for one cycle so cherry-picks can be reviewed before physical delete):
//    process-mining · ai-governance · production-readiness · mfa · monitoring ·
//    backup-dr · white-label · csv-import.
import { BcmDependencyPage } from './pages/bcm-dependency/BcmDependencyPage';
import { RopaPage } from './pages/ropa/RopaPage';
import { EsgPage } from './pages/esg/EsgPage';
import { NdiPage } from './pages/ndi/NdiPage';
import { WorkflowVersionsPage } from './pages/workflow-versions/WorkflowVersionsPage';
import { CrisisManagementPage } from './pages/crisis/CrisisManagementPage';
import { FileUploadDemoPage } from './pages/file-upload/FileUploadDemoPage';
import { NotificationChannelsPage } from './pages/notifications-channels/NotificationChannelsPage';
import { SsoConfigPage } from './pages/sso/SsoConfigPage';
import { NafathLoginPage } from './pages/sso/NafathLoginPage';
import { AttestationCampaignsPage } from './pages/intake/AttestationCampaignsPage';
import { OnboardingPage } from './pages/onboarding/OnboardingPage';
import { FrameworkPacksPage } from './pages/framework-packs/FrameworkPacksPage';
import { PolicyTemplatesPage } from './pages/policy-templates/PolicyTemplatesPage';
import { PolicyManagementPage } from './pages/policy/PolicyManagementPage';
import { RiskLifecyclePage } from './pages/risk-lifecycle/RiskLifecyclePage';
import { AuditLifecyclePage } from './pages/audit-lifecycle/AuditLifecyclePage';
import { PrivacyLifecyclePage } from './pages/privacy-lifecycle/PrivacyLifecyclePage';
import { BcmLifecyclePage } from './pages/bcm-lifecycle/BcmLifecyclePage';
import { CyberLifecyclePage } from './pages/cyber-lifecycle/CyberLifecyclePage';
import { LifecycleOverviewPage } from './pages/lifecycle-overview/LifecycleOverviewPage';
import { NotificationOutboxPage } from './pages/notification-outbox/NotificationOutboxPage';
import { PdplExtensionsPage } from './pages/pdpl-extensions/PdplExtensionsPage';
import { RcaPage } from './pages/rca/RcaPage';
import { RiskScenariosPage } from './pages/risk-scenarios/RiskScenariosPage';
import { SupplierResiliencePage } from './pages/supplier-resilience/SupplierResiliencePage';
import { EmergingRiskPage } from './pages/emerging-risk/EmergingRiskPage';
import { CommitteesPage } from './pages/committees/CommitteesPage';
import { DrPlansPage } from './pages/dr-plans/DrPlansPage';
import { FrameworkControlsPage } from './pages/framework-controls/FrameworkControlsPage';
import { ContractClausesPage } from './pages/contract-clauses/ContractClausesPage';
import { CyberPdplBridgePage } from './pages/cyber-pdpl-bridge/CyberPdplBridgePage';
import { EvidenceChainPage } from './pages/evidence-chain/EvidenceChainPage';
import { FourthPartyPage } from './pages/fourth-party/FourthPartyPage';
import { DataSharingPage } from './pages/data-sharing/DataSharingPage';
import { ProcessorAssessmentsPage } from './pages/processor-assessments/ProcessorAssessmentsPage';
import { ThirdPartyCyberPage } from './pages/third-party-cyber/ThirdPartyCyberPage';
import { ResilienceTestsPage } from './pages/resilience-tests/ResilienceTestsPage';

export function App() {
  return (
    <ThemeProvider>
      <I18nProvider>
        <ToastProvider>
          <DialogProvider>
            <BrowserRouter>
            <Routes>
              <Route path="/" element={<Navigate to="/login" replace />} />
              <Route path="/login" element={<LoginPage />} />
              <Route path="/dashboard" element={<DashboardPage />} />
              <Route path="/settings" element={<SettingsPage />} />

              {/* Built modules. */}
              <Route path="/risk"       element={<RiskRegisterPage />} />
              <Route path="/cyber"            element={<CyberPage />} />
              <Route path="/cyber/vulns/v2"   element={<VulnerabilitiesV2Page />} />

              {/* Sprint 4 §2.9 — Knowledge Base */}
              <Route path="/knowledge-base"             element={<KbHomePage />} />
              <Route path="/knowledge-base/new"         element={<KbEditorPage />} />
              <Route path="/knowledge-base/:slug/edit"  element={<KbEditorPage />} />
              <Route path="/knowledge-base/:slug"       element={<KbArticlePage />} />
              <Route path="/bcm"        element={<BcmWorkspacePage />} />
              <Route path="/bcm/legacy" element={<BcmPage />} />
              <Route path="/privacy"    element={<PrivacyPage />} />
              <Route path="/pdpl"       element={<Navigate to="/privacy" replace />} />
              <Route path="/data-lifecycle"  element={<PdplExtensionsPage />} />
              {/* Legacy route from when this page was called "PDPL extensions". */}
              <Route path="/pdpl-extensions" element={<Navigate to="/data-lifecycle" replace />} />
              <Route path="/compliance" element={<CompliancePage />} />
              <Route path="/regulatory/library" element={<RegulatoryLibraryPage />} />
              <Route path="/controls/library"   element={<ControlLibraryPage />} />
              <Route path="/compliance/posture" element={<PosturePage />} />
              <Route path="/bcm/dashboard"      element={<BcmDashboardPage />} />
              <Route path="/bcm/readiness"      element={<BcmReadinessPage />} />
              <Route path="/bcm/reports"        element={<BcmReportsPage />} />
              <Route path="/bcm/crisis-room"    element={<CrisisRoomPage />} />
              <Route path="/bcm/bia"            element={<BiaWizardPage />} />
              <Route path="/bcm/remediation"    element={<BcmRemediationPage />} />
              <Route path="/bcm/settings"       element={<BcmSettingsPage />} />
              <Route path="/bcm/compliance"     element={<BcmComplianceMappingPage />} />
              <Route path="/scm/dashboard"      element={<ScmDashboardPage />} />
              <Route path="/risk/dashboard"     element={<ErmDashboardPage />} />
              <Route path="/cyber/dashboard"    element={<CyberDashboardPage />} />
              <Route path="/privacy/dashboard"  element={<PdplDashboardPage />} />
              <Route path="/tprm"       element={<TprmPage />} />
              <Route path="/incidents"  element={<IncidentsPage />} />
              <Route path="/rca"        element={<RcaPage />} />
              <Route path="/risk-scenarios"   element={<RiskScenariosPage />} />
              <Route path="/emerging-risk"    element={<EmergingRiskPage />} />
              <Route path="/committees"       element={<CommitteesPage />} />
              <Route path="/dr-plans"         element={<DrPlansPage />} />
              <Route path="/framework-controls" element={<FrameworkControlsPage />} />
              <Route path="/contract-clauses"   element={<ContractClausesPage />} />
              <Route path="/cyber-pdpl-bridge"  element={<CyberPdplBridgePage />} />
              <Route path="/evidence-chain"     element={<EvidenceChainPage />} />
              <Route path="/fourth-party"           element={<FourthPartyPage />} />
              <Route path="/data-sharing"           element={<DataSharingPage />} />
              <Route path="/processor-assessments"  element={<ProcessorAssessmentsPage />} />
              <Route path="/third-party-cyber"      element={<ThirdPartyCyberPage />} />
              <Route path="/resilience-tests"       element={<ResilienceTestsPage />} />
              <Route path="/evidence"   element={<EvidencePage />} />
              <Route path="/admin"      element={<AdminPage />} />
              <Route path="/admin/licensing" element={<LicensingPage />} />
              <Route path="/scm"        element={<ScmPage />} />
              <Route path="/supplier-resilience" element={<SupplierResiliencePage />} />
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
              <Route path="/portal/trust-center" element={<TrustCenterPage />} />

              {/* Vendor self-service portal (Sprint 5) — public */}
              <Route path="/portal/vendor"      element={<VendorPortalPage />} />

              {/* NCA ECC self-assessment export (Sprint 6) */}
              <Route path="/nca-ecc-export"     element={<NcaEccExportPage />} />

              {/* Notification dispatch console (Sprint 6) */}
              <Route path="/notifications/dispatch" element={<NotificationDispatchPage />} />

              {/* Control inheritance / crosswalks (Sprint 6 follow-on B5) */}
              <Route path="/control-crosswalk"  element={<ControlCrosswalkPage />} />

              {/* Sprint 4 §3.3 culled: process-mining, ai-governance,
                  production-readiness, mfa-setup, monitoring, backup-dr,
                  white-label, csv-import. Routes removed; folders kept on
                  disk for one cycle so cherry-picks can be reviewed before
                  physical delete. */}

              {/* BCM dependency map — B4 */}
              <Route path="/bcm-dependency"     element={<BcmDependencyPage />} />

              {/* ROPA + consent management — B2 */}
              <Route path="/ropa"               element={<RopaPage />} />

              {/* ESG / Saudi Green Initiative — C2 */}
              <Route path="/esg"                element={<EsgPage />} />
              <Route path="/ndi"                element={<NdiPage />} />

              {/* Workflow versioning — A3 */}
              <Route path="/workflow-versions"  element={<WorkflowVersionsPage />} />

              {/* Crisis management — C4 */}
              <Route path="/crisis-management"  element={<CrisisManagementPage />} />

              {/* File upload demo */}
              <Route path="/file-upload"        element={<FileUploadDemoPage />} />

              {/* Production hardening — channels */}
              <Route path="/notifications/channels" element={<NotificationChannelsPage />} />

              {/* SSO / SAML / Nafath */}
              <Route path="/sso"                element={<SsoConfigPage />} />
              <Route path="/sso/nafath"         element={<NafathLoginPage />} />

              {/* Onboarding wizard */}
              <Route path="/onboarding"         element={<OnboardingPage />} />

              {/* Framework packs — Track D1 (content moat) */}
              <Route path="/framework-packs"    element={<FrameworkPacksPage />} />

              {/* Policy template library — Track D2 (content moat) */}
              <Route path="/policy-templates"   element={<PolicyTemplatesPage />} />
              <Route path="/policy-management"  element={<PolicyManagementPage />} />
              <Route path="/policy"             element={<Navigate to="/policy-management" replace />} />

              {/* Risk lifecycle — Production hardening (Option 1) */}
              <Route path="/risk-lifecycle"     element={<RiskLifecyclePage />} />

              {/* Audit lifecycle — Production hardening (Option 2) */}
              <Route path="/audit-lifecycle"    element={<AuditLifecyclePage />} />

              {/* Privacy / PDPL lifecycle — Production hardening (Option 3) */}
              <Route path="/privacy-lifecycle"  element={<PrivacyLifecyclePage />} />

              {/* BCM lifecycle — Production hardening (Option 4) */}
              <Route path="/bcm-lifecycle"      element={<BcmLifecyclePage />} />

              {/* Cyber lifecycle — Production hardening (Option 5) */}
              <Route path="/cyber-lifecycle"    element={<CyberLifecyclePage />} />

              {/* Executive lifecycle overview — All 5 pipelines on one screen */}
              <Route path="/lifecycle-overview" element={<LifecycleOverviewPage />} />

              {/* Notification outbox — Phase E infrastructure */}
              <Route path="/notifications/outbox" element={<NotificationOutboxPage />} />

              {/* Triager inbox (DD-58) */}
              <Route path="/intake-inbox"       element={<IntakeInboxPage />} />

              {/* Authenticated employee intake catalog + per-kind forms (DD-59) */}
              <Route path="/intake"             element={<IntakeCatalogPage />} />
              <Route path="/intake/:kind"       element={<IntakeFormPage />} />
              <Route path="/connectors"         element={<ConnectorsPage />} />
              <Route path="/attestations"       element={<AttestationCampaignsPage />} />

              {/* Cross-module approval inbox (Sprint 5) */}
              <Route path="/approvals"          element={<ApprovalInboxPage />} />
              <Route path="/signatures"         element={<SignaturesPage />} />

              {/* Unified asset register / CMDB-lite (Sprint 5) */}
              <Route path="/assets"             element={<AssetRegisterPage />} />

              {/* Executive board pack export (Sprint 5) */}
              <Route path="/board-pack"         element={<BoardPackPage />} />

              {/* Saudi regulatory-change feed (Sprint 5) */}
              <Route path="/regulatory-change"  element={<RegulatoryChangePage />} />

              {/* /data, /audit, /policy, /about were vestigial placeholder
                  routes from an earlier design vision. Removed per user
                  feedback ("no tables tab or page with empty data") — every
                  domain now has a real implementation: /data-lifecycle,
                  /audit-workspace + /audit-lifecycle, /compliance +
                  /policy-templates. The catch-all below sends any lingering
                  link to /dashboard. */}

              <Route path="*" element={<Navigate to="/dashboard" replace />} />
            </Routes>
            </BrowserRouter>
          </DialogProvider>
        </ToastProvider>
      </I18nProvider>
    </ThemeProvider>
  );
}
