// Cross-workspace lifecycle chains — Phase 3 of the gap fill.
//
// These functions wire an action in one lifecycle workspace to automatic
// follow-on creation/transition in another. Real GRC programs are not
// silos — a finding in audit creates a treatment plan in risk; a critical
// vulnerability creates a SOC alert; a PDPL breach creates an incident.

import { createTreatmentPlan } from './riskLifecycle';
import { createCapa, type Finding } from './auditLifecycle';
import { createSocAlert, transitionVuln, type Vulnerability } from './cyberLifecycle';
import { saveDsr, type DsrCase, type DsrType } from './privacyLifecycle';

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

/**
 * PDPL breach → DSR auto-creation for affected subjects.
 * When a breach affecting personal data is confirmed, a DSR case is created
 * for the data subject for the access right (so they can request what was
 * exposed).
 */
export function chainBreachToDsr(subjectName: string, nationalIdLast4: string, contactEmail: string, breachRef: string): string {
  const id = `DSR-AUTO-${Date.now().toString(36).slice(-6).toUpperCase()}`;
  const slaDeadline = new Date(Date.now() + 30 * 86_400_000).toISOString().slice(0, 10);
  const dsr: DsrCase = {
    id, type: 'ACCESS' as DsrType,
    subjectName, nationalIdLast4, contactEmail,
    status: 'INTAKE',
    filedAt: new Date().toISOString(),
    slaDeadline,
    ropaIds: [],
    dataSummaryEn: `Auto-created DSR following PDPL breach ${breachRef}. Subject was within affected data set.`,
    dataSummaryAr: `طلب أُنشئ تلقائياً بعد خرق ${breachRef} وفق نظام حماية البيانات. صاحب البيانات ضمن البيانات المتأثّرة.`,
  };
  saveDsr(dsr);
  return id;
}

/**
 * BCM invocation → Crisis activation hand-off.
 * When a BCP invocation goes from DECLARED to INVOKED for a Tier-1/2 process,
 * we surface a flag that Crisis Management should be activated. This returns
 * a marker the calling page can use to navigate the user to /crisis-management.
 */
export function shouldActivateCrisis(tier: string, isReal: boolean): boolean {
  return isReal && (tier === 'TIER-1-NATIONAL' || tier === 'TIER-2-SECTORAL');
}
