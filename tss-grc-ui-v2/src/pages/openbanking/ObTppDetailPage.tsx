import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import {
  FileCheck2, Activity, type LucideIcon,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { AttachmentsField } from '../../components/ui/AttachmentsField';
import { ControlMappingPicker } from '../../components/ui/ControlMappingPicker';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import {
  obApi,
  TPP_STATUS_LABEL, TPP_STATUS_TONE,
  CONSENT_STATUS_LABEL, CONSENT_STATUS_TONE,
  CONSENT_TYPE_LABEL,
  EVENT_TYPE_LABEL,
  nextTppStatuses,
  type TppResponse, type ConsentResponse, type EventResponse,
  type TppStatus,
} from '../../lib/openbanking';

type Tab = 'consents' | 'events';

/**
 * Sprint 19 PR #3 — TPP workspace (detail page).
 * State-machine action panel + consents + timeline.
 */
export function ObTppDetailPage() {
  const { id } = useParams();
  const tppId = Number(id);
  const { lang } = useI18n();

  const [tpp, setTpp] = useState<TppResponse | null>(null);
  const [consents, setConsents] = useState<ConsentResponse[]>([]);
  const [events, setEvents] = useState<EventResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [acting, setActing] = useState(false);
  const [tab, setTab] = useState<Tab>('consents');

  const [transitionTo, setTransitionTo] = useState<TppStatus | null>(null);
  const [reasonEn, setReasonEn] = useState('');
  const [reasonAr, setReasonAr] = useState('');

  const reload = async () => {
    if (!tppId) return;
    setLoading(true); setError(null);
    try {
      const [t, c, e] = await Promise.all([
        obApi.getTpp(tppId),
        obApi.listConsentsForTpp(tppId).catch(() => []),
        obApi.listTppEvents(tppId).catch(() => []),
      ]);
      setTpp(t); setConsents(c); setEvents(e);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تحميل المزوّد' : 'Failed to load TPP'));
    } finally { setLoading(false); }
  };

  useEffect(() => { reload(); /* eslint-disable-next-line */ }, [tppId]);

  const submitTransition = async () => {
    if (!tpp || !transitionTo) return;
    setActing(true); setError(null);
    try {
      await obApi.transitionTpp(tpp.id, {
        toStatus: transitionTo,
        reasonEn: reasonEn || undefined,
        reasonAr: reasonAr || undefined,
      });
      setTransitionTo(null); setReasonEn(''); setReasonAr('');
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تنفيذ التحوّل' : 'Failed to transition TPP'));
    } finally { setActing(false); }
  };

  if (!tppId) return null;
  if (loading && !tpp) {
    return (
      <Shell title={lang === 'ar' ? 'تفاصيل المزوّد' : 'TPP detail'}>
        <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
      </Shell>
    );
  }
  if (!tpp) {
    return (
      <Shell title={lang === 'ar' ? 'تفاصيل المزوّد' : 'TPP detail'}>
        {error && (
          <div style={{ color: 'var(--danger)', fontSize: 13 }}>{error}</div>
        )}
      </Shell>
    );
  }

  const next = nextTppStatuses(tpp.status);

  const consentCols: Column<ConsentResponse>[] = [
    {
      header: lang === 'ar' ? 'المرجع' : 'Reference', mono: true, width: '180px',
      cell: c => <span style={{ fontSize: 12 }}>{c.consentReference}</span>,
    },
    {
      header: lang === 'ar' ? 'العميل' : 'Customer',
      cell: c => (
        <span style={{ fontSize: 13 }}>
          {(lang === 'ar' ? c.customerLabelAr : c.customerLabelEn) ?? '—'}
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
      header: lang === 'ar' ? 'انتهاء' : 'Expires', width: '120px',
      cell: c => <span style={{ fontSize: 12 }}>{c.expiresAt ?? '—'}</span>,
    },
    {
      header: lang === 'ar' ? 'عدد النداءات' : 'Calls', width: '90px',
      cell: c => <span style={{ fontSize: 12 }}>{c.callCount ?? 0}</span>,
    },
  ];

  return (
    <Shell
      title={lang === 'ar' ? 'تفاصيل المزوّد' : 'TPP workspace'}
      sub={lang === 'ar'
        ? 'مساحة عمل مزوّد الخدمة — الحالة، الموافقات، السجلّ الزمني.'
        : 'TPP workspace — status, consents, timeline.'}
    >
      <PageHeader
        eyebrow={`${tpp.reference} · ${tpp.roles} · ${lang === 'ar' ? 'المصرفية المفتوحة' : 'Open Banking'}`}
        title={lang === 'ar' ? tpp.legalNameAr : tpp.legalNameEn}
        sub={tpp.roles}
      />

      {error && (
        <div style={{ color: 'var(--danger)', fontSize: 13, marginBottom: 12 }}>
          {error}
        </div>
      )}

      <Card padded
        title={lang === 'ar' ? 'الملخّص' : 'Summary'}>
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
          gap: 12,
        }}>
          <Stat label={lang === 'ar' ? 'الحالة' : 'Status'} value={
            <Badge tone={TPP_STATUS_TONE[tpp.status]}>
              {TPP_STATUS_LABEL[tpp.status][lang === 'ar' ? 'ar' : 'en']}
            </Badge>
          } />
          <Stat label={lang === 'ar' ? 'مرجع ترخيص ساما' : 'SAMA licence'}
                value={tpp.samaLicenceRef ?? '—'} />
          <Stat label={lang === 'ar' ? 'معرّف FAPI' : 'FAPI client_id'}
                value={tpp.fapiClientId ?? '—'} />
          <Stat label={lang === 'ar' ? 'الدولة' : 'Country'}
                value={tpp.country ?? '—'} />
          <Stat label={lang === 'ar' ? 'الموافقات النشطة' : 'Active consents'}
                value={String(tpp.activeConsentCount ?? 0)} />
          <Stat label={lang === 'ar' ? 'حوادث مفتوحة' : 'Open incidents'}
                value={
                  <span style={{ color: (tpp.openIncidentCount ?? 0) > 0 ? 'var(--danger)' : undefined,
                    fontWeight: (tpp.openIncidentCount ?? 0) > 0 ? 700 : 400 }}>
                    {tpp.openIncidentCount ?? 0}
                  </span>
                } />
          <Stat label={lang === 'ar' ? 'بصمة الشهادة' : 'Cert fingerprint'}
                value={tpp.certFingerprint
                  ? <code style={{ fontSize: 11 }}>{tpp.certFingerprint.slice(0, 32)}…</code>
                  : '—'} />
          <Stat label={lang === 'ar' ? 'انتهاء الشهادة' : 'Cert expires'}
                value={tpp.certExpiresAt ?? '—'} />
          <Stat label={lang === 'ar' ? 'حدّ المعدّل (طلب/ث)' : 'Rate-limit (req/s)'}
                value={String(tpp.rateLimitPerSec ?? '—')} />
          <Stat label={lang === 'ar' ? 'تاريخ التسجيل' : 'Onboarded at'}
                value={tpp.onboardedAt ?? '—'} />
          {tpp.suspendedAt && (
            <Stat label={lang === 'ar' ? 'تاريخ الإيقاف' : 'Suspended at'}
                  value={tpp.suspendedAt} />
          )}
          {tpp.revokedAt && (
            <Stat label={lang === 'ar' ? 'تاريخ الإلغاء' : 'Revoked at'}
                  value={tpp.revokedAt} />
          )}
        </div>
        {tpp.suspensionReasonEn && (
          <div style={{ marginTop: 12, fontSize: 13 }}>
            <strong>{lang === 'ar' ? 'سبب الإيقاف:' : 'Suspension reason:'}</strong>{' '}
            {lang === 'ar' ? (tpp.suspensionReasonAr ?? tpp.suspensionReasonEn) : tpp.suspensionReasonEn}
          </div>
        )}
      </Card>

      {next.length > 0 && (
        <div style={{ marginTop: 16 }}>
          <Card padded
            title={lang === 'ar' ? 'إجراءات الحالة' : 'Lifecycle actions'}>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
              {next.map(to => (
                <Button key={to}
                  variant={to === 'REVOKED' ? 'danger' : to === 'SUSPENDED' ? 'secondary' : 'primary'}
                  onClick={() => { setTransitionTo(to); setReasonEn(''); setReasonAr(''); }}>
                  {lang === 'ar' ? 'انتقال إلى' : 'Move to'}{' '}
                  {TPP_STATUS_LABEL[to][lang === 'ar' ? 'ar' : 'en']}
                </Button>
              ))}
            </div>
            <div style={{ marginTop: 8, fontSize: 12, color: 'var(--ink-3)' }}>
              {lang === 'ar'
                ? 'انتقال إلى موقوف أو مُلغى يُلغي تلقائياً كل الموافقات النشطة المرتبطة.'
                : 'Moving to SUSPENDED or REVOKED auto-revokes every active consent tied to this TPP.'}
            </div>
          </Card>
        </div>
      )}

      {transitionTo && (
        <div style={{ marginTop: 16 }}>
          <Card padded
            title={lang === 'ar'
              ? `تأكيد الانتقال إلى ${TPP_STATUS_LABEL[transitionTo].ar}`
              : `Confirm transition to ${TPP_STATUS_LABEL[transitionTo].en}`}>
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
              gap: 12,
            }}>
              <Field label={lang === 'ar' ? 'السبب (إنجليزي)' : 'Reason (English)'}>
                <textarea value={reasonEn}
                  onChange={e => setReasonEn(e.target.value)}
                  rows={3} style={inputStyle}
                  placeholder="e.g. SAMA suspended the licence — see ticket TSS-INSP-2026-014" />
              </Field>
              <Field label={lang === 'ar' ? 'السبب (عربي)' : 'Reason (Arabic)'}>
                <textarea value={reasonAr} dir="rtl"
                  onChange={e => setReasonAr(e.target.value)}
                  rows={3} style={inputStyle}
                  placeholder="مثال: علّقت ساما الترخيص — مرجع التذكرة TSS-INSP-2026-014" />
              </Field>
            </div>
            <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
              <Button onClick={submitTransition} disabled={acting}
                      variant={transitionTo === 'REVOKED' ? 'danger' : 'primary'}>
                {acting
                  ? (lang === 'ar' ? 'جارٍ التنفيذ…' : 'Applying…')
                  : (lang === 'ar' ? 'تأكيد' : 'Confirm')}
              </Button>
              <Button variant="secondary"
                onClick={() => { setTransitionTo(null); setReasonEn(''); setReasonAr(''); }}>
                {lang === 'ar' ? 'إلغاء' : 'Cancel'}
              </Button>
            </div>
          </Card>
        </div>
      )}

      <div style={{ marginTop: 16, display: 'flex', gap: 4 }}>
        <TabBtn active={tab === 'consents'} onClick={() => setTab('consents')}
          Icon={FileCheck2}
          label={lang === 'ar' ? `الموافقات (${consents.length})`
                                : `Consents (${consents.length})`} />
        <TabBtn active={tab === 'events'} onClick={() => setTab('events')}
          Icon={Activity}
          label={lang === 'ar' ? `السجلّ (${events.length})`
                                : `Timeline (${events.length})`} />
      </div>

      <div style={{ marginTop: 8 }}>
        {tab === 'consents' && (
          <Card padded
            title={lang === 'ar' ? 'موافقات هذا المزوّد' : 'Consents on this TPP'}>
            <DataTable
              columns={consentCols}
              rows={consents}
              empty={<EmptyState Icon={FileCheck2}
                title={lang === 'ar' ? 'لا موافقات' : 'No consents yet'}
                sub={lang === 'ar'
                  ? 'سيظهر هنا كل عميل منح هذا المزوّد موافقة.'
                  : 'Customers who grant this TPP a consent will appear here.'} />}
            />
          </Card>
        )}

        {tab === 'events' && (
          <Card padded
            title={lang === 'ar' ? 'سجلّ الأحداث' : 'Event timeline'}>
            {events.length === 0 ? (
              <EmptyState Icon={Activity}
                title={lang === 'ar' ? 'لا أحداث' : 'No events yet'} />
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {events.map(e => (
                  <div key={e.id} style={{
                    display: 'flex', gap: 8, padding: '8px 12px',
                    borderInlineStart: '3px solid var(--accent)',
                    background: 'var(--card-bg)',
                    borderRadius: 4,
                  }}>
                    <span style={{ fontSize: 11, color: 'var(--ink-3)', minWidth: 130 }}>
                      {e.occurredAt}
                    </span>
                    <span style={{ fontSize: 12 }}>
                      <Badge tone="neutral">
                        {EVENT_TYPE_LABEL[e.eventType]?.[lang === 'ar' ? 'ar' : 'en'] ?? e.eventType}
                      </Badge>{' '}
                      <span style={{ marginInlineStart: 6 }}>
                        {lang === 'ar' ? (e.noteAr ?? e.noteEn ?? '') : (e.noteEn ?? e.noteAr ?? '')}
                      </span>
                    </span>
                  </div>
                ))}
              </div>
            )}
          </Card>
        )}

        {/* Documents & control mapping — registration cert, API agreements, controls */}
        <Card padded title={lang === 'ar' ? 'المستندات وربط الضوابط' : 'Documents & control mapping'}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
            <AttachmentsField entityType="OB_TPP" entityId={tpp.id} />
            <div style={{ borderTop: '1px solid var(--border)' }} />
            <ControlMappingPicker entityType="OB_TPP" entityId={tpp.id} />
          </div>
        </Card>
      </div>
    </Shell>
  );
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

function Stat({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>{label}</span>
      <span style={{ fontSize: 13, fontWeight: 500 }}>{value}</span>
    </div>
  );
}

function TabBtn({ active, onClick, Icon, label }:
  { active: boolean; onClick: () => void;
    Icon: LucideIcon; label: string }) {
  return (
    <button onClick={onClick} style={{
      padding: '6px 12px',
      background: active ? 'var(--card-bg)' : 'transparent',
      border: '1px solid var(--border)',
      borderBottom: active ? '2px solid var(--accent)' : '1px solid var(--border)',
      borderRadius: '4px 4px 0 0',
      cursor: 'pointer',
      display: 'inline-flex', gap: 6, alignItems: 'center',
      fontSize: 13, fontWeight: active ? 600 : 400,
    }}>
      <Icon size={14} />
      {label}
    </button>
  );
}
