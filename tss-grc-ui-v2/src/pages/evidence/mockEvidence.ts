// Demo-mode fallback for the Evidence Management module. Aligned with backend
// DTOs in lib/api.ts so framework-readiness charts and review workflow render
// without authentication.
import type { EvidenceItemDto, EvidenceRequestDto, EvidenceReviewDto } from '../../lib/api';
import { ANCHOR_PACKS, type RegulatorAnchor } from '../../lib/evidenceAnchors';

/**
 * Multi-authority anchor map: each evidence id is bound to one or more
 * authority + framework + control citations, tracking the control-status
 * each authority sees. ServiceNow IRM "Citation" pattern.
 */
export const EVIDENCE_ANCHORS_BY_ID: Record<number, RegulatorAnchor[]> = {
  1: ANCHOR_PACKS.ISMS_POLICY,
  2: ANCHOR_PACKS.VULN_SCAN,
  3: ANCHOR_PACKS.FIREWALL_RULES,
  4: ANCHOR_PACKS.PDPL_INVENTORY,
  5: ANCHOR_PACKS.BACKUP_VALIDATION,
  6: ANCHOR_PACKS.SAMA_ATTESTATION,
  7: ANCHOR_PACKS.PAM_RECERT,
  8: ANCHOR_PACKS.AUDIT_PLAN,
  9: ANCHOR_PACKS.AWARENESS,
};

// ── Catalog (evidence items) ────────────────────────────────────────────────
export const EVIDENCE: EvidenceItemDto[] = [
  { id: 1, evidenceCode: 'EV-1083', titleEn: 'Information Security Policy v3.2',           titleAr: 'سياسة أمن المعلومات الإصدار 3.2',
    descriptionEn: 'Authoritative ISMS policy approved by the executive board.',
    evidenceType: 'POLICY',        status: 'APPROVED',
    subControlCode: 'A.5.1.1', controlCode: 'A.5.1', frameworkCode: 'ISO_27001',
    collectedDate: '2026-04-15', expiryDate: '2027-04-15' },
  { id: 2, evidenceCode: 'EV-1082', titleEn: 'Q1 2026 Vulnerability Scan Report',          titleAr: 'تقرير فحص الثغرات للربع الأول',
    descriptionEn: 'Tenable Nessus quarterly scan against external + internal estate.',
    evidenceType: 'REPORT',        status: 'APPROVED',
    subControlCode: '5.2.4', controlCode: '5.2', frameworkCode: 'NCA_ECC',
    collectedDate: '2026-04-01', expiryDate: '2026-07-01' },
  { id: 3, evidenceCode: 'EV-1081', titleEn: 'Firewall Rules Configuration Snapshot',      titleAr: 'لقطة من إعدادات الجدار الناري',
    descriptionEn: 'Annotated export of perimeter firewall rules; includes change-IDs.',
    evidenceType: 'CONFIGURATION', status: 'PENDING_REVIEW',
    subControlCode: 'A.8.20.1', controlCode: 'A.8.20', frameworkCode: 'ISO_27001',
    collectedDate: '2026-04-12', expiryDate: '2026-05-12' },
  { id: 4, evidenceCode: 'EV-1080', titleEn: 'PDPL Data Inventory Report Q4 2025',         titleAr: 'تقرير قائمة البيانات الشخصية للربع الرابع',
    descriptionEn: 'Mapping of personal-data flows per PDPL Article 30 requirements.',
    evidenceType: 'REPORT',        status: 'PENDING_REVIEW',
    subControlCode: '4.3.1', controlCode: '4.3', frameworkCode: 'PDPL',
    collectedDate: '2026-04-08', expiryDate: '2026-05-08' },
  { id: 5, evidenceCode: 'EV-1079', titleEn: 'Backup Validation Screenshot (Apr 2026)',    titleAr: 'لقطة التحقق من النسخ الاحتياطية',
    descriptionEn: 'Restore-test evidence for the financial-systems backup chain.',
    evidenceType: 'SCREENSHOT',    status: 'APPROVED',
    subControlCode: '8.13.1', controlCode: '8.13', frameworkCode: 'ISO_22301',
    collectedDate: '2026-04-10', expiryDate: '2026-08-10' },
  { id: 6, evidenceCode: 'EV-1078', titleEn: 'SAMA CSF Attestation Certificate 2025',      titleAr: 'شهادة الالتزام بإطار البنك المركزي',
    descriptionEn: 'External-auditor signed CSF compliance certificate.',
    evidenceType: 'CERTIFICATE',   status: 'EXPIRED',
    subControlCode: 'CSF-1.1.1', controlCode: 'CSF-1.1', frameworkCode: 'SAMA_CSF',
    collectedDate: '2025-04-30', expiryDate: '2026-04-30' },
  { id: 7, evidenceCode: 'EV-1077', titleEn: 'Q1 PAM Recertification Log',                 titleAr: 'سجل اعتماد الصلاحيات للربع الأول',
    descriptionEn: 'Privileged-access recertification log signed by line managers.',
    evidenceType: 'LOG',           status: 'APPROVED',
    subControlCode: 'AC-2.1.2.a', controlCode: 'AC-2.1.2', frameworkCode: 'NCA_ECC',
    collectedDate: '2026-03-31', expiryDate: '2026-06-30' },
  { id: 8, evidenceCode: 'EV-1076', titleEn: 'Annual ISMS Internal Audit Plan',            titleAr: 'خطة المراجعة الداخلية لنظام إدارة أمن المعلومات',
    descriptionEn: 'Approved 2026 internal audit plan; quarterly cadence.',
    evidenceType: 'DOCUMENT',      status: 'APPROVED',
    subControlCode: 'A.5.35.1', controlCode: 'A.5.35', frameworkCode: 'ISO_27001',
    collectedDate: '2026-01-15', expiryDate: '2026-12-31' },
  { id: 9, evidenceCode: 'EV-1075', titleEn: 'Phishing Awareness Campaign Results',        titleAr: 'نتائج حملة التوعية ضد التصيد',
    descriptionEn: 'Click-rate metrics from quarterly phishing simulation.',
    evidenceType: 'REPORT',        status: 'APPROVED',
    subControlCode: 'A.6.3.1', controlCode: 'A.6.3', frameworkCode: 'ISO_27001',
    collectedDate: '2026-04-05', expiryDate: '2026-07-05' },
];

// ── Requests ──────────────────────────────────────────────────────────────
export const EVIDENCE_REQUESTS: EvidenceRequestDto[] = [
  { id: 101, requestCode: 'REQ-2026-042', titleEn: 'Provide H1 2026 firewall change log',  titleAr: 'سجل تغييرات الجدار الناري للنصف الأول',
    subControlCode: 'A.8.20.1', frameworkCode: 'ISO_27001',
    status: 'OPEN', requestedAt: '2026-04-22', dueDate: '2026-05-06', ownerName: 'network.team@tss.sa' },
  { id: 102, requestCode: 'REQ-2026-041', titleEn: 'Quarterly access recertification (Q2)', titleAr: 'إعادة اعتماد الصلاحيات للربع الثاني',
    subControlCode: 'AC-2.1.1.c', frameworkCode: 'NCA_ECC',
    status: 'IN_PROGRESS', requestedAt: '2026-04-08', dueDate: '2026-04-30', ownerName: 'iam.team@tss.sa' },
  { id: 103, requestCode: 'REQ-2026-040', titleEn: 'BCP test results (datacenter failover)', titleAr: 'نتائج اختبار خطة الاستمرارية',
    subControlCode: '8.5.1', frameworkCode: 'ISO_22301',
    status: 'COMPLETED', requestedAt: '2026-02-04', dueDate: '2026-02-15', ownerName: 'bcm.team@tss.sa' },
  { id: 104, requestCode: 'REQ-2026-039', titleEn: 'PDPL data-inventory refresh',          titleAr: 'تحديث قائمة البيانات الشخصية',
    subControlCode: '4.3.1', frameworkCode: 'PDPL',
    status: 'OVERDUE', requestedAt: '2026-03-15', dueDate: '2026-04-15', ownerName: 'dpo@tss.sa' },
  { id: 105, requestCode: 'REQ-2026-038', titleEn: 'PAM session-recording sample',          titleAr: 'عيّنة من تسجيلات الجلسات المميزة',
    subControlCode: 'AC-2.1.2.a', frameworkCode: 'NCA_ECC',
    status: 'COMPLETED', requestedAt: '2026-02-22', dueDate: '2026-03-08', ownerName: 'pam.team@tss.sa' },
  { id: 106, requestCode: 'REQ-2026-037', titleEn: 'SAMA CSF re-attestation evidence',      titleAr: 'دليل إعادة اعتماد إطار البنك المركزي',
    subControlCode: 'CSF-1.1.1', frameworkCode: 'SAMA_CSF',
    status: 'OPEN', requestedAt: '2026-04-25', dueDate: '2026-05-25', ownerName: 'compliance.lead@tss.sa' },
];

// ── Reviews ──────────────────────────────────────────────────────────────
export const EVIDENCE_REVIEWS: EvidenceReviewDto[] = [
  { id: 201, evidenceId: 1, evidenceTitleEn: 'Information Security Policy v3.2',
    reviewerName: 'CISO', reviewDate: '2026-04-16', result: 'APPROVED',
    notesEn: 'Policy aligned with ISO 27001:2022; board-approved minutes attached.' },
  { id: 202, evidenceId: 2, evidenceTitleEn: 'Q1 2026 Vulnerability Scan Report',
    reviewerName: 'Lead Auditor', reviewDate: '2026-04-04', result: 'APPROVED',
    notesEn: 'All critical vulnerabilities tracked under existing CAPA records.' },
  { id: 203, evidenceId: 3, evidenceTitleEn: 'Firewall Rules Configuration Snapshot',
    reviewerName: 'Network Reviewer', reviewDate: '2026-04-14', result: 'CHANGES_REQUESTED',
    notesEn: 'Three any-any rules need owner attestation before approval.' },
  { id: 204, evidenceId: 5, evidenceTitleEn: 'Backup Validation Screenshot (Apr 2026)',
    reviewerName: 'BCM Coordinator', reviewDate: '2026-04-12', result: 'APPROVED',
    notesEn: 'Restore RPO of 14 minutes — within tolerance.' },
  { id: 205, evidenceId: 7, evidenceTitleEn: 'Q1 PAM Recertification Log',
    reviewerName: 'Lead Auditor', reviewDate: '2026-04-02', result: 'APPROVED',
    notesEn: 'Recertification rate 96%; outstanding items moved to CAPA.' },
  { id: 206, evidenceId: 9, evidenceTitleEn: 'Phishing Awareness Campaign Results',
    reviewerName: 'Awareness Lead', reviewDate: '2026-04-08', result: 'APPROVED',
    notesEn: 'Click-rate 4.2% — below 5% target. Repeat-offenders enrolled in remedial training.' },
];
