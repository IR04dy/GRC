// Cross-workspace lifecycle chains — Phase 3 of the gap fill.
//
// These functions wire an action in one lifecycle workspace to automatic
// follow-on creation/transition in another. Real GRC programs are not
// silos — a finding in audit creates a treatment plan in risk; a critical
// vulnerability creates a SOC alert; a PDPL breach creates an incident.

import { createTreatmentPlan } from './riskLifecycle';
import { createCapa, type Finding } from './auditLifecycle';
import { createSocAlert, transitionVuln, type Vulnerability } from './cyberLifecycle';

/**
 * Audit finding → Risk treatment plan + CAPA.
 * Severity HIGH or CRITICAL spawns a treatment plan against the related risk
 * (we don't have a strong link in our model so we attach to risk id 1 by
 * default; in production this would walk the entity-anchor graph).
 */
export function chainFindingToRiskAndCapa(finding: Finding): { planId: string; capaId: string } {
  const plan = createTreatmentPlan(
    1, // default risk id; production: derive from finding.controlCrosswalk → risk anchors
    'MITIGATE',
    `Audit finding ${finding.refNumber}: ${finding.titleEn}. Treatment plan auto-generated to address the root cause.`,
    `ملاحظة تدقيق ${finding.refNumber}: ${finding.titleAr}. خطة معالجة مُولَّدة تلقائياً لمعالجة السبب الجذري.`,
    finding.severity === 'CRITICAL' ? 500 : 200,
  );
  const capa = createCapa(
    finding.id, 'CORRECTIVE',
    `Address finding ${finding.refNumber}: ${finding.recommendationEn || finding.titleEn}`,
    `معالجة الملاحظة ${finding.refNumber}: ${finding.recommendationAr || finding.titleAr}`,
    finding.ownerRole ?? 'Owner',
  );
  return { planId: plan.id, capaId: capa.id };
}

/**
 * Critical vulnerability → SOC alert.
 * If a vuln is rated CRITICAL and external-facing it auto-creates a P2 SOC
 * alert so the SOC team picks up the situational awareness.
 */
export function chainVulnToSocAlert(vuln: Vulnerability, isExternal: boolean): string | null {
  if (vuln.severity !== 'CRITICAL') return null;
  const severity = isExternal ? 'P1' : 'P2';
  const alert = createSocAlert(
    severity, 'CTI-FEED',
    `Critical vuln ${vuln.cveId} on ${vuln.assetId}`,
    `ثغرة حرجة ${vuln.cveId} على ${vuln.assetId}`,
    `Auto-raised SOC alert from vulnerability detector. Vuln: ${vuln.titleEn}. Asset criticality + CVSS ${vuln.cvssScore}.`,
    `تنبيه SOC مُولَّد تلقائياً من كاشف الثغرات. الثغرة: ${vuln.titleAr}. حرجية الأصل + CVSS ${vuln.cvssScore}.`,
    vuln.assetId,
  );
  return alert.id;
}

/**
 * SOC alert → Vulnerability (when escalating to IR).
 * If a SOC alert escalates and the detector indicates it's vulnerability-
 * related, we mark any matching open vuln as IN_REMEDIATION (instead of
 * NEW/TRIAGED) since active exploitation has been observed.
 */
export function chainSocEscalationToVuln(socAlertAssetId: string | undefined, openVulns: Vulnerability[]): string | null {
  if (!socAlertAssetId) return null;
  const matching = openVulns.find(v => v.assetId === socAlertAssetId && v.status !== 'CLOSED' && v.severity === 'CRITICAL');
  if (!matching) return null;
  transitionVuln(matching.id, 'IN_REMEDIATION');
  return matching.id;
}

// ⚠⚠ `chainBreachToDsr` was here and F2-202 DELETED it. Recording why, because the
// capability it named is real and the gap is worth keeping visible.
//
// Intent: when a PDPL breach affecting personal data is confirmed, file an ACCESS request for
// each affected subject so they can ask what was exposed.
//
// What it actually did: minted `DSR-AUTO-<Date.now() in base36>` as the tracking reference,
// computed `Date.now() + 30 days` as the PDPL Article 4 statutory deadline, and wrote the case
// into localStorage via `saveDsr`. That is the third client-side minter of that deadline this
// slice found, after `createDsr` and the page's Extend handler, and F2-172 settled the rule:
// only the server may issue a tracking code or a statutory deadline.
//
// It had NO CALLERS — nothing in the product invoked it, so nothing regressed by removing it.
// Deleting rather than rewiring is deliberate: a breach-to-DSR chain needs the subjects of the
// breach, and this function took them as parameters from a caller that does not exist. Filing
// one case per named subject is a product decision about who is notified, not a plumbing job.
//
// If it is built, it files through `dsrApi.create()` in `lib/privacyLcApi.ts`, which sends no
// id and no deadline — `POST /api/privacy-lc/dsr-cases` stamps both. See the filed item in
// FINDINGS_REGISTER_V2.md under F2-202.

/**
 * BCM invocation → Crisis activation hand-off.
 * When a BCP invocation goes from DECLARED to INVOKED for a Tier-1/2 process,
 * we surface a flag that Crisis Management should be activated. This returns
 * a marker the calling page can use to navigate the user to /crisis-management.
 */
export function shouldActivateCrisis(tier: string, isReal: boolean): boolean {
  return isReal && (tier === 'TIER-1-NATIONAL' || tier === 'TIER-2-SECTORAL');
}
