import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  ShieldCheck, AlertTriangle, Activity, Server, Bug, Radio,
  Download, Send, ThumbsUp, CheckCircle2, ArrowUpRight, Clock,
  AlertOctagon, Wrench,
} from 'lucide-react';
import { LifecyclePipeline, type PipelineStage, type PipelineCard, dueLabel } from '../../components/lifecycle/LifecyclePipeline';
import { printPacket, packetTextToSections } from '../../lib/packetPrint';
import { reportingApi, ApiError } from '../../lib/api';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  loadFiling, transitionFiling,
  loadVulns, transitionVuln,
  loadAssets, createAsset, markPatched, reclassifyAsset,
  loadThreats, acknowledgeThreat, createThreat,
  loadSocAlerts, transitionSocAlert, createSocAlert,
  loadPenTests, schedulePenTest, transitionPenTest, recordPenTestFindings,
  subscribeCyber, aggregateCyber, buildHaseenPacket, buildHaseenSheets,
  FILING_STATUS_LABEL, FILING_STATUS_TONE,
  TIER_LABEL, TIER_TONE,
  VULN_SEVERITY_TONE, VULN_STATUS_LABEL, VULN_STATUS_TONE,
  SOC_SEVERITY_TONE, SOC_STATUS_LABEL, SOC_STATUS_TONE,
  THREAT_SOURCE_LABEL,
  PENTEST_STATUS_LABEL, PENTEST_STATUS_TONE,
  type EccFiling, type Vulnerability, type CyberAsset, type ThreatAdvisory, type SocAlert, type PenTest,
  type EccFilingStatus, type VulnStatus, type SocStatus, type AssetTier, type PenTestStatus,
} from '../../lib/cyberLifecycle';
import { loadWhiteLabel, subscribeWhiteLabel } from '../../lib/whiteLabel';
import '../../styles/pages/cyber-lifecycle.css';

type CycTab = 'pipeline' | 'ecc' | 'vulns' | 'assets' | 'threats' | 'soc' | 'pentest';

const CYC_TABS: ReadonlyArray<CycTab> = ['pipeline', 'ecc', 'vulns', 'assets', 'threats', 'soc', 'pentest'];

function isCycTab(value: string | null | undefined): value is CycTab {
  return !!value && (CYC_TABS as readonly string[]).includes(value);
}

export function CyberLifecyclePage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const [searchParams] = useSearchParams();

  // Honor `?tab=` deep-links from other pages (e.g. the V2 vulnerabilities
  // page navigating here on row click). Falls back to 'pipeline' for any
  // unknown tab value.
  const initialTab: CycTab = isCycTab(searchParams.get('tab')) ? (searchParams.get('tab') as CycTab) : 'pipeline';
  const [tab, setTab] = useState<CycTab>(initialTab);
  const [_tick, setTick] = useState(0);
  useEffect(() => subscribeCyber(() => setTick(t => t + 1)), []);

  const stats = useMemo(() => aggregateCyber(), [_tick]);
  const filing = useMemo(() => loadFiling(), [_tick]);
  const vulns = useMemo(() => loadVulns(), [_tick]);
  const assets = useMemo(() => loadAssets(), [_tick]);
  const threats = useMemo(() => loadThreats(), [_tick]);
  const alerts = useMemo(() => loadSocAlerts(), [_tick]);
  const tests = useMemo(() => loadPenTests(), [_tick]);

  const [brand, setBrand] = useState(() => loadWhiteLabel());
  useEffect(() => subscribeWhiteLabel(() => setBrand(loadWhiteLabel())), []);

  function downloadHaseenPacket() {
    const text = buildHaseenPacket(brand.organisationNameEn, brand.organisationNameAr);
    const blob = new Blob([text], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `NCA-Haseen-${filing.year}.txt`;
    document.body.appendChild(a); a.click(); document.body.removeChild(a);
    URL.revokeObjectURL(url);
    toast.success(t('cycHaseenDownloadedToast'));
  }

  function printHaseenPdf() {
    const text = buildHaseenPacket(brand.organisationNameEn, brand.organisationNameAr);
    printPacket({
      titleEn: 'NCA Haseen — ECC v3 Annual Self-Assessment Filing',
      titleAr: 'حصين — التقديم السنوي للتقييم الذاتي للضوابط الأساسية v3',
      eyebrowEn: 'NCA · Annual Filing',
      eyebrowAr: 'الهيئة الوطنية للأمن السيبراني · تقديم سنوي',
      referenceId: filing.id,
      classificationEn: 'CONFIDENTIAL — REGULATOR',
      classificationAr: 'سرّي — جهة تنظيمية',
      sections: packetTextToSections(text),
    }, lang);
  }

  async function exportHaseen(kind: 'xlsx' | 'docx') {
    const payload = { module: 'NCA', exportType: 'HaseenECC', lang, sheets: buildHaseenSheets() };
    try {
      await (kind === 'xlsx' ? reportingApi.packetXlsx(payload) : reportingApi.packetDocx(payload));
      toast.success(lang === 'ar' ? 'تم التصدير' : 'Exported');
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (lang === 'ar' ? 'تعذّر التصدير' : 'Export failed'));
    }
  }

  return (
    <Shell title={t('cycTitle')} sub={t('cycSub')}>
      <PageHeader
        eyebrow={t('cycEyebrow')}
        title={t('cycTitle')}
        sub={t('cycPageSub')}
        actions={
          <>
            <Button variant="primary" onClick={printHaseenPdf}>{t('cycPrintHaseenPdf')}</Button>
            <Button variant="secondary" Icon={Download} onClick={() => exportHaseen('xlsx')}>{lang === 'ar' ? 'تصدير Excel' : 'Export Excel'}</Button>
            <Button variant="secondary" Icon={Download} onClick={() => exportHaseen('docx')}>{lang === 'ar' ? 'تصدير Word' : 'Export Word'}</Button>
            <Button variant="ghost" Icon={Download} onClick={downloadHaseenPacket}>{t('cycDownloadHaseen')}</Button>
          </>
        }
      />

      <div className="kpi-grid cyc-kpi-strip">
        <Kpi Icon={ShieldCheck}    label={t('cycKpiMaturity')}      value={`${stats.filingMaturity}/5`} tone={stats.filingMaturity >= 4 ? 'success' : 'warning'} to="/nca-ecc/filings" />
        <Kpi Icon={AlertOctagon}    label={t('cycKpiCritical')}       value={String(stats.vulnsCritical)} tone={stats.vulnsCritical > 0 ? 'danger' : 'success'} to="/cyber/vuln-sla/dashboard" />
        <Kpi Icon={Clock}            label={t('cycKpiOverdue')}        value={String(stats.vulnsOverdue)} tone={stats.vulnsOverdue > 0 ? 'danger' : 'success'} to="/cyber/vuln-sla/dashboard" />
        <Kpi Icon={CheckCircle2}     label={t('cycKpiClosedMonth')}    value={String(stats.vulnsClosedThisMonth)} tone="success" to="/cyber/vuln-sla/dashboard" />
        <Kpi Icon={Radio}            label={t('cycKpiThreats')}        value={`${stats.threatsAcknowledged} / ${stats.threatsTotal}`} tone={stats.threatsAcknowledged === stats.threatsTotal ? 'success' : 'warning'} to="/cyber/threat-intel" />
        <Kpi Icon={AlertTriangle}    label={t('cycKpiSocP1')}          value={String(stats.socOpenP1)} tone={stats.socOpenP1 > 0 ? 'danger' : 'success'} to="/socops/events" />
        <Kpi Icon={Clock}             label={t('cycKpiMttr')}            value={`${stats.socAvgMttrHours}h`} tone="info" to="/socops/dashboard" />
        <Kpi Icon={Server}            label={t('cycKpiAssetsTier1')}    value={String(stats.assetsTier1)} tone="primary" to="/assets" />
      </div>

      {/* Tabs */}
      <div className="cyc-tab-row">
        {([
          { id: 'pipeline', labelKey: 'lpcTabPipeline', Icon: Activity },
          { id: 'ecc',     labelKey: 'cycTabEcc',     Icon: ShieldCheck },
          { id: 'vulns',   labelKey: 'cycTabVulns',   Icon: Bug },
          { id: 'assets',  labelKey: 'cycTabAssets',  Icon: Server },
          { id: 'threats', labelKey: 'cycTabThreats', Icon: Radio },
          { id: 'soc',     labelKey: 'cycTabSoc',     Icon: Activity },
          { id: 'pentest', labelKey: 'cycTabPentest', Icon: AlertTriangle },
        ] as { id: CycTab; labelKey: string; Icon: typeof Server }[]).map(td => (
          <button key={td.id} onClick={() => setTab(td.id)}
            className={`cyc-tab-btn${tab === td.id ? ' is-active' : ''}`}>
            <td.Icon size={13} />
            {t(td.labelKey)}
          </button>
        ))}
      </div>

      {tab === 'pipeline' && <VulnPipeline vulns={vulns} assets={assets}
        onAdvance={(id, fromStatus) => {
          const next: Record<string, VulnStatus> = {
            NEW: 'TRIAGED',
            TRIAGED: 'OWNER_ASSIGNED',
            OWNER_ASSIGNED: 'IN_REMEDIATION',
            IN_REMEDIATION: 'AWAITING_VERIFICATION',
            AWAITING_VERIFICATION: 'CLOSED',
          };
          const target = next[fromStatus];
          if (!target) return;
          transitionVuln(id, target);
          toast.success(t('lpcAdvancedToast'));
        }}
        onCardClick={() => setTab('vulns')} />}

      {tab === 'ecc' && <EccTab filing={filing}
        onTransition={(s) => {
          const by = s === 'PEER_REVIEW' ? 'Mohammed Al-Sulaiman (Head of Cyber Defense)' :
                     s === 'CISO_APPROVAL' ? 'Sara Al-Zahrani (CISO)' :
                     s === 'SUBMITTED' ? 'Sara Al-Zahrani (CISO)' : undefined;
          transitionFiling(s, by);
          toast.info(`${FILING_STATUS_LABEL[s][lang]}`);
        }} />}
      {tab === 'vulns' && <VulnsTab vulns={vulns} assets={assets}
        onTransition={(v, s) => { transitionVuln(v.id, s); toast.info(`${VULN_STATUS_LABEL[s][lang]}`); }} />}
      {tab === 'assets' && <AssetsTab assets={assets} vulns={vulns}
        onCreate={() => { createAsset('new-asset', 'New asset', 'أصل جديد', 'TIER-3-MEDIUM', 'Owner', false, 'RIYADH-PRIMARY'); toast.success(t('cycAssetCreatedToast')); }}
        onMarkPatched={(a) => { markPatched(a.id); toast.success(t('cycAssetPatchedToast')); }}
        onReclassify={(a, tier) => { reclassifyAsset(a.id, tier); toast.info(t('cycAssetReclassifiedToast')); }} />}
      {tab === 'threats' && <ThreatsTab threats={threats}
        onAck={(t_) => { acknowledgeThreat(t_.id); toast.success(t('cycThreatAckedToast')); }}
        onCreate={() => { createThreat('INTERNAL', 'MEDIUM', 'New internal advisory', 'تحذير داخلي جديد', 'Threat detected by internal CTI team.', 'تهديد رصده فريق الاستخبارات الداخلي.'); toast.success(t('cycThreatCreatedToast')); }} />}
      {tab === 'soc' && <SocTab alerts={alerts} assets={assets}
        onTransition={(a, s) => { transitionSocAlert(a.id, s); toast.info(`${SOC_STATUS_LABEL[s][lang]}`); }}
        onCreate={() => { createSocAlert('P3', 'MANUAL', 'Analyst-raised alert', 'تنبيه أنشأه المحلّل', 'Manual alert created by SOC analyst.', 'تنبيه يدوي من محلّل SOC.'); toast.success(t('cycSocCreatedToast')); }} />}
      {tab === 'pentest' && <PentestTab tests={tests}
        onSchedule={() => {
          const m = new Date().getMonth();
          const q = `Q${Math.floor(m / 3) + 1}-${new Date().getFullYear()}`;
          schedulePenTest('EXTERNAL', `New pentest ${q}`, `اختبار اختراق جديد ${q}`, 'TBD vendor', q);
          toast.success(t('cycPentestScheduledToast'));
        }}
        onTransition={(p, s) => { transitionPenTest(p.id, s); toast.info(`Pentest ${s}`); }}
        onRecordFindings={(p, c, h, m, pct) => { recordPenTestFindings(p.id, c, h, m, pct); toast.success(t('cycPentestFindingsRecordedToast')); }} />}
    </Shell>
  );
}

// ─── ECC tab ────────────────────────────────────────────────────────────────

function EccTab({ filing, onTransition }: { filing: EccFiling; onTransition: (s: EccFilingStatus) => void }) {
  const { t, lang } = useI18n();
  return (
    <>
      <Card title={t('cycEccTitle')} Icon={ShieldCheck}
        action={
          <div className="cyc-ecc-action">
            <Badge tone={FILING_STATUS_TONE[filing.status]}>{FILING_STATUS_LABEL[filing.status][lang]}</Badge>
            <Badge tone="primary">{filing.year}</Badge>
            {filing.ncaAckRef && <Badge tone="success" className="mono">{filing.ncaAckRef}</Badge>}
          </div>
        }>
        <div className="cyc-card-intro">
          {t('cycEccSub')}
        </div>

        <div className="cyc-ecc-summary">
          <div className={`stat-tile stat-tile-${filing.compositeMaturity >= 4 ? 'success' : 'warning'} cyc-ecc-maturity-tile`}>
            <div className="stat-tile-label">{t('cycCompositeMaturity')}</div>
            <div className="stat-tile-value">{filing.compositeMaturity}</div>
            <div className="cyc-ecc-maturity-tile__hint">/ 5.0</div>
          </div>
          <div>
            <div className="cyc-ecc-spoc">
              <b>{t('cycCiSpoc')}:</b> {filing.ciSpoc}
            </div>
            {filing.reviewedBy && (
              <div className="cyc-ecc-reviewer">
                {t('cycPeerReviewer')}: {filing.reviewedBy}
                {filing.reviewedAt && <> · <span className="mono">{filing.reviewedAt.slice(0, 10)}</span></>}
              </div>
            )}
            {filing.approvedBy && (
              <div className="cyc-ecc-approver">
                {t('cycCisoApprover')}: {filing.approvedBy}
                {filing.approvedAt && <> · <span className="mono">{filing.approvedAt.slice(0, 10)}</span></>}
              </div>
            )}

            {/* Workflow buttons */}
            <div className="cyc-ecc-workflow">
              {filing.status === 'DRAFT' && <Button size="sm" variant="primary" Icon={Send} onClick={() => onTransition('PEER_REVIEW')}>{t('cycSubmitPeer')}</Button>}
              {filing.status === 'PEER_REVIEW' && <Button size="sm" variant="primary" Icon={ThumbsUp} onClick={() => onTransition('CISO_APPROVAL')}>{t('cycPromoteCiso')}</Button>}
              {filing.status === 'CISO_APPROVAL' && <Button size="sm" variant="primary" Icon={Send} onClick={() => onTransition('SUBMITTED')}>{t('cycSubmitNca')}</Button>}
              {filing.status === 'SUBMITTED' && <Button size="sm" variant="primary" Icon={CheckCircle2} onClick={() => onTransition('ACCEPTED')}>{t('cycMarkAccepted')}</Button>}
            </div>
          </div>
        </div>
      </Card>

      {/* Per-domain scores */}
      <div className="cyc-domain-section">
        <Card title={t('cycDomainsTitle')} Icon={ShieldCheck}>
          {filing.scores.map(s => {
            const barTone = s.maturity >= 4 ? 'success' : s.maturity >= 3 ? 'primary' : 'warning';
            const valTone = s.maturity === 0 ? 'neutral' : s.maturity >= 4 ? 'success' : s.maturity >= 3 ? 'default' : 'warning';
            return (
            <div key={s.domain} className="cyc-domain-card">
              <div className="cyc-domain-head">
                <div className="cyc-domain-main">
                  <div className="cyc-domain-chips">
                    <span className="mono cyc-domain-code">{t('cycDomain')} {s.domain}</span>
                    <span className="cyc-domain-name">{lang === 'ar' ? s.nameAr : s.nameEn}</span>
                  </div>
                  <div className="cyc-domain-counts">
                    <span>{s.controlsTotal} {t('cycControlsTotal')}</span>
                    <span className="cyc-domain-counts__evidenced">{s.evidencedControls} {t('cycEvidenced')}</span>
                    {s.remediationControls > 0 && (
                      <span className="cyc-domain-counts__remediation">{s.remediationControls} {t('cycRemediation')}</span>
                    )}
                  </div>
                  <div className="cyc-domain-narrative">
                    {lang === 'ar' ? s.narrativeAr : s.narrativeEn}
                  </div>
                </div>
                <div className="cyc-domain-maturity">
                  <div className="cyc-domain-maturity__label">{t('cycMaturity')}</div>
                  <div className="mono cyc-domain-maturity__value" data-tone={valTone}>
                    {s.maturity === 0 ? 'N/A' : s.maturity}
                  </div>
                </div>
              </div>
              {/* Bar */}
              {s.maturity > 0 && (
                <div className="cyc-bar-track">
                  <div className="cyc-bar-fill" data-tone={barTone}
                    style={{ width: `${(s.maturity / 5) * 100}%` }} />
                </div>
              )}
            </div>
            );
          })}
        </Card>
      </div>
    </>
  );
}

// ─── Vulns tab ──────────────────────────────────────────────────────────────

function VulnsTab({ vulns, assets, onTransition }: {
  vulns: Vulnerability[];
  assets: CyberAsset[];
  onTransition: (v: Vulnerability, s: VulnStatus) => void;
}) {
  const { t, lang } = useI18n();
  const sorted = [...vulns].sort((a, b) => {
    if (a.status === 'CLOSED' && b.status !== 'CLOSED') return 1;
    if (a.status !== 'CLOSED' && b.status === 'CLOSED') return -1;
    return new Date(a.dueDate).getTime() - new Date(b.dueDate).getTime();
  });

  return (
    <Card title={t('cycVulnsTitle')} Icon={Bug}>
      <div className="cyc-card-intro">
        {t('cycVulnsSub')}
      </div>
      <table className="tbl cyc-table-full">
        <thead>
          <tr>
            <th>{t('cycVulnId')}</th>
            <th>{t('cycVulnTitle')}</th>
            <th>{t('cycVulnAsset')}</th>
            <th>CVSS</th>
            <th>{t('cycVulnDue')}</th>
            <th>{t('cycVulnStatus')}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {sorted.map(v => {
            const asset = assets.find(a => a.id === v.assetId);
            const overdue = new Date(v.dueDate) < new Date() && v.status !== 'CLOSED';
            return (
              <tr key={v.id}>
                <td>
                  <div className="mono cyc-cell-id">{v.id}</div>
                  <div className="mono cyc-cell-id-sub">{v.cveId}</div>
                </td>
                <td>
                  <div className="cyc-cell-title">{lang === 'ar' ? v.titleAr : v.titleEn}</div>
                  {v.notesEn && (
                    <div className="cyc-cell-notes">
                      {lang === 'ar' ? v.notesAr : v.notesEn}
                    </div>
                  )}
                </td>
                <td className="cyc-cell-asset">
                  {asset?.name ?? v.assetId}
                  {asset && <div><Badge tone={TIER_TONE[asset.tier]}>{asset.tier.split('-')[1]}</Badge></div>}
                </td>
                <td>
                  <Badge tone={VULN_SEVERITY_TONE[v.severity]}>{v.cvssScore} · {v.severity}</Badge>
                </td>
                <td className={`mono cyc-cell-due${overdue ? ' cyc-cell-due--overdue' : ''}`}>
                  {v.dueDate}
                  <div className="cyc-cell-due__sla">SLA {v.slaDays}d</div>
                </td>
                <td>
                  <Badge tone={VULN_STATUS_TONE[v.status]}>{VULN_STATUS_LABEL[v.status][lang]}</Badge>
                </td>
                <td>
                  {v.status === 'NEW' && <Button size="sm" variant="ghost" onClick={() => onTransition(v, 'TRIAGED')}>{t('cycTriage')}</Button>}
                  {v.status === 'TRIAGED' && <Button size="sm" variant="ghost" onClick={() => onTransition(v, 'OWNER_ASSIGNED')}>{t('cycAssign')}</Button>}
                  {v.status === 'OWNER_ASSIGNED' && <Button size="sm" variant="ghost" onClick={() => onTransition(v, 'IN_REMEDIATION')}>{t('cycStartRem')}</Button>}
                  {v.status === 'IN_REMEDIATION' && <Button size="sm" variant="ghost" onClick={() => onTransition(v, 'AWAITING_VERIFICATION')}>{t('cycSubmitVerif')}</Button>}
                  {v.status === 'AWAITING_VERIFICATION' && <Button size="sm" variant="primary" Icon={CheckCircle2} onClick={() => onTransition(v, 'CLOSED')}>{t('cycCloseVuln')}</Button>}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </Card>
  );
}

// ─── Assets tab ─────────────────────────────────────────────────────────────

function AssetsTab({ assets, vulns, onCreate, onMarkPatched, onReclassify }: {
  assets: CyberAsset[]; vulns: Vulnerability[];
  onCreate: () => void;
  onMarkPatched: (a: CyberAsset) => void;
  onReclassify: (a: CyberAsset, tier: AssetTier) => void;
}) {
  const { t, lang } = useI18n();
  return (
    <Card title={t('cycAssetsTitle')} Icon={Server}
      action={<Button size="sm" variant="primary" onClick={onCreate}>{t('cycAssetCreate')}</Button>}>
      <div className="cyc-card-intro">
        {t('cycAssetsSub')}
      </div>
      <table className="tbl cyc-table-full">
        <thead>
          <tr>
            <th>{t('cycAssetId')}</th>
            <th>{t('cycAssetName')}</th>
            <th>{t('cycAssetTier')}</th>
            <th>{t('cycAssetExposure')}</th>
            <th>{t('cycAssetLocation')}</th>
            <th>{t('cycAssetOwner')}</th>
            <th>{t('cycAssetLastPatched')}</th>
            <th>{t('cycAssetOpenVulns')}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {assets.map(a => {
            const openVulns = vulns.filter(v => v.assetId === a.id && v.status !== 'CLOSED');
            return (
              <tr key={a.id}>
                <td className="mono cyc-cell-id">{a.id}</td>
                <td>
                  <div className="mono cyc-asset-name">{a.name}</div>
                  <div className="cyc-asset-desc">
                    {lang === 'ar' ? a.descriptionAr : a.descriptionEn}
                  </div>
                </td>
                <td>
                  <select value={a.tier} onChange={e => onReclassify(a, e.target.value as AssetTier)}
                    className="cyc-asset-tier-select">
                    {(['TIER-1-CRITICAL', 'TIER-2-HIGH', 'TIER-3-MEDIUM', 'TIER-4-LOW'] as AssetTier[]).map(tr =>
                      <option key={tr} value={tr}>{TIER_LABEL[tr][lang]}</option>
                    )}
                  </select>
                </td>
                <td>
                  {a.externalFacing
                    ? <Badge tone="warning">{t('cycExternal')}</Badge>
                    : <Badge tone="neutral">{t('cycInternal')}</Badge>}
                </td>
                <td className="mono cyc-cell-mono-11">{a.location}</td>
                <td className="cyc-cell-mono-11">{a.owner}</td>
                <td className="mono cyc-cell-mono-11">{a.lastPatchedAt ?? '—'}</td>
                <td>
                  {openVulns.length === 0
                    ? <Badge tone="success">0</Badge>
                    : <Badge tone={openVulns.some(v => v.severity === 'CRITICAL') ? 'danger' : 'warning'}>{openVulns.length}</Badge>}
                </td>
                <td>
                  <Button size="sm" variant="ghost" onClick={() => onMarkPatched(a)}>{t('cycAssetMarkPatched')}</Button>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </Card>
  );
}

// ─── Threats tab ────────────────────────────────────────────────────────────

function ThreatsTab({ threats, onAck, onCreate }: {
  threats: ThreatAdvisory[];
  onAck: (t: ThreatAdvisory) => void;
  onCreate: () => void;
}) {
  const { t, lang } = useI18n();
  const sorted = [...threats].sort((a, b) => new Date(b.issuedAt).getTime() - new Date(a.issuedAt).getTime());
  return (
    <Card title={t('cycThreatsTitle')} Icon={Radio}
      action={<Button size="sm" variant="primary" onClick={onCreate}>{t('cycThreatCreate')}</Button>}>
      <div className="cyc-card-intro">
        {t('cycThreatsSub')}
      </div>
      {sorted.map(th => (
        <div key={th.id} className="cyc-card">
          <div className="cyc-card-head">
            <div className="cyc-card-main">
              <div className="cyc-card-chips">
                <Badge tone={th.severity === 'CRITICAL' ? 'danger' : th.severity === 'HIGH' ? 'warning' : 'info'}>{th.severity}</Badge>
                <Badge tone="primary">{THREAT_SOURCE_LABEL[th.source][lang]}</Badge>
                <span className="mono cyc-card-mono">{th.sourceRef}</span>
                <span className="mono cyc-card-mono">· {th.issuedAt.slice(0, 10)}</span>
                {th.acknowledged
                  ? <Badge tone="success"><CheckCircle2 size={11} /> {t('cycAcked')}</Badge>
                  : <Badge tone="warning">{t('cycNotAcked')}</Badge>}
              </div>
              <div className="cyc-card-title">{lang === 'ar' ? th.titleAr : th.titleEn}</div>
            </div>
            {!th.acknowledged && (
              <Button size="sm" variant="primary" onClick={() => onAck(th)}>{t('cycAcknowledge')}</Button>
            )}
          </div>

          <div className="cyc-threat-summary">
            {lang === 'ar' ? th.summaryAr : th.summaryEn}
          </div>

          {th.affectedProducts.length > 0 && (
            <div className="cyc-threat-affected">
              <span className="cyc-threat-affected__label">{t('cycAffected')}:</span>
              {th.affectedProducts.map(p => <Badge key={p} tone="info">{p}</Badge>)}
            </div>
          )}

          <div className="cyc-threat-recommend">
            <div className="cyc-threat-recommend__label">
              {t('cycRecommendedAction')}
            </div>
            {lang === 'ar' ? th.recommendedActionAr : th.recommendedActionEn}
          </div>

          {th.actionNotesEn && (
            <div className="cyc-threat-actiontaken">
              <div className="cyc-threat-actiontaken__label">
                {t('cycActionTaken')}
              </div>
              {lang === 'ar' ? th.actionNotesAr : th.actionNotesEn}
            </div>
          )}
        </div>
      ))}
    </Card>
  );
}

// ─── SOC tab ────────────────────────────────────────────────────────────────

function SocTab({ alerts, assets, onTransition, onCreate }: {
  alerts: SocAlert[];
  assets: CyberAsset[];
  onTransition: (a: SocAlert, s: SocStatus) => void;
  onCreate: () => void;
}) {
  const { t, lang } = useI18n();
  const sorted = [...alerts].sort((a, b) => new Date(b.detectedAt).getTime() - new Date(a.detectedAt).getTime());
  return (
    <Card title={t('cycSocTitle')} Icon={Activity}
      action={<Button size="sm" variant="primary" onClick={onCreate}>{t('cycSocCreate')}</Button>}>
      <div className="cyc-card-intro">
        {t('cycSocSub')}
      </div>
      {sorted.map(a => {
        const asset = assets.find(x => x.id === a.assetId);
        const mttr = a.resolvedAt
          ? Math.round((new Date(a.resolvedAt).getTime() - new Date(a.detectedAt).getTime()) / 360_000) / 10
          : null;
        return (
          <div key={a.id} className="cyc-soc-card">
            <div className="cyc-soc-card__head">
              <div className="cyc-soc-card__main">
                <div className="cyc-card-chips">
                  <Badge tone={SOC_SEVERITY_TONE[a.severity]}>{a.severity}</Badge>
                  <Badge tone={SOC_STATUS_TONE[a.status]}>{SOC_STATUS_LABEL[a.status][lang]}</Badge>
                  <Badge tone="info">{a.detector}</Badge>
                  {asset && <Badge tone={TIER_TONE[asset.tier]} className="mono">{asset.name}</Badge>}
                  <span className="mono cyc-card-mono">{a.id}</span>
                  {a.irRefId && <Badge tone="warning"><ArrowUpRight size={10} /> {a.irRefId}</Badge>}
                </div>
                <div className="cyc-soc-card__title">{lang === 'ar' ? a.titleAr : a.titleEn}</div>
                <div className="cyc-soc-card__narrative">
                  {lang === 'ar' ? a.narrativeAr : a.narrativeEn}
                </div>
                <div className="cyc-soc-card__meta">
                  {a.analyst} · <span className="mono">{a.detectedAt.slice(0, 16).replace('T', ' ')}</span>
                  {mttr !== null && <> · MTTR <span className="mono">{mttr}h</span></>}
                </div>
              </div>
              <div className="cyc-card-actions">
                {a.status === 'OPEN' && <Button size="sm" variant="primary" onClick={() => onTransition(a, 'INVESTIGATING')}>{t('cycInvestigate')}</Button>}
                {a.status === 'INVESTIGATING' && (
                  <>
                    <Button size="sm" variant="primary" onClick={() => onTransition(a, 'CONTAINED')}>{t('cycContain')}</Button>
                    <Button size="sm" variant="ghost" Icon={ArrowUpRight} onClick={() => onTransition(a, 'ESCALATED_TO_IR')}>{t('cycEscalateIr')}</Button>
                  </>
                )}
                {a.status === 'CONTAINED' && <Button size="sm" variant="primary" Icon={CheckCircle2} onClick={() => onTransition(a, 'CLOSED')}>{t('cycClose')}</Button>}
              </div>
            </div>
          </div>
        );
      })}
    </Card>
  );
}

// ─── Pentest tab ────────────────────────────────────────────────────────────

function PentestTab({ tests, onSchedule, onTransition, onRecordFindings }: {
  tests: PenTest[];
  onSchedule: () => void;
  onTransition: (p: PenTest, s: PenTestStatus) => void;
  onRecordFindings: (p: PenTest, c: number, h: number, m: number, pct: number) => void;
}) {
  const { t, lang } = useI18n();
  return (
    <Card title={t('cycPentestTitle')} Icon={AlertTriangle}
      action={<Button size="sm" variant="primary" onClick={onSchedule}>{t('cycPentestSchedule')}</Button>}>
      <div className="cyc-card-intro">
        {t('cycPentestSub')}
      </div>
      {tests.map(p => {
        const remTone = p.remediationPct >= 80 ? 'success' : p.remediationPct >= 40 ? 'warning' : 'danger';
        return (
        <div key={p.id} className="cyc-card">
          <div className="cyc-card-head">
            <div className="cyc-card-main">
              <div className="cyc-card-chips">
                <span className="mono cyc-pt-card-id">{p.id}</span>
                <Badge tone={PENTEST_STATUS_TONE[p.status]}>{PENTEST_STATUS_LABEL[p.status][lang]}</Badge>
                <Badge tone="info">{p.scope}</Badge>
                <Badge tone="primary">{p.scheduledQuarter}</Badge>
                <span className="cyc-pt-card-vendor">{t('cycPentestVendor')}: <b>{p.vendor}</b></span>
              </div>
              <div className="cyc-card-title">{lang === 'ar' ? p.titleAr : p.titleEn}</div>
              <div className="cyc-soc-card__narrative">
                {lang === 'ar' ? p.scopeNotesAr : p.scopeNotesEn}
              </div>
            </div>
            {p.status !== 'PLANNED' && (
              <div className="cyc-pt-stats">
                <Stat label={t('cycSevC')} value={String(p.criticalFindings)} tone="danger" />
                <Stat label={t('cycSevH')} value={String(p.highFindings)} tone="warning" />
                <Stat label={t('cycSevM')} value={String(p.mediumFindings)} tone="info" />
              </div>
            )}
          </div>
          {p.status !== 'PLANNED' && (
            <div className="cyc-pt-rem-row">
              <span className="cyc-pt-rem-row__label">{t('cycRemediation')}:</span>
              <div className="cyc-pt-rem-track">
                <div className="cyc-pt-rem-fill" data-tone={remTone}
                  style={{ width: `${p.remediationPct}%` }} />
              </div>
              <span className="mono cyc-pt-rem-pct">{p.remediationPct}%</span>
            </div>
          )}
          {/* Pentest action row */}
          <div className="cyc-pt-actions">
            {p.status === 'PLANNED' && <Button size="sm" variant="primary" onClick={() => onTransition(p, 'IN_PROGRESS')}>{t('cycPentestStart')}</Button>}
            {p.status === 'IN_PROGRESS' && <Button size="sm" variant="primary" onClick={() => onTransition(p, 'REPORT_DRAFT')}>{t('cycPentestReportDraft')}</Button>}
            {p.status === 'REPORT_DRAFT' && (
              <>
                <input type="number" placeholder="C" min={0} max={99} defaultValue={p.criticalFindings}
                  id={`pt-c-${p.id}`} className="mono cyc-pt-num cyc-pt-num--w-50" />
                <input type="number" placeholder="H" min={0} max={99} defaultValue={p.highFindings}
                  id={`pt-h-${p.id}`} className="mono cyc-pt-num cyc-pt-num--w-50" />
                <input type="number" placeholder="M" min={0} max={99} defaultValue={p.mediumFindings}
                  id={`pt-m-${p.id}`} className="mono cyc-pt-num cyc-pt-num--w-50" />
                <input type="number" placeholder="%" min={0} max={100} defaultValue={p.remediationPct}
                  id={`pt-pct-${p.id}`} className="mono cyc-pt-num cyc-pt-num--w-60" />
                <Button size="sm" variant="primary" onClick={() => {
                  const cv = Number((document.getElementById(`pt-c-${p.id}`) as HTMLInputElement)?.value || 0);
                  const hv = Number((document.getElementById(`pt-h-${p.id}`) as HTMLInputElement)?.value || 0);
                  const mv = Number((document.getElementById(`pt-m-${p.id}`) as HTMLInputElement)?.value || 0);
                  const pctv = Number((document.getElementById(`pt-pct-${p.id}`) as HTMLInputElement)?.value || 0);
                  onRecordFindings(p, cv, hv, mv, pctv);
                }}>{t('cycPentestRecordFindings')}</Button>
                <Button size="sm" variant="ghost" onClick={() => onTransition(p, 'CLOSED')}>{t('cycPentestClose')}</Button>
              </>
            )}
          </div>
        </div>
        );
      })}
    </Card>
  );
}

function Stat({ label, value, tone }: { label: string; value: string; tone: 'danger' | 'warning' | 'info' }) {
  return (
    <div className="cyc-stat-tile" data-tone={tone}>
      <div className="cyc-stat-tile__label">{label}</div>
      <div className="mono cyc-stat-tile__value">{value}</div>
    </div>
  );
}

// ─── Vulnerability pipeline (interactive kanban) ───────────────────────────

function VulnPipeline({ vulns, assets, onAdvance, onCardClick }: {
  vulns: Vulnerability[];
  assets: CyberAsset[];
  onAdvance: (id: string, fromStatus: string, toStatus: string | null) => void;
  onCardClick: (id: string) => void;
}) {
  const stages: PipelineStage[] = [
    { code: 'NEW',                   labelEn: 'New',                  labelAr: 'جديدة',                 tone: 'danger',  Icon: AlertOctagon },
    { code: 'TRIAGED',               labelEn: 'Triaged',              labelAr: 'مفروزة',                 tone: 'warning', Icon: Bug },
    { code: 'OWNER_ASSIGNED',        labelEn: 'Owner assigned',       labelAr: 'تم تعيين المالك',        tone: 'info',    Icon: Send },
    { code: 'IN_REMEDIATION',        labelEn: 'In remediation',       labelAr: 'قيد المعالجة',           tone: 'warning', Icon: Wrench },
    { code: 'AWAITING_VERIFICATION', labelEn: 'Awaiting verification', labelAr: 'بانتظار التحقق',        tone: 'info',    Icon: Clock },
    { code: 'CLOSED',                labelEn: 'Closed',                labelAr: 'مُغلَقة',                tone: 'success', Icon: CheckCircle2 },
  ];
  const cards: PipelineCard[] = vulns.map(v => {
    const asset = assets.find(a => a.id === v.assetId);
    const due = dueLabel(v.dueDate, () => '');
    return {
      id: v.id, status: v.status, refCode: v.cveId,
      titleEn: v.titleEn, titleAr: v.titleAr,
      subtitleEn: asset ? `${asset.name} · ${asset.tier.split('-')[1]}` : v.assetId,
      subtitleAr: asset ? `${asset.name} · ${asset.tier.split('-')[1]}` : v.assetId,
      badges: [
        { label: `CVSS ${v.cvssScore}`, tone: v.severity === 'CRITICAL' ? 'danger' : v.severity === 'HIGH' ? 'warning' : v.severity === 'MEDIUM' ? 'info' : 'neutral' as const },
        { label: `SLA ${v.slaDays}d`, tone: 'neutral' as const },
      ],
      urgent: due?.urgent && v.status !== 'CLOSED',
      dueLabel: due?.label,
    };
  });
  return (
    <LifecyclePipeline
      stages={stages}
      cards={cards}
      introEn="Live vulnerability pipeline. SLA in days driven by CVSS severity (Critical 7d / High 14d / Medium 30d / Low 60d). Click a card to drill into the detailed vulnerability tab."
      introAr="مسار الثغرات الحيّ. SLA بالأيام مدفوع بخطورة CVSS (حرجة 7 / عالية 14 / متوسطة 30 / منخفضة 60). انقر على بطاقة للانتقال إلى تبويب تفاصيل الثغرات."
      onAdvance={onAdvance}
      onCardClick={onCardClick}
      emptyEn="No vulnerabilities recorded yet."
      emptyAr="لا ثغرات مسجَّلة بعد."
    />
  );
}
