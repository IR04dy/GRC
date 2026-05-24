// Contextual Help — Sprint 12.
//
// The floating "?" button on every page resolves the right help
// topic for the current URL via `helpApi.lookup(route)`. Topics
// carry bilingual EN/AR copy and a Saudi-regulator anchor.

import { api } from './api';

export type HelpModule =
  | 'RISK' | 'CYBER' | 'COMPLIANCE' | 'BCM' | 'PRIVACY'
  | 'TPRM' | 'ESG' | 'AUDIT' | 'AI_GOVERNANCE' | 'CCM'
  | 'GENERAL' | 'ADMIN';

export type HelpSeverity = 'INFO' | 'TIP' | 'WARNING';

export interface HelpTopicResponse {
  id: number;
  externalId: string;
  organisationId?: number | null;
  topicCode: string;
  titleEn: string;
  titleAr: string;
  summaryEn?: string;
  summaryAr?: string;
  bodyEn: string;
  bodyAr: string;
  routePattern: string;
  module: HelpModule;
  regulatoryAnchor?: string;
  regulatorLinkUrl?: string;
  relatedKbArticleId?: number;
  relatedWorkflowCodes?: string;
  severity: HelpSeverity;
  displayOrder: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface HelpTopicRequest {
  topicCode: string;
  titleEn: string;
  titleAr: string;
  summaryEn?: string;
  summaryAr?: string;
  bodyEn: string;
  bodyAr: string;
  routePattern: string;
  module: HelpModule;
  regulatoryAnchor?: string;
  regulatorLinkUrl?: string;
  relatedKbArticleId?: number;
  relatedWorkflowCodes?: string;
  severity?: HelpSeverity;
  displayOrder?: number;
}

export interface HelpLookupResponse {
  route: string;
  topics: HelpTopicResponse[];
}

export const helpApi = {
  lookup:        (route: string) =>
    api.get<HelpLookupResponse>(`/api/v1/platform/help/lookup?route=${encodeURIComponent(route)}`),
  search:        (q: string) =>
    api.get<HelpTopicResponse[]>(`/api/v1/platform/help/search?q=${encodeURIComponent(q)}`),
  listForModule: (module: HelpModule) =>
    api.get<HelpTopicResponse[]>(`/api/v1/platform/help/module/${module}`),
  listAll:       () => api.get<HelpTopicResponse[]>('/api/v1/platform/help/topics'),
  get:           (externalId: string) =>
    api.get<HelpTopicResponse>(`/api/v1/platform/help/topics/${externalId}`),
  create:        (body: HelpTopicRequest) =>
    api.post<HelpTopicResponse>('/api/v1/platform/help/topics', body),
  update:        (externalId: string, body: HelpTopicRequest) =>
    api.put<HelpTopicResponse>(`/api/v1/platform/help/topics/${externalId}`, body),
  remove:        (externalId: string) =>
    api.delete<void>(`/api/v1/platform/help/topics/${externalId}`),
  trackView:     (externalId: string, route: string) =>
    api.post<void>(`/api/v1/platform/help/topics/${externalId}/track-view?route=${encodeURIComponent(route)}`, {}),
};

// ─── Saudi-native bilingual display labels ──────────────────────────

export const HELP_SEVERITY_LABEL: Record<HelpSeverity, { en: string; ar: string }> = {
  INFO:    { en: 'Info',    ar: 'إرشاد' },
  TIP:     { en: 'Tip',     ar: 'تلميح' },
  WARNING: { en: 'Warning', ar: 'تحذير' },
};

export const HELP_SEVERITY_TONE: Record<HelpSeverity, 'info' | 'success' | 'warning'> = {
  INFO:    'info',
  TIP:     'success',
  WARNING: 'warning',
};

export const HELP_MODULE_LABEL: Record<HelpModule, { en: string; ar: string }> = {
  RISK:          { en: 'Risk',                ar: 'المخاطر' },
  CYBER:         { en: 'Cyber',               ar: 'الأمن السيبراني' },
  COMPLIANCE:    { en: 'Compliance',          ar: 'الامتثال' },
  BCM:           { en: 'BCM',                 ar: 'استمرارية الأعمال' },
  PRIVACY:       { en: 'Privacy',             ar: 'الخصوصية' },
  TPRM:          { en: 'Third-party risk',    ar: 'مخاطر الطرف الثالث' },
  ESG:           { en: 'ESG',                 ar: 'الاستدامة' },
  AUDIT:         { en: 'Audit',               ar: 'المراجعة' },
  AI_GOVERNANCE: { en: 'AI governance',       ar: 'حوكمة الذكاء الاصطناعي' },
  CCM:           { en: 'Continuous monitor',  ar: 'المراقبة المستمرة' },
  GENERAL:       { en: 'General',             ar: 'عام' },
  ADMIN:         { en: 'Admin',               ar: 'الإدارة' },
};

/**
 * Track every known route in the app for the admin coverage-gap
 * widget. Kept as a static list (rather than introspecting react-
 * router) because the admin UI runs outside the router context and
 * we want a deterministic source of truth for "what routes exist".
 */
export const KNOWN_ROUTES: string[] = [
  '/dashboard',
  '/risk', '/risk/bowtie', '/risk/dashboard',
  '/cyber', '/cyber/dashboard',
  '/cyber/attack-surface', '/cyber/vuln-sla/dashboard', '/cyber/vuln-sla/policies',
  '/compliance', '/compliance/posture', '/compliance/obligations',
  '/bcm', '/bcm/crisis-room', '/bcm/dashboard', '/bcm/scenarios',
  '/bcm/comms/templates', '/bcm/mtpd-inheritance',
  '/crisis/mobile',
  '/privacy', '/privacy/dashboard', '/privacy/records', '/privacy/subprocessors',
  '/tprm', '/tprm/bcp-campaigns',
  '/esg', '/esg/tcfd-scenarios', '/esg/financed-emissions',
  '/incidents', '/audit-committee/dashboard',
  '/ai/models', '/ccm/dashboard', '/ccm/tests',
  '/controls/library', '/regulatory/library',
  '/calendar', '/workflows', '/workflows/submissions',
  '/evidence', '/governance/three-lines-of-defense',
  '/admin', '/admin/help-topics',
];
