import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { Database, Globe2, FileSignature, History, Activity, ShieldCheck } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { AttachmentsField } from '../../components/ui/AttachmentsField';
import { ControlMappingPicker } from '../../components/ui/ControlMappingPicker';
import { useI18n } from '../../lib/i18n';
import { isAuthenticated } from '../../lib/auth';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import {
  subprocessorsApi,
  LIFECYCLE_LABEL, LIFECYCLE_TONE,
  SAFEGUARD_LABEL, SAFEGUARD_TONE,
  AUTH_TYPE_LABEL,
  LAWFUL_BASIS_LABEL,
  VOLUME_LABEL,
  EVENT_TYPE_LABEL, EVENT_TONE,
  daysUntilReview,
  type SubprocessorResponse, type SubprocessorEventResponse,
  type LifecycleStage,
} from '../../lib/subprocessors';

type Tab = 'overview' | 'safeguard' | 'dpa' | 'events' | 'activity' | 'docs';

/**
 * Sub-processor detail — Sprint 11.
 *
 * <p>Tabs:
 *   - Overview — legal name, country, purpose, lawful basis, authorisation
 *   - Cross-border Safeguard — SDAIA Article 29 / 2023 cross-border safeguard
 *   - DPA — signing dates, expiry, document references
 *   - Events — append-only ledger
 *   - Activity — internal flags (review window, DPO sign-off)
 *
 * <p>Lifecycle controls in the action footer:
 *   - Approve (DPO only — service layer gates)
 *   - Record material change (forces back to UNDER_DPO_REVIEW)
 *   - Record 12-month review
 *   - Suspend / Resume / Retire
 */
export function SubprocessorDetailPage() {
  const { externalId } = useParams<{ externalId: string }>();
  const { lang } = useI18n();
  const toast = useToast();
  const demoMode = !isAuthenticated();

  const [row, setRow] = useState<SubprocessorResponse | null>(null);
  const [events, setEvents] = useState<SubprocessorEventResponse[]>([]);
  const [tab, setTab] = useState<Tab>('overview');
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    if (!externalId || demoMode) return;
    setLoading(true);
    setError(null);
    try {
      const s = await subprocessorsApi.get(externalId);
      setRow(s);
      const ev = await subprocessorsApi.listEvents(externalId);
      setEvents(ev);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load sub-processor');
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { load(); /* eslint-disable-next-line */ }, [externalId]);

  async function approve() {
    if (!row) return;
    setBusy(true);
    try {
      const updated = await subprocessorsApi.approve(row.externalId);
      setRow(updated);
      toast.success(lang === 'ar' ? 'تمت الموافقة' : 'Approved');
      await load();
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : 'Approval failed');
    } finally {
      setBusy(false);
    }
  }

  async function review() {
    if (!row) return;
    setBusy(true);
    try {
      const updated = await subprocessorsApi.review(row.externalId);
      setRow(updated);
      toast.success(lang === 'ar' ? 'تم تسجيل المراجعة' : 'Review recorded');
      await load();
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : 'Review failed');
    } finally {
      setBusy(false);
    }
  }

  async function transition(target: LifecycleStage, label: string) {
    if (!row) return;
    setBusy(true);
    try {
      const updated = await subprocessorsApi.transition(row.externalId, target);
      setRow(updated);
      toast.success(label);
      await load();
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : 'Transition failed');
    } finally {
      setBusy(false);
    }
  }

  // Material change (PDPL Art.30) — a sub-processor change of scope / location /
  // ownership forces a fresh DPO review. Captures the bilingual change note that
  // the backend stamps onto the event timeline.
  const [showMaterialChange, setShowMaterialChange] = useState(false);
  const [mcEn, setMcEn] = useState('');
  const [mcAr, setMcAr] = useState('');
  async function submitMaterialChange() {
    if (!row || !mcEn.trim()) return;
    setBusy(true);
    try {
      const updated = await subprocessorsApi.materialChange(row.externalId, mcEn.trim(), mcAr.trim() || undefined);
      setRow(updated);
      toast.warning(lang === 'ar'
        ? 'سُجِّل تغيير جوهري — أُعيد المعالج للمراجعة'
        : 'Material change recorded — sub-processor back in DPO review');
      setShowMaterialChange(false); setMcEn(''); setMcAr('');
      await load();
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : 'Material change failed');
    } finally {
      setBusy(false);
    }
  }

  if (loading || !row) {
    return (
      <Shell title="" sub="">
        {error && <div style={{ color: 'var(--danger)', padding: 16 }}>{error}</div>}
        {loading && <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>}
      </Shell>
    );
  }

  const eventCols: Column<SubprocessorEventResponse>[] = [
    {
      header: lang === 'ar' ? 'النوع' : 'Type', width: '180px',
      cell: r => (
        <Badge tone={EVENT_TONE[r.eventType]} dot>
          {EVENT_TYPE_LABEL[r.eventType][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'التاريخ' : 'Date', width: '160px',
      cell: r => <span style={{ fontSize: 12 }}>{r.eventDate?.substring(0, 16).replace('T', ' ')}</span>,
    },
    {
      header: lang === 'ar' ? 'الوصف' : 'Description',
      cell: r => (
        <span style={{ fontSize: 12 }}>
          {lang === 'ar' && r.descriptionAr ? r.descriptionAr : r.descriptionEn}
        </span>
      ),
    },
    {
      header: lang === 'ar' ? 'إشعار العملاء' : 'DS notified', width: '120px', align: 'center',
      cell: r => r.dataSubjectsNotified
        ? <Badge tone="success">{lang === 'ar' ? 'تم' : 'Yes'}</Badge>
        : <Badge tone="neutral">{lang === 'ar' ? 'لا' : 'No'}</Badge>,
    },
  ];

  const safeguardText = row.crossBorderSafeguardType
    ? SAFEGUARD_LABEL[row.crossBorderSafeguardType][lang === 'ar' ? 'ar' : 'en']
    : (lang === 'ar' ? '— غير محدّد' : '— not set');

  const daysReview = daysUntilReview(row.nextReviewDueAt);

  return (
    <Shell
      title={lang === 'ar' && row.subProcessorLegalNameAr ? row.subProcessorLegalNameAr : row.subProcessorLegalName}
      sub={lang === 'ar' ? 'سجل معالجي البيانات الفرعيين' : 'Sub-processor register'}
    >
      <PageHeader
        eyebrow={row.subprocessorCode}
        title={lang === 'ar' && row.subProcessorLegalNameAr ? row.subProcessorLegalNameAr : row.subProcessorLegalName}
        sub={lang === 'ar'
          ? 'تفاصيل المعالج الفرعي وفق المادة 30 من نظام حماية البيانات الشخصية'
          : 'Sub-processor detail under PDPL Article 30'}
        actions={
          <>
            <Badge tone={LIFECYCLE_TONE[row.lifecycleStage]} dot>
              {LIFECYCLE_LABEL[row.lifecycleStage][lang === 'ar' ? 'ar' : 'en']}
            </Badge>
            {row.crossBorderSafeguardType && (
              <Badge tone={SAFEGUARD_TONE[row.crossBorderSafeguardType]}>
                {SAFEGUARD_LABEL[row.crossBorderSafeguardType][lang === 'ar' ? 'ar' : 'en']}
              </Badge>
            )}
          </>
        }
      />

      {/* Action bar */}
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 12 }}>
        {row.lifecycleStage === 'PROPOSED' && (
          <button onClick={() => transition('UNDER_DPO_REVIEW', lang === 'ar' ? 'أُحيل للمراجعة' : 'Sent for DPO review')}
                  disabled={busy} style={btnPrimaryStyle}>
            {lang === 'ar' ? 'إرسال للمراجعة' : 'Send for DPO review'}
          </button>
        )}
        {row.lifecycleStage === 'UNDER_DPO_REVIEW' && (
          <button onClick={approve} disabled={busy} style={btnPrimaryStyle}>
            {lang === 'ar' ? 'موافقة مسؤول حماية البيانات' : 'DPO approve'}
          </button>
        )}
        {row.lifecycleStage === 'APPROVED' && (
          <>
            <button onClick={review} disabled={busy} style={btnPrimaryStyle}>
              {lang === 'ar' ? 'تسجيل مراجعة' : 'Record review'}
            </button>
            <button onClick={() => transition('SUSPENDED', lang === 'ar' ? 'تم الإيقاف' : 'Suspended')}
                    disabled={busy} style={btnSecondaryStyle}>
              {lang === 'ar' ? 'إيقاف مؤقّت' : 'Suspend'}
            </button>
          </>
        )}
        {row.lifecycleStage === 'SUSPENDED' && (
          <button onClick={() => transition('APPROVED', lang === 'ar' ? 'تم الاستئناف' : 'Resumed')}
                  disabled={busy} style={btnPrimaryStyle}>
            {lang === 'ar' ? 'استئناف' : 'Resume'}
          </button>
        )}
        {row.lifecycleStage !== 'RETIRED' && (
          <button onClick={() => setShowMaterialChange(s => !s)}
                  disabled={busy} style={btnSecondaryStyle}>
            {lang === 'ar' ? 'تسجيل تغيير جوهري' : 'Record material change'}
          </button>
        )}
        {row.lifecycleStage !== 'RETIRED' && (
          <button onClick={() => transition('RETIRED', lang === 'ar' ? 'تم التقاعد' : 'Retired')}
                  disabled={busy} style={btnDangerStyle}>
            {lang === 'ar' ? 'تقاعد' : 'Retire'}
          </button>
        )}
      </div>

      {showMaterialChange && (
        <Card padded>
          <div style={{ fontWeight: 600, marginBottom: 8 }}>
            {lang === 'ar' ? 'تسجيل تغيير جوهري (المادة 30)' : 'Record a material change (PDPL Art. 30)'}
          </div>
          <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 10 }}>
            {lang === 'ar'
              ? 'تغيّر النطاق أو الموقع أو الملكية يُعيد المعالج الفرعي إلى مراجعة مسؤول حماية البيانات.'
              : 'A change of scope, location, or ownership returns the sub-processor to DPO review.'}
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 12 }}>
            <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              <span style={{ fontSize: 12, fontWeight: 600 }}>{lang === 'ar' ? 'الوصف (إنجليزي)' : 'Description (English)'}</span>
              <textarea value={mcEn} rows={2} onChange={e => setMcEn(e.target.value)} maxLength={2000}
                placeholder={lang === 'ar' ? 'مثال: نقل الاستضافة إلى منطقة جديدة' : 'e.g. hosting region moved to a new location'}
                style={{ padding: 6, border: '1px solid var(--border)', borderRadius: 4, fontSize: 13, fontFamily: 'inherit' }} />
            </label>
            <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              <span style={{ fontSize: 12, fontWeight: 600 }}>{lang === 'ar' ? 'الوصف (عربي)' : 'Description (Arabic)'}</span>
              <textarea value={mcAr} rows={2} dir="rtl" onChange={e => setMcAr(e.target.value)} maxLength={2000}
                style={{ padding: 6, border: '1px solid var(--border)', borderRadius: 4, fontSize: 13, fontFamily: 'inherit' }} />
            </label>
          </div>
          <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
            <button onClick={submitMaterialChange} disabled={busy || !mcEn.trim()} style={btnPrimaryStyle}>
              {busy ? (lang === 'ar' ? 'جارٍ الحفظ…' : 'Saving…') : (lang === 'ar' ? 'تسجيل' : 'Record change')}
            </button>
            <button onClick={() => { setShowMaterialChange(false); setMcEn(''); setMcAr(''); }} style={btnSecondaryStyle}>
              {lang === 'ar' ? 'إلغاء' : 'Cancel'}
            </button>
          </div>
        </Card>
      )}

      {/* Tabs */}
      <Card padded>
        <div style={{ display: 'flex', gap: 8, borderBottom: '1px solid var(--border)', marginBottom: 12 }}>
          <TabButton active={tab === 'overview'}  onClick={() => setTab('overview')}>
            {lang === 'ar' ? 'نظرة عامة' : 'Overview'}
          </TabButton>
          <TabButton active={tab === 'safeguard'} onClick={() => setTab('safeguard')}>
            {lang === 'ar' ? 'ضمانة النقل عبر الحدود' : 'Cross-border safeguard'}
          </TabButton>
          <TabButton active={tab === 'dpa'}       onClick={() => setTab('dpa')}>
            {lang === 'ar' ? 'اتفاقية معالجة البيانات (DPA)' : 'DPA'}
          </TabButton>
          <TabButton active={tab === 'events'}    onClick={() => setTab('events')}>
            {lang === 'ar' ? 'الأحداث' : 'Events'}
          </TabButton>
          <TabButton active={tab === 'activity'}  onClick={() => setTab('activity')}>
            {lang === 'ar' ? 'النشاط' : 'Activity'}
          </TabButton>
          <TabButton active={tab === 'docs'}      onClick={() => setTab('docs')}>
            {lang === 'ar' ? 'المستندات والضوابط' : 'Docs & controls'}
          </TabButton>
        </div>

        {tab === 'overview' && (
          <Grid>
            <Field icon={Database}   label={lang === 'ar' ? 'الرمز' : 'Code'}                    value={row.subprocessorCode} />
            <Field icon={FileSignature} label={lang === 'ar' ? 'الاسم النظامي' : 'Legal name'}     value={row.subProcessorLegalName} valueAr={row.subProcessorLegalNameAr} lang={lang} />
            <Field icon={Globe2}     label={lang === 'ar' ? 'الدولة' : 'Country'}                value={row.countryOfProcessing} />
            <Field icon={Globe2}     label={lang === 'ar' ? 'المنطقة' : 'Region'}                value={row.regionOfProcessing} />
            <Field icon={FileSignature} label={lang === 'ar' ? 'الغرض من المعالجة' : 'Processing purpose'} value={row.processingPurposeEn} valueAr={row.processingPurposeAr} lang={lang} wide />
            <Field icon={ShieldCheck} label={lang === 'ar' ? 'الأساس النظامي' : 'Lawful basis'}    value={LAWFUL_BASIS_LABEL[row.lawfulBasis][lang === 'ar' ? 'ar' : 'en']} />
            <Field icon={ShieldCheck} label={lang === 'ar' ? 'نوع الإذن' : 'Authorisation type'}  value={AUTH_TYPE_LABEL[row.authorisationType][lang === 'ar' ? 'ar' : 'en']} />
            <Field icon={ShieldCheck} label={lang === 'ar' ? 'مرجع وثيقة الإذن' : 'Auth doc ref'} value={row.authorisationBasisDocRef} />
            <Field icon={Database}   label={lang === 'ar' ? 'حجم البيانات' : 'Volume'}            value={VOLUME_LABEL[row.dataVolumeBand][lang === 'ar' ? 'ar' : 'en']} />
          </Grid>
        )}

        {tab === 'safeguard' && (
          <Grid>
            <Field icon={Globe2} label={lang === 'ar' ? 'نوع الضمانة' : 'Safeguard type'} value={safeguardText} wide />
            <Field icon={Globe2} label={lang === 'ar' ? 'مرجع الوثيقة' : 'Document ref'}  value={row.crossBorderSafeguardDocRef} />
            <Field icon={Globe2} label={lang === 'ar' ? 'مرجع تقييم أثر النقل (TIA)' : 'TIA reference'} value={row.tiaReference} />
            <Field icon={Globe2} label={lang === 'ar' ? 'تاريخ إكمال تقييم أثر النقل' : 'TIA completed'} value={row.tiaCompletedAt} />
            <Field icon={Globe2} label={lang === 'ar' ? 'مرجع قرار الكفاية (سدايا)' : 'SDAIA adequacy ref'} value={row.adequacyDecisionRef} />
          </Grid>
        )}

        {tab === 'dpa' && (
          <Grid>
            <Field icon={FileSignature} label={lang === 'ar' ? 'اتفاقية معالجة البيانات (DPA)' : 'DPA in place'}
                   value={row.hasDpaInPlace ? (lang === 'ar' ? 'موقّعة' : 'Signed') : (lang === 'ar' ? 'غير موقّعة' : 'Not signed')} />
            <Field icon={FileSignature} label={lang === 'ar' ? 'تاريخ التوقيع' : 'Signed date'}    value={row.dpaSignedDate} />
            <Field icon={FileSignature} label={lang === 'ar' ? 'تاريخ الانتهاء' : 'Expiry date'}   value={row.dpaExpiryDate} />
            <Field icon={FileSignature} label={lang === 'ar' ? 'مرجع وثيقة الإذن' : 'Auth doc ref'} value={row.authorisationBasisDocRef} wide />
          </Grid>
        )}

        {tab === 'events' && (
          <DataTable
            columns={eventCols}
            rows={events}
            empty={<EmptyState
              Icon={History}
              title={lang === 'ar' ? 'لا توجد أحداث' : 'No events'}
              sub={lang === 'ar'
                ? 'كل تغيير على هذا المعالج الفرعي سيُسجَّل في هذا السجل.'
                : 'Every change to this sub-processor will be recorded here.'} />}
          />
        )}

        {tab === 'activity' && (
          <Grid>
            <Field icon={Activity} label={lang === 'ar' ? 'آخر مراجعة' : 'Last reviewed'}
                   value={row.lastReviewedAt?.substring(0, 10)} />
            <Field icon={Activity} label={lang === 'ar' ? 'المراجعة القادمة' : 'Next review due'}
                   value={row.nextReviewDueAt?.substring(0, 10)} />
            <Field icon={Activity} label={lang === 'ar' ? 'حالة المراجعة' : 'Review status'}
                   value={daysReview === null
                     ? '—'
                     : daysReview < 0
                       ? (lang === 'ar' ? `متأخرة ${-daysReview} يوماً` : `${-daysReview} days overdue`)
                       : (lang === 'ar' ? `بعد ${daysReview} يوماً` : `in ${daysReview} days`)} />
            <Field icon={Activity} label={lang === 'ar' ? 'تاريخ موافقة المسؤول' : 'DPO approval date'}
                   value={row.dpoApprovedAt?.substring(0, 10)} />
            <Field icon={Activity} label={lang === 'ar' ? 'معرّف المسؤول' : 'DPO user id'} value={row.dpoApprovedByUserId?.toString()} />
            <Field icon={Activity} label={lang === 'ar' ? 'تاريخ الإنشاء' : 'Created'}    value={row.createdAt?.substring(0, 10)} />
          </Grid>
        )}

        {tab === 'docs' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
            <AttachmentsField entityType="SUBPROCESSOR" entityId={row.id} />
            <div style={{ borderTop: '1px solid var(--border)' }} />
            <ControlMappingPicker entityType="SUBPROCESSOR" entityId={row.id} />
          </div>
        )}
      </Card>
    </Shell>
  );
}

// ─── Local UI helpers ───────────────────────────────────────────────

function Grid({ children }: { children: React.ReactNode }) {
  return <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 12 }}>{children}</div>;
}

interface FieldProps {
  // Lucide icons satisfy ComponentType<any> — we just call <Icon size=12 />.
  icon?: React.ComponentType<{ size?: number | string }>;
  label: string;
  value?: string | null;
  valueAr?: string | null;
  lang?: string;
  wide?: boolean;
}
function Field({ icon: Icon, label, value, valueAr, lang, wide }: FieldProps) {
  const display = valueAr && lang === 'ar' ? valueAr : value;
  return (
    <div style={{ gridColumn: wide ? '1 / -1' : 'auto', padding: 10, border: '1px solid var(--border)', borderRadius: 6, background: 'var(--surface)' }}>
      <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: 0.4, marginBottom: 4, display: 'flex', alignItems: 'center', gap: 4 }}>
        {Icon && <Icon size={12} />}
        {label}
      </div>
      <div style={{ fontSize: 13, color: 'var(--ink-1)', wordBreak: 'break-word' }}>{display || '—'}</div>
    </div>
  );
}

interface TabButtonProps { active: boolean; onClick: () => void; children: React.ReactNode; }
function TabButton({ active, onClick, children }: TabButtonProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      style={{
        padding: '8px 16px',
        border: 'none',
        borderBottom: active ? '2px solid var(--primary)' : '2px solid transparent',
        background: 'transparent',
        color: active ? 'var(--primary)' : 'var(--ink-3)',
        fontWeight: active ? 600 : 400,
        cursor: 'pointer',
        fontSize: 14,
      }}
    >
      {children}
    </button>
  );
}

const btnPrimaryStyle: React.CSSProperties = {
  padding: '8px 14px',
  background: 'var(--primary)',
  color: 'white',
  border: 'none',
  borderRadius: 4,
  cursor: 'pointer',
  fontSize: 13,
};

const btnSecondaryStyle: React.CSSProperties = {
  padding: '8px 14px',
  background: 'var(--surface)',
  color: 'var(--ink-1)',
  border: '1px solid var(--border)',
  borderRadius: 4,
  cursor: 'pointer',
  fontSize: 13,
};

const btnDangerStyle: React.CSSProperties = {
  padding: '8px 14px',
  background: 'transparent',
  color: 'var(--danger)',
  border: '1px solid var(--danger)',
  borderRadius: 4,
  cursor: 'pointer',
  fontSize: 13,
};
