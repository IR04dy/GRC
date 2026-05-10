// Monitoring + alerting — Sprint 6 production hardening.
//
// Production exposes Prometheus metrics from every Spring Boot service;
// alertmanager fans out to Teams + WhatsApp (CISO) + SMS (on-call).
// SLOs are backed by error-budget burn-rate alerts. NCEMA cross-sector
// readiness checks run weekly.
//
// Frontend reads the snapshot via /actuator/prometheus + /actuator/health
// in production. In demo this lib serves seeded data so the dashboard
// renders before infra exists.

export type ServiceHealth = 'OK' | 'DEGRADED' | 'DOWN' | 'UNKNOWN';

export interface ServiceStatus {
  code: string;
  nameEn: string;
  nameAr: string;
  health: ServiceHealth;
  /** Uptime % over the last 30 days. */
  uptime30d: number;
  /** P95 latency ms. */
  latencyP95Ms: number;
  /** Error rate over last hour (% of requests). */
  errorRatePct: number;
  /** Last health-check timestamp. */
  lastCheckedAt: string;
}

export type AlertSeverity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';

export interface ActiveAlert {
  id: string;
  rule: string;
  service: string;
  severity: AlertSeverity;
  startedAt: string;
  /** Bilingual narrative. */
  narrativeEn: string;
  narrativeAr: string;
  acknowledged: boolean;
}

export interface Slo {
  code: string;
  nameEn: string;
  nameAr: string;
  /** Target SLO (%). */
  target: number;
  /** Actual % over the window. */
  actual: number;
  /** Window — 30d, 7d, 1h. */
  window: string;
  /** Error-budget burn rate (1.0 = on-track, > 1 = burning fast). */
  burnRate: number;
}

export interface OnCallSlot {
  date: string;
  primaryEn: string;
  primaryAr: string;
  backupEn: string;
  backupAr: string;
}

const _now = Date.now();
const _hoursAgo = (h: number) => new Date(_now - h * 3_600_000).toISOString();
const _minAgo = (m: number) => new Date(_now - m * 60_000).toISOString();
const _daysAhead = (d: number) => new Date(_now + d * 86_400_000).toISOString().slice(0, 10);
const _today = new Date().toISOString().slice(0, 10);

// ─── Service health ────────────────────────────────────────────────────────

export const SERVICES: ServiceStatus[] = [
  { code: 'api-gateway',  nameEn: 'API Gateway',                nameAr: 'بوابة API',
    health: 'OK', uptime30d: 99.97, latencyP95Ms: 142, errorRatePct: 0.02, lastCheckedAt: _minAgo(0.5) },
  { code: 'auth-service', nameEn: 'Auth Service (JWT + MFA)',    nameAr: 'خدمة المصادقة',
    health: 'OK', uptime30d: 99.99, latencyP95Ms: 88,  errorRatePct: 0.01, lastCheckedAt: _minAgo(0.5) },
  { code: 'risk-module',  nameEn: 'Risk module',                  nameAr: 'وحدة المخاطر',
    health: 'OK', uptime30d: 99.95, latencyP95Ms: 220, errorRatePct: 0.04, lastCheckedAt: _minAgo(1) },
  { code: 'compliance',   nameEn: 'Compliance module',            nameAr: 'وحدة الامتثال',
    health: 'OK', uptime30d: 99.92, latencyP95Ms: 310, errorRatePct: 0.05, lastCheckedAt: _minAgo(1) },
  { code: 'workflow',     nameEn: 'Workflow engine',              nameAr: 'محرّك سير العمل',
    health: 'DEGRADED', uptime30d: 99.71, latencyP95Ms: 580, errorRatePct: 0.18, lastCheckedAt: _minAgo(2) },
  { code: 'sql-server',   nameEn: 'SQL Server primary',           nameAr: 'SQL Server الأساسي',
    health: 'OK', uptime30d: 99.99, latencyP95Ms: 14,  errorRatePct: 0.00, lastCheckedAt: _minAgo(0.2) },
  { code: 's3-files',     nameEn: 'S3 file storage',              nameAr: 'تخزين الملفات S3',
    health: 'OK', uptime30d: 99.99, latencyP95Ms: 92,  errorRatePct: 0.01, lastCheckedAt: _minAgo(1) },
  { code: 'splunk-siem',  nameEn: 'Splunk SIEM ingest',           nameAr: 'تدفق Splunk SIEM',
    health: 'OK', uptime30d: 99.91, latencyP95Ms: 410, errorRatePct: 0.06, lastCheckedAt: _minAgo(3) },
  { code: 'aws-jeddah-dr', nameEn: 'AWS Jeddah DR replication',    nameAr: 'النسخ المتماثل AWS جدّة',
    health: 'OK', uptime30d: 99.94, latencyP95Ms: 8000, errorRatePct: 0.02, lastCheckedAt: _minAgo(5) },
];

// ─── Active alerts ─────────────────────────────────────────────────────────

export const ACTIVE_ALERTS: ActiveAlert[] = [
  {
    id: 'A-2026-1023',
    rule: 'workflow_engine.p95_latency_breach',
    service: 'workflow',
    severity: 'MEDIUM',
    startedAt: _hoursAgo(2.5),
    narrativeEn: 'Workflow engine P95 latency above 500ms for 30 minutes. SLO target: 300ms. Investigation: chain-rule re-evaluation hot path.',
    narrativeAr: 'زمن استجابة P95 لمحرّك سير العمل تجاوز 500 مللي ثانية لمدّة 30 دقيقة. هدف SLO: 300 مللي ثانية.',
    acknowledged: true,
  },
  {
    id: 'A-2026-1024',
    rule: 'aws_jeddah_dr.replication_lag',
    service: 'aws-jeddah-dr',
    severity: 'LOW',
    startedAt: _hoursAgo(0.7),
    narrativeEn: 'Replication lag to AWS Jeddah peaked at 12s (target ≤ 5s). Self-healed; under SAMA OR Framework 5s threshold for the rolling window.',
    narrativeAr: 'تأخر النسخ إلى AWS جدّة بلغ 12 ثانية (الهدف ≤ 5 ثوان). شُفي ذاتياً.',
    acknowledged: true,
  },
];

// ─── SLOs ──────────────────────────────────────────────────────────────────

export const SLOS: Slo[] = [
  { code: 'platform-availability-30d', nameEn: 'Platform availability (30d)', nameAr: 'توفر المنصة (30 يوم)',
    target: 99.95, actual: 99.94, window: '30d', burnRate: 1.2 },
  { code: 'api-p95-latency-7d', nameEn: 'API P95 latency (7d)', nameAr: 'زمن استجابة API P95 (7 أيام)',
    target: 99.0, actual: 99.7, window: '7d', burnRate: 0.3 },
  { code: 'tier0-rto-1h', nameEn: 'Tier-0 service RTO (≤ 1h)', nameAr: 'هدف الاستعادة Tier-0 (≤ ساعة)',
    target: 99.5, actual: 100.0, window: '90d', burnRate: 0.0 },
  { code: 'pdpl-72h-compliance', nameEn: 'PDPL 72h breach notification', nameAr: 'إبلاغ PDPL 72 ساعة',
    target: 100.0, actual: 100.0, window: '365d', burnRate: 0.0 },
];

// ─── On-call rotation ──────────────────────────────────────────────────────

export const ON_CALL: OnCallSlot[] = [
  { date: _today,
    primaryEn: 'Mohammed Al-Ahmadi', primaryAr: 'محمد الأحمدي',
    backupEn: 'Ahmed Al-Qahtani',     backupAr: 'أحمد القحطاني' },
  { date: _daysAhead(7),
    primaryEn: 'Sara Al-Dosari',      primaryAr: 'سارة الدوسري',
    backupEn: 'Layla Al-Saud',         backupAr: 'ليلى السعود' },
  { date: _daysAhead(14),
    primaryEn: 'Sami Al-Otaibi',      primaryAr: 'سامي العتيبي',
    backupEn: 'Mona Al-Bishi',         backupAr: 'منى البيشي' },
  { date: _daysAhead(21),
    primaryEn: 'Faisal Al-Maliki',    primaryAr: 'فيصل المالكي',
    backupEn: 'Hassan Al-Saud',        backupAr: 'حسن السعود' },
];

// ─── Display ───────────────────────────────────────────────────────────────

export const HEALTH_TONE: Record<ServiceHealth, 'success' | 'warning' | 'danger' | 'neutral'> = {
  OK: 'success', DEGRADED: 'warning', DOWN: 'danger', UNKNOWN: 'neutral',
};

export const HEALTH_LABEL: Record<ServiceHealth, { en: string; ar: string }> = {
  OK:        { en: 'Healthy',   ar: 'سليم' },
  DEGRADED:  { en: 'Degraded',  ar: 'متدهور' },
  DOWN:      { en: 'Down',      ar: 'متعطّل' },
  UNKNOWN:   { en: 'Unknown',   ar: 'غير معروف' },
};

export const SEVERITY_TONE: Record<AlertSeverity, 'info' | 'warning' | 'danger'> = {
  LOW: 'info', MEDIUM: 'warning', HIGH: 'danger', CRITICAL: 'danger',
};

export interface MonitoringStats {
  totalServices: number;
  healthyServices: number;
  degradedServices: number;
  downServices: number;
  activeAlerts: number;
  unacknowledgedAlerts: number;
  slosOnTrack: number;
  totalSlos: number;
  /** Whether monitoring is wired enough to flip the prod-readiness checklist. */
  monitoringReady: boolean;
}

export function aggregateMonitoring(): MonitoringStats {
  const healthy = SERVICES.filter(s => s.health === 'OK').length;
  const degraded = SERVICES.filter(s => s.health === 'DEGRADED').length;
  const down = SERVICES.filter(s => s.health === 'DOWN').length;
  const slosOnTrack = SLOS.filter(s => s.actual >= s.target).length;
  // Production-readiness: at least 5 services monitored + on-call rotation set up.
  const monitoringReady = SERVICES.length >= 5 && ON_CALL.length >= 4;
  return {
    totalServices: SERVICES.length,
    healthyServices: healthy,
    degradedServices: degraded,
    downServices: down,
    activeAlerts: ACTIVE_ALERTS.length,
    unacknowledgedAlerts: ACTIVE_ALERTS.filter(a => !a.acknowledged).length,
    slosOnTrack,
    totalSlos: SLOS.length,
    monitoringReady,
  };
}
