import { useEffect, useState } from 'react';
import { ScrollText, Plus } from 'lucide-react';
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
  RESOLUTION_STATUS_LABEL, RESOLUTION_STATUS_TONE,
  SUBJECT_AREA_LABEL,
  nextResolutionStatuses,
  type ResolutionResponse, type ResolutionStatus, type SubjectArea,
} from '../../lib/sharia';

const SUBJECT_AREAS: SubjectArea[] = [
  'MURABAHA', 'IJARA', 'TAWARRUQ', 'SUKUK', 'MUDARABA',
  'MUSHARAKA', 'TRANSACTION', 'OPERATIONAL', 'OTHER',
];

/** Sprint 20 PR #3 — SSB resolution register + new-fatwa wizard + transition action. */
export function ShariaResolutionsPage() {
  const { lang } = useI18n();
  const demoMode = !isAuthenticated();

  const [rows, setRows] = useState<ResolutionResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [acting, setActing] = useState(false);

  // New form
  const [showNew, setShowNew] = useState(false);
  const [ref, setRef] = useState('');
  const [subject, setSubject] = useState<SubjectArea>('MURABAHA');
  const [titleEn, setTitleEn] = useState('');
  const [titleAr, setTitleAr] = useState('');
  const [bodyEn, setBodyEn] = useState('');
  const [bodyAr, setBodyAr] = useState('');
  const [rationaleEn, setRationaleEn] = useState('');
  const [rationaleAr, setRationaleAr] = useState('');
  const [signingScholars, setSigningScholars] = useState('');
  const [effectiveDate, setEffectiveDate] = useState('');

  // Transition
  const [transitionTarget, setTransitionTarget] = useState<ResolutionResponse | null>(null);
  const [toStatus, setToStatus] = useState<ResolutionStatus | null>(null);
  const [reasonEn, setReasonEn] = useState('');
  const [reasonAr, setReasonAr] = useState('');

  const reload = async () => {
    if (demoMode) return;
    setLoading(true); setError(null);
    try {
      const r = await shariaApi.listResolutions(0, 200);
      setRows(r.content);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تحميل القرارات' : 'Failed to load resolutions'));
    } finally { setLoading(false); }
  };
  useEffect(() => { reload(); /* eslint-disable-next-line */ }, []);

  const submit = async () => {
    if (!ref.trim() || !titleEn.trim() || !titleAr.trim()) return;
    setActing(true);
    try {
      await shariaApi.createResolution({
        reference: ref.trim(), subjectArea: subject,
        titleEn: titleEn.trim(), titleAr: titleAr.trim(),
        bodyEn: bodyEn || undefined, bodyAr: bodyAr || undefined,
        rationaleEn: rationaleEn || undefined, rationaleAr: rationaleAr || undefined,
        signingScholars: signingScholars || undefined,
        effectiveDate: effectiveDate || undefined,
      });
      setShowNew(false);
      setRef(''); setTitleEn(''); setTitleAr(''); setBodyEn(''); setBodyAr('');
      setRationaleEn(''); setRationaleAr(''); setSigningScholars(''); setEffectiveDate('');
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر إنشاء القرار' : 'Failed to create resolution'));
    } finally { setActing(false); }
  };

  const submitTransition = async () => {
    if (!transitionTarget || !toStatus) return;
    setActing(true);
    try {
      await shariaApi.transitionResolution(transitionTarget.id, {
        toStatus, reasonEn: reasonEn || undefined, reasonAr: reasonAr || undefined,
      });
      setTransitionTarget(null); setToStatus(null);
      setReasonEn(''); setReasonAr('');
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تنفيذ التحوّل' : 'Failed to transition'));
    } finally { setActing(false); }
  };

  const cols: Column<ResolutionResponse>[] = [
    { header: lang === 'ar' ? 'المرجع' : 'Reference', mono: true, width: '130px',
      cell: r => <span style={{ fontSize: 12 }}>{r.reference}</span> },
    { header: lang === 'ar' ? 'العنوان' : 'Title',
      cell: r => <span style={{ fontWeight: 500, fontSize: 13 }}>
        {lang === 'ar' ? r.titleAr : r.titleEn}
      </span> },
    { header: lang === 'ar' ? 'الموضوع' : 'Subject', width: '120px',
      cell: r => <Badge tone="info">
        {SUBJECT_AREA_LABEL[r.subjectArea][lang === 'ar' ? 'ar' : 'en']}
      </Badge> },
    { header: lang === 'ar' ? 'الحالة' : 'Status', width: '140px',
      cell: r => <Badge tone={RESOLUTION_STATUS_TONE[r.status]}>
        {RESOLUTION_STATUS_LABEL[r.status][lang === 'ar' ? 'ar' : 'en']}
      </Badge> },
    { header: lang === 'ar' ? 'تاريخ الاعتماد' : 'Approved', width: '130px',
      cell: r => <span style={{ fontSize: 11 }}>{r.approvedAt?.slice(0, 10) ?? '—'}</span> },
    { header: lang === 'ar' ? 'النفاذ' : 'Effective', width: '130px',
      cell: r => <span style={{ fontSize: 11 }}>{r.effectiveDate ?? '—'}</span> },
    { header: '', width: '110px',
      cell: r => {
        const next = nextResolutionStatuses(r.status);
        if (next.length === 0) return null;
        return (
          <Button variant="ghost" size="sm"
            onClick={(e) => { e.stopPropagation();
              setTransitionTarget(r); setToStatus(next[0]); setReasonEn(''); setReasonAr(''); }}>
            {lang === 'ar' ? 'تحريك' : 'Move'}
          </Button>
        );
      } },
  ];

  return (
    <Shell title={lang === 'ar' ? 'قرارات الهيئة الشرعية' : 'SSB Resolutions'}
      sub={lang === 'ar' ? 'سجل فتاوى الهيئة الشرعية بمسارها من المسوّدة إلى النفاذ.'
                          : 'SSB fatwa register from draft through to active.'}>
      <PageHeader
        eyebrow={lang === 'ar' ? 'الحوكمة الشرعية'
                                : 'Sharia Governance'}
        title={lang === 'ar' ? 'القرارات الشرعية' : 'SSB Resolutions'}
        sub={lang === 'ar' ? 'فتاوى مكتوبة بدليل من القرآن والسنّة والإجماع والقياس'
                            : 'Documented fatwas with Quran / Sunnah / Ijma / Qiyas evidence'}
        actions={
          <Button onClick={() => setShowNew(s => !s)} variant="primary" Icon={Plus}>
            {showNew ? (lang === 'ar' ? 'إغلاق' : 'Cancel')
                      : (lang === 'ar' ? 'قرار جديد' : 'New resolution')}
          </Button>
        }
      />

      {error && (
        <div style={{ color: 'var(--danger)', fontSize: 13, marginBottom: 12 }}>{error}</div>
      )}

      {showNew && (
        <div style={{ marginBottom: 16 }}>
          <Card padded title={lang === 'ar' ? 'مسوّدة فتوى جديدة' : 'Draft a new fatwa'}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12 }}>
              <Field label={lang === 'ar' ? 'المرجع *' : 'Reference *'}>
                <input value={ref} onChange={e => setRef(e.target.value)}
                  style={inputStyle} maxLength={64} placeholder="RES-2026-014" />
              </Field>
              <Field label={lang === 'ar' ? 'الموضوع' : 'Subject area'}>
                <select value={subject} onChange={e => setSubject(e.target.value as SubjectArea)}
                        style={inputStyle}>
                  {SUBJECT_AREAS.map(a => (
                    <option key={a} value={a}>
                      {SUBJECT_AREA_LABEL[a][lang === 'ar' ? 'ar' : 'en']}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label={lang === 'ar' ? 'العنوان (إنجليزي) *' : 'Title (English) *'}>
                <input value={titleEn} onChange={e => setTitleEn(e.target.value)}
                  style={inputStyle} maxLength={512}
                  placeholder="Permissibility of vehicle Murabaha with insurance bundling" />
              </Field>
              <Field label={lang === 'ar' ? 'العنوان (عربي) *' : 'Title (Arabic) *'}>
                <input value={titleAr} dir="rtl" onChange={e => setTitleAr(e.target.value)}
                  style={inputStyle} maxLength={512}
                  placeholder="جواز مرابحة السيارات مع تأمين مدمج" />
              </Field>
              <Field label={lang === 'ar' ? 'الجسم (إنجليزي)' : 'Body (English)'}>
                <textarea value={bodyEn} onChange={e => setBodyEn(e.target.value)}
                  rows={4} style={inputStyle} />
              </Field>
              <Field label={lang === 'ar' ? 'الجسم (عربي)' : 'Body (Arabic)'}>
                <textarea value={bodyAr} dir="rtl" onChange={e => setBodyAr(e.target.value)}
                  rows={4} style={inputStyle} />
              </Field>
              <Field label={lang === 'ar' ? 'الدليل الشرعي (إنجليزي)' : 'Sharia evidence (English)'}>
                <textarea value={rationaleEn} onChange={e => setRationaleEn(e.target.value)}
                  rows={4} style={inputStyle}
                  placeholder="Quran: Al-Baqarah 275. Sunnah: ... Ijma' of the contemporary fiqh academies on cost-plus..." />
              </Field>
              <Field label={lang === 'ar' ? 'الدليل الشرعي (عربي)' : 'Sharia evidence (Arabic)'}>
                <textarea value={rationaleAr} dir="rtl" onChange={e => setRationaleAr(e.target.value)}
                  rows={4} style={inputStyle}
                  placeholder="القرآن: البقرة 275. السنّة: ... إجماع المجامع الفقهية المعاصرة على المرابحة..." />
              </Field>
              <Field label={lang === 'ar' ? 'العلماء الموقّعون (معرّفات)' : 'Signing scholars (ids)'}>
                <input value={signingScholars} onChange={e => setSigningScholars(e.target.value)}
                  style={inputStyle} placeholder="1,2,3" />
              </Field>
              <Field label={lang === 'ar' ? 'تاريخ النفاذ' : 'Effective date'}>
                <input type="date" value={effectiveDate}
                  onChange={e => setEffectiveDate(e.target.value)} style={inputStyle} />
              </Field>
            </div>
            <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
              <Button variant="primary" onClick={submit}
                      disabled={acting || !ref.trim() || !titleEn.trim() || !titleAr.trim()}>
                {acting ? (lang === 'ar' ? 'جارٍ الحفظ…' : 'Saving…')
                         : (lang === 'ar' ? 'إنشاء مسوّدة' : 'Create draft')}
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
            ? `تحريك القرار ${transitionTarget.reference}`
            : `Move resolution ${transitionTarget.reference}`}>
            <Field label={lang === 'ar' ? 'الحالة الجديدة' : 'Next status'}>
              <select value={toStatus}
                onChange={e => setToStatus(e.target.value as ResolutionStatus)}
                style={inputStyle}>
                {nextResolutionStatuses(transitionTarget.status).map(s => (
                  <option key={s} value={s}>
                    {RESOLUTION_STATUS_LABEL[s][lang === 'ar' ? 'ar' : 'en']}
                  </option>
                ))}
              </select>
            </Field>
            <div style={{ display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 12, marginTop: 12 }}>
              <Field label={lang === 'ar' ? 'السبب (إنجليزي)' : 'Reason (English)'}>
                <textarea value={reasonEn} onChange={e => setReasonEn(e.target.value)}
                  rows={3} style={inputStyle} />
              </Field>
              <Field label={lang === 'ar' ? 'السبب (عربي)' : 'Reason (Arabic)'}>
                <textarea value={reasonAr} dir="rtl"
                  onChange={e => setReasonAr(e.target.value)}
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

      <Card padded title={lang === 'ar' ? 'كل القرارات' : 'All resolutions'}>
        {loading ? (
          <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        ) : (
          <DataTable columns={cols} rows={rows}
            empty={<EmptyState Icon={ScrollText}
              title={lang === 'ar' ? 'لا قرارات' : 'No resolutions yet'}
              sub={demoMode
                ? (lang === 'ar' ? 'سجّل دخولك لعرض القرارات.' : 'Log in to view resolutions.')
                : (lang === 'ar'
                  ? 'ابدأ بمسوّدة فتوى أوّليّة.'
                  : 'Draft the first fatwa to begin.')} />}
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
