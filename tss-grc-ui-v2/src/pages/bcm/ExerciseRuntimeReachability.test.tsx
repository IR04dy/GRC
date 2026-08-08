import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ApiError } from '../../lib/api';
import { I18nProvider } from '../../lib/i18n';
import { ToastProvider } from '../../lib/toast';
import { ThemeProvider } from '../../lib/theme';
import type { UserInfo } from '../../lib/auth';
import type {
  InjectView, RunDetail, RunSummary,
} from '../../lib/exerciseRuntime';
import type { ObserverDashboard } from '../../lib/exerciseObserver';
import { ExerciseRunsPage } from './ExerciseRunsPage';
import { FacilitatorConsolePage } from './FacilitatorConsolePage';
import { ObserverDashboardPage } from './ObserverDashboardPage';
import { ParticipantViewPage } from './ParticipantViewPage';

/**
 * F2-163 — the first tests for the BCM exercise runtime, written because it had **never been
 * reachable**.
 *
 * <p>`FacilitatorConsolePage` (769 lines), `ObserverDashboardPage` (742) and `ParticipantViewPage`
 * (351) are keyed by `runId`, and until this slice **nothing in the application listed a run** —
 * `listRunsForExercise` and `startRun` were imported by those three pages and by nothing else. 1,862
 * lines of built, API-wired exercise tooling could only be opened by typing a URL containing a run id.
 *
 * <h3>Why a render test, and not just the link</h3>
 * ⚙ <b>An unlinked screen is also an untested one.</b> F2-158 is the precedent: `RoleController#list`
 * answered 500 for every caller from the day it was written, and nobody noticed because `AdminPage`
 * wired a mock fallback into all three failure paths, so the screen always rendered plausible rows.
 * Adding 18 links in bulk without opening the screens would have repeated that in reverse — publishing
 * entry points to pages nobody had ever loaded. So each of the four is mounted here, at its real route,
 * against typed fixtures.
 *
 * <p>⚠ <b>Fixtures are typed against the DTOs on purpose.</b> An untyped fixture in the F2-16 work
 * invented field names that no endpoint returns and the test still passed; `as X['y']` casts throw the
 * check away. Every object below is annotated, so a DTO rename fails compilation instead of drifting.
 *
 * <p>These are reachability + smoke tests: they assert each screen mounts, resolves its `runId` from
 * the route, and renders its own content rather than a crash boundary or a permanent placeholder. They
 * are deliberately not behaviour tests for the runtime itself — that is a larger piece of work, and
 * pretending otherwise in the name would be worse than the gap.
 */

const RUN: RunSummary = {
  id: 7,
  exerciseId: 3,
  runCode: 'RUN-2026-003',
  status: 'IN_PROGRESS',
  channel: 'HYBRID',
  facilitatorUserId: 42,
  participantCount: 9,
  startedAt: '2026-08-08T09:00:00Z',
  totalInjects: 4,
  dispatchedInjects: 2,
  pendingInjects: 2,
};

const INJECT: InjectView = {
  id: 91,
  plannedTimeOffsetMinutes: 15,
  actualDispatchedAt: '2026-08-08T09:15:00Z',
  // ⚠ 'SCENARIO_UPDATE' was the first draft's guess and is not in `InjectType`. The annotation on
  // this fixture is what rejected it — the whole reason these objects are typed.
  injectType: 'ESCALATION',
  injectTextEn: 'Primary data centre has lost utility power.',
  injectTextAr: 'فقد مركز البيانات الرئيسي التغذية الكهربائية.',
  expectedActionEn: 'Declare the crisis and convene the CMT.',
  status: 'DISPATCHED',
  responseCount: 1,
};

const RUN_DETAIL: RunDetail = { summary: RUN, injects: [INJECT] };

const DASHBOARD: ObserverDashboard = {
  summary: RUN,
  injects: [INJECT],
  responsesByInject: {},
  observations: [],
  observers: [],
  currentObserverUserId: 42,
  isAssignedObserver: true,
};

const listRunsForExercise = vi.fn();
const startRun = vi.fn();
const getRun = vi.fn();
const listResponses = vi.fn();
const subscribeToRun = vi.fn();
const getObserverDashboard = vi.fn();
const listObservers = vi.fn();

// Partial mocks: the reads are replaced, every label helper and enum table stays real, so a renamed
// status or a dropped Arabic label surfaces here instead of being mocked away.
vi.mock('../../lib/exerciseRuntime', async (importOriginal) => ({
  ...await importOriginal<typeof import('../../lib/exerciseRuntime')>(),
  listRunsForExercise: (...a: unknown[]) => listRunsForExercise(...a),
  startRun: (...a: unknown[]) => startRun(...a),
  getRun: (...a: unknown[]) => getRun(...a),
  listResponses: (...a: unknown[]) => listResponses(...a),
  subscribeToRun: (...a: unknown[]) => subscribeToRun(...a),
}));

vi.mock('../../lib/exerciseObserver', async (importOriginal) => ({
  ...await importOriginal<typeof import('../../lib/exerciseObserver')>(),
  getObserverDashboard: (...a: unknown[]) => getObserverDashboard(...a),
  listObservers: (...a: unknown[]) => listObservers(...a),
}));

/** A real session rather than a mocked `lib/auth` — `hasAnyRole` reads sessionStorage. */
function signIn(roles: string[]) {
  const user: UserInfo = {
    userId: 42, username: 'bcm_manager', organisationId: 1, roles,
  };
  sessionStorage.setItem('grc.user', JSON.stringify(user));
}

function renderAt(path: string, pattern: string, element: React.ReactElement, lang = 'en') {
  localStorage.setItem('grc.lang', lang);
  return render(
    <MemoryRouter initialEntries={[path]}>
      <ThemeProvider>
        <I18nProvider>
          <ToastProvider>
            <Routes><Route path={pattern} element={element} /></Routes>
          </ToastProvider>
        </I18nProvider>
      </ThemeProvider>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  sessionStorage.clear();
  localStorage.clear();
  listRunsForExercise.mockResolvedValue([RUN]);
  startRun.mockResolvedValue(RUN);
  getRun.mockResolvedValue(RUN_DETAIL);
  listResponses.mockResolvedValue([]);
  subscribeToRun.mockReturnValue(() => {});
  getObserverDashboard.mockResolvedValue(DASHBOARD);
  listObservers.mockResolvedValue([]);
  signIn(['BCM_MANAGER', 'PLATFORM_ADMIN']);
});

describe('F2-163 — ExerciseRunsPage: the entry point the runtime never had', () => {
  it('lists the runs for the exercise in the route, and offers all three consoles', async () => {
    renderAt('/bcm/exercises/3/runs', '/bcm/exercises/:exerciseId/runs', <ExerciseRunsPage />);

    expect(await screen.findByText('RUN-2026-003')).toBeTruthy();
    // The exerciseId from the URL must reach the API call — not a hardcoded or NaN id.
    expect(listRunsForExercise).toHaveBeenCalledWith(3);
    // The three links that are the entire point of this page.
    for (const label of ['Facilitate', 'Observe', 'Participate']) {
      expect(screen.getByText(label), `${label} entry point missing`).toBeTruthy();
    }
    const hrefs = [...document.querySelectorAll('a')].map(a => a.getAttribute('href'));
    expect(hrefs).toContain('/bcm/exercises/run/7/facilitate');
    expect(hrefs).toContain('/bcm/exercises/run/7/observe');
    expect(hrefs).toContain('/bcm/exercises/run/7/participate');
  });

  it('renders the Arabic labels from the real label tables, not from the mock', async () => {
    renderAt('/bcm/exercises/3/runs', '/bcm/exercises/:exerciseId/runs', <ExerciseRunsPage />, 'ar');
    expect(await screen.findByText('RUN-2026-003')).toBeTruthy();
    // RUN_STATUS_LABELS/CHANNEL_LABELS stay real in the partial mock, so this fails if either the
    // Arabic label or the IN_PROGRESS/HYBRID key is renamed away. ⚠ Both strings were READ from
    // those tables, not written from memory — the first draft guessed 'مُدمج' for HYBRID and the
    // real label is 'مختلط'. A label assertion is a fixture and deserves the same discipline.
    expect(screen.getByText('جارٍ التنفيذ')).toBeTruthy();
    expect(screen.getByText('مختلط')).toBeTruthy();
  });

  it('surfaces the server\'s own message on a 403, instead of an empty run list', async () => {
    // ⚠ The distinction F2-158 turned on: an empty table is indistinguishable from "no runs", and
    // `BcmPage` renders mock exercises on failure. This page must not. `GET /by-exercise/{id}` is
    // role-gated, so a 403 is the likeliest failure a real user meets here.
    listRunsForExercise.mockRejectedValue(new ApiError(403, 'Access is denied'));
    renderAt('/bcm/exercises/3/runs', '/bcm/exercises/:exerciseId/runs', <ExerciseRunsPage />);

    expect(await screen.findByText('Access is denied')).toBeTruthy();
    expect(screen.queryByText('No runs for this exercise yet')).toBeNull();
  });

  it('falls back to its own wording when the failure is not an ApiError', async () => {
    // A network drop is an ordinary Error with a message like "Failed to fetch", which is not worth
    // showing a user. Pinned so the two branches stay distinguishable.
    listRunsForExercise.mockRejectedValue(new Error('Failed to fetch'));
    renderAt('/bcm/exercises/3/runs', '/bcm/exercises/:exerciseId/runs', <ExerciseRunsPage />);

    expect(await screen.findByText('Could not load exercise runs')).toBeTruthy();
    expect(screen.queryByText('Failed to fetch')).toBeNull();
  });

  it('does not call the API with a NaN id when the route param is not a number', async () => {
    renderAt('/bcm/exercises/abc/runs', '/bcm/exercises/:exerciseId/runs', <ExerciseRunsPage />);
    expect(await screen.findByText('Invalid exercise id')).toBeTruthy();
    expect(listRunsForExercise).not.toHaveBeenCalled();
  });
});

describe('F2-163 — the three runtime screens mount at their real routes', () => {
  it('FacilitatorConsolePage resolves runId from the URL and renders the run', async () => {
    renderAt('/bcm/exercises/run/7/facilitate', '/bcm/exercises/run/:runId/facilitate',
             <FacilitatorConsolePage />);

    expect(await screen.findByText(/RUN-2026-003/)).toBeTruthy();
    expect(getRun).toHaveBeenCalledWith(7);
    // The inject body is the working surface of the console; if it is absent the page rendered a
    // shell over nothing. `findAllBy`, not `findBy`: the console shows each inject in the timeline
    // AND in the dispatch panel, so a single match would be the surprising result.
    expect((await screen.findAllByText(/Primary data centre has lost utility power/)).length)
      .toBeGreaterThan(0);
  });

  it('ObserverDashboardPage mounts read-only against the observer aggregate', async () => {
    renderAt('/bcm/exercises/run/7/observe', '/bcm/exercises/run/:runId/observe',
             <ObserverDashboardPage />);

    expect(await screen.findByText(/RUN-2026-003/)).toBeTruthy();
    expect(getObserverDashboard).toHaveBeenCalledWith(7);
  });

  it('ParticipantViewPage mounts and shows the dispatched inject', async () => {
    renderAt('/bcm/exercises/run/7/participate', '/bcm/exercises/run/:runId/participate',
             <ParticipantViewPage />);

    expect(await screen.findByText(/Primary data centre has lost utility power/)).toBeTruthy();
    expect(getRun).toHaveBeenCalledWith(7);
  });

  it('all three subscribe to the live run topic — the realtime wiring is not dead code', async () => {
    // Each page opens a STOMP subscription on mount. Pinned because a run is driven in real time and
    // a silently-dropped subscription looks identical to a quiet exercise.
    for (const [path, pattern, el] of [
      ['/bcm/exercises/run/7/facilitate', '/bcm/exercises/run/:runId/facilitate', <FacilitatorConsolePage />],
      ['/bcm/exercises/run/7/observe', '/bcm/exercises/run/:runId/observe', <ObserverDashboardPage />],
      ['/bcm/exercises/run/7/participate', '/bcm/exercises/run/:runId/participate', <ParticipantViewPage />],
    ] as const) {
      vi.clearAllMocks();
      getRun.mockResolvedValue(RUN_DETAIL);
      listResponses.mockResolvedValue([]);
      subscribeToRun.mockReturnValue(() => {});
      getObserverDashboard.mockResolvedValue(DASHBOARD);
      listObservers.mockResolvedValue([]);
      renderAt(path, pattern, el);
      await screen.findAllByText(/RUN-2026-003|Primary data centre/);
      expect(subscribeToRun, `${path} did not subscribe`).toHaveBeenCalled();
      expect(subscribeToRun.mock.calls[0][0], `${path} subscribed to the wrong run`).toBe(7);
    }
  });
});
