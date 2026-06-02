import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Server, ShieldCheck, Archive, ArrowUpRight } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { AttachmentsField } from '../../components/ui/AttachmentsField';
import { ControlMappingPicker } from '../../components/ui/ControlMappingPicker';
import { EvidenceReadinessPanel } from '../../components/ui/EvidenceReadinessPanel';
import { Badge } from '../../components/ui/Badge';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { isAuthenticated } from '../../lib/auth';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import {
  asmApi,
  ASM_ASSET_TYPE_LABEL,
  ASM_EXPOSURE_LABEL, ASM_EXPOSURE_TONE,
  ASM_CRITICALITY_LABEL, ASM_CRITICALITY_TONE,
  ASM_LIFECYCLE_LABEL, ASM_LIFECYCLE_TONE,
  ASM_DISCOVERY_SOURCE_LABEL,
  ASM_FINDING_TYPE_LABEL,
  ASM_FINDING_SEVERITY_LABEL, ASM_FINDING_SEVERITY_TONE,
  ASM_FINDING_STATUS_LABEL, ASM_FINDING_STATUS_TONE,
  formatRiskScore, riskScoreTone,
  type AsmAssetResponse, type AsmFindingResponse, type AsmLifecycleStage,
} from '../../lib/asm';

/**
 * ASM Asset detail — Sprint 11.
 *
 * <p>Layout:
 *   - Header card with bilingual title + KPI strip (risk score,
 *     exposure, criticality, lifecycle).
 *   - Overview card with hostname / IP / port / protocol and the
 *     cert subject + issuer + expiry when present.
 *   - Risk-score breakdown card explaining the formula and current
 *     drivers.
 *   - Linked findings table.
 *   - Action footer: "Promote to formal inventory" (for
 *     SHADOW_IT_PENDING → APPROVED) and "Retire" (terminal).
 */
export function AttackSurfaceAssetDetailPage() {
  const { externalId } = useParams<{ externalId: string }>();
  const { lang } = useI18n();
  const toast = useToast();
  const navigate = useNavigate();
  const demoMode = !isAuthenticated();

  const [asset, setAsset] = useState<AsmAssetResponse | null>(null);
  const [findings, setFindings] = useState<AsmFindingResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    if (!externalId || demoMode) return;
    setLoading(true);
    setError(null);
    try {
      const a = await asmApi.getAsset(externalId);
      setAsset(a);
      const f = await asmApi.listFindingsForAsset(externalId);
      setFindings(f);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load asset');
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { load(); /* eslint-disable-next-line */ }, [externalId]);

  async function transition(targetStage: AsmLifecycleStage, label: string) {
    if (!asset) return;
    setBusy(true);
    try {
      const updated = await asmApi.transition(asset.externalId, targetStage);
      setAsset(updated);
      toast.success(label);
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : 'Transition failed');
    } finally {
      setBusy(false);
    }
  }

  // ─── Findings table columns ─────────────────────────────────────

  const findingCols: Column<AsmFindingResponse>[] = [
    {
      header: lang === 'ar' ? 'النوع' : 'Type', width: '220px',
      cell: r => (
        <span style={{ fontSize: 12 }}>
          {ASM_FINDING_TYPE_LABEL[r.findingType][lang === 'ar' ? 'ar' : 'en']}
        </span>
      ),
    },
    {
      header: lang === 'ar' ? 'الخطورة' : 'Severity', width: '110px', align: 'center',
      cell: r => (
        <Badge tone={ASM_FINDING_SEVERITY_TONE[r.severity]}>
          {ASM_FINDING_SEVERITY_LABEL[r.severity][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'الحالة' : 'Status', width: '140px', align: 'center',
      cell: r => (
        <Badge tone={ASM_FINDING_STATUS_TONE[r.status]} dot>
          {ASM_FINDING_STATUS_LABEL[r.status][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
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
      header: lang === 'ar' ? 'فُتح' : 'Opened', width: '160px',
      cell: r => <span style={{ fontSize: 12 }}>{r.openedAt?.substring(0, 10) ?? '—'}</span>,
    },
  ];

  // ─── Render ─────────────────────────────────────────────────────

  if (loading) {
    return (
      <Shell title={lang === 'ar' ? 'تفاصيل الأصل' : 'Asset detail'}
             sub={lang === 'ar' ? 'إدارة سطح الهجوم' : 'Attack Surface Management'}>
        <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
      </Shell>
    );
  }
  if (!asset) {
    return (
      <Shell title={lang === 'ar' ? 'تفاصيل الأصل' : 'Asset detail'}
             sub={lang === 'ar' ? 'إدارة سطح الهجوم' : 'Attack Surface Management'}>
        <Card padded>
          <EmptyState
            Icon={Server}
            title={lang === 'ar' ? 'الأصل غير موجود' : 'Asset not found'}
            sub={error ?? (lang === 'ar'
              ? 'لم يتمّ العثور على الأصل المطلوب في سجل المنظمة الحالية.'
              : 'No matching asset in the current tenant.')}
          />
        </Card>
      </Shell>
    );
  }

  const canPromote = asset.lifecycleStage === 'SHADOW_IT_PENDING' || asset.lifecycleStage === 'IN_REVIEW' || asset.lifecycleStage === 'DISCOVERED';
  const canRetire = asset.lifecycleStage !== 'RETIRED';

  return (
    <Shell
      title={lang === 'ar' && asset.titleAr ? asset.titleAr : asset.titleEn}
      sub={lang === 'ar' ? 'إدارة سطح الهجوم' : 'Attack Surface Management'}
    >
      <PageHeader
        eyebrow={lang === 'ar' ? 'إدارة سطح الهجوم' : 'Attack Surface'}
        title={lang === 'ar' && asset.titleAr ? asset.titleAr : asset.titleEn}
        sub={asset.assetCode}
        actions={
          <div style={{ display: 'flex', gap: 8 }}>
            <button type="button" onClick={() => navigate('/cyber/attack-surface')}
                    style={btnStyle()}>
              {lang === 'ar' ? 'العودة للسجل' : 'Back to register'}
            </button>
            {canPromote && (
              <button type="button"
                      disabled={busy}
                      onClick={() => transition('APPROVED', lang === 'ar' ? 'تم اعتماد الأصل في السجل الرسمي' : 'Promoted to formal inventory')}
                      style={btnStyle('var(--success)')}
                      title={lang === 'ar' ? 'اعتماد الأصل ضمن السجل الرسمي' : 'Promote to formal inventory'}>
                <ArrowUpRight size={14} />{' '}
                {lang === 'ar' ? 'اعتماد رسمي' : 'Promote'}
              </button>
            )}
            {canRetire && (
              <button type="button"
                      disabled={busy}
                      onClick={() => transition('RETIRED', lang === 'ar' ? 'تم تقاعد الأصل' : 'Asset retired')}
                      style={btnStyle('var(--danger)')}
                      title={lang === 'ar' ? 'إنهاء الأصل (نهائي)' : 'Retire (terminal)'}>
                <Archive size={14} />{' '}
                {lang === 'ar' ? 'تقاعد' : 'Retire'}
              </button>
            )}
          </div>
        }
      />

      {/* Top KPI strip */}
      <Card padded>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 16 }}>
          <KpiBox
            label={lang === 'ar' ? 'درجة الخطورة' : 'Risk score'}
            value={
              <Badge tone={riskScoreTone(asset.riskScore)}>{formatRiskScore(asset.riskScore)}</Badge>
            } />
          <KpiBox
            label={lang === 'ar' ? 'التعرّض' : 'Exposure'}
            value={
              <Badge tone={ASM_EXPOSURE_TONE[asset.exposure]}>
                {ASM_EXPOSURE_LABEL[asset.exposure][lang === 'ar' ? 'ar' : 'en']}
              </Badge>
            } />
          <KpiBox
            label={lang === 'ar' ? 'الأهمية' : 'Criticality'}
            value={
              <Badge tone={ASM_CRITICALITY_TONE[asset.criticality]}>
                {ASM_CRITICALITY_LABEL[asset.criticality][lang === 'ar' ? 'ar' : 'en']}
              </Badge>
            } />
          <KpiBox
            label={lang === 'ar' ? 'دورة الحياة' : 'Lifecycle'}
            value={
              <Badge tone={ASM_LIFECYCLE_TONE[asset.lifecycleStage]} dot>
                {ASM_LIFECYCLE_LABEL[asset.lifecycleStage][lang === 'ar' ? 'ar' : 'en']}
              </Badge>
            } />
        </div>
      </Card>

      {/* Overview */}
      <Card padded title={lang === 'ar' ? 'نظرة عامة' : 'Overview'}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 16 }}>
          <KvRow k={lang === 'ar' ? 'النوع' : 'Asset type'}
                 v={ASM_ASSET_TYPE_LABEL[asset.assetType][lang === 'ar' ? 'ar' : 'en']} />
          <KvRow k={lang === 'ar' ? 'الاسم المضيف' : 'Hostname'} v={asset.hostname} />
          <KvRow k={lang === 'ar' ? 'عنوان IP' : 'IP address'} v={asset.ipAddress} />
          <KvRow k={lang === 'ar' ? 'المنفذ' : 'Port'} v={asset.port?.toString()} />
          <KvRow k={lang === 'ar' ? 'البروتوكول' : 'Protocol'} v={asset.protocol} />
          <KvRow k={lang === 'ar' ? 'وحدة الأعمال' : 'Business unit'}
                 v={lang === 'ar' ? asset.businessUnitAr : asset.businessUnitEn} />
          <KvRow k={lang === 'ar' ? 'مصدر الاكتشاف' : 'Discovery source'}
                 v={ASM_DISCOVERY_SOURCE_LABEL[asset.discoverySource]?.[lang === 'ar' ? 'ar' : 'en']} />
          <KvRow k={lang === 'ar' ? 'الوسوم' : 'Tags'} v={asset.tags} />
        </div>
        {asset.descriptionEn && (
          <p style={{ marginTop: 16, color: 'var(--ink-3)', lineHeight: 1.6 }}>
            {lang === 'ar' && asset.descriptionAr ? asset.descriptionAr : asset.descriptionEn}
          </p>
        )}
      </Card>

      {/* Cert info */}
      {(asset.certSubject || asset.certIssuer || asset.certExpiresAt) && (
        <Card padded title={lang === 'ar' ? 'معلومات الشهادة' : 'Certificate info'}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 16 }}>
            <KvRow k={lang === 'ar' ? 'موضوع الشهادة' : 'Subject'} v={asset.certSubject} />
            <KvRow k={lang === 'ar' ? 'الجهة المُصدِرة' : 'Issuer'} v={asset.certIssuer} />
            <KvRow k={lang === 'ar' ? 'تاريخ الانتهاء' : 'Expires'}
                   v={asset.certExpiresAt?.substring(0, 10)} />
          </div>
        </Card>
      )}

      {/* Risk-score breakdown */}
      <Card padded title={lang === 'ar' ? 'تفسير درجة الخطورة' : 'Risk score breakdown'}>
        <p style={{ color: 'var(--ink-3)', fontSize: 13, lineHeight: 1.6 }}>
          {lang === 'ar'
            ? 'الصيغة: درجة الخطورة = وزن أعلى نتيجة مفتوحة × معامل التعرّض × معامل الأهمية. عند عدم وجود نتائج مفتوحة تُحسب على أساس خطورة "منخفضة" افتراضياً (15).'
            : 'Formula: risk_score = highest-open-finding-severity × exposure × criticality. Defaults to LOW (15) when no open findings.'}
        </p>
        <ul style={{ marginTop: 12, color: 'var(--ink-1)', fontSize: 13, lineHeight: 1.8 }}>
          <li>
            <strong>{lang === 'ar' ? 'التعرّض' : 'Exposure'}: </strong>
            {ASM_EXPOSURE_LABEL[asset.exposure][lang === 'ar' ? 'ar' : 'en']}
            {' '}({asset.exposure === 'PUBLIC' ? '×1.0'
                : asset.exposure === 'PARTNER' ? '×0.7'
                : asset.exposure === 'INTERNAL' ? '×0.4' : '×0.2'})
          </li>
          <li>
            <strong>{lang === 'ar' ? 'الأهمية' : 'Criticality'}: </strong>
            {ASM_CRITICALITY_LABEL[asset.criticality][lang === 'ar' ? 'ar' : 'en']}
            {' '}({asset.criticality === 'CRITICAL' ? '×1.0'
                : asset.criticality === 'HIGH' ? '×0.85'
                : asset.criticality === 'MEDIUM' ? '×0.6' : '×0.3'})
          </li>
          <li>
            <strong>{lang === 'ar' ? 'النتائج المفتوحة' : 'Open findings'}: </strong>
            {findings.filter(f => f.status === 'OPEN' || f.status === 'INVESTIGATING').length}
          </li>
          <li>
            <strong>{lang === 'ar' ? 'الدرجة المُحتسَبة' : 'Computed score'}: </strong>
            <Badge tone={riskScoreTone(asset.riskScore)}>{formatRiskScore(asset.riskScore)}</Badge>
            {' '}/ 100
          </li>
        </ul>
      </Card>

      {/* Findings */}
      <Card padded title={lang === 'ar' ? 'النتائج المرتبطة' : 'Linked findings'}>
        <DataTable
          columns={findingCols}
          rows={findings}
          empty={<EmptyState
            Icon={ShieldCheck}
            title={lang === 'ar' ? 'لا توجد نتائج' : 'No findings'}
            sub={lang === 'ar'
              ? 'لا توجد نتائج مُسجَّلة على هذا الأصل حالياً.'
              : 'No findings have been recorded against this asset yet.'} />}
        />
      </Card>

      {/* Documents & control mapping — scan reports, remediation evidence, controls */}
      {asset && (
        <div style={{ marginTop: 16 }}>
          <Card padded title={lang === 'ar' ? 'المستندات وربط الضوابط' : 'Documents & control mapping'}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
              <AttachmentsField entityType="ASM_ASSET" entityId={asset.id} />
              <div style={{ borderTop: '1px solid var(--border)' }} />
              <ControlMappingPicker entityType="ASM_ASSET" entityId={asset.id} evidence={false} />
              <div style={{ borderTop: '1px solid var(--border)' }} />
              <div>
                <div style={{ fontWeight: 700, marginBottom: 8 }}>{lang === 'ar' ? 'جاهزية الأدلة' : 'Evidence readiness'}</div>
                <EvidenceReadinessPanel entityType="ASM_ASSET" entityId={asset.id} />
              </div>
            </div>
          </Card>
        </div>
      )}
    </Shell>
  );
}

// ─── Local helpers ────────────────────────────────────────────────────

interface KpiBoxProps { label: string; value: React.ReactNode; }
function KpiBox({ label, value }: KpiBoxProps) {
  return (
    <div style={{
      padding: 12,
      border: '1px solid var(--border)',
      borderRadius: 6,
      background: 'var(--surface-2, var(--surface))',
    }}>
      <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: 0.4, marginBottom: 6 }}>
        {label}
      </div>
      <div style={{ fontSize: 16 }}>{value}</div>
    </div>
  );
}

interface KvRowProps { k: string; v?: string | null; }
function KvRow({ k, v }: KvRowProps) {
  return (
    <div>
      <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: 0.4, marginBottom: 4 }}>
        {k}
      </div>
      <div style={{ fontSize: 14, color: 'var(--ink-1)' }}>{v && v.length > 0 ? v : '—'}</div>
    </div>
  );
}

function btnStyle(accent?: string): React.CSSProperties {
  return {
    padding: '6px 14px',
    border: `1px solid ${accent ?? 'var(--border)'}`,
    borderRadius: 4,
    background: 'var(--surface)',
    color: accent ?? 'var(--ink-1)',
    cursor: 'pointer',
    fontSize: 13,
    display: 'inline-flex',
    alignItems: 'center',
    gap: 6,
  };
}
