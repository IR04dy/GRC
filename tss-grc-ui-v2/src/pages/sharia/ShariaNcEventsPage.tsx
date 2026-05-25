import { useEffect, useState } from 'react';
import { FileWarning, Plus, Banknote } from 'lucide-react';
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
  NC_STATUS_LABEL, NC_STATUS_TONE,
  SEVERITY_LABEL, SEVERITY_TONE,
  DETECTION_SOURCE_LABEL,
  nextNcStatuses,
  type NcEventResponse, type NcStatus, type Severity, type DetectionSource,
} from '../../lib/sharia';

const SEVERITIES: Severity[] = ['HIGH', 'MEDIUM', 'LOW'];
const SOURCES: DetectionSource[] = ['AUDIT', 'SSB_REVIEW', 'CUSTOMER_COMPLAINT', 'OPERATIONS', 'EXTERNAL'];

/** Sprint 20 PR #3 — Sharia NC events + SNCI purification flow. */
export function ShariaNcEventsPage() {
  const { lang } = useI18n();
  const demoMode = !isAuthenticated();

  const [rows, setRows] = useState<NcEventResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [acting, setActing] = useState(false);

  const [showNew, setShowNew] = useState(false);
  const [ref, setRef] = useState('');
  const [severity, setSeverity] = useState<Severity>('MEDIUM');
  const [titleEn, setTitleEn] = useState('');
  const [titleAr, setTitleAr] = useState('');
  const [descEn, setDescEn] = useState('');
  const [descAr, setDescAr] = useState('');
  const [productLine, setProductLine] = useState('');
  const [source, setSource] = useState<DetectionSource>('AUDIT');
  const [snci, setSnci] = useState<number | ''>('');
  const [affected, setAffected] = useState<number | ''>('');

  const [purifyTarget, setPurifyTarget] = useState<NcEventResponse | null>(null);
  const [purifyDest, setPurifyDest] = useState('');
  const [purifyReceipt, setPurifyReceipt] = useState('');
  const [purifyAt, setPurifyAt] = useState('');

  const [transitionTarget, setTransitionTarget] = useState<NcEventResponse | null>(null);
  const [toStatus, setToStatus] = useState<NcStatus | null>(null);
  const [noteEn, setNoteEn] = useState('');
  const [noteAr, setNoteAr] = useState('');

  const reload = async () => {
    if (demoMode) return;
    setLoading(true); setError(null);
    try {
      const r = await shariaApi.listNcEvents(0, 200);
      setRows(r.content);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تحميل المخالفات' : 'Failed to load NC events'));
    } finally { setLoading(false); }
  };
  useEffect(() => { reload(); /* eslint-disable-next-line */ }, []);

  const submit = async () => {
    if (!ref.trim() || !titleEn.trim() || !titleAr.trim()
        || typeof snci !== 'number') return;
    setActing(true);
    try {
      await shariaApi.detectNcEvent({
        reference: ref.trim(), severity,
        titleEn: titleEn.trim(), titleAr: titleAr.trim(),
        descriptionEn: descEn || undefined, descriptionAr: descAr || undefined,
        productLine: productLine || undefined,
        detectionSource: source,
        snciAmountSar: snci,
        affectedCustomerCount: typeof affected === 'number' ? affected : undefined,
      });
      setShowNew(false);
      setRef(''); setSeverity('MEDIUM');
      setTitleEn(''); setTitleAr(''); setDescEn(''); setDescAr('');
      setProductLine(''); setSource('AUDIT'); setSnci(''); setAffected('');
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر رصد المخالفة' : 'Failed to detect NC event'));
    } finally { setActing(false); }
  };

  const submitPurify = async () => {
    if (!purifyTarget || !purifyDest.trim() || !purifyReceipt.trim() || !purifyAt) return;
    setActing(true);
    try {
      await shariaApi.purifyNcEvent(purifyTarget.id, {
        purificationDestination: purifyDest.trim(),
        charityReceiptRef: purifyReceipt.trim(),
        purifiedAt: purifyAt,
      });
      setPurifyTarget(null); setPurifyDest(''); setPurifyReceipt(''); setPurifyAt('');
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تسجيل التطهير' : 'Failed to record purification'));
    } finally { setActing(false); }
  };

  const submitTransition = async () => {
    if (!transitionTarget || !toStatus) return;
    setActing(true);
    try {
      await shariaApi.transitionNcEvent(transitionTarget.id, {
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

  const cols: Column<NcEventResponse>[] = [
    { header: lang === 'ar' ? 'المرجع' : 'Reference', mono: true, width: '130px',
      cell: n => <span style={{ fontSize: 12 }}>{n.reference}</span> },
    { header: lang === 'ar' ? 'الحدث' : 'Event',
      cell: n => <span style={{ fontWeight: 500, fontSize: 13 }}>
        {lang === 'ar' ? n.titleAr : n.titleEn}
      </span> },
    { header: lang === 'ar' ? 'دخل غير شرعي (ر.س)' : 'SNCI (SAR)', width: '160px',
      cell: n => <strong style={{ fontSize: 12 }}>
        {Number(n.snciAmountSar ?? 0).toLocaleString(lang === 'ar' ? 'ar-SA' : 'en-US')}
      </strong> },
    { header: lang === 'ar' ? 'الخطورة' : 'Severity', width: '110px',
      cell: n => <Badge tone={SEVERITY_TONE[n.severity]}>
        {SEVERITY_LABEL[n.severity][lang === 'ar' ? 'ar' : 'en']}
      </Badge> },
    { header: lang === 'ar' ? 'الحالة' : 'Status', width: '140px',
      cell: n => <Badge tone={NC_STATUS_TONE[n.status]}>
        {NC_STATUS_LABEL[n.status][lang === 'ar' ? 'ar' : 'en']}
      </Badge> },
    { header: lang === 'ar' ? 'المصدر' : 'Source', width: '140px',
      cell: n => <span style={{ fontSize: 12 }}>
        {n.detectionSource ? DETECTION_SOURCE_LABEL[n.detectionSource][lang === 'ar' ? 'ar' : 'en'] : '—'}
      </span> },
    { header: lang === 'ar' ? 'تاريخ التطهير' : 'Purified at', width: '120px',
      cell: n => <span style={{ fontSize: 11 }}>{n.purifiedAt ?? '—'}</span> },
    { header: '', width: '180px',
      cell: n => {
        const next = nextNcStatuses(n.status);
        const canPurify = n.status === 'DETECTED' || n.status === 'INVESTIGATION';
        return (
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }} onClick={e => e.stopPropagation()}>
            {canPurify && (
              <Button variant="primary" size="sm"
                onClick={() => { setPurifyTarget(n); setPurifyDest(''); setPurifyReceipt(''); setPurifyAt(''); }}>
                <Banknote size={14} style={{ marginInlineEnd: 4 }} />
                {lang === 'ar' ? 'تطهير' : 'Purify'}
              </Button>
            )}
            {next.length > 0 && (
              <Button variant="ghost" size="sm"
                onClick={() => { setTransitionTarget(n); setToStatus(next[0]);
                  setNoteEn(''); setNoteAr(''); }}>
                {lang === 'ar' ? 'تحريك' : 'Move'}
              </Button>
            )}
          </div>
        );
      } },
  ];

  return (
    <Shell title={lang === 'ar' ? 'المخالفات الشرعية' : 'Sharia NC Events'}
      sub={lang === 'ar' ? 'سجل المخالفات الشرعية وتطهير الدخل غير الشرعي.'
                          : 'Sharia non-compliance events and SNCI purification ledger.'}>
      <PageHeader
        eyebrow={lang === 'ar' ? 'الحوكمة الشرعية · المرحلة ٢٠'
                                : 'Sharia Governance · Sprint 20'}
        title={lang === 'ar' ? 'المخالفات الشرعية' : 'Sharia non-compliance events'}
        sub={lang === 'ar' ? 'تتبّع الدخل غير الشرعي وتطهيره ضمن مهلة 90 يوماً'
                            : 'Track SNCI through to charitable purification within 90 days'}
        actions={
          <Button onClick={() => setShowNew(s => !s)} variant="primary" Icon={Plus}>
            {showNew ? (lang === 'ar' ? 'إغلاق' : 'Cancel')
                      : (lang === 'ar' ? 'رصد مخالفة' : 'Detect NC event')}
          </Button>
        }
      />

      {error && (
        <div style={{ color: 'var(--danger)', fontSize: 13, marginBottom: 12 }}>{error}</div>
      )}

      {showNew && (
        <div style={{ marginBottom: 16 }}>
          <Card padded title={lang === 'ar' ? 'رصد مخالفة شرعية جديدة' : 'Detect a new Sharia non-compliance event'}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12 }}>
              <Field label={lang === 'ar' ? 'المرجع *' : 'Reference *'}>
                <input value={ref} onChange={e => setRef(e.target.value)}
                  style={inputStyle} maxLength={64} placeholder="SNCI-2026-012" />
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
              <Field label={lang === 'ar' ? 'المصدر' : 'Detection source'}>
                <select value={source} onChange={e => setSource(e.target.value as DetectionSource)}
                        style={inputStyle}>
                  {SOURCES.map(s => (
                    <option key={s} value={s}>
                      {DETECTION_SOURCE_LABEL[s][lang === 'ar' ? 'ar' : 'en']}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label={lang === 'ar' ? 'خط المنتج' : 'Product line'}>
                <input value={productLine} onChange={e => setProductLine(e.target.value)}
                  style={inputStyle} maxLength={128} placeholder="Personal Murabaha" />
              </Field>
              <Field label={lang === 'ar' ? 'الدخل غير الشرعي (ر.س) *' : 'SNCI amount (SAR) *'}>
                <input type="number" value={snci}
                  onChange={e => setSnci(e.target.value === '' ? '' : Number(e.target.value))}
                  style={inputStyle} placeholder="18500" />
              </Field>
              <Field label={lang === 'ar' ? 'عملاء متأثّرون' : 'Affected customers'}>
                <input type="number" value={affected}
                  onChange={e => setAffected(e.target.value === '' ? '' : Number(e.target.value))}
                  style={inputStyle} placeholder="0" />
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
            </div>
            <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
              <Button variant="primary" onClick={submit}
                      disabled={acting || !ref.trim() || !titleEn.trim() || !titleAr.trim()
                        || typeof snci !== 'number'}>
                {acting ? (lang === 'ar' ? 'جارٍ الرصد…' : 'Detecting…')
                         : (lang === 'ar' ? 'رصد' : 'Detect')}
              </Button>
              <Button variant="secondary" onClick={() => setShowNew(false)}>
                {lang === 'ar' ? 'إلغاء' : 'Cancel'}
              </Button>
            </div>
          </Card>
        </div>
      )}

      {purifyTarget && (
        <div style={{ marginBottom: 16 }}>
          <Card padded title={lang === 'ar'
            ? `تطهير الدخل — ${purifyTarget.reference} (${Number(purifyTarget.snciAmountSar).toLocaleString('ar-SA')} ر.س)`
            : `Purify SNCI — ${purifyTarget.reference} (${Number(purifyTarget.snciAmountSar).toLocaleString('en-US')} SAR)`}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12 }}>
              <Field label={lang === 'ar' ? 'وجهة التطهير *' : 'Charity destination *'}>
                <input value={purifyDest} onChange={e => setPurifyDest(e.target.value)}
                  style={inputStyle} maxLength={256}
                  placeholder="King Faisal Charity for Orphans" />
              </Field>
              <Field label={lang === 'ar' ? 'مرجع الإيصال *' : 'Charity receipt ref *'}>
                <input value={purifyReceipt} onChange={e => setPurifyReceipt(e.target.value)}
                  style={inputStyle} maxLength={128} placeholder="KFCO-2026-04412" />
              </Field>
              <Field label={lang === 'ar' ? 'تاريخ التطهير *' : 'Purified at *'}>
                <input type="date" value={purifyAt}
                  onChange={e => setPurifyAt(e.target.value)} style={inputStyle} />
              </Field>
            </div>
            <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
              <Button variant="primary" onClick={submitPurify}
                      disabled={acting || !purifyDest.trim() || !purifyReceipt.trim() || !purifyAt}>
                {acting ? (lang === 'ar' ? 'جارٍ التسجيل…' : 'Recording…')
                         : (lang === 'ar' ? 'تسجيل التطهير' : 'Record purification')}
              </Button>
              <Button variant="secondary" onClick={() => setPurifyTarget(null)}>
                {lang === 'ar' ? 'إلغاء' : 'Cancel'}
              </Button>
            </div>
            <div style={{ marginTop: 8, fontSize: 12, color: 'var(--ink-muted)' }}>
              {lang === 'ar'
                ? 'يجب تطهير الدخل غير الشرعي خلال 90 يوماً من الرصد وفق نظام الحوكمة الشرعية لساما §6.4 ومعيار أيوفي FAS 1.'
                : 'SNCI must be purified within 90 days of detection per SAMA §6.4 + AAOIFI FAS 1.'}
            </div>
          </Card>
        </div>
      )}

      {transitionTarget && toStatus && (
        <div style={{ marginBottom: 16 }}>
          <Card padded title={lang === 'ar'
            ? `تحريك ${transitionTarget.reference}`
            : `Move ${transitionTarget.reference}`}>
            <Field label={lang === 'ar' ? 'الحالة الجديدة' : 'Next status'}>
              <select value={toStatus}
                onChange={e => setToStatus(e.target.value as NcStatus)}
                style={inputStyle}>
                {nextNcStatuses(transitionTarget.status).map(s => (
                  <option key={s} value={s}>
                    {NC_STATUS_LABEL[s][lang === 'ar' ? 'ar' : 'en']}
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

      <Card padded title={lang === 'ar' ? 'كل المخالفات' : 'All NC events'}>
        {loading ? (
          <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        ) : (
          <DataTable columns={cols} rows={rows}
            empty={<EmptyState Icon={FileWarning}
              title={lang === 'ar' ? 'لا مخالفات' : 'No NC events yet'}
              sub={lang === 'ar'
                ? 'لا دخل غير شرعي مرصود — الحمد لله.'
                : 'No SNCI events on the register.'} />}
          />
        )}
      </Card>
    </Shell>
  );
}

const inputStyle: React.CSSProperties = {
  padding: 6, border: '1px solid var(--card-border)', borderRadius: 4,
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
