// Backup + DR — Sprint 6 production hardening.
//
// Saudi-supervised entities (SAMA, NCA-classified critical) need:
//   • Daily backup of every Tier-0 system
//   • Replication to a second KSA region (we use AWS Riyadh ↔ Jeddah)
//   • Quarterly verified restore drill with documented RTO actuals
//   • SAMA Operational Resilience Framework §3 compliance evidence
//
// This module models the backup-job catalogue, replication health, and
// the DR-drill log. Production wires to AWS Backup + RDS replication +
// the existing chain engine for "drill scheduled" reminders.

export type BackupKind = 'DATABASE' | 'FILES' | 'CONFIG' | 'IMAGES' | 'APPLICATION_STATE';
export type BackupHealth = 'OK' | 'WARNING' | 'FAILED' | 'NOT_RUN';
export type ReplicationStatus = 'IN_SYNC' | 'LAGGING' | 'FAILED';
export type DrillResult = 'PASS' | 'PASS_WITH_NOTES' | 'FAIL';

export interface BackupJob {
  code: string;
  nameEn: string;
  nameAr: string;
  kind: BackupKind;
  /** Cadence — "DAILY" / "HOURLY" / "WEEKLY". */
  cadence: 'HOURLY' | 'DAILY' | 'WEEKLY';
  /** Target — "S3 / Riyadh", "Glacier / Jeddah", … */
  target: string;
  /** Last successful backup timestamp. */
  lastBackupAt?: string;
  /** Size in GB. */
  sizeGb?: number;
  /** Backup retention in days. */
  retentionDays: number;
  /** Encryption status. */
  encrypted: boolean;
  health: BackupHealth;
}

export interface ReplicationLink {
  code: string;
  fromRegionEn: string;
  fromRegionAr: string;
  toRegionEn: string;
  toRegionAr: string;
  status: ReplicationStatus;
  /** Replication lag in seconds. */
  lagSeconds: number;
  /** RPO target in seconds (data we can afford to lose). */
  rpoTargetSeconds: number;
  /** Last verified at. */
  lastVerifiedAt?: string;
}

export interface DrDrill {
  code: string;
  titleEn: string;
  titleAr: string;
  scheduledFor: string;
  conductedAt?: string;
  result?: DrillResult;
  /** Actual RTO — how long it took to be back online. */
  actualRtoMinutes?: number;
  /** Target RTO. */
  targetRtoMinutes: number;
  /** Lessons learned (only if conducted). */
  lessonsEn?: string;
  lessonsAr?: string;
  /** Auditor or observer who certified the result. */
  certifiedBy?: string;
}

const _now = Date.now();
const _hoursAgo = (h: number) => new Date(_now - h * 3_600_000).toISOString();
const _daysAgo = (d: number) => new Date(_now - d * 86_400_000).toISOString().slice(0, 10);
const _daysAhead = (d: number) => new Date(_now + d * 86_400_000).toISOString().slice(0, 10);

// ─── Backup jobs ───────────────────────────────────────────────────────────

export const BACKUP_JOBS: BackupJob[] = [
  {
    code: 'BKP-DB-PRIMARY',
    nameEn: 'SQL Server primary — full + tx-log',
    nameAr: 'SQL Server الأساسي — كامل + سجلّ المعاملات',
    kind: 'DATABASE',
    cadence: 'HOURLY',
    target: 'AWS S3 (Riyadh) + Glacier (Jeddah)',
    lastBackupAt: _hoursAgo(0.7),
    sizeGb: 142,
    retentionDays: 365 * 7, encrypted: true,
    health: 'OK',
  },
  {
    code: 'BKP-FILES',
    nameEn: 'File metadata + S3 object lock',
    nameAr: 'بيانات الملفات + قفل كائنات S3',
    kind: 'FILES',
    cadence: 'DAILY',
    target: 'AWS S3 (Riyadh) cross-region replication',
    lastBackupAt: _hoursAgo(8),
    sizeGb: 380,
    retentionDays: 365 * 10, encrypted: true,
    health: 'OK',
  },
  {
    code: 'BKP-CONFIG',
    nameEn: 'Spring config + secrets vault',
    nameAr: 'إعدادات Spring + خزينة الأسرار',
    kind: 'CONFIG',
    cadence: 'DAILY',
    target: 'AWS Secrets Manager + S3 versioned',
    lastBackupAt: _hoursAgo(12),
    sizeGb: 0.4,
    retentionDays: 365 * 3, encrypted: true,
    health: 'OK',
  },
  {
    code: 'BKP-VM-IMAGES',
    nameEn: 'Application server VM images',
    nameAr: 'صور خوادم التطبيق',
    kind: 'IMAGES',
    cadence: 'WEEKLY',
    target: 'AWS AMI (Riyadh) + replicate (Jeddah)',
    lastBackupAt: _daysAgo(3),
    sizeGb: 84,
    retentionDays: 90, encrypted: true,
    health: 'OK',
  },
  {
    code: 'BKP-LOCALSTORAGE',
    nameEn: 'Persistence rows (legacy localStorage migration)',
    nameAr: 'سجلات التخزين الدائم',
    kind: 'APPLICATION_STATE',
    cadence: 'DAILY',
    target: 'AWS S3 (Riyadh)',
    lastBackupAt: _hoursAgo(20),
    sizeGb: 0.2,
    retentionDays: 365 * 3, encrypted: true,
    health: 'WARNING',
  },
];

// ─── Replication links ─────────────────────────────────────────────────────

export const REPLICATION_LINKS: ReplicationLink[] = [
  {
    code: 'REPL-DB-PRIMARY',
    fromRegionEn: 'AWS Riyadh (me-central-1)',
    fromRegionAr: 'AWS الرياض',
    toRegionEn: 'AWS Jeddah (DR)',
    toRegionAr: 'AWS جدّة (DR)',
    status: 'IN_SYNC',
    lagSeconds: 3,
    rpoTargetSeconds: 5,
    lastVerifiedAt: _hoursAgo(0.3),
  },
  {
    code: 'REPL-S3-FILES',
    fromRegionEn: 'AWS Riyadh',
    fromRegionAr: 'AWS الرياض',
    toRegionEn: 'AWS Jeddah',
    toRegionAr: 'AWS جدّة',
    status: 'IN_SYNC',
    lagSeconds: 12,
    rpoTargetSeconds: 60,
    lastVerifiedAt: _hoursAgo(1),
  },
  {
    code: 'REPL-LDAP',
    fromRegionEn: 'AWS Riyadh',
    fromRegionAr: 'AWS الرياض',
    toRegionEn: 'AWS Jeddah',
    toRegionAr: 'AWS جدّة',
    status: 'LAGGING',
    lagSeconds: 35,
    rpoTargetSeconds: 30,
    lastVerifiedAt: _hoursAgo(2),
  },
];

// ─── DR drills ─────────────────────────────────────────────────────────────

export const DR_DRILLS: DrDrill[] = [
  {
    code: 'DR-2026-Q1',
    titleEn: 'Q1 2026 — Full Tier-0 failover to AWS Jeddah',
    titleAr: 'الربع الأول 2026 — تحوّل كامل Tier-0 إلى AWS جدّة',
    scheduledFor: _daysAgo(85),
    conductedAt: _daysAgo(83),
    result: 'PASS_WITH_NOTES',
    actualRtoMinutes: 47,
    targetRtoMinutes: 60,
    lessonsEn: 'Failover succeeded under target. SAMA Treasury notification cron path needs hardening — ran 18 min late.',
    lessonsAr: 'نجح التحوّل تحت الهدف. مسار إشعار خزينة ساما يحتاج إلى تقوية — تأخّر 18 دقيقة.',
    certifiedBy: 'EY MENA — observer',
  },
  {
    code: 'DR-2025-Q4',
    titleEn: 'Q4 2025 — Database point-in-time restore drill',
    titleAr: 'الربع الرابع 2025 — تمرين استعادة قاعدة البيانات لوقت محدد',
    scheduledFor: _daysAgo(160),
    conductedAt: _daysAgo(158),
    result: 'PASS',
    actualRtoMinutes: 32,
    targetRtoMinutes: 60,
    lessonsEn: 'Clean run. PITR restored within 32 minutes; data integrity verified against pre-drill checksum.',
    lessonsAr: 'تشغيل نظيف. الاستعادة لوقت محدد خلال 32 دقيقة؛ تمّ التحقق من سلامة البيانات.',
    certifiedBy: 'Internal Audit',
  },
  {
    code: 'DR-2026-Q3-PLANNED',
    titleEn: 'Q3 2026 — Cross-sector NCEMA cyber drill',
    titleAr: 'الربع الثالث 2026 — تمرين NCEMA عبر القطاعات',
    scheduledFor: _daysAhead(35),
    targetRtoMinutes: 60,
  },
  {
    code: 'DR-2026-Q4-PLANNED',
    titleEn: 'Q4 2026 — Application-state restore (workflows + chains + persistence)',
    titleAr: 'الربع الرابع 2026 — استعادة حالة التطبيق',
    scheduledFor: _daysAhead(120),
    targetRtoMinutes: 90,
  },
];

// ─── Display ───────────────────────────────────────────────────────────────

export const HEALTH_TONE: Record<BackupHealth, 'success' | 'warning' | 'danger' | 'neutral'> = {
  OK: 'success', WARNING: 'warning', FAILED: 'danger', NOT_RUN: 'neutral',
};

export const REPL_TONE: Record<ReplicationStatus, 'success' | 'warning' | 'danger'> = {
  IN_SYNC: 'success', LAGGING: 'warning', FAILED: 'danger',
};

export const DRILL_TONE: Record<DrillResult, 'success' | 'warning' | 'danger'> = {
  PASS: 'success', PASS_WITH_NOTES: 'warning', FAIL: 'danger',
};

export interface BackupDrStats {
  totalJobs: number;
  okJobs: number;
  failedJobs: number;
  totalReplLinks: number;
  inSyncLinks: number;
  laggingLinks: number;
  drillsCompleted: number;
  drillsPlanned: number;
  lastDrillRtoMin?: number;
  /** All backups OK + all repl in-sync + at least one PASS drill. */
  drReady: boolean;
}

export function aggregateBackupDr(): BackupDrStats {
  const okJobs = BACKUP_JOBS.filter(j => j.health === 'OK').length;
  const failedJobs = BACKUP_JOBS.filter(j => j.health === 'FAILED').length;
  const inSyncLinks = REPLICATION_LINKS.filter(l => l.status === 'IN_SYNC').length;
  const laggingLinks = REPLICATION_LINKS.filter(l => l.status === 'LAGGING').length;
  const completed = DR_DRILLS.filter(d => d.conductedAt);
  const planned = DR_DRILLS.filter(d => !d.conductedAt);
  const lastDrillRtoMin = completed[0]?.actualRtoMinutes;
  const drReady = failedJobs === 0
    && REPLICATION_LINKS.every(l => l.status !== 'FAILED')
    && completed.some(d => d.result === 'PASS' || d.result === 'PASS_WITH_NOTES');
  return {
    totalJobs: BACKUP_JOBS.length, okJobs, failedJobs,
    totalReplLinks: REPLICATION_LINKS.length,
    inSyncLinks, laggingLinks,
    drillsCompleted: completed.length,
    drillsPlanned: planned.length,
    lastDrillRtoMin,
    drReady,
  };
}
