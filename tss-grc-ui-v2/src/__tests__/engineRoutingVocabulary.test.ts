/**
 * S8h / CE-10 — the change module's workflow-engine vocabulary, pinned against the server's.
 *
 * These are the strings the panel renders and the ones it must never offer. Two of them carry the
 * whole slice: `APPROVAL` must stay out of the request-type list (a button whose only outcome is a
 * 400, and worse, one that implies the engine can authorise a change), and every reason code the
 * server can return must have a label — an unlabelled reason renders as the raw enum, which is how a
 * governance screen starts telling an auditor `DEPARTMENT_HAS_NO_MEMBERS`.
 */
import { describe, it, expect } from 'vitest';
import {
  ENGINE_REQUEST_TYPES, REFUSED_REQUEST_TYPE, REASON_TONE, isNameDefect, addresseeKey,
} from '../pages/change/EngineRoutingPanel';
import { en } from '../translations/en';
import { ar } from '../translations/ar';

/** The port's WorkflowPort.REASONS, plus the service's two extra verdicts. */
const SERVER_REASONS = [
  'ADDRESSEE_TYPE_UNKNOWN',
  'USER_NOT_FOUND',
  'ROLE_NOT_FOUND',
  'ROLE_HELD_BY_NOBODY',
  'DEPARTMENT_NOT_FOUND',
  'DEPARTMENT_HAS_NO_MEMBERS',
  'COMMITTEE_NOT_FOUND',
  'COMMITTEE_HAS_NO_LINKED_USERS',
  'LABEL_MATCHES_NOTHING',
  'ENGINE_UNAVAILABLE',
];

describe('CE-10 engine request types', () => {
  it('never offers APPROVAL', () => {
    // The server refuses it outright: change authorisation is recorded CAB votes or a carried
    // committee resolution, and an approval-shaped engine request would contradict the
    // unauthorised-change register while conferring nothing.
    expect(REFUSED_REQUEST_TYPE).toBe('APPROVAL');
    expect(ENGINE_REQUEST_TYPES as readonly string[]).not.toContain('APPROVAL');
    expect(ENGINE_REQUEST_TYPES as readonly string[]).not.toContain(REFUSED_REQUEST_TYPE);
  });

  it('matches the port RAISABLE_TYPES exactly, in order', () => {
    expect([...ENGINE_REQUEST_TYPES]).toEqual([
      'TASK', 'REVIEW', 'EVIDENCE_REQUEST', 'INFORMATION',
      'FOLLOW_UP', 'ACKNOWLEDGEMENT', 'REPORT_DISTRIBUTION',
    ]);
  });

  it('labels every offered type in both languages', () => {
    for (const t of ENGINE_REQUEST_TYPES) {
      expect(en[`ceRequestType_${t}` as keyof typeof en], `en ${t}`).toBeTruthy();
      expect(ar[`ceRequestType_${t}` as keyof typeof ar], `ar ${t}`).toBeTruthy();
    }
  });

  it('does not label the refused type — an unused label invites its reinstatement', () => {
    expect(en[`ceRequestType_${REFUSED_REQUEST_TYPE}` as keyof typeof en]).toBeUndefined();
    expect(ar[`ceRequestType_${REFUSED_REQUEST_TYPE}` as keyof typeof ar]).toBeUndefined();
  });
});

describe('CE-10 reason vocabulary', () => {
  it('labels every reason the server can return, in both languages', () => {
    for (const r of SERVER_REASONS) {
      expect(en[`ceReason_${r}` as keyof typeof en], `en ${r}`).toBeTruthy();
      expect(ar[`ceReason_${r}` as keyof typeof ar], `ar ${r}`).toBeTruthy();
    }
  });

  it('tones every reason', () => {
    for (const r of SERVER_REASONS) expect(REASON_TONE[r], r).toBeTruthy();
  });

  it('separates a wrong name from an empty target', () => {
    // The distinction is the operator's next action: a wrong name is a data fix, an empty target is
    // an appointment. One colour for both would lose that.
    expect(REASON_TONE.ROLE_NOT_FOUND).toBe('danger');
    expect(REASON_TONE.LABEL_MATCHES_NOTHING).toBe('danger');
    expect(REASON_TONE.ROLE_HELD_BY_NOBODY).toBe('warning');
    expect(REASON_TONE.DEPARTMENT_HAS_NO_MEMBERS).toBe('warning');

    expect(isNameDefect('ROLE_NOT_FOUND')).toBe(true);
    expect(isNameDefect('LABEL_MATCHES_NOTHING')).toBe(true);
    expect(isNameDefect('DEPARTMENT_HAS_NO_MEMBERS')).toBe(false);
    expect(isNameDefect('ROLE_HELD_BY_NOBODY')).toBe(false);
    expect(isNameDefect(null)).toBe(false);
  });

  it('tones an unavailable engine as neutral, not as a pass or a failure', () => {
    // With no engine nothing was measured. Colouring it green would report an absence as a clean
    // result; colouring it red would report it as a defect in the data.
    expect(REASON_TONE.ENGINE_UNAVAILABLE).toBe('neutral');
  });
});

describe('CE-10 addressee keys', () => {
  it('keys a ROLE by name and everything else by id', () => {
    // ROLE has no id — the engine addresses it by name — so a key built on id alone would collapse
    // every role in the picker to the same option.
    expect(addresseeKey({ type: 'ROLE', id: null, key: 'CISO', label: 'CISO', users: 1 }))
      .toBe('ROLE:CISO');
    expect(addresseeKey({ type: 'DEPARTMENT', id: 7, key: 'IT', label: 'IT', users: 3 }))
      .toBe('DEPARTMENT:7');
    expect(addresseeKey({ type: 'COMMITTEE', id: 12, key: 'CAB', label: 'CAB', users: 5 }))
      .toBe('COMMITTEE:12');
  });

  it('gives two roles two distinct keys', () => {
    const a = addresseeKey({ type: 'ROLE', id: null, key: 'CISO', label: 'CISO', users: 1 });
    const b = addresseeKey({ type: 'ROLE', id: null, key: 'CHANGE_MANAGER', label: 'CM', users: 2 });
    expect(a).not.toBe(b);
  });
});

describe('CE-10 report labels', () => {
  it('has the counts, the hints and the both-sides wording', () => {
    for (const k of ['ceTitle', 'ceSub', 'ceOpenTasks', 'ceOverdue', 'ceReachable',
                     'ceUnreachable', 'ceUnnamed', 'ceUnreachableHint', 'ceUnnamedHint',
                     'ceEngineAbsent', 'ceEngineAbsentHint', 'ceNoMatch', 'ceNoAddressees',
                     'ceNotAnApproval'] as const) {
      expect(en[k as keyof typeof en], `en ${k}`).toBeTruthy();
      expect(ar[k as keyof typeof ar], `ar ${k}`).toBeTruthy();
    }
  });

  it('says an absent engine means unknown rather than clean', () => {
    expect(String(en.ceEngineAbsentHint)).toMatch(/unknown/i);
    expect(String(en.ceEngineAbsentHint)).toMatch(/not clean/i);
  });

  it('states in both languages that routing is not an authorisation', () => {
    // The one sentence that keeps the two-route rule visible on screen rather than only in a javadoc.
    expect(String(en.ceNotAnApproval)).toMatch(/cannot approve/i);
    expect(String(en.ceNotAnApproval)).toMatch(/CAB/);
    expect(String(ar.ceNotAnApproval).length).toBeGreaterThan(20);
  });
});
