import { useEffect, useState } from 'react';
import { Users, Plus, ShieldCheck } from 'lucide-react';
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
  SSB_ROLE_LABEL, SCHOLAR_STATUS_LABEL, SCHOLAR_STATUS_TONE,
  type ScholarResponse, type SsbRole,
} from '../../lib/sharia';

const ROLES: SsbRole[] = ['CHAIRMAN', 'MEMBER', 'SECRETARY', 'EXTERNAL_ADVISOR'];

/** Sprint 20 PR #3 — SSB scholar register with inline new-scholar wizard + COI declaration. */
export function ShariaScholarsPage() {
  const { lang } = useI18n();
  const demoMode = !isAuthenticated();

  const [rows, setRows] = useState<ScholarResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [showNew, setShowNew] = useState(false);
  const [creating, setCreating] = useState(false);
  const [ref, setRef] = useState('');
  const [nameEn, setNameEn] = useState('');
  const [nameAr, setNameAr] = useState('');
  const [role, setRole] = useState<SsbRole>('MEMBER');
  const [termStart, setTermStart] = useState('');
  const [termEnd, setTermEnd] = useState('');
  const [credEn, setCredEn] = useState('');
  const [credAr, setCredAr] = useState('');

  const [coiTarget, setCoiTarget] = useState<ScholarResponse | null>(null);
  const [coiEn, setCoiEn] = useState('');
  const [coiAr, setCoiAr] = useState('');
  const [acting, setActing] = useState(false);

  const reload = async () => {
    if (demoMode) return;
    setLoading(true); setError(null);
    try {
      const r = await shariaApi.listScholars(0, 200);
      setRows(r.content);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تحميل السجل' : 'Failed to load register'));
    } finally { setLoading(false); }
  };
  useEffect(() => { reload(); /* eslint-disable-next-line */ }, []);

  const reset = () => {
    setRef(''); setNameEn(''); setNameAr(''); setRole('MEMBER');
    setTermStart(''); setTermEnd(''); setCredEn(''); setCredAr('');
  };

  const submit = async () => {
    if (!ref.trim() || !nameEn.trim() || !nameAr.trim()) return;
    setCreating(true);
    try {
      await shariaApi.createScholar({
        reference: ref.trim(), fullNameEn: nameEn.trim(), fullNameAr: nameAr.trim(),
        ssbRole: role,
        termStart: termStart || undefined,
        termEnd: termEnd || undefined,
        credentialsEn: credEn || undefined,
        credentialsAr: credAr || undefined,
      });
      setShowNew(false); reset(); await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر التسجيل' : 'Failed to register scholar'));
    } finally { setCreating(false); }
  };

  const submitCoi = async () => {
    if (!coiTarget) return;
    setActing(true); setError(null);
    try {
      await shariaApi.declareCoi(coiTarget.id, {
        coiNotesEn: coiEn || undefined, coiNotesAr: coiAr || undefined,
      });
      setCoiTarget(null); setCoiEn(''); setCoiAr(''); await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تسجيل الإقرار' : 'Failed to record COI'));
    } finally { setActing(false); }
  };

  // F2-459: `notMeasured` in UX-008's sense -- a figure that is NOT coming, as distinct from
  // one that is not yet computed. There is no SSB meeting-attendance register anywhere in the
  // platform, so this cannot become a number without one.
  const attendanceNotRecorded = lang === 'ar'
    ? 'لا تُسجّل المنصّة حضور اجتماعات الهيئة الشرعية، فلا يمكن قياس هذا الرقم'
    : 'The platform does not record SSB meeting attendance, so this cannot be measured';

  const cols: Column<ScholarResponse>[] = [
    { header: lang === 'ar' ? 'المرجع' : 'Reference', mono: true, width: '130px',
      cell: s => <span style={{ fontSize: 12 }}>{s.reference}</span> },
    { header: lang === 'ar' ? 'الاسم' : 'Name',
      cell: s => <span style={{ fontWeight: 500, fontSize: 13 }}>
        {lang === 'ar' ? s.fullNameAr : s.fullNameEn}
      </span> },
    { header: lang === 'ar' ? 'الدور' : 'Role', width: '170px',
      cell: s => <Badge tone="info">
        {SSB_ROLE_LABEL[s.ssbRole][lang === 'ar' ? 'ar' : 'en']}
      </Badge> },
    { header: lang === 'ar' ? 'الحالة' : 'Status', width: '150px',
      cell: s => <Badge tone={SCHOLAR_STATUS_TONE[s.status]}>
        {SCHOLAR_STATUS_LABEL[s.status][lang === 'ar' ? 'ar' : 'en']}
      </Badge> },
    { header: lang === 'ar' ? 'فترة العضوية' : 'Term', width: '180px',
      cell: s => <span style={{ fontSize: 12 }}>
        {s.termStart ?? '—'} → {s.termEnd ?? '—'}
      </span> },
    { header: lang === 'ar' ? 'تعارض المصالح' : 'COI declared', width: '130px',
      cell: s => <span style={{ fontSize: 12 }}>{s.coiDeclaredAt ?? '—'}</span> },
    /*
     * F2-459: this rendered `s.meetingsAttendedYtd ?? 0` over a column nothing writes and
     * nothing resets, so every scholar showed a frozen seed value under a header claiming a
     * rolling year. Deleting the column would assert nothing at all; rendering 0 would assert
     * that the scholar attended no meetings. UX-008's third state is the honest one -- an
     * em-dash whose reason says the figure CANNOT be measured, not that it is merely not yet
     * computed, because there is no SSB attendance register for it to come from (F2-457).
     * The reason reaches `title` AND `aria-label`: a `title` alone is a tooltip a keyboard or
     * screen-reader user never receives (F2-209), which on an Arabic-primary platform is house
     * rule 7 as much as it is accessibility.
     */
    { header: lang === 'ar' ? 'الحضور هذا العام' : 'Meetings YTD', width: '120px',
      cell: () => (
        <span style={{ fontSize: 12 }} title={attendanceNotRecorded}
              aria-label={attendanceNotRecorded}>&mdash;</span>
      ) },
    { header: '', width: '120px',
      cell: s => (
        <Button variant="ghost" size="sm"
          onClick={(e) => { e.stopPropagation(); setCoiTarget(s); setCoiEn(''); setCoiAr(''); }}>
          <ShieldCheck size={14} style={{ marginInlineEnd: 4 }} />
          {lang === 'ar' ? 'إقرار' : 'COI'}
        </Button>
      ) },
  ];

  return (
    <Shell
      title={lang === 'ar' ? 'الهيئة الشرعية' : 'SSB Scholars'}
      sub={lang === 'ar' ? 'سجل أعضاء الهيئة الشرعية بمعتمداتهم وفترات عضويتهم.'
                          : 'SSB member register with credentials and terms.'}
    >
      <PageHeader
        eyebrow={lang === 'ar' ? 'الحوكمة الشرعية · المرحلة ٢٠'
                                : 'Sharia Governance · Sprint 20'}
        title={lang === 'ar' ? 'أعضاء الهيئة الشرعية' : 'SSB Scholars'}
        sub={lang === 'ar' ? 'كل عضو في الهيئة الشرعية المعتمد لدى ساما'
                            : 'Every SAMA-approved SSB member'}
        actions={
          <Button onClick={() => setShowNew(s => !s)} variant="primary" Icon={Plus}>
            {showNew ? (lang === 'ar' ? 'إغلاق' : 'Cancel')
                      : (lang === 'ar' ? 'تسجيل عضو' : 'Register scholar')}
          </Button>
        }
      />

      {error && (
        <div style={{ color: 'var(--danger)', fontSize: 13, marginBottom: 12 }}>{error}</div>
      )}

      {showNew && (
        <div style={{ marginBottom: 16 }}>
          <Card padded title={lang === 'ar' ? 'تسجيل عضو جديد في الهيئة الشرعية'
                                              : 'Register new SSB scholar'}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12 }}>
              <Field label={lang === 'ar' ? 'المرجع *' : 'Reference *'}>
                <input value={ref} onChange={e => setRef(e.target.value)}
                  style={inputStyle} maxLength={64} placeholder="SCH-AL-MUTLAQ-001" />
              </Field>
              <Field label={lang === 'ar' ? 'الاسم (إنجليزي) *' : 'Full name (English) *'}>
                <input value={nameEn} onChange={e => setNameEn(e.target.value)}
                  style={inputStyle} maxLength={256}
                  placeholder="Sh. Abdullah Al-Mutlaq" />
              </Field>
              <Field label={lang === 'ar' ? 'الاسم (عربي) *' : 'Full name (Arabic) *'}>
                <input value={nameAr} dir="rtl" onChange={e => setNameAr(e.target.value)}
                  style={inputStyle} maxLength={256}
                  placeholder="الشيخ عبدالله المطلق" />
              </Field>
              <Field label={lang === 'ar' ? 'الدور' : 'Role'}>
                <select value={role} onChange={e => setRole(e.target.value as SsbRole)}
                        style={inputStyle}>
                  {ROLES.map(r => (
                    <option key={r} value={r}>
                      {SSB_ROLE_LABEL[r][lang === 'ar' ? 'ar' : 'en']}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label={lang === 'ar' ? 'بداية الفترة' : 'Term start'}>
                <input type="date" value={termStart}
                  onChange={e => setTermStart(e.target.value)} style={inputStyle} />
              </Field>
              <Field label={lang === 'ar' ? 'نهاية الفترة' : 'Term end'}>
                <input type="date" value={termEnd}
                  onChange={e => setTermEnd(e.target.value)} style={inputStyle} />
              </Field>
              <Field label={lang === 'ar' ? 'المعتمدات (إنجليزي)' : 'Credentials (English)'}>
                <textarea value={credEn} onChange={e => setCredEn(e.target.value)}
                  rows={2} style={inputStyle}
                  placeholder="AAOIFI Certified Sharia Adviser; Member, Hai'at Kibar Al-Ulama" />
              </Field>
              <Field label={lang === 'ar' ? 'المعتمدات (عربي)' : 'Credentials (Arabic)'}>
                <textarea value={credAr} dir="rtl" onChange={e => setCredAr(e.target.value)}
                  rows={2} style={inputStyle}
                  placeholder="مستشار شرعي معتمد من أيوفي؛ عضو هيئة كبار العلماء" />
              </Field>
            </div>
            <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
              <Button onClick={submit}
                      disabled={creating || !ref.trim() || !nameEn.trim() || !nameAr.trim()}
                      variant="primary">
                {creating ? (lang === 'ar' ? 'جارٍ التسجيل…' : 'Registering…')
                           : (lang === 'ar' ? 'تسجيل' : 'Register')}
              </Button>
              <Button onClick={() => { setShowNew(false); reset(); }} variant="secondary">
                {lang === 'ar' ? 'إلغاء' : 'Cancel'}
              </Button>
            </div>
          </Card>
        </div>
      )}

      {coiTarget && (
        <div style={{ marginBottom: 16 }}>
          <Card padded title={lang === 'ar'
            ? `إقرار تعارض المصالح — ${coiTarget.fullNameAr}`
            : `COI declaration — ${coiTarget.fullNameEn}`}>
            <div style={{ display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 12 }}>
              <Field label={lang === 'ar' ? 'الملاحظات (إنجليزي)' : 'Notes (English)'}>
                <textarea value={coiEn} onChange={e => setCoiEn(e.target.value)}
                  rows={3} style={inputStyle}
                  placeholder="No conflicts. No directorships in any party related to the bank or its TPPs." />
              </Field>
              <Field label={lang === 'ar' ? 'الملاحظات (عربي)' : 'Notes (Arabic)'}>
                <textarea value={coiAr} dir="rtl" onChange={e => setCoiAr(e.target.value)}
                  rows={3} style={inputStyle}
                  placeholder="لا تعارض. لا عضوية مجلس إدارة في أي جهة مرتبطة بالبنك أو مزوّديه." />
              </Field>
            </div>
            <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
              <Button variant="primary" onClick={submitCoi} disabled={acting}>
                {acting ? (lang === 'ar' ? 'جارٍ الحفظ…' : 'Saving…')
                         : (lang === 'ar' ? 'تسجيل الإقرار' : 'Record COI')}
              </Button>
              <Button variant="secondary" onClick={() => setCoiTarget(null)}>
                {lang === 'ar' ? 'إلغاء' : 'Cancel'}
              </Button>
            </div>
          </Card>
        </div>
      )}

      <Card padded title={lang === 'ar' ? 'كل الأعضاء' : 'All scholars'}>
        {loading ? (
          <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        ) : (
          <DataTable columns={cols} rows={rows}
            empty={<EmptyState Icon={Users}
              title={lang === 'ar' ? 'لا أعضاء' : 'No scholars yet'}
              sub={demoMode
                ? (lang === 'ar' ? 'سجّل دخولك لعرض الأعضاء.' : 'Log in to view scholars.')
                : (lang === 'ar'
                  ? 'سجّل أوّل عضو في الهيئة لبدء الحوكمة الشرعية.'
                  : 'Register the first SSB member to begin Sharia governance.')} />}
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
