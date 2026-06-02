import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Activity, AlertOctagon, ChevronLeft, TrendingDown } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import {
  kriApi,
  formatKriValue,
  KRI_CATEGORY_LABEL, KRI_FREQUENCY_LABEL, KRI_SOURCE_LABEL,
  KRI_STATUS_LABEL, KRI_STATUS_TONE, KRI_UNIT_LABEL,
  type KriDefinitionResponse, type KriHistoryPoint, type KriBreachResponse,
} from '../../lib/kriEngine';

/**
 * KRI detail — Sprint 13.
 *
 * Sparkline of the last 90 days (status-coloured points), threshold
 * lines, the breach history, and a manual "record measurement" form
 * for KRIs whose source is MANUAL.
 *
 * Saudi-native bilingual labels:
 *   "اعتماد الإقرار" = Acknowledge breach
 *   "خرق المؤشر"     = Breach
 */
export function KriDetailPage() {
  const { lang } = useI18n();
  const { externalId } = useParams<{ externalId: string }>();

  const [def, setDef] = useState<KriDefinitionResponse | null>(null);
  const [history, setHistory] = useState<KriHistoryPoint[]>([]);
  const [breaches, setBreaches] = useState<KriBreachResponse[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [recordValue, setRecordValue] = useState('');
  const [notes, setNotes] = useState('');
  const [recording, setRecording] = useState(false);

  async function load() {
    if (!externalId) return;
    setLoading(true);
    setError(null);
    try {
      const [d, h, b] = await Promise.all([
        kriApi.getDefinition(externalId),
        kriApi.history(externalId, 90),
        kriApi.breachesForKri(externalId),
      ]);
      setDef(d); setHistory(h); setBreaches(b);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load KRI');
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { load(); /* eslint-disable-next-line */ }, [externalId]);

  async function recordMeasurement() {
    if (!externalId || !recordValue) return;
    setRecording(true);
    setError(null);
    try {
      const value = Number(recordValue);
      if (Number.isNaN(value)) {
        throw new ApiError(400,
          lang === 'ar' ? 'قيمة غير صالحة' : 'Invalid numeric value', null);
      }
      await kriApi.recordMeasurement(externalId, {
        value,
        source: 'MANUAL',
        notesEn: lang === 'en' ? notes || undefined : undefined,
        notesAr: lang === 'ar' ? notes || undefined : undefined,
      });
      setRecordValue('');
      setNotes('');
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to record measurement');
    } finally {
      setRecording(false);
    }
  }

  async function ack(breachExternalId: string) {
    try {
      await kriApi.acknowledgeBreach(breachExternalId);
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to acknowledge breach');
    }
  }

  const breachCols: Column<KriBreachResponse>[] = [
    {
      header: lang === 'ar' ? 'المستوى' : 'Level', width: '90px',
      cell: b => <Badge tone={b.breachLevel === 'RED' ? 'danger' : 'warning'} dot>
        {b.breachLevel}
      </Badge>,
    },
    {
      header: lang === 'ar' ? 'فُتح' : 'Opened', width: '160px',
      cell: b => <span style={{ fontVariantNumeric: 'tabular-nums', fontSize: 12 }}>
        {new Date(b.openedAt).toLocaleString(lang === 'ar' ? 'ar-SA' : 'en-GB')}
      </span>,
    },
    {
      header: lang === 'ar' ? 'أُغلق' : 'Closed', width: '160px',
      cell: b => b.closedAt
        ? <span style={{ fontVariantNumeric: 'tabular-nums', fontSize: 12 }}>
            {new Date(b.closedAt).toLocaleString(lang === 'ar' ? 'ar-SA' : 'en-GB')}
          </span>
        : <Badge tone="warning">{lang === 'ar' ? 'مفتوح' : 'Open'}</Badge>,
    },
    {
      header: lang === 'ar' ? 'الإقرار' : 'Acknowledged', width: '180px',
      cell: b => b.acknowledgedAt
        ? <span style={{ fontSize: 12, color: 'var(--ink-3)' }}>
            {new Date(b.acknowledgedAt).toLocaleString(lang === 'ar' ? 'ar-SA' : 'en-GB')}
          </span>
        : (b.closedAt
            ? '—'
            : <button onClick={() => ack(b.externalId)}
                      style={{ fontSize: 12, padding: '4px 8px',
                               border: '1px solid var(--border)', borderRadius: 4,
                               background: 'var(--bg)', cursor: 'pointer' }}>
                {lang === 'ar' ? 'اعتماد الإقرار' : 'Acknowledge'}
              </button>),
    },
    {
      header: lang === 'ar' ? 'إجراء تصحيحي' : 'CAPA', width: '90px',
      cell: b => b.capaTaskId
        ? <Link to={`/issues/${b.capaTaskId}`} style={{ fontSize: 12 }}>
            #{b.capaTaskId}
          </Link>
        : '—',
    },
  ];

  return (
    <Shell
      title={lang === 'ar' ? 'تفاصيل مؤشّر المخاطر' : 'KRI detail'}
      sub={def ? def.kriCode : ''}
    >
      <Link to="/risk/kri/dashboard"
            style={{ fontSize: 12, color: 'var(--ink-3)', display: 'inline-block', marginBottom: 4 }}>
        <ChevronLeft size={12} style={{ verticalAlign: '-1px' }} />
        {lang === 'ar' ? 'العودة إلى لوحة المؤشّرات' : 'Back to KRI dashboard'}
      </Link>
      <PageHeader
        eyebrow={lang === 'ar' ? 'مؤشّرات المخاطر الرئيسية' : 'Key Risk Indicators'}
        title={def
          ? (lang === 'ar' ? def.titleAr : def.titleEn)
          : (lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…')}
        sub={def
          ? `${def.kriCode} · ${KRI_CATEGORY_LABEL[def.category][lang === 'ar' ? 'ar' : 'en']} · ${KRI_FREQUENCY_LABEL[def.frequency][lang === 'ar' ? 'ar' : 'en']}`
          : undefined}
        actions={def?.latestStatus
          ? <Badge tone={KRI_STATUS_TONE[def.latestStatus]} dot>
              {KRI_STATUS_LABEL[def.latestStatus][lang === 'ar' ? 'ar' : 'en']}
            </Badge>
          : undefined}
      />

      {error && (
        <Card padded>
          <div style={{ color: 'var(--danger)', fontSize: 13 }}>{error}</div>
        </Card>
      )}

      {def && (
        <Card padded>
          <h3 style={{ marginTop: 0 }}>
            {lang === 'ar' ? 'العتبات والقيمة الحالية' : 'Thresholds & current value'}
          </h3>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 12 }}>
            <ThresholdTile
              label={lang === 'ar' ? 'القيمة الأخيرة' : 'Latest value'}
              value={formatKriValue(def.latestValue, def.measurementUnit)}
              tone={def.latestStatus ? KRI_STATUS_TONE[def.latestStatus] : 'neutral'}
            />
            <ThresholdTile label={lang === 'ar' ? 'الهدف' : 'Target'}
              value={formatKriValue(def.targetValue, def.measurementUnit)} tone="info" />
            <ThresholdTile label={lang === 'ar' ? 'حدّ الأخضر' : 'Green ≤/≥'}
              value={formatKriValue(def.greenThreshold, def.measurementUnit)} tone="success" />
            <ThresholdTile label={lang === 'ar' ? 'حدّ الكهرماني' : 'Amber'}
              value={formatKriValue(def.amberThreshold, def.measurementUnit)} tone="warning" />
            <ThresholdTile label={lang === 'ar' ? 'حدّ الأحمر' : 'Red anchor'}
              value={formatKriValue(def.redThreshold, def.measurementUnit)} tone="danger" />
            <ThresholdTile label={lang === 'ar' ? 'الوحدة' : 'Unit'}
              value={KRI_UNIT_LABEL[def.measurementUnit][lang === 'ar' ? 'ar' : 'en']} tone="neutral" />
            <ThresholdTile label={lang === 'ar' ? 'المصدر' : 'Source'}
              value={KRI_SOURCE_LABEL[def.sourceQueryType][lang === 'ar' ? 'ar' : 'en']} tone="neutral" />
            <ThresholdTile label={lang === 'ar' ? 'الاتجاه' : 'Direction'}
              value={def.valueDirection === 'HIGHER_IS_BETTER'
                ? (lang === 'ar' ? 'الأعلى أفضل' : 'higher = better')
                : (lang === 'ar' ? 'الأقل أفضل' : 'lower = better')}
              tone="neutral" />
          </div>
          {(def.descriptionEn || def.descriptionAr) && (
            <p style={{ marginTop: 12, fontSize: 13, color: 'var(--ink-3)' }}>
              {lang === 'ar' ? def.descriptionAr : def.descriptionEn}
            </p>
          )}
        </Card>
      )}

      <Card padded>
        <h3 style={{ marginTop: 0 }}>
          {lang === 'ar' ? 'الاتجاه (90 يوماً)' : 'Trend (90 days)'}
        </h3>
        {loading
          ? <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
          : history.length === 0
            ? <EmptyState
                Icon={TrendingDown}
                title={lang === 'ar' ? 'لا قياسات بعد' : 'No measurements yet'}
                sub={lang === 'ar'
                  ? 'سيُرسم المخطّط فور تسجيل أول قياس.'
                  : 'The sparkline will draw once the first measurement is recorded.'} />
            : <Sparkline points={history} def={def} lang={lang} />
        }
      </Card>

      {def?.sourceQueryType === 'MANUAL' && (
        <Card padded>
          <h3 style={{ marginTop: 0 }}>
            {lang === 'ar' ? 'تسجيل قياس يدوي' : 'Record a manual measurement'}
          </h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8, maxWidth: 480 }}>
            <label style={{ fontSize: 12, color: 'var(--ink-3)' }}>
              {lang === 'ar' ? 'القيمة' : 'Value'}
              <input type="number" value={recordValue}
                     onChange={e => setRecordValue(e.target.value)}
                     style={{ display: 'block', width: '100%', padding: '6px 8px',
                              border: '1px solid var(--border)', borderRadius: 4, marginTop: 4 }} />
            </label>
            <label style={{ fontSize: 12, color: 'var(--ink-3)' }}>
              {lang === 'ar' ? 'ملاحظات (اختياري)' : 'Notes (optional)'}
              <input type="text" value={notes} onChange={e => setNotes(e.target.value)}
                     style={{ display: 'block', width: '100%', padding: '6px 8px',
                              border: '1px solid var(--border)', borderRadius: 4, marginTop: 4 }} />
            </label>
            <button onClick={recordMeasurement} disabled={recording || !recordValue}
                    style={{ alignSelf: 'flex-start', padding: '6px 12px',
                             border: '1px solid var(--primary)', borderRadius: 4,
                             background: 'var(--primary)', color: 'white', cursor: 'pointer',
                             opacity: recording || !recordValue ? 0.5 : 1, fontSize: 13 }}>
              <Activity size={13} style={{ verticalAlign: '-2px', marginInlineEnd: 6 }} />
              {lang === 'ar' ? 'تسجيل' : 'Record'}
            </button>
          </div>
        </Card>
      )}

      <Card padded>
        <h3 style={{ marginTop: 0 }}>
          {lang === 'ar' ? 'سجل خرق المؤشر' : 'Breach history'}
        </h3>
        {breaches.length === 0
          ? <InlineEmpty>
              {lang === 'ar' ? 'لم يُسجَّل خرق لهذا المؤشر.' : 'No breaches recorded for this KRI.'}
            </InlineEmpty>
          : <DataTable
              columns={breachCols}
              rows={breaches}
              empty={<EmptyState
                Icon={AlertOctagon}
                title={lang === 'ar' ? 'لا خروقات' : 'No breaches'} />}
            />
        }
      </Card>
    </Shell>
  );
}

interface SparklineProps {
  points: KriHistoryPoint[];
  def: KriDefinitionResponse | null;
  lang: 'en' | 'ar';
}

function Sparkline({ points, def, lang }: SparklineProps) {
  const W = 800, H = 180, PAD = 24;
  const values = points.map(p => Number(p.value));
  const thresholds = [def?.greenThreshold, def?.amberThreshold, def?.redThreshold]
    .filter((v): v is number => v != null);
  const allNumbers = [...values, ...thresholds];
  const minV = Math.min(...allNumbers);
  const maxV = Math.max(...allNumbers);
  const rangeV = maxV - minV || 1;

  const yFor = (v: number) =>
    H - PAD - ((v - minV) / rangeV) * (H - PAD * 2);
  const xFor = (i: number) =>
    PAD + (points.length === 1 ? (W - PAD * 2) / 2
                                : (i / (points.length - 1)) * (W - PAD * 2));

  const path = points.map((p, i) =>
    `${i === 0 ? 'M' : 'L'} ${xFor(i)} ${yFor(Number(p.value))}`).join(' ');

  const colorFor = (s: string) =>
    s === 'GREEN' ? '#22c55e' : s === 'AMBER' ? '#f59e0b' : s === 'RED' ? '#ef4444' : '#9ca3af';

  return (
    <svg width="100%" height={H} viewBox={`0 0 ${W} ${H}`} role="img"
         aria-label={lang === 'ar' ? 'الخطّ الزمني للمؤشّر' : 'KRI trend line'}>
      {/* threshold lines */}
      {def?.greenThreshold != null && (
        <line x1={PAD} x2={W - PAD} y1={yFor(def.greenThreshold)} y2={yFor(def.greenThreshold)}
              stroke="#22c55e" strokeOpacity={0.5} strokeDasharray="4 4" />
      )}
      {def?.amberThreshold != null && (
        <line x1={PAD} x2={W - PAD} y1={yFor(def.amberThreshold)} y2={yFor(def.amberThreshold)}
              stroke="#f59e0b" strokeOpacity={0.5} strokeDasharray="4 4" />
      )}
      {def?.redThreshold != null && (
        <line x1={PAD} x2={W - PAD} y1={yFor(def.redThreshold)} y2={yFor(def.redThreshold)}
              stroke="#ef4444" strokeOpacity={0.5} strokeDasharray="4 4" />
      )}
      {/* trend line */}
      <path d={path} fill="none" stroke="var(--ink-1)" strokeOpacity={0.6} strokeWidth={1.5} />
      {/* points coloured by status */}
      {points.map((p, i) => (
        <circle key={i} cx={xFor(i)} cy={yFor(Number(p.value))} r={3}
                fill={colorFor(p.status)}>
          <title>{new Date(p.measuredAt).toLocaleString(lang === 'ar' ? 'ar-SA' : 'en-GB')} — {p.value} ({p.status})</title>
        </circle>
      ))}
    </svg>
  );
}

interface ThresholdTileProps {
  label: string;
  value: string;
  tone: 'success' | 'warning' | 'danger' | 'info' | 'neutral';
}
function ThresholdTile({ label, value, tone }: ThresholdTileProps) {
  const colour = {
    success: '#22c55e', warning: '#f59e0b', danger: '#ef4444',
    info: 'var(--primary)', neutral: 'var(--ink-3)',
  }[tone];
  return (
    <div style={{ border: '1px solid var(--border)', borderRadius: 6, padding: 10 }}>
      <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 4 }}>{label}</div>
      <div style={{ fontSize: 16, fontWeight: 600, color: colour, fontVariantNumeric: 'tabular-nums' }}>
        {value}
      </div>
    </div>
  );
}
