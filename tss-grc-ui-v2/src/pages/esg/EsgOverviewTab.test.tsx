import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { I18nProvider } from '../../lib/i18n';
import { ToastProvider } from '../../lib/toast';
import { ThemeProvider } from '../../lib/theme';
import { OverviewTab } from './EsgOverviewTab';
import type { DashboardPayload } from '../../lib/sustainabilityDashboard';

/**
 * F2-16 tranche 1 — the executive sustainability dashboard's first test, ever.
 *
 * <p><b>⚠ The plan's reason for splitting this page first was measured and is FALSE.</b> The F2-16 row
 * says <i>"split `EsgPage` first, `sust` has ~200 tests to catch mistakes"</i>. Those are <b>backend</b>
 * tests for `module/sust`. Measured 2026-08-08: the only file in the frontend that named
 * `EsgPage.tsx` at all was `sourceFileSizeRatchet.test.ts`, which counts its lines. <b>The 5,582-line
 * page had no behavioural test of any kind</b>, so the safety net the ordering assumed did not exist.
 *
 * <p><b>Which is the argument for the split, not against it.</b> Fourteen of these widgets are pure
 * functions of <code>{{ data, isAr }}</code> — they were always testable, and nobody could test them
 * while they sat at lines 1315–1699 of a page whose import block alone is 130 lines alone. Extracting
 * the surface is what made this file possible; that is the deliverable, not the line count.
 *
 * <h3>What these tests pin</h3>
 * <ul>
 *   <li>all fourteen widgets render from one payload, <b>in both locales</b> — this platform is
 *       Arabic-first, and an English-only assertion would not describe what a Saudi user sees;</li>
 *   <li>the numbers on screen come from the payload, so a widget rendering an empty shell fails
 *       (anti-vacuity: a title-only assertion passes on a component that lost its body);</li>
 *   <li>the dashboard call failing is <b>soft</b> — `OverviewTab` wraps both reads in
 *       `.catch(() => null)` deliberately so the page shell survives a dead endpoint, and that is a
 *       contract worth pinning rather than a coincidence;</li>
 *   <li>the four GF-3 horizon tiles call `onNavigate` with the tab they own — the one piece of
 *       cross-tab behaviour in the extracted range, and the thing a move could silently break.</li>
 * </ul>
 */

const getDashboard = vi.fn();
const getSettings = vi.fn();
const trajectoryGet = vi.fn();

vi.mock('../../lib/sustainabilityDashboard', async (importOriginal) => {
  // Keep the real label/tone helpers — mocking them would make the locale assertions vacuous.
  const real = await importOriginal<typeof import('../../lib/sustainabilityDashboard')>();
  return { ...real, getDashboard: (...a: unknown[]) => getDashboard(...a) };
});
vi.mock('../../lib/sustainabilitySettings', async (importOriginal) => {
  const real = await importOriginal<typeof import('../../lib/sustainabilitySettings')>();
  return { ...real, getSettings: (...a: unknown[]) => getSettings(...a) };
});
vi.mock('../../lib/esgDashboard', async (importOriginal) => {
  const real = await importOriginal<typeof import('../../lib/esgDashboard')>();
  return { ...real, esgTrajectoryApi: { get: (...a: unknown[]) => trajectoryGet(...a) } };
});

/**
 * A complete payload. Every figure is distinctive so an assertion cannot pass on a default: 73 is
 * not a percentage any helper computes, and 1234.5 tonnes appears nowhere else.
 */
const PAYLOAD: DashboardPayload = {
  year: 2025,
  organisationId: 1,
  overall: {
    score: 73,
    band: 'ADEQUATE',
    breakdown: {
      ghgVerification: 60, sgiOnTrack: 80, cmaDisclosure: 55, tcfdAlignment: 47,
      capaClosure: 90, evidenceApproved: 66, climateTreated: 41,
    },
  },
  ghg: {
    year: 2025, scope1Total: 300.5, scope2Total: 400, scope3Total: 534,
    grandTotal: 1234.5, scope3Coverage: 62, scope3PartialFlag: true, recordCount: 19,
  },
  ghgYoy: { year: 2025, scope1YoY: -4.2, scope2YoY: 1.1, scope3YoY: -7.7, totalYoY: -3.3 },
  sgi: {
    activeTotal: 11, achievedTotal: 4, retiredTotal: 1,
    byTier: { PLATINUM: 1, GOLD: 3, SILVER: 7 },
    bySector: { ENERGY: 6, WASTE: 5 },
  },
  cma: {
    year: 2025, total: 20, applicable: 18, byStatus: { DISCLOSED: 9, PARTIAL: 5, NOT_DISCLOSED: 4 },
    withEvidence: 8, withGap: 6, readinessPct: 58,
  },
  tcfd: {
    year: 2025, total: 16, applicable: 15, byStatus: { DISCLOSED: 7, PARTIAL: 4 },
    byPillar: { GOVERNANCE: 4, STRATEGY: 4, RISK_MANAGEMENT: 4, METRICS_TARGETS: 4 },
    disclosedByPillar: { GOVERNANCE: 3, STRATEGY: 1, RISK_MANAGEMENT: 2, METRICS_TARGETS: 1 },
    withEvidence: 6, withGap: 5, alignmentPct: 47,
    strategyResilienceGap: true, strategyResilienceItemId: 42,
  },
  capa: {
    open: 5, inProgress: 3, completed: 2, verified: 1, overdue: 2, dueSoon: 1,
    byPriority: { HIGH: 4, MEDIUM: 3 }, bySource: { GHG_VERIFICATION: 4, CMA_GAP: 3 },
    activeTotal: 8,
  },
  evidence: {
    total: 30, byStatus: { APPROVED: 20, SUBMITTED: 7, DRAFT: 3 },
    byModule: { SUST: 25, CYBER: 5 }, expiringSoon30d: 4,
  },
  materiality: {
    year: 2025, total: 12, byTier: { CRITICAL: 2, HIGH: 4 },
    byCategory: { ENVIRONMENTAL: 5, SOCIAL: 4, GOVERNANCE: 3 },
    criticalCount: 2, highCount: 4,
    matrix: [
      {
        id: 1, topicCode: 'E-01', topicEn: 'Water stewardship', topicAr: 'إدارة المياه',
        category: 'ENVIRONMENTAL', stakeholderImportance: 4.5, businessImpact: 4.1,
        materialityScore: 4.3, priorityTier: 'CRITICAL', status: 'APPROVED',
      },
    ],
  },
  metrics: {
    year: 2025, total: 26, byCategory: { ENVIRONMENTAL: 12 }, atTargetByCategory: { ENVIRONMENTAL: 5 },
    offTrackByCategory: { ENVIRONMENTAL: 3 }, meanProgressByCategory: { ENVIRONMENTAL: 61 },
  },
  topGaps: ['TCFD_STRATEGY_RESILIENCE', 'GHG_SCOPE3_PARTIAL'],
  horizon: {
    allocationRatioPct: 91.4, allocationInstruments: 3, ifrsCoveragePct: 64.2,
    permitsExpiring90d: 2, targetsOnTrack: 5, targetsOffTrack: 2, targetsNoData: 1,
  },
};

/** The fourteen widget headings, in both locales — the extracted surface's whole vocabulary. */
const WIDGETS = [
  ['ESG Overall Score', 'مؤشّر الاستدامة الشامل'],
  ['Top ESG gaps', 'أبرز الثغرات التنفيذية'],
  ['GHG total emissions', 'إجمالي الانبعاثات'],
  ['Scope breakdown', 'توزيع النطاقات'],
  ['Verification status', 'حالة التحقّق'],
  ['SGI commitments', 'التزامات SGI'],
  ['CMA disclosure readiness', 'جاهزية إفصاحات CMA'],
  ['TCFD alignment', 'محاذاة TCFD'],
  ['Climate risk heatmap', 'مصفوفة المخاطر المناخية'],
  ['Corrective actions', 'الإجراءات التصحيحية'],
  ['Evidence completeness', 'اكتمال الأدلة'],
  ['Annex readiness gauge', 'جاهزية الملحق السنوي'],
  ['Materiality — top topics', 'الأهمية النسبية — أهم الموضوعات'],
  ['Annual emissions trend', 'مسار الانبعاثات السنوية'],
] as const;

function renderOverview(lang: 'en' | 'ar', onNavigate = vi.fn()) {
  localStorage.setItem('grc.lang', lang);
  const utils = render(
    <MemoryRouter>
      <ThemeProvider>
        <I18nProvider>
          <ToastProvider>
            <OverviewTab onNavigate={onNavigate} />
          </ToastProvider>
        </I18nProvider>
      </ThemeProvider>
    </MemoryRouter>,
  );
  return { ...utils, onNavigate };
}

describe('F2-16 — the extracted ESG overview surface', () => {
  beforeEach(() => {
    getDashboard.mockResolvedValue(PAYLOAD);
    // ⚠ The field names are read off `OrganisationSettings`, not guessed. My first draft invented
    // `esgWarnThreshold`/`esgCriticalThreshold`; every test here still PASSED, because the page falls
    // back to `?? 60` / `?? 40` and 73 clears both — so the settings mock was inert and nothing said
    // so. Only the banner test, which needs the tenant's own threshold to bite, could tell.
    getSettings.mockResolvedValue({ esgReadinessWarningThreshold: 60, esgReadinessCriticalThreshold: 40 });
    trajectoryGet.mockResolvedValue([]);
  });

  afterEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it.each(['en', 'ar'] as const)('renders all fourteen widgets in %s', async (lang) => {
    renderOverview(lang);
    const i = lang === 'ar' ? 1 : 0;

    // The first await also proves the payload arrived; the rest are already in the DOM.
    expect(await screen.findByText(WIDGETS[0][i])).toBeTruthy();
    for (const titles of WIDGETS) {
      expect(screen.getByText(titles[i]), `${titles[i]} is missing in ${lang}`).toBeTruthy();
    }
  });

  it('prints figures taken from the payload — a widget that lost its body fails', async () => {
    renderOverview('en');

    // Anti-vacuity. Titles are static JSX and would survive a body that stopped reading `data`;
    // these four numbers exist only if each widget still computes from the payload.
    expect(await screen.findByText('73')).toBeTruthy();            // overall score
    expect(screen.getByText('58%')).toBeTruthy();                  // cma.readinessPct
    expect(screen.getByText('47%')).toBeTruthy();                  // tcfd.alignmentPct
    expect(screen.getByText('8')).toBeTruthy();                    // capa.activeTotal
    expect(screen.getByText(/1,234\.5|1234\.5/)).toBeTruthy();     // ghg.grandTotal
  });

  it('a dead dashboard endpoint is a soft failure, not a blank crash', async () => {
    getDashboard.mockRejectedValue(new Error('boom'));
    renderOverview('en');

    // `OverviewTab` wraps both reads in `.catch(() => null)` on purpose — "the page shell still
    // renders". So the widgets are absent and nothing throws; that is the contract.
    await waitFor(() => expect(getDashboard).toHaveBeenCalled());
    await waitFor(() => expect(screen.queryByText('ESG Overall Score')).toBeNull());
    expect(screen.queryByText('CMA disclosure readiness')).toBeNull();
  });

  it('settings failing still renders the dashboard — only the threshold banner drops', async () => {
    getSettings.mockRejectedValue(new Error('no settings'));
    renderOverview('en');

    expect(await screen.findByText('ESG Overall Score')).toBeTruthy();
    // 73 is above both defaults anyway; the point is that a null settings payload does not throw.
    expect(screen.queryByText(/severely below threshold/i)).toBeNull();
  });

  it.each([
    ['IFRS S1/S2 coverage', 'ifrs'],
    ['Permits expiring ≤ 90d', 'env'],
    ['Targets on track', 'targets'],
  ] as const)('the %s tile navigates to the %s tab', async (label, tab) => {
    const { onNavigate } = renderOverview('en');
    const tile = await screen.findByText(label);

    // GF-3: each horizon tile lands on its owning tab. This is the only cross-tab behaviour in the
    // extracted range, so it is the one thing a move could break without a type error.
    fireEvent.click(tile);
    expect(onNavigate).toHaveBeenCalledWith(tab);
  });

  it('the threshold banner fires from the SETTINGS payload, not a hard-coded number', async () => {
    // 73 is below this tenant's configured warning threshold of 80 — so the banner must appear even
    // though it would not against the default. A hard-coded threshold passes the tests above.
    getSettings.mockResolvedValue({ esgReadinessWarningThreshold: 80, esgReadinessCriticalThreshold: 40 });
    renderOverview('en');

    expect(await screen.findByText(/below threshold/i)).toBeTruthy();
    expect(screen.getByText(/configured threshold 80\/100/)).toBeTruthy();
  });
});
