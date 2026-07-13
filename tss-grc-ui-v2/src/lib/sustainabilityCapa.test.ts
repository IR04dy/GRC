// CT-5 — the source-label map must cover every backend source type (blank
// badges shipped once: VENDOR_ESG/EVIDENCE rows rendered empty), and unknown
// codes must fall back to the raw code, never blank.
// CT-3/CT-4 — CAR/PAR labels + the principal-based Rule-A mirror.
import { describe, expect, it } from 'vitest';
import {
  canVerify, capaActionTypeLabel, capaSourceLabel, capaSourceTab,
  type CapaActionType, type CapaSourceType, type CorrectiveAction,
} from './sustainabilityCapa';

const ALL_SOURCES: CapaSourceType[] = [
  'CLIMATE_RISK', 'GHG_RECORD', 'ANNEX_GAP', 'CMA_DISCLOSURE',
  'TCFD_ITEM', 'SGI_COMMITMENT', 'MANUAL',
  'EVIDENCE', 'VENDOR_ESG', 'ESG_TARGET', 'ENV_PERMIT',
];

describe('capaSourceLabel (CT-5)', () => {
  it('labels every backend source type in both languages', () => {
    for (const s of ALL_SOURCES) {
      expect(capaSourceLabel(s, 'en'), `${s} en`).toBeTruthy();
      expect(capaSourceLabel(s, 'ar'), `${s} ar`).toBeTruthy();
    }
  });

  it('falls back to the raw code for a source the map does not know yet', () => {
    const future = 'FUTURE_SOURCE' as CapaSourceType;
    expect(capaSourceLabel(future, 'en')).toBe('FUTURE_SOURCE');
    expect(capaSourceLabel(future, 'ar')).toBe('FUTURE_SOURCE');
  });
});

describe('capaSourceTab (CT-6 — source deep-links)', () => {
  it('maps every source except MANUAL to a live ESG tab id', () => {
    for (const s of ALL_SOURCES) {
      if (s === 'MANUAL') continue;
      expect(capaSourceTab(s), `${s} must deep-link to its owning tab`).toBeTruthy();
    }
    expect(capaSourceTab('MANUAL')).toBeUndefined();
  });
});

describe('capaActionTypeLabel (CT-3)', () => {
  it('labels both action types in both languages', () => {
    for (const t of ['CORRECTIVE', 'PREVENTIVE'] as CapaActionType[]) {
      expect(capaActionTypeLabel(t, 'en'), `${t} en`).toBeTruthy();
      expect(capaActionTypeLabel(t, 'ar'), `${t} ar`).toBeTruthy();
    }
    expect(capaActionTypeLabel('PREVENTIVE', 'ar')).toBe('إجراء وقائي');
  });
});

describe('canVerify (CT-4 — Rule-A principal mirror)', () => {
  const completed = (over: Partial<CorrectiveAction>): CorrectiveAction => ({
    sourceType: 'MANUAL', titleEn: 't', priority: 'HIGH', status: 'COMPLETED', ...over,
  });

  it('blocks the owner verifying their own action, by name or email, case/trim-insensitive', () => {
    expect(canVerify(completed({ ownerName: 'Sara Al-Zahrani' }), '  sara al-zahrani ')).toBe(false);
    expect(canVerify(completed({ ownerEmail: 'sara@tss4it.com' }), 'SARA@TSS4IT.COM')).toBe(false);
  });

  it('allows a different principal, and only from COMPLETED', () => {
    expect(canVerify(completed({ ownerName: 'Sara' }), 'khalid')).toBe(true);
    expect(canVerify({ ...completed({ ownerName: 'Sara' }), status: 'OPEN' }, 'khalid')).toBe(false);
    expect(canVerify(completed({ ownerName: 'Sara' }), '')).toBe(false);
  });
});
