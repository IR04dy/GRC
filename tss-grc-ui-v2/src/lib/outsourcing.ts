// Outsourcing arrangement register client — Sprint 13.
//
// Typed wrappers around /api/v1/tprm/outsourcing/* plus helpers
// (lifecycle tones, materiality labels, service-type labels).
//
// SAMA Rules Governing Outsourcing (2019/2023) language:
//   - Material outsourcing = "إسناد جوهري"
//   - SAMA non-objection = "عدم ممانعة ساما"
//   - Outsourcing register = "سجل ترتيبات الإسناد" (NOT literal
//     "سجل الاستعانة بمصادر خارجية" — Saudi finance uses "إسناد")

import { api } from './api';
import type { BadgeTone } from '../components/ui/Badge';

// ─── Types ────────────────────────────────────────────────────────────

export type LifecycleStage =
  | 'PLANNED'
  | 'DUE_DILIGENCE'
  | 'AWAITING_SAMA'
  | 'APPROVED'
  | 'ACTIVE'
  | 'MONITORING_ALERT'
  | 'UNDER_REVIEW'
  | 'EXIT_PLANNING'
  | 'EXIT_IN_PROGRESS'
  | 'EXITED';

export type ServiceType =
  | 'IT_INFRASTRUCTURE'
  | 'CLOUD_SAAS'
  | 'CUSTOMER_OPERATIONS'
  | 'BACK_OFFICE_PROCESSING'
  | 'CALL_CENTRE'
  | 'CASH_LOGISTICS'
  | 'MARKETING'
  | 'LEGAL'
  | 'INTERNAL_AUDIT'
  | 'IT_DEVELOPMENT'
  | 'ADMINISTRATIVE'
  | 'SHARIA_AUDIT'
  | 'ANALYTICS_BI'
  | 'AML_KYC_SCREENING'
  | 'PRINTING_DISTRIBUTION'
  | 'FACILITY_MGMT';

export type CriticalFunction =
  | 'PAYMENTS'
  | 'CORE_BANKING'
  | 'AML_CFT'
  | 'CUSTOMER_DATA'
  | 'CYBERSECURITY'
  | 'BCM_DRP'
  | 'REGULATORY_REPORTING'
  | 'FINANCIAL_REPORTING'
  | 'NA';

export type EventType =
  | 'KPI_REVIEW'
  | 'SLA_BREACH'
  | 'INCIDENT'
  | 'AUDIT_REPORT'
  | 'CONTRACT_AMENDMENT'
  | 'FINANCIAL_HEALTH_FLAG'
  | 'REGULATORY_QUERY'
  | 'CHANGE_OF_CONTROL'
  | 'RFP_RECOMPETE'
  | 'EXIT_TRIGGER';

export type Severity = 'INFO' | 'MINOR' | 'MODERATE' | 'MAJOR' | 'CRITICAL';

export type TestFrequency = 'NEVER' | 'ANNUAL' | 'BIANNUAL' | 'NEVER_TESTED';

export type Recommendation = 'APPROVE' | 'CONDITIONAL' | 'REJECT';

export interface OutsourcingArrangement {
  id: number;
  externalId: string;
  organisationId: number;
  arrangementCode: string;
  titleEn: string;
  titleAr: string;
  descriptionEn?: string;
  descriptionAr?: string;
  serviceType: ServiceType;
  primaryVendorId?: number;
  coVendorIds?: string;
  isMaterial: boolean;
  materialityBasisEn?: string;
  materialityBasisAr?: string;
  samaNonObjectionRequired: boolean;
  samaNonObjectionObtained: boolean;
  samaNonObjectionRef?: string;
  samaNonObjectionDate?: string;
  contractStartDate?: string;
  contractEndDate?: string;
  contractValueSarAnnual?: number;
  contractRef?: string;
  contractDocumentUrl?: string;
  criticalFunction: CriticalFunction;
  countryOfProcessing: string;
  regionOfProcessing?: string;
  dataResidencyKdc: boolean;
  personalDataProcessed: boolean;
  pdplDpaSigned: boolean;
  pdplDpaDate?: string;
  crossBorderSafeguardType?: string;
  lifecycleStage: LifecycleStage;
  ownerUserId: number;
  sponsorUserId: number;
  nextReviewDueDate?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface DueDiligence {
  id: number;
  externalId: string;
  organisationId: number;
  arrangementId: number;
  assessmentDate: string;
  assessorUserId: number;
  financialHealthScore: number;
  operationalCapabilityScore: number;
  cybersecurityScore: number;
  bcmScore: number;
  pdplComplianceScore: number;
  reputationScore: number;
  compositeScore: number;
  recommendation: Recommendation;
  rationaleEn: string;
  rationaleAr: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface MonitoringEvent {
  id: number;
  externalId: string;
  organisationId: number;
  arrangementId: number;
  eventType: EventType;
  eventDate: string;
  severity: Severity;
  descriptionEn: string;
  descriptionAr: string;
  actionTakenEn?: string;
  actionTakenAr?: string;
  resolved: boolean;
  resolvedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ExitPlan {
  id: number;
  externalId: string;
  organisationId: number;
  arrangementId: number;
  triggerConditionsEn: string;
  triggerConditionsAr: string;
  exitStepsJson?: string;
  dataRepatriationPlanEn?: string;
  dataRepatriationPlanAr?: string;
  testFrequency: TestFrequency;
  lastTestedDate?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface Dashboard {
  totalArrangements: number;
  activeMaterial: number;
  nonMaterial: number;
  awaitingSama: number;
  overdueReviews: number;
  overdueExitTests: number;
  openMajorOrCriticalEvents: number;
}

export interface MaterialityAssessment {
  arrangementId: number;
  suggestedMaterial: boolean;
  currentMaterial: boolean;
  reasons: string[];
}

// ─── Labels & tones ───────────────────────────────────────────────────

export const LIFECYCLE_STAGES: LifecycleStage[] = [
  'PLANNED', 'DUE_DILIGENCE', 'AWAITING_SAMA', 'APPROVED', 'ACTIVE',
  'MONITORING_ALERT', 'UNDER_REVIEW', 'EXIT_PLANNING', 'EXIT_IN_PROGRESS', 'EXITED',
];

export const LIFECYCLE_LABELS: Record<LifecycleStage, { en: string; ar: string }> = {
  PLANNED:          { en: 'Planned',           ar: 'مخطّط' },
  DUE_DILIGENCE:    { en: 'Due diligence',     ar: 'عناية واجبة' },
  AWAITING_SAMA:    { en: 'Awaiting SAMA',     ar: 'بانتظار ساما' },
  APPROVED:         { en: 'Approved',          ar: 'مُعتَمد' },
  ACTIVE:           { en: 'Active',            ar: 'نشط' },
  MONITORING_ALERT: { en: 'Monitoring alert',  ar: 'تنبيه مراقبة' },
  UNDER_REVIEW:     { en: 'Under review',      ar: 'تحت المراجعة' },
  EXIT_PLANNING:    { en: 'Exit planning',     ar: 'تخطيط الخروج' },
  EXIT_IN_PROGRESS: { en: 'Exit in progress',  ar: 'الخروج جارٍ' },
  EXITED:           { en: 'Exited',            ar: 'مُنتهٍ' },
};

export const SERVICE_TYPE_LABELS: Record<ServiceType, { en: string; ar: string }> = {
  IT_INFRASTRUCTURE:      { en: 'IT Infrastructure',          ar: 'البنية التحتية لتقنية المعلومات' },
  CLOUD_SAAS:             { en: 'Cloud / SaaS',               ar: 'الحوسبة السحابية / SaaS' },
  CUSTOMER_OPERATIONS:    { en: 'Customer Operations',        ar: 'عمليات العملاء' },
  BACK_OFFICE_PROCESSING: { en: 'Back-Office Processing',     ar: 'المكتب الخلفي' },
  CALL_CENTRE:            { en: 'Call Centre',                ar: 'مركز الاتصال' },
  CASH_LOGISTICS:         { en: 'Cash Logistics',             ar: 'الخدمات اللوجستية للنقد' },
  MARKETING:              { en: 'Marketing',                  ar: 'التسويق' },
  LEGAL:                  { en: 'Legal',                      ar: 'القانوني' },
  INTERNAL_AUDIT:         { en: 'Internal Audit',             ar: 'المراجعة الداخلية' },
  IT_DEVELOPMENT:         { en: 'IT Development',             ar: 'تطوير البرمجيات' },
  ADMINISTRATIVE:         { en: 'Administrative',             ar: 'الخدمات الإدارية' },
  SHARIA_AUDIT:           { en: 'Sharia Audit',               ar: 'المراجعة الشرعية' },
  ANALYTICS_BI:           { en: 'Analytics / BI',             ar: 'التحليلات / ذكاء الأعمال' },
  AML_KYC_SCREENING:      { en: 'AML / KYC Screening',        ar: 'فحص مكافحة غسل الأموال / اعرف عميلك' },
  PRINTING_DISTRIBUTION:  { en: 'Printing & Distribution',    ar: 'الطباعة والتوزيع' },
  FACILITY_MGMT:          { en: 'Facility Management',        ar: 'إدارة المرافق' },
};

export const CRITICAL_FUNCTION_LABELS: Record<CriticalFunction, { en: string; ar: string }> = {
  PAYMENTS:             { en: 'Payments',               ar: 'المدفوعات' },
  CORE_BANKING:         { en: 'Core Banking',           ar: 'النظام المصرفي الأساسي' },
  AML_CFT:              { en: 'AML / CFT',              ar: 'مكافحة غسل الأموال وتمويل الإرهاب' },
  CUSTOMER_DATA:        { en: 'Customer Data',          ar: 'بيانات العملاء' },
  CYBERSECURITY:        { en: 'Cybersecurity',          ar: 'الأمن السيبراني' },
  BCM_DRP:              { en: 'BCM / DRP',              ar: 'استمرارية الأعمال / التعافي' },
  REGULATORY_REPORTING: { en: 'Regulatory Reporting',   ar: 'الرفع التنظيمي' },
  FINANCIAL_REPORTING:  { en: 'Financial Reporting',    ar: 'الرفع المالي' },
  NA:                   { en: 'Not applicable',         ar: 'غير محدّد' },
};

export const EVENT_TYPE_LABELS: Record<EventType, { en: string; ar: string }> = {
  KPI_REVIEW:            { en: 'KPI Review',             ar: 'مراجعة المؤشرات' },
  SLA_BREACH:            { en: 'SLA Breach',             ar: 'خرق مستوى الخدمة' },
  INCIDENT:              { en: 'Incident',               ar: 'حادثة' },
  AUDIT_REPORT:          { en: 'Audit Report',           ar: 'تقرير مراجعة' },
  CONTRACT_AMENDMENT:    { en: 'Contract Amendment',     ar: 'تعديل تعاقدي' },
  FINANCIAL_HEALTH_FLAG: { en: 'Financial Health Flag',  ar: 'إشارة الوضع المالي' },
  REGULATORY_QUERY:      { en: 'Regulatory Query',       ar: 'استفسار تنظيمي' },
  CHANGE_OF_CONTROL:     { en: 'Change of Control',      ar: 'تغيير في الملكية' },
  RFP_RECOMPETE:         { en: 'RFP / Re-compete',       ar: 'كرّاس / إعادة منافسة' },
  EXIT_TRIGGER:          { en: 'Exit Trigger',           ar: 'مُحفِّز الخروج' },
};

export const SEVERITY_LABELS: Record<Severity, { en: string; ar: string }> = {
  INFO:     { en: 'Info',        ar: 'إعلامي' },
  MINOR:    { en: 'Minor',       ar: 'طفيف' },
  MODERATE: { en: 'Moderate',    ar: 'متوسط' },
  MAJOR:    { en: 'Major',       ar: 'كبير' },
  CRITICAL: { en: 'Critical',    ar: 'حرج' },
};

export const TEST_FREQUENCY_LABELS: Record<TestFrequency, { en: string; ar: string }> = {
  NEVER:        { en: 'Board-accepted: do not test', ar: 'مُعتمَد من المجلس: لا اختبار' },
  ANNUAL:       { en: 'Annual',                       ar: 'سنوي' },
  BIANNUAL:     { en: 'Every two years',              ar: 'كل سنتين' },
  NEVER_TESTED: { en: 'Never tested',                 ar: 'لم يُختبر بعد' },
};

export function lifecycleTone(s: LifecycleStage): BadgeTone {
  switch (s) {
    case 'PLANNED':          return 'neutral';
    case 'DUE_DILIGENCE':    return 'info';
    case 'AWAITING_SAMA':    return 'warning';
    case 'APPROVED':         return 'info';
    case 'ACTIVE':           return 'success';
    case 'MONITORING_ALERT': return 'warning';
    case 'UNDER_REVIEW':     return 'warning';
    case 'EXIT_PLANNING':    return 'warning';
    case 'EXIT_IN_PROGRESS': return 'danger';
    case 'EXITED':           return 'neutral';
  }
}

export function severityTone(s: Severity): BadgeTone {
  switch (s) {
    case 'INFO':     return 'neutral';
    case 'MINOR':    return 'info';
    case 'MODERATE': return 'warning';
    case 'MAJOR':    return 'danger';
    case 'CRITICAL': return 'danger';
  }
}

export function recommendationTone(r: Recommendation): BadgeTone {
  switch (r) {
    case 'APPROVE':     return 'success';
    case 'CONDITIONAL': return 'warning';
    case 'REJECT':      return 'danger';
  }
}

export function fmtSar(value?: number): string {
  if (value == null) return '—';
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'SAR', maximumFractionDigits: 0 }).format(value);
}

// ─── API ──────────────────────────────────────────────────────────────

const BASE = '/api/v1/tprm/outsourcing';

export function listArrangements(opts?: { stage?: LifecycleStage; material?: boolean; criticalFunction?: CriticalFunction }):
  Promise<OutsourcingArrangement[]> {
  const params = new URLSearchParams();
  if (opts?.stage) params.set('stage', opts.stage);
  if (opts?.material !== undefined) params.set('material', String(opts.material));
  if (opts?.criticalFunction) params.set('criticalFunction', opts.criticalFunction);
  const qs = params.toString();
  return api.get<OutsourcingArrangement[]>(qs ? `${BASE}?${qs}` : BASE);
}

export function getArrangement(id: number): Promise<OutsourcingArrangement> {
  return api.get<OutsourcingArrangement>(`${BASE}/${id}`);
}

export function createArrangement(body: Partial<OutsourcingArrangement> & { titleEn: string; titleAr: string; serviceType: ServiceType; countryOfProcessing: string; ownerUserId: number; sponsorUserId: number; }):
  Promise<OutsourcingArrangement> {
  return api.post<OutsourcingArrangement>(BASE, body);
}

export function updateArrangement(id: number, body: Partial<OutsourcingArrangement>): Promise<OutsourcingArrangement> {
  return api.patch<OutsourcingArrangement>(`${BASE}/${id}`, body);
}

export function transitionArrangement(id: number, toStage: LifecycleStage, reason?: string): Promise<OutsourcingArrangement> {
  return api.post<OutsourcingArrangement>(`${BASE}/${id}/transition`, { toStage, reason });
}

export function recordReview(id: number): Promise<OutsourcingArrangement> {
  return api.post<OutsourcingArrangement>(`${BASE}/${id}/record-review`, {});
}

export function getMaterialityAssessment(id: number): Promise<MaterialityAssessment> {
  return api.get<MaterialityAssessment>(`${BASE}/${id}/materiality-assessment`);
}

export function listSamaPending(): Promise<OutsourcingArrangement[]> {
  return api.get<OutsourcingArrangement[]>(`${BASE}/admin/sama-pending`);
}

export function listOverdueReviews(): Promise<OutsourcingArrangement[]> {
  return api.get<OutsourcingArrangement[]>(`${BASE}/admin/overdue-reviews`);
}

export function getDashboard(): Promise<Dashboard> {
  return api.get<Dashboard>(`${BASE}/dashboard`);
}

// Due Diligence
export function listDueDiligence(arrangementId: number): Promise<DueDiligence[]> {
  return api.get<DueDiligence[]>(`${BASE}/${arrangementId}/due-diligence`);
}

export function recordDueDiligence(body: Omit<DueDiligence, 'id' | 'externalId' | 'organisationId' | 'compositeScore' | 'createdAt' | 'updatedAt'>):
  Promise<DueDiligence> {
  return api.post<DueDiligence>(`${BASE}/due-diligence`, body);
}

// Monitoring events
export function listEvents(arrangementId: number): Promise<MonitoringEvent[]> {
  return api.get<MonitoringEvent[]>(`${BASE}/${arrangementId}/monitoring-events`);
}

export function listOpenEvents(): Promise<MonitoringEvent[]> {
  return api.get<MonitoringEvent[]>(`${BASE}/monitoring-events/open`);
}

export function recordEvent(body: Omit<MonitoringEvent, 'id' | 'externalId' | 'organisationId' | 'resolvedAt' | 'createdAt' | 'updatedAt'>):
  Promise<MonitoringEvent> {
  return api.post<MonitoringEvent>(`${BASE}/monitoring-events`, body);
}

export function resolveEvent(eventId: number, body?: { actionTakenEn?: string; actionTakenAr?: string }):
  Promise<MonitoringEvent> {
  return api.post<MonitoringEvent>(`${BASE}/monitoring-events/${eventId}/resolve`, body ?? {});
}

// Exit plan
export function getExitPlan(arrangementId: number): Promise<ExitPlan> {
  return api.get<ExitPlan>(`${BASE}/${arrangementId}/exit-plan`);
}

export function listOverdueExitTests(): Promise<ExitPlan[]> {
  return api.get<ExitPlan[]>(`${BASE}/exit-plans/overdue-tests`);
}

export function createExitPlan(body: Omit<ExitPlan, 'id' | 'externalId' | 'organisationId' | 'createdAt' | 'updatedAt'>):
  Promise<ExitPlan> {
  return api.post<ExitPlan>(`${BASE}/exit-plans`, body);
}

export function updateExitPlan(planId: number, body: Partial<ExitPlan>): Promise<ExitPlan> {
  return api.patch<ExitPlan>(`${BASE}/exit-plans/${planId}`, body);
}

export function recordExitTest(planId: number, testedOn?: string): Promise<ExitPlan> {
  const qs = testedOn ? `?testedOn=${testedOn}` : '';
  return api.post<ExitPlan>(`${BASE}/exit-plans/${planId}/record-test${qs}`, {});
}
