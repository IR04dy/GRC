import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, CheckCheck, Send, Archive, Plus } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Tabs } from '../../components/ui/Tabs';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import {
  bowtieApi, ApiError,
  type BowTieFullResponse,
  type BowTieThreatDto,
  type BowTieConsequenceDto,
  type BowTiePreventiveControlDto,
  type BowTieMitigatingControlDto,
} from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';

/**
 * Bow-tie detail page (ISO 31010). Renders the canonical bow-tie picture
 * as a raw SVG (no external chart library) plus four tabbed registers
 * for threats, consequences, preventive controls, and mitigating
 * controls. Workflow actions live in the header.
 */

const STATUS_TONES: Record<string, BadgeTone> = {
  DRAFT: 'neutral', IN_REVIEW: 'warning', APPROVED: 'success',
  SUPERSEDED: 'info', ARCHIVED: 'danger',
};
const RATING_TONES: Record<string, BadgeTone> = {
  CRITICAL: 'danger', HIGH: 'warning', MEDIUM: 'accent', LOW: 'success',
};
const EFFECT_TONES: Record<string, BadgeTone> = {
  VERY_HIGH: 'success', HIGH: 'success', MEDIUM: 'accent', LOW: 'warning', VERY_LOW: 'danger',
};

type TabId = 'threats' | 'consequences' | 'preventive' | 'mitigating';

// Loaded row variants — children fetched from the backend always carry
// `id` even though the create/update DTOs leave it optional. The
// DataTable requires a definite id; this narrow type satisfies it.
type LoadedThreat       = BowTieThreatDto             & { id: number };
type LoadedConsequence  = BowTieConsequenceDto        & { id: number };
type LoadedPreventive   = BowTiePreventiveControlDto  & { id: number };
type LoadedMitigating   = BowTieMitigatingControlDto  & { id: number };

export function RiskBowTieDetailPage() {
  const { externalId = '' } = useParams<{ externalId: string }>();
  const { t, lang } = useI18n();
  const toast = useToast();
  const navigate = useNavigate();

  const [data, setData] = useState<BowTieFullResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [tab, setTab] = useState<TabId>('threats');
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    if (!externalId) return;
    setLoading(true);
    try {
      const full = await bowtieApi.get(externalId);
      setData(full);
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : 'Could not load bow-tie');
    } finally {
      setLoading(false);
    }
    // toast is stable from useToast() hook; eslint-disable rather than chase a ref.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [externalId]);

  useEffect(() => { load(); }, [load]);

  const header  = data?.header;
  const threats = useMemo(() => data?.threats ?? [], [data]);
  const conseq  = useMemo(() => data?.consequences ?? [], [data]);
  const prev    = useMemo(() => data?.preventiveControls ?? [], [data]);
  const mit     = useMemo(() => data?.mitigatingControls ?? [], [data]);

  // ── Workflow actions ────────────────────────────────────────────
  async function run(fn: () => Promise<unknown>, successMsg: string) {
    setBusy(true);
    try {
      await fn();
      toast.success(successMsg);
      await load();
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : 'Action failed');
    } finally {
      setBusy(false);
    }
  }

  // ── Quick-add prompts. The full inline editor is a follow-on slice;
  // these handlers cover the smoke-test path until that lands.
  async function quickAddThreat() {
    const title = window.prompt(lang === 'ar' ? 'عنوان التهديد:' : 'Threat title:');
    if (!title) return;
    await run(async () => {
      await bowtieApi.addThreat(externalId, {
        titleEn: title,
        threatOrder: threats.length + 1,
        residualLikelihood: 'POSSIBLE',
        source: 'External actor',
      });
    }, lang === 'ar' ? 'تمت إضافة التهديد' : 'Threat added');
  }

  async function quickAddConsequence() {
    const title = window.prompt(lang === 'ar' ? 'عنوان الأثر:' : 'Consequence title:');
    if (!title) return;
    await run(async () => {
      await bowtieApi.addConsequence(externalId, {
        titleEn: title,
        consequenceOrder: conseq.length + 1,
        consequenceType: 'OPERATIONAL',
        residualSeverity: 'MODERATE',
      });
    }, lang === 'ar' ? 'تمت إضافة الأثر' : 'Consequence added');
  }

  async function quickAddPreventive() {
    if (threats.length === 0) {
      toast.warning(lang === 'ar' ? 'أضف تهديداً أولاً' : 'Add a threat first');
      return;
    }
    const title = window.prompt(lang === 'ar' ? 'عنوان الضابط الوقائي:' : 'Preventive control title:');
    if (!title) return;
    const target = threats[0].externalId;
    if (!target) return;
    await run(async () => {
      await bowtieApi.addPreventiveControl(target, {
        titleEn: title,
        effectiveness: 'MEDIUM',
        controlType: 'PREVENTIVE',
      });
    }, lang === 'ar' ? 'تم إضافة الضابط' : 'Preventive control added');
  }

  async function quickAddMitigating() {
    if (conseq.length === 0) {
      toast.warning(lang === 'ar' ? 'أضف أثراً أولاً' : 'Add a consequence first');
      return;
    }
    const title = window.prompt(lang === 'ar' ? 'عنوان الضابط المخفف:' : 'Mitigating control title:');
    if (!title) return;
    const target = conseq[0].externalId;
    if (!target) return;
    await run(async () => {
      await bowtieApi.addMitigatingControl(target, {
        titleEn: title,
        effectiveness: 'MEDIUM',
        controlType: 'CORRECTIVE',
      });
    }, lang === 'ar' ? 'تم إضافة الضابط' : 'Mitigating control added');
  }

  // ── Workflow buttons gated on status ───────────────────────────
  const canSubmit  = header?.status === 'DRAFT';
  const canApprove = header?.status === 'IN_REVIEW';
  const canArchive = header?.status && header.status !== 'ARCHIVED';

  // ── Column defs ────────────────────────────────────────────────
  const threatCols: Column<LoadedThreat>[] = [
    { header: '#', cell: r => r.threatOrder ?? '—', mono: true, width: '60px' },
    { header: lang === 'ar' ? 'العنوان' : 'Title',
      cell: r => <div className="text-bold">{lang === 'ar' && r.titleAr ? r.titleAr : r.titleEn}</div> },
    { header: lang === 'ar' ? 'المصدر' : 'Source', width: '140px',
      cell: r => r.source ?? '—' },
    { header: lang === 'ar' ? 'الاحتمالية الأصلية' : 'Inherent L', width: '120px',
      cell: r => r.inherentLikelihood ?? '—' },
    { header: lang === 'ar' ? 'الاحتمالية المتبقية' : 'Residual L', width: '120px',
      cell: r => r.residualLikelihood ?? '—' },
  ];

  const consCols: Column<LoadedConsequence>[] = [
    { header: '#', cell: r => r.consequenceOrder ?? '—', mono: true, width: '60px' },
    { header: lang === 'ar' ? 'العنوان' : 'Title',
      cell: r => <div className="text-bold">{lang === 'ar' && r.titleAr ? r.titleAr : r.titleEn}</div> },
    { header: lang === 'ar' ? 'النوع' : 'Type', width: '130px',
      cell: r => r.consequenceType ? <Badge tone="info">{r.consequenceType}</Badge> : <span>—</span> },
    { header: lang === 'ar' ? 'الشدة المتبقية' : 'Residual S', width: '130px',
      cell: r => r.residualSeverity ?? '—' },
    { header: lang === 'ar' ? 'الخسارة المقدرة' : 'Est. loss (SAR)', mono: true, align: 'end', width: '160px',
      cell: r => r.estimatedLossSar != null ? r.estimatedLossSar.toLocaleString() : '—' },
  ];

  const prevCols: Column<LoadedPreventive>[] = [
    { header: lang === 'ar' ? 'العنوان' : 'Title',
      cell: r => <div className="text-bold">{lang === 'ar' && r.titleAr ? r.titleAr : r.titleEn}</div> },
    { header: lang === 'ar' ? 'النوع' : 'Type', width: '120px',
      cell: r => r.controlType ?? '—' },
    { header: lang === 'ar' ? 'الفعالية' : 'Effectiveness', width: '130px',
      cell: r => <Badge tone={EFFECT_TONES[r.effectiveness] ?? 'neutral'} dot>{r.effectiveness}</Badge> },
    { header: lang === 'ar' ? 'آخر اختبار' : 'Last test', width: '130px',
      cell: r => r.lastTestResult ?? '—' },
    { header: lang === 'ar' ? 'المالك' : 'Owner', cell: r => r.responsibleOwner ?? '—' },
  ];

  const mitCols: Column<LoadedMitigating>[] = [
    { header: lang === 'ar' ? 'العنوان' : 'Title',
      cell: r => <div className="text-bold">{lang === 'ar' && r.titleAr ? r.titleAr : r.titleEn}</div> },
    { header: lang === 'ar' ? 'النوع' : 'Type', width: '120px',
      cell: r => r.controlType ?? '—' },
    { header: lang === 'ar' ? 'الفعالية' : 'Effectiveness', width: '130px',
      cell: r => <Badge tone={EFFECT_TONES[r.effectiveness] ?? 'neutral'} dot>{r.effectiveness}</Badge> },
    { header: lang === 'ar' ? 'آخر اختبار' : 'Last test', width: '130px',
      cell: r => r.lastTestResult ?? '—' },
    { header: lang === 'ar' ? 'المالك' : 'Owner', cell: r => r.responsibleOwner ?? '—' },
  ];

  const tabs = [
    { id: 'threats' as const,      label: `${lang === 'ar' ? 'التهديدات' : 'Threats'} (${threats.length})` },
    { id: 'consequences' as const, label: `${lang === 'ar' ? 'الآثار' : 'Consequences'} (${conseq.length})` },
    { id: 'preventive' as const,   label: `${lang === 'ar' ? 'الضوابط الوقائية' : 'Preventive controls'} (${prev.length})` },
    { id: 'mitigating' as const,   label: `${lang === 'ar' ? 'الضوابط المخففة' : 'Mitigating controls'} (${mit.length})` },
  ];

  if (loading && !data) {
    return <Shell title={lang === 'ar' ? 'تحليل الفيونكة' : 'Bow-tie analysis'}><InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty></Shell>;
  }
  if (!data || !header) {
    return <Shell title={lang === 'ar' ? 'تحليل الفيونكة' : 'Bow-tie analysis'}><InlineEmpty>{lang === 'ar' ? 'لم يتم العثور' : 'Not found.'}</InlineEmpty></Shell>;
  }

  return (
    <Shell
      title={`${header.bowtieCode} · ${lang === 'ar' && header.topEventTitleAr ? header.topEventTitleAr : header.topEventTitleEn}`}
      sub="ISO 31010"
    >
      <PageHeader
        eyebrow={`${header.bowtieCode} · ISO 31010`}
        title={lang === 'ar' && header.topEventTitleAr ? header.topEventTitleAr : header.topEventTitleEn}
        sub={lang === 'ar' && header.topEventDescriptionAr ? header.topEventDescriptionAr : header.topEventDescriptionEn}
        actions={
          <>
            <Button variant="ghost" Icon={ArrowLeft} onClick={() => navigate('/risk/bowtie')}>
              {lang === 'ar' ? 'الكل' : 'All bow-ties'}
            </Button>
            <Badge tone={STATUS_TONES[header.status] ?? 'neutral'} dot>{header.status}</Badge>
            {canSubmit && (
              <Button variant="primary" Icon={Send} onClick={() => run(() => bowtieApi.submit(externalId), 'Submitted for review')} disabled={busy}>
                {lang === 'ar' ? 'إرسال للمراجعة' : 'Submit for review'}
              </Button>
            )}
            {canApprove && (
              <Button variant="primary" Icon={CheckCheck} onClick={() => {
                const who = window.prompt(lang === 'ar' ? 'اسم المعتمد:' : 'Approver name:', header.approvedByName ?? '');
                if (who === null) return;
                run(() => bowtieApi.approve(externalId, who || undefined), lang === 'ar' ? 'تمت الموافقة' : 'Approved');
              }} disabled={busy}>
                {lang === 'ar' ? 'الموافقة' : 'Approve'}
              </Button>
            )}
            {canArchive && (
              <Button variant="ghost" Icon={Archive} onClick={() => {
                if (!window.confirm(lang === 'ar' ? 'أرشفة هذا التحليل؟' : 'Archive this bow-tie?')) return;
                run(() => bowtieApi.archive(externalId), lang === 'ar' ? 'تمت الأرشفة' : 'Archived').then(() => navigate('/risk/bowtie'));
              }} disabled={busy}>
                {lang === 'ar' ? 'أرشفة' : 'Archive'}
              </Button>
            )}
          </>
        }
      />

      {/* ── KPIs / gauges ─────────────────────────────────────── */}
      <div className="kpi-grid-sm">
        <Card padded>
          <div className="stack-xs">
            <span className="text-sm" style={{ color: 'var(--ink-3)' }}>
              {lang === 'ar' ? 'التقييم المتبقي' : 'Overall residual'}
            </span>
            <div className="row-between">
              <span className="text-bolder" style={{ fontSize: 22 }}>
                {header.overallResidualRating ?? '—'}
              </span>
              {header.overallResidualRating &&
                <Badge tone={RATING_TONES[header.overallResidualRating] ?? 'neutral'} dot>{header.overallResidualRating}</Badge>}
            </div>
          </div>
        </Card>
        <Card padded>
          <div className="stack-xs">
            <span className="text-sm" style={{ color: 'var(--ink-3)' }}>
              {lang === 'ar' ? 'فعالية الضوابط' : 'Control effectiveness'}
            </span>
            <div className="row-between">
              <span className="text-bolder" style={{ fontSize: 22 }}>
                {header.overallControlEffectiveness != null ? `${header.overallControlEffectiveness.toFixed(1)}%` : '—'}
              </span>
            </div>
            <EffectivenessBar score={header.overallControlEffectiveness} />
          </div>
        </Card>
        <Card padded>
          <div className="stack-xs">
            <span className="text-sm" style={{ color: 'var(--ink-3)' }}>
              {lang === 'ar' ? 'مسارات التهديد' : 'Threat pathways'}
            </span>
            <span className="text-bolder" style={{ fontSize: 22 }}>{threats.length}</span>
            <span className="text-sm" style={{ color: 'var(--ink-3)' }}>
              {prev.length} {lang === 'ar' ? 'ضابط وقائي' : 'preventive controls'}
            </span>
          </div>
        </Card>
        <Card padded>
          <div className="stack-xs">
            <span className="text-sm" style={{ color: 'var(--ink-3)' }}>
              {lang === 'ar' ? 'مسارات الأثر' : 'Consequence pathways'}
            </span>
            <span className="text-bolder" style={{ fontSize: 22 }}>{conseq.length}</span>
            <span className="text-sm" style={{ color: 'var(--ink-3)' }}>
              {mit.length} {lang === 'ar' ? 'ضابط مخفف' : 'mitigating controls'}
            </span>
          </div>
        </Card>
      </div>

      {/* ── Visual bow-tie ────────────────────────────────────── */}
      <Card title={lang === 'ar' ? 'مخطط الفيونكة' : 'Bow-tie diagram'}>
        <BowTieDiagram
          topEvent={lang === 'ar' && header.topEventTitleAr ? header.topEventTitleAr : header.topEventTitleEn}
          threats={threats}
          consequences={conseq}
          preventives={prev}
          mitigatings={mit}
          lang={lang}
        />
      </Card>

      {/* ── Tabs ──────────────────────────────────────────────── */}
      <Tabs items={tabs} active={tab} onChange={setTab} />

      {tab === 'threats' && (
        <Card title={lang === 'ar' ? 'التهديدات' : 'Threats'}
              action={<Button variant="secondary" Icon={Plus} onClick={quickAddThreat} disabled={busy}>
                {lang === 'ar' ? 'إضافة' : 'Add'}
              </Button>}>
          {threats.length === 0
            ? <InlineEmpty>{lang === 'ar' ? 'لا توجد تهديدات بعد' : 'No threats yet.'}</InlineEmpty>
            : <DataTable columns={threatCols} rows={threats as LoadedThreat[]} />}
        </Card>
      )}
      {tab === 'consequences' && (
        <Card title={lang === 'ar' ? 'الآثار' : 'Consequences'}
              action={<Button variant="secondary" Icon={Plus} onClick={quickAddConsequence} disabled={busy}>
                {lang === 'ar' ? 'إضافة' : 'Add'}
              </Button>}>
          {conseq.length === 0
            ? <InlineEmpty>{lang === 'ar' ? 'لا توجد آثار بعد' : 'No consequences yet.'}</InlineEmpty>
            : <DataTable columns={consCols} rows={conseq as LoadedConsequence[]} />}
        </Card>
      )}
      {tab === 'preventive' && (
        <Card title={lang === 'ar' ? 'الضوابط الوقائية' : 'Preventive controls'}
              action={<Button variant="secondary" Icon={Plus} onClick={quickAddPreventive} disabled={busy}>
                {lang === 'ar' ? 'إضافة' : 'Add'}
              </Button>}>
          {prev.length === 0
            ? <InlineEmpty>{lang === 'ar' ? 'لا توجد ضوابط بعد' : 'No preventive controls yet.'}</InlineEmpty>
            : <DataTable columns={prevCols} rows={prev as LoadedPreventive[]} />}
        </Card>
      )}
      {tab === 'mitigating' && (
        <Card title={lang === 'ar' ? 'الضوابط المخففة' : 'Mitigating controls'}
              action={<Button variant="secondary" Icon={Plus} onClick={quickAddMitigating} disabled={busy}>
                {lang === 'ar' ? 'إضافة' : 'Add'}
              </Button>}>
          {mit.length === 0
            ? <InlineEmpty>{lang === 'ar' ? 'لا توجد ضوابط بعد' : 'No mitigating controls yet.'}</InlineEmpty>
            : <DataTable columns={mitCols} rows={mit as LoadedMitigating[]} />}
        </Card>
      )}
      {/* Force the t reference so the lint pass doesn't drop the i18n hook
          when the page is rendered in EN-only locales. */}
      <span hidden>{t('demoMode')}</span>
    </Shell>
  );
}

// ─── Effectiveness bar ──────────────────────────────────────────
function EffectivenessBar({ score }: { score?: number }) {
  const s = Math.max(0, Math.min(100, score ?? 0));
  const colour = s >= 70 ? 'var(--success)' : s >= 50 ? 'var(--accent-700)' : s >= 30 ? 'var(--warning)' : 'var(--danger)';
  return (
    <div style={{
      width: '100%', height: 8, borderRadius: 4,
      background: 'var(--surface-2)', overflow: 'hidden',
    }}>
      <div style={{ width: `${s}%`, height: '100%', background: colour }} />
    </div>
  );
}

// ─── SVG bow-tie diagram ────────────────────────────────────────
// Self-contained renderer. Threat boxes on the left, top-event diamond
// in the centre, consequence boxes on the right. Preventive controls
// stack between threat and the top event; mitigating controls stack
// between the top event and the consequence.
interface DiagramProps {
  topEvent: string;
  threats: BowTieThreatDto[];
  consequences: BowTieConsequenceDto[];
  preventives: BowTiePreventiveControlDto[];
  mitigatings: BowTieMitigatingControlDto[];
  lang: 'en' | 'ar';
}
function BowTieDiagram({ topEvent, threats, consequences, preventives, mitigatings, lang }: DiagramProps) {
  // Layout. SVG canvas 1100×600; threats stacked vertically on the left,
  // consequences on the right. Controls render as small rectangles
  // between each threat/consequence and the centre.
  const W = 1100, H = 600;
  const cx = W / 2, cy = H / 2;
  const eventW = 200, eventH = 80;

  const tBoxW = 220, tBoxH = 56, tx = 20;
  const cBoxW = 220, cBoxH = 56, ccx = W - cBoxW - 20;

  const tCount = Math.max(threats.length, 1);
  const cCount = Math.max(consequences.length, 1);
  const tStep = H / (tCount + 1);
  const cStep = H / (cCount + 1);

  // Build threat → preventive control list once.
  const preventiveByThreat = useMemo(() => {
    const m = new Map<string, BowTiePreventiveControlDto[]>();
    for (const p of preventives) {
      if (!p.threatExternalId) continue;
      const arr = m.get(p.threatExternalId) ?? [];
      arr.push(p);
      m.set(p.threatExternalId, arr);
    }
    return m;
  }, [preventives]);
  const mitigatingByCons = useMemo(() => {
    const m = new Map<string, BowTieMitigatingControlDto[]>();
    for (const c of mitigatings) {
      if (!c.consequenceExternalId) continue;
      const arr = m.get(c.consequenceExternalId) ?? [];
      arr.push(c);
      m.set(c.consequenceExternalId, arr);
    }
    return m;
  }, [mitigatings]);

  const effectFill = (e: string | undefined) => {
    switch (e) {
      case 'VERY_HIGH': return '#22c55e';
      case 'HIGH':      return '#86efac';
      case 'MEDIUM':    return '#fde68a';
      case 'LOW':       return '#fdba74';
      case 'VERY_LOW':  return '#fca5a5';
      default:          return '#e5e7eb';
    }
  };

  return (
    <div style={{ width: '100%', overflow: 'auto' }}>
      <svg viewBox={`0 0 ${W} ${H}`} width="100%" style={{ maxWidth: '100%', minWidth: 760 }}>
        {/* Centre top event — drawn as a diamond. */}
        <polygon
          points={`${cx},${cy - eventH/2} ${cx + eventW/2},${cy} ${cx},${cy + eventH/2} ${cx - eventW/2},${cy}`}
          fill="var(--danger)" opacity={0.92} stroke="var(--danger)" strokeWidth={2}
        />
        <text x={cx} y={cy} textAnchor="middle" dominantBaseline="middle"
              fill="#fff" fontSize={13} fontWeight={700}>
          <tspan x={cx} dy="-0.4em">{lang === 'ar' ? 'الحدث الأعلى' : 'Top event'}</tspan>
          <tspan x={cx} dy="1.2em" fontSize={11} fontWeight={500}>{clip(topEvent, 30)}</tspan>
        </text>

        {/* Threats + preventive chains. */}
        {threats.map((th, idx) => {
          const ty = (idx + 1) * tStep - tBoxH / 2;
          const tcy = ty + tBoxH / 2;
          // Chain: thread along a horizontal line from threat-right to top-event-left
          const chainStart = tx + tBoxW;
          const chainEnd   = cx - eventW / 2;
          const controls = (preventiveByThreat.get(th.externalId ?? '') ?? []).slice(0, 5);
          return (
            <g key={th.externalId ?? `t-${idx}`}>
              {/* Connector line */}
              <line x1={chainStart} y1={tcy} x2={chainEnd} y2={cy}
                    stroke="var(--ink-4)" strokeWidth={1.5} strokeDasharray="4 4" />
              {/* Threat box */}
              <rect x={tx} y={ty} width={tBoxW} height={tBoxH} rx={6}
                    fill="var(--surface-2)" stroke="var(--border)" />
              <text x={tx + 10} y={ty + 22} fontSize={12} fontWeight={700} fill="var(--ink-1)">
                {clip(lang === 'ar' && th.titleAr ? th.titleAr : th.titleEn, 28)}
              </text>
              <text x={tx + 10} y={ty + 40} fontSize={10} fill="var(--ink-3)">
                {th.source ?? ''} · {th.residualLikelihood ?? 'N/A'}
              </text>
              {/* Control barriers along the chain. */}
              {controls.map((p, ci) => {
                const ratio = (ci + 1) / (controls.length + 1);
                const ix = chainStart + ratio * (chainEnd - chainStart);
                const iy = tcy + ratio * (cy - tcy);
                return (
                  <g key={p.externalId ?? `p-${ci}`}>
                    <rect x={ix - 18} y={iy - 12} width={36} height={24} rx={3}
                          fill={effectFill(p.effectiveness)} stroke="var(--border)" />
                    <title>{p.titleEn} · {p.effectiveness}</title>
                  </g>
                );
              })}
            </g>
          );
        })}

        {/* Consequences + mitigating chains. */}
        {consequences.map((co, idx) => {
          const cy2 = (idx + 1) * cStep - cBoxH / 2;
          const cycy = cy2 + cBoxH / 2;
          const chainStart = cx + eventW / 2;
          const chainEnd   = ccx;
          const controls = (mitigatingByCons.get(co.externalId ?? '') ?? []).slice(0, 5);
          return (
            <g key={co.externalId ?? `c-${idx}`}>
              <line x1={chainStart} y1={cy} x2={chainEnd} y2={cycy}
                    stroke="var(--ink-4)" strokeWidth={1.5} strokeDasharray="4 4" />
              <rect x={ccx} y={cy2} width={cBoxW} height={cBoxH} rx={6}
                    fill="var(--surface-2)" stroke="var(--border)" />
              <text x={ccx + 10} y={cy2 + 22} fontSize={12} fontWeight={700} fill="var(--ink-1)">
                {clip(lang === 'ar' && co.titleAr ? co.titleAr : co.titleEn, 28)}
              </text>
              <text x={ccx + 10} y={cy2 + 40} fontSize={10} fill="var(--ink-3)">
                {co.consequenceType ?? ''} · {co.residualSeverity ?? 'N/A'}
              </text>
              {controls.map((m, ci) => {
                const ratio = (ci + 1) / (controls.length + 1);
                const ix = chainStart + ratio * (chainEnd - chainStart);
                const iy = cy + ratio * (cycy - cy);
                return (
                  <g key={m.externalId ?? `m-${ci}`}>
                    <rect x={ix - 18} y={iy - 12} width={36} height={24} rx={3}
                          fill={effectFill(m.effectiveness)} stroke="var(--border)" />
                    <title>{m.titleEn} · {m.effectiveness}</title>
                  </g>
                );
              })}
            </g>
          );
        })}
      </svg>
    </div>
  );
}

function clip(s: string, n: number) {
  if (!s) return '';
  return s.length > n ? s.slice(0, n - 1) + '…' : s;
}
