// Demo-mode fallback data for the BCM module. Shapes are aligned with the
// backend DTOs in lib/api.ts so the dependency graph, BIA aggregates and
// exercise scorecards render with realistic numbers without authentication.
import type { BcmPlanDto, BcmProcessDto, BcmBiaDto, BcmExerciseDto } from '../../lib/api';

// ── BCM plans ──────────────────────────────────────────────────────────────
export const BCM_PLANS: BcmPlanDto[] = [
  { id: 1, planNameEn: 'Core Banking System Recovery', planNameAr: 'استعادة النظام المصرفي الأساسي',
    planType: 'BCP', version: '2.4', status: 'APPROVED',     nextReviewDate: '2026-09-15', createdAt: '2024-01-15' },
  { id: 2, planNameEn: 'Payment Gateway Recovery',     planNameAr: 'استعادة بوابة المدفوعات',
    planType: 'BCP', version: '3.1', status: 'APPROVED',     nextReviewDate: '2026-10-02', createdAt: '2024-02-08' },
  { id: 3, planNameEn: 'Customer Service Operations',  planNameAr: 'تشغيل خدمة العملاء',
    planType: 'BCP', version: '1.8', status: 'APPROVED',     nextReviewDate: '2026-08-28', createdAt: '2024-03-22' },
  { id: 4, planNameEn: 'Branch Network Continuity',    planNameAr: 'استمرارية شبكة الفروع',
    planType: 'BCP', version: '1.3', status: 'UNDER_REVIEW', nextReviewDate: '2026-10-10', createdAt: '2024-05-04' },
  { id: 5, planNameEn: 'Data Center Failover',         planNameAr: 'تحويل مركز البيانات',
    planType: 'DRP', version: '4.0', status: 'APPROVED',     nextReviewDate: '2026-10-22', createdAt: '2023-11-30' },
  { id: 6, planNameEn: 'Workforce Remote Operations',  planNameAr: 'العمل عن بُعد',
    planType: 'BCP', version: '2.0', status: 'DRAFT',        nextReviewDate: '2026-07-18', createdAt: '2025-10-01' },
  { id: 7, planNameEn: 'Crisis Management Plan',       planNameAr: 'خطة إدارة الأزمات',
    planType: 'CMP', version: '5.2', status: 'APPROVED',     nextReviewDate: '2026-09-30', createdAt: '2023-09-12' },
];

// ── Critical processes ─────────────────────────────────────────────────────
export const BCM_PROCESSES: BcmProcessDto[] = [
  { id: 11, processNameEn: 'Online Banking Authentication',  processNameAr: 'مصادقة الخدمات المصرفية الإلكترونية',
    ownerId: 1001, criticality: 'CRITICAL', status: 'ACTIVE' },
  { id: 12, processNameEn: 'Card Payment Authorization',     processNameAr: 'تفويض مدفوعات البطاقات',
    ownerId: 1002, criticality: 'CRITICAL', status: 'ACTIVE' },
  { id: 13, processNameEn: 'Salary Disbursement Run',        processNameAr: 'صرف الرواتب',
    ownerId: 1003, criticality: 'HIGH',     status: 'ACTIVE' },
  { id: 14, processNameEn: 'Customer Onboarding (KYC)',      processNameAr: 'استقطاب العملاء',
    ownerId: 1004, criticality: 'HIGH',     status: 'ACTIVE' },
  { id: 15, processNameEn: 'Branch Cash Operations',         processNameAr: 'العمليات النقدية بالفروع',
    ownerId: 1005, criticality: 'HIGH',     status: 'ACTIVE' },
  { id: 16, processNameEn: 'Regulatory Reporting (SAMA)',    processNameAr: 'التقارير التنظيمية للبنك المركزي',
    ownerId: 1006, criticality: 'CRITICAL', status: 'ACTIVE' },
  { id: 17, processNameEn: 'Loan Origination',               processNameAr: 'منح القروض',
    ownerId: 1007, criticality: 'MEDIUM',   status: 'ACTIVE' },
  { id: 18, processNameEn: 'Fraud Monitoring',               processNameAr: 'مراقبة الاحتيال',
    ownerId: 1008, criticality: 'CRITICAL', status: 'ACTIVE' },
  { id: 19, processNameEn: 'Treasury Settlement',            processNameAr: 'تسوية الخزينة',
    ownerId: 1009, criticality: 'HIGH',     status: 'ACTIVE' },
  { id: 20, processNameEn: 'IT Service Desk',                processNameAr: 'مكتب خدمة تقنية المعلومات',
    ownerId: 1010, criticality: 'MEDIUM',   status: 'ACTIVE' },
  { id: 21, processNameEn: 'Marketing Campaigns',            processNameAr: 'الحملات التسويقية',
    ownerId: 1011, criticality: 'LOW',      status: 'ACTIVE' },
];

// ── BIA records ────────────────────────────────────────────────────────────
export const BCM_BIA: BcmBiaDto[] = [
  { id: 31, processId: 11, assessmentDate: '2026-03-10', rtoHours: 1,    rpoHours: 0,    mtpdHours: 4,   overallImpact: 'CRITICAL', status: 'APPROVED', linkedRiskId: 5012 },
  { id: 32, processId: 12, assessmentDate: '2026-03-12', rtoHours: 0.5,  rpoHours: 0,    mtpdHours: 2,   overallImpact: 'CRITICAL', status: 'APPROVED', linkedRiskId: 5013 },
  { id: 33, processId: 13, assessmentDate: '2026-02-22', rtoHours: 24,   rpoHours: 6,    mtpdHours: 72,  overallImpact: 'HIGH',     status: 'APPROVED' },
  { id: 34, processId: 14, assessmentDate: '2026-03-02', rtoHours: 8,    rpoHours: 2,    mtpdHours: 24,  overallImpact: 'HIGH',     status: 'APPROVED' },
  { id: 35, processId: 15, assessmentDate: '2026-04-04', rtoHours: 4,    rpoHours: 1,    mtpdHours: 12,  overallImpact: 'HIGH',     status: 'UNDER_REVIEW' },
  { id: 36, processId: 16, assessmentDate: '2026-01-30', rtoHours: 24,   rpoHours: 24,   mtpdHours: 48,  overallImpact: 'CRITICAL', status: 'APPROVED' },
  { id: 37, processId: 17, assessmentDate: '2026-02-14', rtoHours: 72,   rpoHours: 12,   mtpdHours: 168, overallImpact: 'MEDIUM',   status: 'APPROVED' },
  { id: 38, processId: 18, assessmentDate: '2026-04-15', rtoHours: 1,    rpoHours: 0.25, mtpdHours: 4,   overallImpact: 'CRITICAL', status: 'APPROVED', linkedRiskId: 5018 },
  { id: 39, processId: 19, assessmentDate: '2026-03-25', rtoHours: 4,    rpoHours: 0.5,  mtpdHours: 12,  overallImpact: 'HIGH',     status: 'APPROVED' },
  { id: 40, processId: 20, assessmentDate: '2026-02-04', rtoHours: 24,   rpoHours: 8,    mtpdHours: 72,  overallImpact: 'MEDIUM',   status: 'APPROVED' },
];

// ── Exercises ──────────────────────────────────────────────────────────────
export const BCM_EXERCISES: BcmExerciseDto[] = [
  { id: 51, planId: 5, exerciseNameEn: 'Datacenter Failover Drill (Q1)', exerciseNameAr: 'تمرين تحويل مركز البيانات',
    exerciseType: 'FAILOVER',    scenario: 'Loss of primary site cooling — full failover to DR.',
    scheduledDate: '2026-02-08', executedDate: '2026-02-08', score: 92, status: 'COMPLETED',
    findings: 'RTO met (54 min vs. 1h target). Two minor runbook gaps identified.',
    capaCount: 2, facilitator: 'Ahmed Al-Qahtani' },
  { id: 52, planId: 1, exerciseNameEn: 'Core Banking Walkthrough',       exerciseNameAr: 'استعراض النظام المصرفي الأساسي',
    exerciseType: 'WALKTHROUGH', scenario: 'Tabletop walkthrough of recovery sequence with operations team.',
    scheduledDate: '2026-03-05', executedDate: '2026-03-05', score: 88, status: 'COMPLETED',
    findings: 'Sequence understood; vendor escalation contacts need refresh.',
    capaCount: 1, facilitator: 'Mohammed Al-Otaibi' },
  { id: 53, planId: 2, exerciseNameEn: 'Payment Gateway Tabletop',       exerciseNameAr: 'محاكاة بوابة المدفوعات',
    exerciseType: 'TABLETOP',    scenario: 'Mastercard authorization service degraded for 6 hours.',
    scheduledDate: '2026-03-22', executedDate: '2026-03-22', score: 76, status: 'COMPLETED',
    findings: 'Customer-comms drafts not pre-approved; created friction.',
    capaCount: 3, facilitator: 'Layla Al-Saud' },
  { id: 54, planId: 3, exerciseNameEn: 'Contact-Centre Surge Test',      exerciseNameAr: 'اختبار اندفاع مركز الاتصال',
    exerciseType: 'SIMULATION',  scenario: 'Volume spike to 3× typical inbound after a service outage.',
    scheduledDate: '2026-04-15', executedDate: '2026-04-15', score: 81, status: 'COMPLETED',
    findings: 'Overflow IVR to backup vendor worked; queueing UX needs improvement.',
    capaCount: 2, facilitator: 'Khalid Al-Ghamdi' },
  { id: 55, planId: 7, exerciseNameEn: 'Crisis Management Tabletop',     exerciseNameAr: 'محاكاة إدارة الأزمات',
    exerciseType: 'TABLETOP',    scenario: 'Cyber-physical incident with regulator notification timeline.',
    scheduledDate: '2026-04-22', executedDate: '2026-04-22', score: 85, status: 'COMPLETED',
    findings: 'NCA HEY notification template needs Arabic localisation.',
    capaCount: 2, facilitator: 'Faisal Al-Maliki' },
  { id: 56, planId: 4, exerciseNameEn: 'Branch Network Failover',        exerciseNameAr: 'تحويل شبكة الفروع',
    exerciseType: 'FAILOVER',    scenario: 'WAN-link outage in 3 branches; automatic LTE failover.',
    scheduledDate: '2026-05-12', status: 'PLANNED',
    facilitator: 'Sara Al-Dosari' },
  { id: 57, planId: 6, exerciseNameEn: 'Remote-work Capacity Test',      exerciseNameAr: 'اختبار سعة العمل عن بُعد',
    exerciseType: 'SIMULATION',  scenario: 'Full HQ evacuation; 2,400 staff working remotely for 48h.',
    scheduledDate: '2026-06-04', status: 'PLANNED',
    facilitator: 'Noura Al-Harbi' },
];
