import { lazy, Suspense } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { I18nProvider } from './lib/i18n';
import { ToastProvider } from './lib/toast';
import { ThemeProvider } from './lib/theme';
import { DialogProvider } from './lib/dialog';
// ─── Critical-path pages (sidebar landings + login + dashboard) stay eager. ───
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { SettingsPage } from './pages/SettingsPage';
import { RiskRegisterPage } from './pages/risk/RiskRegisterPage';
import { CyberPage } from './pages/cyber/CyberPage';
import { BcmWorkspacePage } from './pages/bcm/BcmWorkspacePage';
import { PrivacyPage } from './pages/privacy/PrivacyPage';
import { CompliancePage } from './pages/compliance/CompliancePage';
import { TprmPage } from './pages/tprm/TprmPage';
import { IncidentsPage } from './pages/incidents/IncidentsPage';
import { EvidencePage } from './pages/evidence/EvidencePage';

// ─── Secondary pages — lazy-loaded; each becomes its own chunk. ───
const VulnerabilitiesV2Page  = lazy(() => import('./pages/cyber/VulnerabilitiesV2Page').then(m => ({ default: m.VulnerabilitiesV2Page })));
const KbHomePage             = lazy(() => import('./pages/knowledge-base/KbHomePage').then(m => ({ default: m.KbHomePage })));
const KbArticlePage          = lazy(() => import('./pages/knowledge-base/KbArticlePage').then(m => ({ default: m.KbArticlePage })));
const KbEditorPage           = lazy(() => import('./pages/knowledge-base/KbEditorPage').then(m => ({ default: m.KbEditorPage })));
const BcmPage                = lazy(() => import('./pages/bcm/BcmPage').then(m => ({ default: m.BcmPage })));
const RegulatoryLibraryPage  = lazy(() => import('./pages/regulatory/RegulatoryLibraryPage').then(m => ({ default: m.RegulatoryLibraryPage })));
const ControlLibraryPage     = lazy(() => import('./pages/controls/ControlLibraryPage').then(m => ({ default: m.ControlLibraryPage })));
const PosturePage            = lazy(() => import('./pages/compliance/PosturePage').then(m => ({ default: m.PosturePage })));
const BcmDashboardPage       = lazy(() => import('./pages/dashboards/BcmDashboardPage').then(m => ({ default: m.BcmDashboardPage })));
const BcmReadinessPage       = lazy(() => import('./pages/bcm/BcmReadinessPage').then(m => ({ default: m.BcmReadinessPage })));
const BcmReportsPage         = lazy(() => import('./pages/bcm/BcmReportsPage').then(m => ({ default: m.BcmReportsPage })));
const CrisisRoomPage         = lazy(() => import('./pages/bcm/CrisisRoomPage').then(m => ({ default: m.CrisisRoomPage })));
const BiaWizardPage          = lazy(() => import('./pages/bcm/BiaWizardPage').then(m => ({ default: m.BiaWizardPage })));
const BcmRemediationPage     = lazy(() => import('./pages/bcm/BcmRemediationPage').then(m => ({ default: m.BcmRemediationPage })));
const BcpBuilderPage         = lazy(() => import('./pages/bcm/BcpBuilderPage').then(m => ({ default: m.BcpBuilderPage })));
const BcmSettingsPage        = lazy(() => import('./pages/bcm/BcmSettingsPage').then(m => ({ default: m.BcmSettingsPage })));
const BcmComplianceMappingPage = lazy(() => import('./pages/bcm/BcmComplianceMappingPage').then(m => ({ default: m.BcmComplianceMappingPage })));
const BcmLibraryPage         = lazy(() => import('./pages/bcm/BcmLibraryPage').then(m => ({ default: m.BcmLibraryPage })));
const ScmDashboardPage       = lazy(() => import('./pages/dashboards/ScmDashboardPage').then(m => ({ default: m.ScmDashboardPage })));
const ErmDashboardPage       = lazy(() => import('./pages/dashboards/ErmDashboardPage').then(m => ({ default: m.ErmDashboardPage })));
const CyberDashboardPage     = lazy(() => import('./pages/dashboards/CyberDashboardPage').then(m => ({ default: m.CyberDashboardPage })));
const PdplDashboardPage      = lazy(() => import('./pages/dashboards/PdplDashboardPage').then(m => ({ default: m.PdplDashboardPage })));
const BuyerGapPage           = lazy(() => import('./pages/buyergap/BuyerGapPage').then(m => ({ default: m.BuyerGapPage })));
const WorkflowHubPage        = lazy(() => import('./pages/workflow/WorkflowHubPage').then(m => ({ default: m.WorkflowHubPage })));
// ─── Wizards — lazy-loaded so they don't bloat the initial bundle.
//    Each wizard is its own chunk; the first time a user navigates to
//    /workflows/* the relevant ~30 kB chunk is fetched. Reduces initial
//    main bundle by ~280 kB (gzipped) and silences Vite's "chunks > 500 kB"
//    warning that fired on every build.
const RiskIdentificationWizard  = lazy(() => import('./pages/workflow/erm/RiskIdentificationWizard').then(m => ({ default: m.RiskIdentificationWizard })));
const DsrFulfilmentWizard       = lazy(() => import('./pages/workflow/pdpl/DsrFulfilmentWizard').then(m => ({ default: m.DsrFulfilmentWizard })));
const BreachNotificationWizard  = lazy(() => import('./pages/workflow/pdpl/BreachNotificationWizard').then(m => ({ default: m.BreachNotificationWizard })));
const CyberIncidentWizard       = lazy(() => import('./pages/workflow/nca/CyberIncidentWizard').then(m => ({ default: m.CyberIncidentWizard })));
const SupplierOnboardingWizard  = lazy(() => import('./pages/workflow/scm/SupplierOnboardingWizard').then(m => ({ default: m.SupplierOnboardingWizard })));
const RiskTreatmentWizard       = lazy(() => import('./pages/workflow/erm/RiskTreatmentWizard').then(m => ({ default: m.RiskTreatmentWizard })));
const RiskAcceptanceWizard      = lazy(() => import('./pages/workflow/erm/RiskAcceptanceWizard').then(m => ({ default: m.RiskAcceptanceWizard })));
const BcmExerciseWizard         = lazy(() => import('./pages/workflow/bcm/BcmExerciseWizard').then(m => ({ default: m.BcmExerciseWizard })));
const NcaEccAssessmentWizard    = lazy(() => import('./pages/workflow/nca/NcaEccAssessmentWizard').then(m => ({ default: m.NcaEccAssessmentWizard })));
const VendorRiskAssessmentWizard = lazy(() => import('./pages/workflow/scm/VendorRiskAssessmentWizard').then(m => ({ default: m.VendorRiskAssessmentWizard })));
const CloudCccWizard            = lazy(() => import('./pages/workflow/nca/CloudCccWizard').then(m => ({ default: m.CloudCccWizard })));
const VulnerabilityTriageWizard = lazy(() => import('./pages/workflow/nca/VulnerabilityTriageWizard').then(m => ({ default: m.VulnerabilityTriageWizard })));
const SupplierExitPlanWizard    = lazy(() => import('./pages/workflow/scm/SupplierExitPlanWizard').then(m => ({ default: m.SupplierExitPlanWizard })));
const CrossBorderTransferWizard = lazy(() => import('./pages/workflow/pdpl/CrossBorderTransferWizard').then(m => ({ default: m.CrossBorderTransferWizard })));
const ConsentManagementWizard   = lazy(() => import('./pages/workflow/pdpl/ConsentManagementWizard').then(m => ({ default: m.ConsentManagementWizard })));
const RopaWizard                = lazy(() => import('./pages/workflow/pdpl/RopaWizard').then(m => ({ default: m.RopaWizard })));
const IncidentRcaWizard         = lazy(() => import('./pages/workflow/incident/IncidentRcaWizard').then(m => ({ default: m.IncidentRcaWizard })));
const ManagementReviewWizard    = lazy(() => import('./pages/workflow/governance/ManagementReviewWizard').then(m => ({ default: m.ManagementReviewWizard })));
const DigitalSignatureWizard    = lazy(() => import('./pages/workflow/governance/DigitalSignatureWizard').then(m => ({ default: m.DigitalSignatureWizard })));
const ControlTestWizard         = lazy(() => import('./pages/workflow/compliance/ControlTestWizard').then(m => ({ default: m.ControlTestWizard })));
const BoardPackWizard           = lazy(() => import('./pages/workflow/governance/BoardPackWizard').then(m => ({ default: m.BoardPackWizard })));
const AarDetailPage             = lazy(() => import('./pages/bcm/AarDetailPage').then(m => ({ default: m.AarDetailPage })));
const AiSuggestionsPage         = lazy(() => import('./pages/ai/AiSuggestionsPage').then(m => ({ default: m.AiSuggestionsPage })));
// ─── Module landing pages remaining (eager): /scm sidebar landing. ───
import { ScmPage } from './pages/scm/ScmPage';

// ─── Off-critical-path admin/detail/portal/lifecycle pages — lazy. ───
const AdminPage              = lazy(() => import('./pages/admin/AdminPage').then(m => ({ default: m.AdminPage })));
const LicensingPage          = lazy(() => import('./pages/admin/LicensingPage').then(m => ({ default: m.LicensingPage })));
const WorkflowsPage          = lazy(() => import('./pages/workflow/WorkflowsPage').then(m => ({ default: m.WorkflowsPage })));
const ChangesPage            = lazy(() => import('./pages/changes/ChangesPage').then(m => ({ default: m.ChangesPage })));
const EthicsPage             = lazy(() => import('./pages/ethics/EthicsPage').then(m => ({ default: m.EthicsPage })));
const ReportSchedulesPage    = lazy(() => import('./pages/reporting/ReportSchedulesPage').then(m => ({ default: m.ReportSchedulesPage })));
const RiskDetailPage         = lazy(() => import('./pages/risk/RiskDetailPage').then(m => ({ default: m.RiskDetailPage })));
const FindingDetailPage      = lazy(() => import('./pages/detail/FindingDetailPage').then(m => ({ default: m.FindingDetailPage })));
const ControlDetailPage      = lazy(() => import('./pages/detail/ControlDetailPage').then(m => ({ default: m.ControlDetailPage })));
const PolicyDetailPage       = lazy(() => import('./pages/detail/PolicyDetailPage').then(m => ({ default: m.PolicyDetailPage })));
const VendorDetailPage       = lazy(() => import('./pages/detail/VendorDetailPage').then(m => ({ default: m.VendorDetailPage })));
const DpiaDetailPage         = lazy(() => import('./pages/detail/DpiaDetailPage').then(m => ({ default: m.DpiaDetailPage })));
const AuthorityWorkspacePage = lazy(() => import('./pages/authority/AuthorityWorkspacePage').then(m => ({ default: m.AuthorityWorkspacePage })));
const AuthoritiesIndexPage   = lazy(() => import('./pages/authority/AuthoritiesIndexPage').then(m => ({ default: m.AuthoritiesIndexPage })));
const AuditWorkspacePage     = lazy(() => import('./pages/audit/AuditWorkspacePage').then(m => ({ default: m.AuditWorkspacePage })));
const EngagementDetailPage   = lazy(() => import('./pages/audit/EngagementDetailPage').then(m => ({ default: m.EngagementDetailPage })));
const CommunicationsPage     = lazy(() => import('./pages/comms/CommunicationsPage').then(m => ({ default: m.CommunicationsPage })));
const CapaPage               = lazy(() => import('./pages/capa/CapaPage').then(m => ({ default: m.CapaPage })));
const DsrPortalPage          = lazy(() => import('./pages/portal/DsrPortalPage').then(m => ({ default: m.DsrPortalPage })));
const SpeakUpPortalPage      = lazy(() => import('./pages/portal/SpeakUpPortalPage').then(m => ({ default: m.SpeakUpPortalPage })));
const CaseTrackerPage        = lazy(() => import('./pages/portal/CaseTrackerPage').then(m => ({ default: m.CaseTrackerPage })));
const TrustCenterPage        = lazy(() => import('./pages/portal/TrustCenterPage').then(m => ({ default: m.TrustCenterPage })));
const IntakeInboxPage        = lazy(() => import('./pages/intake/IntakeInboxPage').then(m => ({ default: m.IntakeInboxPage })));
const IntakeCatalogPage      = lazy(() => import('./pages/intake/IntakeCatalogPage').then(m => ({ default: m.IntakeCatalogPage })));
const IntakeFormPage         = lazy(() => import('./pages/intake/IntakeFormPage').then(m => ({ default: m.IntakeFormPage })));
const ConnectorsPage         = lazy(() => import('./pages/intake/ConnectorsPage').then(m => ({ default: m.ConnectorsPage })));
const ApprovalInboxPage      = lazy(() => import('./pages/approvals/ApprovalInboxPage').then(m => ({ default: m.ApprovalInboxPage })));
const SignaturesPage         = lazy(() => import('./pages/signatures/SignaturesPage').then(m => ({ default: m.SignaturesPage })));
const AssetRegisterPage      = lazy(() => import('./pages/assets/AssetRegisterPage').then(m => ({ default: m.AssetRegisterPage })));
const BoardPackPage          = lazy(() => import('./pages/board-pack/BoardPackPage').then(m => ({ default: m.BoardPackPage })));
const RegulatoryChangePage   = lazy(() => import('./pages/regulatory-change/RegulatoryChangePage').then(m => ({ default: m.RegulatoryChangePage })));
const VendorPortalPage       = lazy(() => import('./pages/portal/VendorPortalPage').then(m => ({ default: m.VendorPortalPage })));
const NcaEccExportPage       = lazy(() => import('./pages/nca-ecc/NcaEccExportPage').then(m => ({ default: m.NcaEccExportPage })));
const NotificationDispatchPage = lazy(() => import('./pages/notifications/NotificationDispatchPage').then(m => ({ default: m.NotificationDispatchPage })));
const ControlCrosswalkPage   = lazy(() => import('./pages/control-crosswalk/ControlCrosswalkPage').then(m => ({ default: m.ControlCrosswalkPage })));
// ─── Sprint 4 §3.3 — Culled pages (imports removed; folders kept on disk
//    for one cycle so cherry-picks can be reviewed before physical delete):
//    process-mining · ai-governance · production-readiness · mfa · monitoring ·
//    backup-dr · white-label · csv-import.
const BcmDependencyPage      = lazy(() => import('./pages/bcm-dependency/BcmDependencyPage').then(m => ({ default: m.BcmDependencyPage })));
const BcmDependencyGraphPage = lazy(() => import('./pages/bcm/BcmDependencyGraphPage').then(m => ({ default: m.BcmDependencyGraphPage })));
const RopaPage               = lazy(() => import('./pages/ropa/RopaPage').then(m => ({ default: m.RopaPage })));
const EsgPage                = lazy(() => import('./pages/esg/EsgPage').then(m => ({ default: m.EsgPage })));
const NdiPage                = lazy(() => import('./pages/ndi/NdiPage').then(m => ({ default: m.NdiPage })));
const WorkflowVersionsPage   = lazy(() => import('./pages/workflow-versions/WorkflowVersionsPage').then(m => ({ default: m.WorkflowVersionsPage })));
const CrisisManagementPage   = lazy(() => import('./pages/crisis/CrisisManagementPage').then(m => ({ default: m.CrisisManagementPage })));
const FileUploadDemoPage     = lazy(() => import('./pages/file-upload/FileUploadDemoPage').then(m => ({ default: m.FileUploadDemoPage })));
const NotificationChannelsPage = lazy(() => import('./pages/notifications-channels/NotificationChannelsPage').then(m => ({ default: m.NotificationChannelsPage })));
const SsoConfigPage          = lazy(() => import('./pages/sso/SsoConfigPage').then(m => ({ default: m.SsoConfigPage })));
const NafathLoginPage        = lazy(() => import('./pages/sso/NafathLoginPage').then(m => ({ default: m.NafathLoginPage })));
const AttestationCampaignsPage = lazy(() => import('./pages/intake/AttestationCampaignsPage').then(m => ({ default: m.AttestationCampaignsPage })));
const OnboardingPage         = lazy(() => import('./pages/onboarding/OnboardingPage').then(m => ({ default: m.OnboardingPage })));
const FrameworkPacksPage     = lazy(() => import('./pages/framework-packs/FrameworkPacksPage').then(m => ({ default: m.FrameworkPacksPage })));
const PolicyTemplatesPage    = lazy(() => import('./pages/policy-templates/PolicyTemplatesPage').then(m => ({ default: m.PolicyTemplatesPage })));
const PolicyManagementPage   = lazy(() => import('./pages/policy/PolicyManagementPage').then(m => ({ default: m.PolicyManagementPage })));
const RiskLifecyclePage      = lazy(() => import('./pages/risk-lifecycle/RiskLifecyclePage').then(m => ({ default: m.RiskLifecyclePage })));
const AuditLifecyclePage     = lazy(() => import('./pages/audit-lifecycle/AuditLifecyclePage').then(m => ({ default: m.AuditLifecyclePage })));
const PrivacyLifecyclePage   = lazy(() => import('./pages/privacy-lifecycle/PrivacyLifecyclePage').then(m => ({ default: m.PrivacyLifecyclePage })));
const BcmLifecyclePage       = lazy(() => import('./pages/bcm-lifecycle/BcmLifecyclePage').then(m => ({ default: m.BcmLifecyclePage })));
const CyberLifecyclePage     = lazy(() => import('./pages/cyber-lifecycle/CyberLifecyclePage').then(m => ({ default: m.CyberLifecyclePage })));
const LifecycleOverviewPage  = lazy(() => import('./pages/lifecycle-overview/LifecycleOverviewPage').then(m => ({ default: m.LifecycleOverviewPage })));
const NotificationOutboxPage = lazy(() => import('./pages/notification-outbox/NotificationOutboxPage').then(m => ({ default: m.NotificationOutboxPage })));
const PdplExtensionsPage     = lazy(() => import('./pages/pdpl-extensions/PdplExtensionsPage').then(m => ({ default: m.PdplExtensionsPage })));
const RcaPage                = lazy(() => import('./pages/rca/RcaPage').then(m => ({ default: m.RcaPage })));
const RiskScenariosPage      = lazy(() => import('./pages/risk-scenarios/RiskScenariosPage').then(m => ({ default: m.RiskScenariosPage })));
const SupplierResiliencePage = lazy(() => import('./pages/supplier-resilience/SupplierResiliencePage').then(m => ({ default: m.SupplierResiliencePage })));
const EmergingRiskPage       = lazy(() => import('./pages/emerging-risk/EmergingRiskPage').then(m => ({ default: m.EmergingRiskPage })));
const CommitteesPage         = lazy(() => import('./pages/committees/CommitteesPage').then(m => ({ default: m.CommitteesPage })));
const DrPlansPage            = lazy(() => import('./pages/dr-plans/DrPlansPage').then(m => ({ default: m.DrPlansPage })));
const FrameworkControlsPage  = lazy(() => import('./pages/framework-controls/FrameworkControlsPage').then(m => ({ default: m.FrameworkControlsPage })));
const ContractClausesPage    = lazy(() => import('./pages/contract-clauses/ContractClausesPage').then(m => ({ default: m.ContractClausesPage })));
const CyberPdplBridgePage    = lazy(() => import('./pages/cyber-pdpl-bridge/CyberPdplBridgePage').then(m => ({ default: m.CyberPdplBridgePage })));
const EvidenceChainPage      = lazy(() => import('./pages/evidence-chain/EvidenceChainPage').then(m => ({ default: m.EvidenceChainPage })));
const FourthPartyPage        = lazy(() => import('./pages/fourth-party/FourthPartyPage').then(m => ({ default: m.FourthPartyPage })));
const DataSharingPage        = lazy(() => import('./pages/data-sharing/DataSharingPage').then(m => ({ default: m.DataSharingPage })));
const ProcessorAssessmentsPage = lazy(() => import('./pages/processor-assessments/ProcessorAssessmentsPage').then(m => ({ default: m.ProcessorAssessmentsPage })));
const ThirdPartyCyberPage    = lazy(() => import('./pages/third-party-cyber/ThirdPartyCyberPage').then(m => ({ default: m.ThirdPartyCyberPage })));
const ResilienceTestsPage    = lazy(() => import('./pages/resilience-tests/ResilienceTestsPage').then(m => ({ default: m.ResilienceTestsPage })));
import { ErrorBoundary } from './components/ui/ErrorBoundary';

export function App() {
  return (
    <ErrorBoundary>
    <ThemeProvider>
      <I18nProvider>
        <ToastProvider>
          <DialogProvider>
            <BrowserRouter>
            <Suspense fallback={<WizardChunkFallback />}>
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
              <Route path="/bcm/plans"          element={<BcpBuilderPage />} />
              <Route path="/bcm/settings"       element={<BcmSettingsPage />} />
              <Route path="/bcm/compliance"     element={<BcmComplianceMappingPage />} />
              <Route path="/bcm/library"        element={<BcmLibraryPage />} />
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
              <Route path="/buyer-gap"  element={<BuyerGapPage />} />

              {/* Workflow Hub + per-module wizards (Saudi-GRC §7) */}
              <Route path="/workflows"                              element={<WorkflowHubPage />} />
              <Route path="/workflows/erm/risk-identification"       element={<RiskIdentificationWizard />} />
              <Route path="/workflows/pdpl/dsr-fulfilment"           element={<DsrFulfilmentWizard />} />
              <Route path="/workflows/pdpl/breach-notification"      element={<BreachNotificationWizard />} />
              <Route path="/workflows/nca/cyber-incident"            element={<CyberIncidentWizard />} />
              <Route path="/workflows/scm/supplier-onboarding"       element={<SupplierOnboardingWizard />} />
              <Route path="/workflows/erm/risk-treatment"            element={<RiskTreatmentWizard />} />
              <Route path="/workflows/erm/risk-treatment/:riskId"    element={<RiskTreatmentWizard />} />
              <Route path="/workflows/erm/risk-acceptance"           element={<RiskAcceptanceWizard />} />
              <Route path="/workflows/erm/risk-acceptance/:riskId"   element={<RiskAcceptanceWizard />} />
              <Route path="/workflows/bcm/exercise"                  element={<BcmExerciseWizard />} />
              <Route path="/workflows/nca/ecc-assessment"            element={<NcaEccAssessmentWizard />} />
              <Route path="/workflows/scm/vendor-risk-assessment"    element={<VendorRiskAssessmentWizard />} />
              <Route path="/workflows/nca/cloud-ccc"                 element={<CloudCccWizard />} />
              <Route path="/workflows/nca/vulnerability-triage"      element={<VulnerabilityTriageWizard />} />
              <Route path="/workflows/scm/exit-plan"                 element={<SupplierExitPlanWizard />} />
              <Route path="/workflows/pdpl/cross-border"             element={<CrossBorderTransferWizard />} />
              <Route path="/workflows/pdpl/consent"                  element={<ConsentManagementWizard />} />
              <Route path="/workflows/pdpl/ropa"                     element={<RopaWizard />} />
              <Route path="/workflows/incident/rca"                  element={<IncidentRcaWizard />} />
              <Route path="/workflows/governance/management-review"  element={<ManagementReviewWizard />} />
              <Route path="/workflows/governance/digital-signature"  element={<DigitalSignatureWizard />} />
              <Route path="/workflows/governance/board-pack"         element={<BoardPackWizard />} />
              <Route path="/workflows/compliance/control-test"       element={<ControlTestWizard />} />

              {/* Detail + admin pages for V115/V116 modules */}
              <Route path="/bcm/aars/:id"                            element={<AarDetailPage />} />
              <Route path="/ai/suggestions"                          element={<AiSuggestionsPage />} />
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
              <Route path="/bcm-dependency"     element={<BcmDependencyGraphPage />} />
              <Route path="/bcm/dependency"     element={<BcmDependencyGraphPage />} />
              <Route path="/bcm-dependency/legacy" element={<BcmDependencyPage />} />

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
            </Suspense>
            </BrowserRouter>
          </DialogProvider>
        </ToastProvider>
      </I18nProvider>
    </ThemeProvider>
    </ErrorBoundary>
  );
}

/**
 * Tiny placeholder shown while a lazy chunk loads — keeps the route area
 * visually anchored so navigation feels instant. Most chunks are 20-40 kB
 * gzipped so this is rarely on screen for more than ~100 ms on a warm cache.
 */
function WizardChunkFallback() {
  return (
    <div style={{
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      minHeight: '40vh', color: 'var(--ink-3)',
      font: '500 14px var(--font-sans)',
    }} aria-live="polite" aria-busy="true">
      <div style={{
        display: 'inline-flex', alignItems: 'center', gap: 10,
        padding: '8px 14px',
        background: 'var(--surface)',
        border: '1px solid var(--border)',
        borderRadius: 999,
      }}>
        <span className="wizard-chunk-spinner" aria-hidden="true"/>
        <span>Loading…</span>
      </div>
    </div>
  );
}
