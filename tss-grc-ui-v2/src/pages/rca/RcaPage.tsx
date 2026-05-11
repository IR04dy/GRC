import { useEffect, useMemo, useState } from 'react';
import {
  Microscope, Plus, Send, CheckCircle2, XCircle, FileText,
  ChevronRight, ChevronDown, Clock, Wrench,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { EmptyState } from '../../components/ui/EmptyState';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  listRcas, createRca, submitRca, approveRca, rejectRca, generateCapasFromRca,
  parseFiveWhys, RCA_STATUS_TONE,
  type RootCauseAnalysis, type RcaStatus, type RcaMethodology,
} from '../../lib/rootCauseAnalysis';

/**
 * RcaPage — Saudi GRC prompt §3.8 + §7.6 #6.
 *
 * Root Cause Analysis register. Free-text lessons-learned isn't enough
 * for the regulatory evidence package — an RCA captures the
 * methodology (5-Whys / Fishbone / Pareto / Other), facilitator +
 * participants, contributing factors, identified root cause(s), and
 * corrective recommendations bound to a parent entity.
 *
 * Tabs filter by status; "All" shows everything. Inline action buttons
 * are state-machine aware so only legal transitions render.
 */
type TabId = 'all' | 'draft' | 'in_review' | 'approved' | 'rejected';

export function RcaPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const [tab, setTab] = useState<TabId>('all');
  const [rows, setRows] = useState<RootCauseAnalysis[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [expanded, setExpanded] = useState<Set<number>>(new Set());

  useEffect(() => { void reload(); }, []);

  async function reload() {
    setLoading(true);
    try {
      const page = await listRcas(0, 200).catch(() => ({ content: [], totalElements: 0 }));
      setRows(page.content);
    } finally { setLoading(false); }
  }

  const stats = useMemo(() => {
    const c: Record<RcaStatus, number> = { DRAFT: 0, IN_REVIEW: 0, APPROVED: 0, REJECTED: 0 };
    for (const r of rows) c[r.status] = (c[r.status] ?? 0) + 1;
    return c;
  }, [rows]);

  const visible = useMemo(() => {
    if (tab === 'all') return rows;
    const wanted = tab.toUpperCase() as RcaStatus;
    return rows.filter(r => r.status === wanted);
  }, [rows, tab]);

  const FIELDS: FieldDef[] = [
    { kind: 'select', key: 'relatedEntityType', label: t('rcaFldEntityType') + ' *', required: true,
      options: [
        { value: 'incident', label: 'Incident' },
        { value: 'data_breach', label: 'Data breach' },
        { value: 'bcm_invocation', label: 'BCM invocation' },
        { value: 'supplier_incident', label: 'Supplier incident' },
      ], span: 4 },
    { kind: 'number', key: 'relatedEntityId', label: t('rcaFldEntityId') + ' *', required: true, span: 4 },
    { kind: 'text', key: 'relatedEntityLabel', label: t('rcaFldEntityLabel'), placeholder: 'INC-2026-007', span: 4 },
    { kind: 'select', key: 'methodology', label: t('rcaFldMethodology') + ' *', required: true,
      options: [
        { value: 'FIVE_WHYS', label: '5 Whys' },
        { value: 'FISHBONE',  label: 'Fishbone (Ishikawa)' },
        { value: 'PARETO',    label: 'Pareto (80/20)' },
        { value: 'OTHER',     label: 'Other (free-form narrative)' },
      ], span: 6 },
    { kind: 'text', key: 'facilitator', label: t('rcaFldFacilitator'), span: 6 },
    { kind: 'textarea', key: 'problemStatementEn', label: t('rcaFldProblem') + ' *', rows: 2, span: 12 },
    { kind: 'textarea', key: 'rootCauseEn', label: t('rcaFldRootCause'), rows: 2, span: 12 },
    { kind: 'textarea', key: 'recommendationsEn', label: t('rcaFldRecommendations'), rows: 2, span: 12 },
    { kind: 'text', key: 'capaRefs', label: t('rcaFldCapaRefs'), placeholder: 'CAPA-1024,CAPA-1031', span: 12 },
  ];

  async function onCreate(values: Record<string, any>) {
    try {
      await createRca({
        relatedEntityType: values.relatedEntityType,
        relatedEntityId: Number(values.relatedEntityId),
        relatedEntityLabel: values.relatedEntityLabel || undefined,
        methodology: (values.methodology || 'FIVE_WHYS') as RcaMethodology,
        facilitator: values.facilitator || undefined,
        problemStatementEn: values.problemStatementEn,
        rootCauseEn: values.rootCauseEn || undefined,
        recommendationsEn: values.recommendationsEn || undefined,
        capaRefs: values.capaRefs || undefined,
      });
      toast.success(t('rcaCreatedToast'));
      setCreateOpen(false);
      await reload();
    } catch { toast.danger(t('rcaCreateFailedToast')); }
  }

  async function onSubmit(row: RootCauseAnalysis) {
    setBusyId(row.id);
    try { await submitRca(row.id); toast.success(t('rcaSubmittedToast')); await reload(); }
    catch { toast.danger(t('rcaActionFailedToast')); }
    finally { setBusyId(null); }
  }
  async function onApprove(row: RootCauseAnalysis) {
    setBusyId(row.id);
    try { await approveRca(row.id); toast.success(t('rcaApprovedToast')); await reload(); }
    catch { toast.danger(t('rcaActionFailedToast')); }
    finally { setBusyId(null); }
  }
  async function onReject(row: RootCauseAnalysis) {
    const reason = window.prompt(t('rcaRejectPrompt'), '');
    if (!reason || !reason.trim()) return;
    setBusyId(row.id);
    try { await rejectRca(row.id, reason.trim()); toast.success(t('rcaRejectedToast')); await reload(); }
    catch { toast.danger(t('rcaActionFailedToast')); }
    finally { setBusyId(null); }
  }
  async function onGenerateCapas(row: RootCauseAnalysis) {
    if (!window.confirm(t('rcaGenerateCapasConfirm'))) return;
    setBusyId(row.id);
    try {
      const created = await generateCapasFromRca(row.id);
      toast.success(`${t('rcaGenerateCapasToast')} (${created.length})`);
      await reload();
    } catch { toast.danger(t('rcaActionFailedToast')); }
    finally { setBusyId(null); }
  }

  function toggleExpanded(id: number) {
    const next = new Set(expanded);
    if (next.has(id)) next.delete(id); else next.add(id);
    setExpanded(next);
  }

  const tabs: TabItem<TabId>[] = [
    { id: 'all',       label: `${t('rcaTabAll')} (${rows.length})` },
    { id: 'draft',     label: `${t('rcaTabDraft')} (${stats.DRAFT})` },
    { id: 'in_review', label: `${t('rcaTabInReview')} (${stats.IN_REVIEW})` },
    { id: 'approved',  label: `${t('rcaTabApproved')} (${stats.APPROVED})` },
    { id: 'rejected',  label: `${t('rcaTabRejected')} (${stats.REJECTED})` },
  ];

  return (
    <Shell title={t('rcaPageTitle')} sub={t('rcaPageSub')}>
      <PageHeader
        eyebrow={t('rcaPageEyebrow')}
        title={t('rcaPageTitle')}
        sub={t('rcaPageSub')}
        actions={
          <Button variant="primary" Icon={Plus} onClick={() => setCreateOpen(true)}>
            {t('rcaAddBtn')}
          </Button>
        }
      />

      <div className="kpi-grid">
        <Kpi Icon={FileText}     label={t('rcaKpiDraft')}    value={stats.DRAFT}     tone="warning" />
        <Kpi Icon={Clock}        label={t('rcaKpiInReview')} value={stats.IN_REVIEW} tone="primary" />
        <Kpi Icon={CheckCircle2} label={t('rcaKpiApproved')} value={stats.APPROVED}  tone="success" />
        <Kpi Icon={XCircle}      label={t('rcaKpiRejected')} value={stats.REJECTED}  tone="danger" />
      </div>

      <Tabs items={tabs} active={tab} onChange={setTab} />

      <Card title={t('rcaListTitle')} Icon={Microscope}>
        {loading
          ? <div style={{ padding: 24, textAlign: 'center', color: 'var(--ink-3)' }}>{t('loadingEllipsis')}</div>
          : visible.length === 0
            ? <EmptyState Icon={Microscope} title={t('rcaEmpty')} sub={t('rcaEmptySub')} />
            : <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                {visible.map(row => (
                  <RcaRow key={row.id} row={row} lang={lang} t={t} busyId={busyId}
                          isExpanded={expanded.has(row.id)}
                          onToggle={() => toggleExpanded(row.id)}
                          onSubmit={onSubmit} onApprove={onApprove} onReject={onReject}
                          onGenerateCapas={onGenerateCapas} />
                ))}
              </div>}
      </Card>

      <FormModal<Record<string, any>>
        open={createOpen}
        title={t('rcaAddTitle')}
        fields={FIELDS}
        initial={{ methodology: 'FIVE_WHYS', relatedEntityType: 'incident' }}
        onClose={() => setCreateOpen(false)}
        onSubmit={onCreate}
      />
    </Shell>
  );
}

function RcaRow({ row, lang, t, busyId, isExpanded, onToggle, onSubmit, onApprove, onReject, onGenerateCapas }: {
  row: RootCauseAnalysis;
  lang: 'en' | 'ar';
  t: (k: string) => string;
  busyId: number | null;
  isExpanded: boolean;
  onToggle: () => void;
  onSubmit: (r: RootCauseAnalysis) => Promise<void>;
  onApprove: (r: RootCauseAnalysis) => Promise<void>;
  onReject: (r: RootCauseAnalysis) => Promise<void>;
  onGenerateCapas: (r: RootCauseAnalysis) => Promise<void>;
}) {
  const problem = lang === 'ar' && row.problemStatementAr ? row.problemStatementAr : row.problemStatementEn;
  const rootCause = lang === 'ar' && row.rootCauseAr ? row.rootCauseAr : row.rootCauseEn;
  const recs = lang === 'ar' && row.recommendationsAr ? row.recommendationsAr : row.recommendationsEn;
  const whys = parseFiveWhys(row.fiveWhysJson);

  return (
    <div style={{
      padding: '12px 14px', background: 'var(--surface-2)',
      border: '1px solid var(--border)', borderRadius: 'var(--r-md)',
    }}>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr auto', gap: 12, alignItems: 'center' }}>
        <button type="button" onClick={onToggle} style={{
          background: 'transparent', border: 'none', cursor: 'pointer', textAlign: lang === 'ar' ? 'right' : 'left',
          padding: 0, color: 'inherit', font: 'inherit',
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
            {isExpanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
            <Badge tone={RCA_STATUS_TONE[row.status]}>{t('rcaStatus_' + row.status)}</Badge>
            <Badge tone="info">{t('rcaMethod_' + row.methodology)}</Badge>
            <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>
              {row.relatedEntityType}#{row.relatedEntityId}
              {row.relatedEntityLabel ? ` · ${row.relatedEntityLabel}` : ''}
            </span>
          </div>
          <div style={{ fontWeight: 600, fontSize: 13 }}>{problem}</div>
          {row.facilitator && (
            <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }}>
              {t('rcaFldFacilitator')}: <strong>{row.facilitator}</strong>
            </div>
          )}
        </button>
        <div style={{ display: 'flex', gap: 6, flexShrink: 0 }}>
          {row.status === 'DRAFT' && (
            <Button size="sm" variant="primary" Icon={Send} disabled={busyId === row.id}
                    onClick={() => onSubmit(row)}>{t('rcaActSubmit')}</Button>
          )}
          {row.status === 'IN_REVIEW' && (
            <>
              <Button size="sm" variant="primary" Icon={CheckCircle2} disabled={busyId === row.id}
                      onClick={() => onApprove(row)}>{t('rcaActApprove')}</Button>
              <Button size="sm" variant="ghost" Icon={XCircle} disabled={busyId === row.id}
                      onClick={() => onReject(row)}>{t('rcaActReject')}</Button>
            </>
          )}
          {row.status === 'APPROVED' && row.recommendationsEn && (
            <Button size="sm" variant="ghost" Icon={Wrench} disabled={busyId === row.id}
                    onClick={() => onGenerateCapas(row)}
                    title={t('rcaGenerateCapasHint')}>
              {t('rcaActGenerateCapas')}
            </Button>
          )}
        </div>
      </div>

      {isExpanded && (
        <div style={{ marginTop: 12, paddingTop: 12, borderTop: '1px solid var(--border)',
                      display: 'flex', flexDirection: 'column', gap: 12 }}>
          {rootCause && (
            <Section title={t('rcaSectionRootCause')} body={rootCause} tone="danger" />
          )}
          {recs && (
            <Section title={t('rcaSectionRecommendations')} body={recs} tone="success" />
          )}
          {whys.length > 0 && (
            <div>
              <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--ink-3)', marginBottom: 6,
                            textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                {t('rcaSection5Whys')}
              </div>
              <ol style={{ margin: 0, paddingInlineStart: 18, display: 'flex', flexDirection: 'column', gap: 6 }}>
                {whys.map((w, i) => (
                  <li key={i} style={{ fontSize: 12 }}>
                    <strong>{w.question}</strong> — {w.answer}
                  </li>
                ))}
              </ol>
            </div>
          )}
          {row.capaRefs && (
            <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
              <strong>{t('rcaSectionCapas')}:</strong>{' '}
              <span className="mono">{row.capaRefs}</span>
            </div>
          )}
          {row.status === 'REJECTED' && row.rejectionReason && (
            <Section title={t('rcaSectionRejection')} body={row.rejectionReason} tone="danger" />
          )}
          {row.approverName && row.approvedAt && (
            <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
              <strong>{t('rcaApprovedBy')}:</strong> {row.approverName} · {row.approvedAt.slice(0, 16).replace('T', ' ')}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function Section({ title, body, tone }: { title: string; body: string; tone: 'danger' | 'success' }) {
  const fg = tone === 'danger' ? 'var(--danger-fg)' : 'var(--success-fg)';
  return (
    <div>
      <div style={{ fontSize: 11, fontWeight: 700, color: fg, marginBottom: 4,
                    textTransform: 'uppercase', letterSpacing: '0.04em' }}>
        {title}
      </div>
      <div style={{ fontSize: 12, color: 'var(--ink-1)', whiteSpace: 'pre-wrap' }}>{body}</div>
    </div>
  );
}

