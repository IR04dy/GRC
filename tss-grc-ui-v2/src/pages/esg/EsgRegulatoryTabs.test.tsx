import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { I18nProvider } from '../../lib/i18n';
import { ToastProvider } from '../../lib/toast';
import { ThemeProvider } from '../../lib/theme';
import { CmaTab } from './EsgCmaTab';
import { TcfdTab } from './EsgTcfdTab';
import { MaterialityTab } from './EsgMaterialityTab';
import { MetricsTab } from './EsgMetricsTab';
import type { CmaDisclosure, CmaReadiness } from '../../lib/sustainabilityCma';
import type { TcfdItem, TcfdReadiness } from '../../lib/sustainabilityTcfd';
import type { MaterialTopic, MaterialMatrix } from '../../lib/sustainabilityMateriality';
import type { EsgMetric, MetricAggregate } from '../../lib/sustainabilityMetrics';

/**
 * F2-16 tranche 2 — the first tests for the four regulatory ESG tabs, extracted from `EsgPage.tsx`.
 *
 * <p>These four surfaces carry the platform's <b>externally-facing regulatory disclosures</b> — CMA
 * Articles 91–94, TCFD alignment, double-materiality and the ESG metrics register. Until this slice
 * they sat at lines 1287–3055 of a 4,994-line page and had no test of any kind.
 *
 * <p><b>Why a stateful container is worth testing and a pure widget is easy to test.</b> Tranche 1
 * extracted fourteen pure functions of <code>{{ data, isAr }}</code>. These are different: each owns
 * `useState` + a `useEffect` that fans out to its own API module. So the mock <i>is</i> the fixture,
 * and the contract each test pins is "what the server returns reaches the screen".
 *
 * <h3>What these tests pin</h3>
 * <ul>
 *   <li><b>Each tab renders its rows from its own list endpoint</b>, in <b>both locales</b> — this is
 *       an Arabic-first platform, and every row here has an `*_ar` twin the English assertion cannot
 *       see. An `*_ar` field that stops being read is invisible to an English-only test.</li>
 *   <li><b>The headline readiness figure comes from the payload.</b> Every one is distinctive (73%,
 *       41%, 29, 37) and none is a value the component can compute or default to — so a tab that
 *       renders its shell with an empty body fails rather than passing on `?? 0`.</li>
 *   <li><b>A dead endpoint is a SOFT failure, per tab.</b> All four wrap every read in its own
 *       `.catch(…)` so the tab shell survives — that is deliberate (the comments say
 *       "load independently") and therefore a contract, not a coincidence. The empty state must
 *       appear, and nothing may throw.</li>
 *   <li><b>The reporting year is the one the API is asked for.</b> All four hardcode 2025; the move
 *       could have silently dropped an argument, and a tab that queries the wrong year looks correct
 *       on screen while disclosing the wrong period.</li>
 * </ul>
 *
 * <p><b>⚠ Tranche 1's lesson applied here deliberately: the field names are read from the DTOs, not
 * invented.</b> That slice's settings mock used `esgWarnThreshold` where the real field is
 * `esgReadinessWarningThreshold`; eight assertions passed anyway because the component fell back to a
 * default. So every fixture below uses the interface's own names, and each tab has at least one
 * assertion that <i>needs</i> the mocked value to bite — a mock with the wrong field names is a test
 * passing for the wrong reason.
 */

const listCma = vi.fn();
const cmaReadiness = vi.fn();
const listTcfd = vi.fn();
const tcfdReadiness = vi.fn();
const listClimateItems = vi.fn();
const listMateriality = vi.fn();
const generateMatrix = vi.fn();
const listMetrics = vi.fn();
const metricsAggregate = vi.fn();

// Only the READS are replaced. The label/tone/progress helpers stay real: mocking `articleLabel`,
// `tcfdPillarLabel`, `esgMetricCategoryLabel` or `computeMetricProgress` would make every locale
// assertion vacuous — they are exactly what turns `pillar: 'STRATEGY'` into Arabic on screen.
vi.mock('../../lib/sustainabilityCma', async (importOriginal) => ({
  ...await importOriginal<typeof import('../../lib/sustainabilityCma')>(),
  listCma: (...a: unknown[]) => listCma(...a),
  cmaReadiness: (...a: unknown[]) => cmaReadiness(...a),
}));
vi.mock('../../lib/sustainabilityTcfd', async (importOriginal) => ({
  ...await importOriginal<typeof import('../../lib/sustainabilityTcfd')>(),
  listTcfd: (...a: unknown[]) => listTcfd(...a),
  tcfdReadiness: (...a: unknown[]) => tcfdReadiness(...a),
}));
vi.mock('../../lib/climateRisk', async (importOriginal) => ({
  ...await importOriginal<typeof import('../../lib/climateRisk')>(),
  listClimateItems: (...a: unknown[]) => listClimateItems(...a),
}));
vi.mock('../../lib/sustainabilityMateriality', async (importOriginal) => ({
  ...await importOriginal<typeof import('../../lib/sustainabilityMateriality')>(),
  listMateriality: (...a: unknown[]) => listMateriality(...a),
  generateMatrix: (...a: unknown[]) => generateMatrix(...a),
}));
vi.mock('../../lib/sustainabilityMetrics', async (importOriginal) => ({
  ...await importOriginal<typeof import('../../lib/sustainabilityMetrics')>(),
  listMetrics: (...a: unknown[]) => listMetrics(...a),
  metricsAggregate: (...a: unknown[]) => metricsAggregate(...a),
}));

const page = <T,>(content: T[]) => ({ content, totalElements: content.length });

/** Field names taken from `CmaDisclosure` / `CmaReadiness`, not invented. */
const CMA_ROW: CmaDisclosure = {
  id: 9101, reportingYear: 2025, article: '92', clause: '(a)',
  titleEn: 'Board sustainability oversight statement',
  titleAr: 'بيان إشراف المجلس على الاستدامة',
  requirementEn: 'Describe board-level oversight of sustainability matters',
  requirementAr: 'وصف إشراف المجلس على مسائل الاستدامة',
  disclosureStatus: 'PARTIAL',
  evidenceRef: 'EV-CMA-4417', evidenceOwner: 'Governance Office',
  gapDescriptionEn: 'Committee charter does not yet name the ESG mandate',
  gapDescriptionAr: 'لا يسمّي ميثاق اللجنة تكليف الاستدامة بعد',
};
const CMA_READINESS: CmaReadiness = {
  year: 2025, total: 24, applicable: 22,
  byStatus: { DISCLOSED: 11, PARTIAL: 4, NOT_DISCLOSED: 3, NOT_APPLICABLE: 2,
              EVIDENCE_REQUIRED: 5, UNDER_REVIEW: 1 },
  withEvidence: 16, withGap: 7, readinessPct: 73,
};

const TCFD_ROW: TcfdItem = {
  id: 7201, reportingYear: 2025, pillar: 'STRATEGY', recommendationCode: 'S-c',
  titleEn: 'Resilience of the strategy under 2°C scenarios',
  titleAr: 'مرونة الاستراتيجية في سيناريو درجتين مئويتين',
  recommendationEn: 'Describe the resilience of the organisation’s strategy',
  disclosureStatus: 'EVIDENCE_REQUIRED', evidenceRef: 'EV-TCFD-3308',
};
const TCFD_READINESS: TcfdReadiness = {
  year: 2025, total: 11, applicable: 11,
  byStatus: { DISCLOSED: 3, PARTIAL: 2, NOT_DISCLOSED: 1, NOT_APPLICABLE: 0,
              EVIDENCE_REQUIRED: 4, UNDER_REVIEW: 1 },
  byPillar: { GOVERNANCE: 2, STRATEGY: 3, RISK_MGMT: 3, METRICS_TARGETS: 3 },
  disclosedByPillar: { GOVERNANCE: 1, STRATEGY: 0, RISK_MGMT: 1, METRICS_TARGETS: 1 },
  withEvidence: 6, withGap: 3, alignmentPct: 41,
  strategyResilienceGap: true, strategyResilienceItemId: 7201,
};

const TOPIC: MaterialTopic = {
  id: 5501, topicCode: 'MT-ENV-014', reportingYear: 2025, category: 'ENVIRONMENTAL',
  topicEn: 'Water stress in Riyadh operations', topicAr: 'الإجهاد المائي في عمليات الرياض',
  stakeholderImportance: 4, businessImpact: 5,
  impactMateriality: 5, financialMateriality: 4, materialityScore: 5,
  priorityTier: 'CRITICAL', status: 'APPROVED',
};
const MATRIX: MaterialMatrix = {
  year: 2025, total: 29,
  byTier: { CRITICAL: 6, HIGH: 9, MEDIUM: 10, LOW: 4 },
  byCategory: { ENVIRONMENTAL: 12, SOCIAL: 9, GOVERNANCE: 8 },
  criticalCount: 6, highCount: 9, matrix: [],
};

const METRIC: EsgMetric = {
  id: 4402, metricCode: 'ESG-ENV-021', reportingYear: 2025, category: 'ENVIRONMENTAL',
  metricNameEn: 'Renewable electricity share', metricNameAr: 'نسبة الكهرباء المتجددة',
  unit: '%', baselineValue: 12, baselineYear: 2023, currentValue: 38,
  currentValueAsOf: '2025-06-30', targetValue: 65, targetYear: 2030, status: 'ACTIVE',
  ownerName: 'Facilities Engineering',
};
const AGGREGATE: MetricAggregate = {
  year: 2025, total: 37,
  byCategory: { ENVIRONMENTAL: 15, SOCIAL: 12, GOVERNANCE: 10 },
  atTargetByCategory: { ENVIRONMENTAL: 4, SOCIAL: 3, GOVERNANCE: 2 },
  offTrackByCategory: { ENVIRONMENTAL: 2, SOCIAL: 1, GOVERNANCE: 1 },
  meanProgressByCategory: { ENVIRONMENTAL: 46, SOCIAL: 58, GOVERNANCE: 71 },
};

function renderTab(node: React.ReactElement, lang: 'en' | 'ar') {
  localStorage.setItem('grc.lang', lang);
  return render(
    <MemoryRouter><ThemeProvider><I18nProvider><ToastProvider>
      {node}
    </ToastProvider></I18nProvider></ThemeProvider></MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
  listCma.mockResolvedValue(page([CMA_ROW]));
  cmaReadiness.mockResolvedValue(CMA_READINESS);
  listTcfd.mockResolvedValue(page([TCFD_ROW]));
  tcfdReadiness.mockResolvedValue(TCFD_READINESS);
  listClimateItems.mockResolvedValue(page([]));
  listMateriality.mockResolvedValue(page([TOPIC]));
  generateMatrix.mockResolvedValue(MATRIX);
  listMetrics.mockResolvedValue(page([METRIC]));
  metricsAggregate.mockResolvedValue(AGGREGATE);
});

describe('F2-16 tranche 2 — CmaTab (CMA Articles 91–94)', () => {
  it('renders the disclosure returned by the API, with its article grouping and evidence', async () => {
    renderTab(<CmaTab />, 'en');
    expect(await screen.findByText('Board sustainability oversight statement')).toBeTruthy();
    // Grouping and the `articleLabel` helper are both real, so this proves the row was placed by
    // article rather than merely listed.
    expect(screen.getByText('Article 92')).toBeTruthy();
    expect(screen.getByText('Art. 92(a)')).toBeTruthy();
    expect(screen.getByText('EV-CMA-4417')).toBeTruthy();
    expect(screen.getByText(/Committee charter does not yet name/)).toBeTruthy();
  });

  it('renders the Arabic twin of every field it shows in English', async () => {
    renderTab(<CmaTab />, 'ar');
    expect(await screen.findByText('بيان إشراف المجلس على الاستدامة')).toBeTruthy();
    expect(screen.getByText('المادة 92')).toBeTruthy();          // the group heading
    expect(screen.getByText('المادة 92(a)')).toBeTruthy();       // articleLabel(…, 'ar')
    expect(screen.getByText('جاهزية الإفصاح')).toBeTruthy();     // the readiness KPI label
    expect(screen.getByText(/لا يسمّي ميثاق اللجنة/)).toBeTruthy();
    // The English text must be gone, not merely also-present — otherwise the tab is rendering both.
    expect(screen.queryByText('Board sustainability oversight statement')).toBeNull();
  });

  it('takes its readiness percentage from the payload, and asks for the 2025 reporting year', async () => {
    renderTab(<CmaTab />, 'en');
    // 73% is not a figure the component can derive or default to — `?? 0` is the alternative.
    expect(await screen.findByText('73%')).toBeTruthy();
    expect(listCma).toHaveBeenCalledWith(expect.objectContaining({ year: 2025 }));
    expect(cmaReadiness).toHaveBeenCalledWith(2025);
  });

  it('survives a dead disclosures endpoint — the per-call catch is a contract, not a coincidence', async () => {
    listCma.mockRejectedValue(new Error('500 from /api/sustainability/cma'));
    renderTab(<CmaTab />, 'en');
    expect(await screen.findByText('No disclosures for this year')).toBeTruthy();
    expect(screen.getByText('73%')).toBeTruthy();   // readiness loaded independently
  });
});

describe('F2-16 tranche 2 — TcfdTab (TCFD alignment)', () => {
  it('renders the recommendation under its pillar heading', async () => {
    renderTab(<TcfdTab />, 'en');
    expect(await screen.findByText('Resilience of the strategy under 2°C scenarios')).toBeTruthy();
    expect(screen.getByText('(S-c)')).toBeTruthy();
    expect(screen.getByText('Strategy')).toBeTruthy();           // tcfdPillarLabel, real
  });

  it('renders in Arabic, pillar heading included', async () => {
    renderTab(<TcfdTab />, 'ar');
    expect(await screen.findByText('مرونة الاستراتيجية في سيناريو درجتين مئويتين')).toBeTruthy();
    expect(screen.getByText('محاذاة TCFD')).toBeTruthy();
    expect(screen.queryByText('Resilience of the strategy under 2°C scenarios')).toBeNull();
  });

  it('takes its alignment percentage from the payload and asks for 2025', async () => {
    renderTab(<TcfdTab />, 'en');
    expect(await screen.findByText('41%')).toBeTruthy();
    expect(listTcfd).toHaveBeenCalledWith(expect.objectContaining({ year: 2025 }));
    expect(tcfdReadiness).toHaveBeenCalledWith(2025);
  });

  it('survives a dead recommendations endpoint', async () => {
    listTcfd.mockRejectedValue(new Error('503'));
    renderTab(<TcfdTab />, 'en');
    expect(await screen.findByText('No recommendations')).toBeTruthy();
    expect(screen.getByText('41%')).toBeTruthy();
  });

  it('survives the climate-risk picker failing — a THIRD independent read on this tab', async () => {
    // TcfdTab is the only one of the four that fans out to a second module (`climateRisk`) purely to
    // populate a picker. A failure there must not cost the disclosure matrix.
    listClimateItems.mockRejectedValue(new Error('403'));
    renderTab(<TcfdTab />, 'en');
    expect(await screen.findByText('Resilience of the strategy under 2°C scenarios')).toBeTruthy();
  });
});

describe('F2-16 tranche 2 — MaterialityTab (double materiality)', () => {
  it('renders the topic with both materiality axes and its computed tier', async () => {
    renderTab(<MaterialityTab />, 'en');
    expect(await screen.findByText('Water stress in Riyadh operations')).toBeTruthy();
    expect(screen.getByText('MT-ENV-014')).toBeTruthy();
    // Both axes are independent columns — the whole point of double materiality (S-Q).
    expect(screen.getByText('Impact')).toBeTruthy();
    expect(screen.getByText('Financial')).toBeTruthy();
  });

  it('renders in Arabic', async () => {
    renderTab(<MaterialityTab />, 'ar');
    expect(await screen.findByText('الإجهاد المائي في عمليات الرياض')).toBeTruthy();
    expect(screen.getByText('إجمالي الموضوعات')).toBeTruthy();
    expect(screen.getByText('المالية')).toBeTruthy();            // the financial-materiality column
    expect(screen.queryByText('Water stress in Riyadh operations')).toBeNull();
  });

  it('takes its topic counts from the generated matrix and asks for 2025', async () => {
    renderTab(<MaterialityTab />, 'en');
    expect(await screen.findByText('29')).toBeTruthy();          // matrix.total
    expect(screen.getByText('12')).toBeTruthy();                 // byCategory.ENVIRONMENTAL
    expect(listMateriality).toHaveBeenCalledWith(expect.objectContaining({ year: 2025 }));
    expect(generateMatrix).toHaveBeenCalledWith(2025);
  });

  it('survives a dead topics endpoint', async () => {
    listMateriality.mockRejectedValue(new Error('500'));
    renderTab(<MaterialityTab />, 'en');
    expect(await screen.findByText('No matching topics')).toBeTruthy();
    expect(screen.getByText('29')).toBeTruthy();
  });
});

describe('F2-16 tranche 2 — MetricsTab (ESG metrics register)', () => {
  it('renders the metric with baseline, current and target from the payload', async () => {
    renderTab(<MetricsTab />, 'en');
    expect(await screen.findByText('Renewable electricity share')).toBeTruthy();
    expect(screen.getByText('ESG-ENV-021')).toBeTruthy();
    expect(screen.getByText('12')).toBeTruthy();                 // baselineValue
    expect(screen.getByText('38')).toBeTruthy();                 // currentValue
    expect(screen.getByText('65')).toBeTruthy();                 // targetValue
    expect(screen.getByText('Facilities Engineering')).toBeTruthy();
  });

  it('renders in Arabic', async () => {
    renderTab(<MetricsTab />, 'ar');
    expect(await screen.findByText('نسبة الكهرباء المتجددة')).toBeTruthy();
    expect(screen.getByText('إجمالي المؤشّرات')).toBeTruthy();
    expect(screen.queryByText('Renewable electricity share')).toBeNull();
  });

  it('takes its per-category mean progress from the aggregate and asks for 2025', async () => {
    renderTab(<MetricsTab />, 'en');
    // Three distinct percentages from one payload: a tab that wired every KPI to the same field
    // would pass a single-value assertion.
    expect(await screen.findByText('46%')).toBeTruthy();
    expect(screen.getByText('58%')).toBeTruthy();
    expect(screen.getByText('71%')).toBeTruthy();
    expect(listMetrics).toHaveBeenCalledWith(expect.objectContaining({ year: 2025 }));
    expect(metricsAggregate).toHaveBeenCalledWith(2025);
  });

  it('survives a dead metrics endpoint', async () => {
    listMetrics.mockRejectedValue(new Error('500'));
    renderTab(<MetricsTab />, 'en');
    expect(await screen.findByText('No metrics yet')).toBeTruthy();
    expect(screen.getByText('37')).toBeTruthy();                 // aggregate.total survived
  });
});

describe('F2-16 tranche 2 — the extraction itself', () => {
  it('every extracted tab mounts without its sibling tabs being present', async () => {
    // The move's real risk: a tab that quietly depended on something the page rendered around it.
    // Mounting each one alone, with no `EsgPage` shell, is what proves the boundary was real.
    for (const node of [<CmaTab key="c" />, <TcfdTab key="t" />,
                        <MaterialityTab key="m" />, <MetricsTab key="x" />]) {
      const { unmount } = renderTab(node, 'en');
      await waitFor(() => expect(screen.queryByText('Loading…')).toBeNull());
      unmount();
    }
  });
});
