import { useEffect, useState } from 'react';
import { CalendarDays, Plus } from 'lucide-react';
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
  regreportingApi,
  REGULATOR_LABEL, FREQUENCY_LABEL,
  FILING_STATUS_LABEL, FILING_STATUS_TONE,
  type ObligationResponse, type Regulator, type Frequency,
} from '../../lib/regreporting';

/**
 * Obligation Catalog — /regreporting/obligations.
 *
 * Master list of recurring mandatory returns plus an inline wizard to
 * add a new one. Each row shows code, regulator, frequency, owner
 * role, statutory basis, and the most recent filing's status as a
 * quick at-a-glance health indicator.
 */
const REGULATORS: Regulator[] = ['SAMA', 'NCA', 'CMA', 'SDAIA', 'NCEMA', 'ZATCA', 'OTHER'];
const FREQUENCIES: Frequency[] = [
  'DAILY', 'WEEKLY', 'MONTHLY', 'QUARTERLY', 'SEMIANNUAL', 'ANNUAL', 'AD_HOC',
];
const OWNER_ROLES = [
  'COMPLIANCE_OFFICER', 'CFO', 'CRO', 'CISO', 'DPO',
  'INTERNAL_AUDITOR', 'TREASURY', 'TAX_MANAGER', 'BCM_MANAGER',
];

export function RegReportingObligationsPage() {
  const { lang } = useI18n();
  const demoMode = !isAuthenticated();

  const [rows, setRows] = useState<ObligationResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // New-obligation wizard.
  const [showNew, setShowNew] = useState(false);
  const [creating, setCreating] = useState(false);
  const [code, setCode] = useState('');
  const [regulator, setRegulator] = useState<Regulator>('SAMA');
  const [frequency, setFrequency] = useState<Frequency>('MONTHLY');
  const [titleEn, setTitleEn] = useState('');
  const [titleAr, setTitleAr] = useState('');
  const [statutoryBasis, setStatutoryBasis] = useState('');
  const [ownerRole, setOwnerRole] = useState('COMPLIANCE_OFFICER');
  const [reviewerRole, setReviewerRole] = useState('INTERNAL_AUDITOR');
  const [filingChannel, setFilingChannel] = useState('');
  const [dueOffsetBusinessDays, setDueOffsetBusinessDays] = useState(5);
  const [reminderLadder, setReminderLadder] = useState('7,3,1');
  const [descriptionEn, setDescriptionEn] = useState('');
  const [descriptionAr, setDescriptionAr] = useState('');

  const reload = async () => {
    if (demoMode) return;
    setLoading(true); setError(null);
    try {
      const r = await regreportingApi.listObligations(0, 200);
      setRows(r.content);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تحميل الكتالوج' : 'Failed to load catalog'));
    } finally { setLoading(false); }
  };

  useEffect(() => { reload(); /* eslint-disable-next-line */ }, []);

  const submit = async () => {
    if (!code.trim() || !titleEn.trim() || !titleAr.trim()) return;
    setCreating(true);
    try {
      await regreportingApi.createObligation({
        code: code.trim(),
        regulator,
        frequency,
        titleEn: titleEn.trim(),
        titleAr: titleAr.trim(),
        descriptionEn: descriptionEn || undefined,
        descriptionAr: descriptionAr || undefined,
        dueOffsetBusinessDays,
        statutoryBasis: statutoryBasis || undefined,
        ownerRole,
        reviewerRole,
        filingChannel: filingChannel || undefined,
        reminderLadder,
      });
      setShowNew(false);
      setCode(''); setTitleEn(''); setTitleAr('');
      setStatutoryBasis(''); setFilingChannel('');
      setDescriptionEn(''); setDescriptionAr('');
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر إضافة التقرير' : 'Failed to add obligation'));
    } finally { setCreating(false); }
  };

  const cols: Column<ObligationResponse>[] = [
    {
      header: lang === 'ar' ? 'الرمز' : 'Code',
      width: '140px', mono: true,
      cell: o => <span style={{ fontSize: 12 }}>{o.code}</span>,
    },
    {
      header: lang === 'ar' ? 'الجهة' : 'Regulator', width: '110px',
      cell: o => (
        <Badge tone="info">
          {REGULATOR_LABEL[o.regulator][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'الدورية' : 'Frequency', width: '110px',
      cell: o => (
        <Badge tone="neutral">
          {FREQUENCY_LABEL[o.frequency][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'العنوان' : 'Title',
      cell: o => (
        <span style={{ fontWeight: 500, fontSize: 13 }}>
          {lang === 'ar' ? o.titleAr : o.titleEn}
        </span>
      ),
    },
    {
      header: lang === 'ar' ? 'المالك' : 'Owner', width: '160px',
      cell: o => (
        <span style={{ fontSize: 12 }}>{o.ownerRole || '—'}</span>
      ),
    },
    {
      header: lang === 'ar' ? 'آخر دورة' : 'Latest cycle', width: '180px',
      cell: o => o.latestFilingStatus ? (
        <Badge tone={FILING_STATUS_TONE[o.latestFilingStatus]}>
          {FILING_STATUS_LABEL[o.latestFilingStatus][lang === 'ar' ? 'ar' : 'en']}
          {o.latestFilingDueDate ? ` · ${o.latestFilingDueDate}` : ''}
        </Badge>
      ) : <span style={{ fontSize: 12, color: 'var(--ink-3)' }}>—</span>,
    },
    {
      header: lang === 'ar' ? 'الاستحقاق' : 'Due offset', width: '120px',
      cell: o => (
        <span style={{ fontSize: 12 }}>
          +{o.dueOffsetBusinessDays} {lang === 'ar' ? 'يوم عمل' : 'BD'}
        </span>
      ),
    },
  ];

  return (
    <Shell
      title={lang === 'ar' ? 'كتالوج التقارير المُلزمة'
                            : 'Mandatory Return Catalog'}
      sub={lang === 'ar'
        ? 'قائمة كل التقارير الرقابية المتكرّرة على المؤسسة.'
        : 'Master list of every recurring statutory return.'}
    >
      <PageHeader
        eyebrow={lang === 'ar' ? 'تقويم البلاغات · المرحلة ١٦'
                                : 'Reporting Calendar · Sprint 16'}
        title={lang === 'ar' ? 'كتالوج التقارير' : 'Obligation Catalog'}
        sub={lang === 'ar'
          ? 'إضافة وتعديل التقارير الرقابية المتكرّرة'
          : 'Manage recurring statutory returns'}
        actions={
          <Button onClick={() => setShowNew(s => !s)}
                  variant="primary" Icon={Plus}>
            {showNew
              ? (lang === 'ar' ? 'إغلاق' : 'Cancel')
              : (lang === 'ar' ? 'تقرير جديد' : 'New obligation')}
          </Button>
        }
      />

      {error && (
        <div style={{ color: 'var(--danger)', fontSize: 13, marginBottom: 12 }}>
          {error}
        </div>
      )}

      {showNew && (
        <div style={{ marginBottom: 16 }}>
          <Card padded title={lang === 'ar' ? 'إضافة تقرير جديد للكتالوج'
                                              : 'Add new obligation to catalog'}>
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
              gap: 12,
            }}>
              <Field label={lang === 'ar' ? 'الرمز' : 'Code'}>
                <input value={code} onChange={e => setCode(e.target.value)}
                  placeholder="SAMA-DLR"
                  style={inputStyle} maxLength={64} />
              </Field>
              <Field label={lang === 'ar' ? 'الجهة' : 'Regulator'}>
                <select value={regulator}
                  onChange={e => setRegulator(e.target.value as Regulator)}
                  style={inputStyle}>
                  {REGULATORS.map(r => (
                    <option key={r} value={r}>
                      {REGULATOR_LABEL[r][lang === 'ar' ? 'ar' : 'en']}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label={lang === 'ar' ? 'الدورية' : 'Frequency'}>
                <select value={frequency}
                  onChange={e => setFrequency(e.target.value as Frequency)}
                  style={inputStyle}>
                  {FREQUENCIES.map(f => (
                    <option key={f} value={f}>
                      {FREQUENCY_LABEL[f][lang === 'ar' ? 'ar' : 'en']}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label={lang === 'ar' ? 'فجوة الاستحقاق (يوم عمل)'
                                            : 'Due offset (business days)'}>
                <input type="number" min={0} max={90}
                  value={dueOffsetBusinessDays}
                  onChange={e => setDueOffsetBusinessDays(parseInt(e.target.value, 10) || 0)}
                  style={inputStyle} />
              </Field>
              <Field label={lang === 'ar' ? 'العنوان (إنجليزي)' : 'Title (English)'}>
                <input value={titleEn} onChange={e => setTitleEn(e.target.value)}
                  placeholder="SAMA Daily Liquidity Return" style={inputStyle} maxLength={256} />
              </Field>
              <Field label={lang === 'ar' ? 'العنوان (عربي)' : 'Title (Arabic)'}>
                <input value={titleAr} dir="rtl"
                  onChange={e => setTitleAr(e.target.value)}
                  placeholder="تقرير السيولة اليومي لساما"
                  style={inputStyle} maxLength={256} />
              </Field>
              <Field label={lang === 'ar' ? 'المالك (الدور)' : 'Owner role'}>
                <select value={ownerRole}
                  onChange={e => setOwnerRole(e.target.value)} style={inputStyle}>
                  {OWNER_ROLES.map(r => <option key={r} value={r}>{r}</option>)}
                </select>
              </Field>
              <Field label={lang === 'ar' ? 'المراجِع (الدور)' : 'Reviewer role'}>
                <select value={reviewerRole}
                  onChange={e => setReviewerRole(e.target.value)} style={inputStyle}>
                  {OWNER_ROLES.map(r => <option key={r} value={r}>{r}</option>)}
                </select>
              </Field>
              <Field label={lang === 'ar' ? 'السند النظامي' : 'Statutory basis'}>
                <input value={statutoryBasis}
                  onChange={e => setStatutoryBasis(e.target.value)}
                  placeholder="SAMA Circular 41031843"
                  style={inputStyle} maxLength={256} />
              </Field>
              <Field label={lang === 'ar' ? 'قناة التقديم' : 'Filing channel'}>
                <input value={filingChannel}
                  onChange={e => setFilingChannel(e.target.value)}
                  placeholder="SAMA RBS portal"
                  style={inputStyle} maxLength={256} />
              </Field>
              <Field label={lang === 'ar' ? 'سلسلة التذكيرات' : 'Reminder ladder'}>
                <input value={reminderLadder}
                  onChange={e => setReminderLadder(e.target.value)}
                  placeholder="7,3,1"
                  style={inputStyle} maxLength={64} />
              </Field>
            </div>
            <div style={{
              marginTop: 12,
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
              gap: 12,
            }}>
              <Field label={lang === 'ar' ? 'الوصف (إنجليزي)' : 'Description (English)'}>
                <textarea value={descriptionEn} rows={2}
                  onChange={e => setDescriptionEn(e.target.value)}
                  style={{ ...inputStyle, fontFamily: 'inherit' }} maxLength={4000} />
              </Field>
              <Field label={lang === 'ar' ? 'الوصف (عربي)' : 'Description (Arabic)'}>
                <textarea value={descriptionAr} rows={2} dir="rtl"
                  onChange={e => setDescriptionAr(e.target.value)}
                  style={{ ...inputStyle, fontFamily: 'inherit' }} maxLength={4000} />
              </Field>
            </div>
            <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
              <Button onClick={submit}
                      disabled={creating || !code.trim()
                                || !titleEn.trim() || !titleAr.trim()}
                      variant="primary">
                {creating
                  ? (lang === 'ar' ? 'جارٍ الحفظ…' : 'Saving…')
                  : (lang === 'ar' ? 'إضافة' : 'Add')}
              </Button>
              <Button onClick={() => setShowNew(false)} variant="secondary">
                {lang === 'ar' ? 'إلغاء' : 'Cancel'}
              </Button>
            </div>
          </Card>
        </div>
      )}

      <Card padded title={lang === 'ar' ? 'كل التقارير المُلزمة'
                                          : 'All catalog obligations'}>
        {loading ? (
          <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        ) : (
          <DataTable
            columns={cols}
            rows={rows}
            empty={<EmptyState
              Icon={CalendarDays}
              title={lang === 'ar' ? 'الكتالوج فارغ' : 'Catalog is empty'}
              sub={demoMode
                ? (lang === 'ar' ? 'سجّل دخولك لعرض الكتالوج.' : 'Log in to view the catalog.')
                : (lang === 'ar'
                  ? 'ابدأ بإضافة التقارير المُلزمة التي تتعامل معها مؤسستك.'
                  : 'Start by adding the mandatory returns your firm is required to file.')} />}
          />
        )}
      </Card>
    </Shell>
  );
}

const inputStyle: React.CSSProperties = {
  padding: 6,
  border: '1px solid var(--border)',
  borderRadius: 4,
  fontSize: 13,
};

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
      <span style={{ fontSize: 12, fontWeight: 600 }}>{label}</span>
      {children}
    </label>
  );
}
