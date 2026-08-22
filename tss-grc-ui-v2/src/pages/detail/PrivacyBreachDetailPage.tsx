import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import {
  ShieldAlert, ShieldCheck, Send, Archive, Calendar, AlertTriangle,
  ScrollText, ChevronRight, FileText,
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
import { privacyApi, ApiError, type PrivacyBreachDto } from '../../lib/api';
// F2-211: the detail page asks the same four questions the register and the breach
// console ask, through the same predicates — awareness stamp, SDAIA discharge and
// the provisional severity band.
import {
  breachAwarenessAt, breachSeverityInputs, classifySeverity,
  severityIsProvisional, isSdaiaSubmitted, breachTypeLabel,
} from '../../lib/pdplBreach';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';

type TabId = 'overview' | 'docs' | 'activity';

const STATUS_TONE: Record<string, BadgeTone> = {
  OPEN: 'danger', NEW: 'danger',
  INVESTIGATING: 'warning', CONTAINED: 'warning',
  REPORTED: 'info', NOTIFIED: 'info',
  CLOSED: 'success',
};
const SEVERITY_TONE: Record<string, BadgeTone> = {
  CRITICAL: 'risk-crit', HIGH: 'risk-high', MEDIUM: 'risk-med', LOW: 'risk-low',
};

// F2-211: this page held its own seven-token breach-type map, of which exactly ONE
// (`UNAUTHORISED_ACCESS`) appears in any live row — the other six are the vocabulary
// the DEMO CORPUS used, so the map had been written against the fabrication rather
// than the database, and eleven of the twelve stored tokens fell through its raw-token
// fallback. The map now lives in lib/pdplBreach.ts, covers the measured twelve, and is
// shared with the register so the two surfaces cannot label one column two ways.

/**
 * Detail page for a single personal-data breach (PDPL Article 20).
 *
 * <p>Saudi PDPL Article 20 mandates notification to SDAIA (the data
 * protection authority) within **72 hours** of becoming aware of a
 * breach that is likely to result in serious damage to data subjects.
 * Article 21 adds notification to affected data subjects without
 * undue delay.
 *
 * <p>This page makes the 72-hour clock visible and exposes the
 * lifecycle buttons (Contain → Notify SDAIA → Close).
 */
export function PrivacyBreachDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const [item, setItem] = useState<PrivacyBreachDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<TabId>('overview');

  async function load() {
    if (!id) return;
    setLoading(true); setError(null);
    try {
      const breach = await privacyApi.getBreach(Number(id));
      setItem(breach);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Failed to load breach');
      setItem(null);
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { void load(); /* eslint-disable-next-line */ }, [id]);

  async function workflow(
    fn: (id: number) => Promise<PrivacyBreachDto>,
    successAr: string, successEn: string,
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

  const typeLabel = (code: string) => breachTypeLabel(code, isAr ? 'ar' : 'en');

  if (loading) {
    return <Shell title="PDPL"><InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty></Shell>;
  }
  if (error || !item) {
    return (
      <Shell title="PDPL">
        <div style={{ padding: 32, textAlign: 'center', color: 'var(--danger-fg)' }}>
          {error ?? (isAr ? 'لم يُعثر على الحادثة' : 'Breach not found')}
        </div>
      </Shell>
    );
  }

  // ── PDPL Article 20 — 72-hour countdown ────────────────────────────
  //
  // ⚠⚠ F2-211: every input here used to be a field with no column in
  // `privacy.data_breaches`. `reportedToAuthority` was cast in with `as` and the
  // wire has never carried it, so the "SDAIA notified" badge could not render for
  // any breach; `occurredAt` does not exist either, so `hoursRemaining` was null on
  // every row and the countdown badge — guarded by `hoursRemaining != null` — never
  // appeared. The whole Article 20 treatment on this page was inert against real
  // data and visible only under the demo fallback corpus.
  const reportedToAuthority = isSdaiaSubmitted(item.sdaiaNotificationStatus);
  const terminal = item.status === 'CLOSED' || reportedToAuthority;
  const aware = breachAwarenessAt(item);
  const hoursElapsed = aware
    ? Math.floor((Date.now() - new Date(aware).getTime()) / 3_600_000)
    : null;
  const hoursRemaining = hoursElapsed != null ? 72 - hoursElapsed : null;
  // Derived, provisional, and disclosed as such — the same inputs the register and
  // the breach console classify from. There is no stored severity to show.
  const severityInputs = breachSeverityInputs(item);
  const severity = classifySeverity(severityInputs);
  const severityProvisional = severityIsProvisional(severityInputs);
  const severityHint = isAr
    ? 'صُنِّفت الدرجة من عدد الأشخاص المتأثّرين فقط؛ لا تحفظ المنصّة درجة خطورة للحادثة.'
    : 'Band derived from the affected-subject count alone — the platform stores no severity for a breach.';
  const affected = item.dataSubjectsAffectedCount;

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
          { label: isAr ? 'الحوادث' : 'Breaches', to: '/privacy?tab=breaches' },
          { label: `#${item.id}` },
        ]}
        eyebrow={`Breach #${item.id}`}
        title={item.titleEn}
        sub={item.descriptionEn?.slice(0, 200)}
        badges={<>
          <Badge tone={STATUS_TONE[item.status ?? ''] ?? 'neutral'} dot>{(item.status ?? '—').replace(/_/g, ' ')}</Badge>
          <Badge tone={SEVERITY_TONE[severity] ?? 'neutral'} title={severityProvisional ? severityHint : undefined}>
            {severity}{severityProvisional && <span aria-label={severityHint}> ·&nbsp;{isAr ? 'مبدئي' : 'provisional'}</span>}
          </Badge>
          {item.breachType && <Badge tone="primary">{typeLabel(item.breachType)}</Badge>}
          {/*
            F2-285 — provenance. A breach the cyber → PDPL bridge raised is a
            different artefact from one a DPO recorded: the detection story lives in
            another module, and "how did you become aware?" is the first thing an
            SDAIA filing has to answer. The badge links to the originating incident
            because until F2-285 nothing could get there from here — the breach
            carried no readable reference and the bridge log keys only on the
            incident id.
          */}
          {(item.autoGenerated ?? 0) !== 0 && (
            <Badge tone="warning" dot
                   title={isAr
                     ? `أُنشئ تلقائياً بواسطة قاعدة الجسر ${item.autoGenerationRule ?? ''}`
                     : `Raised automatically by bridge rule ${item.autoGenerationRule ?? ''}`}>
              {isAr ? 'مُنشأ من حادثة سيبرانية' : 'Raised from a cyber incident'}
              {item.sourceCyberIncidentNumber && <> ·&nbsp;{item.sourceCyberIncidentNumber}</>}
            </Badge>
          )}
          {reportedToAuthority && (
            <Badge tone="success" dot>{isAr ? 'بُلِّغت الهيئة' : 'SDAIA notified'}</Badge>
          )}
          {!terminal && hoursRemaining != null && (
            <Badge tone={hoursRemaining < 0 ? 'danger' : hoursRemaining <= 12 ? 'warning' : 'success'}>
              {hoursRemaining < 0
                ? (isAr ? `${Math.abs(hoursRemaining)} ساعة متأخرة` : `${Math.abs(hoursRemaining)}h overdue`)
                : (isAr ? `${hoursRemaining} ساعة متبقّ` : `${hoursRemaining}h left`)}
            </Badge>
          )}
        </>}
        actions={<>
          <RaiseRequestButton sourceModule="PRIVACY" sourceRecordType="Breach" sourceRecordId={Number(id) || undefined} />
          {!terminal && <>
            {item.status === 'OPEN' && (
              <Button variant="primary" Icon={ShieldCheck}
                      onClick={() => workflow(privacyApi.containBreach, 'تم احتواء الحادثة', 'Breach contained')}>
                {isAr ? 'احتواء' : 'Contain'}
              </Button>
            )}
            <Button variant="primary" Icon={Send}
                    onClick={() => {
                      if (!window.confirm(isAr
                        ? 'تأكيد إخطار الهيئة السعودية للبيانات والذكاء الاصطناعي (سدايا)؟ هذا الإجراء يُسجّل في سجل التدقيق.'
                        : 'Confirm SDAIA notification? This action is audit-logged.')) return;
                      void workflow(privacyApi.notifyDataOffice, 'تم تسجيل إخطار سدايا', 'SDAIA notification recorded');
                    }}>
              {isAr ? 'إخطار سدايا' : 'Notify SDAIA'}
            </Button>
            <Button variant="ghost" Icon={Archive}
                    onClick={() => workflow(privacyApi.closeBreach, 'تم إغلاق الحادثة', 'Breach closed')}>
              {isAr ? 'إغلاق' : 'Close'}
            </Button>
          </>}
        </>}
      />

      <DetailLayout
        main={<>
          <Tabs items={tabs} active={tab} onChange={setTab} />

          {tab === 'overview' && (
            <Card title={isAr ? 'وصف الحادثة' : 'Breach description'} Icon={ScrollText}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                <div>
                  <div style={{ fontSize: 13, color: 'var(--ink-1)', lineHeight: 1.7 }}>
                    {item.descriptionEn ?? (isAr ? '— لا يوجد وصف —' : '— No description —')}
                  </div>
                </div>

                {affected != null && affected > 0 && (
                  <div style={{ padding: 12, background: 'var(--surface-2)', borderRadius: 6 }}>
                    <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase',
                                  letterSpacing: '0.06em', marginBottom: 4 }}>
                      {isAr ? 'عدد السجلات المتأثرة' : 'Affected records'}
                    </div>
                    <div style={{ fontSize: 22, fontWeight: 700, color: 'var(--danger-fg)' }}>
                      {affected.toLocaleString()}
                    </div>
                    <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 4 }}>
                      {isAr
                        ? 'يجب إخطار الأشخاص المتأثرين دون تأخير غير مبرر (المادة 21).'
                        : 'Affected subjects must be notified without undue delay (Article 21).'}
                    </div>
                  </div>
                )}

                {/* PDPL anchor */}
                <div style={{ padding: 12, background: 'var(--surface-2)',
                              borderRadius: 6, fontSize: 12, color: 'var(--ink-3)' }}>
                  {isAr
                    ? '⚖ المرجع النظامي: نظام حماية البيانات الشخصية — المادة 20 (إخطار سدايا خلال 72 ساعة من العلم بالحادثة) والمادة 21 (إخطار أصحاب البيانات).'
                    : '⚖ Regulatory anchor: Saudi PDPL — Article 20 (SDAIA notification within 72 hours of awareness) and Article 21 (notification of affected subjects).'}
                </div>

                {/* What the IR team actually does — same pattern as DSR */}
                <div>
                  <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase',
                                letterSpacing: '0.06em', marginBottom: 6 }}>
                    {isAr ? 'خطوات الاستجابة' : 'Response checklist'}
                  </div>
                  <ul style={{ fontSize: 12, color: 'var(--ink-2)', lineHeight: 1.8, marginInlineStart: 16 }}>
                    <li>{isAr ? 'احتواء الحادثة وإيقاف الانتشار' : 'Contain breach and stop propagation'}</li>
                    <li>{isAr ? 'تقييم نطاق البيانات المتأثرة والمخاطر' : 'Scope affected data and risk severity'}</li>
                    <li>{isAr ? 'إخطار سدايا خلال 72 ساعة من العلم بالحادثة' : 'Notify SDAIA within 72h of awareness'}</li>
                    <li>{isAr ? 'إخطار أصحاب البيانات المتأثرين (المادة 21)' : 'Notify affected subjects (Art. 21)'}</li>
                    <li>{isAr ? 'وثيقة الدروس المستفادة والإجراءات التصحيحية' : 'Document lessons learned + corrective actions'}</li>
                  </ul>
                </div>
              </div>
            </Card>
          )}

          {tab === 'docs' && (
            <Card title={isAr ? 'المستندات وربط الضوابط' : 'Documents & control mapping'} Icon={FileText}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
                <AttachmentsField entityType="BREACH" entityId={item.id} />
                <div style={{ borderTop: '1px solid var(--border)' }} />
                <ControlMappingPicker entityType="BREACH" entityId={item.id} />
              </div>
            </Card>
          )}

          {tab === 'activity' && (
            <EntityActivityTimeline
              entityType="DataBreach"
              entityId={item.id}
              titleEn="Audit timeline"
              titleAr="سجل النشاط" />
          )}
        </>}
        rail={
          <>
            <Card title={isAr ? 'المسار الزمني' : 'Lifecycle'} Icon={Calendar}>
              <RailFieldGroup>
                {/* F2-211: "Occurred at" read a field with no column. The stamp the
                    schema keeps is `discovered_at` — when the controller became
                    aware — which is a different fact and the one the statutory
                    clock runs from, so the label says which. The "Reported at" row
                    below it was removed rather than relabelled: no column records
                    when the breach was reported internally. */}
                <RailField label={isAr ? 'اكتُشفت في' : 'Discovered at'} value={fmtDate(aware || undefined)} mono />
                <RailField
                  label={isAr ? 'مدّة الاستجابة (72 ساعة)' : '72-hour clock'}
                  value={
                    terminal
                      ? <Badge tone={STATUS_TONE[item.status ?? ''] ?? 'neutral'}>
                          {reportedToAuthority ? (isAr ? 'بُلّغت' : 'Reported') : (isAr ? 'مُغلق' : 'Closed')}
                        </Badge>
                      : hoursRemaining == null
                        ? '—'
                        : <Badge tone={hoursRemaining < 0 ? 'danger' : hoursRemaining <= 12 ? 'warning' : 'success'}>
                            {hoursRemaining < 0
                              ? (isAr ? `${Math.abs(hoursRemaining)} ساعة متأخرة` : `${Math.abs(hoursRemaining)}h overdue`)
                              : (isAr ? `${hoursRemaining} ساعة` : `${hoursRemaining}h left`)}
                          </Badge>
                  } />
              </RailFieldGroup>
            </Card>

            <Card title={isAr ? 'التصنيف' : 'Classification'} Icon={ShieldAlert}>
              <RailFieldGroup>
                <RailField
                  label={isAr ? 'النوع' : 'Type'}
                  value={item.breachType
                    ? <Badge tone="primary">{typeLabel(item.breachType)}</Badge>
                    : '—'} />
                <RailField
                  label={isAr ? 'الشدّة' : 'Severity'}
                  value={<Badge tone={SEVERITY_TONE[severity] ?? 'neutral'} title={severityProvisional ? severityHint : undefined}>
                    {severity}{severityProvisional && <span aria-label={severityHint}> ·&nbsp;{isAr ? 'مبدئي' : 'provisional'}</span>}
                  </Badge>} />
                <RailField
                  label={isAr ? 'الأشخاص المتأثرون' : 'Affected subjects'}
                  value={affected?.toLocaleString() ?? '—'}
                  mono />
              </RailFieldGroup>
            </Card>

            {!terminal && hoursRemaining != null && hoursRemaining <= 12 && (
              <Card title={isAr ? '⚠ تنبيه عاجل' : '⚠ Urgent'} Icon={AlertTriangle}>
                <div style={{ fontSize: 12, color: 'var(--danger)' }}>
                  {isAr
                    ? `أقل من ${hoursRemaining} ساعة متبقية لإخطار سدايا. تأكد من جمع الأدلة الكافية قبل الإرسال.`
                    : `Less than ${hoursRemaining}h to notify SDAIA. Compile evidence before submitting.`}
                </div>
              </Card>
            )}

            <Card title={isAr ? 'روابط ذات صلة' : 'Related'} Icon={ChevronRight}>
              <RailFieldGroup>
                <RailField
                  label={isAr ? 'وحدة الاتصالات' : 'Communications'}
                  value={<a href="/privacy?tab=comms" style={{ color: 'var(--primary)', fontSize: 12 }}>
                    {isAr ? 'افتح' : 'Open'}
                  </a>} />
                <RailField
                  label={isAr ? 'إدارة الحوادث' : 'Incident module'}
                  value={<a href="/incidents" style={{ color: 'var(--primary)', fontSize: 12 }}>
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
