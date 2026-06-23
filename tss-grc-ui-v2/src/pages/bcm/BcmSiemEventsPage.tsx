import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Radio, RefreshCw, Siren, ShieldAlert, Activity, Zap, BellRing, ArrowUpRight,
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
  bcmSiemApi, sevTone, sevLabel, siemStatusLabel, SIEM_STATUS_TONE,
  SIEM_STATUS_FILTERS, ESCALATION_THRESHOLD,
  type SiemInboundEvent, type SiemIngestPayload,
} from '../../lib/bcmSiemEvents';

/**
 * SIEM → BCM crisis bridge.
 *
 * Surfaces the orphaned /api/v1/bcm/siem-events endpoints: the inbound SOC/SIEM
 * alert log and its auto-triage. On ingest the backend escalates severity >= 3
 * to a fresh CrisisEvent (cyber-triggered) and ignores 1-2. The "simulate
 * inbound alert" action exercises that bridge end-to-end.
 */
export function BcmSiemEventsPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [rows, setRows] = useState<SiemInboundEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [filter, setFilter] = useState<typeof SIEM_STATUS_FILTERS[number]>('ALL');
  const [simOpen, setSimOpen] = useState(false);

  async function load() {
    setLoading(true);
    try {
      const list = await bcmSiemApi.list();
      setRows([...list].sort((a, b) => (b.receivedAt ?? '').localeCompare(a.receivedAt ?? '')));
      setFailed(false);
    } catch (e) {
      setFailed(true);
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'تعذّر تحميل الأحداث' : 'Failed to load events'));
    } finally { setLoading(false); }
  }
  useEffect(() => { void load(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const counts = useMemo(() => ({
    total: rows.length,
    escalated: rows.filter(r => r.processingStatus === 'ESCALATED').length,
    ignored: rows.filter(r => r.processingStatus === 'IGNORED').length,
    highSev: rows.filter(r => r.severity >= ESCALATION_THRESHOLD).length,
  }), [rows]);

  const visible = filter === 'ALL' ? rows : rows.filter(r => r.processingStatus === filter);

  async function simulate(v: { sourceSystem: string; severity: string; title: string; summary: string }): Promise<void> {
    try {
      const payload: SiemIngestPayload = {
        sourceSystem: v.sourceSystem,
        severity: Number(v.severity),
        title: v.title.trim() || undefined,
        summary: v.summary.trim() || undefined,
        sourceAlertId: `SIM-${v.sourceSystem}-${Math.round(Date.now() / 1000)}`,
        rawPayload: { via: 'siem-bridge-console', simulated: true },
      };
      const saved = await bcmSiemApi.ingest(payload);
      if (saved.processingStatus === 'ESCALATED') {
        toast.danger(isAr
          ? `تم التصعيد — أُنشئت حادثة أزمة #${saved.crisisEventId}`
          : `Escalated — crisis event #${saved.crisisEventId} created`);
      } else {
        toast.success(isAr ? 'تم الاستيعاب — تجاهُل (خطورة منخفضة)' : 'Ingested — ignored (low severity)');
      }
      setSimOpen(false);
      await load();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل الاستيعاب' : 'Ingest failed'));
    }
  }

  const simFields: FieldDef[] = [
    { kind: 'select', key: 'sourceSystem', label: isAr ? 'مصدر التنبيه *' : 'Alert source *', required: true, span: 6,
      options: ['SPLUNK', 'SENTINEL', 'QRADAR', 'CROWDSTRIKE', 'ELASTIC', 'CUSTOM'].map(v => ({ value: v, label: v })) },
    { kind: 'select', key: 'severity', label: isAr ? 'الخطورة (١-٥) *' : 'Severity (1-5) *', required: true, span: 6,
      options: ['1', '2', '3', '4', '5'].map(v => ({ value: v, label: `${v} · ${sevLabel(Number(v), isAr)}` })) },
    { kind: 'text', key: 'title', label: isAr ? 'عنوان التنبيه *' : 'Alert title *', required: true, span: 12 },
    { kind: 'textarea', key: 'summary', label: isAr ? 'الملخّص' : 'Summary', rows: 2, span: 12 },
  ];
  const simInitial = { sourceSystem: 'SPLUNK', severity: '4', title: '', summary: '' };

  return (
    <Shell title={isAr ? 'جسر SIEM لاستمرارية الأعمال' : 'SIEM → BCM bridge'}
      sub={isAr ? 'استيعاب تنبيهات مركز العمليات الأمنية وتصعيدها إلى أزمات' : 'Ingest SOC/SIEM alerts and escalate to crisis events'}>
      <PageHeader
        eyebrow={isAr ? 'استمرارية الأعمال · مركز العمليات الأمنية' : 'Business continuity · SOC bridge'}
        title={isAr ? 'أحداث SIEM الواردة' : 'SIEM inbound events'}
        sub={isAr
          ? `سجل تنبيهات مركز العمليات الأمنية الواردة وقرار الفرز الآلي — الخطورة ≥ ${ESCALATION_THRESHOLD} تُصعَّد إلى حادثة أزمة سيبرانية، والأقل تُتجاهَل مع بقاء الأثر للتدقيق.`
          : `Inbound SOC/SIEM alert log and the auto-triage decision — severity ≥ ${ESCALATION_THRESHOLD} escalates to a cyber crisis event, lower is ignored but audit-trailed.`}
        actions={
          <div style={{ display: 'inline-flex', gap: 8 }}>
            <Button variant="ghost" size="sm" Icon={RefreshCw} onClick={() => void load()} disabled={loading}>
              {isAr ? 'تحديث' : 'Refresh'}
            </Button>
            <Button variant="primary" size="sm" Icon={BellRing} onClick={() => setSimOpen(true)}>
              {isAr ? 'محاكاة تنبيه' : 'Simulate alert'}
            </Button>
          </div>
        }
      />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Radio}       label={isAr ? 'إجمالي الأحداث' : 'Total events'} value={counts.total} tone="primary" onClick={() => setFilter('ALL')} active={filter === 'ALL'} />
        <Kpi Icon={Siren}       label={isAr ? 'مُصعَّدة لأزمة' : 'Escalated to crisis'} value={counts.escalated} tone={counts.escalated > 0 ? 'danger' : 'neutral'} to="/bcm/crisis-room" />
        <Kpi Icon={ShieldAlert} label={isAr ? 'خطورة ≥٣' : 'High severity (≥3)'} value={counts.highSev} tone={counts.highSev > 0 ? 'warning' : 'success'} to="/bcm/crisis-room" />
        <Kpi Icon={Activity}    label={isAr ? 'مُتجاهَلة' : 'Ignored'} value={counts.ignored} tone="info" onClick={() => setFilter('IGNORED')} active={filter === 'IGNORED'} />
      </div>

      <div style={{ display: 'flex', gap: 4, marginBottom: 12, flexWrap: 'wrap' }}>
        {SIEM_STATUS_FILTERS.map(f => (
          <button key={f} onClick={() => setFilter(f)}
            style={{ padding: '7px 12px', fontSize: 12, fontWeight: 600, cursor: 'pointer',
              border: `1px solid ${filter === f ? 'var(--primary)' : 'var(--border)'}`,
              background: filter === f ? 'var(--primary-50)' : 'var(--surface-1)',
              borderRadius: 'var(--r-sm)', color: filter === f ? 'var(--primary)' : 'var(--ink-2)' }}>
            {f === 'ALL' ? (isAr ? 'الكل' : 'All') : siemStatusLabel(f, isAr)}
          </button>
        ))}
      </div>

      <Card title={isAr ? 'سجل الأحداث' : 'Event log'} Icon={Radio}>
        {loading && rows.length === 0
          ? <div style={{ padding: 24, textAlign: 'center', color: 'var(--ink-3)', fontSize: 13 }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div>
          : failed && rows.length === 0
            ? <div style={{ padding: 24, textAlign: 'center', color: 'var(--ink-3)', fontSize: 13 }}>{isAr ? 'السجل غير متاح حالياً' : 'Log unavailable right now'}</div>
            : visible.length === 0
              ? <div style={{ padding: 24, textAlign: 'center', color: 'var(--ink-3)', fontSize: 13 }}>{isAr ? 'لا أحداث في هذا المرشّح' : 'No events in this filter'}</div>
              : <table className="grc-table">
                  <thead><tr>
                    <th>{isAr ? 'الوقت' : 'Received'}</th>
                    <th>{isAr ? 'المصدر' : 'Source'}</th>
                    <th>{isAr ? 'الخطورة' : 'Severity'}</th>
                    <th>{isAr ? 'التنبيه' : 'Alert'}</th>
                    <th>{isAr ? 'الفرز' : 'Triage'}</th>
                    <th>{isAr ? 'الأزمة' : 'Crisis'}</th>
                  </tr></thead>
                  <tbody>
                    {visible.map(e => (
                      <tr key={e.id}>
                        <td className="mono text-2xs">{e.receivedAt?.slice(0, 16).replace('T', ' ')}</td>
                        <td className="text-2xs" style={{ fontWeight: 600 }}>{e.sourceSystem}</td>
                        <td><Badge tone={sevTone(e.severity)}>{e.severity} · {sevLabel(e.severity, isAr)}</Badge></td>
                        <td style={{ maxWidth: 320 }}>
                          <div style={{ fontSize: 12.5, fontWeight: 600, color: 'var(--ink-1)' }}>{e.title}</div>
                          {e.summary && <div style={{ fontSize: 11, color: 'var(--ink-3)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{e.summary}</div>}
                        </td>
                        <td><Badge tone={SIEM_STATUS_TONE[e.processingStatus] ?? 'neutral'} dot>{siemStatusLabel(e.processingStatus, isAr)}</Badge></td>
                        <td>
                          {e.crisisEventId
                            ? <Link to="/bcm/crisis-room" style={{ color: 'var(--danger-fg)', textDecoration: 'none', fontWeight: 600, display: 'inline-flex', alignItems: 'center', gap: 3, fontSize: 12 }}>
                                <Siren size={12} />#{e.crisisEventId}<ArrowUpRight size={11} />
                              </Link>
                            : <span className="text-muted text-2xs">—</span>}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>}
      </Card>

      <div style={{ marginTop: 12, display: 'flex', alignItems: 'center', gap: 8, fontSize: 12, color: 'var(--ink-3)' }}>
        <Zap size={13} style={{ color: 'var(--warning-fg)' }} />
        {isAr
          ? `قاعدة الفرز الآلي: الخطورة ≥ ${ESCALATION_THRESHOLD} → حادثة أزمة سيبرانية (DETECTED)؛ الخطورة ١-٢ → تجاهُل.`
          : `Auto-triage rule: severity ≥ ${ESCALATION_THRESHOLD} → cyber crisis event (DETECTED); severity 1-2 → ignored.`}
      </div>

      <FormModal<{ sourceSystem: string; severity: string; title: string; summary: string }>
        open={simOpen}
        title={isAr ? 'محاكاة تنبيه SIEM وارد' : 'Simulate inbound SIEM alert'}
        fields={simFields}
        initial={simInitial}
        onClose={() => setSimOpen(false)}
        onSubmit={simulate}
        submitLabel={isAr ? 'استيعاب' : 'Ingest'}
      />
    </Shell>
  );
}
