import { useEffect, useMemo, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  ArrowLeft, RefreshCw, History, Settings2, SlidersHorizontal,
  ShieldAlert, Lock, Unlock, Activity, HeartPulse,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import {
  getIntegrationStatus, getIntegrationConfig, getRecordedCalls,
  tenableRecast, tenableAcceptRisk, tenableRemoveRecast,
  crowdstrikeContain, crowdstrikeLift,
  integrationModeTone, stubMethodTone, getAdapterMeta, INTEGRATION_ACTION,
  type IntegrationKey, type IntegrationStatus, type IntegrationConfig, type StubInvocation,
} from '../../lib/integrationsApi';

const VALID_KEYS: IntegrationKey[] = ['tenable', 'crowdstrike', 'splunk', 'servicenow'];

type ActionModal = 'recast' | 'accept-risk' | 'remove-recast' | 'contain' | 'lift' | null;

/**
 * Integration adapter console — L3 connectors.
 *
 * Surfaces the orphaned action + audit layer of the real /api/v1/integrations
 * adapters: masked configuration (GET /config), the stub-mode call log
 * (GET /stub/recorded-calls — the integration audit trail), and the outbound
 * actions that were never wired to any UI:
 *
 *   • Tenable    — recast severity / accept risk / remove recast (VM disposition)
 *   • CrowdStrike — contain / lift host (Falcon EDR containment)
 *
 * In dev every adapter runs in STUB mode, so each action executes for real
 * and immediately shows up in the recorded-calls trail below.
 */
export function IntegrationConsolePage() {
  const { key: rawKey } = useParams<{ key: string }>();
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const key = (rawKey ?? '') as IntegrationKey;
  const meta = getAdapterMeta(key);
  const known = VALID_KEYS.includes(key);
  const action = known ? INTEGRATION_ACTION[key] : 'outbound-push';

  const [status, setStatus] = useState<IntegrationStatus | null>(null);
  const [config, setConfig] = useState<IntegrationConfig | null>(null);
  const [configDenied, setConfigDenied] = useState(false);
  const [calls, setCalls] = useState<StubInvocation[]>([]);
  const [loading, setLoading] = useState(true);
  const [modal, setModal] = useState<ActionModal>(null);

  async function load() {
    if (!known) { setLoading(false); return; }
    setLoading(true);
    const [s, cfg, rc] = await Promise.all([
      getIntegrationStatus(key).catch(() => null),
      getIntegrationConfig(key).catch(() => 'denied' as const),
      getRecordedCalls(key).catch(() => [] as StubInvocation[]),
    ]);
    setStatus(s);
    if (cfg === 'denied') { setConfig(null); setConfigDenied(true); }
    else { setConfig(cfg); setConfigDenied(false); }
    // Newest first.
    setCalls([...rc].sort((a, b) => (b.at ?? '').localeCompare(a.at ?? '')));
    setLoading(false);
  }
  useEffect(() => { void load(); }, [key]); // eslint-disable-line react-hooks/exhaustive-deps

  const configRows = useMemo(() => (config ? flatten(config) : []), [config]);
  const lastActivity = calls.length > 0 ? calls[0].at : (status?.lastActionAt ?? status?.lastPollAt ?? null);
  const reachable = status != null;

  // ─── Action handlers ──────────────────────────────────────────────

  async function submitRecast(v: RecastForm): Promise<void> {
    if (v.justification.trim().length < 30) {
      toast.danger(isAr ? 'التبرير يجب ألا يقل عن 30 حرفاً' : 'Justification must be at least 30 characters');
      return;
    }
    try {
      const r = await tenableRecast(v.pluginId.trim(), {
        newSeverity: v.newSeverity as RecastForm['newSeverity'],
        justification: v.justification.trim(),
        assetUuid: v.assetUuid.trim() || undefined,
      });
      actionToast(r.accepted, r.tenableRuleId,
        isAr ? `إعادة تصنيف ${v.pluginId} → ${v.newSeverity}` : `Recast ${v.pluginId} → ${v.newSeverity}`);
      setModal(null);
      await load();
    } catch (e) { actionError(e); }
  }

  async function submitAcceptRisk(v: AcceptRiskForm): Promise<void> {
    if (v.justification.trim().length < 30) {
      toast.danger(isAr ? 'التبرير يجب ألا يقل عن 30 حرفاً' : 'Justification must be at least 30 characters');
      return;
    }
    try {
      const r = await tenableAcceptRisk(v.pluginId.trim(), {
        justification: v.justification.trim(),
        expiresAt: v.expiresAt ? `${v.expiresAt}T00:00:00` : undefined,
        assetUuid: v.assetUuid.trim() || undefined,
      });
      actionToast(r.accepted, r.tenableRuleId,
        isAr ? `قبول مخاطر ${v.pluginId}` : `Accepted risk on ${v.pluginId}`);
      setModal(null);
      await load();
    } catch (e) { actionError(e); }
  }

  async function submitRemoveRecast(v: RemoveRecastForm): Promise<void> {
    try {
      const r = await tenableRemoveRecast(v.pluginId.trim(), v.assetUuid.trim() || undefined);
      actionToast(r.accepted, r.tenableRuleId,
        isAr ? `إزالة قاعدة ${v.pluginId}` : `Removed rule on ${v.pluginId}`);
      setModal(null);
      await load();
    } catch (e) { actionError(e); }
  }

  async function submitContain(v: ContainForm): Promise<void> {
    if (!v.reason.trim()) { toast.danger(isAr ? 'سبب العزل مطلوب' : 'A containment reason is required'); return; }
    try {
      const r = await crowdstrikeContain(v.hostId.trim(), v.reason.trim());
      actionToast(r.accepted, r.falconResourceId,
        isAr ? `عزل المضيف ${v.hostId}` : `Contained host ${v.hostId}`);
      setModal(null);
      await load();
    } catch (e) { actionError(e); }
  }

  async function submitLift(v: LiftForm): Promise<void> {
    try {
      const r = await crowdstrikeLift(v.hostId.trim());
      actionToast(r.accepted, r.falconResourceId,
        isAr ? `رفع العزل عن ${v.hostId}` : `Lifted containment on ${v.hostId}`);
      setModal(null);
      await load();
    } catch (e) { actionError(e); }
  }

  function actionToast(accepted: boolean, ruleId: string | null | undefined, label: string) {
    if (ruleId === 'DISABLED') {
      toast.danger(isAr ? `المُوصِّل معطَّل — لم يُنفَّذ الإجراء (${label})` : `Adapter disabled — action not executed (${label})`);
    } else if (accepted) {
      toast.success(isAr ? `تم — ${label}` : `Done — ${label}`);
    } else {
      toast.danger(isAr ? `رُفض الإجراء — ${label}` : `Action rejected — ${label}`);
    }
  }
  function actionError(e: unknown) {
    toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل الإجراء' : 'Action failed'));
  }

  // ─── Unknown adapter guard ────────────────────────────────────────

  if (!known) {
    return (
      <Shell title={isAr ? 'مُوصِّل غير معروف' : 'Unknown adapter'} sub="">
        <PageHeader eyebrow={isAr ? 'الموصِّلات' : 'Connectors'} title={isAr ? 'مُوصِّل غير معروف' : 'Unknown adapter'} sub={rawKey ?? ''} />
        <Card>
          <div style={{ padding: 24, textAlign: 'center', color: 'var(--ink-3)', fontSize: 13 }}>
            {isAr ? 'لا يوجد مُوصِّل بهذا المعرّف.' : 'No adapter with that key.'}
            <div style={{ marginTop: 12 }}>
              <Link to="/connectors" style={{ textDecoration: 'none' }}>
                <Button variant="primary" size="sm" Icon={ArrowLeft}>{isAr ? 'الموصِّلات' : 'Connectors'}</Button>
              </Link>
            </div>
          </div>
        </Card>
      </Shell>
    );
  }

  const name = meta ? (isAr ? meta.nameAr : meta.nameEn) : key;
  const kind = meta ? (isAr ? meta.kindAr : meta.kindEn) : '';

  return (
    <Shell title={name} sub={isAr ? 'وحدة تحكّم المُوصِّل — تهيئة وإجراءات وسجل تدقيق' : 'Adapter console — config, actions & audit trail'}>
      <PageHeader
        eyebrow={isAr ? 'الموصِّلات · الأنظمة الخارجية' : 'Connectors · external systems'}
        title={name}
        sub={kind}
        actions={
          <div style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}>
            <Link to="/connectors" style={{ textDecoration: 'none' }}>
              <Button variant="ghost" size="sm" Icon={ArrowLeft}>{isAr ? 'الموصِّلات' : 'Connectors'}</Button>
            </Link>
            <Button variant="ghost" size="sm" Icon={RefreshCw} onClick={() => void load()} disabled={loading}>
              {isAr ? 'تحديث' : 'Refresh'}
            </Button>
          </div>
        }
      />

      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 14 }}>
        <Badge tone="accent">{kind}</Badge>
        {reachable
          ? <Badge tone={integrationModeTone(status?.mode)}>{status?.mode ?? '—'}</Badge>
          : <Badge tone="neutral" dot>{isAr ? 'غير متاح' : 'Unreachable'}</Badge>}
        {reachable && (status?.enabled === false
          ? <Badge tone="neutral" dot>{isAr ? 'معطَّل' : 'Disabled'}</Badge>
          : status?.healthy
            ? <Badge tone="success" dot>{isAr ? 'سليم' : 'Healthy'}</Badge>
            : <Badge tone="danger" dot>{isAr ? 'غير سليم' : 'Down'}</Badge>)}
        {(status?.baseUrl || status?.platform) &&
          <Badge tone="info">{status?.platform ?? status?.baseUrl}</Badge>}
      </div>

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={HeartPulse} label={isAr ? 'الحالة' : 'Health'}
          value={!reachable ? '—' : status?.healthy ? (isAr ? 'سليم' : 'Healthy') : (isAr ? 'غير سليم' : 'Down')}
          tone={!reachable ? 'neutral' : status?.healthy ? 'success' : 'danger'} to="/cyber" />
        <Kpi Icon={Activity} label={isAr ? 'الوضع' : 'Mode'} value={status?.mode ?? '—'}
          tone={(status?.mode ?? '').toUpperCase() === 'LIVE' ? 'success' : 'info'} to="/intake-inbox" />
        <Kpi Icon={History} label={isAr ? 'مكالمات مُسجَّلة' : 'Recorded calls'} value={calls.length} tone="primary" to="/intake-inbox" />
        <Kpi Icon={SlidersHorizontal} label={isAr ? 'آخر نشاط' : 'Last activity'}
          value={lastActivity ? fmtTime(lastActivity) : '—'} tone="accent" to="/intake-inbox" />
      </div>

      {/* ─── Outbound actions ─────────────────────────────────────── */}
      <Card title={isAr ? 'إجراءات صادرة' : 'Outbound actions'} Icon={ShieldAlert}>
        {action === 'tenable-disposition' && (
          <>
            <p style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
              {isAr
                ? 'التصرّف في ثغرات Tenable: إعادة تصنيف الخطورة، أو قبول المخاطر، أو إزالة قاعدة سابقة. التبرير إلزامي (≥ 30 حرفاً) لأثر التدقيق وفق ECC-2 / SAMA CSF.'
                : 'Dispose of Tenable findings: recast severity, accept the risk, or remove a prior rule. A justification (≥ 30 chars) is mandatory for the NCA ECC-2 / SAMA CSF audit trail.'}
            </p>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              <Button variant="primary" size="sm" Icon={SlidersHorizontal} onClick={() => setModal('recast')}>
                {isAr ? 'إعادة تصنيف الخطورة' : 'Recast severity'}
              </Button>
              <Button variant="secondary" size="sm" Icon={ShieldAlert} onClick={() => setModal('accept-risk')}>
                {isAr ? 'قبول المخاطر' : 'Accept risk'}
              </Button>
              <Button variant="ghost" size="sm" Icon={Unlock} onClick={() => setModal('remove-recast')}>
                {isAr ? 'إزالة قاعدة' : 'Remove rule'}
              </Button>
            </div>
          </>
        )}
        {action === 'crowdstrike-containment' && (
          <>
            <p style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
              {isAr
                ? 'احتواء Falcon: عزل مضيف عن الشبكة أو رفع العزل عنه. سبب العزل يُسجَّل في أثر التدقيق وفق ECC-2.'
                : 'Falcon containment: isolate a host from the network or lift its isolation. The reason is recorded in the NCA ECC-2 audit trail.'}
            </p>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              <Button variant="danger" size="sm" Icon={Lock} onClick={() => setModal('contain')}>
                {isAr ? 'عزل مضيف' : 'Contain host'}
              </Button>
              <Button variant="secondary" size="sm" Icon={Unlock} onClick={() => setModal('lift')}>
                {isAr ? 'رفع العزل' : 'Lift containment'}
              </Button>
            </div>
          </>
        )}
        {action === 'outbound-push' && (
          <p style={{ fontSize: 12, color: 'var(--ink-3)' }}>
            {isAr ? 'الإجراء الصادر لهذا المُوصِّل هو دفع اختباري متاح في ' : 'The outbound action for this adapter is a test-push, available on the '}
            <Link to="/connectors" style={{ color: 'var(--primary)', fontWeight: 600 }}>
              {isAr ? 'صفحة الموصِّلات' : 'Connectors page'}
            </Link>
            {isAr ? ' عبر زر «إرسال اختبار».' : ' via the “Test push” button.'}
          </p>
        )}
      </Card>

      {/* ─── Masked configuration ─────────────────────────────────── */}
      <Card title={isAr ? 'التهيئة (الأسرار مُقنَّعة)' : 'Configuration (secrets masked)'} Icon={Settings2}
        action={<Badge tone="neutral">{configRows.length}</Badge>}>
        {loading && !config
          ? <div style={{ padding: 16, color: 'var(--ink-3)', fontSize: 13 }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div>
          : configDenied
            ? <div style={{ padding: 16, color: 'var(--ink-3)', fontSize: 13 }}>{isAr ? 'التهيئة غير متاحة (صلاحيات).' : 'Configuration unavailable (permissions).'}</div>
            : configRows.length === 0
              ? <div style={{ padding: 16, color: 'var(--ink-3)', fontSize: 13 }}>{isAr ? 'لا توجد تهيئة.' : 'No configuration.'}</div>
              : <table className="grc-table">
                  <thead><tr>
                    <th>{isAr ? 'المفتاح' : 'Key'}</th>
                    <th>{isAr ? 'القيمة' : 'Value'}</th>
                  </tr></thead>
                  <tbody>
                    {configRows.map(([k, v]) => (
                      <tr key={k}>
                        <td className="mono text-2xs" style={{ color: 'var(--ink-2)' }}>{k}</td>
                        <td className="mono text-2xs">{v}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>}
      </Card>

      {/* ─── Recorded calls — the integration audit trail ─────────── */}
      <Card title={isAr ? 'سجل المكالمات (أثر التدقيق)' : 'Recorded calls (audit trail)'} Icon={History}
        action={<Badge tone="primary">{calls.length}</Badge>}>
        <p style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
          {isAr
            ? 'كل استدعاء رآه عميل المُوصِّل (وضع STUB) — استقصاء وفحوصات سلامة وإجراءات صادرة، مُجمَّدة وقت التنفيذ.'
            : 'Every call the adapter client saw (STUB mode) — polls, health checks, and outbound actions, frozen at execution time.'}
        </p>
        {loading && calls.length === 0
          ? <div style={{ padding: 16, color: 'var(--ink-3)', fontSize: 13 }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div>
          : calls.length === 0
            ? <div style={{ padding: 16, color: 'var(--ink-3)', fontSize: 13 }}>
                {isAr ? 'لا توجد مكالمات مُسجَّلة بعد — نفِّذ إجراءً أعلاه أو استقصِ المُوصِّل.' : 'No recorded calls yet — run an action above or poll the adapter.'}
              </div>
            : <table className="grc-table">
                <thead><tr>
                  <th>{isAr ? 'الوقت' : 'Time'}</th>
                  <th>{isAr ? 'الإجراء' : 'Method'}</th>
                  <th>{isAr ? 'الهدف' : 'Target'}</th>
                  <th>{isAr ? 'الحمولة' : 'Payload'}</th>
                </tr></thead>
                <tbody>
                  {calls.map((c, i) => (
                    <tr key={`${c.at}-${i}`}>
                      <td className="mono text-2xs">{fmtTime(c.at)}</td>
                      <td><Badge tone={stubMethodTone(c.method)}>{c.method}</Badge></td>
                      <td className="mono text-2xs">{c.targetId ?? '—'}</td>
                      <td className="mono text-2xs" style={{ maxWidth: 360, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', color: 'var(--ink-3)' }}>
                        {c.payload && Object.keys(c.payload).length > 0 ? JSON.stringify(c.payload) : '—'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>}
      </Card>

      {/* ─── Action modals ────────────────────────────────────────── */}
      <FormModal<RecastForm>
        open={modal === 'recast'}
        title={isAr ? 'إعادة تصنيف خطورة Tenable' : 'Recast Tenable severity'}
        fields={recastFields(isAr)}
        initial={{ pluginId: 'TEN-FND-1', newSeverity: 'MEDIUM', assetUuid: '', justification: '' }}
        onClose={() => setModal(null)}
        onSubmit={submitRecast}
        submitLabel={isAr ? 'إعادة التصنيف' : 'Recast'}
      />
      <FormModal<AcceptRiskForm>
        open={modal === 'accept-risk'}
        title={isAr ? 'قبول مخاطر ثغرة Tenable' : 'Accept Tenable risk'}
        fields={acceptRiskFields(isAr)}
        initial={{ pluginId: 'TEN-FND-1', assetUuid: '', expiresAt: '', justification: '' }}
        onClose={() => setModal(null)}
        onSubmit={submitAcceptRisk}
        submitLabel={isAr ? 'قبول' : 'Accept'}
      />
      <FormModal<RemoveRecastForm>
        open={modal === 'remove-recast'}
        title={isAr ? 'إزالة قاعدة Tenable' : 'Remove Tenable rule'}
        fields={removeRecastFields(isAr)}
        initial={{ pluginId: 'TEN-FND-1', assetUuid: '' }}
        onClose={() => setModal(null)}
        onSubmit={submitRemoveRecast}
        submitLabel={isAr ? 'إزالة' : 'Remove'}
      />
      <FormModal<ContainForm>
        open={modal === 'contain'}
        title={isAr ? 'عزل مضيف Falcon' : 'Contain Falcon host'}
        fields={containFields(isAr)}
        initial={{ hostId: 'falcon-host-1', reason: '' }}
        onClose={() => setModal(null)}
        onSubmit={submitContain}
        submitLabel={isAr ? 'عزل' : 'Contain'}
      />
      <FormModal<LiftForm>
        open={modal === 'lift'}
        title={isAr ? 'رفع عزل Falcon' : 'Lift Falcon containment'}
        fields={liftFields(isAr)}
        initial={{ hostId: 'falcon-host-1' }}
        onClose={() => setModal(null)}
        onSubmit={submitLift}
        submitLabel={isAr ? 'رفع العزل' : 'Lift'}
      />
    </Shell>
  );
}

// ─── Form shapes + field defs ────────────────────────────────────────

interface RecastForm { pluginId: string; newSeverity: 'INFO' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'; assetUuid: string; justification: string; }
interface AcceptRiskForm { pluginId: string; assetUuid: string; expiresAt: string; justification: string; }
interface RemoveRecastForm { pluginId: string; assetUuid: string; }
interface ContainForm { hostId: string; reason: string; }
interface LiftForm { hostId: string; }

const SEVERITY_OPTIONS = ['INFO', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map(v => ({ value: v, label: v }));

function recastFields(isAr: boolean): FieldDef[] {
  return [
    { kind: 'text', key: 'pluginId', label: isAr ? 'معرّف الثغرة (Plugin id) *' : 'Plugin id *', required: true, span: 6 },
    { kind: 'select', key: 'newSeverity', label: isAr ? 'الخطورة الجديدة *' : 'New severity *', required: true, options: SEVERITY_OPTIONS, span: 6 },
    { kind: 'text', key: 'assetUuid', label: isAr ? 'معرّف الأصل (اختياري — فارغ = شامل)' : 'Asset UUID (optional — blank = global)', span: 12 },
    { kind: 'textarea', key: 'justification', label: isAr ? 'التبرير * (30 حرفاً على الأقل لأثر التدقيق)' : 'Justification * (≥ 30 chars for the audit trail)', rows: 3, span: 12 },
  ];
}
function acceptRiskFields(isAr: boolean): FieldDef[] {
  return [
    { kind: 'text', key: 'pluginId', label: isAr ? 'معرّف الثغرة (Plugin id) *' : 'Plugin id *', required: true, span: 6 },
    { kind: 'date', key: 'expiresAt', label: isAr ? 'تنتهي صلاحية القبول في (اختياري)' : 'Acceptance expires (optional)', span: 6 },
    { kind: 'text', key: 'assetUuid', label: isAr ? 'معرّف الأصل (اختياري — فارغ = شامل)' : 'Asset UUID (optional — blank = global)', span: 12 },
    { kind: 'textarea', key: 'justification', label: isAr ? 'التبرير * (30 حرفاً على الأقل لأثر التدقيق)' : 'Justification * (≥ 30 chars for the audit trail)', rows: 3, span: 12 },
  ];
}
function removeRecastFields(isAr: boolean): FieldDef[] {
  return [
    { kind: 'text', key: 'pluginId', label: isAr ? 'معرّف الثغرة (Plugin id) *' : 'Plugin id *', required: true, span: 6 },
    { kind: 'text', key: 'assetUuid', label: isAr ? 'معرّف الأصل (اختياري)' : 'Asset UUID (optional)', span: 6 },
  ];
}
function containFields(isAr: boolean): FieldDef[] {
  return [
    { kind: 'text', key: 'hostId', label: isAr ? 'معرّف المضيف (Falcon device id) *' : 'Host id (Falcon device id) *', required: true, span: 12 },
    { kind: 'textarea', key: 'reason', label: isAr ? 'سبب العزل * (يُسجَّل في أثر التدقيق)' : 'Containment reason * (recorded in the audit trail)', rows: 3, span: 12 },
  ];
}
function liftFields(isAr: boolean): FieldDef[] {
  return [
    { kind: 'text', key: 'hostId', label: isAr ? 'معرّف المضيف (Falcon device id) *' : 'Host id (Falcon device id) *', required: true, span: 12 },
  ];
}

// ─── helpers ─────────────────────────────────────────────────────────

/** Flatten a (possibly nested) config object into dotted key/value rows. */
function flatten(obj: unknown, prefix = ''): Array<[string, string]> {
  const out: Array<[string, string]> = [];
  if (obj && typeof obj === 'object' && !Array.isArray(obj)) {
    for (const [k, v] of Object.entries(obj as Record<string, unknown>)) {
      const path = prefix ? `${prefix}.${k}` : k;
      if (v && typeof v === 'object' && !Array.isArray(v)) {
        out.push(...flatten(v, path));
      } else {
        out.push([path, v == null || v === '' ? '—' : String(v)]);
      }
    }
  }
  return out;
}

/** Format an ISO instant to a compact "MM-DD HH:MM:SS" without timezone noise. */
function fmtTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  // Backend sends Instant (…Z) or LocalDateTime — slice the date/time portion.
  const m = iso.replace('T', ' ');
  return m.length >= 19 ? m.slice(5, 19) : m.slice(0, 19);
}
