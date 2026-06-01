import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import {
  Database, FileText, Globe, ScrollText, ChevronRight, Calendar,
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
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { EntityActivityTimeline } from '../../components/audit/EntityActivityTimeline';
import { privacyApi, ApiError, type PrivacyRopaDto } from '../../lib/api';
import { useI18n } from '../../lib/i18n';

type TabId = 'overview' | 'docs' | 'activity';

const STATUS_TONE: Record<string, BadgeTone> = {
  DRAFT: 'neutral', UNDER_REVIEW: 'info',
  APPROVED: 'success', ACTIVE: 'success',
  EXPIRED: 'warning', RETIRED: 'neutral',
};
const LEGAL_BASIS_TONE: Record<string, BadgeTone> = {
  CONSENT:             'primary',
  LEGITIMATE_INTEREST: 'info',
  CONTRACT:            'success',
  LEGAL_OBLIGATION:    'accent',
  VITAL_INTEREST:      'warning',
  PUBLIC_TASK:         'neutral',
};

/**
 * Detail page for a single Record of Processing Activity (PDPL Article 25).
 *
 * <p>Saudi PDPL Article 25 mandates that every controller maintain an
 * up-to-date register of processing activities ("سجل أنشطة المعالجة")
 * covering purposes, categories of data, recipients, retention, and
 * security measures. The RoPA is the primary artefact SDAIA examiners
 * request during inspections.
 *
 * <p>This page surfaces every field required by Art. 25 and links to
 * the audit timeline so changes are traceable.
 */
export function RopaDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const [item, setItem] = useState<PrivacyRopaDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<TabId>('overview');

  useEffect(() => {
    if (!id) return;
    setLoading(true); setError(null);
    privacyApi.getRopa(Number(id))
      .then(setItem)
      .catch(e => setError(e instanceof ApiError ? e.message : 'Failed to load RoPA'))
      .finally(() => setLoading(false));
  }, [id]);

  if (loading) {
    return <Shell title="PDPL"><InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty></Shell>;
  }
  if (error || !item) {
    return (
      <Shell title="PDPL">
        <div style={{ padding: 32, textAlign: 'center', color: 'var(--danger-fg)' }}>
          {error ?? (isAr ? 'لم يُعثر على السجل' : 'RoPA entry not found')}
        </div>
      </Shell>
    );
  }

  const title = isAr && item.activityNameAr ? item.activityNameAr : item.activityNameEn;
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
          { label: isAr ? 'سجل أنشطة المعالجة' : 'RoPA', to: '/privacy?tab=ropa' },
          { label: `#${item.id}` },
        ]}
        eyebrow={`RoPA #${item.id}`}
        title={title}
        sub={item.purposeEn?.slice(0, 200) ?? (isAr ? 'سجل نشاط معالجة وفق المادة 25' : 'PDPL Article 25 record')}
        badges={<>
          <Badge tone={STATUS_TONE[item.status ?? ''] ?? 'neutral'} dot>
            {(item.status ?? '—').replace(/_/g, ' ')}
          </Badge>
          {item.legalBasis && (
            <Badge tone={LEGAL_BASIS_TONE[item.legalBasis] ?? 'neutral'}>
              {item.legalBasis.replace(/_/g, ' ')}
            </Badge>
          )}
        </>}
      />

      <DetailLayout
        main={<>
          <Tabs items={tabs} active={tab} onChange={setTab} />

          {tab === 'overview' && (
            <Card title={isAr ? 'تفاصيل نشاط المعالجة' : 'Processing activity details'} Icon={ScrollText}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                <Field label={isAr ? 'اسم النشاط (إنجليزي)' : 'Activity name (English)'} value={item.activityNameEn} />
                {item.activityNameAr && (
                  <Field label={isAr ? 'اسم النشاط (عربي)' : 'Activity name (Arabic)'} value={item.activityNameAr} dir="rtl" />
                )}
                <Field
                  label={isAr ? 'غرض المعالجة' : 'Purpose of processing'}
                  value={item.purposeEn ?? (isAr ? '— غير محدد —' : '— Not specified —')}
                  multiline />

                {/* PDPL anchor */}
                <div style={{ padding: 12, background: 'var(--surface-2)',
                              borderRadius: 6, fontSize: 12, color: 'var(--ink-3)' }}>
                  {isAr
                    ? '⚖ المرجع النظامي: نظام حماية البيانات الشخصية — المادة 25 (سجل أنشطة المعالجة). يجب أن يحتوي السجل على: الأغراض، فئات البيانات، الشرائح المعنية، الأطراف المستلمة، فترات الاحتفاظ، التدابير الأمنية، ونقل البيانات خارج المملكة.'
                    : '⚖ Regulatory anchor: Saudi PDPL — Article 25 (Record of Processing Activities). The register must capture: purposes, data categories, subject categories, recipients, retention periods, security measures, and cross-border transfers.'}
                </div>

                {/* What a SAMA/SDAIA examiner expects to see */}
                <div>
                  <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase',
                                letterSpacing: '0.06em', marginBottom: 6 }}>
                    {isAr ? 'فحص الاكتمال (متطلبات سدايا)' : 'Completeness check (SDAIA examiner expectations)'}
                  </div>
                  <ul style={{ fontSize: 12, color: 'var(--ink-2)', lineHeight: 1.8, marginInlineStart: 16 }}>
                    <Check yes={!!item.activityNameEn}>{isAr ? 'اسم النشاط محدد' : 'Activity name set'}</Check>
                    <Check yes={!!item.purposeEn}>{isAr ? 'غرض المعالجة موثّق' : 'Purpose documented'}</Check>
                    <Check yes={!!item.legalBasis}>{isAr ? 'الأساس النظامي محدد' : 'Legal basis defined'}</Check>
                    <Check yes={!!item.retentionPeriod}>{isAr ? 'مدة الاحتفاظ محددة' : 'Retention period set'}</Check>
                    <Check yes={item.status === 'APPROVED' || item.status === 'ACTIVE'}>
                      {isAr ? 'تمت الموافقة / نشط' : 'Approved / active'}
                    </Check>
                  </ul>
                </div>
              </div>
            </Card>
          )}

          {tab === 'docs' && (
            <Card title={isAr ? 'المستندات وربط الضوابط' : 'Documents & control mapping'} Icon={FileText}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
                <AttachmentsField entityType="ROPA" entityId={item.id} />
                <div style={{ borderTop: '1px solid var(--border)' }} />
                <ControlMappingPicker entityType="ROPA" entityId={item.id} />
              </div>
            </Card>
          )}

          {tab === 'activity' && (
            <EntityActivityTimeline
              entityType="ProcessingActivity"
              entityId={item.id}
              titleEn="Audit timeline"
              titleAr="سجل النشاط" />
          )}
        </>}
        rail={
          <>
            <Card title={isAr ? 'الأساس النظامي' : 'Legal basis'} Icon={ScrollText}>
              <RailFieldGroup>
                <RailField
                  label={isAr ? 'الأساس' : 'Basis'}
                  value={item.legalBasis
                    ? <Badge tone={LEGAL_BASIS_TONE[item.legalBasis] ?? 'neutral'}>
                        {item.legalBasis.replace(/_/g, ' ')}
                      </Badge>
                    : '—'} />
              </RailFieldGroup>
            </Card>

            <Card title={isAr ? 'الاحتفاظ بالبيانات' : 'Data retention'} Icon={Calendar}>
              <RailFieldGroup>
                <RailField
                  label={isAr ? 'المدة' : 'Period'}
                  value={item.retentionPeriod ?? '—'}
                  mono />
              </RailFieldGroup>
            </Card>

            <Card title={isAr ? 'الحالة' : 'Status'} Icon={Database}>
              <RailFieldGroup>
                <RailField
                  label={isAr ? 'الحالة الحالية' : 'Current status'}
                  value={<Badge tone={STATUS_TONE[item.status ?? ''] ?? 'neutral'} dot>
                    {(item.status ?? '—').replace(/_/g, ' ')}
                  </Badge>} />
              </RailFieldGroup>
            </Card>

            <Card title={isAr ? 'روابط ذات صلة' : 'Related'} Icon={ChevronRight}>
              <RailFieldGroup>
                <RailField
                  label={isAr ? 'الموافقات' : 'Consents'}
                  value={<a href="/privacy?tab=consent" style={{ color: 'var(--primary)', fontSize: 12 }}>
                    {isAr ? 'افتح' : 'Open'}
                  </a>} />
                <RailField
                  label={isAr ? 'تقييم الأثر (DPIA)' : 'DPIA assessments'}
                  value={<a href="/privacy?tab=dpia" style={{ color: 'var(--primary)', fontSize: 12 }}>
                    {isAr ? 'افتح' : 'Open'}
                  </a>} />
                <RailField
                  label={isAr ? 'النقل خارج المملكة' : 'Cross-border transfers'}
                  value={<a href="/privacy?tab=alignment" style={{ color: 'var(--primary)', fontSize: 12 }}>
                    <Globe size={11} style={{ verticalAlign: 'middle', marginInlineEnd: 4 }} />
                    {isAr ? 'افتح' : 'Open'}
                  </a>} />
              </RailFieldGroup>
            </Card>
          </>
        } />
    </Shell>
  );
}

function Field({ label, value, multiline, dir }: { label: string; value: string; multiline?: boolean; dir?: 'rtl' | 'ltr' }) {
  return (
    <div>
      <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase',
                    letterSpacing: '0.06em', marginBottom: 4 }}>{label}</div>
      <div dir={dir} style={{
        fontSize: 13, color: 'var(--ink-1)',
        lineHeight: multiline ? 1.7 : 1.4,
        fontWeight: multiline ? 400 : 600,
      }}>{value}</div>
    </div>
  );
}

function Check({ yes, children }: { yes: boolean; children: React.ReactNode }) {
  return (
    <li style={{ color: yes ? 'var(--success-fg)' : 'var(--ink-3)' }}>
      {yes ? '✓' : '○'} <FileText size={11} style={{ verticalAlign: 'middle', opacity: 0.5, marginInline: 4 }} />
      {children}
    </li>
  );
}
