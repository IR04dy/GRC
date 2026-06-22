import { useCallback, useEffect, useMemo, useState } from 'react';
import { Cloud, Server, Globe2, AlertOctagon, ClipboardCheck, Plus, RefreshCw } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  cyberCloudServicesApi, ApiError,
  CLOUD_SERVICE_MODELS, CLOUD_DEPLOYMENT_MODELS, CLOUD_DATA_CLASSES, CLOUD_CRITICALITIES, CLOUD_STATUSES,
  type CyberCloudServiceDto,
} from '../../lib/api';

const CRIT_TONE: Record<string, BadgeTone> = { LOW: 'neutral', MEDIUM: 'info', HIGH: 'warning', CRITICAL: 'danger' };
const CLASS_TONE: Record<string, BadgeTone> = { PUBLIC: 'neutral', INTERNAL: 'info', CONFIDENTIAL: 'warning', RESTRICTED: 'danger', TOP_SECRET: 'danger' };

const STATUS_LABEL_AR: Record<string, string> = {
  ACTIVE: 'نشط', UNDER_REVIEW: 'قيد المراجعة', DEPROVISIONED: 'مُلغى التزويد', ARCHIVED: 'مؤرشف',
};
const CRIT_LABEL_AR: Record<string, string> = { LOW: 'منخفض', MEDIUM: 'متوسط', HIGH: 'مرتفع', CRITICAL: 'حرج' };
const CLASS_LABEL_AR: Record<string, string> = {
  PUBLIC: 'عام', INTERNAL: 'داخلي', CONFIDENTIAL: 'سري', RESTRICTED: 'مقيّد', TOP_SECRET: 'سري للغاية',
};
const CSCC_TIERS = ['TIER_1', 'TIER_2', 'TIER_3'];
const YESNO = (isAr: boolean) => [{ value: 'true', label: isAr ? 'نعم' : 'Yes' }, { value: 'false', label: isAr ? 'لا' : 'No' }];

const SELECT_STYLE: React.CSSProperties = {
  padding: '6px 10px', fontSize: 12, minWidth: 130, border: '1px solid var(--border)',
  borderRadius: 'var(--r-sm)', background: 'var(--surface-1)', color: 'var(--ink-1)',
};

interface CloudForm {
  serviceName: string; provider: string; serviceModel: string; deploymentModel: string;
  dataClassification: string; residencyRegion: string; kingdomDataResident: string; csccTier: string;
  criticality: string; status: string; ssoEnabled: string; mfaEnforced: string;
  encryptionAtRest: string; encryptionInTransit: string; notes: string;
}

/** Small green/grey security-posture chip. */
function PostureChip({ on, label }: { on: boolean; label: string }) {
  return (
    <span className="mono text-2xs" style={{
      padding: '1px 5px', borderRadius: 'var(--r-sm)', marginInlineEnd: 4,
      background: on ? 'var(--success-bg)' : 'var(--surface-2)',
      color: on ? 'var(--success-fg)' : 'var(--ink-3)',
      border: `1px solid ${on ? 'var(--success-border)' : 'var(--border)'}`,
    }}>{label}</span>
  );
}

/**
 * Cyber cloud-service inventory — surfaces /api/v1/cyber/cloud-services (NCA CSCC
 * §1.1 cloud catalogue). One row per (service × provider) with residency, CSCC
 * tier, criticality and security posture. Distinct from the outsourcing/SAMA
 * materiality view. Create + inline status transition; status/criticality filters.
 */
export function CyberCloudServicesPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [rows, setRows] = useState<CyberCloudServiceDto[]>([]);
  const [stats, setStats] = useState<Record<string, number> | null>(null);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState('');
  const [critFilter, setCritFilter] = useState('');
  const [formOpen, setFormOpen] = useState(false);
  const [busyId, setBusyId] = useState<number | null>(null);

  const statusLabel = (s: string) => (isAr ? (STATUS_LABEL_AR[s] ?? s) : s.replace(/_/g, ' '));
  const critLabel = (c: string) => (isAr ? (CRIT_LABEL_AR[c] ?? c) : c);
  const classLabel = (c: string) => (isAr ? (CLASS_LABEL_AR[c] ?? c) : c);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRows(await cyberCloudServicesApi.list());
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'تعذّر تحميل الخدمات السحابية' : 'Failed to load cloud services'));
      setRows([]);
    } finally { setLoading(false); }
  }, []);  // eslint-disable-line react-hooks/exhaustive-deps
  const loadStats = useCallback(() => { cyberCloudServicesApi.stats().then(setStats).catch(() => setStats(null)); }, []);
  useEffect(() => { void load(); }, [load]);
  useEffect(() => { loadStats(); }, [loadStats]);

  const filtered = useMemo(() => rows.filter(r =>
    (!statusFilter || r.status === statusFilter) && (!critFilter || r.criticality === critFilter)
  ), [rows, statusFilter, critFilter]);

  async function createService(v: CloudForm): Promise<void> {
    if (!v.serviceName.trim()) { toast.danger(isAr ? 'اسم الخدمة مطلوب' : 'Service name is required'); return; }
    if (!v.provider.trim()) { toast.danger(isAr ? 'المزوّد مطلوب' : 'Provider is required'); return; }
    try {
      await cyberCloudServicesApi.create({
        serviceName: v.serviceName.trim(), provider: v.provider.trim(),
        serviceModel: v.serviceModel, deploymentModel: v.deploymentModel,
        dataClassification: v.dataClassification || undefined,
        residencyRegion: v.residencyRegion.trim() || undefined,
        kingdomDataResident: v.kingdomDataResident === 'true',
        csccTier: v.csccTier || undefined,
        criticality: v.criticality, status: v.status,
        ssoEnabled: v.ssoEnabled === 'true', mfaEnforced: v.mfaEnforced === 'true',
        encryptionAtRest: v.encryptionAtRest === 'true', encryptionInTransit: v.encryptionInTransit === 'true',
        notes: v.notes.trim() || undefined,
      });
      toast.success(isAr ? 'تم تسجيل الخدمة السحابية' : 'Cloud service registered');
      setFormOpen(false);
      await load(); loadStats();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل الإنشاء' : 'Create failed'));
    }
  }

  async function changeStatus(svc: CyberCloudServiceDto, status: string) {
    if (!status || status === svc.status) return;
    setBusyId(svc.id);
    try {
      await cyberCloudServicesApi.update(svc.id, { status });
      toast.success(isAr ? `${svc.serviceName} → ${statusLabel(status)}` : `${svc.serviceName} → ${status.replace(/_/g, ' ')}`);
      await load(); loadStats();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل التحديث' : 'Update failed'));
    } finally { setBusyId(null); }
  }

  const formFields: FieldDef[] = [
    { kind: 'text', key: 'serviceName', label: isAr ? 'اسم الخدمة *' : 'Service name *', required: true, span: 6, maxLength: 255 },
    { kind: 'text', key: 'provider', label: isAr ? 'المزوّد *' : 'Provider *', required: true, span: 6, maxLength: 255 },
    { kind: 'select', key: 'serviceModel', label: isAr ? 'نموذج الخدمة' : 'Service model', span: 4, options: CLOUD_SERVICE_MODELS.map(m => ({ value: m, label: m })) },
    { kind: 'select', key: 'deploymentModel', label: isAr ? 'نموذج النشر' : 'Deployment', span: 4, options: CLOUD_DEPLOYMENT_MODELS.map(m => ({ value: m, label: m })) },
    { kind: 'select', key: 'dataClassification', label: isAr ? 'تصنيف البيانات' : 'Data class', span: 4, options: CLOUD_DATA_CLASSES.map(c => ({ value: c, label: classLabel(c) })) },
    { kind: 'text', key: 'residencyRegion', label: isAr ? 'منطقة الاستضافة' : 'Residency region', span: 6, maxLength: 100 },
    { kind: 'select', key: 'kingdomDataResident', label: isAr ? 'مُستضاف داخل المملكة' : 'Kingdom-resident', span: 3, options: YESNO(isAr) },
    { kind: 'select', key: 'csccTier', label: isAr ? 'فئة CSCC' : 'CSCC tier', span: 3, options: CSCC_TIERS.map(t => ({ value: t, label: t.replace('_', ' ') })) },
    { kind: 'select', key: 'criticality', label: isAr ? 'الأهمية' : 'Criticality', span: 6, options: CLOUD_CRITICALITIES.map(c => ({ value: c, label: critLabel(c) })) },
    { kind: 'select', key: 'status', label: isAr ? 'الحالة' : 'Status', span: 6, options: CLOUD_STATUSES.map(s => ({ value: s, label: statusLabel(s) })) },
    { kind: 'select', key: 'ssoEnabled', label: isAr ? 'الدخول الموحّد (SSO)' : 'SSO', span: 3, options: YESNO(isAr) },
    { kind: 'select', key: 'mfaEnforced', label: isAr ? 'التحقق متعدد العوامل' : 'MFA', span: 3, options: YESNO(isAr) },
    { kind: 'select', key: 'encryptionAtRest', label: isAr ? 'تشفير التخزين' : 'Enc. at rest', span: 3, options: YESNO(isAr) },
    { kind: 'select', key: 'encryptionInTransit', label: isAr ? 'تشفير النقل' : 'Enc. in transit', span: 3, options: YESNO(isAr) },
    { kind: 'textarea', key: 'notes', label: isAr ? 'ملاحظات' : 'Notes', rows: 2, span: 12 },
  ];
  const formInitial: CloudForm = {
    serviceName: '', provider: '', serviceModel: 'SAAS', deploymentModel: 'PUBLIC',
    dataClassification: 'INTERNAL', residencyRegion: '', kingdomDataResident: 'false', csccTier: 'TIER_2',
    criticality: 'MEDIUM', status: 'ACTIVE', ssoEnabled: 'true', mfaEnforced: 'true',
    encryptionAtRest: 'true', encryptionInTransit: 'true', notes: '',
  };

  return (
    <Shell title={isAr ? 'الخدمات السحابية' : 'Cloud services'} sub={isAr ? 'جرد الخدمات السحابية — NCA CSCC' : 'Cloud-service inventory — NCA CSCC'}>
      <PageHeader
        eyebrow={isAr ? 'الأمن السيبراني · NCA CSCC' : 'Cybersecurity · NCA CSCC'}
        title={isAr ? 'الخدمات السحابية' : 'Cloud services'}
        sub={isAr
          ? 'جرد الخدمات السحابية المستخدمة عبر المنشأة — مع منطقة الاستضافة، فئة CSCC، الأهمية، ووضع الأمان لكل خدمة.'
          : 'A tenant-wide inventory of cloud services in use — residency, CSCC tier, criticality and per-service security posture.'}
      />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Server}       label={isAr ? 'خدمات نشطة' : 'Active services'} value={stats?.status_active ?? '—'} tone="primary"
          onClick={() => setStatusFilter(statusFilter === 'ACTIVE' ? '' : 'ACTIVE')} active={statusFilter === 'ACTIVE'} />
        <Kpi Icon={Globe2}       label={isAr ? 'خارج المملكة' : 'Off-Kingdom'} value={stats?.off_kingdom ?? '—'} tone="danger" />
        <Kpi Icon={AlertOctagon} label={isAr ? 'أهمية حرجة' : 'Critical'} value={stats?.crit_critical ?? '—'} tone="danger"
          onClick={() => setCritFilter(critFilter === 'CRITICAL' ? '' : 'CRITICAL')} active={critFilter === 'CRITICAL'} />
        <Kpi Icon={ClipboardCheck} label={isAr ? 'قيد المراجعة' : 'Under review'} value={stats?.status_under_review ?? '—'} tone="warning"
          onClick={() => setStatusFilter(statusFilter === 'UNDER_REVIEW' ? '' : 'UNDER_REVIEW')} active={statusFilter === 'UNDER_REVIEW'} />
      </div>

      <Card title={isAr ? 'سجل الخدمات السحابية' : 'Cloud-service register'} Icon={Cloud}
        action={
          <div style={{ display: 'inline-flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
            <select value={statusFilter} onChange={e => setStatusFilter(e.target.value)} style={SELECT_STYLE} aria-label={isAr ? 'الحالة' : 'Status'}>
              <option value="">{isAr ? 'كل الحالات' : 'All statuses'}</option>
              {CLOUD_STATUSES.map(s => <option key={s} value={s}>{statusLabel(s)}</option>)}
            </select>
            <select value={critFilter} onChange={e => setCritFilter(e.target.value)} style={SELECT_STYLE} aria-label={isAr ? 'الأهمية' : 'Criticality'}>
              <option value="">{isAr ? 'كل المستويات' : 'All criticalities'}</option>
              {CLOUD_CRITICALITIES.map(c => <option key={c} value={c}>{critLabel(c)}</option>)}
            </select>
            <Button size="sm" variant="ghost" Icon={RefreshCw} onClick={() => { void load(); loadStats(); }} disabled={loading}>{isAr ? 'تحديث' : 'Refresh'}</Button>
            <Button size="sm" variant="primary" Icon={Plus} onClick={() => setFormOpen(true)}>{isAr ? 'خدمة جديدة' : 'New service'}</Button>
          </div>
        }>
        {loading && rows.length === 0
          ? <div style={{ padding: 16, color: 'var(--ink-3)', fontSize: 13 }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div>
          : filtered.length === 0
            ? <div style={{ padding: 16, color: 'var(--ink-3)', fontSize: 13 }}>{isAr ? 'لا توجد خدمات' : 'No services'}</div>
            : <table className="grc-table">
                <thead><tr>
                  <th>{isAr ? 'الخدمة' : 'Service'}</th>
                  <th>{isAr ? 'النموذج' : 'Model'}</th>
                  <th>{isAr ? 'التصنيف' : 'Class'}</th>
                  <th>{isAr ? 'الاستضافة' : 'Residency'}</th>
                  <th>{isAr ? 'CSCC' : 'CSCC'}</th>
                  <th>{isAr ? 'الأهمية' : 'Criticality'}</th>
                  <th>{isAr ? 'وضع الأمان' : 'Posture'}</th>
                  <th style={{ textAlign: 'end' }}>{isAr ? 'الحالة' : 'Status'}</th>
                </tr></thead>
                <tbody>
                  {filtered.map(r => (
                    <tr key={r.id}>
                      <td>
                        <div style={{ fontWeight: 600, fontSize: 13 }}>{r.serviceName}</div>
                        <div className="text-2xs text-muted">{r.provider}</div>
                      </td>
                      <td className="text-2xs">{r.serviceModel} · {r.deploymentModel}</td>
                      <td>{r.dataClassification ? <Badge tone={CLASS_TONE[r.dataClassification] ?? 'neutral'}>{classLabel(r.dataClassification)}</Badge> : <span className="text-2xs text-muted">—</span>}</td>
                      <td>
                        <Badge tone={r.kingdomDataResident ? 'success' : 'danger'} dot>{r.kingdomDataResident ? (isAr ? 'داخل المملكة' : 'KSA') : (isAr ? 'خارج المملكة' : 'Off-KSA')}</Badge>
                        {r.residencyRegion && <div className="text-2xs text-muted">{r.residencyRegion}</div>}
                      </td>
                      <td className="mono text-2xs">{r.csccTier ? r.csccTier.replace('_', ' ') : '—'}</td>
                      <td><Badge tone={CRIT_TONE[r.criticality] ?? 'neutral'}>{critLabel(r.criticality)}</Badge></td>
                      <td style={{ whiteSpace: 'nowrap' }}>
                        <PostureChip on={r.ssoEnabled} label="SSO" />
                        <PostureChip on={r.mfaEnforced} label="MFA" />
                        <PostureChip on={r.encryptionAtRest} label={isAr ? 'تخزين' : 'E@R'} />
                        <PostureChip on={r.encryptionInTransit} label={isAr ? 'نقل' : 'TLS'} />
                      </td>
                      <td style={{ textAlign: 'end' }}>
                        <select value={r.status} disabled={busyId === r.id}
                          onChange={e => void changeStatus(r, e.target.value)}
                          style={{ ...SELECT_STYLE, minWidth: 120 }} aria-label={isAr ? 'الحالة' : 'Status'}>
                          {CLOUD_STATUSES.map(s => <option key={s} value={s}>{statusLabel(s)}</option>)}
                        </select>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>}
      </Card>

      <FormModal<CloudForm>
        open={formOpen}
        title={isAr ? 'تسجيل خدمة سحابية' : 'Register cloud service'}
        fields={formFields}
        initial={formInitial}
        onClose={() => setFormOpen(false)}
        onSubmit={createService}
        submitLabel={isAr ? 'تسجيل' : 'Register'}
      />
    </Shell>
  );
}
