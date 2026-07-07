import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  Fingerprint, ShieldCheck, ScrollText, History as HistoryIcon,
  X as XIcon, ChevronRight, FileSignature, Clock,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { DetailHeader } from '../../components/layout/DetailHeader';
import { RaiseRequestButton } from '../../components/workflow/RaiseRequestButton';
import { DetailLayout } from '../../components/layout/DetailLayout';
import { RailField, RailFieldGroup } from '../../components/layout/DetailRail';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
import { AttachmentsField } from '../../components/ui/AttachmentsField';
import { ControlMappingPicker } from '../../components/ui/ControlMappingPicker';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { EntityActivityTimeline } from '../../components/audit/EntityActivityTimeline';
import { consentApi, ApiError, type ConsentRecordDto } from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';

type TabId = 'overview' | 'history' | 'docs' | 'activity';

const STATUS_TONE: Record<string, BadgeTone> = {
  ACTIVE:     'success',
  WITHDRAWN:  'danger',
  EXPIRED:    'warning',
  SUPERSEDED: 'neutral',
};
const LEGAL_BASIS_TONE: Record<string, BadgeTone> = {
  CONSENT:             'primary',
  LEGITIMATE_INTEREST: 'info',
  CONTRACT:            'success',
  LEGAL_OBLIGATION:    'accent',
  VITAL_INTEREST:      'warning',
  PUBLIC_TASK:         'neutral',
};

// PDPL Article 5 — Saudi-native purpose codes. Mirrors the ConsentTab map
// so labels stay consistent across the two screens. A consent record links
// a (subject ref × purpose) tuple to a lawful basis, language, and version.
const PURPOSE_LABEL: Record<string, { en: string; ar: string }> = {
  KYC_ONBOARDING:        { en: 'KYC / identity onboarding',     ar: 'التحقق من الهوية' },
  ACCOUNT_OPERATION:     { en: 'Operate customer account',       ar: 'تشغيل حساب العميل' },
  TRANSACTION_PROCESSING:{ en: 'Process transactions',           ar: 'تنفيذ المعاملات' },
  SERVICE_DELIVERY:      { en: 'Deliver requested service',      ar: 'تقديم الخدمة المطلوبة' },
  MARKETING_EMAIL:       { en: 'Marketing email',                ar: 'التسويق عبر البريد' },
  MARKETING_SMS:         { en: 'Marketing SMS',                  ar: 'التسويق عبر الرسائل النصية' },
  MARKETING_WHATSAPP:    { en: 'Marketing via WhatsApp',         ar: 'التسويق عبر واتساب' },
  PROFILING:             { en: 'Behavioural profiling',          ar: 'تحليل سلوكي للملف الشخصي' },
  PERSONALISATION:       { en: 'Personalised recommendations',   ar: 'تخصيص التوصيات' },
  THIRD_PARTY_SHARING:   { en: '3rd-party data sharing',         ar: 'مشاركة البيانات مع طرف ثالث' },
  CROSS_BORDER:          { en: 'Cross-border transfer',          ar: 'النقل خارج المملكة' },
  HEALTH_DATA:           { en: 'Health data processing',         ar: 'معالجة البيانات الصحية' },
  CREDIT_SCORING:        { en: 'Credit scoring (SIMAH)',         ar: 'التقييم الائتماني (سمة)' },
  BIOMETRIC:             { en: 'Biometric authentication',       ar: 'المصادقة الحيوية' },
  LOCATION:              { en: 'Location tracking',              ar: 'تتبع الموقع' },
  AML_SCREENING:         { en: 'AML / sanctions screening',      ar: 'فحص غسل الأموال والعقوبات' },
  REGULATORY_REPORTING:  { en: 'Regulatory reporting',           ar: 'الإبلاغ التنظيمي' },
  CALL_RECORDING:        { en: 'Call recording',                 ar: 'تسجيل المكالمات' },
  EMPLOYEE_MONITORING:   { en: 'Employee activity monitoring',   ar: 'مراقبة نشاط الموظفين' },
};
const CHANNEL_LABEL: Record<string, { en: string; ar: string }> = {
  WEB_FORM:  { en: 'Web portal',         ar: 'البوابة الإلكترونية' },
  API:       { en: 'API / mobile',       ar: 'واجهة برمجية / الجوال' },
  PAPER:     { en: 'Paper form',         ar: 'نموذج ورقي' },
  IN_PERSON: { en: 'Branch / in-person', ar: 'فرع / حضوريًا' },
  EMAIL:     { en: 'Email',              ar: 'البريد الإلكتروني' },
  OTHER:     { en: 'Other',              ar: 'أخرى' },
};

/**
 * Detail page for a single consent record. PDPL Art. 5 / 6.
 *
 * <p>Surfaces:
 *  - Overview rail with subject ref, purpose, basis, channel, version
 *  - Tabs: Overview / History (other versions for same subject) / Activity
 *  - Withdraw action when status is ACTIVE
 *  - Cross-link to subject's full consent history
 *  - Audit timeline (EntityType="ConsentRecord")
 */
export function ConsentDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const [item, setItem] = useState<ConsentRecordDto | null>(null);
  const [history, setHistory] = useState<ConsentRecordDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<TabId>('overview');

  async function load() {
    if (!id) return;
    setLoading(true); setError(null);
    try {
      const consent = await consentApi.get(Number(id));
      setItem(consent);
      // History — same subject_ref, all versions / lifecycle states.
      const hist = await consentApi.history(consent.dataSubjectRef);
      setHistory(hist);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Failed to load consent');
      setItem(null);
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { void load(); /* eslint-disable-next-line */ }, [id]);

  async function withdraw() {
    if (!item) return;
    const reason = window.prompt(
      isAr ? 'سبب سحب الموافقة:' : 'Reason for withdrawing this consent:',
      isAr ? 'طلب صاحب البيانات' : 'Subject request');
    if (reason === null) return;
    try {
      const updated = await consentApi.withdraw(item.id, reason);
      setItem(updated);
      toast.success(isAr ? 'تم سحب الموافقة' : 'Consent withdrawn');
      void load();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Withdraw failed');
    }
  }

  function purposeLabel(code: string): string {
    const m = PURPOSE_LABEL[code];
    if (!m) return code.replace(/_/g, ' ');
    return isAr ? m.ar : m.en;
  }
  function channelLabel(code: string): string {
    const m = CHANNEL_LABEL[code];
    if (!m) return code.replace(/_/g, ' ');
    return isAr ? m.ar : m.en;
  }

  if (loading) {
    return <Shell title="PDPL"><InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty></Shell>;
  }
  if (error || !item) {
    return (
      <Shell title="PDPL">
        <div style={{ padding: 32, textAlign: 'center', color: 'var(--danger-fg)' }}>
          {error ?? (isAr ? 'لم يُعثر على السجل' : 'Consent not found')}
        </div>
      </Shell>
    );
  }

  const title = purposeLabel(item.purposeCode);
  const tabs: TabItem<TabId>[] = [
    { id: 'overview', label: isAr ? 'نظرة عامة' : 'Overview' },
    { id: 'history',  label: isAr ? `السجل التاريخي · ${history.length}` : `History · ${history.length}` },
    { id: 'docs',     label: isAr ? 'المستندات والضوابط' : 'Docs & controls' },
    { id: 'activity', label: isAr ? 'سجل النشاط' : 'Activity' },
  ];

  return (
    <Shell title={isAr ? 'حماية البيانات الشخصية' : 'PDPL'}>
      <DetailHeader
        breadcrumbs={[
          { label: isAr ? 'حماية البيانات' : 'PDPL', to: '/privacy' },
          { label: isAr ? 'الموافقات' : 'Consents', to: '/privacy?tab=consent' },
          { label: `#${item.id}` },
        ]}
        eyebrow={`Consent #${item.id} · ${item.version}`}
        title={title}
        sub={item.purposeDescription ?? (isAr
          ? 'سجل موافقة وفق المادة 5 من نظام حماية البيانات الشخصية'
          : 'PDPL Article 5 consent ledger entry')}
        badges={<>
          <Badge tone={STATUS_TONE[item.status] ?? 'neutral'} dot>
            {statusText(item.status, isAr)}
          </Badge>
          <Badge tone={LEGAL_BASIS_TONE[item.legalBasis] ?? 'neutral'}>
            {item.legalBasis.replace(/_/g, ' ')}
          </Badge>
          <Badge tone="info">{channelLabel(item.grantedVia)}</Badge>
          <Badge tone="neutral">{item.language.toUpperCase()}</Badge>
        </>}
        actions={<>
          <RaiseRequestButton sourceModule="PRIVACY" sourceRecordType="Consent" sourceRecordId={Number(id) || undefined} />
          {item.status === 'ACTIVE'
            ? <Button variant="primary" Icon={XIcon} onClick={withdraw}>
                {isAr ? 'سحب الموافقة' : 'Withdraw consent'}
              </Button>
            : null}
        </>}
      />

      <DetailLayout
        main={<>
          <Tabs items={tabs} active={tab} onChange={setTab} />

          {tab === 'overview' && (
            <Card title={isAr ? 'وصف الغرض الكامل' : 'Full purpose description'} Icon={ScrollText}>
              {item.purposeDescription
                ? <p style={{ fontSize: 13, color: 'var(--ink-1)', lineHeight: 1.7 }}>{item.purposeDescription}</p>
                : <InlineEmpty>
                    {isAr
                      ? 'لا يوجد وصف مفصّل لهذا الغرض. يُفضّل أن يحتوي كل سجل موافقة على نص واضح لإعلام صاحب البيانات.'
                      : 'No detailed description for this purpose. Best practice: each consent should include the exact text shown to the subject.'}
                  </InlineEmpty>}
              <div style={{ marginTop: 16, padding: 12, background: 'var(--surface-2)',
                            borderRadius: 6, fontSize: 12, color: 'var(--ink-3)' }}>
                {isAr
                  ? '⚖ المرجع النظامي: نظام حماية البيانات الشخصية في المملكة العربية السعودية — المادة 5 (سجل الموافقات) والمادة 6 (الحق في سحب الموافقة).'
                  : '⚖ Regulatory anchor: Saudi PDPL — Article 5 (consent ledger) and Article 6 (right of withdrawal).'}
              </div>
            </Card>
          )}

          {tab === 'history' && (
            <Card title={isAr ? 'موافقات صاحب البيانات نفسه' : 'Same-subject history'} Icon={HistoryIcon}>
              {history.length === 0
                ? <InlineEmpty>{isAr ? 'لا توجد سجلات أخرى' : 'No other records'}</InlineEmpty>
                : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                    {history.map(h => (
                      <Link key={h.id}
                        to={`/privacy/consents/${h.id}`}
                        style={{
                          display: 'grid', gridTemplateColumns: '1fr auto auto auto auto',
                          gap: 12, padding: '10px 12px', borderRadius: 6,
                          background: h.id === item.id ? 'var(--surface-2)' : 'transparent',
                          fontSize: 12, alignItems: 'center', textDecoration: 'none',
                          color: 'var(--ink-1)',
                        }}>
                        <div>
                          <div style={{ fontWeight: 600 }}>{purposeLabel(h.purposeCode)}</div>
                          <div className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                            v{h.version} · {channelLabel(h.grantedVia)}
                          </div>
                        </div>
                        <Badge tone={LEGAL_BASIS_TONE[h.legalBasis] ?? 'neutral'}>{h.legalBasis.replace(/_/g, ' ')}</Badge>
                        <Badge tone={STATUS_TONE[h.status] ?? 'neutral'} dot>{statusText(h.status, isAr)}</Badge>
                        <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>{fmtDate(h.grantedAt)}</span>
                        <ChevronRight size={12} color="var(--ink-3)" />
                      </Link>
                    ))}
                  </div>
                )}
            </Card>
          )}

          {tab === 'docs' && (
            <Card title={isAr ? 'المستندات وربط الضوابط' : 'Documents & control mapping'} Icon={FileSignature}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
                <AttachmentsField entityType="CONSENT" entityId={item.id} />
                <div style={{ borderTop: '1px solid var(--border)' }} />
                <ControlMappingPicker entityType="CONSENT" entityId={item.id} />
              </div>
            </Card>
          )}

          {tab === 'activity' && (
            <EntityActivityTimeline
              entityType="ConsentRecord"
              entityId={item.id}
              titleEn="Audit timeline"
              titleAr="سجل النشاط" />
          )}
        </>}
        rail={
          <>
            <Card title={isAr ? 'الموضوع' : 'Subject'} Icon={Fingerprint}>
              <RailFieldGroup>
                <RailField
                  label={isAr ? 'معرّف صاحب البيانات' : 'Subject ref'}
                  value={<span style={{ fontSize: 11, wordBreak: 'break-all' }}>{item.dataSubjectRef}</span>}
                  mono />
                <RailField
                  label={isAr ? 'سجل صاحب البيانات' : 'Subject history'}
                  value={
                    <Link to={`/privacy?tab=consent&subject=${encodeURIComponent(item.dataSubjectRef)}`}
                          style={{ color: 'var(--primary)', fontSize: 12 }}>
                      {history.length} {isAr ? 'سجل' : 'records'}
                    </Link>
                  } />
              </RailFieldGroup>
            </Card>

            <Card title={isAr ? 'الغرض والأساس النظامي' : 'Purpose & basis'} Icon={ScrollText}>
              <RailFieldGroup>
                <RailField label={isAr ? 'رمز الغرض' : 'Purpose code'} value={item.purposeCode} mono />
                <RailField
                  label={isAr ? 'الأساس النظامي' : 'Legal basis'}
                  value={<Badge tone={LEGAL_BASIS_TONE[item.legalBasis] ?? 'neutral'}>{item.legalBasis.replace(/_/g, ' ')}</Badge>} />
                <RailField label={isAr ? 'الإصدار' : 'Version'} value={item.version} mono />
                <RailField
                  label={isAr ? 'لغة النص المعروض' : 'Display language'}
                  value={<Badge tone="neutral">{item.language.toUpperCase()}</Badge>} />
              </RailFieldGroup>
            </Card>

            <Card title={isAr ? 'المسار الزمني' : 'Lifecycle'} Icon={Clock}>
              <RailFieldGroup>
                <RailField label={isAr ? 'مُنحت في' : 'Granted at'} value={fmtDateTime(item.grantedAt)} mono />
                <RailField label={isAr ? 'قناة الجمع' : 'Collection channel'} value={channelLabel(item.grantedVia)} />
                {item.validUntil && (
                  <RailField
                    label={isAr ? 'سارية حتى' : 'Valid until'}
                    value={<ExpiryBadge iso={item.validUntil} isAr={isAr} />} />
                )}
                {item.withdrawnAt && (
                  <RailField
                    label={isAr ? 'سُحبت في' : 'Withdrawn at'}
                    value={<span style={{ color: 'var(--danger)' }}>{fmtDateTime(item.withdrawnAt)}</span>}
                    mono />
                )}
                {item.withdrawnReason && (
                  <RailField
                    label={isAr ? 'سبب السحب' : 'Withdrawal reason'}
                    value={<div style={{ fontSize: 12, color: 'var(--ink-2)' }}>{item.withdrawnReason}</div>} />
                )}
              </RailFieldGroup>
            </Card>

            {item.evidenceId && (
              <Card title={isAr ? 'الدليل' : 'Evidence'} Icon={ShieldCheck}>
                <RailFieldGroup>
                  <RailField
                    label={isAr ? 'إثبات الموافقة' : 'Proof of consent'}
                    value={
                      <Link to={`/evidence/${item.evidenceId}`} style={{ color: 'var(--primary)', fontSize: 12 }}>
                        #{item.evidenceId} <ChevronRight size={11} style={{ verticalAlign: 'middle' }} />
                      </Link>
                    } />
                </RailFieldGroup>
              </Card>
            )}
          </>
        } />
    </Shell>
  );
}

function statusText(s: string, isAr: boolean): string {
  if (!isAr) return s;
  switch (s) {
    case 'ACTIVE':     return 'نشطة';
    case 'WITHDRAWN':  return 'مسحوبة';
    case 'EXPIRED':    return 'منتهية';
    case 'SUPERSEDED': return 'استُبدلت';
    default:           return s;
  }
}

function fmtDate(s?: string): string { return s ? s.slice(0, 10) : '—'; }
function fmtDateTime(s?: string): string {
  if (!s) return '—';
  // Server returns ISO; trim the millisecond/TZ tail to keep the rail tidy.
  return s.replace('T', ' ').slice(0, 16);
}

function ExpiryBadge({ iso, isAr }: { iso: string; isAr: boolean }) {
  const ms = new Date(iso).getTime() - Date.now();
  const days = Math.floor(ms / 86_400_000);
  if (days < 0) return <Badge tone="danger">{isAr ? `${Math.abs(days)} يوم متأخرة` : `${Math.abs(days)}d overdue`}</Badge>;
  if (days <= 7)  return <Badge tone="danger">{isAr ? `${days} يوم` : `${days}d`}</Badge>;
  if (days <= 30) return <Badge tone="warning">{isAr ? `${days} يوم` : `${days}d`}</Badge>;
  return <span className="mono" style={{ fontSize: 11 }}>{iso.slice(0, 10)} <FileSignature size={11} style={{ verticalAlign: 'middle', opacity: 0.5 }} /></span>;
}
