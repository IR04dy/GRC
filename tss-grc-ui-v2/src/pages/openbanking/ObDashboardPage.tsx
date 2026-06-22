import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Building2, ShieldAlert, FileCheck2, AlertOctagon, Activity, KeyRound,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';
import {
  obApi,
  TPP_STATUS_LABEL, TPP_STATUS_TONE,
  CONSENT_TYPE_LABEL,
  INCIDENT_TYPE_LABEL, INCIDENT_STATUS_LABEL, INCIDENT_STATUS_TONE,
  SEVERITY_LABEL, SEVERITY_TONE,
  type DashboardResponse, type TppResponse, type IncidentResponse,
  type TppStatus, type ConsentType,
} from '../../lib/openbanking';

/**
 * Sprint 19 PR #3 — Open Banking dashboard.
 * SAMA Open Banking Framework v2 (AISP+PISP+CBPII+VRP).
 * Compliance Officer + OB Officer landing: TPPs, consents, incidents, certs.
 */
export function ObDashboardPage() {
  const { lang } = useI18n();
  const nav = useNavigate();
  const demoMode = !isAuthenticated();

  const [data, setData] = useState<DashboardResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (demoMode) return;
    let cancelled = false;
    (async () => {
      setLoading(true); setError(null);
      try {
        const r = await obApi.dashboard();
        if (!cancelled) setData(r);
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof ApiError ? err.message :
            (lang === 'ar' ? 'تعذّر تحميل لوحة المصرفية المفتوحة'
                            : 'Failed to load open-banking dashboard'));
        }
      } finally { if (!cancelled) setLoading(false); }
    })();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const tppCols: Column<TppResponse>[] = [
    {
      header: lang === 'ar' ? 'المرجع' : 'Reference',
      width: '120px', mono: true,
      cell: t => <span style={{ fontSize: 12 }}>{t.reference}</span>,
    },
    {
      header: lang === 'ar' ? 'المزوّد' : 'Provider',
      cell: t => (
        <span style={{ fontWeight: 500, fontSize: 13 }}>
          {lang === 'ar' ? t.legalNameAr : t.legalNameEn}
          {t.roles && (
            <Badge tone="info" className="m-l-6">{t.roles}</Badge>
          )}
        </span>
      ),
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
      header: lang === 'ar' ? 'موافقات نشطة' : 'Active consents', width: '120px',
      cell: t => <span style={{ fontSize: 12 }}>{t.activeConsentCount ?? 0}</span>,
    },
    {
      header: lang === 'ar' ? 'حوادث مفتوحة' : 'Open incidents', width: '120px',
      cell: t => (
        <span style={{ fontSize: 12,
          fontWeight: (t.openIncidentCount ?? 0) > 0 ? 700 : 400,
          color: (t.openIncidentCount ?? 0) > 0 ? 'var(--danger)' : undefined }}>
          {t.openIncidentCount ?? 0}
        </span>
      ),
    },
  ];

  const incidentCols: Column<IncidentResponse>[] = [
    {
      header: lang === 'ar' ? 'المرجع' : 'Reference', width: '130px', mono: true,
      cell: i => <span style={{ fontSize: 12 }}>{i.reference}</span>,
    },
    {
      header: lang === 'ar' ? 'العنوان' : 'Title',
      cell: i => (
        <span style={{ fontWeight: 500, fontSize: 13 }}>
          {lang === 'ar' ? i.titleAr : i.titleEn}
          {i.samaReportable && (
            <Badge tone="danger" className="m-l-6">
              {lang === 'ar' ? 'بلاغ ساما' : 'SAMA-reportable'}
            </Badge>
          )}
        </span>
      ),
    },
    {
      header: lang === 'ar' ? 'النوع' : 'Type', width: '170px',
      cell: i => (
        <Badge tone="neutral">
          {INCIDENT_TYPE_LABEL[i.incidentType][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'الخطورة' : 'Severity', width: '110px',
      cell: i => (
        <Badge tone={SEVERITY_TONE[i.severity]}>
          {SEVERITY_LABEL[i.severity][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'الحالة' : 'Status', width: '130px',
      cell: i => (
        <Badge tone={INCIDENT_STATUS_TONE[i.status]}>
          {INCIDENT_STATUS_LABEL[i.status][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
  ];

  return (
    <Shell
      title={lang === 'ar' ? 'المصرفية المفتوحة' : 'Open Banking'}
      sub={lang === 'ar'
        ? 'سجل مزوّدي الخدمة، الموافقات، حوادث الواجهات، والتزامات ساما الزمنية.'
        : 'TPP register, customer consents, API incidents, and SAMA-deadline obligations.'}
    >
      <PageHeader
        eyebrow={lang === 'ar' ? 'المصرفية المفتوحة · المرحلة ١٩'
                                : 'Open Banking · Sprint 19'}
        title={lang === 'ar' ? 'لوحة المصرفية المفتوحة'
                              : 'Open Banking Compliance Dashboard'}
        sub={lang === 'ar'
          ? 'مزوّدو الخدمة، الموافقات، والحوادث الخاصّة بإطار ساما للمصرفية المفتوحة'
          : 'TPPs, consents, and OB-specific incidents under SAMA OBF v2'}
        actions={
          <>
            <Button onClick={() => nav('/openbanking/tpps')}
                    variant="primary" Icon={Building2}>
              {lang === 'ar' ? 'سجل المزوّدين' : 'TPP register'}
            </Button>
            <Button onClick={() => nav('/openbanking/incidents')}
                    variant="secondary" Icon={AlertOctagon}>
              {lang === 'ar' ? 'الحوادث' : 'Incidents'}
            </Button>
          </>
        }
      />

      {error && (
        <div style={{ color: 'var(--danger)', fontSize: 13, marginBottom: 12 }}>
          {error}
        </div>
      )}

      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
        gap: 12, marginBottom: 16,
      }}>
        <Kpi Icon={Building2} tone="success"
          label={lang === 'ar' ? 'مزوّدون نشطون' : 'Active TPPs'}
          value={data?.activeTpps ?? 0}
          to="/openbanking/tpps" />
        <Kpi Icon={ShieldAlert} tone="warning"
          label={lang === 'ar' ? 'موقوفون' : 'Suspended'}
          value={data?.suspendedTpps ?? 0}
          to="/openbanking/tpps" />
        <Kpi Icon={FileCheck2} tone="info"
          label={lang === 'ar' ? 'موافقات نشطة' : 'Active consents'}
          value={data?.activeConsents ?? 0}
          to="/openbanking/consents" />
        <Kpi Icon={AlertOctagon} tone={(data?.criticalOpenIncidents ?? 0) > 0 ? 'danger' : 'warning'}
          label={lang === 'ar' ? 'حوادث مفتوحة' : 'Open incidents'}
          value={data?.openIncidents ?? 0}
          to="/openbanking/incidents" />
        <Kpi Icon={AlertOctagon} tone="danger"
          label={lang === 'ar' ? 'بلاغ ساما مفتوح' : 'SAMA-reportable open'}
          value={data?.samaReportableOpen ?? 0}
          to="/openbanking/incidents" />
        <Kpi Icon={KeyRound} tone="warning"
          label={lang === 'ar' ? 'شهادات تنتهي < ٣٠ ي' : 'Certs expiring < 30d'}
          value={data?.certExpiring30d ?? 0}
          to="/openbanking/tpps" />
      </div>

      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
        gap: 12, marginBottom: 16,
      }}>
        <Card padded
          title={lang === 'ar' ? 'المزوّدون حسب الحالة' : 'TPPs by status'}>
          {loading ? <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty> : (
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
              {Object.entries(data?.tppsByStatus ?? {}).map(([k, v]) => (
                <Badge key={k} tone={TPP_STATUS_TONE[k as TppStatus] ?? 'neutral'}>
                  {TPP_STATUS_LABEL[k as TppStatus]?.[lang === 'ar' ? 'ar' : 'en'] ?? k}: {v}
                </Badge>
              ))}
              {Object.keys(data?.tppsByStatus ?? {}).length === 0 && (
                <span style={{ fontSize: 13, color: 'var(--ink-3)' }}>
                  {lang === 'ar' ? 'لم يُسجَّل مزوّدون بعد.' : 'No TPPs registered yet.'}
                </span>
              )}
            </div>
          )}
        </Card>

        <Card padded
          title={lang === 'ar' ? 'الموافقات حسب النوع' : 'Consents by type'}>
          {loading ? <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty> : (
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
              {Object.entries(data?.consentsByType ?? {}).map(([k, v]) => (
                <Badge key={k} tone="info">
                  {CONSENT_TYPE_LABEL[k as ConsentType]?.[lang === 'ar' ? 'ar' : 'en'] ?? k}: {v}
                </Badge>
              ))}
              {Object.keys(data?.consentsByType ?? {}).length === 0 && (
                <span style={{ fontSize: 13, color: 'var(--ink-3)' }}>
                  {lang === 'ar' ? 'لا موافقات بعد.' : 'No consents yet.'}
                </span>
              )}
            </div>
          )}
        </Card>

        <Card padded
          title={lang === 'ar' ? 'صحّة الواجهات (٢٤ساعة)' : 'API health (24h)'}>
          {loading ? <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty> : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              <span style={{ fontSize: 13 }}>
                <Activity size={14} style={{ verticalAlign: 'text-bottom', marginInlineEnd: 4 }} />
                {lang === 'ar' ? 'نداءات' : 'Calls'}:{' '}
                <strong>
                  {(data?.apiCalls24h ?? 0).toLocaleString(lang === 'ar' ? 'ar-SA' : 'en-US')}
                </strong>
              </span>
              <span style={{ fontSize: 13 }}>
                <AlertOctagon size={14} style={{ verticalAlign: 'text-bottom', marginInlineEnd: 4,
                  color: (data?.apiErrors24h ?? 0) > 0 ? 'var(--danger)' : undefined }} />
                {lang === 'ar' ? 'أخطاء' : 'Errors'}:{' '}
                <strong style={{ color: (data?.apiErrors24h ?? 0) > 0 ? 'var(--danger)' : undefined }}>
                  {(data?.apiErrors24h ?? 0).toLocaleString(lang === 'ar' ? 'ar-SA' : 'en-US')}
                </strong>
              </span>
              <span style={{ fontSize: 13 }}>
                {lang === 'ar' ? 'سحب موافقات آخر ٢٤س' : 'Consent revocations 24h'}:{' '}
                <strong>{data?.revokedConsents24h ?? 0}</strong>
              </span>
            </div>
          )}
        </Card>
      </div>

      <Card padded
        title={lang === 'ar' ? 'أحدث المزوّدين' : 'Recent TPPs'}>
        {loading ? (
          <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        ) : (
          <DataTable
            columns={tppCols}
            rows={data?.recentTpps ?? []}
            rowHref={t => `/openbanking/tpps/${t.id}`}
            empty={<EmptyState Icon={Building2}
              title={lang === 'ar' ? 'لا مزوّدون' : 'No TPPs yet'}
              sub={demoMode
                ? (lang === 'ar' ? 'سجّل دخولك لعرض المزوّدين.' : 'Log in to view TPPs.')
                : (lang === 'ar'
                  ? 'سجّل أوّل مزوّد خدمة من سجل المزوّدين.'
                  : 'Register the first TPP from the register page.')} />}
          />
        )}
      </Card>

      <div style={{ marginTop: 16 }}>
        <Card padded
          title={lang === 'ar' ? 'حوادث مفتوحة' : 'Open incidents'}>
          {loading ? (
            <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
          ) : (
            <DataTable
              columns={incidentCols}
              rows={data?.openIncidentList ?? []}
              rowHref={() => `/openbanking/incidents`}
              empty={<EmptyState Icon={AlertOctagon}
                title={lang === 'ar' ? 'لا حوادث مفتوحة' : 'No open incidents'}
                sub={lang === 'ar'
                  ? 'لا حوادث تتطلّب تدخّلاً الآن.'
                  : 'Nothing demanding action right now.'} />}
            />
          )}
        </Card>
      </div>
    </Shell>
  );
}
