import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { FileCheck2 } from 'lucide-react';
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
  CONSENT_STATUS_LABEL, CONSENT_STATUS_TONE,
  CONSENT_TYPE_LABEL,
  type ConsentResponse, type RevokedBy,
  type ConsentType, type TppResponse,
} from '../../lib/openbanking';

// Sensible default OAuth scopes per consent family — prefilled when the
// consent type changes, still editable by the operator.
const SCOPES_BY_TYPE: Record<ConsentType, string> = {
  AIS_ACCOUNT_INFO:    'accounts',
  AIS_BALANCES:        'accounts,balances',
  AIS_TRANSACTIONS:    'accounts,transactions',
  PIS_PAYMENT:         'payments',
  VRP_SETUP:           'payments,vrp',
  CBPII_CONFIRM_FUNDS: 'funds-confirmation',
};

/**
 * Sprint 19 PR #3 — Customer-consent register.
 * Flat view across every TPP: who is still ACTIVE, who has been REVOKED
 * by whom. Revocation triggers AR-244 / AR-245.
 */
export function ObConsentsPage() {
  const { lang } = useI18n();
  const demoMode = !isAuthenticated();

  const [rows, setRows] = useState<ConsentResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [revokeTarget, setRevokeTarget] = useState<ConsentResponse | null>(null);
  const [revokedBy, setRevokedBy] = useState<RevokedBy>('BANK');
  const [reasonEn, setReasonEn] = useState('');
  const [reasonAr, setReasonAr] = useState('');
  const [acting, setActing] = useState(false);

  // Grant-consent form state.
  const [showGrant, setShowGrant] = useState(false);
  const [tpps, setTpps] = useState<TppResponse[]>([]);
  const [gTppId, setGTppId] = useState<number | ''>('');
  const [gType, setGType] = useState<ConsentType>('AIS_ACCOUNT_INFO');
  const [gRef, setGRef] = useState('');
  const [gCustomerHash, setGCustomerHash] = useState('');
  const [gLabelEn, setGLabelEn] = useState('');
  const [gLabelAr, setGLabelAr] = useState('');
  const [gScopes, setGScopes] = useState('');
  const [gExpiresAt, setGExpiresAt] = useState('');
  const [gAmountCap, setGAmountCap] = useState('');
  const [gFreqDays, setGFreqDays] = useState('');
  const [granting, setGranting] = useState(false);

  const reload = async () => {
    if (demoMode) return;
    setLoading(true); setError(null);
    try {
      const r = await obApi.listConsents(0, 500);
      setRows(r.content);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تحميل سجل الموافقات'
                        : 'Failed to load consent register'));
    } finally { setLoading(false); }
  };

  useEffect(() => { reload(); /* eslint-disable-next-line */ }, []);

  const submitRevoke = async () => {
    if (!revokeTarget) return;
    setActing(true); setError(null);
    try {
      await obApi.revokeConsent(revokeTarget.id, {
        revokedBy,
        reasonEn: reasonEn || undefined,
        reasonAr: reasonAr || undefined,
      });
      setRevokeTarget(null);
      setReasonEn(''); setReasonAr(''); setRevokedBy('BANK');
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر سحب الموافقة' : 'Failed to revoke consent'));
    } finally { setActing(false); }
  };

  const openGrant = () => {
    const stamp = Date.now().toString(36).toUpperCase();
    setRevokeTarget(null);
    setShowGrant(true);
    setGTppId(''); setGType('AIS_ACCOUNT_INFO');
    setGRef(`OB-CON-${stamp}`);
    setGCustomerHash(`cust-${stamp.toLowerCase()}`);
    setGLabelEn(''); setGLabelAr('');
    setGScopes(SCOPES_BY_TYPE.AIS_ACCOUNT_INFO);
    setGExpiresAt(''); setGAmountCap(''); setGFreqDays('');
    if (tpps.length === 0) {
      obApi.listTpps(0, 100).then(p => setTpps(p.content)).catch(() => { /* picker stays empty */ });
    }
  };

  const submitGrant = async () => {
    if (!gTppId) {
      setError(lang === 'ar' ? 'اختر مزوّداً نشطاً' : 'Select an active TPP');
      return;
    }
    if (!gRef.trim() || !gCustomerHash.trim() || !gScopes.trim()) {
      setError(lang === 'ar' ? 'المرجع ومعرّف العميل والنطاقات حقول مطلوبة'
                             : 'Reference, customer id and scopes are required');
      return;
    }
    setGranting(true); setError(null);
    try {
      await obApi.grantConsent({
        tppId: gTppId as number,
        consentReference: gRef.trim(),
        customerHash: gCustomerHash.trim(),
        customerLabelEn: gLabelEn.trim() || undefined,
        customerLabelAr: gLabelAr.trim() || undefined,
        consentType: gType,
        scopes: gScopes.trim(),
        expiresAt: gExpiresAt || undefined,
        amountCapSar: gAmountCap ? Number(gAmountCap) : undefined,
        frequencyWindowDays: gFreqDays ? Number(gFreqDays) : undefined,
      });
      setShowGrant(false);
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر منح الموافقة' : 'Failed to grant consent'));
    } finally { setGranting(false); }
  };

  const cols: Column<ConsentResponse>[] = [
    {
      header: lang === 'ar' ? 'المرجع' : 'Reference', mono: true, width: '170px',
      cell: c => <span style={{ fontSize: 12 }}>{c.consentReference}</span>,
    },
    {
      header: lang === 'ar' ? 'المزوّد' : 'TPP', width: '90px',
      cell: c => (
        <Link to={`/openbanking/tpps/${c.tppId}`}
              style={{ fontSize: 12, fontFamily: 'var(--font-mono)' }}>
          #{c.tppId}
        </Link>
      ),
    },
    {
      header: lang === 'ar' ? 'العميل' : 'Customer',
      cell: c => (
        <span style={{ fontSize: 13 }}>
          {(lang === 'ar' ? c.customerLabelAr : c.customerLabelEn) ?? c.customerHash.slice(0, 12) + '…'}
        </span>
      ),
    },
    {
      header: lang === 'ar' ? 'النوع' : 'Type', width: '180px',
      cell: c => (
        <Badge tone="info">
          {CONSENT_TYPE_LABEL[c.consentType][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'الحالة' : 'Status', width: '120px',
      cell: c => (
        <Badge tone={CONSENT_STATUS_TONE[c.status]}>
          {CONSENT_STATUS_LABEL[c.status][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'منح' : 'Granted', width: '130px',
      cell: c => <span style={{ fontSize: 11 }}>{c.grantedAt?.slice(0, 16) ?? '—'}</span>,
    },
    {
      header: lang === 'ar' ? 'انتهاء' : 'Expires', width: '110px',
      cell: c => <span style={{ fontSize: 11 }}>{c.expiresAt ?? '—'}</span>,
    },
    {
      header: lang === 'ar' ? 'سحب' : 'Revoked by', width: '110px',
      cell: c => (
        c.revokedBy
          ? <Badge tone={c.revokedBy === 'CUSTOMER' ? 'warning' : 'neutral'}>
              {revokedByLabel(c.revokedBy, lang)}
            </Badge>
          : <span style={{ fontSize: 12, color: 'var(--ink-3)' }}>—</span>
      ),
    },
    {
      header: lang === 'ar' ? 'استخدام' : 'Calls', width: '80px',
      cell: c => <span style={{ fontSize: 12 }}>{c.callCount ?? 0}</span>,
    },
    {
      header: '', width: '110px',
      cell: c => (
        c.status === 'ACTIVE' || c.status === 'AUTHORISED' ? (
          <Button variant="ghost" size="sm"
            onClick={(e) => { e.stopPropagation(); setRevokeTarget(c); }}>
            {lang === 'ar' ? 'سحب' : 'Revoke'}
          </Button>
        ) : null
      ),
    },
  ];

  return (
    <Shell
      title={lang === 'ar' ? 'سجل الموافقات' : 'Consent register'}
      sub={lang === 'ar'
        ? 'كل موافقات العملاء النشطة وغير النشطة عبر جميع المزوّدين.'
        : 'Every active and historical customer consent across every TPP.'}
    >
      <PageHeader
        eyebrow={lang === 'ar' ? 'المصرفية المفتوحة'
                                : 'Open Banking'}
        title={lang === 'ar' ? 'الموافقات' : 'Consents'}
        sub={lang === 'ar'
          ? 'سجل موافقات العملاء على وصول مزوّدي الخدمة'
          : 'Customer authorisations granted to TPPs'}
        actions={!demoMode ? (
          <Button variant="primary" onClick={openGrant}>
            {lang === 'ar' ? 'منح موافقة' : 'Grant consent'}
          </Button>
        ) : null}
      />

      {showGrant && (
        <div style={{ marginBottom: 16 }}>
          <Card padded title={lang === 'ar' ? 'منح موافقة جديدة' : 'Grant a new consent'}>
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
              gap: 12,
            }}>
              <Field label={lang === 'ar' ? 'المزوّد (نشط) *' : 'TPP (active) *'}>
                <select value={gTppId}
                  onChange={e => setGTppId(e.target.value ? Number(e.target.value) : '')}
                  style={inputStyle}>
                  <option value="">{lang === 'ar' ? '— اختر مزوّداً —' : '— Select a TPP —'}</option>
                  {tpps.filter(t => t.status === 'ACTIVE').map(t => (
                    <option key={t.id} value={t.id}>
                      {((lang === 'ar' ? t.legalNameAr : t.legalNameEn) || t.reference)} (#{t.id})
                    </option>
                  ))}
                </select>
              </Field>
              <Field label={lang === 'ar' ? 'نوع الموافقة *' : 'Consent type *'}>
                <select value={gType}
                  onChange={e => { const t = e.target.value as ConsentType; setGType(t); setGScopes(SCOPES_BY_TYPE[t]); }}
                  style={inputStyle}>
                  {(Object.keys(CONSENT_TYPE_LABEL) as ConsentType[]).map(k => (
                    <option key={k} value={k}>{CONSENT_TYPE_LABEL[k][lang === 'ar' ? 'ar' : 'en']}</option>
                  ))}
                </select>
              </Field>
              <Field label={lang === 'ar' ? 'مرجع الموافقة *' : 'Consent reference *'}>
                <input value={gRef} onChange={e => setGRef(e.target.value)} style={inputStyle} />
              </Field>
              <Field label={lang === 'ar' ? 'معرّف العميل (مجزّأ) *' : 'Customer id (hashed) *'}>
                <input value={gCustomerHash} onChange={e => setGCustomerHash(e.target.value)} style={inputStyle} />
              </Field>
              <Field label={lang === 'ar' ? 'النطاقات *' : 'Scopes *'}>
                <input value={gScopes} onChange={e => setGScopes(e.target.value)} style={inputStyle} />
              </Field>
              <Field label={lang === 'ar' ? 'تسمية العميل (إنجليزي)' : 'Customer label (EN)'}>
                <input value={gLabelEn} onChange={e => setGLabelEn(e.target.value)}
                  style={inputStyle} placeholder="e.g. Retail customer · Riyadh" />
              </Field>
              <Field label={lang === 'ar' ? 'تسمية العميل (عربي)' : 'Customer label (AR)'}>
                <input value={gLabelAr} dir="rtl" onChange={e => setGLabelAr(e.target.value)}
                  style={inputStyle} placeholder="مثال: عميل أفراد · الرياض" />
              </Field>
              <Field label={lang === 'ar' ? 'تاريخ الانتهاء' : 'Expires at'}>
                <input type="date" value={gExpiresAt} onChange={e => setGExpiresAt(e.target.value)} style={inputStyle} />
              </Field>
              <Field label={lang === 'ar' ? 'سقف المبلغ (ريال)' : 'Amount cap (SAR)'}>
                <input type="number" value={gAmountCap} onChange={e => setGAmountCap(e.target.value)}
                  style={inputStyle} placeholder={lang === 'ar' ? '(اختياري)' : '(optional)'} />
              </Field>
              <Field label={lang === 'ar' ? 'نافذة التكرار (أيام)' : 'Frequency window (days)'}>
                <input type="number" value={gFreqDays} onChange={e => setGFreqDays(e.target.value)}
                  style={inputStyle} placeholder={lang === 'ar' ? '(اختياري)' : '(optional)'} />
              </Field>
            </div>
            <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
              <Button variant="primary" onClick={submitGrant} disabled={granting}>
                {granting
                  ? (lang === 'ar' ? 'جارٍ المنح…' : 'Granting…')
                  : (lang === 'ar' ? 'منح الموافقة' : 'Grant consent')}
              </Button>
              <Button variant="secondary" onClick={() => setShowGrant(false)}>
                {lang === 'ar' ? 'إلغاء' : 'Cancel'}
              </Button>
            </div>
          </Card>
        </div>
      )}

      {error && (
        <div style={{ color: 'var(--danger)', fontSize: 13, marginBottom: 12 }}>
          {error}
        </div>
      )}

      {revokeTarget && (
        <div style={{ marginBottom: 16 }}>
          <Card padded
            title={lang === 'ar'
              ? `سحب موافقة ${revokeTarget.consentReference}`
              : `Revoke consent ${revokeTarget.consentReference}`}>
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
              gap: 12,
            }}>
              <Field label={lang === 'ar' ? 'مَن سحب الموافقة؟' : 'Revoked by'}>
                <select value={revokedBy}
                  onChange={e => setRevokedBy(e.target.value as RevokedBy)}
                  style={inputStyle}>
                  <option value="CUSTOMER">{lang === 'ar' ? 'العميل' : 'Customer'}</option>
                  <option value="BANK">{lang === 'ar' ? 'البنك' : 'Bank'}</option>
                  <option value="TPP">{lang === 'ar' ? 'المزوّد' : 'TPP'}</option>
                </select>
              </Field>
              <Field label={lang === 'ar' ? 'السبب (إنجليزي)' : 'Reason (English)'}>
                <textarea value={reasonEn}
                  onChange={e => setReasonEn(e.target.value)}
                  rows={3} style={inputStyle}
                  placeholder="e.g. Customer closed account on mobile" />
              </Field>
              <Field label={lang === 'ar' ? 'السبب (عربي)' : 'Reason (Arabic)'}>
                <textarea value={reasonAr} dir="rtl"
                  onChange={e => setReasonAr(e.target.value)}
                  rows={3} style={inputStyle}
                  placeholder="مثال: أغلق العميل الحساب عبر التطبيق" />
              </Field>
            </div>
            <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
              <Button variant="danger" onClick={submitRevoke} disabled={acting}>
                {acting
                  ? (lang === 'ar' ? 'جارٍ السحب…' : 'Revoking…')
                  : (lang === 'ar' ? 'تأكيد السحب' : 'Confirm revoke')}
              </Button>
              <Button variant="secondary" onClick={() => setRevokeTarget(null)}>
                {lang === 'ar' ? 'إلغاء' : 'Cancel'}
              </Button>
            </div>
          </Card>
        </div>
      )}

      <Card padded
        title={lang === 'ar' ? 'كل الموافقات' : 'All consents'}>
        {loading ? (
          <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        ) : (
          <DataTable
            columns={cols}
            rows={rows}
            empty={<EmptyState Icon={FileCheck2}
              title={lang === 'ar' ? 'لا موافقات' : 'No consents yet'}
              sub={demoMode
                ? (lang === 'ar' ? 'سجّل دخولك لعرض الموافقات.' : 'Log in to view consents.')
                : (lang === 'ar'
                  ? 'ستظهر هنا أيّ موافقة منحها عميل لمزوّد خدمة.'
                  : 'Any consent a customer grants a TPP will appear here.')} />}
          />
        )}
      </Card>
    </Shell>
  );
}

function revokedByLabel(by: RevokedBy, lang: string) {
  if (lang === 'ar') {
    if (by === 'CUSTOMER') return 'العميل';
    if (by === 'BANK')     return 'البنك';
    return 'المزوّد';
  }
  if (by === 'CUSTOMER') return 'Customer';
  if (by === 'BANK')     return 'Bank';
  return 'TPP';
}

const inputStyle: React.CSSProperties = {
  padding: 6,
  border: '1px solid var(--border)',
  borderRadius: 4,
  fontSize: 13,
  width: '100%',
  fontFamily: 'inherit',
};

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
      <span style={{ fontSize: 12, fontWeight: 600 }}>{label}</span>
      {children}
    </label>
  );
}
