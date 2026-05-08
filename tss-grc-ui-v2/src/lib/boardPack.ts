// Executive board pack aggregator — Sprint 5.
//
// Consolidates live signal from every module we've built so far into a
// single structured payload the board pack page can render. The hard part
// of a board pack is getting the numbers right — once that's done a CFO,
// CEO, or audit-committee chair can cite them in a meeting next morning.
//
// Sources pulled:
//   • Saudi appetite breaches (riskAppetiteSaudi + appetiteBreach)
//   • Pending approvals at board level (approvals)
//   • Audit engagement milestones (auditEngagement)
//   • PDPL breach 72h posture (pdplBreach)
//   • CCM coverage (ccm)
//   • Asset blast-radius hot-spots (assets)
//   • Cross-module chain fires (workflowChains)
//   • Cross-module SLA pulse (sla)

import { SAUDI_APPETITES } from './riskAppetiteSaudi';
import { synthesiseAllBreaches, type AppetiteBreachWorkflow } from './appetiteBreach';
import { APPROVAL_REQUESTS, APPROVER_REGISTRY, type ApprovalRequest, type ApproverRole } from './approvals';
import { AUDIT_ENGAGEMENTS } from '../pages/compliance/mockCompliance';
import { buildMilestones } from './auditEngagement';
import { BREACHES } from '../pages/privacy/mockPrivacy';
import { computeBreachClock, classifySeverity } from './pdplBreach';
import { CCM_CHECKS, aggregateCoverage, classifyFreshness } from './ccm';
import { ASSETS, transitiveBlastRadius, type Asset } from './assets';
import { CHAIN_RULES, loadFires, type ChainFire } from './workflowChains';
import { ALL_WORKFLOWS } from './mockWorkflows';
import { deriveAllLive, formatHijri } from './sla';

// ─── Output shape ───────────────────────────────────────────────────────────

export interface BoardPackContent {
  /** Cover page metadata. */
  cover: {
    titleEn: string;
    titleAr: string;
    periodEn: string;
    periodAr: string;
    generatedAt: string;
    generatedAtHijri: string;
    confidentiality: 'BOARD_CONFIDENTIAL';
  };

  /** Executive summary — 6 headline KPIs. */
  executiveSummary: {
    activeWorkflows: number;
    overdueWorkflows: number;
    appetiteBreaches: number;
    boardApprovalsPending: number;
    pdpl72hWindowOpen: number;
    ccmCoveragePct: number;
  };

  /** Saudi appetite breaches needing board attention. */
  appetiteBreaches: AppetiteBreachWorkflow[];

  /** Approvals where the BOARD / Risk Committee / Audit Committee /
   *  Sharia Committee is one of the outstanding approvers. */
  boardApprovals: ApprovalRequest[];

  /** Open audit engagements with their current milestone status. */
  auditEngagements: Array<{
    code: string;
    titleEn: string;
    framework: string;
    status: string;
    auditorFirm?: string;
    leadAuditor?: string;
    currentMilestoneEn: string;
    currentMilestoneAr: string;
    currentMilestoneDate?: string;
    daysRemaining?: number;
  }>;

  /** PDPL posture summary. */
  pdpl: {
    open: number;
    in72hWindow: number;
    breached72h: number;
    sampleBreach?: {
      titleEn: string;
      titleAr: string;
      hoursRemaining: number;
      severity: string;
    };
  };

  /** CCM evidence freshness. */
  ccm: {
    totalChecks: number;
    fresh: number;
    stale: number;
    expired: number;
    coveragePct: number;
    controlsCovered: number;
  };

  /** Top 5 assets by transitive blast radius. Helps the board see
   *  "where would a single failure hurt us most". */
  topAssets: Array<{
    code: string;
    nameEn: string;
    nameAr: string;
    type: string;
    criticality: string;
    transitiveImpact: number;
    dataResidency: string;
    regulatorScope: string[];
  }>;

  /** Cross-module chain fires (last 7 days) — proof the orchestration is
   *  actually running. */
  recentChainFires: ChainFire[];

  /** Regulator coverage tally — count of active items per Saudi regulator. */
  regulators: Array<{ code: string; nameEn: string; nameAr: string; activeItems: number }>;
}

// ─── Builder ────────────────────────────────────────────────────────────────

const SAUDI_REGULATORS = [
  { code: 'SAMA',   nameEn: 'Saudi Central Bank',                nameAr: 'البنك المركزي السعودي' },
  { code: 'NCA',    nameEn: 'National Cybersecurity Authority',  nameAr: 'الهيئة الوطنية للأمن السيبراني' },
  { code: 'SDAIA',  nameEn: 'Saudi Data & AI Authority (PDPL)',  nameAr: 'هيئة البيانات والذكاء الاصطناعي (PDPL)' },
  { code: 'CMA',    nameEn: 'Capital Market Authority',          nameAr: 'هيئة السوق المالية' },
  { code: 'ZATCA',  nameEn: 'Zakat, Tax & Customs Authority',    nameAr: 'هيئة الزكاة والضريبة والجمارك' },
  { code: 'CITC',   nameEn: 'Communications & IT Commission',    nameAr: 'هيئة الاتصالات وتقنية المعلومات' },
  { code: 'NCEMA',  nameEn: 'National Centre for Emergency Mgmt', nameAr: 'المركز الوطني لإدارة الكوارث' },
  { code: 'HRSD',   nameEn: 'Human Resources & Social Devp.',    nameAr: 'الموارد البشرية والتنمية الاجتماعية' },
  { code: 'NAZAHA', nameEn: 'Anti-Corruption Commission',         nameAr: 'هيئة مكافحة الفساد' },
  { code: 'MOC',    nameEn: 'Ministry of Commerce',              nameAr: 'وزارة التجارة' },
];

const BOARD_APPROVER_ROLES: ApproverRole[] = ['BOARD', 'AUDIT_COMMITTEE', 'RISK_COMMITTEE', 'SHARIA_COMMITTEE'];

export function buildBoardPack(now: Date = new Date()): BoardPackContent {
  const live = deriveAllLive(ALL_WORKFLOWS, undefined, now);
  const overdue = live.filter(w =>
    w.sla.tier === 'OVERDUE' || w.sla.tier === 'MANAGER_ESC'
    || w.sla.tier === 'DIRECTOR_ESC' || w.sla.tier === 'EXEC_ESC').length;

  // Saudi appetite breaches.
  const breaches = synthesiseAllBreaches(SAUDI_APPETITES, now);

  // Board-level pending approvals — anyone in the chain who hasn't decided
  // and is in the BOARD_APPROVER_ROLES set.
  const boardApprovals = APPROVAL_REQUESTS.filter(r => {
    if (r.status !== 'PENDING') return false;
    return r.approverRoles.some(role =>
      BOARD_APPROVER_ROLES.includes(role)
      && !r.decisions.some(d => d.role === role && d.decision === 'APPROVED'),
    );
  });

  // Audit engagements summary — only the active ones (PLANNED / FIELDWORK
  // / REPORT_ISSUED). CLOSED don't go in the next-quarter board pack.
  const auditEngagements = AUDIT_ENGAGEMENTS
    .filter(e => e.status !== 'CLOSED')
    .map(e => {
      const ms = buildMilestones(e);
      const current = ms.find(m => m.isCurrent);
      const dueDate = current?.date;
      const daysRemaining = dueDate
        ? Math.round((new Date(dueDate).getTime() - now.getTime()) / 86_400_000)
        : undefined;
      return {
        code: e.engagementCode ?? String(e.id),
        titleEn: e.titleEn,
        framework: e.framework ?? '—',
        status: e.status,
        auditorFirm: e.auditorFirm,
        leadAuditor: e.leadAuditor,
        currentMilestoneEn: current?.labelEn ?? '—',
        currentMilestoneAr: current?.labelAr ?? '—',
        currentMilestoneDate: dueDate,
        daysRemaining,
      };
    });

  // PDPL posture.
  const openBreaches = BREACHES.filter(b => b.status !== 'CLOSED');
  const sampleBreach = openBreaches
    .filter(b => b.reportedAt)
    .map(b => {
      const clock = computeBreachClock(b.reportedAt ?? b.occurredAt ?? '', now);
      const sev = classifySeverity({ affectedRecords: b.affectedRecords, financial: (b.affectedRecords ?? 0) > 5_000 });
      return { breach: b, clock, sev };
    })
    .sort((a, b) => a.clock.hoursRemaining - b.clock.hoursRemaining)[0];

  const breachedPdpl = openBreaches.filter(b => {
    if (!b.reportedAt) return false;
    return computeBreachClock(b.reportedAt, now).hoursRemaining < 0;
  });
  const inWindowPdpl = openBreaches.filter(b => {
    if (!b.reportedAt) return false;
    const c = computeBreachClock(b.reportedAt, now);
    return c.hoursRemaining >= 0;
  });

  // CCM coverage.
  const coverage = aggregateCoverage(CCM_CHECKS, now);
  const coveragePct = coverage.totalChecks > 0
    ? Math.round((coverage.fresh / coverage.totalChecks) * 100)
    : 0;

  // Top 5 assets by transitive blast radius.
  const topAssets = ASSETS
    .map(a => ({ asset: a, blast: transitiveBlastRadius(a.code).size }))
    .sort((a, b) => b.blast - a.blast)
    .slice(0, 5)
    .map(({ asset, blast }) => ({
      code: asset.code,
      nameEn: asset.nameEn,
      nameAr: asset.nameAr,
      type: asset.type,
      criticality: asset.criticality,
      transitiveImpact: blast,
      dataResidency: asset.dataResidency,
      regulatorScope: asset.regulatorScope,
    }));

  // Recent chain fires (last 7 days).
  const recentChainFires = loadFires().filter(f =>
    now.getTime() - new Date(f.firedAt).getTime() < 7 * 86_400_000
  );

  // Regulator tally — for each SAUDI_REGULATORS entry, count items across:
  //  - approvals with that citation
  //  - appetite statements with that regulator
  //  - audit engagements with that framework
  //  - assets with that regulator scope
  const regulators = SAUDI_REGULATORS.map(r => {
    let count = 0;
    count += APPROVAL_REQUESTS.filter(a => (a.regulatorCitation ?? '').includes(r.code)).length;
    count += SAUDI_APPETITES.filter(a => a.regulators.includes(r.code as 'SAMA' | 'NCA' | 'SDAIA' | 'CMA' | 'ZATCA' | 'CITC' | 'NCEMA' | 'HRSD' | 'NAZAHA' | 'MOC')).length;
    count += AUDIT_ENGAGEMENTS.filter(e => (e.framework ?? '').includes(r.code === 'SDAIA' ? 'PDPL' : r.code)).length;
    count += ASSETS.filter(a => (a.regulatorScope as readonly string[]).includes(r.code === 'SDAIA' ? 'PDPL' : r.code)).length;
    return { ...r, activeItems: count };
  }).sort((a, b) => b.activeItems - a.activeItems);

  return {
    cover: {
      titleEn: 'Board Pack — GRC Posture',
      titleAr: 'ملف اجتماع مجلس الإدارة — وضع الحوكمة والمخاطر والامتثال',
      periodEn: quarterLabel(now, 'en'),
      periodAr: quarterLabel(now, 'ar'),
      generatedAt: now.toISOString().slice(0, 10),
      generatedAtHijri: formatHijri(now, 'en'),
      confidentiality: 'BOARD_CONFIDENTIAL',
    },
    executiveSummary: {
      activeWorkflows: live.length,
      overdueWorkflows: overdue,
      appetiteBreaches: breaches.length,
      boardApprovalsPending: boardApprovals.length,
      pdpl72hWindowOpen: inWindowPdpl.length,
      ccmCoveragePct: coveragePct,
    },
    appetiteBreaches: breaches,
    boardApprovals,
    auditEngagements,
    pdpl: {
      open: openBreaches.length,
      in72hWindow: inWindowPdpl.length,
      breached72h: breachedPdpl.length,
      sampleBreach: sampleBreach ? {
        titleEn: sampleBreach.breach.titleEn,
        titleAr: sampleBreach.breach.titleEn, // titleAr not present on PrivacyBreachDto in mock
        hoursRemaining: sampleBreach.clock.hoursRemaining,
        severity: sampleBreach.sev,
      } : undefined,
    },
    ccm: {
      totalChecks: coverage.totalChecks,
      fresh: coverage.fresh,
      stale: coverage.stale,
      expired: coverage.expired,
      coveragePct,
      controlsCovered: coverage.controlsCovered,
    },
    topAssets,
    recentChainFires,
    regulators,
  };
}

// Helper that doesn't really compute fiscal quarter but renders a sensible
// "period" label for the cover page.
function quarterLabel(d: Date, lang: 'en' | 'ar'): string {
  const q = Math.floor(d.getMonth() / 3) + 1;
  const y = d.getFullYear();
  if (lang === 'ar') return `الربع ${q} لعام ${y}`;
  return `Q${q} ${y}`;
}

// ─── Stats also useful inline ───────────────────────────────────────────────
//
// Convenience: re-export classifyFreshness so the board-pack page can use
// the same freshness call as the CCM dashboard does.
export { classifyFreshness };

export const CHAIN_RULES_COUNT = CHAIN_RULES.length;
