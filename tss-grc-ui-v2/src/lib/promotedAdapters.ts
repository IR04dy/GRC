// Promoted-record DTO adapters — DD-71.
//
// DD-69 added the `promotedRecords` overlay (a localStorage cache of
// records created via intake promotion). DD-71 wires that overlay into
// the visible registers so that when a triager promotes an intake row
// to "Incident #14", the new incident actually appears on /incidents.
//
// Each register page's DTO has its own shape, so we need a small per-target
// mapping function that takes a generic PromotedRecord (intake payload +
// audit metadata) and produces the right DTO shape with sensible defaults.
// Shared utilities live here so the register pages stay clean.
//
// Pattern (per register page):
//   const apiRows = useApiList<XxxDto>(...);
//   const promoted = usePromoted('XXX').map(toXxxDto);
//   const allRows  = [...promoted, ...apiRows.data];   // promoted at top
//
// Promoted records use *negative* synthesized ids (or a clearly distinct
// scheme) so they never collide with backend-assigned ids.
//
// ⚠ F2-171 — that sentence was true as an intention and false as a description for
// as long as it had been written. `intake.ts` minted `Date.now() % 1_000_000`, a
// POSITIVE id, and every adapter below passed it straight through with
// `typeof p.id === 'number' ? p.id : Number(p.id)` — so the file that documented the
// hazard was the file that let it through, the same way `entityAnchors.ts` opened with
// the mock-id range in F2-168. Exactly one of the thirteen adapters actually honoured
// the contract: `toCapaFromObservation`, which mints a string. Now the rule is enforced
// rather than described — every numeric adapter goes through `promotedId()` below, and
// `promotedRecords.mintPromotedId()` is the only producer.

import type {
  IncidentDto,
  PrivacyDsrDto,
  PrivacyBreachDto,
  RiskResponse,
  TprmVendorDto,
  EthicsCaseDto,
  ChangeRequestDto,
  DpiaDto,
  BcmBiaDto,
  AuditEngagementDto,
  ComplianceFindingDto,
  CyberExceptionDto,
} from './api';
import type { Capa } from './capa';
import { usePromoted, type PromotedRecord } from './promotedRecords';
import type { RegisterTarget } from './intake';
import { useMemo } from 'react';

/** Reads the submitter cached on the payload (set by triage()) — falls
 *  back to a placeholder when the intake was anonymous. */
function submitterOf(p: PromotedRecord): string {
  return (p.payload._submitter as string | undefined) ?? 'anonymous';
}

function pickStr(p: PromotedRecord, key: string, fallback = ''): string {
  const v = p.payload[key];
  return typeof v === 'string' ? v : fallback;
}

/**
 * The single numeric-id path for every adapter — F2-171.
 *
 * Coerces rather than trusts, for one concrete reason: overlays minted before this fix
 * are already sitting in users' `localStorage` with positive ids, and there is no
 * migration step for browser storage. `-Math.abs(n)` repairs those on read, so the
 * invariant "a promoted row's id is negative" holds for old and new records alike
 * without anyone having to clear site data.
 *
 * A non-numeric id (the CAPA scheme) never reaches here — those adapters build their
 * own string id and are not routed through this helper.
 */
function promotedId(p: PromotedRecord): number {
  const n = typeof p.id === 'number' ? p.id : Number(p.id);
  if (!Number.isFinite(n) || n === 0) return -1;
  return -Math.abs(n);
}

function severityOf(p: PromotedRecord): 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL' {
  const s = p.payload.severitySelfAssessed;
  return s === 'LOW' || s === 'MEDIUM' || s === 'HIGH' || s === 'CRITICAL' ? s : 'MEDIUM';
}

// ── Per-register adapters ───────────────────────────────────────────────

/** SECURITY_INCIDENT / VRP_DISCLOSURE → IncidentDto */
export function toIncidentDto(p: PromotedRecord): IncidentDto {
  const summary = pickStr(p, 'summary');
  const category = pickStr(p, 'category', 'CYBER');
  return {
    id: promotedId(p),
    title: summary.slice(0, 80) || `Promoted from ${p.sourceReferenceCode}`,
    severity: severityOf(p),
    category: category.includes('PRIVACY') ? 'PRIVACY' : 'CYBER',
    status: 'OPEN',
    assignee: submitterOf(p),
    discoveryDate: pickStr(p, 'observedAt', p.promotedAt).slice(0, 10),
    description: summary || pickStr(p, 'reproductionSteps') || `Promoted from intake ${p.sourceReferenceCode}`,
  };
}

/** DSR_REQUEST → PrivacyDsrDto */
export function toDsrDto(p: PromotedRecord): PrivacyDsrDto {
  return {
    id: promotedId(p),
    requestType: pickStr(p, 'requestType', 'ACCESS'),
    requesterName: pickStr(p, '_submitter') || 'Anonymous',
    requesterEmail: pickStr(p, 'requesterEmail'),
    receivedAt: p.promotedAt,
    status: 'NEW',
  };
}

/** BREACH_REPORT / PRIVACY_BREACH → PrivacyBreachDto */
export function toBreachDto(p: PromotedRecord): PrivacyBreachDto {
  const summary = pickStr(p, 'summary') || pickStr(p, 'description');
  return {
    id: promotedId(p),
    titleEn: summary.slice(0, 80) || `Promoted from ${p.sourceReferenceCode}`,
    descriptionEn: summary,
    breachType: pickStr(p, 'category', 'CONFIDENTIALITY'),
    severity: severityOf(p),
    status: 'NEW',
    occurredAt: pickStr(p, 'observedAt', p.promotedAt),
    reportedAt: p.promotedAt,
  };
}

/** RISK_ISSUE → RiskResponse */
export function toRiskResponse(p: PromotedRecord): RiskResponse {
  const summary = pickStr(p, 'summary') || pickStr(p, 'description');
  // Map self-assessed severity to a 1-5 inherent score for display.
  const sev = severityOf(p);
  const score = sev === 'LOW' ? 4 : sev === 'MEDIUM' ? 9 : sev === 'HIGH' ? 16 : 20;
  const lik = sev === 'LOW' ? 2 : sev === 'MEDIUM' ? 3 : 4;
  const imp = sev === 'LOW' ? 2 : sev === 'MEDIUM' ? 3 : sev === 'HIGH' ? 4 : 5;
  return {
    id: promotedId(p),
    riskCode: `R-${p.sourceReferenceCode}`,
    titleEn: summary.slice(0, 80) || `Promoted from ${p.sourceReferenceCode}`,
    descriptionEn: summary,
    categoryName: pickStr(p, 'category', 'OPERATIONAL'),
    ownerName: submitterOf(p),
    status: 'IDENTIFIED',
    inherentLikelihood: lik,
    inherentImpact: imp,
    inherentScore: score,
    riskLevel: sev,
    identifiedDate: p.promotedAt.slice(0, 10),
  };
}

/** VENDOR_ONBOARDING → TprmVendorDto */
export function toVendorDto(p: PromotedRecord): TprmVendorDto {
  const name = pickStr(p, 'vendorName') || pickStr(p, 'name') || `Vendor from ${p.sourceReferenceCode}`;
  return {
    id: promotedId(p),
    vendorName: name,
    vendorNameAr: pickStr(p, 'vendorNameAr'),
    vendorType: pickStr(p, 'vendorType', 'SERVICE'),
    country: pickStr(p, 'country', 'SA'),
    riskRating: severityOf(p),
    status: 'ONBOARDING',
    description: pickStr(p, 'description') || pickStr(p, 'serviceScope'),
  };
}

/** SPEAKUP / ETHICS_DISCLOSURE → EthicsCaseDto */
export function toEthicsCaseDto(p: PromotedRecord): EthicsCaseDto {
  const summary = pickStr(p, 'summary') || pickStr(p, 'description');
  return {
    id: promotedId(p),
    caseCode: `EC-${p.sourceReferenceCode}`,
    titleEn: summary.slice(0, 80) || `Promoted from ${p.sourceReferenceCode}`,
    category: pickStr(p, 'category') || pickStr(p, 'disclosureType', 'OTHER'),
    severity: severityOf(p),
    intakeChannel: 'WEB_FORM',
    isAnonymous: !pickStr(p, '_submitter') || pickStr(p, '_submitter') === 'anonymous',
    reporterName: submitterOf(p) === 'anonymous' ? undefined : submitterOf(p),
    description: summary,
    status: 'INTAKE',
    triageDecision: 'PENDING',
    createdAt: p.promotedAt,
  };
}

/** Provenance lookup hook — returns a Map<numeric-record-id, source intake
 *  reference-code> for the given target. Register pages use this to render
 *  the "↩ from SI-XXXX" badge on rows that originated from intake promotion,
 *  preserving the audit trail visually. */
export function usePromotedProvenance(target: RegisterTarget): Map<number, string> {
  const records = usePromoted(target);
  return useMemo(() => {
    const m = new Map<number, string>();
    for (const r of records) {
      // F2-171 — keyed with the SAME coercion the adapters apply, or the badge silently
      // stops resolving: the row would carry -483 and the map +483. F2-168's lesson,
      // which was that keys and record ids have to move together or the lookup misses.
      m.set(promotedId(r), r.sourceReferenceCode);
    }
    return m;
  }, [records]);
}

/** DPIA_INITIATION → DpiaDto */
export function toDpiaDto(p: PromotedRecord): DpiaDto {
  const summary = pickStr(p, 'description') || pickStr(p, 'projectName');
  return {
    id: promotedId(p),
    titleEn: pickStr(p, 'projectName') || summary.slice(0, 80) || `Promoted from ${p.sourceReferenceCode}`,
    triggerReason: pickStr(p, 'trigger', 'NEW_PROCESSING_ACTIVITY'),
    status: 'DRAFT',
    overallResidualRisk: severityOf(p),
    assessmentDate: p.promotedAt.slice(0, 10),
  };
}

/** BIA_SUBMISSION → BcmBiaDto */
export function toBiaDto(p: PromotedRecord): BcmBiaDto {
  const rto = Number(pickStr(p, 'rtoHours'));
  const rpo = Number(pickStr(p, 'rpoHours'));
  return {
    id: promotedId(p),
    assessmentDate: p.promotedAt.slice(0, 10),
    rtoHours: Number.isFinite(rto) ? rto : undefined,
    rpoHours: Number.isFinite(rpo) ? rpo : undefined,
    overallImpact: severityOf(p),
    status: 'DRAFT',
  };
}

/** AUDIT_REQUEST → AuditEngagementDto */
export function toEngagementDto(p: PromotedRecord): AuditEngagementDto {
  const summary = pickStr(p, 'scope') || pickStr(p, 'description');
  return {
    id: promotedId(p),
    engagementCode: `ENG-${p.sourceReferenceCode}`,
    titleEn: pickStr(p, 'titleEn') || summary.slice(0, 80) || `Promoted from ${p.sourceReferenceCode}`,
    auditType: pickStr(p, 'auditType', 'INTERNAL'),
    leadAuditor: submitterOf(p),
    scopeSummary: summary,
    status: 'PLANNING',
    createdAt: p.promotedAt,
  };
}

/** CONTROL_OBSERVATION → ComplianceFindingDto */
export function toFindingDto(p: PromotedRecord): ComplianceFindingDto {
  const summary = pickStr(p, 'observation') || pickStr(p, 'summary');
  return {
    id: promotedId(p),
    title: summary.slice(0, 80) || `Promoted from ${p.sourceReferenceCode}`,
    severity: severityOf(p),
    owner: submitterOf(p),
    status: 'OPEN',
    capaStage: 'IDENTIFIED',
  };
}

/** POLICY_EXCEPTION → CyberExceptionDto (used by /compliance exceptions view) */
export function toExceptionDto(p: PromotedRecord): CyberExceptionDto {
  return {
    id: promotedId(p),
    titleEn: pickStr(p, 'policyTitle') || pickStr(p, 'policyCode') || `Promoted from ${p.sourceReferenceCode}`,
    exceptionReason: pickStr(p, 'businessJustification'),
    workflowStatus: 'PENDING_REVIEW',
    ownerName: submitterOf(p),
    compensatingControls: pickStr(p, 'compensatingControls'),
    expiryDate: pickStr(p, 'sunsetDate'),
  };
}

/** Synthesise a Capa from a promoted control-observation. CapaSource only
 *  accepts a fixed enum so we map to FINDING (the most common upstream). */
export function toCapaFromObservation(p: PromotedRecord): Capa {
  const summary = pickStr(p, 'observation') || pickStr(p, 'summary');
  return {
    id: `capa-${p.sourceIntakeId}`,
    code: `CAPA-${p.sourceReferenceCode}`,
    titleEn: summary.slice(0, 80) || `Promoted from ${p.sourceReferenceCode}`,
    descriptionEn: summary,
    type: 'CORRECTIVE',
    source: 'AUDIT_FINDING',
    sourceRecordKey: `INTAKE:${p.sourceIntakeId}`,
    sourceRecordTitle: p.sourceReferenceCode,
    severity: severityOf(p),
    owner: submitterOf(p),
    status: 'IDENTIFIED',
    openedAt: p.promotedAt,
    dueDate: new Date(new Date(p.promotedAt).getTime() + 60 * 86_400_000).toISOString().slice(0, 10),
    progressPct: 0,
    rcaMethod: 'NONE',
    rcaSteps: [],
    history: [],
  };
}

/** CHANGE_REQUEST → ChangeRequestDto */
export function toChangeRequestDto(p: PromotedRecord): ChangeRequestDto {
  return {
    id: promotedId(p),
    changeCode: `CR-${p.sourceReferenceCode}`,
    titleEn: pickStr(p, 'titleEn') || pickStr(p, 'description', '').slice(0, 80) || `Promoted from ${p.sourceReferenceCode}`,
    titleAr: pickStr(p, 'titleAr'),
    descriptionEn: pickStr(p, 'description'),
    changeType: pickStr(p, 'changeType', 'NORMAL'),
    riskLevel: severityOf(p),
    rollbackPlan: pickStr(p, 'rollbackPlan'),
    requestedByName: submitterOf(p),
    requestedDate: p.promotedAt.slice(0, 10),
    scheduledStart: pickStr(p, 'proposedScheduleAt'),
    status: 'SUBMITTED',
    createdAt: p.promotedAt,
  };
}
