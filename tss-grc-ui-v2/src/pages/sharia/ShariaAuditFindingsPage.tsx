import { useEffect, useState } from 'react';
import { ShieldAlert, Plus } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';
import {
  shariaApi,
  SEVERITY_LABEL, SEVERITY_TONE,
  FINDING_STATUS_LABEL, FINDING_STATUS_TONE,
  nextFindingStatuses,
  type AuditFindingResponse, type FindingStatus, type Severity,
} from '../../lib/sharia';

const SEVERITIES: Severity[] = ['HIGH', 'MEDIUM', 'LOW'];

/** Sprint 20 PR #3 — Internal Sharia Audit findings with raise + transition. */
export function ShariaAuditFindingsPage() {
  const { lang } = useI18n();
  const demoMode = !isAuthenticated();

  const [rows, setRows] = useState<AuditFindingResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [acting, setActing] = useState(false);

  const [showNew, setShowNew] = useState(false);
  const [ref, setRef] = useState('');
  const [cycle, setCycle] = useState('');
  const [severity, setSeverity] = useState<Severity>('MEDIUM');
  const [titleEn, setTitleEn] = useState('');
  const [titleAr, setTitleAr] = useState('');
  const [descEn, setDescEn] = useState('');
  const [descAr, setDescAr] = useState('');
  const [rootEn, setRootEn] = useState('');
  const [rootAr, setRootAr] = useState('');
  const [planEn, setPlanEn] = useState('');
  const [planAr, setPlanAr] = useState('');
  const [productLine, setProductLine] = useState('');
  const [dueAt, setDueAt] = useState('');

  const [transitionTarget, setTransitionTarget] = useState<AuditFindingResponse | null>(null);
  const [toStatus, setToStatus] = useState<FindingStatus | null>(null);
  const [noteEn, setNoteEn] = useState('');
  const [noteAr, setNoteAr] = useState('');

  const reload = async () => {
    if (demoMode) return;
    setLoading(true); setError(null);
    try {
      const r = await shariaApi.listFindings(0, 200);
      setRows(r.content);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تحميل النتائج' : 'Failed to load findings'));
    } finally { setLoading(false); }
  };
  useEffect(() => { reload(); /* eslint-disable-next-line */ }, []);

  const submit = async () => {
    if (!ref.trim() || !titleEn.trim() || !titleAr.trim()) return;
    setActing(true);
    try {
      await shariaApi.raiseFinding({
        reference: ref.trim(), severity,
        titleEn: titleEn.trim(), titleAr: titleAr.trim(),
        auditCycleLabel: cycle || undefined,
        descriptionEn: descEn || undefined, descriptionAr: descAr || undefined,
        rootCauseEn: rootEn || undefined, rootCauseAr: rootAr || undefined,
        remediationPlanEn: planEn || undefined, remediationPlanAr: planAr || undefined,
        productLine: productLine || undefined,
        remediationDueAt: dueAt || undefined,
      });
      setShowNew(false);
      setRef(''); setCycle(''); setSeverity('MEDIUM');
      setTitleEn(''); setTitleAr(''); setDescEn(''); setDescAr('');
      setRootEn(''); setRootAr(''); setPlanEn(''); setPlanAr('');
      setProductLine(''); setDueAt('');
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر رفع النتيجة' : 'Failed to raise finding'));
    } finally { setActing(false); }
  };

  const submitTransition = async () => {
    if (!transitionTarget || !toStatus) return;
    setActing(true);
    try {
      await shariaApi.transitionFinding(transitionTarget.id, {
        toStatus, noteEn: noteEn || undefined, noteAr: noteAr || undefined,
      });
      setTransitionTarget(null); setToStatus(null);
      setNoteEn(''); setNoteAr('');
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تنفيذ التحوّل' : 'Failed to transition'));
    } finally { setActing(false); }
  };

  const cols: Column<AuditFindingResponse>[] = [
    { header: lang === 'ar' ? 'المرجع' : 'Reference', mono: true, width: '130px',
      cell: f => <span style={{ fontSize: 12 }}>{f.reference}</span> },
    { header: lang === 'ar' ? 'النتيجة' : 'Finding',
      cell: f => <span style={{ fontWeight: 500, fontSize: 13 }}>
        {lang === 'ar' ? f.titleAr : f.titleEn}
      </span> },
    { header: lang === 'ar' ? 'الخطورة' : 'Severity', width: '110px',
      cell: f => <Badge tone={SEVERITY_TONE[f.severity]}>
        {SEVERITY_LABEL[f.severity][lang === 'ar' ? 'ar' : 'en']}
      </Badge> },
    { header: lang === 'ar' ? 'الحالة' : 'Status', width: '140px',
      cell: f => <Badge tone={FINDING_STATUS_TONE[f.status]}>
        {FINDING_STATUS_LABEL[f.status][lang === 'ar' ? 'ar' : 'en']}
      </Badge> },
    { header: lang === 'ar' ? 'دورة المراجعة' : 'Audit cycle', width: '140px',
      cell: f => <span style={{ fontSize: 12 }}>{f.auditCycleLabel ?? '—'}</span> },
    { header: lang === 'ar' ? 'خط المنتج' : 'Product line', width: '150px',
      cell: f => <span style={{ fontSize: 12 }}>{f.productLine ?? '—'}</span> },
    { header: lang === 'ar' ? 'موعد المعالجة' : 'Due', width: '130px',
      cell: f => <span style={{ fontSize: 11 }}>{f.remediationDueAt ?? '—'}</span> },
    { header: '', width: '110px',
      cell: f => {
        const next = nextFindingStatuses(f.status);
        if (next.length === 0) return null;
        return (
          <Button variant="ghost" size="sm"
            onClick={(e) => { e.stopPropagation();
              setTransitionTarget(f); setToStatus(next[0]); setNoteEn(''); setNoteAr(''); }}>
            {lang === 'ar' ? 'تحريك' : 'Move'}
          </Button>
        );
      } },
  ];

  return (
    <Shell title={lang === 'ar' ? 'المراجعة الشرعية الداخلية' : 'Internal Sharia Audit'}
      sub={lang === 'ar' ? 'نتائج المراجعة الشرعية بمراحل المعالجة والتحقّق والإغلاق.'
                          : 'Internal Sharia Audit findings with remediation, verification, closure.'}>
      <PageHeader
        eyebrow={lang === 'ar' ? 'الحوكمة الشرعية'
                                : 'Sharia Governance'}
        title={lang === 'ar' ? 'نتائج المراجعة الشرعية' : 'Sharia Audit Findings'}
        sub={lang === 'ar' ? 'كل نتيجة من نتائج المراجعة الشرعية الدورية'
                            : 'Every finding raised by the Internal Sharia Audit cycle'}
        actions={
          <Button onClick={() => setShowNew(s => !s)} variant="primary" Icon={Plus}>
            {showNew ? (lang === 'ar' ? 'إغلاق' : 'Cancel')
                      : (lang === 'ar' ? 'رفع نتيجة' : 'Raise finding')}
          </Button>
        }
      />

      {error && (
        <div style={{ color: 'var(--danger)', fontSize: 13, marginBottom: 12 }}>{error}</div>
      )}

      {showNew && (
        <div style={{ marginBottom: 16 }}>
          <Card padded title={lang === 'ar' ? 'رفع نتيجة مراجعة شرعية جديدة' : 'Raise a new Sharia audit finding'}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12 }}>
              <Field label={lang === 'ar' ? 'المرجع *' : 'Reference *'}>
                <input value={ref} onChange={e => setRef(e.target.value)}
                  style={inputStyle} maxLength={64} placeholder="SAF-2026-008" />
              </Field>
              <Field label={lang === 'ar' ? 'دورة المراجعة' : 'Audit cycle'}>
                <input value={cycle} onChange={e => setCycle(e.target.value)}
                  style={inputStyle} maxLength={64} placeholder="Sharia-Audit-2026-Q2" />
              </Field>
              <Field label={lang === 'ar' ? 'الخطورة' : 'Severity'}>
                <select value={severity} onChange={e => setSeverity(e.target.value as Severity)}
                        style={inputStyle}>
                  {SEVERITIES.map(s => (
                    <option key={s} value={s}>
                      {SEVERITY_LABEL[s][lang === 'ar' ? 'ar' : 'en']}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label={lang === 'ar' ? 'خط المنتج' : 'Product line'}>
                <input value={productLine} onChange={e => setProductLine(e.target.value)}
                  style={inputStyle} maxLength={128} placeholder="Personal Murabaha Financing" />
              </Field>
              <Field label={lang === 'ar' ? 'العنوان (إنجليزي) *' : 'Title (English) *'}>
                <input value={titleEn} onChange={e => setTitleEn(e.target.value)}
                  style={inputStyle} maxLength={512} />
              </Field>
              <Field label={lang === 'ar' ? 'العنوان (عربي) *' : 'Title (Arabic) *'}>
                <input value={titleAr} dir="rtl" onChange={e => setTitleAr(e.target.value)}
                  style={inputStyle} maxLength={512} />
              </Field>
              <Field label={lang === 'ar' ? 'الوصف (إنجليزي)' : 'Description (English)'}>
                <textarea value={descEn} onChange={e => setDescEn(e.target.value)}
                  rows={3} style={inputStyle} />
              </Field>
              <Field label={lang === 'ar' ? 'الوصف (عربي)' : 'Description (Arabic)'}>
                <textarea value={descAr} dir="rtl" onChange={e => setDescAr(e.target.value)}
                  rows={3} style={inputStyle} />
              </Field>
              <Field label={lang === 'ar' ? 'السبب الجذري (إنجليزي)' : 'Root cause (English)'}>
                <textarea value={rootEn} onChange={e => setRootEn(e.target.value)}
                  rows={3} style={inputStyle} />
              </Field>
              <Field label={lang === 'ar' ? 'السبب الجذري (عربي)' : 'Root cause (Arabic)'}>
                <textarea value={rootAr} dir="rtl" onChange={e => setRootAr(e.target.value)}
                  rows={3} style={inputStyle} />
              </Field>
              <Field label={lang === 'ar' ? 'خطّة المعالجة (إنجليزي)' : 'Remediation plan (English)'}>
                <textarea value={planEn} onChange={e => setPlanEn(e.target.value)}
                  rows={3} style={inputStyle} />
              </Field>
              <Field label={lang === 'ar' ? 'خطّة المعالجة (عربي)' : 'Remediation plan (Arabic)'}>
                <textarea value={planAr} dir="rtl" onChange={e => setPlanAr(e.target.value)}
                  rows={3} style={inputStyle} />
              </Field>
              <Field label={lang === 'ar' ? 'موعد المعالجة' : 'Remediation due'}>
                <input type="date" value={dueAt} onChange={e => setDueAt(e.target.value)}
                  style={inputStyle} />
              </Field>
            </div>
            <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
              <Button variant="primary" onClick={submit}
                      disabled={acting || !ref.trim() || !titleEn.trim() || !titleAr.trim()}>
                {acting ? (lang === 'ar' ? 'جارٍ الرفع…' : 'Raising…')
                         : (lang === 'ar' ? 'رفع' : 'Raise')}
              </Button>
              <Button variant="secondary" onClick={() => setShowNew(false)}>
                {lang === 'ar' ? 'إلغاء' : 'Cancel'}
              </Button>
            </div>
          </Card>
        </div>
      )}

      {transitionTarget && toStatus && (
        <div style={{ marginBottom: 16 }}>
          <Card padded title={lang === 'ar'
            ? `تحريك نتيجة ${transitionTarget.reference}`
            : `Move finding ${transitionTarget.reference}`}>
            <Field label={lang === 'ar' ? 'الحالة الجديدة' : 'Next status'}>
              <select value={toStatus}
                onChange={e => setToStatus(e.target.value as FindingStatus)}
                style={inputStyle}>
                {nextFindingStatuses(transitionTarget.status).map(s => (
                  <option key={s} value={s}>
                    {FINDING_STATUS_LABEL[s][lang === 'ar' ? 'ar' : 'en']}
                  </option>
                ))}
              </select>
            </Field>
            <div style={{ display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 12, marginTop: 12 }}>
              <Field label={lang === 'ar' ? 'ملاحظة (إنجليزي)' : 'Note (English)'}>
                <textarea value={noteEn} onChange={e => setNoteEn(e.target.value)}
                  rows={3} style={inputStyle} />
              </Field>
              <Field label={lang === 'ar' ? 'ملاحظة (عربي)' : 'Note (Arabic)'}>
                <textarea value={noteAr} dir="rtl" onChange={e => setNoteAr(e.target.value)}
                  rows={3} style={inputStyle} />
              </Field>
            </div>
            <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
              <Button variant="primary" onClick={submitTransition} disabled={acting}>
                {acting ? (lang === 'ar' ? 'جارٍ التنفيذ…' : 'Applying…')
                         : (lang === 'ar' ? 'تنفيذ' : 'Apply')}
              </Button>
              <Button variant="secondary"
                onClick={() => { setTransitionTarget(null); setToStatus(null); }}>
                {lang === 'ar' ? 'إلغاء' : 'Cancel'}
              </Button>
            </div>
          </Card>
        </div>
      )}

      <Card padded title={lang === 'ar' ? 'كل النتائج' : 'All findings'}>
        {loading ? (
          <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        ) : (
          <DataTable columns={cols} rows={rows}
            empty={<EmptyState Icon={ShieldAlert}
              title={lang === 'ar' ? 'لا نتائج' : 'No findings yet'}
              sub={lang === 'ar'
                ? 'النتائج المرفوعة من دورات المراجعة الشرعية الداخلية تظهر هنا.'
                : 'Findings raised by Internal Sharia Audit cycles appear here.'} />}
          />
        )}
      </Card>
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
