import { useEffect, useMemo, useState } from 'react';
import {
  ShieldCheck, RefreshCw, Plus, ArrowRight, XCircle, Gauge,
  CheckCircle2, Send, FileText, UserCheck, CalendarClock,
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
  ncaEccFilingApi, forwardStatus, ncaStatusLabel, ncaAdvanceLabel,
  NCA_STATUS_TONE, NCA_DOMAINS, overallScoreNum, scoreTone,
  type NcaEccFiling, type NcaEccFilingCreate, type NcaEccTransition,
} from '../../lib/ncaEccFiling';

/**
 * NCA ECC annual filings register (ECC-2:2024).
 *
 * Surfaces the orphaned /api/nca-ecc/filings endpoints: list, create, and the
 * DRAFT -> PEER_REVIEW -> CISO_APPROVAL -> SUBMITTED -> ACKNOWLEDGED workflow,
 * with the five-domain maturity scorecard, gap summary and an audit timeline.
 */
export function NcaEccFilingsPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [rows, setRows] = useState<NcaEccFiling[]>([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [advance, setAdvance] = useState<{ filing: NcaEccFiling; to: string } | null>(null);
  const [cancelFor, setCancelFor] = useState<NcaEccFiling | null>(null);

  async function load() {
    setLoading(true);
    try {
      const list = await ncaEccFilingApi.list();
      const sorted = [...list].sort((a, b) => (b.fiscalYear ?? 0) - (a.fiscalYear ?? 0));
      setRows(sorted);
      setFailed(false);
      setSelectedId(prev => prev ?? (sorted[0]?.id ?? null));
    } catch (e) {
      setFailed(true);
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'تعذّر تحميل البيانات' : 'Failed to load filings'));
    } finally { setLoading(false); }
  }
  useEffect(() => { void load(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const counts = useMemo(() => {
    const latest = [...rows].sort((a, b) => (b.fiscalYear ?? 0) - (a.fiscalYear ?? 0))[0];
    return {
      total: rows.length,
      open: rows.filter(r => ['DRAFT', 'PEER_REVIEW', 'CISO_APPROVAL'].includes(r.status)).length,
      filed: rows.filter(r => ['SUBMITTED', 'ACKNOWLEDGED'].includes(r.status)).length,
      latestScore: latest ? overallScoreNum(latest) : null,
    };
  }, [rows]);

  const selected = rows.find(r => r.id === selectedId) ?? null;

  async function doTransition(filing: NcaEccFiling, body: NcaEccTransition) {
    setBusyId(filing.id);
    try {
      const updated = await ncaEccFilingApi.transition(filing.id, body);
      setRows(prev => prev.map(x => x.id === updated.id ? updated : x));
      toast.success(isAr ? `${updated.fiscalYear} → ${ncaStatusLabel(body.status, true)}` : `FY${updated.fiscalYear} → ${ncaStatusLabel(body.status, false)}`);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل الانتقال' : 'Transition failed'));
    } finally { setBusyId(null); }
  }

  function onAdvanceClick(filing: NcaEccFiling) {
    const to = forwardStatus(filing.status);
    if (!to) return;
    // ACKNOWLEDGED needs no extra field — fire directly. Others collect one.
    if (to === 'ACKNOWLEDGED') void doTransition(filing, { status: to });
    else setAdvance({ filing, to });
  }

  async function submitCreate(v: NcaEccFilingCreate): Promise<void> {
    try {
      const created = await ncaEccFilingApi.create({
        fiscalYear: Number(v.fiscalYear),
        frameworkVersion: v.frameworkVersion || 'ECC-2:2024',
        titleEn: v.titleEn?.trim() || undefined,
        domain1Score: numOrUndef(v.domain1Score), domain2Score: numOrUndef(v.domain2Score),
        domain3Score: numOrUndef(v.domain3Score), domain4Score: numOrUndef(v.domain4Score),
        domain5Score: numOrUndef(v.domain5Score),
        gapSummaryEn: v.gapSummaryEn?.trim() || undefined,
      });
      toast.success(isAr ? `تم إنشاء بلاغ ${created.fiscalYear}` : `FY${created.fiscalYear} filing created`);
      setCreateOpen(false);
      setSelectedId(created.id);
      await load();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل الإنشاء' : 'Create failed'));
    }
  }

  const year = new Date().getFullYear();
  const scoreField = (n: number): FieldDef => ({
    kind: 'select', key: `domain${n}Score`, span: 6,
    label: isAr ? NCA_DOMAINS[n - 1].nameAr : NCA_DOMAINS[n - 1].nameEn,
    options: ['1', '2', '3', '4', '5'].map(v => ({ value: v, label: v })),
  });
  const createFields: FieldDef[] = [
    { kind: 'number', key: 'fiscalYear', label: isAr ? 'السنة المالية *' : 'Fiscal year *', required: true, min: 2024, max: 2100, span: 6 },
    { kind: 'text', key: 'frameworkVersion', label: isAr ? 'إصدار الإطار' : 'Framework version', span: 6 },
    scoreField(1), scoreField(2), scoreField(3), scoreField(4), scoreField(5),
    { kind: 'textarea', key: 'gapSummaryEn', label: isAr ? 'ملخّص الفجوات' : 'Gap summary', rows: 2, span: 12 },
  ];
  const createInitial = { fiscalYear: year, frameworkVersion: 'ECC-2:2024', domain1Score: '3', domain2Score: '3', domain3Score: '3', domain4Score: '3', domain5Score: '3', gapSummaryEn: '' };

  return (
    <Shell title={isAr ? 'بلاغات الضوابط الأساسية (NCA ECC)' : 'NCA ECC filings'}
      sub={isAr ? 'البلاغ السنوي للضوابط الأساسية للأمن السيبراني — الهيئة الوطنية' : 'Annual Essential Cybersecurity Controls return — Saudi NCA'}>
      <PageHeader
        eyebrow={isAr ? 'الامتثال · الهيئة الوطنية للأمن السيبراني' : 'Compliance · National Cybersecurity Authority'}
        title={isAr ? 'بلاغات الضوابط الأساسية' : 'NCA ECC filings'}
        sub={isAr
          ? 'سجل البلاغات السنوية للضوابط الأساسية (ECC-2:2024) — تقييم النضج عبر خمسة مجالات، ومراجعة الأقران، واعتماد رئيس الأمن، والتقديم للهيئة.'
          : 'Annual Essential Cybersecurity Controls returns (ECC-2:2024) — five-domain maturity assessment through peer review, CISO approval and submission to the NCA.'}
        actions={
          <div style={{ display: 'inline-flex', gap: 8 }}>
            <Button variant="ghost" size="sm" Icon={RefreshCw} onClick={() => void load()} disabled={loading}>
              {isAr ? 'تحديث' : 'Refresh'}
            </Button>
            <Button variant="primary" size="sm" Icon={Plus} onClick={() => setCreateOpen(true)}>
              {isAr ? 'بلاغ جديد' : 'New filing'}
            </Button>
          </div>
        }
      />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={FileText}     label={isAr ? 'إجمالي البلاغات' : 'Total filings'} value={counts.total} tone="primary" to="/nca-ecc/filings" />
        <Kpi Icon={Gauge}        label={isAr ? 'أحدث نضج' : 'Latest maturity'}      value={counts.latestScore != null ? `${counts.latestScore.toFixed(2)}/5` : '—'} tone="info" to="/nca-ecc/filings" />
        <Kpi Icon={Send}         label={isAr ? 'مُقدَّمة' : 'Filed'}                value={counts.filed} tone="success" to="/nca-ecc/filings" />
        <Kpi Icon={CalendarClock} label={isAr ? 'قيد الإعداد' : 'In progress'}      value={counts.open} tone={counts.open > 0 ? 'warning' : 'neutral'} to="/nca-ecc/filings" />
      </div>

      <Card title={isAr ? 'السجل' : 'Register'} Icon={ShieldCheck}>
        {loading && rows.length === 0
          ? <div style={{ padding: 24, textAlign: 'center', color: 'var(--ink-3)', fontSize: 13 }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div>
          : failed && rows.length === 0
            ? <div style={{ padding: 24, textAlign: 'center', color: 'var(--ink-3)', fontSize: 13 }}>{isAr ? 'السجل غير متاح حالياً' : 'Register unavailable right now'}</div>
            : rows.length === 0
              ? <div style={{ padding: 24, textAlign: 'center', color: 'var(--ink-3)', fontSize: 13 }}>{isAr ? 'لا توجد بلاغات بعد' : 'No filings yet'}</div>
              : <table className="grc-table">
                  <thead><tr>
                    <th>{isAr ? 'السنة' : 'Year'}</th>
                    <th>{isAr ? 'الإطار' : 'Framework'}</th>
                    <th>{isAr ? 'النضج العام' : 'Overall maturity'}</th>
                    <th>{isAr ? 'فجوات/CAPA' : 'CAPAs'}</th>
                    <th>{isAr ? 'الحالة' : 'Status'}</th>
                    <th style={{ textAlign: 'end' }} />
                  </tr></thead>
                  <tbody>
                    {rows.map(r => {
                      const to = forwardStatus(r.status);
                      const score = overallScoreNum(r);
                      const isSel = r.id === selectedId;
                      return (
                        <tr key={r.id} onClick={() => setSelectedId(r.id)}
                          style={{ cursor: 'pointer', background: isSel ? 'var(--primary-50)' : undefined }}>
                          <td className="mono" style={{ fontWeight: 700 }}>{r.fiscalYear}</td>
                          <td className="text-2xs">{r.frameworkVersion ?? '—'}</td>
                          <td>{score != null
                            ? <Badge tone={scoreTone(score)}>{score.toFixed(2)}/5</Badge>
                            : <span className="text-muted text-2xs">{isAr ? 'غير مُقيّم' : 'unscored'}</span>}</td>
                          <td style={{ textAlign: 'center' }}>{r.capaCount ?? 0}</td>
                          <td><Badge tone={NCA_STATUS_TONE[r.status] ?? 'neutral'} dot>{ncaStatusLabel(r.status, isAr)}</Badge></td>
                          <td style={{ textAlign: 'end' }}>
                            <div style={{ display: 'inline-flex', gap: 4 }}>
                              {to && (
                                <Button size="sm" variant="secondary" Icon={r.status === 'SUBMITTED' ? CheckCircle2 : ArrowRight}
                                  disabled={busyId === r.id}
                                  onClick={(e) => { e.stopPropagation(); onAdvanceClick(r); }}>
                                  {ncaAdvanceLabel(r.status, isAr)}
                                </Button>
                              )}
                              {['DRAFT', 'PEER_REVIEW', 'CISO_APPROVAL'].includes(r.status) && (
                                <Button size="sm" variant="ghost" Icon={XCircle}
                                  disabled={busyId === r.id}
                                  onClick={(e) => { e.stopPropagation(); setCancelFor(r); }}>
                                  {isAr ? 'إلغاء' : 'Cancel'}
                                </Button>
                              )}
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>}
      </Card>

      {selected && <FilingDetail filing={selected} isAr={isAr} />}

      {/* Create */}
      <FormModal<NcaEccFilingCreate & Record<string, unknown>>
        open={createOpen}
        title={isAr ? 'بلاغ ضوابط أساسية جديد' : 'New NCA ECC filing'}
        fields={createFields}
        initial={createInitial as never}
        onClose={() => setCreateOpen(false)}
        onSubmit={submitCreate as never}
        submitLabel={isAr ? 'إنشاء' : 'Create'}
      />

      {/* Forward transition with a context field */}
      {advance && (
        <FormModal<Record<string, string>>
          open
          title={`${ncaAdvanceLabel(advance.filing.status, isAr)} · FY${advance.filing.fiscalYear}`}
          fields={advanceFields(advance.to, isAr)}
          initial={advanceInitial(advance.to)}
          onClose={() => setAdvance(null)}
          onSubmit={async (v) => {
            const body: NcaEccTransition = { status: advance.to, ...v };
            await doTransition(advance.filing, body);
            setAdvance(null);
          }}
          submitLabel={ncaAdvanceLabel(advance.filing.status, isAr)}
        />
      )}

      {/* Cancel */}
      {cancelFor && (
        <FormModal<{ cancellationReasonEn: string }>
          open
          title={`${isAr ? 'إلغاء البلاغ' : 'Cancel filing'} · FY${cancelFor.fiscalYear}`}
          fields={[{ kind: 'textarea', key: 'cancellationReasonEn', label: isAr ? 'سبب الإلغاء' : 'Cancellation reason', rows: 2, span: 12 }]}
          initial={{ cancellationReasonEn: '' }}
          onClose={() => setCancelFor(null)}
          onSubmit={async (v) => {
            await doTransition(cancelFor, { status: 'CANCELLED', cancellationReasonEn: v.cancellationReasonEn?.trim() || undefined });
            setCancelFor(null);
          }}
          submitLabel={isAr ? 'تأكيد الإلغاء' : 'Confirm cancel'}
        />
      )}
    </Shell>
  );
}

function advanceFields(to: string, isAr: boolean): FieldDef[] {
  if (to === 'PEER_REVIEW') return [
    { kind: 'text', key: 'peerReviewer', label: isAr ? 'مراجع الأقران' : 'Peer reviewer', span: 12 },
    { kind: 'textarea', key: 'peerReviewCommentsEn', label: isAr ? 'ملاحظات' : 'Comments', rows: 2, span: 12 },
  ];
  // F2-225: no "CISO approver" input. The approval happens on this call — the server
  // stamps cisoApprovalAt from its own clock — so its performer is whoever is signed
  // in, and asking an operator to TYPE the approver is how a free-text attribution
  // field teaches them to enter somebody else's name (7 recorded instances).
  if (to === 'CISO_APPROVAL') return [];
  if (to === 'SUBMITTED') return [
    { kind: 'text', key: 'submissionReference', label: isAr ? 'مرجع التقديم للهيئة' : 'NCA submission reference', span: 12, placeholder: 'NCA-ECC-2026-XXXXXX' },
  ];
  return [];
}
function advanceInitial(to: string): Record<string, string> {
  if (to === 'PEER_REVIEW') return { peerReviewer: '', peerReviewCommentsEn: '' };
  if (to === 'CISO_APPROVAL') return {};
  if (to === 'SUBMITTED') return { submissionReference: '' };
  return {};
}
function numOrUndef(v: unknown): number | undefined {
  const n = Number(v);
  return Number.isFinite(n) && n >= 1 && n <= 5 ? n : undefined;
}

// ─── Detail ────────────────────────────────────────────────────────────────────

function FilingDetail({ filing, isAr }: { filing: NcaEccFiling; isAr: boolean }) {
  const overall = overallScoreNum(filing);
  const gap = (isAr ? filing.gapSummaryAr : filing.gapSummaryEn) || filing.gapSummaryEn;

  const timeline: Array<{ label: string; who?: string | null; at?: string | null }> = [
    { label: isAr ? 'الإعداد' : 'Drafted', who: filing.draftedBy, at: filing.draftedAt },
    { label: isAr ? 'مراجعة الأقران' : 'Peer review', who: filing.peerReviewer, at: filing.peerReviewCompletedAt ?? filing.peerReviewStartedAt },
    { label: isAr ? 'اعتماد رئيس الأمن' : 'CISO approval', who: filing.cisoApprover, at: filing.cisoApprovalAt },
    { label: isAr ? 'التقديم للهيئة' : 'Submitted to NCA', who: filing.submissionReference, at: filing.submittedAt },
    { label: isAr ? 'الإقرار' : 'Acknowledged', who: null, at: filing.acknowledgedAt },
  ];

  return (
    <div style={{ marginTop: 16 }}>
      <Card title={`${isAr ? 'السنة المالية' : 'FY'} ${filing.fiscalYear} · ${filing.frameworkVersion ?? 'ECC-2:2024'}`} Icon={ShieldCheck}
        action={overall != null ? <Badge tone={scoreTone(overall)}>{isAr ? 'النضج العام' : 'Overall'} {overall.toFixed(2)}/5</Badge> : undefined}>

        <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: 10 }}>
          {isAr ? 'نضج المجالات الخمسة' : 'Five-domain maturity'}
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 18 }}>
          {NCA_DOMAINS.map((d, i) => {
            const score = filing[d.key] as number | null | undefined;
            const pct = score ? (score / 5) * 100 : 0;
            const tone = scoreTone(score);
            const colour = tone === 'success' ? 'var(--success)' : tone === 'info' ? 'var(--info)' : tone === 'warning' ? 'var(--warning)' : tone === 'danger' ? 'var(--danger)' : 'var(--border)';
            return (
              <div key={i} style={{ display: 'grid', gridTemplateColumns: '1fr 120px 36px', gap: 10, alignItems: 'center' }}>
                <span style={{ fontSize: 12, color: 'var(--ink-2)' }}>{isAr ? d.nameAr : d.nameEn}</span>
                <div style={{ height: 8, borderRadius: 4, background: 'var(--surface-2)', overflow: 'hidden' }}>
                  <div style={{ width: `${pct}%`, height: '100%', background: colour, borderRadius: 4, transition: 'width .3s' }} />
                </div>
                <span className="mono" style={{ fontSize: 12, fontWeight: 700, textAlign: 'end', color: 'var(--ink-1)' }}>{score ?? '—'}</span>
              </div>
            );
          })}
        </div>

        {gap && (
          <div style={{ marginBottom: 16 }}>
            <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: 6 }}>
              {isAr ? 'ملخّص الفجوات' : 'Gap summary'}
            </div>
            <p style={{ margin: 0, fontSize: 13, lineHeight: 1.6, color: 'var(--ink-2)' }}>{gap}</p>
            {(filing.capaCount ?? 0) > 0 && (
              <div style={{ marginTop: 6 }}><Badge tone="warning">{filing.capaCount} CAPA</Badge></div>
            )}
          </div>
        )}

        <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: 8 }}>
          {isAr ? 'مسار الاعتماد' : 'Approval trail'}
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          {timeline.map((s, i) => {
            const done = !!s.at;
            return (
              <div key={i} style={{ display: 'grid', gridTemplateColumns: '20px 160px 1fr auto', gap: 10, alignItems: 'center', padding: '5px 6px' }}>
                <span style={{ width: 16, height: 16, borderRadius: '50%', background: done ? 'var(--success)' : 'var(--surface-2)', color: '#fff', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                  {done ? <CheckCircle2 size={11} /> : <span style={{ width: 5, height: 5, borderRadius: '50%', background: 'var(--ink-4)' }} />}
                </span>
                <span style={{ fontSize: 12, fontWeight: 600, color: done ? 'var(--ink-1)' : 'var(--ink-3)' }}>{s.label}</span>
                <span style={{ fontSize: 11, color: 'var(--ink-3)', display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                  {s.who && <><UserCheck size={11} />{s.who}</>}
                </span>
                <span className="mono text-2xs" style={{ color: 'var(--ink-4)' }}>{s.at ? s.at.slice(0, 10) : ''}</span>
              </div>
            );
          })}
        </div>

        {filing.peerReviewCommentsEn && (
          <div style={{ marginTop: 12, padding: 10, borderRadius: 'var(--r-sm)', background: 'var(--surface-2)', fontSize: 12, color: 'var(--ink-2)', lineHeight: 1.5 }}>
            <strong style={{ color: 'var(--ink-3)' }}>{isAr ? 'ملاحظات المراجعة: ' : 'Review notes: '}</strong>
            {filing.peerReviewCommentsEn}
          </div>
        )}
        {filing.status === 'CANCELLED' && filing.cancellationReasonEn && (
          <div style={{ marginTop: 12, padding: 10, borderRadius: 'var(--r-sm)', background: 'var(--danger-bg)', color: 'var(--danger-fg)', fontSize: 12, lineHeight: 1.5 }}>
            <strong>{isAr ? 'سبب الإلغاء: ' : 'Cancellation reason: '}</strong>{filing.cancellationReasonEn}
          </div>
        )}
      </Card>
    </div>
  );
}
