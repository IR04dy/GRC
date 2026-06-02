import { useEffect, useState } from 'react';
import { Plus, ClipboardCheck, FolderOpen } from 'lucide-react';
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
  VALIDATION_STATUS_LABEL, VALIDATION_STATUS_TONE,
  nextValidationStatuses,
  type ValidationResponse, type ValidationStatus,
} from '../../lib/aiLifecycle';

/** Sprint 31 — Independent Model Validation register. */
export function AiLifecycleValidationsPage() {
  const { lang } = useI18n();
  const demoMode = !isAuthenticated();

  const [rows, setRows] = useState<ValidationResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [acting, setActing] = useState(false);

  // create
  const [showNew, setShowNew] = useState(false);
  const [modelId, setModelId] = useState<number | ''>('');
  const [ref, setRef] = useState('');
  const [vType, setVType] = useState('INDEPENDENT');
  const [vOrg, setVOrg] = useState('');
  const [scheduled, setScheduled] = useState('');

  // transition
  const [moveTarget, setMoveTarget] = useState<ValidationResponse | null>(null);
  const [docsTarget, setDocsTarget] = useState<ValidationResponse | null>(null);
  const [toStatus, setToStatus] = useState<ValidationStatus | null>(null);
  const [disparity, setDisparity] = useState<number | ''>('');
  const [findingsEn, setFindingsEn] = useState('');
  const [findingsAr, setFindingsAr] = useState('');
  const [conditionsEn, setConditionsEn] = useState('');
  const [conditionsAr, setConditionsAr] = useState('');

  const reload = async () => {
    if (demoMode) return;
    setLoading(true); setError(null);
    try {
      const r = await aiLifecycleApi.listValidations();
      setRows(r);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تحميل التحقّقات' : 'Failed to load validations'));
    } finally { setLoading(false); }
  };
  useEffect(() => { reload(); /* eslint-disable-next-line */ }, []);

  const submit = async () => {
    if (typeof modelId !== 'number' || !ref.trim()) return;
    setActing(true);
    try {
      await aiLifecycleApi.createValidation({
        modelId, reference: ref.trim(),
        validationType: vType || undefined,
        validatorOrgLabel: vOrg || undefined,
        scheduledAt: scheduled || undefined,
      });
      setShowNew(false);
      setModelId(''); setRef(''); setVOrg(''); setScheduled('');
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر إنشاء التحقّق' : 'Failed to create validation'));
    } finally { setActing(false); }
  };

  const submitTransition = async () => {
    if (!moveTarget || !toStatus) return;
    setActing(true);
    try {
      await aiLifecycleApi.transitionValidation(moveTarget.id, {
        toStatus,
        fairnessDisparityPct: typeof disparity === 'number' ? disparity : undefined,
        findingsEn: findingsEn || undefined,
        findingsAr: findingsAr || undefined,
        conditionsEn: conditionsEn || undefined,
        conditionsAr: conditionsAr || undefined,
      });
      setMoveTarget(null); setToStatus(null);
      setDisparity(''); setFindingsEn(''); setFindingsAr('');
      setConditionsEn(''); setConditionsAr('');
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تنفيذ التحوّل' : 'Failed to transition'));
    } finally { setActing(false); }
  };

  const cols: Column<ValidationResponse>[] = [
    { header: lang === 'ar' ? 'المرجع' : 'Reference', width: '160px', mono: true,
      cell: v => <span style={{ fontSize: 12 }}>{v.reference}</span> },
    { header: lang === 'ar' ? 'النموذج' : 'Model', width: '90px',
      cell: v => <span style={{ fontSize: 12 }}>#{v.modelId}</span> },
    { header: lang === 'ar' ? 'المُحقّق' : 'Validator',
      cell: v => <span style={{ fontSize: 12 }}>{v.validatorOrgLabel ?? '—'}</span> },
    { header: lang === 'ar' ? 'النوع' : 'Type', width: '120px',
      cell: v => v.validationType
        ? <Badge tone="neutral">{v.validationType}</Badge>
        : <span style={{ fontSize: 12, opacity: 0.5 }}>—</span> },
    { header: lang === 'ar' ? 'العدالة %' : 'Fairness Δ%', width: '110px',
      cell: v => {
        if (v.fairnessDisparityPct == null) return <span style={{ fontSize: 12, opacity: 0.5 }}>—</span>;
        const d = Number(v.fairnessDisparityPct);
        const tone = d > 10 ? 'danger' : d > 5 ? 'warning' : 'success';
        return <Badge tone={tone}>{d.toFixed(1)}%</Badge>;
      } },
    { header: lang === 'ar' ? 'الحالة' : 'Status', width: '180px',
      cell: v => <Badge tone={VALIDATION_STATUS_TONE[v.status]}>
        {VALIDATION_STATUS_LABEL[v.status][lang === 'ar' ? 'ar' : 'en']}</Badge> },
    { header: '', width: '140px',
      cell: v => {
        const next = nextValidationStatuses(v.status);
        return (
          <span style={{ display: 'inline-flex', gap: 4, justifyContent: 'flex-end' }}>
            <Button variant="ghost" size="sm" Icon={FolderOpen}
              title={lang === 'ar' ? 'المستندات والضوابط' : 'Docs & controls'}
              onClick={(e) => { e.stopPropagation(); setDocsTarget(v); }} />
            {next.length > 0 && (
              <Button variant="ghost" size="sm"
                onClick={(e) => { e.stopPropagation();
                  setMoveTarget(v); setToStatus(next[0]);
                  setDisparity(''); setFindingsEn(''); setFindingsAr('');
                  setConditionsEn(''); setConditionsAr(''); }}>
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
        title={lang === 'ar' ? 'تحقّقات النماذج' : 'Model Validations'}
        sub={lang === 'ar'
          ? 'مبادئ سدايا لأخلاقيات الذكاء + SR 11-7 للتحقّق المستقل — كل تحقّق يحمل قياس عدالة وحالة'
          : 'SDAIA AI Ethics + SR 11-7 independent validation — each carries fairness disparity + outcome'}
        actions={
          <Button onClick={() => setShowNew(s => !s)} variant="primary" Icon={Plus}>
            {showNew ? (lang === 'ar' ? 'إغلاق' : 'Cancel')
                     : (lang === 'ar' ? 'تحقّق جديد' : 'New validation')}
          </Button>
        } />

      {error && (
        <div style={{ color: 'var(--danger)', fontSize: 13, marginBottom: 12 }}>{error}</div>
      )}

      {showNew && (
        <div style={{ marginBottom: 16 }}>
          <Card padded title={lang === 'ar' ? 'جدولة تحقّق جديد' : 'Schedule a new validation'}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12 }}>
              <Field label={lang === 'ar' ? 'مُعرّف النموذج *' : 'Model ID *'}>
                <input type="number" value={modelId}
                  onChange={e => setModelId(e.target.value === '' ? '' : Number(e.target.value))}
                  style={inputStyle} placeholder="1" />
              </Field>
              <Field label={lang === 'ar' ? 'المرجع *' : 'Reference *'}>
                <input value={ref} onChange={e => setRef(e.target.value)}
                  style={inputStyle} maxLength={64} placeholder="VAL-2026-Q2-CRD" />
              </Field>
              <Field label={lang === 'ar' ? 'نوع التحقّق' : 'Validation type'}>
                <select value={vType} onChange={e => setVType(e.target.value)} style={inputStyle}>
                  <option value="INDEPENDENT">INDEPENDENT</option>
                  <option value="INTERNAL">INTERNAL</option>
                  <option value="POST_DEPLOY">POST_DEPLOY</option>
                  <option value="DRIFT_CHECK">DRIFT_CHECK</option>
                </select>
              </Field>
              <Field label={lang === 'ar' ? 'جهة التحقّق' : 'Validator org'}>
                <input value={vOrg} onChange={e => setVOrg(e.target.value)}
                  style={inputStyle} placeholder="Deloitte Saudi Arabia — Model Validation Practice" />
              </Field>
              <Field label={lang === 'ar' ? 'تاريخ الجدولة' : 'Scheduled date'}>
                <input type="date" value={scheduled} onChange={e => setScheduled(e.target.value)}
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
                onChange={e => setToStatus(e.target.value as ValidationStatus)}
                style={inputStyle}>
                {nextValidationStatuses(moveTarget.status).map(s => (
                  <option key={s} value={s}>
                    {VALIDATION_STATUS_LABEL[s][lang === 'ar' ? 'ar' : 'en']}
                  </option>
                ))}
              </select>
            </Field>
            {(toStatus === 'PASSED' || toStatus === 'PASSED_WITH_CONDITIONS' || toStatus === 'FAILED') && (
              <Field label={lang === 'ar' ? 'تفاوت العدالة %' : 'Fairness disparity %'}>
                <input type="number" step="0.1" value={disparity}
                  onChange={e => setDisparity(e.target.value === '' ? '' : Number(e.target.value))}
                  style={inputStyle} placeholder="5.0" />
              </Field>
            )}
            <div style={{ display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 12, marginTop: 12 }}>
              <Field label={lang === 'ar' ? 'النتائج (إنجليزي)' : 'Findings (English)'}>
                <textarea value={findingsEn} onChange={e => setFindingsEn(e.target.value)}
                  rows={3} style={inputStyle} />
              </Field>
              <Field label={lang === 'ar' ? 'النتائج (عربي)' : 'Findings (Arabic)'}>
                <textarea value={findingsAr} dir="rtl" onChange={e => setFindingsAr(e.target.value)}
                  rows={3} style={inputStyle} />
              </Field>
              {toStatus === 'PASSED_WITH_CONDITIONS' && (
                <>
                  <Field label={lang === 'ar' ? 'الشروط (إنجليزي)' : 'Conditions (English)'}>
                    <textarea value={conditionsEn} onChange={e => setConditionsEn(e.target.value)}
                      rows={3} style={inputStyle} />
                  </Field>
                  <Field label={lang === 'ar' ? 'الشروط (عربي)' : 'Conditions (Arabic)'}>
                    <textarea value={conditionsAr} dir="rtl" onChange={e => setConditionsAr(e.target.value)}
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

      <Card padded title={lang === 'ar' ? 'كل التحقّقات' : 'All validations'}>
        {loading ? (
          <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        ) : (
          <DataTable columns={cols} rows={rows}
            empty={<EmptyState Icon={ClipboardCheck}
              title={lang === 'ar' ? 'لا تحقّقات بعد' : 'No validations yet'}
              sub={lang === 'ar'
                ? 'جدوِل أوّل تحقّق مستقل لبدء دورة حياة التحقّق.'
                : 'Schedule the first independent validation.'} />}
          />
        )}
      </Card>
      <EntityDocsModal
        entityType="AI_VALIDATION"
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
