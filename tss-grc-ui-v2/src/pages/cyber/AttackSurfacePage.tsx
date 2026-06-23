import { useEffect, useMemo, useState } from 'react';
import { Globe, ShieldAlert, AlertOctagon, Cloud, Server, Bug } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { Button } from '../../components/ui/Button';
import { Modal } from '../../components/ui/Modal';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { isAuthenticated } from '../../lib/auth';
import { ApiError, tprmApi, type TprmVendorDto } from '../../lib/api';
import {
  asmApi,
  ASM_ASSET_TYPE_LABEL,
  ASM_EXPOSURE_LABEL, ASM_EXPOSURE_TONE,
  ASM_CRITICALITY_LABEL, ASM_CRITICALITY_TONE,
  ASM_LIFECYCLE_LABEL, ASM_LIFECYCLE_TONE,
  ASM_FINDING_TYPE_LABEL,
  ASM_FINDING_SEVERITY_LABEL, ASM_FINDING_SEVERITY_TONE,
  ASM_FINDING_STATUS_LABEL, ASM_FINDING_STATUS_TONE,
  SHADOW_IT_STATUS_LABEL, SHADOW_IT_STATUS_TONE,
  SHADOW_IT_DATA_CLASS_LABEL,
  SHADOW_IT_DISCOVERED_VIA_LABEL,
  formatRiskScore, riskScoreTone,
  type AsmAssetResponse, type AsmFindingResponse, type ShadowItResponse,
  type AsmAssetType, type AsmExposure, type AsmCriticality, type AsmLifecycleStage,
  type AsmFindingSeverity, type AsmFindingStatus,
  type ShadowItStatus, type ShadowItDiscoveredVia, type ShadowItDataClassification,
} from '../../lib/asm';

type Tab = 'assets' | 'findings' | 'shadowIt';

/**
 * ASM Register — Sprint 11.
 *
 * <p>Three-tab register for the CISO's external attack surface:
 *   - Assets (the inventory)
 *   - Findings (issues attached to assets)
 *   - Shadow IT (pre-inventory pipeline)
 *
 * <p>Saudi-native title: "إدارة سطح الهجوم" — not literal
 * "إدارة سطح الهجوم الخارجي". Lifecycle stage labels follow the
 * platform's Saudi-native convention (مُكتشَف / قيد المراجعة / مُعتَمد / …).
 */
export function AttackSurfacePage() {
  const { lang } = useI18n();
  const toast = useToast();
  const demoMode = !isAuthenticated();

  const [tab, setTab] = useState<Tab>('assets');
  const [assets, setAssets] = useState<AsmAssetResponse[]>([]);
  const [findings, setFindings] = useState<AsmFindingResponse[]>([]);
  const [shadowIt, setShadowIt] = useState<ShadowItResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Shadow-IT governance action (sanction / block) modal state.
  const [actionTarget, setActionTarget] = useState<ShadowItResponse | null>(null);
  const [actionMode, setActionMode] = useState<'sanction' | 'block'>('sanction');
  const [vendors, setVendors] = useState<TprmVendorDto[]>([]);
  const [selVendorId, setSelVendorId] = useState<number | ''>('');
  const [actionNote, setActionNote] = useState('');
  const [actionBusy, setActionBusy] = useState(false);

  // Record-shadow-IT-discovery modal state.
  const [showRecord, setShowRecord] = useState(false);
  const [recBusy, setRecBusy] = useState(false);
  const [recName, setRecName] = useState('');
  const [recNameAr, setRecNameAr] = useState('');
  const [recDomain, setRecDomain] = useState('');
  const [recVia, setRecVia] = useState<ShadowItDiscoveredVia>('USER_DECLARATION');
  const [recUsers, setRecUsers] = useState('');
  const [recClass, setRecClass] = useState<ShadowItDataClassification>('UNKNOWN');
  const [recPdpl, setRecPdpl] = useState(false);
  const [recNotes, setRecNotes] = useState('');

  // Filters per tab
  const [filterAssetType, setFilterAssetType] = useState<'ALL' | AsmAssetType>('ALL');
  const [filterExposure, setFilterExposure] = useState<'ALL' | AsmExposure>('ALL');
  const [filterCriticality, setFilterCriticality] = useState<'ALL' | AsmCriticality>('ALL');
  const [filterLifecycle, setFilterLifecycle] = useState<'ALL' | AsmLifecycleStage>('ALL');

  const [filterFindingSev, setFilterFindingSev] = useState<'ALL' | AsmFindingSeverity>('ALL');
  const [filterFindingStatus, setFilterFindingStatus] = useState<'ALL' | AsmFindingStatus>('ALL');

  const [filterShadowItStatus, setFilterShadowItStatus] = useState<'ALL' | ShadowItStatus>('ALL');

  async function load() {
    if (demoMode) { setAssets([]); setFindings([]); setShadowIt([]); return; }
    setLoading(true);
    setError(null);
    try {
      const [a, f, s] = await Promise.all([
        asmApi.listAssets(), asmApi.listFindings(), asmApi.listShadowIt(),
      ]);
      setAssets(a); setFindings(f); setShadowIt(s);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load attack-surface data');
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { load(); /* eslint-disable-next-line */ }, []);

  // ─── Shadow-IT governance actions ───────────────────────────────

  const SHADOW_IT_ACTIONABLE = (s: ShadowItResponse) =>
    s.status === 'PENDING_REVIEW' || s.status === 'IN_PROGRESS';

  function openSanction(s: ShadowItResponse) {
    setActionTarget(s); setActionMode('sanction'); setSelVendorId(''); setActionNote('');
    if (vendors.length === 0) {
      tprmApi.vendors(0, 200).then(p => setVendors(p.content ?? [])).catch(() => { /* picker stays empty */ });
    }
  }
  function openBlock(s: ShadowItResponse) {
    setActionTarget(s); setActionMode('block'); setActionNote('');
  }
  function closeAction() { setActionTarget(null); setActionNote(''); setSelVendorId(''); }

  async function submitAction() {
    if (!actionTarget) return;
    if (actionMode === 'sanction' && !selVendorId) {
      toast.danger(lang === 'ar' ? 'اختر مورّداً معتمداً' : 'Select a sanctioned vendor');
      return;
    }
    setActionBusy(true);
    try {
      if (actionMode === 'sanction') {
        await asmApi.sanctionShadowIt(actionTarget.externalId, selVendorId as number, actionNote.trim() || undefined);
        toast.success(lang === 'ar' ? `${actionTarget.serviceName}: تم الاعتماد` : `${actionTarget.serviceName} sanctioned`);
      } else {
        await asmApi.blockShadowIt(actionTarget.externalId, actionNote.trim() || undefined);
        toast.success(lang === 'ar' ? `${actionTarget.serviceName}: تم الحجب` : `${actionTarget.serviceName} blocked`);
      }
      closeAction();
      await load();
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : 'Action failed');
    } finally {
      setActionBusy(false);
    }
  }

  function openRecord() {
    setShowRecord(true);
    setRecName(''); setRecNameAr(''); setRecDomain('');
    setRecVia('USER_DECLARATION'); setRecUsers(''); setRecClass('UNKNOWN');
    setRecPdpl(false); setRecNotes('');
  }
  async function submitRecord() {
    if (!recName.trim()) {
      toast.danger(lang === 'ar' ? 'اسم الخدمة مطلوب' : 'Service name is required');
      return;
    }
    setRecBusy(true);
    try {
      await asmApi.recordShadowIt({
        serviceName: recName.trim(),
        serviceNameAr: recNameAr.trim() || undefined,
        vendorDomain: recDomain.trim() || undefined,
        discoveredVia: recVia,
        observedUserCount: recUsers.trim() ? Number(recUsers) : undefined,
        dataClassification: recClass,
        pdplDataProcessed: recPdpl,
        notesEn: recNotes.trim() || undefined,
      });
      toast.success(lang === 'ar' ? `سُجِّلت خدمة الظل: ${recName.trim()}` : `Shadow IT recorded: ${recName.trim()}`);
      setShowRecord(false);
      await load();
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : 'Record failed');
    } finally {
      setRecBusy(false);
    }
  }

  // ─── KPI computations ───────────────────────────────────────────

  const kpis = useMemo(() => {
    const totalAssets = assets.length;
    const criticalExposure = assets.filter(a => a.exposure === 'PUBLIC' && a.criticality === 'CRITICAL').length;
    const openFindings = findings.filter(f => f.status === 'OPEN' || f.status === 'INVESTIGATING').length;
    const pendingShadow = shadowIt.filter(s => s.status === 'PENDING_REVIEW' || s.status === 'IN_PROGRESS').length;
    return { totalAssets, criticalExposure, openFindings, pendingShadow };
  }, [assets, findings, shadowIt]);

  // ─── Asset filters ──────────────────────────────────────────────

  const filteredAssets = useMemo(() => assets.filter(a => {
    if (filterAssetType !== 'ALL' && a.assetType !== filterAssetType) return false;
    if (filterExposure !== 'ALL' && a.exposure !== filterExposure) return false;
    if (filterCriticality !== 'ALL' && a.criticality !== filterCriticality) return false;
    if (filterLifecycle !== 'ALL' && a.lifecycleStage !== filterLifecycle) return false;
    return true;
  }), [assets, filterAssetType, filterExposure, filterCriticality, filterLifecycle]);

  const filteredFindings = useMemo(() => findings.filter(f => {
    if (filterFindingSev !== 'ALL' && f.severity !== filterFindingSev) return false;
    if (filterFindingStatus !== 'ALL' && f.status !== filterFindingStatus) return false;
    return true;
  }), [findings, filterFindingSev, filterFindingStatus]);

  const filteredShadowIt = useMemo(() => shadowIt.filter(s => {
    if (filterShadowItStatus !== 'ALL' && s.status !== filterShadowItStatus) return false;
    return true;
  }), [shadowIt, filterShadowItStatus]);

  // ─── Columns ────────────────────────────────────────────────────

  const assetCols: Column<AsmAssetResponse>[] = [
    {
      header: lang === 'ar' ? 'الرمز' : 'Asset code', width: '160px',
      cell: r => <code style={{ fontSize: 12 }}>{r.assetCode}</code>,
    },
    {
      header: lang === 'ar' ? 'العنوان' : 'Title',
      cell: r => lang === 'ar' && r.titleAr ? r.titleAr : r.titleEn,
    },
    {
      header: lang === 'ar' ? 'النوع' : 'Type', width: '160px',
      cell: r => <span style={{ fontSize: 12 }}>
        {ASM_ASSET_TYPE_LABEL[r.assetType][lang === 'ar' ? 'ar' : 'en']}
      </span>,
    },
    {
      header: lang === 'ar' ? 'التعرّض' : 'Exposure', width: '110px', align: 'center',
      cell: r => (
        <Badge tone={ASM_EXPOSURE_TONE[r.exposure]}>
          {ASM_EXPOSURE_LABEL[r.exposure][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'الأهمية' : 'Criticality', width: '110px', align: 'center',
      cell: r => (
        <Badge tone={ASM_CRITICALITY_TONE[r.criticality]}>
          {ASM_CRITICALITY_LABEL[r.criticality][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'دورة الحياة' : 'Lifecycle', width: '160px', align: 'center',
      cell: r => (
        <Badge tone={ASM_LIFECYCLE_TONE[r.lifecycleStage]} dot>
          {ASM_LIFECYCLE_LABEL[r.lifecycleStage][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'درجة الخطورة' : 'Risk score', width: '120px', align: 'center',
      cell: r => (
        <Badge tone={riskScoreTone(r.riskScore)}>{formatRiskScore(r.riskScore)}</Badge>
      ),
    },
  ];

  const findingCols: Column<AsmFindingResponse>[] = [
    {
      header: lang === 'ar' ? 'الأصل' : 'Asset', width: '160px',
      cell: r => <code style={{ fontSize: 12 }}>{r.assetCode ?? '—'}</code>,
    },
    {
      header: lang === 'ar' ? 'النوع' : 'Type', width: '200px',
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
      header: lang === 'ar' ? 'المصدر' : 'Source', width: '120px',
      cell: r => <span style={{ fontSize: 12 }}>{r.source}</span>,
    },
    {
      header: lang === 'ar' ? 'الوصف' : 'Description',
      cell: r => (
        <span style={{ fontSize: 12 }}>
          {lang === 'ar' && r.descriptionAr ? r.descriptionAr : r.descriptionEn}
        </span>
      ),
    },
  ];

  const shadowItCols: Column<ShadowItResponse>[] = [
    {
      header: lang === 'ar' ? 'الخدمة' : 'Service', width: '180px',
      cell: r => (
        <span style={{ fontWeight: 500 }}>
          {lang === 'ar' && r.serviceNameAr ? r.serviceNameAr : r.serviceName}
        </span>
      ),
    },
    {
      header: lang === 'ar' ? 'النطاق' : 'Vendor domain', width: '160px',
      cell: r => <code style={{ fontSize: 12 }}>{r.vendorDomain ?? '—'}</code>,
    },
    {
      header: lang === 'ar' ? 'الاكتشاف عبر' : 'Discovered via', width: '180px',
      cell: r => <span style={{ fontSize: 12 }}>
        {SHADOW_IT_DISCOVERED_VIA_LABEL[r.discoveredVia][lang === 'ar' ? 'ar' : 'en']}
      </span>,
    },
    {
      header: lang === 'ar' ? 'المستخدمون' : 'Users', width: '80px', align: 'center',
      cell: r => <span>{r.observedUserCount ?? '—'}</span>,
    },
    {
      header: lang === 'ar' ? 'تصنيف البيانات' : 'Data class', width: '120px', align: 'center',
      cell: r => (
        <Badge tone={r.dataClassification === 'RESTRICTED' || r.dataClassification === 'CONFIDENTIAL' ? 'danger' : 'info'}>
          {SHADOW_IT_DATA_CLASS_LABEL[r.dataClassification][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'بيانات شخصية' : 'PDPL', width: '100px', align: 'center',
      cell: r => r.pdplDataProcessed
        ? <Badge tone="danger" dot>{lang === 'ar' ? 'نعم' : 'Yes'}</Badge>
        : <Badge tone="neutral">{lang === 'ar' ? 'لا' : 'No'}</Badge>,
    },
    {
      header: lang === 'ar' ? 'الحالة' : 'Status', width: '140px', align: 'center',
      cell: r => (
        <Badge tone={SHADOW_IT_STATUS_TONE[r.status]} dot>
          {SHADOW_IT_STATUS_LABEL[r.status][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'الإجراء' : 'Action', width: '190px', align: 'center',
      cell: r => SHADOW_IT_ACTIONABLE(r) ? (
        <div style={{ display: 'flex', gap: 6, justifyContent: 'center' }}>
          <Button size="sm" variant="secondary" onClick={() => openSanction(r)}>
            {lang === 'ar' ? 'اعتماد' : 'Sanction'}
          </Button>
          <Button size="sm" variant="danger" onClick={() => openBlock(r)}>
            {lang === 'ar' ? 'حجب' : 'Block'}
          </Button>
        </div>
      ) : <span style={{ color: 'var(--ink-3)', fontSize: 12 }}>—</span>,
    },
  ];

  // ─── Render ─────────────────────────────────────────────────────

  return (
    <Shell
      title={lang === 'ar' ? 'إدارة سطح الهجوم' : 'Attack Surface Management'}
      sub={lang === 'ar' ? 'الأمن السيبراني' : 'Cybersecurity'}
    >
      <PageHeader
        eyebrow={lang === 'ar' ? 'الأمن السيبراني' : 'Cybersecurity'}
        title={lang === 'ar' ? 'إدارة سطح الهجوم' : 'Attack Surface Management'}
        sub={lang === 'ar'
          ? 'سجلّ حي للأصول الخارجية والنتائج وتقنية الظل. يخدم متطلبات الضابط 2-3 من ECC-2 وإطار SAMA السيبراني (4.2.1) وضوابط NCA OTCC.'
          : 'Live register of external assets, findings, and shadow IT. Anchors NCA ECC-2 control 2-3, SAMA Cyber Security Framework 4.2.1, and NCA OTCC obligations.'}
        actions={demoMode ? <Badge tone="warning">{lang === 'ar' ? 'وضع العرض' : 'Demo mode'}</Badge> : null}
      />

      {/* KPI tiles */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12 }}>
        <Kpi Icon={Server} tone="primary"
             label={lang === 'ar' ? 'إجمالي الأصول' : 'Total assets'}
             value={kpis.totalAssets}
             onClick={() => setTab('assets')} active={tab === 'assets'} />
        <Kpi Icon={Globe} tone="danger"
             label={lang === 'ar' ? 'أصول عامة حرجة' : 'Public + critical'}
             value={kpis.criticalExposure}
             onClick={() => setTab('assets')} active={tab === 'assets'} />
        <Kpi Icon={Bug} tone="warning"
             label={lang === 'ar' ? 'نتائج مفتوحة' : 'Open findings'}
             value={kpis.openFindings}
             onClick={() => setTab('findings')} active={tab === 'findings'} />
        <Kpi Icon={Cloud} tone="info"
             label={lang === 'ar' ? 'تقنية الظل قيد المراجعة' : 'Shadow IT pending'}
             value={kpis.pendingShadow}
             onClick={() => setTab('shadowIt')} active={tab === 'shadowIt'} />
      </div>

      {/* Tabs */}
      <Card padded>
        <div style={{ display: 'flex', gap: 8, borderBottom: '1px solid var(--border)', marginBottom: 12 }}>
          <TabButton active={tab === 'assets'} onClick={() => setTab('assets')}>
            {lang === 'ar' ? 'الأصول المُكتشَفة' : 'Assets'}
          </TabButton>
          <TabButton active={tab === 'findings'} onClick={() => setTab('findings')}>
            {lang === 'ar' ? 'النتائج' : 'Findings'}
          </TabButton>
          <TabButton active={tab === 'shadowIt'} onClick={() => setTab('shadowIt')}>
            {lang === 'ar' ? 'تقنية الظل' : 'Shadow IT'}
          </TabButton>
        </div>

        {/* Filters */}
        {tab === 'assets' && (
          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'flex-end', marginBottom: 12 }}>
            <FilterSelect
              label={lang === 'ar' ? 'النوع' : 'Type'}
              value={filterAssetType}
              onChange={v => setFilterAssetType(v as 'ALL' | AsmAssetType)}
              options={[
                { value: 'ALL', label: lang === 'ar' ? 'الكل' : 'All' },
                ...(Object.keys(ASM_ASSET_TYPE_LABEL) as AsmAssetType[]).map(k => ({
                  value: k, label: ASM_ASSET_TYPE_LABEL[k][lang === 'ar' ? 'ar' : 'en'],
                })),
              ]} />
            <FilterSelect
              label={lang === 'ar' ? 'التعرّض' : 'Exposure'}
              value={filterExposure}
              onChange={v => setFilterExposure(v as 'ALL' | AsmExposure)}
              options={[
                { value: 'ALL', label: lang === 'ar' ? 'الكل' : 'All' },
                ...(['PUBLIC', 'PARTNER', 'INTERNAL', 'RESTRICTED'] as AsmExposure[]).map(k => ({
                  value: k, label: ASM_EXPOSURE_LABEL[k][lang === 'ar' ? 'ar' : 'en'],
                })),
              ]} />
            <FilterSelect
              label={lang === 'ar' ? 'الأهمية' : 'Criticality'}
              value={filterCriticality}
              onChange={v => setFilterCriticality(v as 'ALL' | AsmCriticality)}
              options={[
                { value: 'ALL', label: lang === 'ar' ? 'الكل' : 'All' },
                ...(['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'] as AsmCriticality[]).map(k => ({
                  value: k, label: ASM_CRITICALITY_LABEL[k][lang === 'ar' ? 'ar' : 'en'],
                })),
              ]} />
            <FilterSelect
              label={lang === 'ar' ? 'دورة الحياة' : 'Lifecycle'}
              value={filterLifecycle}
              onChange={v => setFilterLifecycle(v as 'ALL' | AsmLifecycleStage)}
              options={[
                { value: 'ALL', label: lang === 'ar' ? 'الكل' : 'All' },
                ...(['DISCOVERED', 'IN_REVIEW', 'APPROVED', 'SHADOW_IT_PENDING', 'RETIRED'] as AsmLifecycleStage[]).map(k => ({
                  value: k, label: ASM_LIFECYCLE_LABEL[k][lang === 'ar' ? 'ar' : 'en'],
                })),
              ]} />
          </div>
        )}

        {tab === 'findings' && (
          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'flex-end', marginBottom: 12 }}>
            <FilterSelect
              label={lang === 'ar' ? 'الخطورة' : 'Severity'}
              value={filterFindingSev}
              onChange={v => setFilterFindingSev(v as 'ALL' | AsmFindingSeverity)}
              options={[
                { value: 'ALL', label: lang === 'ar' ? 'الكل' : 'All' },
                ...(['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'] as AsmFindingSeverity[]).map(k => ({
                  value: k, label: ASM_FINDING_SEVERITY_LABEL[k][lang === 'ar' ? 'ar' : 'en'],
                })),
              ]} />
            <FilterSelect
              label={lang === 'ar' ? 'الحالة' : 'Status'}
              value={filterFindingStatus}
              onChange={v => setFilterFindingStatus(v as 'ALL' | AsmFindingStatus)}
              options={[
                { value: 'ALL', label: lang === 'ar' ? 'الكل' : 'All' },
                ...(['OPEN', 'INVESTIGATING', 'FIXED', 'ACCEPTED_RISK', 'FALSE_POSITIVE', 'OUT_OF_SCOPE'] as AsmFindingStatus[]).map(k => ({
                  value: k, label: ASM_FINDING_STATUS_LABEL[k][lang === 'ar' ? 'ar' : 'en'],
                })),
              ]} />
          </div>
        )}

        {tab === 'shadowIt' && (
          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'flex-end', marginBottom: 12 }}>
            <FilterSelect
              label={lang === 'ar' ? 'الحالة' : 'Status'}
              value={filterShadowItStatus}
              onChange={v => setFilterShadowItStatus(v as 'ALL' | ShadowItStatus)}
              options={[
                { value: 'ALL', label: lang === 'ar' ? 'الكل' : 'All' },
                ...(['PENDING_REVIEW', 'IN_PROGRESS', 'SANCTIONED', 'MIGRATED_TO_SANCTIONED', 'BLOCKED', 'NA'] as ShadowItStatus[]).map(k => ({
                  value: k, label: SHADOW_IT_STATUS_LABEL[k][lang === 'ar' ? 'ar' : 'en'],
                })),
              ]} />
            {!demoMode && (
              <div style={{ marginInlineStart: 'auto' }}>
                <Button variant="primary" onClick={openRecord}>
                  {lang === 'ar' ? 'تسجيل اكتشاف' : 'Record discovery'}
                </Button>
              </div>
            )}
          </div>
        )}

        {error && (
          <div style={{ color: 'var(--danger)', fontSize: 13, marginBottom: 12 }}>{error}</div>
        )}

        {loading ? (
          <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        ) : tab === 'assets' ? (
          <DataTable
            columns={assetCols}
            rows={filteredAssets}
            rowHref={r => `/cyber/attack-surface/assets/${r.externalId}`}
            empty={<EmptyState
              Icon={Server}
              title={lang === 'ar' ? 'لا توجد أصول' : 'No assets'}
              sub={lang === 'ar'
                ? 'ستظهر هنا كل الأصول الخارجية المُكتشَفة عند توفّرها.'
                : 'External assets discovered or registered will appear here.'} />}
          />
        ) : tab === 'findings' ? (
          <DataTable
            columns={findingCols}
            rows={filteredFindings}
            empty={<EmptyState
              Icon={AlertOctagon}
              title={lang === 'ar' ? 'لا توجد نتائج' : 'No findings'}
              sub={lang === 'ar'
                ? 'كل النتائج المُسجَّلة على الأصول ستظهر هنا.'
                : 'Findings recorded against any asset will appear here.'} />}
          />
        ) : (
          <DataTable
            columns={shadowItCols}
            rows={filteredShadowIt}
            empty={<EmptyState
              Icon={Cloud}
              title={lang === 'ar' ? 'لا توجد اكتشافات' : 'No shadow IT'}
              sub={lang === 'ar'
                ? 'ستظهر هنا كل خدمات تقنية الظل المكتشفة لمراجعتها.'
                : 'Shadow-IT discoveries pending governance review will appear here.'} />}
          />
        )}
      </Card>

      {actionTarget && (
        <Modal open onClose={closeAction} size="md"
          title={actionMode === 'sanction'
            ? (lang === 'ar' ? 'اعتماد خدمة تقنية الظل' : 'Sanction shadow IT')
            : (lang === 'ar' ? 'حجب خدمة تقنية الظل' : 'Block shadow IT')}
          footer={<>
            <Button variant="ghost" onClick={closeAction}>{lang === 'ar' ? 'إلغاء' : 'Cancel'}</Button>
            <Button variant={actionMode === 'block' ? 'danger' : 'primary'} disabled={actionBusy} onClick={submitAction}>
              {actionMode === 'sanction'
                ? (lang === 'ar' ? 'اعتماد ونقل إلى TPRM' : 'Sanction & link to TPRM')
                : (lang === 'ar' ? 'تأكيد الحجب' : 'Confirm block')}
            </Button>
          </>}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <div style={{ fontSize: 13, display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
              <strong>{lang === 'ar' && actionTarget.serviceNameAr ? actionTarget.serviceNameAr : actionTarget.serviceName}</strong>
              <Badge tone={SHADOW_IT_STATUS_TONE[actionTarget.status]} dot>
                {SHADOW_IT_STATUS_LABEL[actionTarget.status][lang === 'ar' ? 'ar' : 'en']}
              </Badge>
              {actionTarget.pdplDataProcessed && (
                <Badge tone="danger" dot>{lang === 'ar' ? 'بيانات شخصية' : 'PDPL data'}</Badge>
              )}
            </div>

            {actionMode === 'sanction' ? (
              <label style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 12 }}>
                {lang === 'ar' ? 'المورّد المعتمد (TPRM) *' : 'Sanctioned vendor (TPRM) *'}
                <select value={selVendorId}
                  onChange={e => setSelVendorId(e.target.value ? Number(e.target.value) : '')}
                  style={{ padding: 8, border: '1px solid var(--border)', borderRadius: 4, background: 'var(--surface)', fontSize: 13 }}>
                  <option value="">{lang === 'ar' ? '— اختر مورّداً —' : '— Select a vendor —'}</option>
                  {vendors.map(v => (
                    <option key={v.id} value={v.id}>
                      {lang === 'ar' && v.vendorNameAr ? v.vendorNameAr : v.vendorName}
                    </option>
                  ))}
                </select>
              </label>
            ) : (
              <div style={{ fontSize: 12, color: 'var(--danger)' }}>
                {lang === 'ar'
                  ? 'الحجب إجراء نهائي لا يمكن التراجع عنه — تُمنع الخدمة من سطح الهجوم.'
                  : 'Block is terminal and cannot be undone — the service is denied from the attack surface.'}
              </div>
            )}

            <label style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 12 }}>
              {lang === 'ar' ? 'ملاحظة (اختياري)' : 'Note (optional)'}
              <textarea value={actionNote} onChange={e => setActionNote(e.target.value)} rows={3}
                style={{ padding: 8, border: '1px solid var(--border)', borderRadius: 4, background: 'var(--surface)', fontSize: 13 }} />
            </label>
          </div>
        </Modal>
      )}

      {showRecord && (
        <Modal open onClose={() => setShowRecord(false)} size="md"
          title={lang === 'ar' ? 'تسجيل اكتشاف تقنية ظل' : 'Record a shadow-IT discovery'}
          footer={<>
            <Button variant="ghost" onClick={() => setShowRecord(false)}>{lang === 'ar' ? 'إلغاء' : 'Cancel'}</Button>
            <Button variant="primary" disabled={recBusy} onClick={submitRecord}>{lang === 'ar' ? 'تسجيل' : 'Record'}</Button>
          </>}>
          {(() => {
            const fld: React.CSSProperties = { display: 'flex', flexDirection: 'column', gap: 4, fontSize: 12 };
            const inp: React.CSSProperties = { padding: 8, border: '1px solid var(--border)', borderRadius: 4, background: 'var(--surface)', fontSize: 13 };
            return (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                <label style={fld}>
                  {lang === 'ar' ? 'اسم الخدمة *' : 'Service name *'}
                  <input value={recName} onChange={e => setRecName(e.target.value)} placeholder="e.g. Figma" style={inp} />
                </label>
                <label style={fld}>
                  {lang === 'ar' ? 'اسم الخدمة (عربي)' : 'Service name (AR)'}
                  <input value={recNameAr} dir="rtl" onChange={e => setRecNameAr(e.target.value)} style={inp} />
                </label>
                <label style={fld}>
                  {lang === 'ar' ? 'نطاق المزوّد' : 'Vendor domain'}
                  <input value={recDomain} onChange={e => setRecDomain(e.target.value)} placeholder="e.g. figma.com" style={inp} />
                </label>
                <label style={fld}>
                  {lang === 'ar' ? 'الاكتشاف عبر' : 'Discovered via'}
                  <select value={recVia} onChange={e => setRecVia(e.target.value as ShadowItDiscoveredVia)} style={inp}>
                    {(Object.keys(SHADOW_IT_DISCOVERED_VIA_LABEL) as ShadowItDiscoveredVia[]).map(k => (
                      <option key={k} value={k}>{SHADOW_IT_DISCOVERED_VIA_LABEL[k][lang === 'ar' ? 'ar' : 'en']}</option>
                    ))}
                  </select>
                </label>
                <label style={fld}>
                  {lang === 'ar' ? 'عدد المستخدمين المرصودين' : 'Observed user count'}
                  <input type="number" value={recUsers} onChange={e => setRecUsers(e.target.value)}
                    placeholder={lang === 'ar' ? '(اختياري)' : '(optional)'} style={inp} />
                </label>
                <label style={fld}>
                  {lang === 'ar' ? 'تصنيف البيانات' : 'Data classification'}
                  <select value={recClass} onChange={e => setRecClass(e.target.value as ShadowItDataClassification)} style={inp}>
                    {(Object.keys(SHADOW_IT_DATA_CLASS_LABEL) as ShadowItDataClassification[]).map(k => (
                      <option key={k} value={k}>{SHADOW_IT_DATA_CLASS_LABEL[k][lang === 'ar' ? 'ar' : 'en']}</option>
                    ))}
                  </select>
                </label>
                <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13 }}>
                  <input type="checkbox" checked={recPdpl} onChange={e => setRecPdpl(e.target.checked)} />
                  {lang === 'ar' ? 'تعالج بيانات شخصية (PDPL) — يُصعَّد للمسؤول عن البيانات' : 'Processes PDPL personal data — escalates to the DPO'}
                </label>
                <label style={fld}>
                  {lang === 'ar' ? 'ملاحظات (اختياري)' : 'Notes (optional)'}
                  <textarea value={recNotes} onChange={e => setRecNotes(e.target.value)} rows={2} style={inp} />
                </label>
              </div>
            );
          })()}
        </Modal>
      )}
    </Shell>
  );
}

// ─── Local helpers ───────────────────────────────────────────────────

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

interface FilterSelectProps {
  label: string; value: string; onChange: (v: string) => void;
  options: { value: string; label: string }[];
}
function FilterSelect({ label, value, onChange, options }: FilterSelectProps) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 4, minWidth: 140 }}>
      <label style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: 0.4 }}>
        {label}
      </label>
      <select
        value={value} onChange={e => onChange(e.target.value)}
        style={{
          padding: '6px 8px',
          border: '1px solid var(--border)',
          borderRadius: 4,
          background: 'var(--surface)',
          color: 'var(--ink-1)',
          fontSize: 13,
        }}
      >
        {options.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
      </select>
    </div>
  );
}

/** Suppress unused-import warnings for icons not yet wired into the tile chip. */
export const _shieldAlert = ShieldAlert;
