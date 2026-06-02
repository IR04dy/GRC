import { useEffect, useState } from 'react';
import { Plus, Award, FolderOpen } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { EntityDocsModal } from '../../components/ui/EntityDocsModal';
import { Card } from '../../components/ui/Card';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';
import {
  aiLifecycleApi,
  RECERT_STATUS_LABEL, RECERT_STATUS_TONE,
  nextRecertStatuses,
  type RecertificationResponse, type RecertificationStatus,
} from '../../lib/aiLifecycle';

/** Sprint 31 — Periodic Recertification register. */
export function AiLifecycleRecertificationsPage() {
  const { lang } = useI18n();
  const demoMode = !isAuthenticated();

  const [rows, setRows] = useState<RecertificationResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [acting, setActing] = useState(false);

  // create
  const [showNew, setShowNew] = useState(false);
  const [modelId, setModelId] = useState<number | ''>('');
  const [ref, setRef] = useState('');
  const [cycle, setCycle] = useState('');
  const [dueDate, setDueDate] = useState('');

  // transition
  const [moveTarget, setMoveTarget] = useState<RecertificationResponse | null>(null);
  const [docsTarget, setDocsTarget] = useState<RecertificationResponse | null>(null);
  const [toStatus, setToStatus] = useState<RecertificationStatus | null>(null);
  const [nextDue, setNextDue] = useState('');
  const [decisionEn, setDecisionEn] = useState('');
  const [decisionAr, setDecisionAr] = useState('');
  const [condEn, setCondEn] = useState('');
  const [condAr, setCondAr] = useState('');
  const [linkedVal, setLinkedVal] = useState<number | ''>('');

  const reload = async () => {
    if (demoMode) return;
    setLoading(true); setError(null);
    try {
      const r = await aiLifecycleApi.listRecerts();
      setRows(r);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تحميل سجل إعادة الاعتماد' : 'Failed to load recertifications'));
    } finally { setLoading(false); }
  };
  useEffect(() => { reload(); /* eslint-disable-next-line */ }, []);

  const submit = async () => {
    if (typeof modelId !== 'number' || !ref.trim()) return;
    setActing(true);
    try {
      await aiLifecycleApi.createRecert({
        modelId, reference: ref.trim(),
        cycleLabel: cycle || undefined,
        dueDate: dueDate || undefined,
      });
      setShowNew(false);
      setModelId(''); setRef(''); setCycle(''); setDueDate('');
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر إنشاء إعادة الاعتماد' : 'Failed to create recertification'));
    } finally { setActing(false); }
  };

  const submitTransition = async () => {
    if (!moveTarget || !toStatus) return;
    setActing(true);
    try {
      await aiLifecycleApi.transitionRecert(moveTarget.id, {
        toStatus,
        nextDueDate: nextDue || undefined,
        decisionRationaleEn: decisionEn || undefined,
        decisionRationaleAr: decisionAr || undefined,
        conditionsEn: condEn || undefined,
        conditionsAr: condAr || undefined,
        linkedValidationId: typeof linkedVal === 'number' ? linkedVal : undefined,
      });
      setMoveTarget(null); setToStatus(null);
      setNextDue(''); setDecisionEn(''); setDecisionAr('');
      setCondEn(''); setCondAr(''); setLinkedVal('');
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تنفيذ التحوّل' : 'Failed to transition'));
    } finally { setActing(false); }
  };

  const cols: Column<RecertificationResponse>[] = [
    { header: lang === 'ar' ? 'المرجع' : 'Reference', width: '170px', mono: true,
      cell: r => <span style={{ fontSize: 12 }}>{r.reference}</span> },
    { header: lang === 'ar' ? 'النموذج' : 'Model', width: '90px',
      cell: r => <span style={{ fontSize: 12 }}>#{r.modelId}</span> },
    { header: lang === 'ar' ? 'الدورة' : 'Cycle', width: '110px',
      cell: r => <span style={{ fontSize: 12 }}>{r.cycleLabel ?? '—'}</span> },
    { header: lang === 'ar' ? 'تاريخ الاستحقاق' : 'Due date', width: '140px',
      cell: r => {
        if (!r.dueDate) return <span style={{ fontSize: 12, opacity: 0.5 }}>—</span>;
        const overdue = new Date(r.dueDate) < new Date() && r.status === 'DUE';
        return overdue
          ? <Badge tone="danger">{r.dueDate}</Badge>
          : <span style={{ fontSize: 12 }}>{r.dueDate}</span>;
      } },
    { header: lang === 'ar' ? 'الحالة' : 'Status', width: '190px',
      cell: r => <Badge tone={RECERT_STATUS_TONE[r.status]}>
        {RECERT_STATUS_LABEL[r.status][lang === 'ar' ? 'ar' : 'en']}</Badge> },
    { header: lang === 'ar' ? 'الاستحقاق القادم' : 'Next due', width: '120px',
      cell: r => <span style={{ fontSize: 12 }}>{r.nextDueDate ?? '—'}</span> },
    { header: '', width: '140px',
      cell: r => {
        const next = nextRecertStatuses(r.status);
        return (
          <span style={{ display: 'inline-flex', gap: 4, justifyContent: 'flex-end' }}>
            <Button variant="ghost" size="sm" Icon={FolderOpen}
              title={lang === 'ar' ? 'المستندات والضوابط' : 'Docs & controls'}
              onClick={(e) => { e.stopPropagation(); setDocsTarget(r); }} />
            {next.length > 0 && (
              <Button variant="ghost" size="sm"
                onClick={(e) => { e.stopPropagation();
                  setMoveTarget(r); setToStatus(next[0]);
                  setNextDue(''); setDecisionEn(''); setDecisionAr('');
                  setCondEn(''); setCondAr(''); setLinkedVal(''); }}>
                {lang === 'ar' ? 'تحريك' : 'Move'}
              </Button>
            )}
          </span>
        );
      } },
  ];

  return (
    <Shell>
      <PageHeader
        eyebrow={lang === 'ar' ? 'دورة حياة الذكاء · المرحلة ٣١' : 'AI Lifecycle · Sprint 31'}
        title={lang === 'ar' ? 'إعادة اعتماد النماذج' : 'Model Recertifications'}
        sub={lang === 'ar'
          ? 'دورة الاعتماد السنوية (سدايا §6.4) — كل نموذج إنتاجي يُعاد اعتماده كل ١٢-٢٤ شهراً'
          : 'Annual recertification cycle (SDAIA §6.4) — every production model recertified every 12–24 months'}
        actions={
          <Button onClick={() => setShowNew(s => !s)} variant="primary" Icon={Plus}>
            {showNew ? (lang === 'ar' ? 'إغلاق' : 'Cancel')
                     : (lang === 'ar' ? 'فتح دورة جديدة' : 'Open recert cycle')}
          </Button>
        } />

      {error && (
        <div style={{ color: 'var(--danger)', fontSize: 13, marginBottom: 12 }}>{error}</div>
      )}

      {showNew && (
        <div style={{ marginBottom: 16 }}>
          <Card padded title={lang === 'ar' ? 'فتح دورة إعادة اعتماد' : 'Open a recertification cycle'}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12 }}>
              <Field label={lang === 'ar' ? 'مُعرّف النموذج *' : 'Model ID *'}>
                <input type="number" value={modelId}
                  onChange={e => setModelId(e.target.value === '' ? '' : Number(e.target.value))}
                  style={inputStyle} placeholder="1" />
              </Field>
              <Field label={lang === 'ar' ? 'المرجع *' : 'Reference *'}>
                <input value={ref} onChange={e => setRef(e.target.value)}
                  style={inputStyle} maxLength={64} placeholder="REC-2026-Q4-CRD" />
              </Field>
              <Field label={lang === 'ar' ? 'تسمية الدورة' : 'Cycle label'}>
                <input value={cycle} onChange={e => setCycle(e.target.value)}
                  style={inputStyle} placeholder="2026-ANNUAL" />
              </Field>
              <Field label={lang === 'ar' ? 'تاريخ الاستحقاق' : 'Due date'}>
                <input type="date" value={dueDate} onChange={e => setDueDate(e.target.value)}
                  style={inputStyle} />
              </Field>
            </div>
            <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
              <Button variant="primary" onClick={submit}
                disabled={acting || typeof modelId !== 'number' || !ref.trim()}>
                {acting ? (lang === 'ar' ? 'جارٍ الإنشاء…' : 'Creating…')
                         : (lang === 'ar' ? 'إنشاء' : 'Create')}
              </Button>
              <Button variant="secondary" onClick={() => setShowNew(false)}>
                {lang === 'ar' ? 'إلغاء' : 'Cancel'}
              </Button>
            </div>
          </Card>
        </div>
      )}

      {moveTarget && toStatus && (
        <div style={{ marginBottom: 16 }}>
          <Card padded title={lang === 'ar' ? `تحريك ${moveTarget.reference}` : `Move ${moveTarget.reference}`}>
            <Field label={lang === 'ar' ? 'الحالة الجديدة' : 'Next status'}>
              <select value={toStatus}
                onChange={e => setToStatus(e.target.value as RecertificationStatus)}
                style={inputStyle}>
                {nextRecertStatuses(moveTarget.status).map(s => (
                  <option key={s} value={s}>
                    {RECERT_STATUS_LABEL[s][lang === 'ar' ? 'ar' : 'en']}
                  </option>
                ))}
              </select>
            </Field>
            {(toStatus === 'RECERTIFIED' || toStatus === 'RECERTIFIED_WITH_CONDITIONS') && (
              <Field label={lang === 'ar' ? 'تاريخ الاستحقاق القادم' : 'Next due date'}>
                <input type="date" value={nextDue} onChange={e => setNextDue(e.target.value)}
                  style={inputStyle} />
              </Field>
            )}
            {toStatus === 'IN_REVIEW' && (
              <Field label={lang === 'ar' ? 'مُعرّف التحقّق المرتبط' : 'Linked validation id'}>
                <input type="number" value={linkedVal}
                  onChange={e => setLinkedVal(e.target.value === '' ? '' : Number(e.target.value))}
                  style={inputStyle} placeholder="3" />
              </Field>
            )}
            <div style={{ display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 12, marginTop: 12 }}>
              <Field label={lang === 'ar' ? 'مبرّر القرار (إنجليزي)' : 'Decision rationale (English)'}>
                <textarea value={decisionEn} onChange={e => setDecisionEn(e.target.value)}
                  rows={3} style={inputStyle} />
              </Field>
              <Field label={lang === 'ar' ? 'مبرّر القرار (عربي)' : 'Decision rationale (Arabic)'}>
                <textarea value={decisionAr} dir="rtl" onChange={e => setDecisionAr(e.target.value)}
                  rows={3} style={inputStyle} />
              </Field>
              {toStatus === 'RECERTIFIED_WITH_CONDITIONS' && (
                <>
                  <Field label={lang === 'ar' ? 'الشروط (إنجليزي)' : 'Conditions (English)'}>
                    <textarea value={condEn} onChange={e => setCondEn(e.target.value)}
                      rows={3} style={inputStyle} />
                  </Field>
                  <Field label={lang === 'ar' ? 'الشروط (عربي)' : 'Conditions (Arabic)'}>
                    <textarea value={condAr} dir="rtl" onChange={e => setCondAr(e.target.value)}
                      rows={3} style={inputStyle} />
                  </Field>
                </>
              )}
            </div>
            <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
              <Button variant="primary" onClick={submitTransition} disabled={acting}>
                {acting ? (lang === 'ar' ? 'جارٍ التنفيذ…' : 'Applying…')
                         : (lang === 'ar' ? 'تنفيذ' : 'Apply')}
              </Button>
              <Button variant="secondary"
                onClick={() => { setMoveTarget(null); setToStatus(null); }}>
                {lang === 'ar' ? 'إلغاء' : 'Cancel'}
              </Button>
            </div>
          </Card>
        </div>
      )}

      <Card padded title={lang === 'ar' ? 'كل دورات إعادة الاعتماد' : 'All recertifications'}>
        {loading ? (
          <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        ) : (
          <DataTable columns={cols} rows={rows}
            empty={<EmptyState Icon={Award}
              title={lang === 'ar' ? 'لا دورات بعد' : 'No recertifications yet'}
              sub={lang === 'ar'
                ? 'افتح أوّل دورة لبدء عملية إعادة الاعتماد.'
                : 'Open the first recert cycle to begin.'} />}
          />
        )}
      </Card>
      <EntityDocsModal
        entityType="AI_RECERTIFICATION"
        entityId={docsTarget?.id ?? null}
        open={!!docsTarget}
        onClose={() => setDocsTarget(null)}
      />
    </Shell>
  );
}

const inputStyle: React.CSSProperties = {
  padding: 6, border: '1px solid var(--border)', borderRadius: 4,
  fontSize: 13, width: '100%', fontFamily: 'inherit',
};

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
      <span style={{ fontSize: 12, fontWeight: 600 }}>{label}</span>
      {children}
    </label>
  );
}
