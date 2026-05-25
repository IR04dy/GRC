import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Building2, Plus } from 'lucide-react';
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
  obApi,
  TPP_STATUS_LABEL, TPP_STATUS_TONE,
  type TppResponse,
} from '../../lib/openbanking';

/**
 * Sprint 19 PR #3 — TPP register page.
 * Inline new-TPP wizard for KYB onboarding.
 */
export function ObTppsPage() {
  const { lang } = useI18n();
  const nav = useNavigate();
  const demoMode = !isAuthenticated();

  const [rows, setRows] = useState<TppResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [showNew, setShowNew] = useState(false);
  const [creating, setCreating] = useState(false);
  const [reference, setReference] = useState('');
  const [legalNameEn, setLegalNameEn] = useState('');
  const [legalNameAr, setLegalNameAr] = useState('');
  const [samaLicence, setSamaLicence] = useState('');
  const [fapiClientId, setFapiClientId] = useState('');
  const [aisp, setAisp] = useState(true);
  const [pisp, setPisp] = useState(false);
  const [cbpii, setCbpii] = useState(false);
  const [country, setCountry] = useState('SA');
  const [certFingerprint, setCertFingerprint] = useState('');
  const [certExpiresAt, setCertExpiresAt] = useState('');
  const [rateLimit, setRateLimit] = useState<number | ''>('');

  const reload = async () => {
    if (demoMode) return;
    setLoading(true); setError(null);
    try {
      const r = await obApi.listTpps(0, 200);
      setRows(r.content);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تحميل سجل المزوّدين' : 'Failed to load TPP register'));
    } finally { setLoading(false); }
  };

  useEffect(() => { reload(); /* eslint-disable-next-line */ }, []);

  const reset = () => {
    setReference(''); setLegalNameEn(''); setLegalNameAr('');
    setSamaLicence(''); setFapiClientId(''); setAisp(true); setPisp(false); setCbpii(false);
    setCountry('SA'); setCertFingerprint(''); setCertExpiresAt(''); setRateLimit('');
  };

  const submit = async () => {
    if (!reference.trim() || !legalNameEn.trim() || !legalNameAr.trim()) return;
    const roles = [aisp && 'AISP', pisp && 'PISP', cbpii && 'CBPII']
      .filter(Boolean).join(',');
    if (!roles) {
      setError(lang === 'ar' ? 'يجب اختيار دور واحد على الأقل'
                              : 'Pick at least one role (AISP / PISP / CBPII).');
      return;
    }
    setCreating(true);
    try {
      const created = await obApi.createTpp({
        reference: reference.trim(),
        legalNameEn: legalNameEn.trim(),
        legalNameAr: legalNameAr.trim(),
        samaLicenceRef: samaLicence || undefined,
        fapiClientId: fapiClientId || undefined,
        roles,
        country: country || undefined,
        certFingerprint: certFingerprint || undefined,
        certExpiresAt: certExpiresAt || undefined,
        rateLimitPerSec: typeof rateLimit === 'number' ? rateLimit : undefined,
      });
      setShowNew(false); reset();
      nav(`/openbanking/tpps/${created.id}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تسجيل المزوّد' : 'Failed to register TPP'));
    } finally { setCreating(false); }
  };

  const cols: Column<TppResponse>[] = [
    {
      header: lang === 'ar' ? 'المرجع' : 'Reference', width: '130px', mono: true,
      cell: t => <span style={{ fontSize: 12 }}>{t.reference}</span>,
    },
    {
      header: lang === 'ar' ? 'المزوّد' : 'Provider',
      cell: t => (
        <span style={{ fontWeight: 500, fontSize: 13 }}>
          {lang === 'ar' ? t.legalNameAr : t.legalNameEn}
        </span>
      ),
    },
    {
      header: lang === 'ar' ? 'الأدوار' : 'Roles', width: '150px',
      cell: t => <span style={{ fontSize: 12 }}>{t.roles}</span>,
    },
    {
      header: lang === 'ar' ? 'مرجع ترخيص ساما' : 'SAMA licence', width: '160px',
      cell: t => <span style={{ fontSize: 12 }}>{t.samaLicenceRef ?? '—'}</span>,
    },
    {
      header: lang === 'ar' ? 'الحالة' : 'Status', width: '130px',
      cell: t => (
        <Badge tone={TPP_STATUS_TONE[t.status]}>
          {TPP_STATUS_LABEL[t.status][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'موافقات نشطة' : 'Active consents', width: '110px',
      cell: t => <span style={{ fontSize: 12 }}>{t.activeConsentCount ?? 0}</span>,
    },
    {
      header: lang === 'ar' ? 'حوادث' : 'Incidents', width: '90px',
      cell: t => (
        <span style={{
          fontSize: 12, fontWeight: (t.openIncidentCount ?? 0) > 0 ? 700 : 400,
          color: (t.openIncidentCount ?? 0) > 0 ? 'var(--danger)' : undefined,
        }}>
          {t.openIncidentCount ?? 0}
        </span>
      ),
    },
    {
      header: lang === 'ar' ? 'انتهاء الشهادة' : 'Cert expires', width: '120px',
      cell: t => <span style={{ fontSize: 12 }}>{t.certExpiresAt ?? '—'}</span>,
    },
  ];

  return (
    <Shell
      title={lang === 'ar' ? 'سجل مزوّدي الخدمة' : 'TPP Register'}
      sub={lang === 'ar'
        ? 'مزوّدو الخدمة المصرفية المفتوحة — تسجيل KYB، تعليق، إلغاء.'
        : 'Open-banking TPPs — KYB onboarding, suspension, revocation.'}
    >
      <PageHeader
        eyebrow={lang === 'ar' ? 'المصرفية المفتوحة · المرحلة ١٩'
                                : 'Open Banking · Sprint 19'}
        title={lang === 'ar' ? 'سجل المزوّدين' : 'TPP register'}
        sub={lang === 'ar' ? 'كل مزوّدي الخدمة الذين يصلون إلى واجهاتنا'
                            : 'Every TPP that touches our APIs'}
        actions={
          <Button onClick={() => setShowNew(s => !s)}
                  variant="primary" Icon={Plus}>
            {showNew
              ? (lang === 'ar' ? 'إغلاق' : 'Cancel')
              : (lang === 'ar' ? 'تسجيل مزوّد' : 'Register TPP')}
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
          <Card padded
            title={lang === 'ar' ? 'تسجيل مزوّد جديد (KYB)'
                                  : 'Register new TPP (KYB onboarding)'}>
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
              gap: 12,
            }}>
              <Field label={lang === 'ar' ? 'المرجع الداخلي *' : 'Internal reference *'}>
                <input value={reference} onChange={e => setReference(e.target.value)}
                  style={inputStyle} maxLength={64} placeholder="TPP-LEAN-001" />
              </Field>
              <Field label={lang === 'ar' ? 'الاسم القانوني (إنجليزي) *'
                                            : 'Legal name (English) *'}>
                <input value={legalNameEn} onChange={e => setLegalNameEn(e.target.value)}
                  style={inputStyle} maxLength={256} placeholder="Lean Technologies KSA" />
              </Field>
              <Field label={lang === 'ar' ? 'الاسم القانوني (عربي) *'
                                            : 'Legal name (Arabic) *'}>
                <input value={legalNameAr} dir="rtl"
                  onChange={e => setLegalNameAr(e.target.value)}
                  style={inputStyle} maxLength={256}
                  placeholder="لين تكنولوجيز" />
              </Field>
              <Field label={lang === 'ar' ? 'مرجع ترخيص ساما' : 'SAMA licence ref'}>
                <input value={samaLicence} onChange={e => setSamaLicence(e.target.value)}
                  style={inputStyle} maxLength={64}
                  placeholder="SAMA-OB-AISP-2025-12" />
              </Field>
              <Field label={lang === 'ar' ? 'معرّف FAPI client' : 'FAPI client_id'}>
                <input value={fapiClientId} onChange={e => setFapiClientId(e.target.value)}
                  style={inputStyle} maxLength={128} placeholder="lean-prod-001" />
              </Field>
              <Field label={lang === 'ar' ? 'الدولة (ISO-2)' : 'Country (ISO-2)'}>
                <input value={country} onChange={e => setCountry(e.target.value.toUpperCase())}
                  maxLength={2} style={inputStyle} />
              </Field>
              <Field label={lang === 'ar' ? 'الأدوار' : 'Roles'}>
                <div style={{ display: 'flex', gap: 12, alignItems: 'center', fontSize: 13 }}>
                  <label style={{ display: 'flex', gap: 4, alignItems: 'center' }}>
                    <input type="checkbox" checked={aisp}
                      onChange={e => setAisp(e.target.checked)} />
                    AISP
                  </label>
                  <label style={{ display: 'flex', gap: 4, alignItems: 'center' }}>
                    <input type="checkbox" checked={pisp}
                      onChange={e => setPisp(e.target.checked)} />
                    PISP
                  </label>
                  <label style={{ display: 'flex', gap: 4, alignItems: 'center' }}>
                    <input type="checkbox" checked={cbpii}
                      onChange={e => setCbpii(e.target.checked)} />
                    CBPII
                  </label>
                </div>
              </Field>
              <Field label={lang === 'ar' ? 'بصمة شهادة mTLS' : 'mTLS cert fingerprint'}>
                <input value={certFingerprint} onChange={e => setCertFingerprint(e.target.value)}
                  style={inputStyle} maxLength={128}
                  placeholder="SHA256:ab12cd34…" />
              </Field>
              <Field label={lang === 'ar' ? 'انتهاء الشهادة' : 'Cert expires (YYYY-MM-DD)'}>
                <input type="date" value={certExpiresAt}
                  onChange={e => setCertExpiresAt(e.target.value)}
                  style={inputStyle} />
              </Field>
              <Field label={lang === 'ar' ? 'حدّ المعدّل (طلب/ث)'
                                            : 'Rate-limit (req/s)'}>
                <input type="number" value={rateLimit}
                  onChange={e => setRateLimit(e.target.value === '' ? '' : Number(e.target.value))}
                  style={inputStyle} min={1} placeholder="50" />
              </Field>
            </div>
            <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
              <Button onClick={submit}
                      disabled={creating || !reference.trim()
                        || !legalNameEn.trim() || !legalNameAr.trim()}
                      variant="primary">
                {creating
                  ? (lang === 'ar' ? 'جارٍ التسجيل…' : 'Registering…')
                  : (lang === 'ar' ? 'تسجيل (PENDING_KYB)' : 'Register (PENDING_KYB)')}
              </Button>
              <Button onClick={() => { setShowNew(false); reset(); }} variant="secondary">
                {lang === 'ar' ? 'إلغاء' : 'Cancel'}
              </Button>
            </div>
            <div style={{ marginTop: 8, fontSize: 12, color: 'var(--ink-muted)' }}>
              {lang === 'ar'
                ? 'سيُسجّل المزوّد بالحالة "قيد التحقّق". الاعتماد إلى ACTIVE يتمّ من صفحة التفاصيل بعد إنهاء KYB.'
                : 'TPP enters in PENDING_KYB. Move to ACTIVE from the detail page once KYB is signed off.'}
            </div>
          </Card>
        </div>
      )}

      <Card padded
        title={lang === 'ar' ? 'كل المزوّدين' : 'All TPPs'}>
        {loading ? (
          <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        ) : (
          <DataTable
            columns={cols}
            rows={rows}
            rowHref={t => `/openbanking/tpps/${t.id}`}
            empty={<EmptyState Icon={Building2}
              title={lang === 'ar' ? 'لا مزوّدون' : 'No TPPs yet'}
              sub={demoMode
                ? (lang === 'ar' ? 'سجّل دخولك لعرض المزوّدين.' : 'Log in to view TPPs.')
                : (lang === 'ar'
                  ? 'سجّل أوّل مزوّد لتشغيل المسار التشغيلي.'
                  : 'Register the first TPP to start the workflow.')} />}
          />
        )}
      </Card>
    </Shell>
  );
}

const inputStyle: React.CSSProperties = {
  padding: 6,
  border: '1px solid var(--card-border)',
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
