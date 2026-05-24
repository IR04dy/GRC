// Sanctions Screening API client — Sprint 13.
//
// Wires the React frontend to the V196 module's REST surface
// (SanctionsScreeningController under /api/v1/aml/sanctions/*).
//
// Shapes mirror the backend DTOs exactly:
//   SanctionsDtos.ListResponse           → SanctionsList
//   SanctionsDtos.EntryResponse          → SanctionsListEntry
//   SanctionsDtos.CheckResponse          → ScreeningCheck
//   SanctionsDtos.HitResponse            → ScreeningHit
//   SanctionsDtos.DashboardResponse      → SanctionsDashboard

import { api } from './api';

export type SourceType =
  | 'UN' | 'OFAC' | 'EU' | 'SAMA' | 'KSA_NATIONAL' | 'FATF' | 'OTHER';

export type RefreshFrequency = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'ON_DEMAND';

export type EntityType = 'INDIVIDUAL' | 'ENTITY' | 'VESSEL' | 'AIRCRAFT';

export type SubjectType =
  | 'CUSTOMER_ONBOARDING' | 'TRANSACTION_COUNTERPARTY'
  | 'BENEFICIAL_OWNER' | 'EMPLOYEE_KYC' | 'VENDOR_KYC' | 'AD_HOC';

export type CheckSource =
  | 'MANUAL' | 'REAL_TIME_API' | 'BATCH_FILE' | 'INTEGRATION_VENDOR';

export type CheckResult = 'NO_HIT' | 'POTENTIAL_HIT' | 'CONFIRMED_HIT';

export type HitStatus =
  | 'PENDING_REVIEW' | 'TRUE_POSITIVE' | 'FALSE_POSITIVE'
  | 'ESCALATED' | 'BLOCK_RECOMMENDED' | 'RELEASE_RECOMMENDED' | 'CLOSED';

export type MatchType =
  | 'EXACT_NAME' | 'FUZZY_NAME' | 'ALIAS'
  | 'DOB_MATCH' | 'DOCUMENT_MATCH' | 'COUNTRY_MATCH' | 'COMPOSITE';

export interface SanctionsList {
  id: number;
  externalId: string;
  organisationId: number | null; // null = platform-global
  listCode: string;
  titleEn: string;
  titleAr: string;
  sourceUrl: string | null;
  sourceType: SourceType;
  lastRefreshedAt: string | null;
  refreshFrequency: RefreshFrequency;
  entryCount: number;
  isActive: boolean;
  createdAt: string;
  updatedAt: string | null;
}

export interface SanctionsListEntry {
  id: number;
  externalId: string;
  listId: number;
  entryExternalId: string;
  entityType: EntityType;
  primaryNameEn: string;
  primaryNameAr: string | null;
  aliasesJson: string | null;
  dateOfBirth: string | null;
  country: string | null;
  idDocumentsJson: string | null;
  listingDate: string | null;
  listedBy: string | null;
  listingReasonEn: string | null;
  listingReasonAr: string | null;
  severity: 'STRONG_MATCH_REQUIRED' | 'REVIEW_REQUIRED';
  createdAt: string;
}

export interface ScreeningHit {
  id: number;
  externalId: string;
  checkId: number;
  listEntryId: number;
  listCode: string | null;
  matchedNameEn: string | null;
  matchedNameAr: string | null;
  matchScore: number;
  matchType: MatchType;
  status: HitStatus;
  reviewerUserId: number | null;
  reviewedAt: string | null;
  dispositionEn: string | null;
  dispositionAr: string | null;
  slaBreach: boolean;
  escalatedToUserId: number | null;
  escalatedAt: string | null;
  capaTaskId: number | null;
  createdAt: string;
}

export interface ScreeningCheck {
  id: number;
  externalId: string;
  organisationId: number;
  checkCode: string;
  subjectType: SubjectType;
  subjectReference: string | null;
  subjectNameEn: string;
  subjectNameAr: string | null;
  subjectDob: string | null;
  subjectCountry: string | null;
  subjectIdDocument: string | null;
  checkAt: string;
  checkSource: CheckSource;
  listsScreenedCodes: string | null;
  result: CheckResult;
  hits: ScreeningHit[];
  createdAt: string;
  updatedAt: string | null;
}

export interface SanctionsDashboard {
  checksLast24h: number;
  totalChecks: number;
  hitsPendingReview: number;
  hitsTruePositive: number;
  hitsFalsePositive: number;
  hitsEscalated: number;
  hitsSlaBreached: number;
  falsePositiveRate: number | null;
  asOf: string;
}

export interface ScreenRequest {
  subjectType: SubjectType;
  subjectReference?: string;
  subjectNameEn: string;
  subjectNameAr?: string;
  subjectDob?: string;
  subjectCountry?: string;
  subjectIdDocument?: string;
  checkSource?: CheckSource;
  listsToScreen?: string[];
}

export interface HitReviewRequest {
  newStatus: HitStatus;
  reviewerUserId?: number;
  dispositionEn?: string;
  dispositionAr?: string;
  capaTaskId?: number;
}

export interface HitEscalateRequest {
  escalatedToUserId?: number;
  reasonEn?: string;
  reasonAr?: string;
}

const BASE = '/api/v1/aml/sanctions';

export const sanctionsApi = {
  // Lists
  listLists:    () => api.get<SanctionsList[]>(`${BASE}/lists`),
  getList:      (id: number) => api.get<SanctionsList>(`${BASE}/lists/${id}`),
  refreshList:  (id: number) => api.post<SanctionsList>(`${BASE}/lists/${id}/refresh`),
  listEntries:  (id: number) => api.get<SanctionsListEntry[]>(`${BASE}/lists/${id}/entries`),

  // Checks
  screen:       (req: ScreenRequest) => api.post<ScreeningCheck>(`${BASE}/checks`, req),
  listChecks:   (result?: CheckResult) => api.get<ScreeningCheck[]>(
    result ? `${BASE}/checks?result=${result}` : `${BASE}/checks`),
  getCheck:     (id: number) => api.get<ScreeningCheck>(`${BASE}/checks/${id}`),

  // Hits
  listHits:     (status: HitStatus = 'PENDING_REVIEW') =>
    api.get<ScreeningHit[]>(`${BASE}/hits?status=${status}`),
  reviewHit:    (id: number, req: HitReviewRequest) =>
    api.post<ScreeningHit>(`${BASE}/hits/${id}/review`, req),
  escalateHit:  (id: number, req: HitEscalateRequest) =>
    api.post<ScreeningHit>(`${BASE}/hits/${id}/escalate`, req),

  // Dashboard
  dashboard:    () => api.get<SanctionsDashboard>(`${BASE}/dashboard`),
};

// ── Display helpers ─────────────────────────────────────────────────

export function sourceTypeLabel(s: SourceType, isAr: boolean): string {
  if (isAr) {
    const m: Record<SourceType, string> = {
      UN: 'الأمم المتحدة',
      OFAC: 'مكتب الأصول الأجنبية (OFAC)',
      EU: 'الاتحاد الأوروبي',
      SAMA: 'مؤسسة النقد العربي السعودي (ساما)',
      KSA_NATIONAL: 'القائمة الوطنية السعودية',
      FATF: 'مجموعة العمل المالي (FATF)',
      OTHER: 'مصدر آخر',
    };
    return m[s];
  }
  const m: Record<SourceType, string> = {
    UN: 'United Nations',
    OFAC: 'OFAC (US Treasury)',
    EU: 'European Union',
    SAMA: 'SAMA',
    KSA_NATIONAL: 'KSA National',
    FATF: 'FATF',
    OTHER: 'Other',
  };
  return m[s];
}

export function resultLabel(r: CheckResult, isAr: boolean): string {
  if (isAr) {
    const m: Record<CheckResult, string> = {
      NO_HIT: 'لا تطابق',
      POTENTIAL_HIT: 'تطابق احتمالي',
      CONFIRMED_HIT: 'تطابق مُؤكّد',
    };
    return m[r];
  }
  const m: Record<CheckResult, string> = {
    NO_HIT: 'No hit',
    POTENTIAL_HIT: 'Potential hit',
    CONFIRMED_HIT: 'Confirmed hit',
  };
  return m[r];
}

export function hitStatusLabel(s: HitStatus, isAr: boolean): string {
  if (isAr) {
    const m: Record<HitStatus, string> = {
      PENDING_REVIEW: 'في انتظار المراجعة',
      TRUE_POSITIVE: 'إيجابي حقيقي',
      FALSE_POSITIVE: 'إيجابي خاطئ',
      ESCALATED: 'تمّ التصعيد',
      BLOCK_RECOMMENDED: 'يُوصى بالحجب',
      RELEASE_RECOMMENDED: 'يُوصى بالإفراج',
      CLOSED: 'مُغلق',
    };
    return m[s];
  }
  const m: Record<HitStatus, string> = {
    PENDING_REVIEW: 'Pending review',
    TRUE_POSITIVE: 'True positive',
    FALSE_POSITIVE: 'False positive',
    ESCALATED: 'Escalated',
    BLOCK_RECOMMENDED: 'Block recommended',
    RELEASE_RECOMMENDED: 'Release recommended',
    CLOSED: 'Closed',
  };
  return m[s];
}

export function subjectTypeLabel(s: SubjectType, isAr: boolean): string {
  if (isAr) {
    const m: Record<SubjectType, string> = {
      CUSTOMER_ONBOARDING: 'استقبال عميل',
      TRANSACTION_COUNTERPARTY: 'طرف معاملة',
      BENEFICIAL_OWNER: 'مستفيد حقيقي',
      EMPLOYEE_KYC: 'موظف (اعرف عميلك)',
      VENDOR_KYC: 'مورد (اعرف عميلك)',
      AD_HOC: 'فحص فوري',
    };
    return m[s];
  }
  const m: Record<SubjectType, string> = {
    CUSTOMER_ONBOARDING: 'Customer onboarding',
    TRANSACTION_COUNTERPARTY: 'Transaction counterparty',
    BENEFICIAL_OWNER: 'Beneficial owner',
    EMPLOYEE_KYC: 'Employee KYC',
    VENDOR_KYC: 'Vendor KYC',
    AD_HOC: 'Ad-hoc',
  };
  return m[s];
}

export function entityTypeLabel(t: EntityType, isAr: boolean): string {
  if (isAr) {
    const m: Record<EntityType, string> = {
      INDIVIDUAL: 'فرد',
      ENTITY: 'كيان',
      VESSEL: 'سفينة',
      AIRCRAFT: 'طائرة',
    };
    return m[t];
  }
  const m: Record<EntityType, string> = {
    INDIVIDUAL: 'Individual',
    ENTITY: 'Entity',
    VESSEL: 'Vessel',
    AIRCRAFT: 'Aircraft',
  };
  return m[t];
}

export function matchTypeLabel(t: MatchType, isAr: boolean): string {
  if (isAr) {
    const m: Record<MatchType, string> = {
      EXACT_NAME: 'تطابق تامّ للاسم',
      FUZZY_NAME: 'تطابق تقريبي للاسم',
      ALIAS: 'مطابقة اسم بديل',
      DOB_MATCH: 'تطابق تاريخ الميلاد',
      DOCUMENT_MATCH: 'تطابق وثيقة هوية',
      COUNTRY_MATCH: 'تطابق البلد',
      COMPOSITE: 'تطابق مركّب',
    };
    return m[t];
  }
  const m: Record<MatchType, string> = {
    EXACT_NAME: 'Exact name',
    FUZZY_NAME: 'Fuzzy name',
    ALIAS: 'Alias',
    DOB_MATCH: 'DOB match',
    DOCUMENT_MATCH: 'Document match',
    COUNTRY_MATCH: 'Country match',
    COMPOSITE: 'Composite',
  };
  return m[t];
}
