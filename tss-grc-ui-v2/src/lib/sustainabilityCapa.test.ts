// CT-5 — the source-label map must cover every backend source type (blank
// badges shipped once: VENDOR_ESG/EVIDENCE rows rendered empty), and unknown
// codes must fall back to the raw code, never blank.
import { describe, expect, it } from 'vitest';
import { capaSourceLabel, type CapaSourceType } from './sustainabilityCapa';

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
