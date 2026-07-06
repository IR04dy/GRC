import { lazy, Suspense } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { I18nProvider } from './lib/i18n';
import { ToastProvider } from './lib/toast';
import { ThemeProvider } from './lib/theme';
import { DialogProvider } from './lib/dialog';
// ─── Critical path: only the login page stays eager (first paint). LH-01/WS13:
//     the module landings used to be eager too, which bundled them all into the
//     initial chunk (~1.8 MB on /login). They are now lazy like every other
//     route (the <Routes> block is already wrapped in <Suspense>), so the
//     initial bundle ships essentially just the login screen. ───
import { LoginPage } from './pages/LoginPage';
const DashboardPage      = lazy(() => import('./pages/DashboardPage').then(m => ({ default: m.DashboardPage })));
const SettingsPage       = lazy(() => import('./pages/SettingsPage').then(m => ({ default: m.SettingsPage })));
const RiskRegisterPage   = lazy(() => import('./pages/risk/RiskRegisterPage').then(m => ({ default: m.RiskRegisterPage })));
const CyberPage          = lazy(() => import('./pages/cyber/CyberPage').then(m => ({ default: m.CyberPage })));
const BcmWorkspacePage   = lazy(() => import('./pages/bcm/BcmWorkspacePage').then(m => ({ default: m.BcmWorkspacePage })));
const PrivacyPage        = lazy(() => import('./pages/privacy/PrivacyPage').then(m => ({ default: m.PrivacyPage })));
const CompliancePage     = lazy(() => import('./pages/compliance/CompliancePage').then(m => ({ default: m.CompliancePage })));
const TprmPage           = lazy(() => import('./pages/tprm/TprmPage').then(m => ({ default: m.TprmPage })));
const IncidentsPage      = lazy(() => import('./pages/incidents/IncidentsPage').then(m => ({ default: m.IncidentsPage })));
const EvidencePage       = lazy(() => import('./pages/evidence/EvidencePage').then(m => ({ default: m.EvidencePage })));

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
const MobileCrisisRoomPage   = lazy(() => import('./pages/bcm/MobileCrisisRoomPage').then(m => ({ default: m.MobileCrisisRoomPage })));
const BiaWizardPage          = lazy(() => import('./pages/bcm/BiaWizardPage').then(m => ({ default: m.BiaWizardPage })));
const QuantitativeBiaPage    = lazy(() => import('./pages/bcm/QuantitativeBiaPage').then(m => ({ default: m.QuantitativeBiaPage })));
const StrategyRoiPage        = lazy(() => import('./pages/bcm/StrategyRoiPage').then(m => ({ default: m.StrategyRoiPage })));
const MtpdInheritancePage    = lazy(() => import('./pages/bcm/MtpdInheritancePage').then(m => ({ default: m.MtpdInheritancePage })));
const MtpdInheritanceDetailPage = lazy(() => import('./pages/bcm/MtpdInheritanceDetailPage').then(m => ({ default: m.MtpdInheritanceDetailPage })));
const VendorBcpCampaignsPage = lazy(() => import('./pages/tprm/VendorBcpCampaignsPage').then(m => ({ default: m.VendorBcpCampaignsPage })));
const VendorBcpCampaignDetailPage = lazy(() => import('./pages/tprm/VendorBcpCampaignDetailPage').then(m => ({ default: m.VendorBcpCampaignDetailPage })));
const VendorAttestationSubmitPage = lazy(() => import('./pages/tprm/VendorAttestationSubmitPage').then(m => ({ default: m.VendorAttestationSubmitPage })));
const OutsourcingRegisterPage = lazy(() => import('./pages/outsourcing/OutsourcingRegisterPage').then(m => ({ default: m.OutsourcingRegisterPage })));
const OutsourcingDetailPage = lazy(() => import('./pages/outsourcing/OutsourcingDetailPage').then(m => ({ default: m.OutsourcingDetailPage })));
const OutsourcingSamaPendingPage = lazy(() => import('./pages/outsourcing/OutsourcingSamaPendingPage').then(m => ({ default: m.OutsourcingSamaPendingPage })));
const ExerciseScenarioLibraryPage = lazy(() => import('./pages/bcm/ExerciseScenarioLibraryPage').then(m => ({ default: m.ExerciseScenarioLibraryPage })));
const ExerciseScenarioDetailPage  = lazy(() => import('./pages/bcm/ExerciseScenarioDetailPage').then(m => ({ default: m.ExerciseScenarioDetailPage })));
const FacilitatorConsolePage      = lazy(() => import('./pages/bcm/FacilitatorConsolePage').then(m => ({ default: m.FacilitatorConsolePage })));
const ParticipantViewPage         = lazy(() => import('./pages/bcm/ParticipantViewPage').then(m => ({ default: m.ParticipantViewPage })));
const ObserverDashboardPage       = lazy(() => import('./pages/bcm/ObserverDashboardPage').then(m => ({ default: m.ObserverDashboardPage })));
const CrisisCommsTemplatesPage      = lazy(() => import('./pages/bcm/CrisisCommsTemplatesPage').then(m => ({ default: m.CrisisCommsTemplatesPage })));
const CrisisCommsTemplateDetailPage = lazy(() => import('./pages/bcm/CrisisCommsTemplateDetailPage').then(m => ({ default: m.CrisisCommsTemplateDetailPage })));
const CrisisCommsDispatchWizard     = lazy(() => import('./pages/bcm/CrisisCommsDispatchWizard').then(m => ({ default: m.CrisisCommsDispatchWizard })));
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
const WorkflowSubmissionsPage = lazy(() => import('./pages/workflow/WorkflowSubmissionsPage').then(m => ({ default: m.WorkflowSubmissionsPage })));
const WorkflowRequestsPage   = lazy(() => import('./pages/workflow/WorkflowRequestsPage').then(m => ({ default: m.WorkflowRequestsPage })));
const WorkflowRequestDetailPage = lazy(() => import('./pages/workflow/WorkflowRequestDetailPage').then(m => ({ default: m.WorkflowRequestDetailPage })));
const PublicWorkflowRequestPage = lazy(() => import('./pages/workflow/PublicWorkflowRequestPage').then(m => ({ default: m.PublicWorkflowRequestPage })));
const CalendarPage           = lazy(() => import('./pages/calendar/CalendarPage').then(m => ({ default: m.CalendarPage })));
const SocOpsDashboardPage    = lazy(() => import('./pages/socops/SocOpsDashboardPage').then(m => ({ default: m.SocOpsDashboardPage })));
const SocOpsEventsPage       = lazy(() => import('./pages/socops/SocOpsEventsPage').then(m => ({ default: m.SocOpsEventsPage })));
const IrPlaybooksPage        = lazy(() => import('./pages/socops/IrPlaybooksPage').then(m => ({ default: m.IrPlaybooksPage })));
const PentestsPage           = lazy(() => import('./pages/socops/PentestsPage').then(m => ({ default: m.PentestsPage })));
const RegulatorExamsPage     = lazy(() => import('./pages/socops/RegulatorExamsPage').then(m => ({ default: m.RegulatorExamsPage })));
const OtAssetsPage           = lazy(() => import('./pages/socops/OtAssetsPage').then(m => ({ default: m.OtAssetsPage })));
const ThreatIntelPage        = lazy(() => import('./pages/socops/ThreatIntelPage').then(m => ({ default: m.ThreatIntelPage })));
const DetectionLibraryPage   = lazy(() => import('./pages/socops/DetectionLibraryPage').then(m => ({ default: m.DetectionLibraryPage })));
const ScannerRunsPage        = lazy(() => import('./pages/socops/ScannerRunsPage').then(m => ({ default: m.ScannerRunsPage })));
const CryptoAssetsPage       = lazy(() => import('./pages/socops/CryptoAssetsPage').then(m => ({ default: m.CryptoAssetsPage })));
const SbomPage               = lazy(() => import('./pages/socops/SbomPage').then(m => ({ default: m.SbomPage })));
const ZtMaturityPage         = lazy(() => import('./pages/socops/ZtMaturityPage').then(m => ({ default: m.ZtMaturityPage })));
const SeasonalOverlaysPage   = lazy(() => import('./pages/socops/SeasonalOverlaysPage').then(m => ({ default: m.SeasonalOverlaysPage })));
const AwarenessPage          = lazy(() => import('./pages/socops/AwarenessPage').then(m => ({ default: m.AwarenessPage })));
const IamJmlPage             = lazy(() => import('./pages/socops/IamJmlPage').then(m => ({ default: m.IamJmlPage })));
const AppsecPage             = lazy(() => import('./pages/socops/AppsecPage').then(m => ({ default: m.AppsecPage })));
const DlpPage                = lazy(() => import('./pages/socops/DlpPage').then(m => ({ default: m.DlpPage })));
const CyberInsurancePage     = lazy(() => import('./pages/socops/CyberInsurancePage').then(m => ({ default: m.CyberInsurancePage })));
const BrandMonitoringPage    = lazy(() => import('./pages/socops/BrandMonitoringPage').then(m => ({ default: m.BrandMonitoringPage })));
const ForensicsPage          = lazy(() => import('./pages/socops/ForensicsPage').then(m => ({ default: m.ForensicsPage })));
const MaDueDiligencePage     = lazy(() => import('./pages/socops/MaDueDiligencePage').then(m => ({ default: m.MaDueDiligencePage })));
const SocOpsDetailPage       = lazy(() => import('./pages/socops/SocOpsDetailPage').then(m => ({ default: m.SocOpsDetailPage })));
const ThreeLinesOfDefensePage = lazy(() => import('./pages/governance/ThreeLinesOfDefensePage').then(m => ({ default: m.ThreeLinesOfDefensePage })));
const NotificationInboxPage  = lazy(() => import('./pages/inbox/InboxPage').then(m => ({ default: m.InboxPage })));
const WorkflowSubmissionDetailPage = lazy(() => import('./pages/workflow/WorkflowSubmissionDetailPage').then(m => ({ default: m.WorkflowSubmissionDetailPage })));
const ApprovalChainTemplatesPage    = lazy(() => import('./pages/workflow/ApprovalChainTemplatesPage').then(m => ({ default: m.ApprovalChainTemplatesPage })));
const ApprovalChainInstanceDetailPage = lazy(() => import('./pages/workflow/ApprovalChainInstanceDetailPage').then(m => ({ default: m.ApprovalChainInstanceDetailPage })));
const ApprovalChainPendingPage       = lazy(() => import('./pages/workflow/ApprovalChainPendingPage').then(m => ({ default: m.ApprovalChainPendingPage })));
// ─── Sprint 13 — Internal Audit Annual Plan + Engagement workflow.
const IaPlanListPage                 = lazy(() => import('./pages/audit-ia/IaPlanListPage').then(m => ({ default: m.IaPlanListPage })));
const IaPlanDetailPage               = lazy(() => import('./pages/audit-ia/IaPlanDetailPage').then(m => ({ default: m.IaPlanDetailPage })));
const IaUniversePage                 = lazy(() => import('./pages/audit-ia/IaUniversePage').then(m => ({ default: m.IaUniversePage })));
const IaEngagementDetailPage         = lazy(() => import('./pages/audit-ia/IaEngagementDetailPage').then(m => ({ default: m.IaEngagementDetailPage })));
// ─── Sprint 13 — Customer Complaints Management (SAMA Consumer Protection).
const ComplaintsRegisterPage         = lazy(() => import('./pages/consumer/ComplaintsRegisterPage').then(m => ({ default: m.ComplaintsRegisterPage })));
const ComplaintDetailPage            = lazy(() => import('./pages/consumer/ComplaintDetailPage').then(m => ({ default: m.ComplaintDetailPage })));
const PublicComplaintSubmissionPage  = lazy(() => import('./pages/consumer/PublicComplaintSubmissionPage').then(m => ({ default: m.PublicComplaintSubmissionPage })));
const PublicComplaintTrackingPage    = lazy(() => import('./pages/consumer/PublicComplaintTrackingPage').then(m => ({ default: m.PublicComplaintTrackingPage })));
// ─── Sprint 13 — Sanctions Screening engine (AML / CFT).
const SanctionsDashboardPage         = lazy(() => import('./pages/aml-sanctions/SanctionsDashboardPage').then(m => ({ default: m.SanctionsDashboardPage })));
const SanctionsListsPage             = lazy(() => import('./pages/aml-sanctions/SanctionsListsPage').then(m => ({ default: m.SanctionsListsPage })));
const SanctionsChecksPage            = lazy(() => import('./pages/aml-sanctions/SanctionsChecksPage').then(m => ({ default: m.SanctionsChecksPage })));
const SanctionsCheckDetailPage       = lazy(() => import('./pages/aml-sanctions/SanctionsCheckDetailPage').then(m => ({ default: m.SanctionsCheckDetailPage })));
const SanctionsListDetailPage        = lazy(() => import('./pages/aml-sanctions/SanctionsListDetailPage').then(m => ({ default: m.SanctionsListDetailPage })));
const SanctionsScreenPage            = lazy(() => import('./pages/aml-sanctions/SanctionsScreenPage').then(m => ({ default: m.SanctionsScreenPage })));
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
const AiModelRegisterPage       = lazy(() => import('./pages/ai/AiModelRegisterPage').then(m => ({ default: m.AiModelRegisterPage })));
const AiModelDetailPage         = lazy(() => import('./pages/ai/AiModelDetailPage').then(m => ({ default: m.AiModelDetailPage })));
const ObligationsPage           = lazy(() => import('./pages/compliance/ObligationsPage').then(m => ({ default: m.ObligationsPage })));
const ObligationDetailPage      = lazy(() => import('./pages/compliance/ObligationDetailPage').then(m => ({ default: m.ObligationDetailPage })));
const ObligationCampaignsPage   = lazy(() => import('./pages/compliance/ObligationCampaignsPage').then(m => ({ default: m.ObligationCampaignsPage })));
// Sprint 11 — Continuous Control Monitoring Engine.
const CcmDashboardPage          = lazy(() => import('./pages/ccm/CcmDashboardPage').then(m => ({ default: m.CcmDashboardPage })));
const CcmTestsPage              = lazy(() => import('./pages/ccm/CcmTestsPage').then(m => ({ default: m.CcmTestsPage })));
const CcmTestDetailPage         = lazy(() => import('./pages/ccm/CcmTestDetailPage').then(m => ({ default: m.CcmTestDetailPage })));
// Sprint 11 — Audit Committee Dashboard (curation slice over every GRC stream).
const AuditCommitteeDashboardPage = lazy(() => import('./pages/audit-committee/AuditCommitteeDashboardPage').then(m => ({ default: m.AuditCommitteeDashboardPage })));
const AuditCommitteeSnapshotPage  = lazy(() => import('./pages/audit-committee/AuditCommitteeSnapshotPage').then(m => ({ default: m.AuditCommitteeSnapshotPage })));
// Sprint 11 — Risk-Control-Finding Pivot (cross-module aggregation slice).
const RiskControlFindingPivotPage = lazy(() => import('./pages/governance/RiskControlFindingPivotPage').then(m => ({ default: m.RiskControlFindingPivotPage })));
// Sprint 11 — Attack Surface Management (ASM).
const AttackSurfacePage         = lazy(() => import('./pages/cyber/AttackSurfacePage').then(m => ({ default: m.AttackSurfacePage })));
const AttackSurfaceAssetDetailPage = lazy(() => import('./pages/cyber/AttackSurfaceAssetDetailPage').then(m => ({ default: m.AttackSurfaceAssetDetailPage })));
// Sprint 11 — Risk-Based Vulnerability SLA engine.
const VulnSlaDashboardPage      = lazy(() => import('./pages/cyber/VulnSlaDashboardPage').then(m => ({ default: m.VulnSlaDashboardPage })));
const VulnSlaPolicyPage         = lazy(() => import('./pages/cyber/VulnSlaPolicyPage').then(m => ({ default: m.VulnSlaPolicyPage })));
// Sprint 11 — PDPL Sub-processor Tracking.
const SubprocessorsPage         = lazy(() => import('./pages/privacy/SubprocessorsPage').then(m => ({ default: m.SubprocessorsPage })));
const SubprocessorDetailPage    = lazy(() => import('./pages/privacy/SubprocessorDetailPage').then(m => ({ default: m.SubprocessorDetailPage })));
const PublicSubprocessorListPage = lazy(() => import('./pages/privacy/PublicSubprocessorListPage').then(m => ({ default: m.PublicSubprocessorListPage })));
// Sprint 13 — KRI engine.
const KriDashboardPage          = lazy(() => import('./pages/risk/KriDashboardPage').then(m => ({ default: m.KriDashboardPage })));
const KriDetailPage             = lazy(() => import('./pages/risk/KriDetailPage').then(m => ({ default: m.KriDetailPage })));
const KriAdminPage              = lazy(() => import('./pages/risk/KriAdminPage').then(m => ({ default: m.KriAdminPage })));
// ─── Module landing pages remaining (eager): /scm sidebar landing. ───
import { ScmPage } from './pages/scm/ScmPage';

// ─── Off-critical-path admin/detail/portal/lifecycle pages — lazy. ───
const AdminPage              = lazy(() => import('./pages/admin/AdminPage').then(m => ({ default: m.AdminPage })));
const LicensingPage          = lazy(() => import('./pages/admin/LicensingPage').then(m => ({ default: m.LicensingPage })));
const HelpTopicsAdminPage    = lazy(() => import('./pages/admin/HelpTopicsAdminPage').then(m => ({ default: m.HelpTopicsAdminPage })));
const ChangesPage            = lazy(() => import('./pages/changes/ChangesPage').then(m => ({ default: m.ChangesPage })));
const EthicsPage             = lazy(() => import('./pages/ethics/EthicsPage').then(m => ({ default: m.EthicsPage })));
// Sprint 13 — Whistleblower / Ethics Hotline.
const WhistleblowerSubmitPublicPage = lazy(() => import('./pages/ethics/WhistleblowerSubmitPublicPage').then(m => ({ default: m.WhistleblowerSubmitPublicPage })));
const WhistleblowerStatusPage = lazy(() => import('./pages/ethics/WhistleblowerStatusPage').then(m => ({ default: m.WhistleblowerStatusPage })));
const WhistleblowerCasesPage = lazy(() => import('./pages/ethics/WhistleblowerCasesPage').then(m => ({ default: m.WhistleblowerCasesPage })));
const WhistleblowerCaseDetailPage = lazy(() => import('./pages/ethics/WhistleblowerCaseDetailPage').then(m => ({ default: m.WhistleblowerCaseDetailPage })));
const WhistleblowerRetaliationPage = lazy(() => import('./pages/ethics/WhistleblowerRetaliationPage').then(m => ({ default: m.WhistleblowerRetaliationPage })));
// Sprint 13 — Conflict of Interest (COI) Disclosure Register.
const CoiRegisterPage         = lazy(() => import('./pages/ethics/coi/CoiRegisterPage').then(m => ({ default: m.CoiRegisterPage })));
const CoiCampaignDetailPage   = lazy(() => import('./pages/ethics/coi/CoiCampaignDetailPage').then(m => ({ default: m.CoiCampaignDetailPage })));
// Sprint 14 — Fraud Risk Management (SAMA Anti-Fraud Framework 2018).
const FraudDashboardPage      = lazy(() => import('./pages/fraud/FraudDashboardPage').then(m => ({ default: m.FraudDashboardPage })));
const FraudCasesPage          = lazy(() => import('./pages/fraud/FraudCasesPage').then(m => ({ default: m.FraudCasesPage })));
const FraudCaseDetailPage     = lazy(() => import('./pages/fraud/FraudCaseDetailPage').then(m => ({ default: m.FraudCaseDetailPage })));
const FraudCaseNewPage        = lazy(() => import('./pages/fraud/FraudCaseNewPage').then(m => ({ default: m.FraudCaseNewPage })));
// Sprint 14 — Operational Resilience self-assessment (SAMA Op-Res 2024).
const OpResDashboardPage         = lazy(() => import('./pages/opres/OpResDashboardPage').then(m => ({ default: m.OpResDashboardPage })));
const OpResAssessmentsPage       = lazy(() => import('./pages/opres/OpResAssessmentsPage').then(m => ({ default: m.OpResAssessmentsPage })));
const OpResAssessmentDetailPage  = lazy(() => import('./pages/opres/OpResAssessmentDetailPage').then(m => ({ default: m.OpResAssessmentDetailPage })));
// Sprint 15 — Regulatory Inspection Workspace.
const RegInspectDashboardPage       = lazy(() => import('./pages/reginspect/RegInspectDashboardPage').then(m => ({ default: m.RegInspectDashboardPage })));
const RegInspectInspectionsPage     = lazy(() => import('./pages/reginspect/RegInspectInspectionsPage').then(m => ({ default: m.RegInspectInspectionsPage })));
const RegInspectWorkspaceDetailPage = lazy(() => import('./pages/reginspect/RegInspectWorkspaceDetailPage').then(m => ({ default: m.RegInspectWorkspaceDetailPage })));
// Sprint 16 — Mandatory Regulatory Reporting Calendar.
const RegReportingDashboardPage    = lazy(() => import('./pages/regreporting/RegReportingDashboardPage').then(m => ({ default: m.RegReportingDashboardPage })));
const RegReportingObligationsPage  = lazy(() => import('./pages/regreporting/RegReportingObligationsPage').then(m => ({ default: m.RegReportingObligationsPage })));
const RegReportingFilingsPage      = lazy(() => import('./pages/regreporting/RegReportingFilingsPage').then(m => ({ default: m.RegReportingFilingsPage })));
const RegReportingFilingDetailPage = lazy(() => import('./pages/regreporting/RegReportingFilingDetailPage').then(m => ({ default: m.RegReportingFilingDetailPage })));
// Sprint 17 — Recovery & Resolution Plan.
const RrpDashboardPage   = lazy(() => import('./pages/rrp/RrpDashboardPage').then(m => ({ default: m.RrpDashboardPage })));
const RrpCyclesPage      = lazy(() => import('./pages/rrp/RrpCyclesPage').then(m => ({ default: m.RrpCyclesPage })));
const RrpCycleDetailPage = lazy(() => import('./pages/rrp/RrpCycleDetailPage').then(m => ({ default: m.RrpCycleDetailPage })));
// Sprint 18 — Counterparty Credit Rating Registry.
const CptyDashboardPage           = lazy(() => import('./pages/cpty/CptyDashboardPage').then(m => ({ default: m.CptyDashboardPage })));
const CptyCounterpartiesPage      = lazy(() => import('./pages/cpty/CptyCounterpartiesPage').then(m => ({ default: m.CptyCounterpartiesPage })));
const CptyCounterpartyDetailPage  = lazy(() => import('./pages/cpty/CptyCounterpartyDetailPage').then(m => ({ default: m.CptyCounterpartyDetailPage })));
// Sprint 19 — SAMA Open Banking Compliance.
const ObDashboardPage   = lazy(() => import('./pages/openbanking/ObDashboardPage').then(m => ({ default: m.ObDashboardPage })));
const ObTppsPage        = lazy(() => import('./pages/openbanking/ObTppsPage').then(m => ({ default: m.ObTppsPage })));
const ObTppDetailPage   = lazy(() => import('./pages/openbanking/ObTppDetailPage').then(m => ({ default: m.ObTppDetailPage })));
const ObConsentsPage    = lazy(() => import('./pages/openbanking/ObConsentsPage').then(m => ({ default: m.ObConsentsPage })));
const ObIncidentsPage   = lazy(() => import('./pages/openbanking/ObIncidentsPage').then(m => ({ default: m.ObIncidentsPage })));
// Sprint 20 — Sharia Governance & Audit Workflow.
const ShariaDashboardPage     = lazy(() => import('./pages/sharia/ShariaDashboardPage').then(m => ({ default: m.ShariaDashboardPage })));
const ShariaScholarsPage      = lazy(() => import('./pages/sharia/ShariaScholarsPage').then(m => ({ default: m.ShariaScholarsPage })));
const ShariaResolutionsPage   = lazy(() => import('./pages/sharia/ShariaResolutionsPage').then(m => ({ default: m.ShariaResolutionsPage })));
const ShariaAuditFindingsPage = lazy(() => import('./pages/sharia/ShariaAuditFindingsPage').then(m => ({ default: m.ShariaAuditFindingsPage })));
const ShariaNcEventsPage      = lazy(() => import('./pages/sharia/ShariaNcEventsPage').then(m => ({ default: m.ShariaNcEventsPage })));
const ShariaProductsPage      = lazy(() => import('./pages/sharia/ShariaProductsPage').then(m => ({ default: m.ShariaProductsPage })));
// Sprint 30 — Outsourcing & Cloud Governance.
const OutsourcingDashboardPage      = lazy(() => import('./pages/outsourcing/OutsourcingDashboardPage').then(m => ({ default: m.OutsourcingDashboardPage })));
const OutsourcingArrangementsPage   = lazy(() => import('./pages/outsourcing/OutsourcingArrangementsPage').then(m => ({ default: m.OutsourcingArrangementsPage })));
const OutsourcingCloudServicesPage  = lazy(() => import('./pages/outsourcing/OutsourcingCloudServicesPage').then(m => ({ default: m.OutsourcingCloudServicesPage })));
// Sprint 31 — AI/ML Model Lifecycle.
const AiLifecycleDashboardPage         = lazy(() => import('./pages/aiLifecycle/AiLifecycleDashboardPage').then(m => ({ default: m.AiLifecycleDashboardPage })));
const AiLifecycleValidationsPage       = lazy(() => import('./pages/aiLifecycle/AiLifecycleValidationsPage').then(m => ({ default: m.AiLifecycleValidationsPage })));
const AiLifecycleRetrainingsPage       = lazy(() => import('./pages/aiLifecycle/AiLifecycleRetrainingsPage').then(m => ({ default: m.AiLifecycleRetrainingsPage })));
const AiLifecycleRecertificationsPage  = lazy(() => import('./pages/aiLifecycle/AiLifecycleRecertificationsPage').then(m => ({ default: m.AiLifecycleRecertificationsPage })));
// Sprint 32 — Data Classification + NDMO.
const DataclassDashboardPage    = lazy(() => import('./pages/dataclass/DataclassDashboardPage').then(m => ({ default: m.DataclassDashboardPage })));
const DataclassAssetsPage       = lazy(() => import('./pages/dataclass/DataclassAssetsPage').then(m => ({ default: m.DataclassAssetsPage })));
const DataclassFlowsPage        = lazy(() => import('./pages/dataclass/DataclassFlowsPage').then(m => ({ default: m.DataclassFlowsPage })));
// Sprint 33 — Saudi Data Officer Toolkit (PDPL + NDMO + SDAIA)
const DataOfficerDashboardPage  = lazy(() => import('./pages/dataOfficer/DataOfficerDashboardPage').then(m => ({ default: m.DataOfficerDashboardPage })));
const DsrPage                   = lazy(() => import('./pages/dataOfficer/DsrPage').then(m => ({ default: m.DsrPage })));
const BreachPage                = lazy(() => import('./pages/dataOfficer/BreachPage').then(m => ({ default: m.BreachPage })));
const ConsentPage               = lazy(() => import('./pages/dataOfficer/OtherPages').then(m => ({ default: m.ConsentPage })));
const DpiaPage                  = lazy(() => import('./pages/dataOfficer/OtherPages').then(m => ({ default: m.DpiaPage })));
const NoticePage                = lazy(() => import('./pages/dataOfficer/OtherPages').then(m => ({ default: m.NoticePage })));
const RetentionPage             = lazy(() => import('./pages/dataOfficer/OtherPages').then(m => ({ default: m.RetentionPage })));
const NdrPage                   = lazy(() => import('./pages/dataOfficer/OtherPages').then(m => ({ default: m.NdrPage })));
const CatalogPage               = lazy(() => import('./pages/dataOfficer/OtherPages').then(m => ({ default: m.CatalogPage })));
const QualityPage               = lazy(() => import('./pages/dataOfficer/OtherPages').then(m => ({ default: m.QualityPage })));
const DoEthicsPage              = lazy(() => import('./pages/dataOfficer/OtherPages').then(m => ({ default: m.EthicsPage })));
const ReportSchedulesPage    = lazy(() => import('./pages/reporting/ReportSchedulesPage').then(m => ({ default: m.ReportSchedulesPage })));
const ReportsCentrePage      = lazy(() => import('./pages/reporting/ReportsCentrePage').then(m => ({ default: m.ReportsCentrePage })));
const RiskDetailPage         = lazy(() => import('./pages/risk/RiskDetailPage').then(m => ({ default: m.RiskDetailPage })));
const FindingDetailPage      = lazy(() => import('./pages/detail/FindingDetailPage').then(m => ({ default: m.FindingDetailPage })));
const ControlDetailPage      = lazy(() => import('./pages/detail/ControlDetailPage').then(m => ({ default: m.ControlDetailPage })));
const PolicyDetailPage       = lazy(() => import('./pages/detail/PolicyDetailPage').then(m => ({ default: m.PolicyDetailPage })));
const VendorDetailPage       = lazy(() => import('./pages/detail/VendorDetailPage').then(m => ({ default: m.VendorDetailPage })));
const DpiaDetailPage         = lazy(() => import('./pages/detail/DpiaDetailPage').then(m => ({ default: m.DpiaDetailPage })));
const ConsentDetailPage      = lazy(() => import('./pages/detail/ConsentDetailPage').then(m => ({ default: m.ConsentDetailPage })));
const DsrDetailPage          = lazy(() => import('./pages/detail/DsrDetailPage').then(m => ({ default: m.DsrDetailPage })));
const PrivacyBreachDetailPage = lazy(() => import('./pages/detail/PrivacyBreachDetailPage').then(m => ({ default: m.PrivacyBreachDetailPage })));
const RopaDetailPage         = lazy(() => import('./pages/detail/RopaDetailPage').then(m => ({ default: m.RopaDetailPage })));
const AuthorityWorkspacePage = lazy(() => import('./pages/authority/AuthorityWorkspacePage').then(m => ({ default: m.AuthorityWorkspacePage })));
const AuthoritiesIndexPage   = lazy(() => import('./pages/authority/AuthoritiesIndexPage').then(m => ({ default: m.AuthoritiesIndexPage })));
const AuditWorkspacePage     = lazy(() => import('./pages/audit/AuditWorkspacePage').then(m => ({ default: m.AuditWorkspacePage })));
const EngagementDetailPage   = lazy(() => import('./pages/audit/EngagementDetailPage').then(m => ({ default: m.EngagementDetailPage })));
const CommunicationsPage     = lazy(() => import('./pages/comms/CommunicationsPage').then(m => ({ default: m.CommunicationsPage })));
const CapaPage               = lazy(() => import('./pages/capa/CapaPage').then(m => ({ default: m.CapaPage })));
const CapaDetailPage         = lazy(() => import('./pages/detail/CapaDetailPage').then(m => ({ default: m.CapaDetailPage })));
const DsrPortalPage          = lazy(() => import('./pages/portal/DsrPortalPage').then(m => ({ default: m.DsrPortalPage })));
const SpeakUpPortalPage      = lazy(() => import('./pages/portal/SpeakUpPortalPage').then(m => ({ default: m.SpeakUpPortalPage })));
const CaseTrackerPage        = lazy(() => import('./pages/portal/CaseTrackerPage').then(m => ({ default: m.CaseTrackerPage })));
const TrustCenterPage        = lazy(() => import('./pages/portal/TrustCenterPage').then(m => ({ default: m.TrustCenterPage })));
const IntakeInboxPage        = lazy(() => import('./pages/intake/IntakeInboxPage').then(m => ({ default: m.IntakeInboxPage })));
const IntakeCatalogPage      = lazy(() => import('./pages/intake/IntakeCatalogPage').then(m => ({ default: m.IntakeCatalogPage })));
const IntakeFormPage         = lazy(() => import('./pages/intake/IntakeFormPage').then(m => ({ default: m.IntakeFormPage })));
const ConnectorsPage         = lazy(() => import('./pages/intake/ConnectorsPage').then(m => ({ default: m.ConnectorsPage })));
const IntegrationConsolePage = lazy(() => import('./pages/intake/IntegrationConsolePage').then(m => ({ default: m.IntegrationConsolePage })));
const AuditLogPage           = lazy(() => import('./pages/platform/AuditLogPage').then(m => ({ default: m.AuditLogPage })));
const IssuesPage             = lazy(() => import('./pages/platform/IssuesPage').then(m => ({ default: m.IssuesPage })));
const RegulatoryHorizonPage  = lazy(() => import('./pages/compliance/RegulatoryHorizonPage').then(m => ({ default: m.RegulatoryHorizonPage })));
const CyberCloudServicesPage = lazy(() => import('./pages/cyber/CyberCloudServicesPage').then(m => ({ default: m.CyberCloudServicesPage })));
const FairCalculatorPage     = lazy(() => import('./pages/risk/FairCalculatorPage').then(m => ({ default: m.FairCalculatorPage })));
const SessionsPage           = lazy(() => import('./pages/account/SessionsPage').then(m => ({ default: m.SessionsPage })));
const ThreatIntelCorrelationPage = lazy(() => import('./pages/cyber/ThreatIntelCorrelationPage').then(m => ({ default: m.ThreatIntelCorrelationPage })));
const DependencyCascadePage   = lazy(() => import('./pages/bcm/DependencyCascadePage').then(m => ({ default: m.DependencyCascadePage })));
const DrTestTrendPage         = lazy(() => import('./pages/bcm/DrTestTrendPage').then(m => ({ default: m.DrTestTrendPage })));
const BcmManagementReviewsPage = lazy(() => import('./pages/bcm/BcmManagementReviewsPage').then(m => ({ default: m.BcmManagementReviewsPage })));
const BcmSiemEventsPage       = lazy(() => import('./pages/bcm/BcmSiemEventsPage').then(m => ({ default: m.BcmSiemEventsPage })));
const AiControlMapperPage     = lazy(() => import('./pages/controls/AiControlMapperPage').then(m => ({ default: m.AiControlMapperPage })));
const DataFlowGraphPage       = lazy(() => import('./pages/privacy/DataFlowGraphPage').then(m => ({ default: m.DataFlowGraphPage })));
const ApprovalInboxPage      = lazy(() => import('./pages/approvals/ApprovalInboxPage').then(m => ({ default: m.ApprovalInboxPage })));
const SignaturesPage         = lazy(() => import('./pages/signatures/SignaturesPage').then(m => ({ default: m.SignaturesPage })));
const AssetRegisterPage      = lazy(() => import('./pages/assets/AssetRegisterPage').then(m => ({ default: m.AssetRegisterPage })));
const BoardPackPage          = lazy(() => import('./pages/board-pack/BoardPackPage').then(m => ({ default: m.BoardPackPage })));
const RegulatoryChangePage   = lazy(() => import('./pages/regulatory-change/RegulatoryChangePage').then(m => ({ default: m.RegulatoryChangePage })));
const VendorPortalPage       = lazy(() => import('./pages/portal/VendorPortalPage').then(m => ({ default: m.VendorPortalPage })));
const NcaEccExportPage       = lazy(() => import('./pages/nca-ecc/NcaEccExportPage').then(m => ({ default: m.NcaEccExportPage })));
const NcaEccFilingsPage      = lazy(() => import('./pages/nca-ecc/NcaEccFilingsPage').then(m => ({ default: m.NcaEccFilingsPage })));
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
const TcfdScenariosPage      = lazy(() => import('./pages/esg/TcfdScenariosPage').then(m => ({ default: m.TcfdScenariosPage })));
const FinancedEmissionsPage  = lazy(() => import('./pages/esg/FinancedEmissionsPage').then(m => ({ default: m.FinancedEmissionsPage })));
const ClimateAnnualReportPage = lazy(() => import('./pages/esg/ClimateAnnualReportPage').then(m => ({ default: m.ClimateAnnualReportPage })));
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
const CommitteesPage              = lazy(() => import('./pages/committees/CommitteesPage').then(m => ({ default: m.CommitteesPage })));
const CommitteeCatalogPage        = lazy(() => import('./pages/committees/CommitteeCatalogPage').then(m => ({ default: m.CommitteeCatalogPage })));
const CommitteeTemplateDetailPage = lazy(() => import('./pages/committees/CommitteeTemplateDetailPage').then(m => ({ default: m.CommitteeTemplateDetailPage })));
const CommitteeFormDetailPage     = lazy(() => import('./pages/committees/CommitteeFormDetailPage').then(m => ({ default: m.CommitteeFormDetailPage })));
const CommitteeMemberRegistryPage = lazy(() => import('./pages/committees/CommitteeMemberRegistryPage').then(m => ({ default: m.CommitteeMemberRegistryPage })));
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
// Sprint 11 P1 GRC — ISO 31010 Bow-Tie analysis pages.
const RiskBowTiePage         = lazy(() => import('./pages/risk/RiskBowTiePage').then(m => ({ default: m.RiskBowTiePage })));
const RiskBowTieDetailPage   = lazy(() => import('./pages/risk/RiskBowTieDetailPage').then(m => ({ default: m.RiskBowTieDetailPage })));
// GRC Expert — SME surface linking framework controls to evidence lifecycle.
const GrcExpertPage          = lazy(() => import('./pages/expert/GrcExpertPage').then(m => ({ default: m.GrcExpertPage })));
import { ErrorBoundary } from './components/ui/ErrorBoundary';
import { AppLayout } from './components/layout/AppLayout';

export function App() {
  return (
    <ErrorBoundary>
    <ThemeProvider>
      <I18nProvider>
        <ToastProvider>
          <DialogProvider>
            <BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
            <Suspense fallback={<WizardChunkFallback />}>
            <Routes>
              {/* Single persistent shell. AppLayout mounts the sidebar once and
                  renders each page through <Outlet/>; it drops the chrome for
                  public/full-screen routes (see isBarePath). */}
              <Route element={<AppLayout />}>
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
              {/* Sprint 11 — Obligation register + periodic attestation. */}
              <Route path="/compliance/obligations"                element={<ObligationsPage />} />
              <Route path="/compliance/obligations/:id"            element={<ObligationDetailPage />} />
              <Route path="/compliance/obligation-campaigns"       element={<ObligationCampaignsPage />} />
              <Route path="/compliance/obligation-campaigns/:id"   element={<ObligationCampaignsPage />} />
              <Route path="/bcm/dashboard"      element={<BcmDashboardPage />} />
              <Route path="/bcm/readiness"      element={<BcmReadinessPage />} />
              <Route path="/bcm/reports"        element={<BcmReportsPage />} />
              <Route path="/bcm/crisis-room"    element={<CrisisRoomPage />} />
              {/* Slice 9-F — mobile-optimised Crisis Room for the Crisis Commander on a phone. */}
              <Route path="/crisis/mobile"      element={<MobileCrisisRoomPage />} />
              <Route path="/bcm/crisis/mobile"  element={<MobileCrisisRoomPage />} />
              <Route path="/bcm/bia"            element={<BiaWizardPage />} />
              <Route path="/bcm/bia-quantitative" element={<QuantitativeBiaPage />} />
              <Route path="/bcm/strategy-roi"     element={<StrategyRoiPage />} />
              {/* Slice 10-E — Vendor MTPD inheritance check. */}
              <Route path="/bcm/mtpd-inheritance"          element={<MtpdInheritancePage />} />
              <Route path="/bcm/mtpd-inheritance/:assessmentId" element={<MtpdInheritanceDetailPage />} />
              <Route path="/bcm/scenarios"        element={<ExerciseScenarioLibraryPage />} />
              <Route path="/bcm/scenarios/:id"    element={<ExerciseScenarioDetailPage />} />
              <Route path="/bcm/exercises/run/:runId/facilitate"  element={<FacilitatorConsolePage />} />
              <Route path="/bcm/exercises/run/:runId/participate" element={<ParticipantViewPage />} />
              {/* Slice 10-C — read-only Observer Dashboard for auditors + CISO + regulator liaisons. */}
              <Route path="/bcm/exercises/run/:runId/observe"     element={<ObserverDashboardPage />} />
              <Route path="/bcm/comms/templates"        element={<CrisisCommsTemplatesPage />} />
              <Route path="/bcm/comms/templates/:id"    element={<CrisisCommsTemplateDetailPage />} />
              <Route path="/bcm/comms/dispatch"         element={<CrisisCommsDispatchWizard />} />
              <Route path="/bcm/remediation"    element={<BcmRemediationPage />} />
              <Route path="/bcm/plans"          element={<BcpBuilderPage />} />
              <Route path="/bcm/settings"       element={<BcmSettingsPage />} />
              <Route path="/bcm/compliance"     element={<BcmComplianceMappingPage />} />
              <Route path="/bcm/library"        element={<BcmLibraryPage />} />
              <Route path="/scm/dashboard"      element={<ScmDashboardPage />} />
              <Route path="/risk/dashboard"     element={<ErmDashboardPage />} />
              <Route path="/cyber/dashboard"    element={<CyberDashboardPage />} />
              <Route path="/privacy/dashboard"  element={<PdplDashboardPage />} />
              <Route path="/tprm"                    element={<TprmPage />} />
              {/* Slice 10-D — Vendor BCP attestation campaigns. */}
              <Route path="/tprm/bcp-campaigns"        element={<VendorBcpCampaignsPage />} />
              <Route path="/tprm/bcp-campaigns/:id"    element={<VendorBcpCampaignDetailPage />} />
              <Route path="/tprm/outsourcing"                  element={<OutsourcingRegisterPage />} />
              <Route path="/tprm/outsourcing/admin/sama-pending" element={<OutsourcingSamaPendingPage />} />
              <Route path="/tprm/outsourcing/:id"              element={<OutsourcingDetailPage />} />
              {/* Public vendor-portal stub — token IS the credential. */}
              <Route path="/tprm/attestations/:token/submit" element={<VendorAttestationSubmitPage />} />
              <Route path="/incidents"  element={<IncidentsPage />} />
              <Route path="/rca"        element={<RcaPage />} />
              <Route path="/risk-scenarios"   element={<RiskScenariosPage />} />
              <Route path="/emerging-risk"    element={<EmergingRiskPage />} />
              <Route path="/committees"                              element={<CommitteesPage />} />
              <Route path="/committees/catalog"                      element={<CommitteeCatalogPage />} />
              <Route path="/committees/catalog/forms/:formCode"      element={<CommitteeFormDetailPage />} />
              <Route path="/committees/catalog/:templateCode"        element={<CommitteeTemplateDetailPage />} />
              <Route path="/committees/:committeeId/members"         element={<CommitteeMemberRegistryPage />} />
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
              <Route path="/expert"     element={<GrcExpertPage />} />
              <Route path="/buyer-gap"  element={<BuyerGapPage />} />

              {/* Workflow Hub + per-module wizards (Saudi-GRC §7) */}
              <Route path="/workflows"                              element={<WorkflowHubPage />} />
              <Route path="/workflows/requests"                      element={<WorkflowRequestsPage />} />
              <Route path="/workflows/requests/:id"                  element={<WorkflowRequestDetailPage />} />
              <Route path="/workflows/submissions"                   element={<WorkflowSubmissionsPage />} />
              <Route path="/workflows/submissions/:id"               element={<WorkflowSubmissionDetailPage />} />

              {/* Sprint 13 — Multi-step approval chains */}
              <Route path="/workflows/chains"                        element={<ApprovalChainTemplatesPage />} />
              <Route path="/workflows/chains/templates"              element={<ApprovalChainTemplatesPage />} />
              <Route path="/workflows/chains/pending"                element={<ApprovalChainPendingPage />} />
              <Route path="/workflows/chains/:id"                    element={<ApprovalChainInstanceDetailPage />} />

              {/* Compliance Calendar (PR #61) — Saudi-workdays-aware */}
              <Route path="/calendar"                                 element={<CalendarPage />} />

              {/* SOC Operations — Saudi-CISO backbone (PRs #97 / #98 / #99 + V151/V153 rules) */}
              <Route path="/socops"                                   element={<SocOpsDashboardPage />} />
              <Route path="/socops/dashboard"                         element={<SocOpsDashboardPage />} />
              <Route path="/socops/events"                            element={<SocOpsEventsPage />} />
              <Route path="/socops/playbooks"                         element={<IrPlaybooksPage />} />
              <Route path="/socops/pentests"                          element={<PentestsPage />} />
              <Route path="/socops/regulator-exams"                   element={<RegulatorExamsPage />} />
              <Route path="/socops/ot-assets"                         element={<OtAssetsPage />} />
              <Route path="/socops/threat-intel"                      element={<ThreatIntelPage />} />
              <Route path="/socops/detections"                        element={<DetectionLibraryPage />} />
              <Route path="/socops/scanner-runs"                      element={<ScannerRunsPage />} />
              <Route path="/socops/crypto-assets"                     element={<CryptoAssetsPage />} />
              <Route path="/socops/sbom"                              element={<SbomPage />} />
              <Route path="/socops/zt-maturity"                       element={<ZtMaturityPage />} />
              <Route path="/socops/seasonal-overlays"                 element={<SeasonalOverlaysPage />} />
              <Route path="/socops/awareness"                         element={<AwarenessPage />} />
              <Route path="/socops/iam-jml"                           element={<IamJmlPage />} />
              <Route path="/socops/appsec"                            element={<AppsecPage />} />
              <Route path="/socops/dlp"                               element={<DlpPage />} />
              <Route path="/socops/cyber-insurance"                   element={<CyberInsurancePage />} />
              <Route path="/socops/brand-monitoring"                  element={<BrandMonitoringPage />} />
              <Route path="/socops/forensics"                         element={<ForensicsPage />} />
              <Route path="/socops/ma-due-diligence"                  element={<MaDueDiligencePage />} />
              {/* Generic SOC ops detail route — single page renders every entity via RESOURCE_CONFIG.
                  :resource is one of events|playbooks|pentests|ot-assets|iam-jml|threat-intel|... */}
              <Route path="/socops/:resource/:id"                     element={<SocOpsDetailPage />} />
              <Route path="/notifications"                            element={<NotificationInboxPage />} />
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

              {/* Sprint 11 — Three Lines of Defense (IIA 2020 / SAMA CSF 1.1.1) */}
              <Route path="/governance/three-lines"                  element={<ThreeLinesOfDefensePage />} />

              {/* Sprint 13 — Internal Audit Annual Plan + Engagement workflow.
                  Static segments (/universe, /engagements) registered before
                  any param routes so they match first. */}
              <Route path="/audit/ia/plans"                          element={<IaPlanListPage />} />
              <Route path="/audit/ia/universe"                       element={<IaUniversePage />} />
              <Route path="/audit/ia/plans/:id"                      element={<IaPlanDetailPage />} />
              <Route path="/audit/ia/engagements/:id"                element={<IaEngagementDetailPage />} />

              {/* Sprint 13 — Customer Complaints Management.
                  Two surfaces: authenticated register/detail (gated
                  server-side to PLATFORM_ADMIN /
                  CONSUMER_PROTECTION_OFFICER / COMPLIANCE_OFFICER), and
                  two PUBLIC routes — these MUST stay reachable WITHOUT
                  login per SAMA Banking Consumer Protection Principles
                  (submit + track). The public surfaces use PublicShell
                  (no sidebar) and call /api/v1/complaints/public/* with
                  anonymous=true so no JWT is attached. */}
              <Route path="/complaints"                              element={<ComplaintsRegisterPage />} />
              <Route path="/complaints/:id"                          element={<ComplaintDetailPage />} />
              <Route path="/public/complaint"                        element={<PublicComplaintSubmissionPage />} />
              <Route path="/public/track-complaint"                  element={<PublicComplaintTrackingPage />} />
              {/* WF-7 — EXTERNAL workflow-request recipient tracker (token-credentialed, no login;
                  PublicShell, bare fetch with no JWT). /public/* is already a bare prefix. */}
              <Route path="/public/workflow-requests/:token"         element={<PublicWorkflowRequestPage />} />

              {/* Sprint 13 — Sanctions Screening engine. Static segments
                  (/dashboard, /lists, /checks, /screen) registered before
                  the param route so they match first. */}
              <Route path="/aml/sanctions/dashboard"                 element={<SanctionsDashboardPage />} />
              <Route path="/aml/sanctions/lists"                     element={<SanctionsListsPage />} />
              <Route path="/aml/sanctions/lists/:id"                 element={<SanctionsListDetailPage />} />
              <Route path="/aml/sanctions/checks"                    element={<SanctionsChecksPage />} />
              <Route path="/aml/sanctions/screen"                    element={<SanctionsScreenPage />} />
              <Route path="/aml/sanctions/checks/:id"                element={<SanctionsCheckDetailPage />} />

              {/* Detail + admin pages for V115/V116 modules */}
              <Route path="/bcm/aars/:id"                            element={<AarDetailPage />} />
              <Route path="/ai/suggestions"                          element={<AiSuggestionsPage />} />
              {/* Sprint 11 — AI/ML Model Registry. Static /ai/models segment
                  registered before any param routes (none exist for /ai today
                  but follow the same defensive pattern as bowtie above). */}
              <Route path="/ai/models"                               element={<AiModelRegisterPage />} />
              <Route path="/ai/models/:externalId"                   element={<AiModelDetailPage />} />
              {/* Sprint 11 — Continuous Control Monitoring engine.
                  Static segments registered before any param routes. */}
              <Route path="/ccm/dashboard"                           element={<CcmDashboardPage />} />
              <Route path="/ccm/tests"                               element={<CcmTestsPage />} />
              <Route path="/ccm/tests/:externalId"                   element={<CcmTestDetailPage />} />
              {/* Sprint 11 — Audit Committee Dashboard (curation slice). */}
              <Route path="/audit-committee/dashboard"               element={<AuditCommitteeDashboardPage />} />
              <Route path="/audit-committee/snapshots/:id"           element={<AuditCommitteeSnapshotPage />} />
              {/* Sprint 11 — Risk-Control-Finding Pivot. Three lens routes
                  share a single page component; useParams picks the focal entity. */}
              <Route path="/governance/pivot/risk/:riskId"           element={<RiskControlFindingPivotPage />} />
              <Route path="/governance/pivot/control/:controlId"     element={<RiskControlFindingPivotPage />} />
              <Route path="/governance/pivot/finding/:findingId"     element={<RiskControlFindingPivotPage />} />
              {/* Sprint 11 — Attack Surface Management (ASM).
                  Static segments registered before any param routes. */}
              <Route path="/cyber/attack-surface"                              element={<AttackSurfacePage />} />
              <Route path="/cyber/attack-surface/assets/:externalId"           element={<AttackSurfaceAssetDetailPage />} />
              {/* Sprint 11 — Risk-Based Vulnerability SLA engine. */}
              <Route path="/cyber/vuln-sla/dashboard"                          element={<VulnSlaDashboardPage />} />
              <Route path="/cyber/vuln-sla/policies"                           element={<VulnSlaPolicyPage />} />
              {/* Sprint 11 — PDPL Sub-processor Tracking. Public route
                  registered first so /public is matched before /:externalId. */}
              <Route path="/privacy/public/subprocessors"   element={<PublicSubprocessorListPage />} />
              <Route path="/privacy/subprocessors"          element={<SubprocessorsPage />} />
              <Route path="/privacy/subprocessors/:externalId" element={<SubprocessorDetailPage />} />
              {/* Sprint 13 — KRI engine. Static segments registered
                  before any param routes. */}
              <Route path="/risk/kri/dashboard"             element={<KriDashboardPage />} />
              <Route path="/risk/kri/admin"                 element={<KriAdminPage />} />
              <Route path="/risk/kri/:externalId"           element={<KriDetailPage />} />
              <Route path="/admin"      element={<AdminPage />} />
              <Route path="/admin/licensing" element={<LicensingPage />} />
              {/* Sprint 12 — Embedded Help admin */}
              <Route path="/admin/help-topics" element={<HelpTopicsAdminPage />} />
              <Route path="/scm"        element={<ScmPage />} />
              <Route path="/supplier-resilience" element={<SupplierResiliencePage />} />
              {/* NOTE: "/workflows" is served by WorkflowHubPage (declared earlier). The
                  former duplicate route to the legacy WorkflowsPage was unreachable dead
                  code (React Router used the first match) and has been removed. */}
              <Route path="/changes"    element={<ChangesPage />} />
              <Route path="/ethics"     element={<EthicsPage />} />
              {/* Sprint 13 — Whistleblower / Ethics Hotline.
                  Public surfaces are wired permitAll in backend SecurityConfig. */}
              <Route path="/ethics/whistleblower/submit"         element={<WhistleblowerSubmitPublicPage />} />
              <Route path="/ethics/whistleblower/status/:token"  element={<WhistleblowerStatusPage />} />
              <Route path="/ethics/whistleblower/cases"          element={<WhistleblowerCasesPage />} />
              <Route path="/ethics/whistleblower/cases/:id"      element={<WhistleblowerCaseDetailPage />} />
              <Route path="/ethics/whistleblower/retaliation"    element={<WhistleblowerRetaliationPage />} />
              {/* Sprint 13 — Conflict of Interest Disclosure Register. */}
              <Route path="/ethics/coi"                          element={<CoiRegisterPage />} />
              <Route path="/ethics/coi/campaigns/:id"            element={<CoiCampaignDetailPage />} />
              {/* Sprint 14 — Fraud Risk Management. */}
              <Route path="/fraud/dashboard"                     element={<FraudDashboardPage />} />
              <Route path="/fraud/cases"                         element={<FraudCasesPage />} />
              <Route path="/fraud/cases/new"                     element={<FraudCaseNewPage />} />
              <Route path="/fraud/cases/:id"                     element={<FraudCaseDetailPage />} />

              {/* Sprint 14 — Operational Resilience self-assessment. */}
              <Route path="/opres/dashboard"                     element={<OpResDashboardPage />} />
              <Route path="/opres/assessments"                   element={<OpResAssessmentsPage />} />
              <Route path="/opres/assessments/:id"               element={<OpResAssessmentDetailPage />} />

              {/* Sprint 15 — Regulatory Inspection Workspace. */}
              <Route path="/reginspect/dashboard"                 element={<RegInspectDashboardPage />} />
              <Route path="/reginspect/inspections"               element={<RegInspectInspectionsPage />} />
              <Route path="/reginspect/inspections/:id"           element={<RegInspectWorkspaceDetailPage />} />

              {/* Sprint 16 — Mandatory Regulatory Reporting Calendar. */}
              <Route path="/regreporting/dashboard"               element={<RegReportingDashboardPage />} />
              <Route path="/regreporting/obligations"             element={<RegReportingObligationsPage />} />
              <Route path="/regreporting/filings"                 element={<RegReportingFilingsPage />} />
              <Route path="/regreporting/filings/:id"             element={<RegReportingFilingDetailPage />} />

              {/* Sprint 17 — Recovery & Resolution Plan. */}
              <Route path="/rrp/dashboard"                        element={<RrpDashboardPage />} />
              <Route path="/rrp/cycles"                           element={<RrpCyclesPage />} />
              <Route path="/rrp/cycles/:id"                       element={<RrpCycleDetailPage />} />

              {/* Sprint 18 — Counterparty Credit Rating Registry. */}
              <Route path="/cpty/dashboard"                       element={<CptyDashboardPage />} />
              <Route path="/cpty/counterparties"                  element={<CptyCounterpartiesPage />} />
              <Route path="/cpty/counterparties/:id"              element={<CptyCounterpartyDetailPage />} />

              {/* Sprint 19 — SAMA Open Banking Compliance. */}
              <Route path="/openbanking/dashboard"                element={<ObDashboardPage />} />
              <Route path="/openbanking/tpps"                     element={<ObTppsPage />} />
              <Route path="/openbanking/tpps/:id"                 element={<ObTppDetailPage />} />
              <Route path="/openbanking/consents"                 element={<ObConsentsPage />} />
              <Route path="/openbanking/incidents"                element={<ObIncidentsPage />} />

              {/* Sprint 20 — Sharia Governance & Audit Workflow. */}
              <Route path="/sharia/dashboard"                     element={<ShariaDashboardPage />} />
              <Route path="/sharia/scholars"                      element={<ShariaScholarsPage />} />
              <Route path="/sharia/resolutions"                   element={<ShariaResolutionsPage />} />
              <Route path="/sharia/audit-findings"                element={<ShariaAuditFindingsPage />} />
              <Route path="/sharia/nc-events"                     element={<ShariaNcEventsPage />} />
              <Route path="/sharia/products"                      element={<ShariaProductsPage />} />

              {/* Sprint 30 — Outsourcing & Cloud Governance. */}
              <Route path="/outsourcing/dashboard"                element={<OutsourcingDashboardPage />} />
              <Route path="/outsourcing/arrangements"             element={<OutsourcingArrangementsPage />} />
              <Route path="/outsourcing/cloud-services"           element={<OutsourcingCloudServicesPage />} />

              {/* Sprint 31 — AI/ML Model Lifecycle. */}
              <Route path="/ai-lifecycle/dashboard"               element={<AiLifecycleDashboardPage />} />
              <Route path="/ai-lifecycle/validations"             element={<AiLifecycleValidationsPage />} />
              <Route path="/ai-lifecycle/retrainings"             element={<AiLifecycleRetrainingsPage />} />
              <Route path="/ai-lifecycle/recertifications"        element={<AiLifecycleRecertificationsPage />} />

              {/* Sprint 32 — Data Classification + NDMO. */}
              <Route path="/dataclass/dashboard"                  element={<DataclassDashboardPage />} />
              <Route path="/dataclass/assets"                     element={<DataclassAssetsPage />} />
              <Route path="/dataclass/flows"                      element={<DataclassFlowsPage />} />

              {/* Sprint 33 — Saudi Data Officer Toolkit. */}
              <Route path="/data-officer/dashboard"               element={<DataOfficerDashboardPage />} />
              <Route path="/data-officer/dsr"                     element={<DsrPage />} />
              <Route path="/data-officer/consents"                element={<ConsentPage />} />
              <Route path="/data-officer/dpia"                    element={<DpiaPage />} />
              <Route path="/data-officer/breaches"                element={<BreachPage />} />
              <Route path="/data-officer/notices"                 element={<NoticePage />} />
              <Route path="/data-officer/retention"               element={<RetentionPage />} />
              <Route path="/data-officer/ndr"                     element={<NdrPage />} />
              <Route path="/data-officer/catalog"                 element={<CatalogPage />} />
              <Route path="/data-officer/quality"                 element={<QualityPage />} />
              <Route path="/data-officer/ethics"                  element={<DoEthicsPage />} />

              <Route path="/reports/centre" element={<ReportsCentrePage />} />
              <Route path="/reports/schedules" element={<ReportSchedulesPage />} />

              {/* Sprint 11 P1 GRC — bow-tie routes registered BEFORE
                  /risk/:id so the static "bowtie" segment matches first. */}
              <Route path="/risk/bowtie"                 element={<RiskBowTiePage />} />
              <Route path="/risk/bowtie/:externalId"     element={<RiskBowTieDetailPage />} />

              {/* Detail-page deep-drill routes (Tier 2) */}
              <Route path="/risk/:id"        element={<RiskDetailPage />} />
              <Route path="/findings/:id"    element={<FindingDetailPage />} />
              <Route path="/controls/:id"    element={<ControlDetailPage />} />
              <Route path="/policies/:id"    element={<PolicyDetailPage />} />
              <Route path="/vendors/:id"     element={<VendorDetailPage />} />
              <Route path="/dpias/:id"       element={<DpiaDetailPage />} />
              <Route path="/privacy/consents/:id" element={<ConsentDetailPage />} />
              <Route path="/privacy/dsrs/:id"     element={<DsrDetailPage />} />
              <Route path="/privacy/breaches/:id" element={<PrivacyBreachDetailPage />} />
              <Route path="/privacy/ropa/:id"     element={<RopaDetailPage />} />

              {/* Authority workspace (Phase B — auditor pivot view) */}
              <Route path="/authorities"        element={<AuthoritiesIndexPage />} />
              <Route path="/authority/:code"    element={<AuthorityWorkspacePage />} />

              {/* Auditor Workspace (Phase C — auditor's sign-off home) */}
              <Route path="/audit-workspace"    element={<AuditWorkspacePage />} />
              <Route path="/audit-workspace/engagement/:id" element={<EngagementDetailPage />} />

              {/* Communications + Stakeholder workspace (DD-22..26) */}
              <Route path="/communications"     element={<CommunicationsPage />} />

              {/* CAPA register (DD-42) + detail page */}
              <Route path="/capa"               element={<CapaPage />} />
              <Route path="/capa/:id"           element={<CapaDetailPage />} />

              {/* Public-facing intake portals (DD-55..57) — no auth */}
              <Route path="/portal/dsr"         element={<DsrPortalPage />} />
              <Route path="/portal/speakup"     element={<SpeakUpPortalPage />} />
              <Route path="/portal/track"       element={<CaseTrackerPage />} />
              <Route path="/portal/trust-center" element={<TrustCenterPage />} />

              {/* Vendor self-service portal (Sprint 5) — public */}
              <Route path="/portal/vendor"      element={<VendorPortalPage />} />

              {/* NCA ECC self-assessment export (Sprint 6) */}
              <Route path="/nca-ecc-export"     element={<NcaEccExportPage />} />
              <Route path="/nca-ecc/filings"    element={<NcaEccFilingsPage />} />

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

              {/* Sprint 11 — TCFD scenarios + Scope-3 financed emissions */}
              <Route path="/esg/tcfd-scenarios"     element={<TcfdScenariosPage />} />
              <Route path="/esg/financed-emissions" element={<FinancedEmissionsPage />} />
              <Route path="/esg/climate-report"     element={<ClimateAnnualReportPage />} />

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
              <Route path="/connectors/:key"    element={<IntegrationConsolePage />} />
              <Route path="/audit-log"          element={<AuditLogPage />} />
              <Route path="/issues"             element={<IssuesPage />} />
              <Route path="/regulatory/horizon" element={<RegulatoryHorizonPage />} />
              <Route path="/cyber/cloud-services" element={<CyberCloudServicesPage />} />
              <Route path="/risk/fair"          element={<FairCalculatorPage />} />
              <Route path="/account/sessions"   element={<SessionsPage />} />
              <Route path="/cyber/threat-intel" element={<ThreatIntelCorrelationPage />} />
              <Route path="/bcm/dependency-cascade" element={<DependencyCascadePage />} />
              <Route path="/bcm/dr-test-trend" element={<DrTestTrendPage />} />
              <Route path="/bcm/management-reviews" element={<BcmManagementReviewsPage />} />
              <Route path="/bcm/siem-events" element={<BcmSiemEventsPage />} />
              <Route path="/controls/ai-mapper" element={<AiControlMapperPage />} />
              <Route path="/privacy/data-flow" element={<DataFlowGraphPage />} />
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
              </Route>
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
