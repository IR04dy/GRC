import { beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * W2-1a (F2-02) — the command-palette index must never invent records.
 *
 * Before this slice `searchIndex.ts` imported eight mock datasets at module scope and
 * indexed them, so Cmd+K confidently returned risks, findings, controls, vendors, DPIAs,
 * incidents and changes that did not exist in the tenant, with refs and hrefs that looked
 * real. The three properties below are what "de-mocked" actually has to mean, and the third
 * is the one a naive fix gets wrong: a module whose fetch FAILS must be distinguishable from
 * a module that is genuinely empty, because the old implementation could never tell the
 * difference — it always had mock rows to fall back on.
 */

const mockRiskList = vi.fn();
const mockFindings = vi.fn();
const mockControls = vi.fn();
const mockVendors = vi.fn();
const mockDpias = vi.fn();
const mockIncidents = vi.fn();
const mockChanges = vi.fn();
const mockEvidence = vi.fn();
const mockKris = vi.fn();

vi.mock('./api', () => ({
  riskApi: { list: (...a: unknown[]) => mockRiskList(...a) },
  complianceApi: {
    findings: (...a: unknown[]) => mockFindings(...a),
    controls: (...a: unknown[]) => mockControls(...a),
  },
  dpiaApi: { list: (...a: unknown[]) => mockDpias(...a) },
  tprmApi: { vendors: (...a: unknown[]) => mockVendors(...a) },
  incidentApi: { list: (...a: unknown[]) => mockIncidents(...a) },
  changeApi: { list: (...a: unknown[]) => mockChanges(...a) },
}));
vi.mock('./evidenceItems', () => ({ listEvidenceItems: (...a: unknown[]) => mockEvidence(...a) }));
vi.mock('./kriEngine', () => ({ kriApi: { listDefinitions: (...a: unknown[]) => mockKris(...a) } }));

const page = <T,>(content: T[]) => Promise.resolve({ content });

async function load() {
  const mod = await import('./searchIndex');
  mod._resetSearchIndex();
  return mod;
}

beforeEach(() => {
  vi.clearAllMocks();
  mockRiskList.mockReturnValue(page([{
    id: 7, riskCode: 'RISK-0007', titleEn: 'Third-party concentration',
    categoryNameEn: 'Operational', status: 'OPEN', riskLevel: 'HIGH',
  }]));
  mockFindings.mockReturnValue(page([]));
  mockControls.mockReturnValue(page([]));
  mockVendors.mockReturnValue(page([{
    id: 3, vendorName: 'Acme Cloud', riskRating: 'HIGH', status: 'ACTIVE',
  }]));
  mockDpias.mockReturnValue(page([]));
  mockIncidents.mockReturnValue(page([]));
  mockChanges.mockReturnValue(page([]));
  mockEvidence.mockReturnValue(page([]));
  mockKris.mockResolvedValue([]);
});

describe('W2-1a — the palette index holds live records only', () => {
  it('indexes nothing but pages and the regulator catalogue before hydration', async () => {
    const { searchIndex, searchIndexStatus } = await load();

    expect(searchIndexStatus().hydrated).toBe(false);
    expect(searchIndexStatus().entityItems).toBe(0);
    // no risk exists in the index yet, so a risk-filtered search finds nothing
    expect(searchIndex('risk: concentration')).toHaveLength(0);
    // ...while pages are still navigable, which is the palette's other job
    expect(searchIndex('dashboard').some(i => i.kind === 'PAGE')).toBe(true);
    // the static Saudi regulator catalogue is product reference data, not tenant data
    expect(searchIndex('auth: nca').length).toBeGreaterThan(0);
  });

  it('indexes records returned by the live API, with their real codes and hrefs', async () => {
    const { hydrateSearchIndex, searchIndex } = await load();
    await hydrateSearchIndex();

    const [risk] = searchIndex('risk: concentration');
    expect(risk).toMatchObject({
      kind: 'RISK', ref: 'RISK-0007', href: '/risk/7', title: 'Third-party concentration',
    });
    expect(searchIndex('vendor: acme')[0]).toMatchObject({ kind: 'VENDOR', href: '/vendors/3' });
  });

  it('a module that fails is reported as failed, NOT as empty', async () => {
    mockVendors.mockRejectedValue(new Error('503 module unavailable'));
    const { hydrateSearchIndex, searchIndex } = await load();

    const status = await hydrateSearchIndex();

    // the failure is named, so the UI can say "results are incomplete" instead of
    // letting an empty list read as "this tenant has no vendors"
    expect(status.failed).toContain('VENDOR');
    expect(status.hydrated).toBe(true);
    // and one module failing must not blank the rest of the index
    expect(status.kinds).toContain('RISK');
    expect(searchIndex('risk: concentration')).toHaveLength(1);
    expect(searchIndex('vendor: acme')).toHaveLength(0);
  });

  it('re-hydrating replaces the previous results rather than accumulating them', async () => {
    const { hydrateSearchIndex, searchIndex } = await load();
    await hydrateSearchIndex();
    expect(searchIndex('risk: concentration')).toHaveLength(1);

    // the record was deleted between hydrations
    mockRiskList.mockReturnValue(page([]));
    const status = await hydrateSearchIndex();

    expect(searchIndex('risk: concentration')).toHaveLength(0);
    expect(status.kinds).not.toContain('RISK');
  });

  it('hydratePolicySearch still merges canonical policies (CH-9 kept working)', async () => {
    const { hydrateSearchIndex, hydratePolicySearch, searchIndex } = await load();
    await hydrateSearchIndex();
    hydratePolicySearch([
      { id: 12, policyCode: 'POL-012', version: '2.0', titleEn: 'Access Control Policy', status: 'PUBLISHED' },
    ]);

    expect(searchIndex('policy: access')[0]).toMatchObject({
      kind: 'POLICY', ref: 'POL-012', href: '/policies/12',
    });
    // and it must not have wiped the kinds hydrateSearchIndex populated
    expect(searchIndex('risk: concentration')).toHaveLength(1);
  });
});
