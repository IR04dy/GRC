import { useEffect, useMemo, useState } from 'react';
import { Activity, AlertTriangle, BookOpen, Shield, FileWarning } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { PillTabs } from '../../components/ui/PillTabs';
import { ApiError } from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  getPostureHeatmap, getDocumentCoverage, coverageBandTone,
  type PostureHeatmap, type PostureRow, type DocumentCoverage,
} from '../../lib/regulatoryAuthority';

/**
 * Compliance Posture Hub — Framework Intersection F6.
 *
 * The cross-cutting page the CRO / CISO / DPO opens before a
 * regulator meeting. Pivots the same data through different lenses:
 *   - Family filter (NCA Cyber / SAMA / Privacy / BCM / …) acts as
 *     the audience selector — "NCA audit prep" / "PDPL board
 *     update" / "SAMA inspection" are different filter sets on the
 *     same view, not different pages.
 *   - Heatmap rows = authority documents, columns = domains, cell =
 *     count of controls from that domain mapped to that document.
 *   - Click any row to see its citation-level coverage on the right.
 *
 * Per the plan, there is intentionally NO dashboard-per-regulator —
 * filter this page instead. Avoids the classic IRM drift problem.
 */
export function PosturePage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [data, setData] = useState<PostureHeatmap | null>(null);
  const [familyFilter, setFamilyFilter] = useState<string>('');
  const [loading, setLoading] = useState(true);

  const [selected, setSelected] = useState<PostureRow | null>(null);
  const [detail, setDetail] = useState<DocumentCoverage | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  async function reload() {
    setLoading(true);
    try {
      setData(await getPostureHeatmap(familyFilter || undefined));
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل تحميل وضع الامتثال' : 'Failed to load posture'));
      setData(null);
    } finally { setLoading(false); }
  }
  useEffect(() => { void reload(); }, [familyFilter]); // eslint-disable-line react-hooks/exhaustive-deps

  async function openDetail(row: PostureRow) {
    setSelected(row);
    setDetailLoading(true);
    try {
      setDetail(await getDocumentCoverage(row.authorityDocumentId));
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed to load coverage');
      setDetail(null);
    } finally { setDetailLoading(false); }
  }

  const totals = useMemo(() => {
    const rows = data?.rows ?? [];
    let cits = 0, mapped = 0;
    for (const r of rows) { cits += r.totalCitations; mapped += r.mappedCitations; }
    const avg = rows.length === 0 ? 0
      : Math.round((rows.reduce((s, r) => s + r.coveragePct, 0) / rows.length) * 10) / 10;
    return { docCount: rows.length, cits, mapped, avg };
  }, [data]);

  const domains = data?.domains ?? [];
  const families = data ? Object.keys(data.familyTotals).sort() : [];

  return (
    <Shell
      title={isAr ? 'لوحة وضع الامتثال' : 'Compliance Posture Hub'}
      sub={isAr ? 'عرض موحّد لكل المرجعيات التنظيمية مقابل ضوابط المنشأة'
                : 'Unified view of every authority document vs. the org control library'}>
      <PageHeader
        eyebrow={isAr ? 'الإطار التنظيمي' : 'Regulatory framework'}
        title={isAr ? 'وضع الامتثال' : 'Compliance posture'} />

      {/* KPIs */}
      <div className="kpi-strip-4">
        <Kpi Icon={BookOpen}   label={isAr ? 'المرجعيات' : 'Authority documents'} value={totals.docCount} />
        <Kpi Icon={Shield}     label={isAr ? 'البنود المغطّاة' : 'Citations covered'}
             value={`${totals.mapped} / ${totals.cits}`} />
        <Kpi Icon={Activity}   label={isAr ? 'متوسط التغطية' : 'Avg coverage'}
             value={`${totals.avg}%`} tone={coverageBandTone(totals.avg)} />
        <Kpi Icon={FileWarning} label={isAr ? 'مجالات الضوابط' : 'Control domains'} value={domains.length} />
      </div>

      {/* Family filter chips — the "audience selector" */}
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 14, alignItems: 'center' }}>
        <span className="text-2xs text-muted" style={{ marginInlineEnd: 4 }}>
          {isAr ? 'تصفية حسب العائلة:' : 'Filter by family:'}
        </span>
        <PillTabs
          aria-label={isAr ? 'تصفية حسب العائلة' : 'Filter by family'}
          active={familyFilter}
          onChange={setFamilyFilter}
          options={[
            { id: '', label: isAr ? 'الكل' : 'All' },
            ...families.map(f => ({ id: f, label: f, count: data?.familyTotals[f] ?? 0 })),
          ]}
        />
      </div>

      <div className="dash-grid" style={{ gridTemplateColumns: 'minmax(520px, 1fr) minmax(0, 1fr)' }}>
        {/* Heatmap */}
        <Card title={isAr ? 'الخارطة الحرارية' : 'Coverage heatmap'}
          action={<Badge tone="primary">{data?.rows.length ?? 0}</Badge>}>
          {loading
            ? <div style={{ padding: 24, textAlign: 'center', color: 'var(--ink-3)' }}>
                {isAr ? 'جاري التحميل…' : 'Loading…'}
              </div>
            : !data || data.rows.length === 0
              ? <InlineEmpty>
                  {isAr ? 'لا توجد مرجعيات مطابقة' : 'No authority documents match the filter'}
                </InlineEmpty>
              : <div style={{ overflowX: 'auto' }}>
                  <table className="grc-table" style={{ minWidth: 480 }}>
                    <thead>
                      <tr>
                        <th>{isAr ? 'المرجعية' : 'Document'}</th>
                        <th>{isAr ? 'التغطية' : 'Coverage'}</th>
                        {domains.map(d => (
                          <th key={d} style={{ textAlign: 'center', minWidth: 56 }}>{shortDomain(d)}</th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {data.rows.map(r => (
                        <tr key={r.authorityDocumentId}
                            onClick={() => openDetail(r)}
                            style={{
                              cursor: 'pointer',
                              background: selected?.authorityDocumentId === r.authorityDocumentId
                                ? 'var(--surface-2)' : undefined,
                            }}>
                          <td>
                            <div style={{ fontWeight: 600, fontSize: 12 }}>
                              {isAr && r.titleAr ? r.titleAr : r.titleEn}
                            </div>
                            <div className="mono text-2xs text-muted">{r.code} · {r.family}</div>
                          </td>
                          <td>
                            <Badge tone={coverageBandTone(r.coveragePct)}>{r.coveragePct}%</Badge>
                            <div className="mono text-2xs text-muted" style={{ marginTop: 2 }}>
                              {r.mappedCitations} / {r.totalCitations}
                            </div>
                          </td>
                          {domains.map(d => {
                            const n = r.controlsByDomain[d] ?? 0;
                            return (
                              <td key={d} style={{ textAlign: 'center' }}>
                                {n === 0
                                  ? <span className="text-2xs text-muted">—</span>
                                  : <HeatCell n={n} />}
                              </td>
                            );
                          })}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>}
        </Card>

        {/* Drill-down */}
        <Card title={selected
            ? `${selected.code} — ${isAr && selected.titleAr ? selected.titleAr : selected.titleEn}`
            : (isAr ? 'تفاصيل التغطية' : 'Coverage detail')}
          action={selected
            ? <Badge tone={coverageBandTone(selected.coveragePct)}>{selected.coveragePct}%</Badge>
            : null}>
          {!selected
            ? <InlineEmpty>
                <div style={{ fontWeight: 600, marginBottom: 4 }}>
                  {isAr ? 'اختر مرجعية لاستعراض تفاصيلها'
                        : 'Pick an authority document to drill into'}
                </div>
                <div className="text-2xs text-muted">
                  {isAr ? 'سترى الفجوات والتغطية لكل بند' : "You'll see per-citation coverage + gap list"}
                </div>
              </InlineEmpty>
            : detailLoading
              ? <div style={{ padding: 24, textAlign: 'center', color: 'var(--ink-3)' }}>
                  {isAr ? 'جاري التحميل…' : 'Loading…'}
                </div>
              : !detail
                ? <InlineEmpty>{isAr ? 'فشل التحميل' : 'Could not load coverage'}</InlineEmpty>
                : <DetailPanel d={detail} isAr={isAr} />}
        </Card>
      </div>

      {/* Audience helper */}
      <Card title={isAr ? 'كيف يقرأ هذا' : 'How to read this'}>
        <ul style={{ fontSize: 12, color: 'var(--ink-2)', lineHeight: 1.6, paddingInlineStart: 18, margin: 0 }}>
          <li>{isAr
              ? 'صفّ الجدول = مرجعية تنظيمية. عمود الـHeatmap = مجال داخل المنشأة (سيبراني، استمرارية، خصوصية…).'
              : 'Row = one authority document. Heatmap column = an internal control domain (Cyber, BCM, Privacy, …).'}</li>
          <li>{isAr
              ? 'الرقم في الخانة = عدد الضوابط من هذا المجال المربوطة ببنود هذه المرجعية.'
              : 'Cell number = controls from that domain mapped to citations of that document.'}</li>
          <li>{isAr
              ? 'لاجتماع NCA: فلتر "NCA Cyber". لاجتماع المجلس: فلتر "International" + "Risk".'
              : 'For an NCA inspection: filter "NCA Cyber". For a board update: filter "International" + "Risk".'}</li>
        </ul>
      </Card>
    </Shell>
  );
}

function DetailPanel({ d, isAr }: { d: DocumentCoverage; isAr: boolean }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 8 }}>
        <Stat label={isAr ? 'بنود' : 'Citations'}     value={d.totalCitations} />
        <Stat label={isAr ? 'مغطّاة' : 'Covered'}      value={d.mappedCitations} tone="success" />
        <Stat label={isAr ? 'فجوات' : 'Gaps'}          value={d.gapCitations}    tone={d.gapCitations > 0 ? 'warning' : 'success'} />
        <Stat label={isAr ? 'تغطية كاملة' : 'FULL'}    value={d.fullCoverage}    tone="info" />
      </div>

      <div>
        <h4 style={{ margin: '0 0 6px', fontSize: 13 }}>
          {isAr ? 'الضوابط حسب المجال' : 'Controls by domain'}
        </h4>
        {Object.keys(d.controlsByDomain).length === 0
          ? <span className="text-xs text-muted">{isAr ? 'لا توجد ضوابط مربوطة' : 'No controls mapped'}</span>
          : <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
              {Object.entries(d.controlsByDomain).map(([domain, count]) => (
                <Badge key={domain} tone="info">{domain} · {count}</Badge>
              ))}
            </div>}
      </div>

      <div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 6 }}>
          <h4 style={{ margin: 0, fontSize: 13 }}>{isAr ? 'فجوات' : 'Gap citations'}</h4>
          <Badge tone={d.gapCitations > 0 ? 'warning' : 'success'}>{d.gapCitations}</Badge>
        </div>
        {d.gaps.length === 0
          ? <InlineEmpty>
              {isAr ? 'لا توجد فجوات' : 'No unmapped citations'}
            </InlineEmpty>
          : <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              {d.gaps.map(g => (
                <div key={g.id} style={{
                  display: 'grid', gridTemplateColumns: 'auto 1fr auto',
                  gap: 8, padding: '4px 8px', alignItems: 'center',
                  borderInlineStart: '2px solid var(--warning, #f6c149)',
                  background: 'var(--surface-2)', borderRadius: 'var(--r-sm)',
                }}>
                  <span className="mono text-2xs">{g.citationCode}</span>
                  <span style={{ fontSize: 12 }}>{isAr && g.titleAr ? g.titleAr : g.titleEn}</span>
                  <Badge tone="warning">{isAr ? 'فجوة' : 'gap'}</Badge>
                </div>
              ))}
              {d.gaps.length === 25 && d.gapCitations > 25 && (
                <div className="text-2xs text-muted" style={{ marginTop: 4 }}>
                  {isAr
                    ? `يُعرض أول 25 فجوة من ${d.gapCitations} — افتح مكتبة المرجعيات لاستعراض الكل.`
                    : `Showing first 25 gaps of ${d.gapCitations} — open the Regulatory library to browse all.`}
                </div>
              )}
            </div>}
      </div>

      <div>
        <a href="/regulatory/library" style={{
          display: 'inline-flex', alignItems: 'center', gap: 6,
          padding: '6px 12px', fontSize: 12,
          border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
          background: 'var(--surface-1)', color: 'var(--ink-1)',
          textDecoration: 'none',
        }}>
          <AlertTriangle size={14} />
          {isAr ? 'فتح وثيقة المرجعية' : 'Open authority document'}
        </a>
      </div>
    </div>
  );
}

function Stat({ label, value, tone }: { label: string; value: number; tone?: 'success' | 'warning' | 'danger' | 'info' }) {
  const palette: Record<string, string> = {
    success: 'var(--success, #2e9c5e)',
    warning: 'var(--warning, #c47e00)',
    danger:  'var(--danger,  #c8362c)',
    info:    'var(--primary)',
  };
  const color = tone ? palette[tone] : 'var(--ink-1)';
  return (
    <div style={{ padding: 8, background: 'var(--surface-2)', borderRadius: 'var(--r-sm)' }}>
      <div className="text-2xs text-muted">{label}</div>
      <div style={{ fontSize: 18, fontWeight: 700, color }}>{value}</div>
    </div>
  );
}

function HeatCell({ n }: { n: number }) {
  // Darker green for higher control counts. Pure CSS so RTL flips
  // safely.
  const intensity = Math.min(n, 6);
  const bg = `rgba(46, 156, 94, ${0.2 + intensity * 0.12})`;
  return (
    <span style={{
      display: 'inline-block', minWidth: 28, padding: '2px 6px',
      borderRadius: 4, background: bg, fontSize: 11, fontWeight: 600,
    }}>{n}</span>
  );
}

function shortDomain(d: string): string {
  // Compact labels so the heatmap header doesn't blow up the layout.
  switch (d) {
    case 'CYBER':      return 'Cyber';
    case 'BCM':        return 'BCM';
    case 'RISK':       return 'Risk';
    case 'PRIVACY':    return 'Priv';
    case 'TPRM':       return 'TPRM';
    case 'COMPLIANCE': return 'Comp';
    case 'GENERAL':    return 'Gen';
    default:           return d;
  }
}
