// Cross-workspace lifecycle chains — Phase 3 of the gap fill.
//
// These functions wire an action in one lifecycle workspace to automatic
// follow-on creation/transition in another. Real GRC programs are not
// silos — a finding in audit creates a treatment plan in risk; a critical
// vulnerability creates a SOC alert; a PDPL breach creates an incident.

import { createCapa, type Finding } from './auditLifecycle';
import { createSocAlert, type Vulnerability } from './cyberLifecycle';

/**
 * Audit finding → Risk treatment plan + CAPA.
 * Severity HIGH or CRITICAL spawns a treatment plan against the related risk
 * (we don't have a strong link in our model so we attach to risk id 1 by
 * default; in production this would walk the entity-anchor graph).
 */
export function chainFindingToRiskAndCapa(finding: Finding): { capaId: string } {
  // ⚠⚠ F2-204: the treatment-plan half is REMOVED, and wiring the store is exactly why.
  //
  // It called `createTreatmentPlan(1, …)` — a hard-coded risk id, with the comment
  // "production: derive from finding.controlCrosswalk → risk anchors" standing in for the
  // derivation. While the store was browser-local that produced a wrong linkage in
  // localStorage. Now that `createTreatmentPlan` POSTs, it would **persist** a treatment plan
  // against whatever risk happens to be id 1 in the tenant, attributed to an audit finding
  // that has nothing to do with it — so wiring the store makes this call worse, not better.
  //
  // Deriving the right risk from a finding's control crosswalk is a product decision, not
  // plumbing (F2-201's precedent for `chainBreachToDsr`). Filed. The CAPA half is a real
  // server write and stays.
  const capa = createCapa(
    finding.id, 'CORRECTIVE',
    `Address finding ${finding.refNumber}: ${finding.recommendationEn || finding.titleEn}`,
    `معالجة الملاحظة ${finding.refNumber}: ${finding.recommendationAr || finding.titleAr}`,
    finding.ownerRole ?? 'Owner',
  );
  return { capaId: capa.id };
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

// ⚠⚠ `chainSocEscalationToVuln` was here and F2-205 DELETED it. Recording why, on the same
// grounds F2-202 recorded the deletion of `chainBreachToDsr` a few lines below.
//
// Intent: when a SOC alert escalates to incident response and the detector points at a
// vulnerability, move the matching CRITICAL vuln on that asset into IN_REMEDIATION, because
// active exploitation has been observed. That is a real and desirable control.
//
// Two things were wrong with it, and the second is the reason it cannot simply be repointed.
//
//   1. It had NO CALLER anywhere in the frontend. Nothing escalated anything through it. So it
//      was never the working control its comment described — F2-187's population exactly, on the
//      client side.
//   2. Its own comment states the transition it performs as `IN_REMEDIATION` *"instead of
//      NEW/TRIAGED"* — that is, it jumps a row from NEW or TRIAGED straight to IN_REMEDIATION.
//      `VulnerabilityService.ALLOWED` reaches IN_REMEDIATION only from OWNER_ASSIGNED, so
//      against the real register this is a 409 for exactly the rows it was written for. While
//      the store was browser-local the jump silently succeeded and the register drifted into a
//      state the machine would never have permitted — F2-204's undo-over-a-machine shape, and
//      F2-190's decorative hop.
//
// Walking it legally means THREE attributed transitions (TRIAGED stamps `triaged_by` from the
// authenticated principal, OWNER_ASSIGNED records an owner), which a side effect firing off an
// alert must not perform on a person's behalf. So the honest form of this chain is server-side —
// F2-190's `hopVia`, capped and recorded as its own event — not a client loop. Filed.

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
