import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Radar, Crosshair, Play, Plus, Trash2, Target, Server, Bug, Siren, Clock, Rss,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  threatIntelApi, ApiError, IOC_TYPES, IOC_SEVERITIES,
  type IndicatorOfCompromise, type CorrelationReport,
} from '../../lib/api';

const INPUT_STYLE: React.CSSProperties = {
  padding: '6px 8px', fontSize: 13, width: '100%', border: '1px solid var(--border)',
  borderRadius: 'var(--r-sm)', background: 'var(--surface-1)', color: 'var(--ink-1)',
};

// SAMA Cyber Framework §3.5 — material-incident notification window.
const SAMA_WINDOW_MS = 4 * 60 * 60 * 1000;

const SEV_COLOR: Record<string, string> = {
  CRITICAL: 'var(--risk-crit)', HIGH: 'var(--risk-high)', MEDIUM: 'var(--risk-med)',
  LOW: 'var(--risk-low)', INFO: 'var(--ink-3)',
};

type Row = { id: number; type: string; value: string; severity: string; context: string };

interface Preset {
  id: string; en: string; ar: string; feedName: string;
  rows: Array<Omit<Row, 'id'>>;
}

/* Presets use real seeded org-1 IOCs so correlation lands demonstrable hits. */
const PRESETS: Preset[] = [
  {
    id: 'sama-weekly', en: 'SAMA Cyber weekly bulletin', ar: 'نشرة ساما السيبرانية الأسبوعية',
    feedName: 'SAMA-CYBER-2026-W22',
    rows: [
      { type: 'CVE', value: 'CVE-2022-3602', severity: 'CRITICAL', context: 'OpenSSL RCE — active exploitation' },
      { type: 'CVE', value: 'CVE-2024-12345', severity: 'CRITICAL', context: 'SMBv1 wormable' },
      { type: 'IP', value: '10.10.1.5', severity: 'HIGH', context: 'Outbound C2 beacon destination' },
      { type: 'HOSTNAME', value: 'cbs', severity: 'MEDIUM', context: 'Suspicious DNS lookups' },
      { type: 'IP', value: '203.0.113.99', severity: 'LOW', context: 'Scanning source (no estate match expected)' },
    ],
  },
  {
    id: 'nca-haseen', en: 'NCA Haseen advisory — OpenSSL', ar: 'تنبيه حصين — OpenSSL',
    feedName: 'NCA-HASEEN-2026-114',
    rows: [
      { type: 'CVE', value: 'CVE-2024-23456', severity: 'HIGH', context: 'OpenSSL Heartbleed information disclosure' },
      { type: 'CVE', value: 'CVE-2022-3602', severity: 'CRITICAL', context: 'OpenSSL buffer overflow RCE' },
      { type: 'HOSTNAME', value: 'sadad', severity: 'HIGH', context: 'Payment-gateway probing' },
      { type: 'IP', value: '10.10.20.11', severity: 'MEDIUM', context: 'Lateral-movement candidate' },
    ],
  },
  {
    id: 'misp-vendor', en: 'Commercial MISP feed', ar: 'تغذية MISP تجارية',
    feedName: 'MISP-VENDOR-2026-0603',
    rows: [
      { type: 'CVE', value: 'CVE-2023-34567', severity: 'MEDIUM', context: 'Apache Log4j RCE' },
      { type: 'IP', value: '10.10.1.20', severity: 'HIGH', context: 'Card-data exfil destination' },
      { type: 'HOSTNAME', value: 'murex', severity: 'LOW', context: 'Treasury host enumeration' },
      { type: 'IP', value: '198.51.100.7', severity: 'HIGH', context: 'Known-bad (no estate match expected)' },
    ],
  },
  {
    id: 'clean-drill', en: 'Clean drill (no hits)', ar: 'تمرين نظيف (بدون تطابق)',
    feedName: 'DRILL-CLEAN-2026',
    rows: [
      { type: 'CVE', value: 'CVE-2099-00000', severity: 'LOW', context: 'Synthetic IOC' },
      { type: 'IP', value: '8.8.8.8', severity: 'INFO', context: 'Public resolver' },
      { type: 'HOSTNAME', value: 'external-unknown', severity: 'LOW', context: 'Unmatched hostname' },
    ],
  },
];

let _rid = 1;
const mkRows = (p: Preset): Row[] => p.rows.map(r => ({ ...r, id: _rid++ }));

const two = (n: number) => String(Math.floor(n)).padStart(2, '0');

/**
 * Threat-intel IOC correlation console — surfaces
 * POST /api/v1/cyber/threat-intel/correlate.
 *
 * Submit a bundle of indicators (CVE / IP / HOSTNAME) from a feed (SAMA, NCA
 * Haseen, MISP, commercial vendor); the backend matches them against the
 * tenant's cyber assets + vulnerabilities and returns per-IOC hits with a
 * blended severity. When a hit lands on a CRITICAL vuln or a CRITICAL/HIGH
 * asset, regulatorEscalation fires — this page then starts the SAMA Cyber
 * Framework §3.5 four-hour notification clock. Bilingual; severity uses the
 * shared --risk-* ramp.
 */
export function ThreatIntelCorrelationPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const sevLabel = useCallback((s?: string) => {
    const k = (s ?? 'INFO').toUpperCase();
    if (isAr) return ({ CRITICAL: 'حرجة', HIGH: 'عالية', MEDIUM: 'متوسطة', LOW: 'منخفضة', INFO: 'معلوماتية' } as Record<string, string>)[k] ?? k;
    return ({ CRITICAL: 'Critical', HIGH: 'High', MEDIUM: 'Medium', LOW: 'Low', INFO: 'Info' } as Record<string, string>)[k] ?? k;
  }, [isAr]);
  const typeLabel = useCallback((t: string) => {
    const k = (t ?? '').toUpperCase();
    if (k === 'HOSTNAME') return isAr ? 'اسم المضيف' : 'Hostname';
    return k; // CVE / IP keep their canonical form
  }, [isAr]);
  const entityLabel = useCallback((t: string) =>
    (t ?? '').toUpperCase() === 'VULNERABILITY' ? (isAr ? 'ثغرة' : 'Vulnerability') : (isAr ? 'أصل' : 'Asset'),
  [isAr]);

  const [feedName, setFeedName] = useState(PRESETS[0].feedName);
  const [rows, setRows] = useState<Row[]>(() => mkRows(PRESETS[0]));
  const [result, setResult] = useState<CorrelationReport | null>(null);
  const [submitted, setSubmitted] = useState<IndicatorOfCompromise[]>([]);
  const [busy, setBusy] = useState(false);

  // Live "now" tick for the SAMA 4h countdown.
  const [now, setNow] = useState(() => Date.now());
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  useEffect(() => {
    timerRef.current = setInterval(() => setNow(Date.now()), 1000);
    return () => { if (timerRef.current) clearInterval(timerRef.current); };
  }, []);

  const applyPreset = (p: Preset) => {
    setFeedName(p.feedName);
    setRows(mkRows(p));
    setResult(null);
    setSubmitted([]);
  };
  const addRow = () => setRows(rs => [...rs, { id: _rid++, type: 'CVE', value: '', severity: 'HIGH', context: '' }]);
  const removeRow = (id: number) => setRows(rs => rs.filter(r => r.id !== id));
  const patchRow = (id: number, k: keyof Row, v: string) => setRows(rs => rs.map(r => r.id === id ? { ...r, [k]: v } : r));

  const correlate = useCallback(async () => {
    if (!feedName.trim()) { toast.danger(isAr ? 'اسم التغذية مطلوب' : 'Feed name is required'); return; }
    const indicators: IndicatorOfCompromise[] = rows
      .filter(r => r.value.trim())
      .map(r => ({ type: r.type, value: r.value.trim(), severity: r.severity, context: r.context.trim() || undefined }));
    if (indicators.length === 0) { toast.danger(isAr ? 'أضف مؤشّر اختراق واحدًا على الأقل' : 'Add at least one indicator'); return; }
    setBusy(true);
    try {
      const r = await threatIntelApi.correlate({ feedName: feedName.trim(), feedTimestamp: new Date(now).toISOString(), indicators });
      setResult(r);
      setSubmitted(indicators);
      if (r.regulatorEscalation) {
        toast.danger(isAr ? '⚠ تصعيد تنظيمي — بدأت مهلة ساما (4 ساعات)' : '⚠ Regulator escalation — SAMA 4h clock started');
      } else {
        toast.success(isAr ? `${r.matchCount} تطابق` : `${r.matchCount} match${r.matchCount === 1 ? '' : 'es'}`);
      }
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل الارتباط' : 'Correlation failed'));
    } finally { setBusy(false); }
  }, [feedName, rows, now, isAr]);  // eslint-disable-line react-hooks/exhaustive-deps

  // Auto-correlate the default preset on first mount.
  useEffect(() => { void correlate(); }, []);  // eslint-disable-line react-hooks/exhaustive-deps

  const hits = result?.hits ?? [];

  // IOCs that produced no estate match — reassuring "clean" set for the SOC.
  const unmatched = useMemo(() => {
    if (!result) return [];
    const hitKeys = new Set(hits.map(h => `${(h.iocType || '').toUpperCase()}:${(h.iocValue || '').toUpperCase()}`));
    return submitted.filter(s => !hitKeys.has(`${(s.type || '').toUpperCase()}:${(s.value || '').toUpperCase()}`));
  }, [result, hits, submitted]);

  // SAMA 4h clock — anchored to the server's evaluatedAt.
  const clock = useMemo(() => {
    if (!result?.regulatorEscalation) return null;
    const startMs = new Date(result.evaluatedAt).getTime();
    if (Number.isNaN(startMs)) return null;
    const deadline = startMs + SAMA_WINDOW_MS;
    const remaining = Math.max(0, deadline - now);
    const h = remaining / 3_600_000;
    const m = (remaining % 3_600_000) / 60_000;
    const s = (remaining % 60_000) / 1000;
    const pct = Math.max(0, Math.min(100, (remaining / SAMA_WINDOW_MS) * 100));
    return { remaining, text: `${two(h)}:${two(m)}:${two(s)}`, pct, deadline: new Date(deadline) };
  }, [result, now]);

  const matchedTitle = (h: { matchedTitleEn?: string; matchedTitleAr?: string; assetName?: string }) =>
    (isAr ? h.matchedTitleAr : h.matchedTitleEn) || h.matchedTitleEn || h.assetName || '—';

  return (
    <Shell
      title={isAr ? 'ارتباط معلومات التهديدات' : 'Threat-intel correlation'}
      sub={isAr ? 'مطابقة مؤشّرات الاختراق مع الأصول والثغرات' : 'Match IOCs against assets & vulnerabilities'}
    >
      <PageHeader
        eyebrow={isAr ? 'الأمن السيبراني · معلومات التهديدات · ساما/الهيئة الوطنية' : 'Cyber · Threat Intelligence · SAMA / NCA'}
        title={isAr ? 'ارتباط مؤشّرات الاختراق' : 'IOC correlation'}
        sub={isAr
          ? 'استورد حزمة مؤشّرات اختراق (CVE / IP / اسم مضيف) من تغذية تهديدات، وطابقها فورًا مع الأصول والثغرات في منشأتك. عند إصابة أصل حرج/عالي الأهمية يبدأ عدّاد ساما السيبراني (4 ساعات) تلقائيًا.'
          : 'Import an IOC bundle (CVE / IP / hostname) from a threat feed and correlate it in real time against your estate of assets and vulnerabilities. A hit on a critical/high-criticality entity automatically starts the SAMA Cyber 4-hour clock.'}
      />

      {/* SAMA escalation banner — only when the report flags it */}
      {result?.regulatorEscalation && clock && (
        <div
          role="alert"
          style={{
            display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap',
            padding: '14px 18px', marginBottom: 16, borderRadius: 'var(--r-sm)',
            background: 'var(--danger-bg)', border: '1px solid var(--danger-border)', color: 'var(--danger-fg)',
          }}
        >
          <Siren size={26} style={{ flexShrink: 0 }} />
          <div style={{ flex: 1, minWidth: 240 }}>
            <div style={{ fontWeight: 700, fontSize: 14 }}>
              {isAr ? 'تصعيد تنظيمي — إطار ساما السيبراني §3.5' : 'Regulator escalation — SAMA Cyber Framework §3.5'}
            </div>
            <div style={{ fontSize: 12.5, opacity: 0.9, marginTop: 2 }}>
              {isAr
                ? 'تطابق على كيان حرج. يجب إبلاغ ساما خلال 4 ساعات.'
                : 'Match on a critical entity. SAMA must be notified within 4 hours.'}
              {' '}
              {isAr ? 'الموعد النهائي:' : 'Deadline:'}{' '}
              <span className="mono">{clock.deadline.toLocaleString(isAr ? 'ar-SA' : 'en-GB')}</span>
            </div>
          </div>
          <div style={{ textAlign: 'center', minWidth: 132 }}>
            <div className="mono" style={{ fontSize: 26, fontWeight: 700, lineHeight: 1, display: 'flex', alignItems: 'center', gap: 8, justifyContent: 'center' }}>
              <Clock size={20} />{clock.text}
            </div>
            <div style={{ height: 4, borderRadius: 2, marginTop: 8, background: 'var(--danger-border)', overflow: 'hidden' }}>
              <div style={{ height: '100%', width: `${clock.pct}%`, background: 'var(--danger)', transition: 'width 1s linear' }} />
            </div>
            <div style={{ fontSize: 10.5, marginTop: 4, letterSpacing: 0.4 }}>
              {clock.remaining === 0 ? (isAr ? 'انتهت المهلة' : 'WINDOW ELAPSED') : (isAr ? 'متبقٍّ للإبلاغ' : 'TO NOTIFY')}
            </div>
          </div>
        </div>
      )}

      {/* IOC bundle input */}
      <Card
        title={isAr ? 'حزمة مؤشّرات الاختراق' : 'IOC bundle'} Icon={Crosshair}
        action={
          <select value="" onChange={e => { const p = PRESETS.find(x => x.id === e.target.value); if (p) applyPreset(p); e.currentTarget.value = ''; }}
            style={{ ...INPUT_STYLE, width: 'auto', fontSize: 12 }} aria-label={isAr ? 'تغذية جاهزة' : 'Preset feed'}>
            <option value="">{isAr ? 'تحميل تغذية جاهزة…' : 'Load preset feed…'}</option>
            {PRESETS.map(p => <option key={p.id} value={p.id}>{isAr ? p.ar : p.en}</option>)}
          </select>
        }>
        <div style={{ marginBottom: 14, maxWidth: 460 }}>
          <label style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink-1)', display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
            <Rss size={13} />{isAr ? 'اسم التغذية' : 'Feed name'}
          </label>
          <input type="text" value={feedName} onChange={e => setFeedName(e.target.value)} style={INPUT_STYLE} maxLength={120}
            placeholder={isAr ? 'مثال: SAMA-CYBER-2026-W22' : 'e.g. SAMA-CYBER-2026-W22'} />
        </div>

        {/* Column headers */}
        <div style={{ display: 'grid', gridTemplateColumns: '120px 1.6fr 130px 1.4fr 36px', gap: 8, marginBottom: 4 }}>
          <span className="text-2xs text-muted">{isAr ? 'النوع' : 'Type'}</span>
          <span className="text-2xs text-muted">{isAr ? 'القيمة' : 'Value'}</span>
          <span className="text-2xs text-muted">{isAr ? 'الخطورة' : 'Severity'}</span>
          <span className="text-2xs text-muted">{isAr ? 'السياق (اختياري)' : 'Context (optional)'}</span>
          <span />
        </div>
        {rows.map(r => (
          <div key={r.id} style={{ display: 'grid', gridTemplateColumns: '120px 1.6fr 130px 1.4fr 36px', gap: 8, alignItems: 'center', marginBottom: 6 }}>
            <select value={r.type} onChange={e => patchRow(r.id, 'type', e.target.value)} style={{ ...INPUT_STYLE, fontSize: 12 }} aria-label={isAr ? 'نوع المؤشّر' : 'IOC type'}>
              {IOC_TYPES.map(t => <option key={t} value={t}>{typeLabel(t)}</option>)}
            </select>
            <input type="text" value={r.value} onChange={e => patchRow(r.id, 'value', e.target.value)} className="mono"
              style={{ ...INPUT_STYLE, fontSize: 12 }} placeholder={r.type === 'CVE' ? 'CVE-2025-0001' : r.type === 'IP' ? '10.10.1.5' : 'host.tss.local'}
              aria-label={isAr ? 'قيمة المؤشّر' : 'IOC value'} />
            <select value={r.severity} onChange={e => patchRow(r.id, 'severity', e.target.value)} style={{ ...INPUT_STYLE, fontSize: 12 }} aria-label={isAr ? 'خطورة المؤشّر' : 'IOC severity'}>
              {IOC_SEVERITIES.map(s => <option key={s} value={s}>{sevLabel(s)}</option>)}
            </select>
            <input type="text" value={r.context} onChange={e => patchRow(r.id, 'context', e.target.value)}
              style={{ ...INPUT_STYLE, fontSize: 12 }} placeholder={isAr ? 'إسناد / ملاحظات' : 'attribution / notes'} aria-label={isAr ? 'سياق' : 'context'} />
            <button onClick={() => removeRow(r.id)} title={isAr ? 'حذف' : 'Remove'} aria-label={isAr ? 'حذف المؤشّر' : 'Remove indicator'}
              style={{ display: 'inline-flex', alignItems: 'center', justifyContent: 'center', height: 30, width: 30, border: '1px solid var(--border)', borderRadius: 'var(--r-sm)', background: 'var(--surface-1)', color: 'var(--ink-3)', cursor: 'pointer' }}>
              <Trash2 size={14} />
            </button>
          </div>
        ))}

        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginTop: 12, flexWrap: 'wrap' }}>
          <Button size="sm" variant="secondary" Icon={Plus} onClick={addRow}>{isAr ? 'إضافة مؤشّر' : 'Add indicator'}</Button>
          <Button size="sm" variant="primary" Icon={busy ? Radar : Play} onClick={() => void correlate()} disabled={busy}>
            {busy ? (isAr ? 'جارٍ الارتباط…' : 'Correlating…') : (isAr ? 'ارتبط' : 'Correlate')}
          </Button>
          <span className="text-2xs text-muted" style={{ marginInlineStart: 'auto' }}>
            {isAr ? 'CVE ← الثغرات · IP/المضيف ← الأصول' : 'CVE ← vulnerabilities · IP/hostname ← assets'}
          </span>
        </div>
      </Card>

      {result && (
        <>
          <div className="kpi-grid" style={{ marginBottom: 16 }}>
            <Kpi Icon={Target}    label={isAr ? 'إجمالي التطابقات' : 'Total matches'} value={result.matchCount} tone={result.matchCount > 0 ? 'primary' : 'neutral'} to="/cyber" />
            <Kpi Icon={Crosshair} label={isAr ? 'مؤشّرات مطابِقة' : 'IOCs matched'} value={`${result.distinctIocsMatched} / ${submitted.length}`} tone="info" to="/cyber/threat-intel" />
            <Kpi Icon={Server}    label={isAr ? 'كيانات مصابة' : 'Entities hit'} value={result.distinctEntitiesMatched} tone="warning" to="/assets" />
            <Kpi Icon={Siren}     label={isAr ? 'تصعيد ساما' : 'SAMA escalation'}
              value={result.regulatorEscalation ? (isAr ? 'نعم' : 'YES') : (isAr ? 'لا' : 'No')}
              tone={result.regulatorEscalation ? 'danger' : 'success'} to="/socops" />
          </div>

          <Card
            title={isAr ? 'التطابقات' : 'Correlation hits'} Icon={Radar}
            action={<span className="text-2xs text-muted">{result.feedName} · {new Date(result.evaluatedAt).toLocaleString(isAr ? 'ar-SA' : 'en-GB')}</span>}
          >
            {hits.length === 0 ? (
              <div style={{ padding: '28px 8px', textAlign: 'center', color: 'var(--ink-3)', fontSize: 13 }}>
                {isAr ? 'لا تطابقات — لا يوجد أيٌّ من هذه المؤشّرات في منشأتك.' : 'No matches — none of these indicators are present in your estate.'}
              </div>
            ) : (
              <div style={{ overflowX: 'auto' }}>
                <table className="grc-table">
                  <thead>
                    <tr>
                      <th>{isAr ? 'المؤشّر' : 'Indicator'}</th>
                      <th>{isAr ? 'خطورة المؤشّر' : 'IOC sev.'}</th>
                      <th>{isAr ? 'الكيان المصاب' : 'Matched entity'}</th>
                      <th>{isAr ? 'خطورة الكيان' : 'Entity sev.'}</th>
                      <th>{isAr ? 'الخطورة المدمجة' : 'Blended'}</th>
                      <th>{isAr ? 'التغذية' : 'Source feed'}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {hits.map((h, i) => {
                      const isVuln = (h.matchedEntityType || '').toUpperCase() === 'VULNERABILITY';
                      return (
                        <tr key={i}>
                          <td>
                            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                              <span className="badge-sm" style={{ background: 'var(--surface-2)', color: 'var(--ink-2)', fontWeight: 600 }}>{typeLabel(h.iocType)}</span>
                              <span className="mono text-2xs">{h.iocValue}</span>
                            </div>
                          </td>
                          <td><SevChip sev={h.iocSeverity} label={sevLabel(h.iocSeverity)} /></td>
                          <td>
                            <div style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
                              {isVuln ? <Bug size={14} style={{ color: 'var(--ink-3)', flexShrink: 0 }} /> : <Server size={14} style={{ color: 'var(--ink-3)', flexShrink: 0 }} />}
                              <div style={{ minWidth: 0 }}>
                                <div style={{ fontSize: 12.5, color: 'var(--ink-1)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', maxWidth: 280 }}>{matchedTitle(h)}</div>
                                <div className="text-2xs text-muted">{entityLabel(h.matchedEntityType)}{h.assetName && !isVuln ? '' : (h.assetName ? ` · ${h.assetName}` : '')}</div>
                              </div>
                            </div>
                          </td>
                          <td><SevChip sev={h.matchedSeverity} label={sevLabel(h.matchedSeverity)} /></td>
                          <td><SevChip sev={h.blendedSeverity} label={sevLabel(h.blendedSeverity)} strong /></td>
                          <td className="text-2xs text-muted mono">{h.sourceFeed || result.feedName}</td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}

            {/* Severity legend */}
            <div style={{ display: 'flex', gap: 14, marginTop: 14, flexWrap: 'wrap' }}>
              {IOC_SEVERITIES.map(s => (
                <span key={s} style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 11.5, color: 'var(--ink-2)' }}>
                  <span style={{ width: 9, height: 9, borderRadius: 2, background: SEV_COLOR[s] }} />{sevLabel(s)}
                </span>
              ))}
            </div>
          </Card>

          {unmatched.length > 0 && (
            <Card title={isAr ? 'مؤشّرات بلا تطابق' : 'No-match indicators'} Icon={Crosshair}>
              <p className="text-2xs text-muted" style={{ marginTop: 0, marginBottom: 10 }}>
                {isAr ? 'هذه المؤشّرات غير موجودة في أصولك أو ثغراتك الحالية — لا إجراء مطلوب.' : 'These indicators are absent from your current assets and vulnerabilities — no action required.'}
              </p>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                {unmatched.map((u, i) => (
                  <span key={i} className="mono" style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 11.5, padding: '3px 9px', borderRadius: 999, border: '1px dashed var(--border)', color: 'var(--ink-3)', background: 'var(--surface-1)' }}>
                    <span style={{ width: 7, height: 7, borderRadius: 2, background: SEV_COLOR[(u.severity || 'INFO').toUpperCase()] ?? 'var(--ink-3)' }} />
                    {typeLabel(u.type)} · {u.value}
                  </span>
                ))}
              </div>
            </Card>
          )}
        </>
      )}
    </Shell>
  );
}

/* Severity chip — colored dot + label, shared --risk-* ramp. `strong` bolds
   the blended verdict so the row's effective severity reads first. */
function SevChip({ sev, label, strong }: { sev?: string; label: string; strong?: boolean }) {
  const k = (sev ?? 'INFO').toUpperCase();
  const c = SEV_COLOR[k] ?? 'var(--ink-3)';
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 11.5, fontWeight: strong ? 700 : 500, color: strong ? 'var(--ink-1)' : 'var(--ink-2)', whiteSpace: 'nowrap' }}>
      <span style={{ width: 9, height: 9, borderRadius: 2, background: c, flexShrink: 0, boxShadow: strong ? `0 0 0 3px ${k === 'CRITICAL' ? 'var(--danger-bg)' : 'transparent'}` : 'none' }} />
      {label}
    </span>
  );
}
