import { describe, it, expect } from 'vitest';
import { en } from '../translations/en';
import { ar } from '../translations/ar';
import {
  isLivePreApproval, MODEL_STATUS_TONE, MIN_VALID_DAYS, MAX_VALID_DAYS,
} from '../pages/change/ChangeModelCatalogue';
import type { ChangeModelDto } from '../lib/api';

/**
 * S8e / CE-2 — the frontend must not be the place where "pre-approved" becomes a label again.
 *
 * The server refuses to apply an expired model; if the UI still renders a green "Pre-approved" chip
 * for one, the screen contradicts the API and the screen is what a user believes. So the predicate
 * is exported and pinned here rather than inlined into JSX.
 */
function model(over: Partial<ChangeModelDto> = {}): ChangeModelDto {
  return {
    id: 1, code: 'STD-01', nameEn: 'Onboard a server', nameAr: 'تهيئة خادم',
    changeType: 'STANDARD', preApproved: true, status: 'APPROVED',
    authorisedByName: 'compliance',
    authorisedUntil: new Date(Date.now() + 86_400_000 * 30).toISOString(),
    ...over,
  };
}

describe('CE-2 — the pre-approval predicate', () => {
  it('needs the flag, the status and an unexpired date', () => {
    expect(isLivePreApproval(model())).toBe(true);
    expect(isLivePreApproval(model({ preApproved: false })))
      .toBe(false);
    expect(isLivePreApproval(model({ status: 'DRAFT' })))
      .toBe(false);
    expect(isLivePreApproval(model({ status: 'SUSPENDED' })))
      .toBe(false);
    expect(isLivePreApproval(model({ authorisedUntil: null })))
      .toBe(false);
  });

  it('an expired pre-approval is NOT live — the one the UI would otherwise render green', () => {
    const lapsed = model({ authorisedUntil: new Date(Date.now() - 86_400_000).toISOString() });
    expect(lapsed.status).toBe('APPROVED');
    expect(lapsed.preApproved).toBe(true);
    expect(isLivePreApproval(lapsed))
      .toBe(false);
  });

  it('the day bounds match the service (30–730)', () => {
    expect(MIN_VALID_DAYS).toBe(30);
    expect(MAX_VALID_DAYS).toBe(730);
  });

  it('every model status has a tone, and APPROVED is the only success tone', () => {
    for (const s of ['DRAFT', 'APPROVED', 'SUSPENDED', 'RETIRED']) {
      expect(MODEL_STATUS_TONE[s], s).toBeTruthy();
    }
    expect(MODEL_STATUS_TONE.APPROVED).toBe('success');
    expect(Object.values(MODEL_STATUS_TONE).filter(v => v === 'success')).toHaveLength(1);
  });
});

describe('CE-2 — catalogue copy', () => {
  const KEYS = [
    'cmodelTitle', 'cmodelSub', 'cmodelFormHint', 'cmodelNew', 'cmodelSaveDraft', 'cmodelCode',
    'cmodelName', 'cmodelNameEn', 'cmodelNameAr', 'cmodelType', 'cmodelRisk', 'cmodelSteps',
    'cmodelRollback', 'cmodelTest', 'cmodelStatus', 'cmodelAuthority', 'cmodelUntil',
    'cmodelStatus_DRAFT', 'cmodelStatus_APPROVED', 'cmodelStatus_SUSPENDED', 'cmodelStatus_RETIRED',
    'cmodelLapsed', 'cmodelLive', 'cmodelExpired', 'cmodelEmptyTitle', 'cmodelEmptySub',
    'cmodelApproveBtn', 'cmodelApproveTitle', 'cmodelApproveLabel', 'cmodelApproveBody',
    'cmodelApproveRange', 'cmodelApproved', 'cmodelSuspendTitle', 'cmodelSuspendBtn',
    'cmodelRetireTitle', 'cmodelRetireBtn', 'cmodelStatusChanged', 'cmodelCreated', 'cmodelFailed',
  ] as const;

  it('every key exists in BOTH dictionaries', () => {
    for (const k of KEYS) {
      expect(en[k as keyof typeof en], `en.${k}`).toBeTruthy();
      expect(ar[k as keyof typeof ar], `ar.${k}`).toBeTruthy();
    }
  });

  it('the Arabic copy is Arabic, not a copied English string', () => {
    const arabic = /[؀-ۿ]/;
    for (const k of KEYS) {
      expect(arabic.test(String(ar[k as keyof typeof ar])), `ar.${k}`).toBe(true);
    }
  });

  it('the empty state says what the absence MEANS, not just that it is empty', () => {
    // V354 ships the catalogue empty on purpose. "No models" alone reads as an unused feature; the
    // point is that until one exists, a STANDARD change is pre-approved by nothing but its own label.
    expect(en.cmodelEmptySub).toMatch(/pre-approved by nothing/i);
  });

  it('the form hint warns that saving does not grant the authority', () => {
    expect(en.cmodelFormHint).toMatch(/DRAFT/);
    expect(en.cmodelFormHint).toMatch(/may not approve/i);
  });

  it('the approval copy states the bounds and why an unbounded one is not a control', () => {
    expect(en.cmodelApproveBody).toContain('{min}');
    expect(en.cmodelApproveBody).toContain('{max}');
    expect(en.cmodelApproveBody).toMatch(/never expires is not a control/i);
  });
});
