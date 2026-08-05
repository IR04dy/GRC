import { describe, it, expect } from 'vitest';
import { en } from '../translations/en';
import { ar } from '../translations/ar';
import {
  isAuthorityGap, REASON_TONE, DISPOSITION_STATES,
} from '../pages/change/UnauthorisedChangeRegister';
import type { UnauthorisedFindingDto } from '../lib/api';

/**
 * S8f / CE-11 — the frontend must not turn a bookkeeping gap into a compliance breach.
 *
 * The server distinguishes an authority gap (the approvals fall short of what the cell demanded) from
 * an unattributed authorisation (the approvals are there; only `cab_decision_at` is missing). Six of
 * dev's eleven findings are the second kind. If the UI paints them the same red as the first, the
 * screen reports eight breaches where three exist — and the screen is what a reader believes.
 */
function finding(over: Partial<UnauthorisedFindingDto> = {}): UnauthorisedFindingDto {
  return {
    changeId: 3, changeCode: 'CHG-2025-0003',
    titleEn: 'Emergency patch', titleAr: 'ترقيع طارئ',
    changeType: 'EMERGENCY', riskLevel: 'CRITICAL', status: 'IMPLEMENTED',
    reasonCode: 'INSUFFICIENT_APPROVALS', severity: 'HIGH', effective: true,
    gateRequired: 'ECAB', minApprovalsRequired: 2, approvalsRecorded: 1,
    cabDecisionStamped: false, ratificationState: 'GRANDFATHERED',
    ...over,
  };
}

const ALL_REASONS: UnauthorisedFindingDto['reasonCode'][] = [
  'NO_AUTHORISATION_RECORD', 'INSUFFICIENT_APPROVALS', 'UNATTRIBUTED_AUTHORISATION',
  'NO_AUTHORISATION_POLICY', 'EXPIRED_MODEL_APPLIED', 'RATIFICATION_BREACHED',
];

describe('CE-11 — which findings are unauthorised', () => {
  it('an unattributed-but-approved change is NOT an authority gap', () => {
    expect(isAuthorityGap(finding({ reasonCode: 'UNATTRIBUTED_AUTHORISATION' }))).toBe(false);
  });

  it('every other reason is', () => {
    for (const reasonCode of ALL_REASONS) {
      expect(isAuthorityGap(finding({ reasonCode })))
        .toBe(reasonCode !== 'UNATTRIBUTED_AUTHORISATION');
    }
  });

  it('the unattributed finding is never coloured like a breach', () => {
    // The visual half of the same rule. `danger` on this row is the misreport.
    expect(REASON_TONE.UNATTRIBUTED_AUTHORISATION).toBe('info');
    expect(REASON_TONE.NO_AUTHORISATION_RECORD).toBe('danger');
    expect(REASON_TONE.INSUFFICIENT_APPROVALS).toBe('danger');
  });

  it('every reason code has a tone — an unstyled row renders as neutral and reads as fine', () => {
    for (const reasonCode of ALL_REASONS) expect(REASON_TONE[reasonCode]).toBeDefined();
  });
});

describe('CE-11 — the disposition vocabulary', () => {
  it('has no state that closes a finding', () => {
    // Deliberate: "signed off" and "never happened" are different facts. Mirrors
    // UnauthorisedChangeDisposition.STATES, which V355's ck_ucd_state pins in the schema.
    expect(DISPOSITION_STATES).toEqual([
      'ACCEPTED_RISK', 'RETROSPECTIVELY_AUTHORISED', 'REVERSED', 'FALSE_POSITIVE',
    ]);
    expect(DISPOSITION_STATES as readonly string[]).not.toContain('CLOSED');
    expect(DISPOSITION_STATES as readonly string[]).not.toContain('RESOLVED');
  });
});

describe('CE-11 — house rule 7: every code renders in both languages', () => {
  const keys = [
    ...ALL_REASONS.map(r => `unauthReason_${r}`),
    ...DISPOSITION_STATES.map(s => `unauthState_${s}`),
    'unauthBlind_UNTICKETED_PRODUCTION_CHANGE',
    'unauthBlind_IMPLEMENTED_DURING_FREEZE',
    'unauthTitle', 'unauthSub', 'unauthGaps', 'unauthUnattributed', 'unauthGapsHint',
    'unauthNoneTitle', 'unauthNoneHint', 'unauthNotInvestigated', 'unauthBlindTitle',
    'unauthLiveInProduction', 'unauthAutoOnly',
  ];

  it('English and Arabic both carry every key', () => {
    // A missing key renders as the raw code — so a finding reason would read
    // "unauthReason_INSUFFICIENT_APPROVALS" to an Arabic-speaking CAB chair.
    const e = en as unknown as Record<string, string>;
    const a = ar as unknown as Record<string, string>;
    for (const k of keys) {
      expect(e[k], `en.${k}`).toBeTruthy();
      expect(a[k], `ar.${k}`).toBeTruthy();
    }
  });

  it('the Arabic is a translation, not the English string copied across', () => {
    const e = en as unknown as Record<string, string>;
    const a = ar as unknown as Record<string, string>;
    for (const k of keys) expect(a[k], `ar.${k} is identical to en.${k}`).not.toBe(e[k]);
  });

  it('the blind-spot copy states WHY each class is undetectable', () => {
    // A blind spot listed without its reason reads as a to-do rather than as a measured limit, and
    // this is the card's whole honesty claim.
    const e = en as unknown as Record<string, string>;
    expect(e.unauthBlind_UNTICKETED_PRODUCTION_CHANGE).toMatch(/drift/i);
    expect(e.unauthBlind_IMPLEMENTED_DURING_FREEZE).toMatch(/not recorded|implementation start/i);
  });

  it('the headline hint says the gap count survives sign-off', () => {
    // The one sentence that stops a reader assuming the number is a work queue they can drain.
    expect((en as unknown as Record<string, string>).unauthGapsHint)
      .toMatch(/cannot be cleared/i);
  });
});
