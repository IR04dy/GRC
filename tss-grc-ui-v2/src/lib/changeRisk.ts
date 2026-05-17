// Change-management risk scoring — DD-70.
//
// Translates the CHANGE_REQUEST intake payload into a 0-100 risk score and
// a CAB approval gate. The model mirrors how ITIL v4 + ISO 27001 §A.8.32 +
// NCA ECC-1 §2-10 + SAMA CSF §3.3.13 want change risk classified:
//
//   risk = f( change_type, impact, likelihood-of-failure, recoverability )
//
//   impact          → affected-system tier × users-impacted × data-privacy-impact
//   likelihood      → tested-in-lower-env × change-window × security-review state
//   recoverability  → rollback-complexity
//
// Scoring is intentionally simple and inspectable — auditors should be able
// to reproduce it on paper. Each input contributes a small weighted score,
// which is summed and capped at 100. The classification thresholds match
// the industry pattern's default Change Risk Calculator buckets (LOW < 25, MED 25-49,
// HIGH 50-74, CRITICAL ≥ 75).
//
// References:
//   ITIL v4               — Change Enablement, risk-assessment matrix
//   ISO 27001 §A.8.32     — Change management — documented risk assessment
//   ISO 27001 §A.8.31     — Separation of dev/test/prod environments
//   NCA ECC-1 §2-10       — Change management cybersecurity requirements
//   SAMA CSF §3.3.13      — Change management
//   PDPL Art. 25          — DPIA when change affects personal-data processing

export type ChangeRiskClass = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type ChangeCabGate   = 'AUTO_APPROVED' | 'CAB_REQUIRED' | 'ECAB_REQUIRED';

export interface ChangeRiskBreakdown {
  /** 0-100. Higher = riskier. */
  score: number;
  classification: ChangeRiskClass;
  cabGate: ChangeCabGate;
  /** Per-factor contribution (0-100, summed and capped). For audit trail. */
  factors: Array<{ key: string; weight: number; reason: string }>;
  /** Human-readable triggers that escalate the gate (DPIA needed, etc.). */
  warnings: string[];
}

/** Factor weights chosen so any single critical factor cannot push to
 *  CRITICAL on its own — but two or more compounding risks will. */
const W = {
  changeType: { STANDARD: 0, NORMAL: 10, EMERGENCY: 25 },
  systemTier: { TIER_4: 0, TIER_3: 8,  TIER_2: 18, TIER_1: 28 },
  users:      { LT_10: 0,  LT_100: 5,  LT_1000: 12, GT_1000: 20, EXTERNAL: 25 },
  rollback:   { EASY: 0,   MODERATE: 6, DIFFICULT: 14, IRREVERSIBLE: 22 },
  window:     { OFF_HOURS: 0, WEEKEND: 2, BUSINESS_HOURS: 10, MONTH_END_FREEZE: 18 },
  tested:     { FULLY_TESTED: 0, NOT_APPLICABLE: 0, PARTIALLY_TESTED: 8, NOT_TESTED: 20 },
  security:   { NOT_SECURITY_RELEVANT: 0, SECURITY_REVIEWED: 0, SECURITY_PENDING: 12, SECURITY_BYPASSED: 22 },
  privacy:    { NONE: 0, INDIRECT: 4, DIRECT: 12, CROSS_BORDER: 18 },
  severity:   { LOW: 0, MEDIUM: 5, HIGH: 12, CRITICAL: 18 },
} as const;

function lookup<T extends Record<string, number>>(map: T, value: unknown): number {
  return value && (value as string) in map ? map[value as keyof T] : 0;
}

export function computeChangeRisk(payload: Record<string, unknown>): ChangeRiskBreakdown {
  const factors: ChangeRiskBreakdown['factors'] = [];
  const warnings: string[] = [];

  function add(key: string, weight: number, reason: string) {
    if (weight > 0) factors.push({ key, weight, reason });
  }

  add('changeType', lookup(W.changeType, payload.changeType),
      `Change type: ${payload.changeType ?? '—'}`);
  add('systemTier', lookup(W.systemTier, payload.affectedSystemsCriticality),
      `Affected system criticality: ${payload.affectedSystemsCriticality ?? '—'}`);
  add('users', lookup(W.users, payload.usersImpacted),
      `Users impacted: ${payload.usersImpacted ?? '—'}`);
  add('rollback', lookup(W.rollback, payload.rollbackComplexity),
      `Rollback: ${payload.rollbackComplexity ?? '—'}`);
  add('window', lookup(W.window, payload.changeWindow),
      `Implementation window: ${payload.changeWindow ?? '—'}`);
  add('tested', lookup(W.tested, payload.testedInLowerEnv),
      `Pre-prod testing: ${payload.testedInLowerEnv ?? '—'}`);
  add('security', lookup(W.security, payload.securityImpactReview),
      `Security review: ${payload.securityImpactReview ?? '—'}`);
  add('privacy', lookup(W.privacy, payload.dataPrivacyImpact),
      `Privacy impact: ${payload.dataPrivacyImpact ?? '—'}`);
  add('severity', lookup(W.severity, payload.severitySelfAssessed),
      `Self-assessed impact: ${payload.severitySelfAssessed ?? '—'}`);

  const rawScore = factors.reduce((s, f) => s + f.weight, 0);
  const score = Math.min(rawScore, 100);

  // ── Classification thresholds (the industry pattern-aligned)
  let classification: ChangeRiskClass;
  if      (score < 25)  classification = 'LOW';
  else if (score < 50)  classification = 'MEDIUM';
  else if (score < 75)  classification = 'HIGH';
  else                  classification = 'CRITICAL';

  // ── CAB gate
  // Standard pre-approved + low score → auto-approved (zero-touch).
  // Emergency → always E-CAB (whether or not the score is low — that's
  // the whole point of the emergency lane).
  // Otherwise: classification drives the CAB level.
  let cabGate: ChangeCabGate;
  if (payload.changeType === 'EMERGENCY') {
    cabGate = 'ECAB_REQUIRED';
  } else if (payload.changeType === 'STANDARD' && classification === 'LOW') {
    cabGate = 'AUTO_APPROVED';
  } else if (classification === 'HIGH' || classification === 'CRITICAL') {
    cabGate = 'ECAB_REQUIRED';
  } else {
    cabGate = 'CAB_REQUIRED';
  }

  // ── Hard-stop warnings
  if (payload.testedInLowerEnv === 'NOT_TESTED' && payload.changeType !== 'EMERGENCY') {
    warnings.push('NOT_TESTED');
  }
  if (payload.securityImpactReview === 'SECURITY_BYPASSED') {
    warnings.push('SECURITY_BYPASSED');
  }
  if (payload.dataPrivacyImpact === 'DIRECT' || payload.dataPrivacyImpact === 'CROSS_BORDER') {
    warnings.push('DPIA_REQUIRED');
  }
  if (payload.changeWindow === 'MONTH_END_FREEZE' && payload.changeType !== 'EMERGENCY') {
    warnings.push('FREEZE_VIOLATION');
  }
  if (payload.rollbackComplexity === 'IRREVERSIBLE') {
    warnings.push('IRREVERSIBLE');
  }

  return { score, classification, cabGate, factors, warnings };
}

export function changeRiskTone(c: ChangeRiskClass): 'success' | 'info' | 'warning' | 'danger' {
  return c === 'LOW' ? 'success' : c === 'MEDIUM' ? 'info' : c === 'HIGH' ? 'warning' : 'danger';
}
