import { useEffect, useState } from 'react';
import { Plus, RefreshCw, FolderOpen } from 'lucide-react';
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
  RETRAINING_STATUS_LABEL, RETRAINING_STATUS_TONE,
  RETRAINING_TRIGGER_LABEL,
  nextRetrainingStatuses,
  type RetrainingResponse, type RetrainingStatus, type RetrainingTrigger,
} from '../../lib/aiLifecycle';

const TRIGGERS: RetrainingTrigger[] = ['DRIFT','SCHEDULED','MANUAL','POST_INCIDENT'];

/** Sprint 31 — Model Retraining register. */
export function AiLifecycleRetrainingsPage() {
  const { lang } = useI18n();
  const demoMode = !isAuthenticated();

  const [rows, setRows] = useState<RetrainingResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [acting, setActing] = useState(false);

  // create
  const [showNew, setShowNew] = useState(false);
  const [modelId, setModelId] = useState<number | ''>('');
  const [ref, setRef] = useState('');
  const [trigger, setTrigger] = useState<RetrainingTrigger>('MANUAL');
  const [fromV, setFromV] = useState('');
  const [toV, setToV] = useState('');
  const [rationaleEn, setRationaleEn] = useState('');
  const [rationaleAr, setRationaleAr] = useState('');

  // transition
  const [moveTarget, setMoveTarget] = useState<RetrainingResponse | null>(null);
  const [docsTarget, setDocsTarget] = useState<RetrainingResponse | null>(null);
  const [toStatus, setToStatus] = useState<RetrainingStatus | null>(null);
  const [rollbackEn, setRollbackEn] = useState('');
  const [rollbackAr, setRollbackAr] = useState('');

  const reload = async () => {
    if (demoMode) return;
    setLoading(true); setError(null);
    try {
      const r = await aiLifecycleApi.listRetrainings();
      setRows(r);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تحميل عمليات إعادة التدريب' : 'Failed to load retrainings'));
    } finally { setLoading(false); }
  };
  useEffect(() => { reload(); /* eslint-disable-next-line */ }, []);

  const submit = async () => {
    if (typeof modelId !== 'number' || !ref.trim()) return;
    setActing(true);
    try {
      await aiLifecycleApi.createRetraining({
        modelId, reference: ref.trim(),
        triggerReason: trigger,
        fromVersionLabel: fromV || undefined,
        toVersionLabel: toV || undefined,
        rationaleEn: rationaleEn || undefined,
        rationaleAr: rationaleAr || undefined,
      });
      setShowNew(false);
      setModelId(''); setRef(''); setFromV(''); setToV('');
      setRationaleEn(''); setRationaleAr('');
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر إنشاء عملية إعادة التدريب' : 'Failed to create retraining'));
    } finally { setActing(false); }
  };

  const submitTransition = async () => {
    if (!moveTarget || !toStatus) return;
    setActing(true);
    try {
      await aiLifecycleApi.transitionRetraining(moveTarget.id, {
        toStatus,
        rollbackReasonEn: rollbackEn || undefined,
        rollbackReasonAr: rollbackAr || undefined,
      });
      setMoveTarget(null); setToStatus(null);
      setRollbackEn(''); setRollbackAr('');
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تنفيذ التحوّل' : 'Failed to transition'));
    } finally { setActing(false); }
  };

  const cols: Column<RetrainingResponse>[] = [
    { header: lang === 'ar' ? 'المرجع' : 'Reference', width: '160px', mono: true,
      cell: r => <span style={{ fontSize: 12 }}>{r.reference}</span> },
    { header: lang === 'ar' ? 'النموذج' : 'Model', width: '90px',
      cell: r => <span style={{ fontSize: 12 }}>#{r.modelId}</span> },
    { header: lang === 'ar' ? 'الإصدار' : 'Versions', width: '170px',
      cell: r => <span style={{ fontSize: 12 }}>
        {r.fromVersionLabel ?? '?'} → {r.toVersionLabel ?? '?'}</span> },
    { header: lang === 'ar' ? 'سبب التشغيل' : 'Trigger', width: '120px',
      cell: r => r.triggerReason
        ? <Badge tone={r.triggerReason === 'DRIFT' ? 'warning' : 'info'}>
            {RETRAINING_TRIGGER_LABEL[r.triggerReason][lang === 'ar' ? 'ar' : 'en']}
          </Badge>
        : <span style={{ fontSize: 12, opacity: 0.5 }}>—</span> },
    { header: lang === 'ar' ? 'الحالة' : 'Status', width: '150px',
      cell: r => <Badge tone={RETRAINING_STATUS_TONE[r.status]}>
        {RETRAINING_STATUS_LABEL[r.status][lang === 'ar' ? 'ar' : 'en']}</Badge> },
    { header: '', width: '140px',
      cell: r => {
        const next = nextRetrainingStatuses(r.status);
        return (
          <span style={{ display: 'inline-flex', gap: 4, justifyContent: 'flex-end' }}>
            <Button variant="ghost" size="sm" Icon={FolderOpen}
              title={lang === 'ar' ? 'المستندات والضوابط' : 'Docs & controls'}
              onClick={(e) => { e.stopPropagation(); setDocsTarget(r); }} />
            {next.length > 0 && (
              <Button variant="ghost" size="sm"
                onClick={(e) => { e.stopPropagation();
                  setMoveTarget(r); setToStatus(next[0]);
                  setRollbackEn(''); setRollbackAr(''); }}>
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
        title={lang === 'ar' ? 'إعادة تدريب النماذج' : 'Model Retraining'}
        sub={lang === 'ar'
          ? 'دورات إعادة التدريب — تشغيل من انحراف أو جدولة، اعتماد، نشر، وتراجع عند الفشل'
          : 'Retraining cycles — drift/scheduled trigger, approval, deploy, rollback on failure'}
        actions={
          <Button onClick={() => setShowNew(s => !s)} variant="primary" Icon={Plus}>
            {showNew ? (lang === 'ar' ? 'إغلاق' : 'Cancel')
                     : (lang === 'ar' ? 'إعادة تدريب جديدة' : 'New retraining')}
          </Button>
        } />

      {error && (
        <div style={{ color: 'var(--danger)', fontSize: 13, marginBottom: 12 }}>{error}</div>
      )}

      {showNew && (
        <div style={{ marginBottom: 16 }}>
          <Card padded title={lang === 'ar' ? 'اقتراح إعادة تدريب' : 'Propose a retraining'}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12 }}>
              <Field label={lang === 'ar' ? 'مُعرّف النموذج *' : 'Model ID *'}>
                <input type="number" value={modelId}
                  onChange={e => setModelId(e.target.value === '' ? '' : Number(e.target.value))}
                  style={inputStyle} placeholder="1" />
              </Field>
              <Field label={lang === 'ar' ? 'المرجع *' : 'Reference *'}>
                <input value={ref} onChange={e => setRef(e.target.value)}
                  style={inputStyle} maxLength={64} placeholder="RTR-2026-002" />
              </Field>
              <Field label={lang === 'ar' ? 'سبب التشغيل' : 'Trigger'}>
                <select value={trigger}
                  onChange={e => setTrigger(e.target.value as RetrainingTrigger)}
                  style={inputStyle}>
                  {TRIGGERS.map(t => (
                    <option key={t} value={t}>
                      {RETRAINING_TRIGGER_LABEL[t][lang === 'ar' ? 'ar' : 'en']}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label={lang === 'ar' ? 'من إصدار' : 'From version'}>
                <input value={fromV} onChange={e => setFromV(e.target.value)}
                  style={inputStyle} placeholder="v3.2.1" />
              </Field>
              <Field label={lang === 'ar' ? 'إلى إصدار' : 'To version'}>
                <input value={toV} onChange={e => setToV(e.target.value)}
                  style={inputStyle} placeholder="v3.3.0" />
              </Field>
              <Field label={lang === 'ar' ? 'المبرّر (إنجليزي)' : 'Rationale (English)'}>
                <textarea value={rationaleEn} onChange={e => setRationaleEn(e.target.value)}
                  rows={2} style={inputStyle} />
              </Field>
              <Field label={lang === 'ar' ? 'المبرّر (عربي)' : 'Rationale (Arabic)'}>
                <textarea value={rationaleAr} dir="rtl" onChange={e => setRationaleAr(e.target.value)}
                  rows={2} style={inputStyle} />
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
                onChange={e => setToStatus(e.target.value as RetrainingStatus)}
                style={inputStyle}>
                {nextRetrainingStatuses(moveTarget.status).map(s => (
                  <option key={s} value={s}>
                    {RETRAINING_STATUS_LABEL[s][lang === 'ar' ? 'ar' : 'en']}
                  </option>
                ))}
              </select>
            </Field>
            {toStatus === 'ROLLED_BACK' && (
              <div style={{ display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 12, marginTop: 12 }}>
                <Field label={lang === 'ar' ? 'سبب التراجع (إنجليزي)' : 'Rollback reason (English)'}>
                  <textarea value={rollbackEn} onChange={e => setRollbackEn(e.target.value)}
                    rows={3} style={inputStyle} />
                </Field>
                <Field label={lang === 'ar' ? 'سبب التراجع (عربي)' : 'Rollback reason (Arabic)'}>
                  <textarea value={rollbackAr} dir="rtl" onChange={e => setRollbackAr(e.target.value)}
                    rows={3} style={inputStyle} />
                </Field>
              </div>
            )}
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

      <Card padded title={lang === 'ar' ? 'كل عمليات إعادة التدريب' : 'All retrainings'}>
        {loading ? (
          <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        ) : (
          <DataTable columns={cols} rows={rows}
            empty={<EmptyState Icon={RefreshCw}
              title={lang === 'ar' ? 'لا عمليات بعد' : 'No retrainings yet'}
              sub={lang === 'ar'
                ? 'اقترح أوّل إعادة تدريب لبدء الدورة.'
                : 'Propose the first retraining to start the cycle.'} />}
          />
        )}
      </Card>
      <EntityDocsModal
        entityType="AI_RETRAINING"
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
