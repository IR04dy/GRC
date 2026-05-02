// English dictionary. Keys must stay flat strings — see ar.ts for the parallel file.
export const en = {
  // Brand
  brandName: 'TSS GRC Platform',
  brandSub: 'v2.0 · Riyadh',

  // Sidebar sections + items
  navMain: 'Main',
  navCoreModules: 'Core Modules',
  navTools: 'Tools',
  navSettings: 'Settings',

  itemDashboard: 'Dashboard',
  itemModules: 'Modules',
  itemAbout: 'About',
  itemSettings: 'Settings',

  modRisk: 'Enterprise Risk',
  modCyber: 'Cybersecurity',
  modBcm: 'Business Continuity',
  modData: 'Data Governance',
  modPdpl: 'PDPL',
  modCompliance: 'Compliance',
  modAudit: 'Internal Audit',
  modThirdParty: 'Third-Party Risk',
  modPolicy: 'Policy Management',

  // Topbar
  searchPlaceholder: 'Search risks, controls, policies…',
  notifications: 'Notifications',
  langToggle: 'العربية',
  signOut: 'Sign out',

  // Login
  loginEyebrow: 'Sign In',
  loginTitle: 'Welcome back',
  loginSub: 'Sign in to manage governance, risk and compliance.',
  loginUsername: 'Username',
  loginPassword: 'Password',
  loginSubmit: 'Sign In',
  loginSubmitting: 'Signing in…',
  loginInvalid: 'Invalid username or password.',
  loginNetworkError: 'Cannot reach the API. Is the backend running on http://localhost:8084?',
  loginHero: 'Unified governance, risk and compliance for Saudi enterprises.',
  loginHeroSub: 'NCA ECC, SDAIA, PDPL, SAMA, ISO 27001 — out of the box. Native Arabic + English.',
  loginHint: 'Demo · admin / Admin@12345 — riskmanager / Risk@12345',
  loginSkipDemo: 'Continue without backend (demo mode)',

  // Dashboard
  dashEyebrow: 'Dashboard',
  dashTitle: 'GRC Command Center',
  dashSub: 'Live indicators across all GRC modules. Refresh interval: 15 min.',
  dashRefresh: 'Refresh',
  dashExport: 'Export',
  dashAddRisk: 'New Risk',
  kpiTotalRisks: 'Total Risks',
  kpiCriticalRisks: 'Critical Risks',
  kpiCompliance: 'Compliance Level',
  kpiActiveControls: 'Active Controls',
  cardHeatTitle: 'Risk Heat Map',
  cardByCategory: 'Risks by Category',
  cardFrameworks: 'Framework Readiness',
  cardActivity: 'Recent Activity',

  // Risk Register
  riskEyebrow: 'Enterprise Risk',
  riskTitle: 'Risk Register',
  riskSub: 'Lifecycle management aligned with ISO 31000 and COSO ERM.',
  riskTabRegister: 'Register',
  riskTabHeatMap: 'Heat Map',
  riskTabKri: 'KRI Dashboard',
  riskTabAppetite: 'Appetite',
  riskFilter: 'Filter',
  riskExport: 'Export',
  riskNew: 'New Risk',

  riskColId: 'Risk ID',
  riskColName: 'Name',
  riskColCategory: 'Category',
  riskColScore: 'Score',
  riskColStatus: 'Status',
  riskColOwner: 'Owner',

  riskEmpty: 'No risks yet',
  riskEmptySub: 'Click "New Risk" to register the first one.',
  riskLoading: 'Loading risks…',
  riskLoadError: 'Could not load risks.',

  riskAxisImpact: 'Impact',
  riskAxisLikelihood: 'Likelihood',
  heatLevels: 'VL,L,M,H,VH',

  // New Risk Modal
  modalNewRisk: 'Register a New Risk',
  fldTitleEn: 'Title (English)',
  fldTitleAr: 'Title (Arabic)',
  fldDescription: 'Description',
  fldStatus: 'Status',
  fldInherentLikelihood: 'Inherent Likelihood (1–5)',
  fldInherentImpact: 'Inherent Impact (1–5)',
  fldReviewDate: 'Review Date',

  cancel: 'Cancel',
  save: 'Save',
  saving: 'Saving…',

  toastRiskAdded: 'Risk added',
  toastRiskFailed: 'Could not save the risk',

  // Generic
  comingSoon: 'Coming next',
  comingSoonSub: 'This view will follow once we agree on the design language.',
  demoMode: 'Demo mode · using sample data',

  // Settings page
  settingsEyebrow: 'Workspace',
  settingsTitle: 'Personalize your workspace',
  settingsSub: 'Adjust colors, sidebar and layout. Changes apply live and persist on this device.',
  settingsReset: 'Reset to defaults',
  settingsPresets: 'Theme presets',
  settingsColors: 'Colors',
  settingsLayout: 'Layout & Sizing',
  settingsHint: 'Logical-only tokens are exposed here. Tints (primary-50 … primary-900) are kept fixed so semantic surfaces stay legible.',

  secSurface: 'Surface',
  secBrand: 'Brand',
  secSidebar: 'Sidebar',

  colorBg: 'Canvas background',
  colorSurface: 'Card surface',
  colorPrimary: 'Primary brand',
  colorAccent: 'Accent',
  colorSbBg: 'Sidebar background',
  colorSbText: 'Sidebar text',
  colorSbAccent: 'Sidebar highlight',

  sizeSidebar: 'Sidebar width',
  sizeHeader: 'Header height',
  sizeRadius: 'Border radius',

  presetDefault: 'Eye-Comfort Default',
  presetSand: 'Sand',
  presetPetrol: 'Petrol',
  presetMono: 'Mono',
  presetRose: 'Rose',
} as const;
