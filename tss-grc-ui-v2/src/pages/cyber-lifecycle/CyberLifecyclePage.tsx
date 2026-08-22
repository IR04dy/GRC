import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  ShieldCheck, AlertTriangle, Activity, Server, Bug, Radio,
  Send, ThumbsUp, CheckCircle2, ArrowUpRight, Clock,
  AlertOctagon, Wrench,
} from 'lucide-react';
import { LifecyclePipeline, type PipelineStage, type PipelineCard, dueLabel } from '../../components/lifecycle/LifecyclePipeline';
import { ApiError } from '../../lib/api';
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
  loadVulns, transitionVuln, hydrateVulns, vulnsLoadFailed,
  nextVulnStatuses, primaryNextVulnStatus, isVulnStatusManaged, isVulnOpen, isVulnOverdue,
  vulnStatusLabel, vulnStatusTone,
  loadAssets, createAsset, markPatched, reclassifyAsset,
  loadThreats, acknowledgeThreat, createThreat,
  loadSocAlerts, transitionSocAlert, createSocAlert,
  loadPenTests, schedulePenTest, transitionPenTest, recordPenTestFindings,
  subscribeCyber, aggregateCyber,
  FILING_STATUS_LABEL, FILING_STATUS_TONE,
  TIER_LABEL, TIER_TONE,
  VULN_SEVERITY_TONE,
  SOC_SEVERITY_TONE, SOC_STATUS_LABEL, SOC_STATUS_TONE,
  THREAT_SOURCE_LABEL,
  PENTEST_STATUS_LABEL, PENTEST_STATUS_TONE,
  type EccFiling, type Vulnerability, type CyberAsset, type ThreatAdvisory, type SocAlert, type PenTest,
  type EccFilingStatus, type VulnStatus, type SocStatus, type AssetTier, type PenTestStatus,
} from '../../lib/cyberLifecycle';
import '../../styles/pages/cyber-lifecycle.css';

type CycTab = 'pipeline' | 'ecc' | 'vulns' | 'assets' | 'threats' | 'soc' | 'pentest';

const CYC_TABS: ReadonlyArray<CycTab> = ['pipeline', 'ecc', 'vulns', 'assets', 'threats', 'soc', 'pentest'];

function isCycTab(value: string | null | undefined): value is CycTab {
  return !!value && (CYC_TABS as readonly string[]).includes(value);
}

export function CyberLifecyclePage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const navigate = useNavigate();
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

  // F2-205: the white-label subscription is gone with the export. Its only use was naming the
  // reporting entity on the fabricated NCA Haseen cover — the same role `brandName` played in
  // F2-199's ECC submission, where "TSS GRC Platform" (the software product) was filed as the
  // reporting organisation for every tenant.

  // ⚠⚠ F2-205: `downloadHaseenPacket`, `printHaseenPdf` and `exportHaseen` are DELETED. All
  // three emitted the "NCA Haseen — ECC v3 Annual Self-Assessment Filing" — .txt download,
  // print-to-PDF stamped "CONFIDENTIAL — REGULATOR", and branded .xlsx/.docx through the
  // platform's export subsystem — entirely out of `lib/cyberLifecycle.ts`'s browser constants.
  // The real register is `nca_ecc.filings` (4 filings, dev and prod) and `NcaEccFilingsPage`
  // already renders it at /nca-ecc/filings, so this is ADR-017 §D-5 retirement, the same answer
  // F2-42, F2-198 and F2-199 reached. See the note at the deleted generators for why F2-199's
  // own export gate passed this page, and `exportProvenance.test.ts` for the fix.

  // F2-205: the vulnerability register is SERVER-BACKED now. Three pages hydrate this store and
  // `hydrateVulns` shares one request between them.
  useEffect(() => { void hydrateVulns(); }, []);

  /** Surface a server refusal instead of swallowing it — the machine answers 409, not silence. */
  async function runVuln(action: () => Promise<unknown>, okMsg: string) {
    try {
      await action();
      toast.success(okMsg);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (lang === 'ar' ? 'تعذّر تنفيذ الإجراء' : 'The action could not be completed'));
    }
  }

  return (
    <Shell title={t('cycTitle')} sub={t('cycSub')}>
      <PageHeader
        eyebrow={t('cycEyebrow')}
        title={t('cycTitle')}
        sub={t('cycPageSub')}
        actions={
          // F2-205: the four NCA Haseen export buttons stood here. See the comment above
          // `runVuln` — every figure they emitted was a browser constant, and the real filing
          // register is rendered at /nca-ecc/filings.
          <Button variant="secondary" onClick={() => navigate('/nca-ecc/filings')}>
            {lang === 'ar' ? 'سجل تقديمات الهيئة' : 'NCA filing register'}
          </Button>
        }
      />

      {vulnsLoadFailed() && (
        <div className="cyc-load-error" role="alert">
          {lang === 'ar'
            ? 'تعذّر تحميل سجل الثغرات من الخادم. الأرقام أدناه غير مكتملة.'
            : 'The vulnerability register could not be loaded from the server. The figures below are incomplete.'}
          <Button size="sm" variant="ghost" onClick={() => { void hydrateVulns(true); }}>
            {lang === 'ar' ? 'إعادة المحاولة' : 'Retry'}
          </Button>
        </div>
      )}

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

      {/* ⚠⚠ F2-205: this handler used to hold its OWN linear `Record<string, VulnStatus>` — a
          third vocabulary for the machine, with no branch to RISK_ACCEPTED or FALSE_POSITIVE, so
          neither disposition was reachable from the pipeline at all. It now asks
          `primaryNextVulnStatus`, which is declared once in the lib and gated against
          `VULN_ALLOWED`, and the transition is performed by the SERVER. */}
      {tab === 'pipeline' && <VulnPipeline vulns={vulns} assets={assets}
        onAdvance={(id, fromStatus) => {
          const target = primaryNextVulnStatus(fromStatus);
          if (!target) return;
          void runVuln(() => transitionVuln(id, target), t('lpcAdvancedToast'));
        }}
        onCardClick={() => setTab('vulns')} />}

      {/* ⚠⚠ F2-205: this passed `'Mohammed Al-Sulaiman (Head of Cyber Defense)'` and
          `'Sara Al-Zahrani (CISO)'` as the officers who peer-reviewed and CISO-approved the ECC
          return — two invented individuals presented as this organisation's cyber leadership,
          the fifth and sixth in this programme after F2-201's approver registry and F2-204's CRO.
          Nothing persists them today because the filing store is still browser-local, and that
          is precisely why they had to go NOW: F2-204's finding was a naive wire turning an
          invented officer into a stored one, and whoever wires `nca_ecc.filings` next would have
          inherited these. An approval is attributed by the server from the authenticated
          principal or not at all. */}
      {tab === 'ecc' && <EccTab filing={filing}
        onTransition={(s) => {
          transitionFiling(s);
          toast.info(`${FILING_STATUS_LABEL[s][lang]}`);
        }} />}
      {tab === 'vulns' && <VulnsTab vulns={vulns} assets={assets}
        onTransition={(v, st) => {
          void runVuln(() => transitionVuln(v.id, st), vulnStatusLabel(st, lang));
        }} />}
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
            {/* F2-269 - no NCA acknowledgement badge. It rendered `ncaAckRef`, minted
                here with Math.random() on the ACCEPTED transition, as a green success
                badge - a fabricated statement that the National Cybersecurity Authority
                had accepted this filing. There is no column and no NCA integration that
                could receive a real one, so per F2-209 it is deleted rather than
                rewired. The real register is at /nca-ecc/filings (F2-205). */}
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
  // F2-205: `isVulnOpen` is the complement of the DERIVED terminal set, and rows with no
  // deadline yet (not triaged) sort last among open ones rather than to an Invalid Date.
  const sorted = [...vulns].sort((a, b) => {
    const ao = isVulnOpen(a.status), bo = isVulnOpen(b.status);
    if (ao !== bo) return ao ? -1 : 1;
    const ad = a.slaDeadline ? new Date(a.slaDeadline).getTime() : Number.MAX_SAFE_INTEGER;
    const bd = b.slaDeadline ? new Date(b.slaDeadline).getTime() : Number.MAX_SAFE_INTEGER;
    return ad - bd;
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
            const overdue = isVulnOverdue(v);
            const managed = isVulnStatusManaged(v.status);
            return (
              <tr key={v.id}>
                <td>
                  <div className="mono cyc-cell-id">{v.id}</div>
                  <div className="mono cyc-cell-id-sub">{v.cveId}</div>
                </td>
                <td>
                  <div className="cyc-cell-title">{lang === 'ar' ? v.titleAr : v.titleEn}</div>
                  {/* F2-205: the server's `description`, not the deleted corpus's invented
                      `notes`. `remediation_method` is populated on 0 of the 36 real rows, so the
                      confident remediation narratives the seed carried were not stale data. */}
                  {(lang === 'ar' ? v.descriptionAr : v.descriptionEn) && (
                    <div className="cyc-cell-notes">
                      {lang === 'ar' ? v.descriptionAr : v.descriptionEn}
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
                {/* F2-205: `slaDeadline` is the row's own fact. `SLA {v.slaDays}d` stood here
                    and there is no such column — the server computes the deadline once, at
                    triage, from its NCA ECC severity matrix. Re-deriving the matrix in the
                    browser would be a second copy of the policy, which is this slice's defect.
                    A row not yet triaged has no deadline, and reads as unset rather than as
                    today (F2-197: an absence must never read as health). */}
                <td className={`mono cyc-cell-due${overdue ? ' cyc-cell-due--overdue' : ''}`}>
                  {v.slaDeadline ?? (
                    <span title={lang === 'ar'
                      ? 'يُحدَّد الموعد النهائي عند الفرز'
                      : 'The deadline is set at triage'}>—</span>
                  )}
                  {typeof v.breachCount === 'number' && v.breachCount > 0 && (
                    <div className="cyc-cell-due__sla">
                      {lang === 'ar' ? `تجاوزات: ${v.breachCount}` : `Breaches: ${v.breachCount}`}
                    </div>
                  )}
                </td>
                <td>
                  <Badge tone={vulnStatusTone(v.status)}>{vulnStatusLabel(v.status, lang)}</Badge>
                  {!managed && (
                    <div className="cyc-cell-notes" role="status">
                      {lang === 'ar'
                        ? 'حالة غير معروفة لآلة المعالجة — لا يمكن تقديمها'
                        : 'A status the remediation machine does not declare — it cannot be advanced'}
                    </div>
                  )}
                  {v.status === 'RISK_ACCEPTED' && !v.riskAcceptedBy && (
                    <div className="cyc-cell-notes" role="status">
                      {lang === 'ar'
                        ? 'قابل المخاطرة غير مُسجَّل'
                        : 'No risk acceptor recorded'}
                    </div>
                  )}
                </td>
                {/* ⚠⚠ F2-205: five hand-written `v.status === '…' &&` branches stood here, one
                    per stage of the linear path. Between them they offered NEITHER
                    `RISK_ACCEPTED` nor `FALSE_POSITIVE` from any state — so this table, the
                    pipeline's `onAdvance`, the `VulnStatus` union and the overview's stage list
                    were four surfaces that agreed the ECC machine had no branches. It has two.
                    The buttons are now the machine's own answer, so a state that gains an edge
                    gains a control and one that loses an edge loses it. */}
                <td>
                  {nextVulnStatuses(v.status).map(target => (
                    <Button key={target} size="sm"
                      variant={target === 'CLOSED' ? 'primary' : 'ghost'}
                      Icon={target === 'CLOSED' ? CheckCircle2 : undefined}
                      onClick={() => onTransition(v, target)}>
                      {vulnStatusLabel(target, lang)}
                    </Button>
                  ))}
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
                {/* F2-269 - em-dash rather than a blank when the advisory carried no reference. */}
                <span className="mono cyc-card-mono" title={th.sourceRef ? undefined : (lang === 'ar' ? 'لم تُرفق الجهة المُصدِرة رقم تعميم' : 'The issuing body supplied no advisory number')} aria-label={th.sourceRef ?? (lang === 'ar' ? 'لم تُرفق الجهة المُصدِرة رقم تعميم' : 'The issuing body supplied no advisory number')}>{th.sourceRef ?? '—'}</span>
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
    // F2-205: RISK_ACCEPTED and FALSE_POSITIVE were absent from this list, so 7 of the 36 real
    // rows had no column to land in and simply did not appear on the pipeline.
    { code: 'RISK_ACCEPTED',         labelEn: 'Risk accepted',         labelAr: 'مقبولة كمخاطرة',        tone: 'neutral', Icon: ThumbsUp },
    { code: 'FALSE_POSITIVE',        labelEn: 'False positive',        labelAr: 'إنذار كاذب',             tone: 'neutral', Icon: AlertTriangle },
    { code: 'CLOSED',                labelEn: 'Closed',                labelAr: 'مُغلَقة',                tone: 'success', Icon: CheckCircle2 },
  ];
  const cards: PipelineCard[] = vulns.map(v => {
    const asset = assets.find(a => a.id === v.assetId);
    const due = v.slaDeadline ? dueLabel(v.slaDeadline, () => '') : undefined;
    return {
      id: v.id, status: v.status, refCode: v.cveId,
      titleEn: v.titleEn, titleAr: v.titleAr,
      subtitleEn: asset ? `${asset.name} · ${asset.tier.split('-')[1]}` : v.assetId,
      subtitleAr: asset ? `${asset.name} · ${asset.tier.split('-')[1]}` : v.assetId,
      // F2-205: the `SLA {v.slaDays}d` badge is gone with the field — see the Vulnerability
      // interface. `breachCount` is a real column and says something the deadline cannot.
      badges: [
        { label: `CVSS ${v.cvssScore ?? '—'}`, tone: v.severity === 'CRITICAL' ? 'danger' : v.severity === 'HIGH' ? 'warning' : v.severity === 'MEDIUM' ? 'info' : 'neutral' as const },
        ...(typeof v.breachCount === 'number' && v.breachCount > 0
          ? [{ label: `SLA ×${v.breachCount}`, tone: 'danger' as const }]
          : []),
      ],
      urgent: due?.urgent && isVulnOpen(v.status),
      dueLabel: due?.label,
    };
  });
  // ⚠ F2-205: the intro below asserted the SLA matrix as "Critical 7d / High 14d / Medium 30d /
  // Low 60d" — a FOURTH copy of a server policy, and wrong: `VulnerabilityService.SLA_DAYS` is
  // CRITICAL 7 / HIGH 30 / MEDIUM 60 / LOW 90, so it told an operator High was due in 14 days
  // when the server had stamped 30. Restating a server constant in prose cannot be gated, so it
  // is not restated: the deadline the server computed is on every row.
  return (
    <LifecyclePipeline
      stages={stages}
      cards={cards}
      introEn="Live vulnerability pipeline over the NCA ECC remediation register. The deadline on each card is the one the server stamped at triage from its ECC severity matrix. Click a card to drill into the detailed vulnerability tab."
      introAr="مسار الثغرات الحيّ على سجل معالجة الضوابط الأساسية. الموعد النهائي على كل بطاقة هو ما حدّده الخادم عند الفرز وفق مصفوفة الخطورة. انقر على بطاقة للانتقال إلى تبويب تفاصيل الثغرات."
      onAdvance={onAdvance}
      onCardClick={onCardClick}
      emptyEn="No vulnerabilities recorded yet."
      emptyAr="لا ثغرات مسجَّلة بعد."
    />
  );
}
