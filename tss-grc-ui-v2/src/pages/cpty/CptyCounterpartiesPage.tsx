import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Users, Plus } from 'lucide-react';
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
  cptyApi,
  STATUS_LABEL, STATUS_TONE, SECTOR_LABEL,
  type CounterpartyResponse, type Sector,
} from '../../lib/cpty';

const SECTORS: Sector[] = [
  'SOVEREIGN', 'GOVERNMENT_RELATED_ENTITY', 'FINANCIAL_INSTITUTION',
  'CORPORATE_LARGE', 'CORPORATE_SME', 'RETAIL', 'MULTILATERAL',
];

export function CptyCounterpartiesPage() {
  const { lang } = useI18n();
  const nav = useNavigate();
  const demoMode = !isAuthenticated();

  const [rows, setRows] = useState<CounterpartyResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [showNew, setShowNew] = useState(false);
  const [creating, setCreating] = useState(false);
  const [legalNameEn, setLegalNameEn] = useState('');
  const [legalNameAr, setLegalNameAr] = useState('');
  const [shortName, setShortName] = useState('');
  const [sector, setSector] = useState<Sector>('CORPORATE_LARGE');
  const [country, setCountry] = useState('SA');
  const [industry, setIndustry] = useState('');
  const [parentGroup, setParentGroup] = useState('');
  const [sovereignFlag, setSovereignFlag] = useState(false);
  const [greFlag, setGreFlag] = useState(false);
  const [fiFlag, setFiFlag] = useState(false);
  const [lei, setLei] = useState('');

  const reload = async () => {
    if (demoMode) return;
    setLoading(true); setError(null);
    try {
      const r = await cptyApi.list(0, 200);
      setRows(r.content);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تحميل الأطراف' : 'Failed to load counterparties'));
    } finally { setLoading(false); }
  };

  useEffect(() => { reload(); /* eslint-disable-next-line */ }, []);

  const submit = async () => {
    if (!legalNameEn.trim() || !legalNameAr.trim()) return;
    setCreating(true);
    try {
      const created = await cptyApi.create({
        legalNameEn: legalNameEn.trim(),
        legalNameAr: legalNameAr.trim(),
        shortName: shortName || undefined,
        sector, country: country || undefined,
        industry: industry || undefined,
        parentGroupReference: parentGroup || undefined,
        sovereignFlag, greFlag, fiFlag,
        lei: lei || undefined,
      });
      setShowNew(false);
      setLegalNameEn(''); setLegalNameAr(''); setShortName('');
      setIndustry(''); setParentGroup(''); setLei('');
      setSovereignFlag(false); setGreFlag(false); setFiFlag(false);
      nav(`/cpty/counterparties/${created.id}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر إنشاء الطرف' : 'Failed to register counterparty'));
    } finally { setCreating(false); }
  };

  const cols: Column<CounterpartyResponse>[] = [
    {
      header: lang === 'ar' ? 'المرجع' : 'Reference',
      width: '110px', mono: true,
      cell: c => <span style={{ fontSize: 12 }}>{c.reference}</span>,
    },
    {
      header: lang === 'ar' ? 'الاسم' : 'Name',
      cell: c => (
        <span style={{ fontWeight: 500, fontSize: 13 }}>
          {lang === 'ar' ? c.legalNameAr : c.legalNameEn}
        </span>
      ),
    },
    {
      header: lang === 'ar' ? 'القطاع' : 'Sector', width: '160px',
      cell: c => <Badge tone="neutral">
        {SECTOR_LABEL[c.sector][lang === 'ar' ? 'ar' : 'en']}
      </Badge>,
    },
    {
      header: lang === 'ar' ? 'الحالة' : 'Status', width: '120px',
      cell: c => <Badge tone={STATUS_TONE[c.status]}>
        {STATUS_LABEL[c.status][lang === 'ar' ? 'ar' : 'en']}
      </Badge>,
    },
    {
      header: lang === 'ar' ? 'تصنيف داخلي' : 'Internal grade', width: '100px',
      cell: c => <span style={{ fontSize: 12 }}>{c.currentInternalGrade ?? '—'}</span>,
    },
    {
      header: lang === 'ar' ? 'تصنيف خارجي' : 'External', width: '140px',
      cell: c => (
        <span style={{ fontSize: 12 }}>
          {c.currentExternalRating ? `${c.currentExternalRating} (${c.currentExternalAgency ?? ''})` : '—'}
        </span>
      ),
    },
    {
      header: lang === 'ar' ? 'التعرّض' : 'Exposure', width: '120px',
      cell: c => (
        <span style={{ fontSize: 12 }}>
          {Number(c.totalExposureSarM ?? 0).toLocaleString(
            lang === 'ar' ? 'ar-SA' : 'en-US')}{' '}
          {lang === 'ar' ? 'م.ر' : 'SAR M'}
        </span>
      ),
    },
  ];

  return (
    <Shell
      title={lang === 'ar' ? 'سجل الأطراف المقابلة'
                            : 'Counterparty Register'}
      sub={lang === 'ar' ? 'كل الأطراف الائتمانية النشطة وغير النشطة.'
                          : 'Every credit counterparty active or otherwise.'}
    >
      <PageHeader
        eyebrow={lang === 'ar' ? 'الائتمان'
                                : 'Credit Risk'}
        title={lang === 'ar' ? 'الأطراف المقابلة' : 'Counterparties'}
        sub={lang === 'ar' ? 'سجل الأطراف الائتمانية'
                            : 'Credit counterparty register'}
        actions={
          <Button onClick={() => setShowNew(s => !s)}
                  variant="primary" Icon={Plus}>
            {showNew
              ? (lang === 'ar' ? 'إغلاق' : 'Cancel')
              : (lang === 'ar' ? 'طرف جديد' : 'New counterparty')}
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
          <Card padded title={lang === 'ar' ? 'تسجيل طرف مقابل جديد'
                                              : 'Register new counterparty'}>
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
              gap: 12,
            }}>
              <Field label={lang === 'ar' ? 'الاسم القانوني (إنجليزي)'
                                            : 'Legal name (English)'}>
                <input value={legalNameEn} onChange={e => setLegalNameEn(e.target.value)}
                  style={inputStyle} maxLength={256} placeholder="Saudi Aramco" />
              </Field>
              <Field label={lang === 'ar' ? 'الاسم القانوني (عربي)'
                                            : 'Legal name (Arabic)'}>
                <input value={legalNameAr} dir="rtl"
                  onChange={e => setLegalNameAr(e.target.value)}
                  style={inputStyle} maxLength={256}
                  placeholder="أرامكو السعودية" />
              </Field>
              <Field label={lang === 'ar' ? 'الاسم القصير' : 'Short name'}>
                <input value={shortName} onChange={e => setShortName(e.target.value)}
                  style={inputStyle} maxLength={128} placeholder="Aramco" />
              </Field>
              <Field label={lang === 'ar' ? 'القطاع' : 'Sector'}>
                <select value={sector}
                  onChange={e => setSector(e.target.value as Sector)}
                  style={inputStyle}>
                  {SECTORS.map(s => (
                    <option key={s} value={s}>
                      {SECTOR_LABEL[s][lang === 'ar' ? 'ar' : 'en']}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label={lang === 'ar' ? 'الدولة (ISO-2)' : 'Country (ISO-2)'}>
                <input value={country} onChange={e => setCountry(e.target.value.toUpperCase())}
                  maxLength={2} style={inputStyle} />
              </Field>
              <Field label={lang === 'ar' ? 'الصناعة' : 'Industry'}>
                <input value={industry} onChange={e => setIndustry(e.target.value)}
                  style={inputStyle} maxLength={128}
                  placeholder="Oil & gas" />
              </Field>
              <Field label={lang === 'ar' ? 'الشركة الأم' : 'Parent group ref'}>
                <input value={parentGroup} onChange={e => setParentGroup(e.target.value)}
                  style={inputStyle} maxLength={64} placeholder="ARAMCO_GROUP" />
              </Field>
              <Field label={lang === 'ar' ? 'رقم LEI' : 'LEI'}>
                <input value={lei} onChange={e => setLei(e.target.value)}
                  style={inputStyle} maxLength={20} />
              </Field>
              <Field label={lang === 'ar' ? 'سيادي / حكومي' : 'Sovereign / GRE'}>
                <div style={{ display: 'flex', gap: 12, alignItems: 'center', fontSize: 13 }}>
                  <label style={{ display: 'flex', gap: 4, alignItems: 'center' }}>
                    <input type="checkbox" checked={sovereignFlag}
                      onChange={e => setSovereignFlag(e.target.checked)} />
                    {lang === 'ar' ? 'سيادي' : 'Sovereign'}
                  </label>
                  <label style={{ display: 'flex', gap: 4, alignItems: 'center' }}>
                    <input type="checkbox" checked={greFlag}
                      onChange={e => setGreFlag(e.target.checked)} />
                    {lang === 'ar' ? 'جهة حكومية' : 'GRE'}
                  </label>
                  <label style={{ display: 'flex', gap: 4, alignItems: 'center' }}>
                    <input type="checkbox" checked={fiFlag}
                      onChange={e => setFiFlag(e.target.checked)} />
                    {lang === 'ar' ? 'مؤسّسة مالية' : 'FI'}
                  </label>
                </div>
              </Field>
            </div>
            <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
              <Button onClick={submit}
                      disabled={creating || !legalNameEn.trim() || !legalNameAr.trim()}
                      variant="primary">
                {creating
                  ? (lang === 'ar' ? 'جارٍ التسجيل…' : 'Registering…')
                  : (lang === 'ar' ? 'تسجيل' : 'Register')}
              </Button>
              <Button onClick={() => setShowNew(false)} variant="secondary">
                {lang === 'ar' ? 'إلغاء' : 'Cancel'}
              </Button>
            </div>
          </Card>
        </div>
      )}

      <Card padded title={lang === 'ar' ? 'كل الأطراف' : 'All counterparties'}>
        {loading ? (
          <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        ) : (
          <DataTable
            columns={cols}
            rows={rows}
            rowHref={c => `/cpty/counterparties/${c.id}`}
            empty={<EmptyState Icon={Users}
              title={lang === 'ar' ? 'لا أطراف' : 'No counterparties yet'}
              sub={demoMode
                ? (lang === 'ar' ? 'سجّل دخولك لعرض الأطراف.' : 'Log in to view counterparties.')
                : (lang === 'ar'
                  ? 'ابدأ بتسجيل أوّل طرف مقابل ائتماني.'
                  : 'Register the first credit counterparty.')} />}
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
