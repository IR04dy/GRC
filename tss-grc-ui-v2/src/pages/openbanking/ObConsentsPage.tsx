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
} from '../../lib/openbanking';

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
          : <span style={{ fontSize: 12, color: 'var(--ink-muted)' }}>—</span>
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
        eyebrow={lang === 'ar' ? 'المصرفية المفتوحة · المرحلة ١٩'
                                : 'Open Banking · Sprint 19'}
        title={lang === 'ar' ? 'الموافقات' : 'Consents'}
        sub={lang === 'ar'
          ? 'سجل موافقات العملاء على وصول مزوّدي الخدمة'
          : 'Customer authorisations granted to TPPs'}
      />

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
