import { useEffect, useMemo, useState } from 'react';
import {
  FileBarChart, Printer, FileSpreadsheet, FileJson, RefreshCw, Search,
  Gauge, Activity, ClipboardCheck, Landmark, Layers, Phone, Users,
  AlertTriangle, BookOpen, GitBranch, ScrollText, LayoutGrid, FileText,
  ShieldCheck, Building2, CalendarClock, Info, type LucideIcon,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Kpi } from '../../components/ui/Kpi';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { RiskMatrix5x5, type RiskMatrixCell } from '../../components/viz/RiskMatrix5x5';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import { listReportCatalog, generateReport, type BcmReportDef } from '../../lib/bcmReports';
import { ragTonePercent, type Rag } from '../../lib/ragColors';
import { downloadCsv, downloadJson, stampFilename, type ExportRow } from '../../lib/clientExport';
import { printPacket, type PacketSection, type PacketDocument } from '../../lib/packetPrint';

/**
 * BCM Reporting Studio — BCM-7 catalogue + board/regulator-grade viewer.
 *
 * <p>Re-architected from a generic JSON dumper into a reporting product that
 * matches the global GRC leaders (ServiceNow IRM, Archer, MetricStream,
 * OneTrust, AuditBoard):
 * <ul>
 *   <li><b>Categorised, searchable catalogue</b> — the 17 reports grouped
 *       into Posture, Risk, Remediation, Governance, Communications and
 *       Regulatory Filings, each with a one-line SME description.</li>
 *   <li><b>Report document framing</b> — every report opens with a header
 *       band (framework reference, classification, generated date) and an
 *       executive RAG verdict where a headline metric exists.</li>
 *   <li><b>Domain-aware rendering</b> — the residual-risk report draws the
 *       signature 5×5 matrix; status / severity / threat breakdowns draw as
 *       RAG-coloured distribution bars; KPIs carry semantic tone (overdue /
 *       critical = red, passed / approved = green); regulator attestations
 *       render as proper callout blocks.</li>
 *   <li><b>Real exports</b> — Print / PDF (regulator-ready, branded),
 *       Excel / CSV (the dataset), and raw JSON for integrators.</li>
 * </ul>
 *
 * <p>Each generation hits the backend which writes an audit row, so the
 * report timeline is reconstructable from /api/v1/audit/logs?actionType=REPORT.
 * Results are cached per code in-session; "Regenerate" forces a fresh pull
 * (and a fresh audit row).
 */

// ─── Domain metadata (SME registry) ───────────────────────────────

type Category = 'POSTURE' | 'RISK' | 'REMEDIATION' | 'GOVERNANCE' | 'COMMS' | 'REGULATORY' | 'OTHER';
type Classification = 'INTERNAL' | 'BOARD' | 'REGULATOR';
type Tone = 'primary' | 'danger' | 'success' | 'warning' | 'info' | 'accent' | 'neutral';

const CATEGORY_ORDER: Category[] = ['POSTURE', 'RISK', 'REMEDIATION', 'GOVERNANCE', 'COMMS', 'REGULATORY', 'OTHER'];

const CATEGORY_TITLE: Record<Category, { en: string; ar: string }> = {
  POSTURE:     { en: 'Posture & Readiness',   ar: 'الجاهزية والوضع العام' },
  RISK:        { en: 'Risk & Resilience',     ar: 'المخاطر والصمود' },
  REMEDIATION: { en: 'Remediation & Lessons', ar: 'المعالجة والدروس' },
  GOVERNANCE:  { en: 'Governance & Policy',   ar: 'الحوكمة والسياسات' },
  COMMS:       { en: 'Communications',        ar: 'الاتصالات' },
  REGULATORY:  { en: 'Regulatory Filings',    ar: 'التقارير التنظيمية' },
  OTHER:       { en: 'Other',                 ar: 'أخرى' },
};

interface ReportMeta {
  category: Category;
  Icon: LucideIcon;
  descEn: string;
  descAr: string;
  framework: string;
  classification: Classification;
}

const REPORT_META: Record<string, ReportMeta> = {
  BCM_READINESS: {
    category: 'POSTURE', Icon: Gauge, framework: 'ISO 22301', classification: 'INTERNAL',
    descEn: 'Top-level BCMS posture: cycles, average score, and open gaps / CAPAs.',
    descAr: 'الوضع العام لنظام إدارة الاستمرارية: الدورات والمتوسط والفجوات والإجراءات المفتوحة.',
  },
  BIA_COMPLETION: {
    category: 'POSTURE', Icon: ClipboardCheck, framework: 'ISO 22301 §8.2', classification: 'INTERNAL',
    descEn: 'Average Business Impact Analysis completion across active cycles.',
    descAr: 'متوسط إنجاز تحليل أثر الأعمال عبر الدورات النشطة.',
  },
  CYCLE_PROGRESS: {
    category: 'POSTURE', Icon: GitBranch, framework: 'ISO 22301 §9.1', classification: 'INTERNAL',
    descEn: 'Per-cycle BIA / BCP / DRP / evidence completion and overall score.',
    descAr: 'إنجاز كل دورة (تحليل الأثر/الخطة/التعافي/الأدلة) والدرجة العامة.',
  },
  EXECUTIVE_SUMMARY: {
    category: 'POSTURE', Icon: FileText, framework: 'Board / ISO 22301', classification: 'BOARD',
    descEn: 'One-screen board snapshot: readiness, call tree, gaps, CAPAs, policies.',
    descAr: 'لقطة تنفيذية واحدة: الجاهزية وشجرة الاتصال والفجوات والإجراءات والسياسات.',
  },
  CRITICAL_SERVICES: {
    category: 'RISK', Icon: Activity, framework: 'SAMA BCM', classification: 'INTERNAL',
    descEn: 'Count of continuity risks rated HIGH or CRITICAL by impact.',
    descAr: 'عدد مخاطر الاستمرارية ذات الأثر المرتفع أو الحرج.',
  },
  BCM_RISK_REGISTER: {
    category: 'RISK', Icon: AlertTriangle, framework: 'ISO 31000', classification: 'INTERNAL',
    descEn: 'BCM risk register grouped by status and threat category.',
    descAr: 'سجل مخاطر الاستمرارية حسب الحالة وفئة التهديد.',
  },
  RESIDUAL_RISK_HEATMAP: {
    category: 'RISK', Icon: LayoutGrid, framework: 'ISO 31000', classification: 'INTERNAL',
    descEn: 'Residual risk distribution across the 5×5 likelihood × impact matrix.',
    descAr: 'توزيع المخاطر المتبقية على مصفوفة الاحتمال × الأثر ٥×٥.',
  },
  GAP_ANALYSIS: {
    category: 'RISK', Icon: GitBranch, framework: 'ISO 22301 §9', classification: 'INTERNAL',
    descEn: 'Continuity gaps by severity and remediation status.',
    descAr: 'فجوات الاستمرارية حسب الخطورة وحالة المعالجة.',
  },
  CAPA_TRACKER: {
    category: 'REMEDIATION', Icon: ClipboardCheck, framework: 'ISO 22301 §10.1', classification: 'INTERNAL',
    descEn: 'Corrective and preventive actions: progress and overdue count.',
    descAr: 'الإجراءات التصحيحية والوقائية: التقدم والمتأخر منها.',
  },
  LESSONS_LEARNED: {
    category: 'REMEDIATION', Icon: BookOpen, framework: 'ISO 22301 §10', classification: 'INTERNAL',
    descEn: 'Post-incident and post-exercise lessons by source and status.',
    descAr: 'الدروس المستفادة بعد الحوادث والتمارين حسب المصدر والحالة.',
  },
  GOVERNANCE_FRAMEWORK: {
    category: 'GOVERNANCE', Icon: Landmark, framework: 'ISO 22301 §5', classification: 'INTERNAL',
    descEn: 'BCM policies and governance records by approval status.',
    descAr: 'سياسات وحوكمة الاستمرارية حسب حالة الاعتماد.',
  },
  STRATEGIES_INVENTORY: {
    category: 'GOVERNANCE', Icon: Layers, framework: 'ISO 22301 §8.3', classification: 'INTERNAL',
    descEn: 'Continuity strategies inventory by approval status.',
    descAr: 'فهرس استراتيجيات الاستمرارية حسب حالة الاعتماد.',
  },
  POLICY_REVIEW_QUEUE: {
    category: 'GOVERNANCE', Icon: ScrollText, framework: 'ISO 22301 §5.2', classification: 'INTERNAL',
    descEn: 'Governance records past their next-review date.',
    descAr: 'سجلات الحوكمة المتجاوزة لتاريخ المراجعة التالي.',
  },
  CALL_TREE_HEALTH: {
    category: 'COMMS', Icon: Phone, framework: 'ISO 22301 §8.4.3', classification: 'INTERNAL',
    descEn: 'Communication call-tree test outcomes: passed, failed, not reached.',
    descAr: 'نتائج اختبار شجرة الاتصال: ناجح، فاشل، لم يُجَب.',
  },
  COMMS_CONTACT_DIRECTORY: {
    category: 'COMMS', Icon: Users, framework: 'ISO 22301 §8.4.3', classification: 'INTERNAL',
    descEn: 'Emergency contact directory by escalation level and status.',
    descAr: 'دليل جهات الاتصال الطارئة حسب مستوى التصعيد والحالة.',
  },
  NCA_CYBER_CONTINUITY: {
    category: 'REGULATORY', Icon: ShieldCheck, framework: 'NCA ECC §3-5', classification: 'REGULATOR',
    descEn: 'Cyber-disruption continuity capability per NCA ECC §3-5.',
    descAr: 'قدرة الاستمرارية عند التعطل السيبراني وفق ضوابط الهيئة §٣-٥.',
  },
  CMA_ANNUAL_DISCLOSURE: {
    category: 'REGULATORY', Icon: Building2, framework: 'CMA Annual Disclosure', classification: 'REGULATOR',
    descEn: 'Annual BCM disclosure aggregation for the CMA listing report.',
    descAr: 'تجميع الإفصاح السنوي للاستمرارية لتقرير هيئة السوق المالية.',
  },
};

const DEFAULT_META: ReportMeta = {
  category: 'OTHER', Icon: FileBarChart, framework: 'BCM', classification: 'INTERNAL',
  descEn: 'BCM report.', descAr: 'تقرير استمرارية الأعمال.',
};

function metaFor(code: string): ReportMeta { return REPORT_META[code] ?? DEFAULT_META; }

// ─── Page ─────────────────────────────────────────────────────────

export function BcmReportsPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [catalog, setCatalog] = useState<BcmReportDef[]>([]);
  const [selected, setSelected] = useState<BcmReportDef | null>(null);
  const [cache, setCache] = useState<Record<string, Record<string, unknown>>>({});
  const [loading, setLoading] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [query, setQuery] = useState('');

  const payload = selected ? cache[selected.code] ?? null : null;

  useEffect(() => {
    void (async () => {
      setLoading(true);
      try {
        const cat = await listReportCatalog();
        const safe = Array.isArray(cat) ? cat : [];
        setCatalog(safe);
        if (safe.length) await runReport(safe[0]);
      } catch (e) {
        toast.danger(e instanceof ApiError ? e.message
          : (isAr ? 'تعذر تحميل التقارير' : 'Failed to load report catalog'));
      } finally {
        setLoading(false);
      }
    })();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  async function runReport(def: BcmReportDef, force = false) {
    setSelected(def);
    if (cache[def.code] && !force) return; // served from session cache
    setGenerating(true);
    try {
      const body = await generateReport(def.code);
      setCache(prev => ({ ...prev, [def.code]: body }));
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل توليد التقرير' : 'Report generation failed'));
    } finally {
      setGenerating(false);
    }
  }

  // Catalogue grouped by category, filtered by the search box.
  const grouped = useMemo(() => {
    const q = query.trim().toLowerCase();
    const match = (d: BcmReportDef) =>
      !q || d.titleEn.toLowerCase().includes(q) || d.titleAr.includes(q)
      || metaFor(d.code).descEn.toLowerCase().includes(q);
    const out = new Map<Category, BcmReportDef[]>();
    for (const cat of CATEGORY_ORDER) out.set(cat, []);
    for (const d of catalog) {
      if (!match(d)) continue;
      out.get(metaFor(d.code).category)!.push(d);
    }
    return CATEGORY_ORDER
      .map(cat => ({ cat, items: out.get(cat)! }))
      .filter(g => g.items.length > 0);
  }, [catalog, query]);

  // ── Exports ──
  function exportPrint() {
    if (!payload || !selected) return;
    printPacket(buildPacket(selected, payload, isAr), isAr ? 'ar' : 'en');
  }
  function exportCsv() {
    if (!payload || !selected) return;
    const rows = buildCsvRows(payload, isAr);
    downloadCsv(rows, stampFilename(`bcm-${selected.code.toLowerCase()}`, 'csv'));
    toast.success(isAr ? 'تم تصدير CSV' : 'CSV exported');
  }
  function exportJson() {
    if (!payload || !selected) return;
    downloadJson(payload, stampFilename(`bcm-${selected.code.toLowerCase()}`, 'json'));
  }

  const meta = selected ? metaFor(selected.code) : null;

  return (
    <Shell>
      <PageHeader
        eyebrow={isAr ? 'إدارة استمرارية الأعمال' : 'Business Continuity'}
        title={isAr ? 'استوديو تقارير الاستمرارية' : 'BCM reporting studio'}
        sub={isAr
          ? 'تقارير جاهزة للمجلس والجهات التنظيمية تغطي الجاهزية والمخاطر والمعالجة والحوكمة والاتصالات.'
          : 'Board- and regulator-ready reports across readiness, risk, remediation, governance and communications.'}
      />

      <div style={{ display: 'grid', gridTemplateColumns: '300px 1fr', gap: 16, alignItems: 'start' }}
        className="bcm-reports-layout">
        {/* ── Catalogue (left) ── */}
        <Card title={isAr ? 'كتالوج التقارير' : 'Report catalog'} Icon={FileBarChart}>
          <div style={{ position: 'relative', marginBottom: 10 }}>
            <Search size={14} style={{
              position: 'absolute', insetInlineStart: 9, top: '50%', transform: 'translateY(-50%)',
              color: 'var(--ink-3)', pointerEvents: 'none',
            }} />
            <input
              value={query}
              onChange={e => setQuery(e.target.value)}
              placeholder={isAr ? 'بحث في التقارير…' : 'Search reports…'}
              style={{
                width: '100%', padding: '7px 10px 7px 30px', fontSize: 12,
                background: 'var(--surface)', color: 'var(--ink-1)',
                border: '1px solid var(--border)', borderRadius: 6,
              }}
            />
          </div>

          {loading && <div className="text-muted" style={{ fontSize: 11, marginBottom: 6 }}>
            {isAr ? 'جارٍ التحميل…' : 'Loading…'}
          </div>}

          {!loading && grouped.length === 0 ? (
            <InlineEmpty text={isAr ? 'لا توجد تقارير مطابقة' : 'No matching reports'} />
          ) : (
            <div style={{ display: 'grid', gap: 12 }}>
              {grouped.map(({ cat, items }) => (
                <div key={cat}>
                  <div style={{
                    fontSize: 10, fontWeight: 700, letterSpacing: '.08em', textTransform: 'uppercase',
                    color: 'var(--ink-3)', padding: '2px 4px', marginBottom: 2,
                  }}>
                    {isAr ? CATEGORY_TITLE[cat].ar : CATEGORY_TITLE[cat].en}
                  </div>
                  <ul style={{ listStyle: 'none', padding: 0, margin: 0, display: 'grid', gap: 2 }}>
                    {items.map(def => {
                      const m = metaFor(def.code);
                      const active = selected?.code === def.code;
                      return (
                        <li key={def.code}>
                          <button
                            onClick={() => void runReport(def)}
                            title={isAr ? m.descAr : m.descEn}
                            style={{
                              width: '100%', textAlign: 'start', padding: '8px 10px', borderRadius: 6,
                              background: active ? 'var(--primary-50)' : 'transparent',
                              border: '1px solid', borderColor: active ? 'var(--primary-200, var(--primary))' : 'transparent',
                              cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 9, fontSize: 13,
                              color: active ? 'var(--primary-700, var(--primary))' : 'var(--ink-1)',
                              fontWeight: active ? 600 : 400,
                            }}>
                            <m.Icon size={15} style={{ flexShrink: 0, color: active ? 'var(--primary)' : 'var(--ink-3)' }} />
                            <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                              {isAr ? def.titleAr : def.titleEn}
                            </span>
                          </button>
                        </li>
                      );
                    })}
                  </ul>
                </div>
              ))}
            </div>
          )}
        </Card>

        {/* ── Report document (right) ── */}
        <Card padded>
          {!selected || !meta ? (
            <InlineEmpty text={isAr ? 'اختر تقريراً من الكتالوج' : 'Select a report from the catalog'} />
          ) : (
            <>
              {/* Export toolbar */}
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 6, flexWrap: 'wrap', marginBottom: 14 }}>
                <Button variant="ghost" size="sm" onClick={() => void runReport(selected, true)} disabled={generating}>
                  <RefreshCw size={14} style={generating ? { animation: 'wizard-chunk-spin 700ms linear infinite' } : undefined} /> {isAr ? 'إعادة التوليد' : 'Regenerate'}
                </Button>
                <Button variant="ghost" size="sm" onClick={exportCsv} disabled={!payload}>
                  <FileSpreadsheet size={14} /> {isAr ? 'إكسل' : 'Excel'}
                </Button>
                <Button variant="ghost" size="sm" onClick={exportJson} disabled={!payload}>
                  <FileJson size={14} /> JSON
                </Button>
                <Button variant="primary" size="sm" onClick={exportPrint} disabled={!payload}>
                  <Printer size={14} /> {isAr ? 'طباعة / PDF' : 'Print / PDF'}
                </Button>
              </div>

              <ReportDocHeader
                def={selected} meta={meta} isAr={isAr}
                generatedAt={payload?.generatedAt as string | undefined}
              />

              {generating && !payload ? (
                <ReportSkeleton />
              ) : payload ? (
                <RenderBody obj={payload} isAr={isAr} depth={0} />
              ) : (
                <InlineEmpty text={isAr ? 'اضغط إعادة التوليد' : 'Press Regenerate'} />
              )}
            </>
          )}
        </Card>
      </div>
    </Shell>
  );
}

// ─── Report document header ───────────────────────────────────────

function ReportDocHeader({ def, meta, isAr, generatedAt }:
  { def: BcmReportDef; meta: ReportMeta; isAr: boolean; generatedAt?: string }) {
  return (
    <div style={{ borderBottom: '1px solid var(--border)', paddingBottom: 14, marginBottom: 16 }}>
      <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: '.08em', textTransform: 'uppercase', color: 'var(--accent-600, var(--accent-500))' }}>
        {isAr ? CATEGORY_TITLE[meta.category].ar : CATEGORY_TITLE[meta.category].en}
      </div>
      <h2 style={{ fontSize: 20, fontWeight: 700, margin: '4px 0 6px' }}>
        {isAr ? def.titleAr : def.titleEn}
      </h2>
      <p className="text-muted" style={{ fontSize: 13, margin: 0, maxWidth: '64ch' }}>
        {isAr ? meta.descAr : meta.descEn}
      </p>
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center', marginTop: 10 }}>
        <Badge tone="neutral"><Layers size={12} style={{ marginInlineEnd: 4 }} />{meta.framework}</Badge>
        <ClassificationChip classification={meta.classification} isAr={isAr} />
        {generatedAt && (
          <span className="text-muted" style={{ fontSize: 12, display: 'inline-flex', alignItems: 'center', gap: 4 }}>
            <CalendarClock size={13} />
            {isAr ? 'تاريخ التوليد' : 'Generated'}: {formatValue(generatedAt, 'generatedAt')}
          </span>
        )}
      </div>
    </div>
  );
}

function ClassificationChip({ classification, isAr }: { classification: Classification; isAr: boolean }) {
  const map: Record<Classification, { tone: BadgeTone; en: string; ar: string }> = {
    REGULATOR: { tone: 'danger',  en: 'Regulator-facing', ar: 'موجّه للجهة التنظيمية' },
    BOARD:     { tone: 'accent',  en: 'Board',            ar: 'مجلس الإدارة' },
    INTERNAL:  { tone: 'neutral', en: 'Internal',         ar: 'داخلي' },
  };
  const c = map[classification];
  return <Badge tone={c.tone}>{isAr ? c.ar : c.en}</Badge>;
}

// ─── Recursive body renderer ──────────────────────────────────────

function RenderBody({ obj, isAr, depth }: { obj: Record<string, unknown>; isAr: boolean; depth: number }) {
  const { primitives, longTexts, heatmaps, breakdowns, nested, arrays } = classify(obj);

  // Executive verdict — only at the top level, only when a headline %/score exists.
  const headline = depth === 0
    ? primitives.find(([k]) => /percent|progress|score/i.test(k))
    : undefined;

  return (
    <div style={{ display: 'grid', gap: depth === 0 ? 18 : 12 }}>
      {headline && (
        <VerdictBanner
          label={humanLabel(headline[0])}
          value={formatValue(headline[1], headline[0])}
          tone={ragTonePercent(Number(headline[1]))}
          isAr={isAr}
        />
      )}

      {/* KPI strip */}
      {primitives.length > 0 && (
        depth === 0 ? (
          <div className="kpi-grid-sm" style={{ marginBottom: 0 }}>
            {primitives.map(([k, v]) => {
              const tone = kpiTone(k, v);
              return (
                <Kpi key={k} Icon={kpiIcon(k)} label={humanLabel(k)} value={formatValue(v, k)} tone={tone} />
              );
            })}
          </div>
        ) : (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(150px,1fr))', gap: 8 }}>
            {primitives.map(([k, v]) => <StatChip key={k} label={humanLabel(k)} value={formatValue(v, k)} tone={kpiTone(k, v)} />)}
          </div>
        )
      )}

      {/* Distribution breakdowns */}
      {breakdowns.length > 0 && (
        <div style={{ display: 'grid', gridTemplateColumns: depth === 0 ? 'repeat(auto-fit,minmax(300px,1fr))' : '1fr', gap: 12 }}>
          {breakdowns.map(([k, v]) => (
            <BreakdownCard key={k} title={humanLabel(k)} data={v as Record<string, number>} parentKey={k} isAr={isAr} />
          ))}
        </div>
      )}

      {/* Residual-risk 5×5 matrix */}
      {heatmaps.map(([k, v]) => (
        <div key={k}>
          <SectionTitle>{humanLabel(k)}</SectionTitle>
          <RiskMatrix5x5 cells={parseHeatmapCells(v as Record<string, unknown>)} ar={isAr} showVectors={false} />
        </div>
      ))}

      {/* Nested sub-reports (e.g. executive summary children) */}
      {nested.map(([k, v]) => (
        <div key={k} style={{
          border: '1px solid var(--border)', borderRadius: 8, padding: 14,
          background: 'var(--surface-2, var(--surface))',
        }}>
          <SectionTitle>{humanLabel(k)}</SectionTitle>
          <RenderBody obj={v as Record<string, unknown>} isAr={isAr} depth={depth + 1} />
        </div>
      ))}

      {/* Tabular datasets */}
      {arrays.map(([k, v]) => (
        <ReportTable key={k} title={humanLabel(k)} rows={v as Record<string, unknown>[]} isAr={isAr} />
      ))}

      {/* Attestations / disclosures / notes */}
      {longTexts.map(([k, v]) => (
        <AttestationBlock key={k} title={humanLabel(k)} text={String(v)} isAr={isAr} />
      ))}
    </div>
  );
}

// ─── Presentational pieces ────────────────────────────────────────

function SectionTitle({ children }: { children: React.ReactNode }) {
  return <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 8 }}>{children}</div>;
}

function VerdictBanner({ label, value, tone, isAr }:
  { label: string; value: string; tone: Rag; isAr: boolean }) {
  const verdict = tone === 'success' ? (isAr ? 'جيد' : 'Healthy')
    : tone === 'info' ? (isAr ? 'على المسار' : 'On track')
    : tone === 'warning' ? (isAr ? 'يحتاج إلى انتباه' : 'Needs attention')
    : (isAr ? 'في خطر' : 'At risk');
  const Icon = (tone === 'success' || tone === 'info') ? ShieldCheck : AlertTriangle;
  return (
    <div style={{
      display: 'flex', alignItems: 'center', gap: 10, padding: '10px 14px', borderRadius: 8,
      background: `var(--${tone}-bg)`, color: `var(--${tone}-fg)`,
      borderInlineStart: `4px solid var(--${tone})`,
    }}>
      <Icon size={18} />
      <div style={{ fontSize: 13 }}>
        <strong>{label}: {value}</strong>
        <span style={{ marginInlineStart: 8, opacity: 0.85 }}>· {verdict}</span>
      </div>
    </div>
  );
}

function StatChip({ label, value, tone }: { label: string; value: string; tone: Tone }) {
  return (
    <div style={{ border: '1px solid var(--border)', borderRadius: 6, padding: '8px 10px', display: 'grid', gap: 3 }}>
      <span className="text-muted" style={{ fontSize: 11 }}>{label}</span>
      <span style={{ fontSize: 16, fontWeight: 700, color: TONE_COLOR[tone] }}>{value}</span>
    </div>
  );
}

function BreakdownCard({ title, data, parentKey, isAr }:
  { title: string; data: Record<string, number>; parentKey: string; isAr: boolean }) {
  const entries = Object.entries(data).sort((a, b) => b[1] - a[1]);
  const total = entries.reduce((s, [, v]) => s + (Number(v) || 0), 0);
  const max = Math.max(1, ...entries.map(([, v]) => Number(v) || 0));
  const isLevel = /level/i.test(parentKey);
  return (
    <div style={{ border: '1px solid var(--border)', borderRadius: 8, padding: 14 }}>
      <SectionTitle>{title}</SectionTitle>
      {entries.length === 0 ? (
        <Badge tone="neutral">{isAr ? 'لا توجد بيانات' : 'No data'}</Badge>
      ) : (
        <div style={{ display: 'grid', gap: 8 }}>
          {entries.map(([label, count]) => {
            const n = Number(count) || 0;
            const pct = total ? Math.round((n / total) * 100) : 0;
            const w = Math.round((n / max) * 100);
            const shown = isLevel ? `${isAr ? 'المستوى' : 'Level'} ${label}` : humanLabel(label);
            return (
              <div key={label}>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, marginBottom: 3 }}>
                  <span style={{ color: 'var(--ink-2)' }}>{shown}</span>
                  <span className="mono" style={{ color: 'var(--ink-3)' }}>{n} · {pct}%</span>
                </div>
                <div style={{ height: 8, background: 'var(--surface-3)', borderRadius: 999, overflow: 'hidden' }}>
                  <div style={{ width: `${w}%`, height: '100%', background: breakdownColor(label), borderRadius: 999, transition: 'width var(--dur-normal) var(--ease-standard)' }} />
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

function ReportTable({ title, rows, isAr }: { title: string; rows: Record<string, unknown>[]; isAr: boolean }) {
  if (rows.length === 0) {
    return (
      <div>
        <SectionTitle>{title}</SectionTitle>
        <Badge tone="neutral">{isAr ? 'لا توجد صفوف' : 'No rows'}</Badge>
      </div>
    );
  }
  const cols = Object.keys(rows[0]);
  return (
    <div>
      <SectionTitle>{title}</SectionTitle>
      <div style={{ overflowX: 'auto' }}>
        <table className="data-table" style={{ width: '100%', fontSize: 12 }}>
          <thead>
            <tr>{cols.map(c => <th key={c} style={{ textAlign: 'start' }}>{humanLabel(c)}</th>)}</tr>
          </thead>
          <tbody>
            {rows.slice(0, 100).map((row, i) => (
              <tr key={i}>{cols.map(c => <td key={c}>{renderCell(c, row[c], isAr)}</td>)}</tr>
            ))}
          </tbody>
        </table>
        {rows.length > 100 && (
          <div className="text-muted" style={{ fontSize: 11, marginTop: 4 }}>
            {isAr ? `… و ${rows.length - 100} صف إضافي` : `… and ${rows.length - 100} more rows`}
          </div>
        )}
      </div>
    </div>
  );
}

/** Domain-aware table cell: status/severity → badge, %-cols → progress bar, overdue → red. */
function renderCell(col: string, value: unknown, isAr: boolean): React.ReactNode {
  const c = col.toLowerCase();
  if (value == null || value === '') return '—';

  if (/status|approval/.test(c) && typeof value === 'string') {
    return <Badge tone={statusBadgeTone(value)}>{humanLabel(value)}</Badge>;
  }
  if (/severity/.test(c) && typeof value === 'string') {
    return <Badge tone={statusBadgeTone(value)}>{humanLabel(value)}</Badge>;
  }
  if (/overdue/.test(c) && typeof value === 'number') {
    return value > 0
      ? <Badge tone="danger">{value} {isAr ? 'يوم' : 'd'}</Badge>
      : <span className="text-muted">0</span>;
  }
  if (isPercentCol(c) && typeof value === 'number') {
    return <ProgressCell value={value} />;
  }
  return formatValue(value, col);
}

function ProgressCell({ value }: { value: number }) {
  const pct = Math.max(0, Math.min(100, value));
  const tone = ragTonePercent(pct);
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8, minWidth: 110 }}>
      <div style={{ flex: 1, height: 6, background: 'var(--surface-3)', borderRadius: 999, overflow: 'hidden' }}>
        <div style={{ width: `${pct}%`, height: '100%', background: `var(--${tone})`, borderRadius: 999 }} />
      </div>
      <span className="mono" style={{ fontSize: 11, color: 'var(--ink-2)', minWidth: 34, textAlign: 'end' }}>
        {Number.isInteger(value) ? value : value.toFixed(1)}%
      </span>
    </div>
  );
}

function AttestationBlock({ title, text, isAr }: { title: string; text: string; isAr: boolean }) {
  return (
    <div style={{
      border: '1px solid var(--border)', borderInlineStart: '4px solid var(--accent-500)',
      borderRadius: 8, padding: 14, background: 'var(--accent-50, var(--surface-2))',
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 7, marginBottom: 6 }}>
        <Info size={15} style={{ color: 'var(--accent-600, var(--accent-500))' }} />
        <span style={{ fontSize: 13, fontWeight: 600 }}>
          {title || (isAr ? 'إقرار' : 'Attestation')}
        </span>
      </div>
      <p style={{ fontSize: 12.5, lineHeight: 1.7, color: 'var(--ink-2)', margin: 0 }}>{text}</p>
    </div>
  );
}

function ReportSkeleton() {
  return (
    <div style={{ display: 'grid', gap: 14 }}>
      <div className="kpi-grid-sm" style={{ marginBottom: 0 }}>
        {[0, 1, 2, 3].map(i => (
          <span key={i} className="skeleton" style={{ display: 'block', width: '100%', height: 78, borderRadius: 8 }} />
        ))}
      </div>
      <span className="skeleton" style={{ display: 'block', width: '100%', height: 180, borderRadius: 8 }} />
    </div>
  );
}

// ─── Domain helpers ───────────────────────────────────────────────

type Entry = [string, unknown];

function classify(obj: Record<string, unknown>) {
  const entries = Object.entries(obj).filter(([k]) => k !== 'reportCode' && k !== 'generatedAt');
  const primitives: Entry[] = [];
  const longTexts: Entry[] = [];
  const heatmaps: Entry[] = [];
  const breakdowns: Entry[] = [];
  const nested: Entry[] = [];
  const arrays: Entry[] = [];
  for (const [k, v] of entries) {
    if (v == null || typeof v !== 'object') {
      if (typeof v === 'string' && (v.length > 80 || /attest|disclos|note/i.test(k))) longTexts.push([k, v]);
      else primitives.push([k, v]);
    } else if (Array.isArray(v)) {
      arrays.push([k, v]);
    } else {
      const o = v as Record<string, unknown>;
      if (isHeatmap(k, o)) heatmaps.push([k, o]);
      else if (Object.values(o).length > 0 && Object.values(o).every(x => typeof x === 'number')) breakdowns.push([k, o]);
      else if (Object.values(o).length === 0) breakdowns.push([k, o]); // empty map → "No data"
      else nested.push([k, o]);
    }
  }
  return { primitives, longTexts, heatmaps, breakdowns, nested, arrays };
}

function isHeatmap(key: string, o: Record<string, unknown>): boolean {
  const keys = Object.keys(o);
  return (key === 'cells' || /heatmap/i.test(key)) && keys.length > 0 && keys.every(k => /^L\d+_I\d+$/.test(k));
}

function parseHeatmapCells(o: Record<string, unknown>): RiskMatrixCell[] {
  const cells: RiskMatrixCell[] = [];
  for (const [k, v] of Object.entries(o)) {
    const m = /^L(\d+)_I(\d+)$/.exec(k);
    if (m) cells.push({ likelihood: Number(m[1]), impact: Number(m[2]), count: Number(v) || 0 });
  }
  return cells;
}

/** camelCase / snake_case JSON key → human-readable Title Case label. */
function humanLabel(key: string): string {
  return key
    .replace(/_/g, ' ')
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/([A-Za-z])(\d)/g, '$1 $2')
    .replace(/\b\w/g, c => c.toUpperCase())
    .replace(/\bBia\b/g, 'BIA').replace(/\bBcp\b/g, 'BCP').replace(/\bDrp\b/g, 'DRP')
    .replace(/\bCapa(s)?\b/gi, m => m.toUpperCase()).replace(/\bSla\b/gi, 'SLA');
}

/** Format a raw JSON value into something readable for end-users. */
function formatValue(val: unknown, key?: string): string {
  if (val == null || val === '') return '—';
  if (typeof val === 'boolean') return val ? 'Yes' : 'No';
  if (typeof val === 'number') {
    if (key && /year/i.test(key)) return String(val);                       // 2026, not "2,026"
    if (key && /percent|progress/i.test(key)) return `${Number.isInteger(val) ? val : val.toFixed(1)}%`;
    if (key && /score/i.test(key)) return Number.isInteger(val) ? String(val) : val.toFixed(1);
    return Number.isInteger(val) ? val.toLocaleString() : val.toFixed(2);
  }
  if (typeof val === 'string') {
    if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(val)) {
      const d = new Date(val);
      if (!Number.isNaN(d.getTime())) return d.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
    }
    if (/^\d{4}-\d{2}-\d{2}$/.test(val)) {
      const d = new Date(val);
      if (!Number.isNaN(d.getTime())) return d.toLocaleDateString(undefined, { dateStyle: 'medium' });
    }
  }
  return String(val);
}

function isPercentCol(c: string): boolean {
  return /percent|progress|completion|score|^bia$|^bcp$|^drp$|^evidence$/.test(c);
}

const TONE_COLOR: Record<Tone, string> = {
  primary: 'var(--ink-1)', danger: 'var(--danger)', success: 'var(--success)',
  warning: 'var(--warning)', info: 'var(--info)', accent: 'var(--accent-600, var(--accent-500))', neutral: 'var(--ink-2)',
};

/** Semantic KPI tone from the field's meaning and value. */
function kpiTone(key: string, value: unknown): Tone {
  const k = key.toLowerCase();
  const n = Number(value);
  if (/percent|progress|score/.test(k) && !Number.isNaN(n)) return ragTonePercent(n);
  if (/overdue|fail|notreach|untested|breach/.test(k)) return n > 0 ? 'danger' : 'success';
  if (/critical|highimpact|opengap|opencapa|openmaterial|opencorrective|cyberrisk|^open/.test(k)) return n > 0 ? 'warning' : 'success';
  if (/passed|approved|active/.test(k)) return 'success';
  return 'primary';
}

function kpiIcon(key: string): LucideIcon {
  const k = key.toLowerCase();
  if (/overdue|fail|breach|critical|highimpact|risk/.test(k)) return AlertTriangle;
  if (/passed|approved|active|complete/.test(k)) return ShieldCheck;
  if (/percent|progress|score/.test(k)) return Gauge;
  if (/polic/.test(k)) return ScrollText;
  if (/contact|comms|call/.test(k)) return Phone;
  if (/cycle/.test(k)) return CalendarClock;
  if (/gap/.test(k)) return GitBranch;
  if (/capa|action/.test(k)) return ClipboardCheck;
  if (/lesson/.test(k)) return BookOpen;
  if (/strateg/.test(k)) return Layers;
  return Activity;
}

/** Normalise a status/severity label to letters only so NOT_REACHED, notReached
 *  and "Not Reached" all collapse to the same key. */
function normStatus(label: string): string {
  return label.toUpperCase().replace(/[^A-Z]/g, '');
}

const TONE_DANGER  = ['OPEN', 'FAILED', 'REJECTED', 'OVERDUE', 'NOTREACHED', 'BREACHED', 'ESCALATED'];
const TONE_WARNING = ['INPROGRESS', 'UNDERREVIEW', 'PENDING', 'DRAFT', 'PLANNED', 'ASSIGNED', 'PARTIAL'];
const TONE_SUCCESS = ['CLOSED', 'APPROVED', 'PASSED', 'ACTIVE', 'COMPLETED', 'DONE', 'RESOLVED', 'PUBLISHED'];

/** Status / severity label → Badge tone. */
function statusBadgeTone(label: string): BadgeTone {
  const s = normStatus(label);
  if (['CRITICAL', 'CRIT'].includes(s)) return 'risk-crit';
  if (['VERYHIGH', 'VHIGH', 'SEVERE'].includes(s)) return 'risk-vhigh';
  if (['HIGH', 'MAJOR'].includes(s)) return 'risk-high';
  if (['MEDIUM', 'MED', 'MODERATE'].includes(s)) return 'risk-med';
  if (['LOW', 'MINOR'].includes(s)) return 'risk-low';
  if (TONE_DANGER.includes(s)) return 'danger';
  if (TONE_WARNING.includes(s)) return 'warning';
  if (TONE_SUCCESS.includes(s)) return 'success';
  return 'neutral';
}

/** Status / severity label → CSS colour for distribution bars. */
function breakdownColor(label: string): string {
  const s = normStatus(label);
  if (['CRITICAL', 'CRIT'].includes(s)) return 'var(--risk-crit)';
  if (['VERYHIGH', 'VHIGH', 'SEVERE'].includes(s)) return 'var(--risk-vhigh)';
  if (['HIGH', 'MAJOR'].includes(s)) return 'var(--risk-high)';
  if (['MEDIUM', 'MED', 'MODERATE'].includes(s)) return 'var(--risk-med)';
  if (['LOW', 'MINOR'].includes(s)) return 'var(--risk-low)';
  if (TONE_DANGER.includes(s)) return 'var(--danger)';
  if (TONE_WARNING.includes(s)) return 'var(--warning)';
  if (TONE_SUCCESS.includes(s)) return 'var(--success)';
  return 'var(--accent-500)';
}

// ─── Export builders ──────────────────────────────────────────────

function buildCsvRows(payload: Record<string, unknown>, isAr: boolean): ExportRow[] {
  const { primitives, breakdowns, arrays } = classify(payload);
  // Prefer the largest tabular dataset — that's the real export.
  if (arrays.length > 0) {
    const biggest = arrays.reduce((a, b) => ((b[1] as unknown[]).length > (a[1] as unknown[]).length ? b : a));
    const rows = biggest[1] as Record<string, unknown>[];
    return rows.map(r => {
      const out: ExportRow = {};
      for (const [k, v] of Object.entries(r)) out[humanLabel(k)] = formatValue(v, k);
      return out;
    });
  }
  // Otherwise flatten metrics + breakdowns to a Metric / Value sheet.
  const mLabel = isAr ? 'المؤشر' : 'Metric';
  const vLabel = isAr ? 'القيمة' : 'Value';
  const out: ExportRow[] = [];
  for (const [k, v] of primitives) out.push({ [mLabel]: humanLabel(k), [vLabel]: formatValue(v, k) });
  for (const [k, obj] of breakdowns) {
    for (const [sk, sv] of Object.entries(obj as Record<string, number>)) {
      out.push({ [mLabel]: `${humanLabel(k)} / ${humanLabel(sk)}`, [vLabel]: sv });
    }
  }
  return out;
}

function buildPacket(def: BcmReportDef, payload: Record<string, unknown>, isAr: boolean): PacketDocument {
  const meta = metaFor(def.code);
  const { primitives, breakdowns, heatmaps, nested, arrays, longTexts } = classify(payload);
  const sections: PacketSection[] = [];

  if (primitives.length > 0) {
    sections.push({
      titleEn: 'Key Metrics', titleAr: 'المؤشرات الرئيسية',
      body: htmlKvTable(primitives.map(([k, v]) => [humanLabel(k), formatValue(v, k)])),
    });
  }
  for (const [k, v] of breakdowns) {
    sections.push({ titleEn: humanLabel(k), titleAr: humanLabel(k), body: htmlBreakdownTable(v as Record<string, number>, isAr) });
  }
  for (const [k, v] of heatmaps) {
    sections.push({ titleEn: humanLabel(k), titleAr: humanLabel(k), body: html5x5(v as Record<string, unknown>, isAr) });
  }
  for (const [k, v] of nested) {
    sections.push({ titleEn: humanLabel(k), titleAr: humanLabel(k), body: htmlNested(v as Record<string, unknown>, isAr) });
  }
  for (const [k, v] of arrays) {
    sections.push({ titleEn: humanLabel(k), titleAr: humanLabel(k), body: htmlRowsTable(v as Record<string, unknown>[]) });
  }
  for (const [k, v] of longTexts) {
    sections.push({ titleEn: humanLabel(k), titleAr: humanLabel(k), body: esc(String(v)) });
  }

  const cls: Record<Classification, { en: string; ar: string }> = {
    REGULATOR: { en: 'Regulator-facing', ar: 'موجّه للجهة التنظيمية' },
    BOARD:     { en: 'Board',            ar: 'مجلس الإدارة' },
    INTERNAL:  { en: 'Internal',         ar: 'داخلي' },
  };
  const cat = CATEGORY_TITLE[meta.category];
  return {
    titleEn: def.titleEn, titleAr: def.titleAr,
    referenceId: `BCM-${def.code}-${new Date().toISOString().slice(0, 10)}`,
    eyebrowEn: `${cat.en} · ${meta.framework}`,
    eyebrowAr: `${cat.ar} · ${meta.framework}`,
    classificationEn: cls[meta.classification].en,
    classificationAr: cls[meta.classification].ar,
    sections,
  };
}

// ── HTML builders for the print packet ──

function esc(s: string): string {
  return s.replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c] ?? c));
}

function htmlKvTable(pairs: [string, string][]): string {
  const rows = pairs.map(([k, v]) => `<tr><th style="width:50%">${esc(k)}</th><td>${esc(v)}</td></tr>`).join('');
  return `<table>${rows}</table>`;
}

function htmlBreakdownTable(data: Record<string, number>, isAr: boolean): string {
  const entries = Object.entries(data).sort((a, b) => b[1] - a[1]);
  if (entries.length === 0) return isAr ? 'لا توجد بيانات' : 'No data';
  const total = entries.reduce((s, [, v]) => s + (Number(v) || 0), 0);
  const head = `<tr><th>${isAr ? 'الفئة' : 'Category'}</th><th>${isAr ? 'العدد' : 'Count'}</th><th>%</th></tr>`;
  const rows = entries.map(([k, v]) => {
    const pct = total ? Math.round((Number(v) / total) * 100) : 0;
    return `<tr><td>${esc(humanLabel(k))}</td><td>${v}</td><td>${pct}%</td></tr>`;
  }).join('');
  return `<table>${head}${rows}</table>`;
}

function html5x5(o: Record<string, unknown>, isAr: boolean): string {
  const get = (L: number, I: number) => Number(o[`L${L}_I${I}`] || 0);
  const band = (score: number) => score >= 20 ? '#fde2e1' : score >= 12 ? '#fdebd3' : score >= 6 ? '#fef9c3' : '#dcfce7';
  let html = `<table style="text-align:center"><tr><th></th>`;
  for (let L = 1; L <= 5; L++) html += `<th>L${L}</th>`;
  html += `</tr>`;
  for (let I = 5; I >= 1; I--) {
    html += `<tr><th>I${I}</th>`;
    for (let L = 1; L <= 5; L++) {
      const c = get(L, I);
      html += `<td style="background:${band(L * I)};font-weight:700">${c || ''}</td>`;
    }
    html += `</tr>`;
  }
  html += `</table><div style="font-size:8pt;color:#666;margin-top:4pt">${isAr ? 'الأثر (عمودي) × الاحتمال (أفقي)' : 'Impact (rows) × Likelihood (columns)'}</div>`;
  return html;
}

function htmlNested(o: Record<string, unknown>, isAr: boolean): string {
  const parts: string[] = [];
  const kv: [string, string][] = [];
  for (const [k, v] of Object.entries(o)) {
    if (v != null && typeof v === 'object' && !Array.isArray(v)) {
      parts.push(`<div style="font-weight:700;margin:6pt 0 2pt">${esc(humanLabel(k))}</div>`);
      parts.push(htmlBreakdownTable(v as Record<string, number>, isAr));
    } else {
      kv.push([humanLabel(k), formatValue(v, k)]);
    }
  }
  return (kv.length ? htmlKvTable(kv) : '') + parts.join('');
}

function htmlRowsTable(rows: Record<string, unknown>[]): string {
  if (rows.length === 0) return 'No rows';
  const cols = Object.keys(rows[0]);
  const head = `<tr>${cols.map(c => `<th>${esc(humanLabel(c))}</th>`).join('')}</tr>`;
  const body = rows.slice(0, 200).map(r =>
    `<tr>${cols.map(c => `<td>${esc(formatValue(r[c], c))}</td>`).join('')}</tr>`).join('');
  return `<table>${head}${body}</table>`;
}
