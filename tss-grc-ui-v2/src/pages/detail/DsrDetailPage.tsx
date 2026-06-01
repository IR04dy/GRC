import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import {
  Inbox, Mail, Calendar, Clock, ScrollText, CheckCheck, XIcon as X,
  ChevronRight, PlayCircle, AlertTriangle,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { DetailHeader } from '../../components/layout/DetailHeader';
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
import { privacyApi, ApiError, type PrivacyDsrDto } from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';

type TabId = 'overview' | 'docs' | 'activity';

const STATUS_TONE: Record<string, BadgeTone> = {
  PENDING: 'warning', NEW: 'warning', OPEN: 'warning',
  IN_PROGRESS: 'info', UNDER_REVIEW: 'info',
  APPROVED: 'success', FULFILLED: 'success', CLOSED: 'success',
  REJECTED: 'danger',
};

const REQUEST_TYPE_LABEL: Record<string, { en: string; ar: string }> = {
  ACCESS:        { en: 'Access',        ar: 'الوصول إلى البيانات' },
  ERASURE:       { en: 'Erasure',       ar: 'محو البيانات' },
  RECTIFICATION: { en: 'Rectification', ar: 'تصحيح البيانات' },
  PORTABILITY:   { en: 'Portability',   ar: 'نقل البيانات' },
  OBJECTION:     { en: 'Objection',     ar: 'الاعتراض' },
  RESTRICTION:   { en: 'Restriction',   ar: 'تقييد المعالجة' },
};

/**
 * Detail page for a single Data Subject Request (PDPL Article 4).
 *
 * <p>Saudi PDPL Article 4 grants subjects six rights — access,
 * rectification, erasure, portability, objection, and restriction.
 * Each request lands here for the DPO to triage and fulfil within
 * the **30-day SLA** (Art. 4.4). This page makes the countdown
 * unmistakable and gives the DPO the lifecycle workflow buttons
 * directly in the header.
 *
 * <p>Workflow: PENDING → IN_PROGRESS → FULFILLED | REJECTED (terminal).
 * Extensions allowed once for up to 60 days under Art. 4.5 (justification
 * required).
 */
export function DsrDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const [item, setItem] = useState<PrivacyDsrDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<TabId>('overview');

  async function load() {
    if (!id) return;
    setLoading(true); setError(null);
    try {
      const dsr = await privacyApi.getDsr(Number(id));
      setItem(dsr);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Failed to load DSR');
      setItem(null);
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { void load(); /* eslint-disable-next-line */ }, [id]);

  async function workflow(
    fn: (id: number) => Promise<PrivacyDsrDto>,
    successAr: string,
    successEn: string,
  ) {
    if (!item) return;
    try {
      const updated = await fn(item.id);
      setItem(updated);
      toast.success(isAr ? successAr : successEn);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Action failed');
    }
  }

  async function extend() {
    if (!item) return;
    const daysStr = window.prompt(
      isAr ? 'تمديد المهلة بعدد الأيام (الحد الأقصى 60 يومًا):' : 'Extend SLA by how many days (max 60):',
      '30');
    if (daysStr === null) return;
    const days = Math.max(1, Math.min(60, Number(daysStr) || 30));
    try {
      const updated = await privacyApi.extendDsr(item.id, days);
      setItem(updated);
      toast.success(isAr ? `تم التمديد ${days} يومًا` : `Extended by ${days} days`);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Extension failed');
    }
  }

  function requestTypeLabel(code: string): string {
    const m = REQUEST_TYPE_LABEL[code];
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
          {error ?? (isAr ? 'لم يُعثر على الطلب' : 'DSR not found')}
        </div>
      </Shell>
    );
  }

  // ── PDPL 30-day countdown ────────────────────────────────────────────
  const terminal = item.status === 'FULFILLED' || item.status === 'REJECTED' || item.status === 'CLOSED';
  const dueDays = item.dueDate
    ? Math.ceil((new Date(item.dueDate).getTime() - Date.now()) / 86_400_000)
    : null;

  const tabs: TabItem<TabId>[] = [
    { id: 'overview', label: isAr ? 'نظرة عامة' : 'Overview' },
    { id: 'docs',     label: isAr ? 'المستندات والضوابط' : 'Docs & controls' },
    { id: 'activity', label: isAr ? 'سجل النشاط' : 'Activity' },
  ];

  return (
    <Shell title={isAr ? 'حماية البيانات الشخصية' : 'PDPL'}>
      <DetailHeader
        breadcrumbs={[
          { label: isAr ? 'حماية البيانات' : 'PDPL', to: '/privacy' },
          { label: isAr ? 'طلبات أصحاب البيانات' : 'DSRs', to: '/privacy?tab=dsr' },
          { label: `#${item.id}` },
        ]}
        eyebrow={`DSR #${item.id}`}
        title={item.requesterName}
        sub={item.requesterEmail
          ? `${requestTypeLabel(item.requestType)} · ${item.requesterEmail}`
          : requestTypeLabel(item.requestType)}
        badges={<>
          <Badge tone={STATUS_TONE[item.status] ?? 'neutral'} dot>{item.status.replace(/_/g, ' ')}</Badge>
          <Badge tone="primary">{requestTypeLabel(item.requestType)}</Badge>
          {!terminal && dueDays != null && (
            <Badge tone={dueDays < 0 ? 'danger' : dueDays <= 6 ? 'warning' : 'success'}>
              {dueDays < 0
                ? (isAr ? `${Math.abs(dueDays)} يوم متأخر` : `${Math.abs(dueDays)}d overdue`)
                : (isAr ? `${dueDays} يوم متبقّ` : `${dueDays}d left`)}
            </Badge>
          )}
        </>}
        actions={
          !terminal && <>
            {(item.status === 'OPEN' || item.status === 'PENDING' || item.status === 'NEW') && (
              <Button variant="primary" Icon={PlayCircle}
                      onClick={() => workflow(privacyApi.startDsr, 'تم بدء المعالجة', 'DSR started')}>
                {isAr ? 'بدء المعالجة' : 'Start'}
              </Button>
            )}
            <Button variant="primary" Icon={CheckCheck}
                    onClick={() => workflow(privacyApi.fulfilDsr, 'تم تنفيذ الطلب', 'Fulfilled')}>
              {isAr ? 'تنفيذ' : 'Fulfil'}
            </Button>
            <Button variant="ghost" Icon={Clock} onClick={extend}>
              {isAr ? 'تمديد' : 'Extend'}
            </Button>
            <Button variant="ghost" Icon={X}
                    onClick={() => {
                      if (!window.confirm(isAr ? 'رفض هذا الطلب نهائيًا؟' : 'Reject this DSR? (terminal)')) return;
                      void workflow(privacyApi.rejectDsr, 'تم رفض الطلب', 'DSR rejected');
                    }}>
              {isAr ? 'رفض' : 'Reject'}
            </Button>
          </>
        }
      />

      <DetailLayout
        main={<>
          <Tabs items={tabs} active={tab} onChange={setTab} />

          {tab === 'overview' && (
            <Card title={isAr ? 'تفاصيل الطلب' : 'Request details'} Icon={ScrollText}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                <div>
                  <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase',
                                letterSpacing: '0.06em', marginBottom: 4 }}>
                    {isAr ? 'نوع الطلب وفق المادة 4' : 'Article 4 right exercised'}
                  </div>
                  <div style={{ fontSize: 14, fontWeight: 600 }}>{requestTypeLabel(item.requestType)}</div>
                </div>

                {/* PDPL anchor — every DSR detail in a Saudi DPO's hands needs the legal anchor explicit. */}
                <div style={{ padding: 12, background: 'var(--surface-2)',
                              borderRadius: 6, fontSize: 12, color: 'var(--ink-3)' }}>
                  {isAr
                    ? '⚖ المرجع النظامي: نظام حماية البيانات الشخصية في المملكة العربية السعودية — المادة 4 (حقوق صاحب البيانات) — مهلة الاستجابة 30 يومًا، قابلة للتمديد مرة واحدة بحد أقصى 60 يومًا مع تبرير موثّق.'
                    : '⚖ Regulatory anchor: Saudi PDPL — Article 4 (Data subject rights). 30-day response SLA, extendable once up to 60 days with documented justification.'}
                </div>

                {/* Surfaces a manual fulfilment workflow checklist — what the
                    DPO actually does before clicking Fulfil. Not persisted;
                    a workflow checklist would be a follow-up. */}
                <div>
                  <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase',
                                letterSpacing: '0.06em', marginBottom: 6 }}>
                    {isAr ? 'قائمة فحص التنفيذ' : 'Fulfilment checklist'}
                  </div>
                  <ul style={{ fontSize: 12, color: 'var(--ink-2)', lineHeight: 1.8, marginInlineStart: 16 }}>
                    <li>{isAr ? 'التحقق من هوية مقدّم الطلب' : 'Verify requester identity'}</li>
                    <li>{isAr ? 'تحديد جميع البيانات المتأثرة عبر الأنظمة' : 'Locate all subject data across systems'}</li>
                    <li>{isAr ? 'الفحص ضد الإعفاءات النظامية (الأمن الوطني، التحقيقات)' : 'Check exemptions (national security, investigations)'}</li>
                    <li>{isAr ? 'تجميع الاستجابة بصيغة قابلة للقراءة' : 'Compile machine-readable response'}</li>
                    <li>{isAr ? 'التوثيق في سجل التنفيذ' : 'Document in fulfilment log'}</li>
                  </ul>
                </div>
              </div>
            </Card>
          )}

          {tab === 'docs' && (
            <Card title={isAr ? 'المستندات وربط الضوابط' : 'Documents & control mapping'} Icon={ScrollText}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
                <AttachmentsField entityType="DSR" entityId={item.id} />
                <div style={{ borderTop: '1px solid var(--border)' }} />
                <ControlMappingPicker entityType="DSR" entityId={item.id} />
              </div>
            </Card>
          )}

          {tab === 'activity' && (
            <EntityActivityTimeline
              entityType="DataSubjectRequest"
              entityId={item.id}
              titleEn="Audit timeline"
              titleAr="سجل النشاط" />
          )}
        </>}
        rail={
          <>
            <Card title={isAr ? 'مقدّم الطلب' : 'Requester'} Icon={Inbox}>
              <RailFieldGroup>
                <RailField
                  label={isAr ? 'الاسم' : 'Name'}
                  value={<span style={{ fontWeight: 600 }}>{item.requesterName}</span>} />
                <RailField
                  label={isAr ? 'البريد' : 'Email'}
                  value={item.requesterEmail
                    ? <a href={`mailto:${item.requesterEmail}`} style={{ color: 'var(--primary)', fontSize: 12 }}>
                        <Mail size={11} style={{ verticalAlign: 'middle', marginInlineEnd: 4 }} />
                        {item.requesterEmail}
                      </a>
                    : '—'} />
              </RailFieldGroup>
            </Card>

            <Card title={isAr ? 'المسار الزمني' : 'Lifecycle'} Icon={Calendar}>
              <RailFieldGroup>
                <RailField label={isAr ? 'استُلم في' : 'Received'} value={fmtDate(item.receivedAt)} mono />
                <RailField
                  label={isAr ? 'تاريخ الاستحقاق' : 'Due date'}
                  value={item.dueDate
                    ? <span style={{ color: dueDays != null && dueDays < 0 ? 'var(--danger)' : undefined,
                                     fontWeight: dueDays != null && dueDays <= 6 ? 700 : 500 }}>
                        {fmtDate(item.dueDate)}
                      </span>
                    : '—'}
                  mono />
                <RailField
                  label={isAr ? 'المهلة المتبقية' : 'SLA countdown'}
                  value={
                    terminal
                      ? <Badge tone={STATUS_TONE[item.status] ?? 'neutral'}>
                          {isAr ? 'منتهي' : 'Closed'}
                        </Badge>
                      : dueDays == null
                        ? '—'
                        : <Badge tone={dueDays < 0 ? 'danger' : dueDays <= 6 ? 'warning' : 'success'}>
                            {dueDays < 0
                              ? (isAr ? `${Math.abs(dueDays)} يوم متأخر` : `${Math.abs(dueDays)}d overdue`)
                              : (isAr ? `${dueDays} يوم` : `${dueDays}d`)}
                          </Badge>
                  } />
              </RailFieldGroup>
            </Card>

            {!terminal && dueDays != null && dueDays <= 6 && (
              <Card title={isAr ? 'تنبيه' : 'Alert'} Icon={AlertTriangle}>
                <div style={{ fontSize: 12, color: 'var(--ink-2)' }}>
                  {isAr
                    ? `يستحق هذا الطلب خلال ${dueDays} يوم. إذا لم تكن قادرًا على التنفيذ في الوقت المحدد، يمكنك التمديد لمرة واحدة مع تبرير.`
                    : `This DSR is due in ${dueDays} day${dueDays === 1 ? '' : 's'}. If you can't meet the SLA, extend once with a justification.`}
                </div>
              </Card>
            )}

            <Card title={isAr ? 'روابط ذات صلة' : 'Related'} Icon={ChevronRight}>
              <RailFieldGroup>
                <RailField
                  label={isAr ? 'سجل الموافقات' : 'Consent ledger'}
                  value={<a href="/privacy?tab=consent" style={{ color: 'var(--primary)', fontSize: 12 }}>
                    {isAr ? 'افتح' : 'Open'}
                  </a>} />
                <RailField
                  label={isAr ? 'سجل المعالجة (RoPA)' : 'RoPA'}
                  value={<a href="/privacy?tab=ropa" style={{ color: 'var(--primary)', fontSize: 12 }}>
                    {isAr ? 'افتح' : 'Open'}
                  </a>} />
              </RailFieldGroup>
            </Card>
          </>
        } />
    </Shell>
  );
}

function fmtDate(s?: string): string { return s ? s.slice(0, 10) : '—'; }
