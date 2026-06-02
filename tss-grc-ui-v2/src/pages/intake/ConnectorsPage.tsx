import { useEffect, useState } from 'react';
import {
  Plug, Activity, Clock, BookOpen, Database, Zap, Pause, Play, PlayCircle, RefreshCw, ServerCog,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  CONNECTOR_REGISTRY, CADENCE_LABEL,
  CONNECTOR_STATUS_TONE, CONNECTOR_STATUS_LABEL,
  type ConnectorDefinition, type ConnectorStatus,
} from '../../lib/connectors';
import {
  listCcmTests, getCcmDashboard, enableCcmTest, disableCcmTest, runCcmTestNow,
  ccmStatusTone, ccmSeverityTone, ccmStatusLabel,
  type CcmTestDefinition, type CcmDashboardSnapshot,
} from '../../lib/ccmApi';
import { ApiError } from '../../lib/api';
import { CcmDashboard } from '../../components/compliance/CcmDashboard';

/**
 * Connectors page — DD-62.
 *
 * Visualises the third intake channel — automated connectors pulling
 * from external monitoring + scanner + regulator-watch sources. Each
 * row shows source / what it auto-creates / cadence / status / 30-day
 * intake count.
 *
 * industry-standard GRC tooling "Continuous Monitoring" + continuous-monitoring tooling "Integrations" pattern.
 */
export function ConnectorsPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const [filter, setFilter] = useState<'ALL' | ConnectorStatus>('ALL');

  // Connector statuses are mutable per-session so Pause / Activate produce
  // a visible state change even though the backend connector module is not
  // yet wired up. The overrides map keyed on connector id wins over the
  // baked-in CONNECTOR_REGISTRY status.
  const [overrides, setOverrides] = useState<Record<string, ConnectorStatus>>({});

  function effectiveStatus(c: ConnectorDefinition): ConnectorStatus {
    return overrides[c.id] ?? c.status;
  }
  function pause(c: ConnectorDefinition) {
    setOverrides(prev => ({ ...prev, [c.id]: 'DISABLED' }));
    toast.success((t('connectorsPausedToast') ?? 'Connector paused').replace('{name}', c.id));
  }
  function activate(c: ConnectorDefinition) {
    setOverrides(prev => ({ ...prev, [c.id]: 'ACTIVE' }));
    toast.success(t('connectorsActivated'));
  }

  const withEffective = CONNECTOR_REGISTRY.map(c => ({ ...c, status: effectiveStatus(c) }));
  const connectors = withEffective;
  const active = connectors.filter(c => c.status === 'ACTIVE');
  const configured = connectors.filter(c => c.status === 'CONFIGURED');
  const intake30d = active.reduce((acc, c) => acc + (c.intake30d ?? 0), 0);

  const visible = filter === 'ALL' ? connectors : connectors.filter(c => c.status === filter);

  return (
    <Shell title={t('connectorsTitle')} sub={t('connectorsSub')}>
      <PageHeader
        eyebrow={t('connectorsEyebrow')}
        title={t('connectorsTitle')}
        sub={t('connectorsPageSub')}
      />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Activity} label={t('connectorsKpiActive')}     value={active.length}     tone="success" />
        <Kpi Icon={Plug}     label={t('connectorsKpiConfigured')} value={configured.length} tone="info" />
        <Kpi Icon={Database} label={t('connectorsKpiTotal')}      value={connectors.length} tone="primary" />
        <Kpi Icon={Zap}      label={t('connectorsKpiIntake30d')}  value={intake30d}         tone="accent" />
      </div>

      {/* L3 — Live integration: the real CCM engine (/api/v1/ccm). Lists the
          persisted control-test definitions and lets an operator enable /
          disable and run each one for real against its target system. */}
      <div style={{ marginBottom: 16 }}>
        <LiveControlTests />
      </div>

      {/* Sprint 4 — Continuous Control Monitoring panel sits above the
          connector registry. Every check the connectors run is graded by
          freshness; stale or expired evidence becomes a workflow instance
          fed into the cross-module SLA pulse. */}
      <div style={{ marginBottom: 16 }}>
        <CcmDashboard />
      </div>

      <Card title={t('connectorsRegistryTitle')} Icon={Plug}
        action={<select value={filter} onChange={e => setFilter(e.target.value as 'ALL' | ConnectorStatus)}
          style={{
            padding: '6px 8px', fontSize: 12,
            border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
            background: 'var(--surface-2)', color: 'var(--ink-1)',
          }}>
          <option value="ALL">{t('connectorsFilterAll')}</option>
          <option value="ACTIVE">{t('connectorsFilterActive')}</option>
          <option value="CONFIGURED">{t('connectorsFilterConfigured')}</option>
          <option value="AVAILABLE">{t('connectorsFilterAvailable')}</option>
        </select>}>
        <p style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
          {t('connectorsBlurb')}
        </p>
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          {visible.map(c => (
            <ConnectorRow key={c.id} c={c} t={t} lang={lang} toast={toast}
              onPause={() => pause(c)} onActivate={() => activate(c)} />
          ))}
        </div>
      </Card>
    </Shell>
  );
}

/**
 * Live CCM integration (L3). Drives the real backend monitoring engine:
 * lists persisted control-test definitions, toggles them, and runs them
 * synchronously — each run executes a real runner and is recorded server-side.
 */
function LiveControlTests() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const [tests, setTests] = useState<CcmTestDefinition[]>([]);
  const [dash, setDash] = useState<CcmDashboardSnapshot | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);

  async function reload() {
    setLoading(true);
    try {
      const [ts, d] = await Promise.all([listCcmTests(), getCcmDashboard()]);
      setTests([...ts].sort((a, b) => (a.testCode ?? '').localeCompare(b.testCode ?? '')));
      setDash(d);
      setFailed(false);
    } catch (e) {
      setFailed(true);
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'تعذّر تحميل محرّك المراقبة' : 'Failed to load CCM engine'));
    } finally { setLoading(false); }
  }
  useEffect(() => { void reload(); }, []);  // eslint-disable-line react-hooks/exhaustive-deps

  async function toggle(test: CcmTestDefinition) {
    setBusyId(test.externalId);
    try {
      const updated = test.isEnabled ? await disableCcmTest(test.externalId) : await enableCcmTest(test.externalId);
      setTests(prev => prev.map(x => x.externalId === updated.externalId ? updated : x));
      setDash(await getCcmDashboard());
      toast.success(updated.isEnabled
        ? (isAr ? 'تم تفعيل الفحص' : 'Test enabled')
        : (isAr ? 'تم تعطيل الفحص' : 'Test disabled'));
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل التبديل' : 'Toggle failed'));
    } finally { setBusyId(null); }
  }

  async function runNow(test: CcmTestDefinition) {
    setBusyId(test.externalId);
    try {
      const run = await runCcmTestNow(test.externalId);
      const label = `${test.testCode ?? test.titleEn}: ${ccmStatusLabel(run.status, isAr ? 'ar' : 'en')}`;
      if (run.status?.toUpperCase() === 'PASS') toast.success(label);
      else toast.danger(run.failureReason ? `${label} — ${run.failureReason}` : label);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل التشغيل' : 'Run failed'));
    } finally { setBusyId(null); }
  }

  const passRate = dash ? Math.round((dash.passRateLast24h ?? 0) * (dash.passRateLast24h <= 1 ? 100 : 1)) : 0;

  return (
    <Card title={isAr ? 'محرّك المراقبة المستمرة للضوابط (مباشر)' : 'Continuous control-monitoring engine (live)'}
      Icon={ServerCog}
      action={
        <Button variant="ghost" size="sm" Icon={RefreshCw} onClick={() => void reload()} disabled={loading}>
          {isAr ? 'تحديث' : 'Refresh'}
        </Button>
      }>
      <p style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
        {isAr
          ? 'فحوصات حقيقية تعمل على /api/v1/ccm — فعِّل أو عطِّل أو شغِّل أي فحص الآن؛ كل تشغيل يُنفَّذ فعلياً ويُسجَّل في الخادم.'
          : 'Real checks backed by /api/v1/ccm — enable, disable, or run any test now; every run executes for real and is recorded server-side.'}
      </p>

      {dash && (
        <div className="kpi-grid" style={{ marginBottom: 14 }}>
          <Kpi Icon={Database}   label={isAr ? 'إجمالي الفحوصات' : 'Total tests'}   value={dash.totalTests}   tone="primary" />
          <Kpi Icon={Activity}   label={isAr ? 'المُفعَّلة' : 'Enabled'}            value={dash.enabledTests} tone="success" />
          <Kpi Icon={PlayCircle} label={isAr ? 'ناجحة (24س)' : 'Passing (24h)'}     value={dash.passingLast24h} tone="info" />
          <Kpi Icon={Zap}        label={isAr ? 'نسبة النجاح (24س)' : 'Pass rate (24h)'} value={`${passRate}%`} tone="accent" />
        </div>
      )}

      {loading && tests.length === 0
        ? <div style={{ padding: 16, color: 'var(--ink-3)', fontSize: 13 }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div>
        : failed && tests.length === 0
          ? <div style={{ padding: 16, color: 'var(--ink-3)', fontSize: 13 }}>{isAr ? 'محرّك المراقبة غير متاح حالياً' : 'Monitoring engine unavailable right now'}</div>
          : tests.length === 0
            ? <div style={{ padding: 16, color: 'var(--ink-3)', fontSize: 13 }}>{isAr ? 'لا توجد فحوصات معرّفة' : 'No control tests defined'}</div>
            : <table className="grc-table">
                <thead><tr>
                  <th>{isAr ? 'الكود' : 'Code'}</th>
                  <th>{isAr ? 'الفحص' : 'Test'}</th>
                  <th>{isAr ? 'النظام المستهدف' : 'Target'}</th>
                  <th>{isAr ? 'الخطورة' : 'Severity'}</th>
                  <th>{isAr ? 'آخر نتيجة' : 'Last result'}</th>
                  <th>{isAr ? 'الحالة' : 'State'}</th>
                  <th style={{ textAlign: 'end' }}>{isAr ? 'الإجراءات' : 'Actions'}</th>
                </tr></thead>
                <tbody>
                  {tests.map(test => {
                    const busy = busyId === test.externalId;
                    return (
                      <tr key={test.externalId}>
                        <td className="mono text-2xs">{test.testCode ?? '—'}</td>
                        <td>
                          <div style={{ fontWeight: 600, fontSize: 13 }}>{isAr && test.titleAr ? test.titleAr : test.titleEn}</div>
                          <div className="text-2xs text-muted mono">{test.testType}</div>
                        </td>
                        <td className="text-2xs">{test.targetSystemType}</td>
                        <td>{test.severity
                          ? <Badge tone={ccmSeverityTone(test.severity)}>{test.severity}</Badge>
                          : <span className="text-muted text-2xs">—</span>}</td>
                        <td>
                          {test.lastRunStatus
                            ? <Badge tone={ccmStatusTone(test.lastRunStatus)} dot>{ccmStatusLabel(test.lastRunStatus, isAr ? 'ar' : 'en')}</Badge>
                            : <span className="text-muted text-2xs">{isAr ? 'لم يُشغَّل' : 'Never run'}</span>}
                          {test.lastRunAt && <div className="text-2xs text-muted mono">{test.lastRunAt.slice(5, 16).replace('T', ' ')}</div>}
                        </td>
                        <td>
                          <Badge tone={test.isEnabled ? 'success' : 'neutral'} dot>
                            {test.isEnabled ? (isAr ? 'مُفعَّل' : 'Enabled') : (isAr ? 'مُعطَّل' : 'Disabled')}
                          </Badge>
                        </td>
                        <td style={{ textAlign: 'end' }}>
                          <div style={{ display: 'inline-flex', gap: 4 }}>
                            <Button size="sm" variant="primary" Icon={Play} disabled={busy} onClick={() => void runNow(test)}>
                              {isAr ? 'تشغيل' : 'Run now'}
                            </Button>
                            <Button size="sm" variant="ghost" Icon={test.isEnabled ? Pause : PlayCircle} disabled={busy} onClick={() => void toggle(test)}>
                              {test.isEnabled ? (isAr ? 'تعطيل' : 'Disable') : (isAr ? 'تفعيل' : 'Enable')}
                            </Button>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>}
    </Card>
  );
}

function ConnectorRow({ c, t, lang, onPause, onActivate }: {
  c: ConnectorDefinition;
  t: (k: string) => string;
  lang: 'en' | 'ar';
  toast: { info: (m: string) => void; success: (m: string) => void };
  onPause: () => void;
  onActivate: () => void;
}) {
  const statusLabel = CONNECTOR_STATUS_LABEL[c.status][lang === 'ar' ? 'ar' : 'en'];
  const cadenceLabel = CADENCE_LABEL[c.cadence][lang === 'ar' ? 'ar' : 'en'];

  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: '38px 1fr 130px 110px 110px 90px 110px',
      gap: 10, padding: '12px 0',
      borderBottom: '1px dashed var(--border)',
      alignItems: 'center',
    }}>
      <div style={{
        width: 32, height: 32, borderRadius: 'var(--r-sm)',
        background: 'var(--primary-50)', color: 'var(--primary)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}>
        <Plug size={16} strokeWidth={2} />
      </div>
      <div style={{ minWidth: 0 }}>
        <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--ink-1)' }}>
          {lang === 'ar' ? c.nameAr : c.nameEn}
        </div>
        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2, lineHeight: 1.5 }}>
          {lang === 'ar' ? c.descriptionAr : c.descriptionEn}
        </div>
        <div style={{ display: 'flex', gap: 6, marginTop: 4 }}>
          <Badge tone="neutral">
            <BookOpen size={10} style={{ marginInlineEnd: 4 }} />
            {c.reference}
          </Badge>
          {c.targetEntity && <Badge tone="info">→ {c.targetEntity}</Badge>}
          {c.intakeKind && <Badge tone="accent">→ intake</Badge>}
        </div>
      </div>
      <div>
        <div className="mono" style={{ fontSize: 11, color: 'var(--ink-2)' }}>{c.source}</div>
        {c.owner && <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>{c.owner}</div>}
      </div>
      <div>
        <div style={{ display: 'inline-flex', alignItems: 'center', gap: 4, fontSize: 12, color: 'var(--ink-2)' }}>
          <Clock size={12} /> {cadenceLabel}
        </div>
      </div>
      <div>
        <div className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>
          {c.lastRunAt ? c.lastRunAt.slice(5, 16).replace('T', ' ') : '—'}
        </div>
      </div>
      <div style={{ textAlign: 'center' }}>
        <span className="mono" style={{ fontSize: 16, fontWeight: 700, color: 'var(--ink-1)' }}>
          {c.intake30d ?? 0}
        </span>
        <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>{t('connectorsIntake30d')}</div>
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 4 }}>
        <Badge tone={CONNECTOR_STATUS_TONE[c.status]} dot>{statusLabel}</Badge>
        {c.status === 'ACTIVE' && (
          <Button variant="ghost" size="sm" Icon={Pause} onClick={onPause}>
            {t('connectorsPause')}
          </Button>
        )}
        {(c.status === 'CONFIGURED' || c.status === 'AVAILABLE' || c.status === 'DISABLED') && (
          <Button variant="primary" size="sm" Icon={Play} onClick={onActivate}>
            {t('connectorsActivate')}
          </Button>
        )}
      </div>
    </div>
  );
}
