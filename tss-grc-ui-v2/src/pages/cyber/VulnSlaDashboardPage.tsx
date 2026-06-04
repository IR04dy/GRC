import { useEffect, useMemo, useState } from 'react';
import { Clock, AlertOctagon, TimerReset, ShieldCheck, ShieldAlert, Activity, CheckCircle2 } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { isAuthenticated } from '../../lib/auth';
import { ApiError } from '../../lib/api';
import {
  vulnSlaApi,
  VULN_SLA_SEVERITY_LABEL, VULN_SLA_SEVERITY_TONE,
  VULN_SLA_EXPOSURE_LABEL,
  VULN_SLA_CRITICALITY_LABEL,
  VULN_SLA_STATUS_LABEL, VULN_SLA_STATUS_TONE,
  formatHours,
  type VulnSlaDashboardSnapshot, type VulnSlaFindingResponse,
} from '../../lib/vulnSla';

/**
 * Vulnerability SLA Dashboard — Sprint 11.
 *
 * <p>Saudi-native title (operator-natural, not literal):
 *   ar: اتفاقيات مستوى الخدمة القائمة على المخاطر للثغرات الأمنية
 *   en: Risk-Based Vulnerability SLAs
 *
 * <p>Anchors NCA ECC-2 control 2-12, SAMA CSF 3.3.8, CISA SSVC.
 * KPI tiles show open / warning / breached / remediated counts; two
 * tables surface BREACHED rows (top) and WARNING rows (below). A
 * "Run escalation pass" button forces a sweep so the SOC lead can
 * see the latest classification without waiting for the scheduler.
 */
export function VulnSlaDashboardPage() {
  const { lang } = useI18n();
  const ar = lang === 'ar';
  const toast = useToast();
  const demoMode = !isAuthenticated();

  const [snap, setSnap] = useState<VulnSlaDashboardSnapshot | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<'recompute' | 'escalation' | null>(null);
  const [remediatingId, setRemediatingId] = useState<string | null>(null);

  // Close out a finding from the SOC dashboard — POST .../findings/{id}/remediated
  // stamps remediated_at + remediated_by and flips it out of the breach/warning queue.
  async function markRemediated(f: VulnSlaFindingResponse) {
    if (demoMode || remediatingId) return;
    setRemediatingId(f.externalId);
    try {
      await vulnSlaApi.markRemediated(f.externalId);
      toast.success(ar ? `عُولجت الثغرة #${f.vulnerabilityId}` : `Finding #${f.vulnerabilityId} marked remediated`);
      await load();
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : (ar ? 'تعذّرت المعالجة' : 'Remediation failed'));
    } finally {
      setRemediatingId(null);
    }
  }

  async function load() {
    if (demoMode) { setSnap(null); return; }
    setLoading(true);
    setError(null);
    try {
      const data = await vulnSlaApi.dashboard();
      setSnap(data);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load vulnerability SLA dashboard');
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { load(); /* eslint-disable-next-line */ }, []);

  async function runEscalation() {
    if (demoMode || busy) return;
    setBusy('escalation');
    try {
      await vulnSlaApi.runEscalation();
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Escalation pass failed');
    } finally {
      setBusy(null);
    }
  }

  async function recomputeAll() {
    if (demoMode || busy) return;
    setBusy('recompute');
    try {
      await vulnSlaApi.recompute();
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Recompute failed');
    } finally {
      setBusy(null);
    }
  }

  const kpis = useMemo(() => ({
    total:      snap?.totalFindings ?? 0,
    open:       snap?.openFindings ?? 0,
    warning:    snap?.warningFindings ?? 0,
    breached:   snap?.breachedFindings ?? 0,
    remediated: snap?.remediatedFindings ?? 0,
  }), [snap]);

  // ─── Table columns shared by breached + warning ────────────────

  const findingCols: Column<VulnSlaFindingResponse>[] = [
    {
      header: lang === 'ar' ? 'الثغرة' : 'Vulnerability', width: '120px',
      cell: r => <code style={{ fontSize: 12 }}>#{r.vulnerabilityId}</code>,
    },
    {
      header: lang === 'ar' ? 'الخطورة' : 'Severity', width: '110px', align: 'center',
      cell: r => (
        <Badge tone={VULN_SLA_SEVERITY_TONE[r.cvssSeverity]}>
          {VULN_SLA_SEVERITY_LABEL[r.cvssSeverity][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'التعرّض' : 'Exposure', width: '110px', align: 'center',
      cell: r => (
        <Badge tone={r.assetExposure === 'PUBLIC' ? 'danger' : r.assetExposure === 'PARTNER' ? 'warning' : 'neutral'}>
          {VULN_SLA_EXPOSURE_LABEL[r.assetExposure][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'أهمية الأصل' : 'Criticality', width: '110px', align: 'center',
      cell: r => (
        <Badge tone={r.assetCriticality === 'CRITICAL' ? 'danger' : r.assetCriticality === 'HIGH' ? 'warning' : 'neutral'}>
          {VULN_SLA_CRITICALITY_LABEL[r.assetCriticality][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'المهلة الأساسية' : 'Base SLA', width: '120px', align: 'center',
      cell: r => <span style={{ fontSize: 12 }}>{formatHours(r.baseSlaHours)}</span>,
    },
    {
      header: lang === 'ar' ? 'المهلة الفعلية' : 'Effective SLA', width: '140px', align: 'center',
      cell: r => (
        <Badge tone="warning">{formatHours(r.effectiveSlaHours)}</Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'تاريخ الاستحقاق' : 'Due at', width: '180px',
      cell: r => <span style={{ fontSize: 12 }}>{r.dueAt ? new Date(r.dueAt).toLocaleString() : '—'}</span>,
    },
    {
      header: lang === 'ar' ? 'الحالة' : 'Status', width: '160px', align: 'center',
      cell: r => (
        <Badge tone={VULN_SLA_STATUS_TONE[r.status]} dot>
          {VULN_SLA_STATUS_LABEL[r.status][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: '', width: '150px', align: 'end',
      cell: r => (['REMEDIATED', 'ACCEPTED_RISK', 'FALSE_POSITIVE'].includes(r.status) ? null : (
        <Button size="sm" variant="secondary" Icon={CheckCircle2}
          disabled={demoMode || remediatingId === r.externalId}
          onClick={() => markRemediated(r)}>
          {remediatingId === r.externalId ? (ar ? 'جارٍ…' : '…') : (ar ? 'تمت المعالجة' : 'Remediated')}
        </Button>
      )),
    },
  ];

  // ─── Render ────────────────────────────────────────────────────

  return (
    <Shell
      title={lang === 'ar' ? 'اتفاقيات مستوى الخدمة القائمة على المخاطر للثغرات الأمنية' : 'Risk-Based Vulnerability SLAs'}
      sub={lang === 'ar' ? 'الأمن السيبراني' : 'Cybersecurity'}
    >
      <PageHeader
        eyebrow={lang === 'ar' ? 'الأمن السيبراني' : 'Cybersecurity'}
        title={lang === 'ar' ? 'اتفاقيات مستوى الخدمة القائمة على المخاطر للثغرات الأمنية' : 'Risk-Based Vulnerability SLAs'}
        sub={lang === 'ar'
          ? 'مهلة معالجة مُسرَّعة تجمع الخطورة وفق CVSS مع تعرّض الأصل وأهميته للأعمال. تخدم الضابط 2-12 من ECC-2 وإطار SAMA السيبراني 3.3.8 ومنهجية CISA SSVC.'
          : 'Accelerated remediation clock blending CVSS severity with asset exposure and business criticality. Anchors NCA ECC-2 control 2-12, SAMA Cyber Security Framework 3.3.8, and CISA SSVC methodology.'}
        actions={
          <div style={{ display: 'flex', gap: 8 }}>
            {demoMode && <Badge tone="warning">{lang === 'ar' ? 'وضع العرض' : 'Demo mode'}</Badge>}
            {!demoMode && (
              <>
                <button
                  type="button"
                  onClick={recomputeAll}
                  disabled={busy !== null}
                  style={btnStyle('secondary', busy === 'recompute')}
                >
                  {busy === 'recompute'
                    ? (lang === 'ar' ? 'جارٍ إعادة الحساب…' : 'Recomputing…')
                    : (lang === 'ar' ? 'إعادة حساب الكل' : 'Recompute all')}
                </button>
                <button
                  type="button"
                  onClick={runEscalation}
                  disabled={busy !== null}
                  style={btnStyle('primary', busy === 'escalation')}
                >
                  {busy === 'escalation'
                    ? (lang === 'ar' ? 'جارٍ التشغيل…' : 'Running…')
                    : (lang === 'ar' ? 'تشغيل دورة التصعيد' : 'Run escalation pass')}
                </button>
              </>
            )}
          </div>
        }
      />

      {/* KPI tiles */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 12 }}>
        <Kpi Icon={Activity} tone="primary"
             label={lang === 'ar' ? 'إجمالي النتائج' : 'Total findings'}
             value={kpis.total} />
        <Kpi Icon={Clock} tone="info"
             label={lang === 'ar' ? 'مفتوحة' : 'Open'}
             value={kpis.open} />
        <Kpi Icon={TimerReset} tone="warning"
             label={lang === 'ar' ? 'تنبيه قبل الإخلال' : 'Warning'}
             value={kpis.warning} />
        <Kpi Icon={AlertOctagon} tone="danger"
             label={lang === 'ar' ? 'مُخَلّة بالاتفاقية' : 'Breached'}
             value={kpis.breached} />
        <Kpi Icon={ShieldCheck} tone="success"
             label={lang === 'ar' ? 'مُعالَجة' : 'Remediated'}
             value={kpis.remediated} />
      </div>

      {/* Default policy callout */}
      {snap?.defaultPolicy && (
        <Card padded>
          <div style={{ display: 'flex', gap: 16, alignItems: 'flex-start', flexWrap: 'wrap' }}>
            <ShieldAlert size={20} style={{ color: 'var(--primary)', flexShrink: 0, marginTop: 2 }} />
            <div style={{ flex: '1 1 280px' }}>
              <div style={{ display: 'flex', gap: 8, alignItems: 'baseline', flexWrap: 'wrap' }}>
                <span style={{ fontWeight: 600 }}>
                  {lang === 'ar' && snap.defaultPolicy.titleAr ? snap.defaultPolicy.titleAr : snap.defaultPolicy.titleEn}
                </span>
                <code style={{ fontSize: 12, color: 'var(--ink-3)' }}>{snap.defaultPolicy.policyCode}</code>
                <Badge tone="success" dot>{lang === 'ar' ? 'افتراضي' : 'Default'}</Badge>
              </div>
              <div style={{ fontSize: 13, color: 'var(--ink-3)', marginTop: 4 }}>
                {lang === 'ar'
                  ? `الساعات الأساسية: حرج ${formatHours(snap.defaultPolicy.criticalSlaHours)} · مرتفع ${formatHours(snap.defaultPolicy.highSlaHours)} · متوسط ${formatHours(snap.defaultPolicy.mediumSlaHours)} · منخفض ${formatHours(snap.defaultPolicy.lowSlaHours)}`
                  : `Base hours — Critical ${formatHours(snap.defaultPolicy.criticalSlaHours)} · High ${formatHours(snap.defaultPolicy.highSlaHours)} · Medium ${formatHours(snap.defaultPolicy.mediumSlaHours)} · Low ${formatHours(snap.defaultPolicy.lowSlaHours)}`}
              </div>
            </div>
          </div>
        </Card>
      )}

      {error && (
        <div style={{ color: 'var(--danger)', fontSize: 13 }}>{error}</div>
      )}

      {/* Breached findings */}
      <Card padded>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
          <AlertOctagon size={18} style={{ color: 'var(--danger)' }} />
          <h3 style={{ margin: 0, fontSize: 16, fontWeight: 600 }}>
            {lang === 'ar' ? 'نتائج مُخَلّة بالاتفاقية' : 'Breached findings'}
          </h3>
          <Badge tone="danger">{snap?.breached?.length ?? 0}</Badge>
        </div>
        {loading ? (
          <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        ) : (
          <DataTable
            columns={findingCols}
            rows={snap?.breached ?? []}
            empty={<EmptyState
              Icon={ShieldCheck}
              title={lang === 'ar' ? 'لا توجد إخلالات' : 'No breaches'}
              sub={lang === 'ar'
                ? 'لا تتجاوز أي ثغرة حالياً مهلة الاتفاقية. تابع تشغيل دورة التصعيد بانتظام.'
                : 'No vulnerability currently breaches its SLA clock. Keep running the escalation pass.'} />}
          />
        )}
      </Card>

      {/* Warning findings */}
      <Card padded>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
          <TimerReset size={18} style={{ color: 'var(--warning)' }} />
          <h3 style={{ margin: 0, fontSize: 16, fontWeight: 600 }}>
            {lang === 'ar' ? 'تنبيهات قبل الإخلال' : 'Warning findings'}
          </h3>
          <Badge tone="warning">{snap?.warning?.length ?? 0}</Badge>
        </div>
        {loading ? (
          <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        ) : (
          <DataTable
            columns={findingCols}
            rows={snap?.warning ?? []}
            empty={<EmptyState
              Icon={ShieldCheck}
              title={lang === 'ar' ? 'لا توجد تنبيهات' : 'No warnings'}
              sub={lang === 'ar'
                ? 'لا توجد ثغرات اقتربت من حد التنبيه (75% من المهلة).'
                : 'No vulnerabilities currently sitting in the 75% warning band.'} />}
          />
        )}
      </Card>
    </Shell>
  );
}

function btnStyle(tone: 'primary' | 'secondary', busy: boolean): React.CSSProperties {
  return {
    padding: '8px 14px',
    border: tone === 'primary' ? 'none' : '1px solid var(--border)',
    borderRadius: 6,
    background: tone === 'primary' ? 'var(--primary)' : 'var(--surface)',
    color: tone === 'primary' ? 'var(--on-primary, #fff)' : 'var(--ink-1)',
    fontSize: 13,
    fontWeight: 500,
    cursor: busy ? 'wait' : 'pointer',
    opacity: busy ? 0.7 : 1,
  };
}
