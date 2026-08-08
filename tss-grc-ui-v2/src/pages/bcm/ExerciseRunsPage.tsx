import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Activity, Eye, Play, Radio, Users } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { EmptyState } from '../../components/ui/EmptyState';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import {
  listRunsForExercise, startRun,
  RUN_STATUS_LABELS, CHANNEL_LABELS,
  type RunStatus, type RunSummary,
} from '../../lib/exerciseRuntime';

/**
 * F2-163 — the entry point the BCM exercise runtime never had.
 *
 * <p>`FacilitatorConsolePage` (769 lines), `ObserverDashboardPage` (742) and
 * `ParticipantViewPage` (351) are keyed by `runId`, and **nothing in the application listed
 * a run** — `listRunsForExercise` and `startRun` were imported by those three pages and by
 * nothing else, so the only way to open 1,862 lines of built, API-wired exercise tooling was
 * to type a URL containing a run id. This page is the missing list: exercise → its runs →
 * the three role-specific consoles.
 *
 * <p>🔴 <b>Why this mattered more than a missing link usually does.</b> {@link
 * ../../../../TSS-GRC-Platform-Bible/Registers/FINDINGS_REGISTER_V2.md F2-161} converted the
 * runtime's authorisation two days before the gap was measured, so a facilitator could drive
 * their own run instead of only a platform administrator. ⚙ The authorisation was corrected
 * on three screens that had no entry point: a guard audit cannot tell that nothing routes to
 * a page, and a navigation audit cannot tell that its guard was wrong.
 *
 * <h3>⚠ No mock fallback, deliberately</h3>
 * `BcmPage` reads exercises through `useApiList(..., BCM_EXERCISES)` and renders the mock
 * array when the call fails. F2-158 is what that habit costs: `RoleController#list` answered
 * 500 for every caller from the day it was written and nobody noticed, because the page had a
 * client-side fallback that always rendered plausible rows. A read failure here says so.
 *
 * <h3>⚠ Starting a run is `BCM_MANAGER`/`PLATFORM_ADMIN`, and `BCM_MANAGER` has no users</h3>
 * `POST /api/v1/bcm/exercise-runs` still carries a bare `hasAnyRole` list. F2-161 measured
 * `BCM_MANAGER` at **0 users** in dev, so in practice only the platform administrator can
 * start a run. The 403 is surfaced verbatim rather than hidden — the button is the honest
 * place to discover it.
 */
export function ExerciseRunsPage() {
  const { exerciseId } = useParams<{ exerciseId: string }>();
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const nav = useNavigate();

  const [runs, setRuns] = useState<RunSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [starting, setStarting] = useState(false);

  const id = Number(exerciseId);

  const reload = useCallback(async () => {
    if (!Number.isFinite(id)) {
      setError(isAr ? 'معرّف تمرين غير صالح' : 'Invalid exercise id');
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      setRuns(await listRunsForExercise(id));
    } catch (e) {
      setError(e instanceof ApiError
        ? e.message
        : (isAr ? 'تعذّر تحميل جولات التمرين' : 'Could not load exercise runs'));
    } finally {
      setLoading(false);
    }
  }, [id, isAr]);

  useEffect(() => { void reload(); }, [reload]);

  async function onStart() {
    setStarting(true);
    try {
      const run = await startRun({ exerciseId: id });
      toast.success(isAr
        ? `بدأت الجولة ${run.runCode}`
        : `Run ${run.runCode} started`);
      nav(`/bcm/exercises/run/${run.id}/facilitate`);
    } catch (e) {
      toast.danger(e instanceof ApiError
        ? e.message
        : (isAr ? 'تعذّر بدء الجولة' : 'Could not start the run'));
    } finally {
      setStarting(false);
    }
  }

  const STATUS_TONE: Record<RunStatus, 'success' | 'info' | 'warning' | 'neutral' | 'danger'> = {
    SCHEDULED: 'info', IN_PROGRESS: 'success', PAUSED: 'warning',
    COMPLETED: 'neutral', ABORTED: 'danger',
  };

  return (
    <Shell>
      <PageHeader
        backTo="/bcm?tab=exercises"
        eyebrow={isAr ? `تمرين ${exerciseId}` : `Exercise ${exerciseId}`}
        title={isAr ? 'جولات التمرين' : 'Exercise runs'}
        sub={isAr
          ? 'افتح وحدة التحكّم للميسّر، أو لوحة المراقب، أو شاشة المشارك لأي جولة.'
          : 'Open the facilitator console, the observer dashboard or the participant view for a run.'}
        actions={
          <Button variant="primary" Icon={Play} onClick={onStart} disabled={starting}>
            {starting
              ? (isAr ? 'جارٍ البدء…' : 'Starting…')
              : (isAr ? 'بدء جولة' : 'Start a run')}
          </Button>
        }
      />

      <Card padded title={isAr ? 'الجولات' : 'Runs'} Icon={Activity}>
        {loading && <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>}

        {!loading && error && (
          <InlineEmpty>
            {/* Surfaced, not swallowed: an empty table would read as "no runs". */}
            <span style={{ color: 'var(--danger-fg)' }}>{error}</span>
          </InlineEmpty>
        )}

        {!loading && !error && runs.length === 0 && (
          <EmptyState
            Icon={Activity}
            title={isAr ? 'لا توجد جولات لهذا التمرين' : 'No runs for this exercise yet'}
            sub={isAr
              ? 'ابدأ جولة لتشغيل التمرين وإرسال الحقن للمشاركين.'
              : 'Start a run to drive the exercise and dispatch injects to participants.'} />
        )}

        {!loading && !error && runs.length > 0 && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            {runs.map(r => (
              <div key={r.id} style={{
                display: 'grid',
                gridTemplateColumns: 'minmax(140px, 1fr) 120px 120px 110px auto',
                gap: 12, alignItems: 'center', padding: '10px 12px',
                border: '1px solid var(--border)', borderRadius: 'var(--r-md)',
                background: 'var(--surface-2)',
              }}>
                <div>
                  <div className="mono" style={{ fontWeight: 600 }}>{r.runCode}</div>
                  <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }}>
                    {r.startedAt ?? (isAr ? 'لم تبدأ' : 'not started')}
                  </div>
                </div>
                <Badge tone={STATUS_TONE[r.status] ?? 'neutral'} dot>
                  {isAr ? RUN_STATUS_LABELS[r.status].ar : RUN_STATUS_LABELS[r.status].en}
                </Badge>
                <Badge tone="info">
                  {isAr ? CHANNEL_LABELS[r.channel].ar : CHANNEL_LABELS[r.channel].en}
                </Badge>
                <span className="mono" style={{ fontSize: 12, color: 'var(--ink-2)' }}>
                  {r.dispatchedInjects}/{r.totalInjects}
                </span>
                <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end' }}>
                  {/* The three routes F2-163 found stranded. */}
                  <Link to={`/bcm/exercises/run/${r.id}/facilitate`} className="link">
                    <Button size="sm" variant="primary" Icon={Radio}>
                      {isAr ? 'إدارة' : 'Facilitate'}
                    </Button>
                  </Link>
                  <Link to={`/bcm/exercises/run/${r.id}/observe`} className="link">
                    <Button size="sm" variant="secondary" Icon={Eye}>
                      {isAr ? 'مراقبة' : 'Observe'}
                    </Button>
                  </Link>
                  <Link to={`/bcm/exercises/run/${r.id}/participate`} className="link">
                    <Button size="sm" variant="ghost" Icon={Users}>
                      {isAr ? 'مشاركة' : 'Participate'}
                    </Button>
                  </Link>
                </div>
              </div>
            ))}
          </div>
        )}
      </Card>
    </Shell>
  );
}
