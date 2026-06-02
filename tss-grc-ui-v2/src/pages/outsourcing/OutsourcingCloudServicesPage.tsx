import { useEffect, useState } from 'react';
import { Plus, Cloud } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';
import {
  outsourcingCloudApi,
  CLOUD_STATUS_LABEL, CLOUD_STATUS_TONE,
  DATA_CLASSIFICATION_LABEL, DATA_RESIDENCY_LABEL,
  nextCloudStatuses,
  type CloudServiceResponse, type CloudStatus,
  type DeploymentModel, type CspBrand,
  type DataClassification, type DataResidency,
  type ArrangementResponse,
} from '../../lib/outsourcingCloud';

const DEPLOYMENTS: DeploymentModel[] = ['SAAS','PAAS','IAAS','FAAS','HYBRID'];
const CLASSIFICATIONS: DataClassification[] = ['T1','T2','T3','T4'];
const RESIDENCIES: DataResidency[] = ['IN_KINGDOM','GCC','GLOBAL'];
const CSP_BRANDS: CspBrand[] = ['AWS','AZURE','GOOGLE_CLOUD','ORACLE_CLOUD','SALESFORCE','STC_CLOUD','MOBILY_CLOUD','OTHER'];

/** Sprint 30 — Cloud Services register (SAMA Cloud Computing Framework). */
export function OutsourcingCloudServicesPage() {
  const { lang } = useI18n();
  const demoMode = !isAuthenticated();

  const [rows, setRows] = useState<CloudServiceResponse[]>([]);
  const [arrangements, setArrangements] = useState<ArrangementResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [acting, setActing] = useState(false);

  // create form
  const [showNew, setShowNew] = useState(false);
  const [arrangementId, setArrangementId] = useState<number | ''>('');
  const [ref, setRef] = useState('');
  const [nameEn, setNameEn] = useState('');
  const [nameAr, setNameAr] = useState('');
  const [deployment, setDeployment] = useState<DeploymentModel>('SAAS');
  const [cspEn, setCspEn] = useState('');
  const [cspAr, setCspAr] = useState('');
  const [cspBrand, setCspBrand] = useState<CspBrand | ''>('');
  const [classification, setClassification] = useState<DataClassification | ''>('');
  const [residency, setResidency] = useState<DataResidency | ''>('');
  const [region, setRegion] = useState('');

  // transition form
  const [moveTarget, setMoveTarget] = useState<CloudServiceResponse | null>(null);
  const [toStatus, setToStatus] = useState<CloudStatus | null>(null);
  const [blockReasonEn, setBlockReasonEn] = useState('');
  const [blockReasonAr, setBlockReasonAr] = useState('');

  const reload = async () => {
    if (demoMode) return;
    setLoading(true); setError(null);
    try {
      const [svc, arr] = await Promise.all([
        outsourcingCloudApi.listCloudServices(),
        outsourcingCloudApi.listArrangements(),
      ]);
      setRows(svc); setArrangements(arr);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تحميل سجل خدمات السحابة' : 'Failed to load cloud services'));
    } finally { setLoading(false); }
  };
  useEffect(() => { reload(); /* eslint-disable-next-line */ }, []);

  const submit = async () => {
    if (typeof arrangementId !== 'number' || !ref.trim() ||
        !nameEn.trim() || !nameAr.trim() || !cspEn.trim() || !cspAr.trim()) return;
    setActing(true);
    try {
      await outsourcingCloudApi.createCloudService({
        arrangementId,
        reference: ref.trim(),
        serviceNameEn: nameEn.trim(),
        serviceNameAr: nameAr.trim(),
        deploymentModel: deployment,
        cspLegalNameEn: cspEn.trim(),
        cspLegalNameAr: cspAr.trim(),
        cspBrand: cspBrand || undefined,
        dataClassification: classification || undefined,
        dataResidency: residency || undefined,
        cspRegionLabel: region || undefined,
      });
      setShowNew(false);
      setArrangementId(''); setRef(''); setNameEn(''); setNameAr('');
      setCspEn(''); setCspAr(''); setCspBrand('');
      setClassification(''); setResidency(''); setRegion('');
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر إنشاء خدمة السحابة' : 'Failed to create cloud service'));
    } finally { setActing(false); }
  };

  const submitTransition = async () => {
    if (!moveTarget || !toStatus) return;
    setActing(true);
    try {
      await outsourcingCloudApi.transitionCloudService(moveTarget.id, {
        toStatus,
        blockReasonEn: blockReasonEn || undefined,
        blockReasonAr: blockReasonAr || undefined,
      });
      setMoveTarget(null); setToStatus(null);
      setBlockReasonEn(''); setBlockReasonAr('');
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تنفيذ التحوّل' : 'Failed to transition'));
    } finally { setActing(false); }
  };

  const cols: Column<CloudServiceResponse>[] = [
    { header: lang === 'ar' ? 'المرجع' : 'Reference', width: '120px', mono: true,
      cell: s => <span style={{ fontSize: 12 }}>{s.reference}</span> },
    { header: lang === 'ar' ? 'الخدمة' : 'Service',
      cell: s => (
        <div>
          <div style={{ fontWeight: 500, fontSize: 13 }}>
            {lang === 'ar' ? s.serviceNameAr : s.serviceNameEn}
          </div>
          <div style={{ fontSize: 11, opacity: 0.7 }}>
            {lang === 'ar' ? s.cspLegalNameAr : s.cspLegalNameEn}
            {s.cspBrand ? ` · ${s.cspBrand}` : ''}
          </div>
        </div>
      ) },
    { header: lang === 'ar' ? 'النموذج' : 'Model', width: '80px',
      cell: s => <Badge tone="neutral">{s.deploymentModel}</Badge> },
    { header: lang === 'ar' ? 'التصنيف' : 'Classification', width: '160px',
      cell: s => s.dataClassification
        ? <Badge tone={s.dataClassification === 'T4' || s.dataClassification === 'T3' ? 'danger' : 'info'}>
            {DATA_CLASSIFICATION_LABEL[s.dataClassification][lang === 'ar' ? 'ar' : 'en']}
          </Badge>
        : <span style={{ fontSize: 12, opacity: 0.5 }}>—</span> },
    { header: lang === 'ar' ? 'الإقامة' : 'Residency', width: '130px',
      cell: s => s.dataResidency
        ? <Badge tone={s.dataResidency === 'IN_KINGDOM' ? 'success' :
                       s.dataResidency === 'GCC' ? 'info' : 'warning'}>
            {DATA_RESIDENCY_LABEL[s.dataResidency][lang === 'ar' ? 'ar' : 'en']}
          </Badge>
        : <span style={{ fontSize: 12, opacity: 0.5 }}>—</span> },
    { header: lang === 'ar' ? 'الحالة' : 'Status', width: '140px',
      cell: s => <Badge tone={CLOUD_STATUS_TONE[s.status]}>
        {CLOUD_STATUS_LABEL[s.status][lang === 'ar' ? 'ar' : 'en']}</Badge> },
    { header: '', width: '95px',
      cell: s => {
        const next = nextCloudStatuses(s.status);
        if (next.length === 0) return null;
        return (
          <Button variant="ghost" size="sm"
            onClick={(e) => { e.stopPropagation();
              setMoveTarget(s); setToStatus(next[0]);
              setBlockReasonEn(''); setBlockReasonAr(''); }}>
            {lang === 'ar' ? 'تحريك' : 'Move'}
          </Button>
        );
      } },
  ];

  return (
    <Shell>
      <PageHeader
        eyebrow={lang === 'ar' ? 'خدمات السحابة · المرحلة ٣٠' : 'Cloud Services · Sprint 30'}
        title={lang === 'ar' ? 'سجل خدمات السحابة' : 'Cloud Services Register'}
        sub={lang === 'ar'
          ? 'إطار ساما لخوسبة البيانات السحابية — التصنيف T1-T4 يحدّد بوّابة الإقامة (T3/T4 → داخل المملكة)'
          : 'SAMA Cloud Computing Framework — T1-T4 classification gates residency (T3/T4 → IN_KINGDOM)'}
        actions={
          <Button onClick={() => setShowNew(s => !s)} variant="primary" Icon={Plus}>
            {showNew ? (lang === 'ar' ? 'إغلاق' : 'Cancel')
                      : (lang === 'ar' ? 'خدمة جديدة' : 'New cloud service')}
          </Button>
        } />

      {error && (
        <div style={{ color: 'var(--danger)', fontSize: 13, marginBottom: 12 }}>{error}</div>
      )}

      {showNew && (
        <div style={{ marginBottom: 16 }}>
          <Card padded title={lang === 'ar' ? 'خدمة سحابة جديدة' : 'New cloud service'}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 12 }}>
              <Field label={lang === 'ar' ? 'الترتيب الأم *' : 'Parent arrangement *'}>
                <select value={arrangementId}
                  onChange={e => setArrangementId(e.target.value === '' ? '' : Number(e.target.value))}
                  style={inputStyle}>
                  <option value="">—</option>
                  {arrangements.map(a => (
                    <option key={a.id} value={a.id}>
                      {a.reference} — {lang === 'ar' ? a.providerLegalNameAr : a.providerLegalNameEn}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label={lang === 'ar' ? 'المرجع *' : 'Reference *'}>
                <input value={ref} onChange={e => setRef(e.target.value)}
                  style={inputStyle} maxLength={64} placeholder="CLD-2026-001" />
              </Field>
              <Field label={lang === 'ar' ? 'نموذج النشر' : 'Deployment model'}>
                <select value={deployment}
                  onChange={e => setDeployment(e.target.value as DeploymentModel)}
                  style={inputStyle}>
                  {DEPLOYMENTS.map(d => <option key={d} value={d}>{d}</option>)}
                </select>
              </Field>
              <Field label={lang === 'ar' ? 'اسم الخدمة (إنجليزي) *' : 'Service name (English) *'}>
                <input value={nameEn} onChange={e => setNameEn(e.target.value)}
                  style={inputStyle} maxLength={256}
                  placeholder="AWS S3 storage — Bahrain region" />
              </Field>
              <Field label={lang === 'ar' ? 'اسم الخدمة (عربي) *' : 'Service name (Arabic) *'}>
                <input value={nameAr} dir="rtl" onChange={e => setNameAr(e.target.value)}
                  style={inputStyle} maxLength={256}
                  placeholder="خدمة تخزين S3 من AWS — منطقة البحرين" />
              </Field>
              <Field label={lang === 'ar' ? 'علامة المزوّد' : 'CSP brand'}>
                <select value={cspBrand}
                  onChange={e => setCspBrand(e.target.value as CspBrand)}
                  style={inputStyle}>
                  <option value="">—</option>
                  {CSP_BRANDS.map(b => <option key={b} value={b}>{b}</option>)}
                </select>
              </Field>
              <Field label={lang === 'ar' ? 'مزوّد السحابة (إنجليزي) *' : 'CSP legal name (English) *'}>
                <input value={cspEn} onChange={e => setCspEn(e.target.value)}
                  style={inputStyle} maxLength={256}
                  placeholder="Amazon Web Services, Inc." />
              </Field>
              <Field label={lang === 'ar' ? 'مزوّد السحابة (عربي) *' : 'CSP legal name (Arabic) *'}>
                <input value={cspAr} dir="rtl" onChange={e => setCspAr(e.target.value)}
                  style={inputStyle} maxLength={256}
                  placeholder="أمازون لخدمات الويب المحدودة" />
              </Field>
              <Field label={lang === 'ar' ? 'تصنيف البيانات' : 'Data classification'}>
                <select value={classification}
                  onChange={e => setClassification(e.target.value as DataClassification)}
                  style={inputStyle}>
                  <option value="">—</option>
                  {CLASSIFICATIONS.map(c => (
                    <option key={c} value={c}>
                      {DATA_CLASSIFICATION_LABEL[c][lang === 'ar' ? 'ar' : 'en']}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label={lang === 'ar' ? 'إقامة البيانات' : 'Data residency'}>
                <select value={residency}
                  onChange={e => setResidency(e.target.value as DataResidency)}
                  style={inputStyle}>
                  <option value="">—</option>
                  {RESIDENCIES.map(r => (
                    <option key={r} value={r}>
                      {DATA_RESIDENCY_LABEL[r][lang === 'ar' ? 'ar' : 'en']}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label={lang === 'ar' ? 'منطقة مزوّد السحابة' : 'CSP region label'}>
                <input value={region} onChange={e => setRegion(e.target.value)}
                  style={inputStyle} maxLength={64} placeholder="me-south-1 (Bahrain)" />
              </Field>
            </div>
            <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
              <Button variant="primary" onClick={submit}
                disabled={acting || typeof arrangementId !== 'number' || !ref.trim()
                  || !nameEn.trim() || !nameAr.trim() || !cspEn.trim() || !cspAr.trim()}>
                {acting ? (lang === 'ar' ? 'جارٍ الإنشاء…' : 'Creating…')
                         : (lang === 'ar' ? 'إنشاء' : 'Create')}
              </Button>
              <Button variant="secondary" onClick={() => setShowNew(false)}>
                {lang === 'ar' ? 'إلغاء' : 'Cancel'}
              </Button>
            </div>
          </Card>
        </div>
      )}

      {moveTarget && toStatus && (
        <div style={{ marginBottom: 16 }}>
          <Card padded title={lang === 'ar' ? `تحريك ${moveTarget.reference}` : `Move ${moveTarget.reference}`}>
            <Field label={lang === 'ar' ? 'الحالة الجديدة' : 'Next status'}>
              <select value={toStatus}
                onChange={e => setToStatus(e.target.value as CloudStatus)}
                style={inputStyle}>
                {nextCloudStatuses(moveTarget.status).map(s => (
                  <option key={s} value={s}>
                    {CLOUD_STATUS_LABEL[s][lang === 'ar' ? 'ar' : 'en']}
                  </option>
                ))}
              </select>
            </Field>
            {toStatus === 'BLOCKED' && (
              <div style={{ display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 12, marginTop: 12 }}>
                <Field label={lang === 'ar' ? 'سبب الحظر (إنجليزي)' : 'Block reason (English)'}>
                  <textarea value={blockReasonEn} onChange={e => setBlockReasonEn(e.target.value)}
                    rows={3} style={inputStyle} />
                </Field>
                <Field label={lang === 'ar' ? 'سبب الحظر (عربي)' : 'Block reason (Arabic)'}>
                  <textarea value={blockReasonAr} dir="rtl" onChange={e => setBlockReasonAr(e.target.value)}
                    rows={3} style={inputStyle} />
                </Field>
              </div>
            )}
            <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
              <Button variant="primary" onClick={submitTransition} disabled={acting}>
                {acting ? (lang === 'ar' ? 'جارٍ التنفيذ…' : 'Applying…')
                         : (lang === 'ar' ? 'تنفيذ' : 'Apply')}
              </Button>
              <Button variant="secondary"
                onClick={() => { setMoveTarget(null); setToStatus(null); }}>
                {lang === 'ar' ? 'إلغاء' : 'Cancel'}
              </Button>
            </div>
          </Card>
        </div>
      )}

      <Card padded title={lang === 'ar' ? 'كل خدمات السحابة' : 'All cloud services'}>
        {loading ? (
          <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        ) : (
          <DataTable columns={cols} rows={rows}
            empty={<EmptyState Icon={Cloud}
              title={lang === 'ar' ? 'لا خدمات سحابة بعد' : 'No cloud services yet'}
              sub={lang === 'ar'
                ? 'أنشئ أوّل خدمة سحابة لبدء بوّابة التصنيف والإقامة.'
                : 'Create the first cloud service to start the classification + residency gate.'} />}
          />
        )}
      </Card>
    </Shell>
  );
}

const inputStyle: React.CSSProperties = {
  padding: 6, border: '1px solid var(--border)', borderRadius: 4,
  fontSize: 13, width: '100%', fontFamily: 'inherit',
};

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
      <span style={{ fontSize: 12, fontWeight: 600 }}>{label}</span>
      {children}
    </label>
  );
}
