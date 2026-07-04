import { useCallback, useEffect, useState } from 'react';
import { ListChecks, RefreshCw, Play, CheckCircle2, ChevronDown, ChevronRight, Clock } from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { socopsApi, type IrPlaybookRun, type IrPlaybookRunTask } from '../../lib/socops';

/**
 * SOC-B: run execution surface for a playbook. Lists the playbook's runs and,
 * on expand, the run's clocked/owner-assigned tasks — each completable inline.
 * Completing the last open task advances the run to COMPLETED. Rendered on the
 * playbook detail page (SocOpsDetailPage) below the template details.
 */

const RUN_STATUS_TONE: Record<string, BadgeTone> = {
  RUNNING: 'info', COMPLETED: 'success', CANCELLED: 'neutral',
};
const TASK_STATUS_TONE: Record<string, BadgeTone> = {
  PENDING: 'warning', IN_PROGRESS: 'info', DONE: 'success', SKIPPED: 'neutral',
};
const PHASE_TONE: Record<string, BadgeTone> = {
  DETECT: 'neutral', TRIAGE: 'info', CONTAIN: 'warning',
  ERADICATE: 'risk-high', RECOVER: 'info', LESSONS_LEARNED: 'success',
};

const fmt = (v: string | null) => (v ? v.slice(0, 16).replace('T', ' ') : '—');

export function PlaybookRunsPanel({ playbookId }: { playbookId: number }) {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [runs, setRuns] = useState<IrPlaybookRun[]>([]);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);
  const [openRunId, setOpenRunId] = useState<number | null>(null);
  const [tasks, setTasks] = useState<IrPlaybookRunTask[]>([]);
  const [tasksLoading, setTasksLoading] = useState(false);
  const [busyTaskId, setBusyTaskId] = useState<number | null>(null);

  const loadRuns = useCallback(async () => {
    setLoading(true);
    setErr(null);
    try {
      setRuns(await socopsApi.playbookRuns(playbookId));
    } catch (e) {
      setErr((e as Error)?.message ?? 'Failed to load runs');
    } finally {
      setLoading(false);
    }
  }, [playbookId]);

  useEffect(() => { void loadRuns(); }, [loadRuns]);

  async function openRun(runId: number) {
    if (openRunId === runId) { setOpenRunId(null); return; }   // collapse
    setOpenRunId(runId);
    setTasksLoading(true);
    try {
      const detail = await socopsApi.runGet(runId);
      setTasks(detail.tasks);
    } catch (e) {
      toast.danger((e as Error)?.message ?? 'Failed to load tasks');
      setTasks([]);
    } finally {
      setTasksLoading(false);
    }
  }

  // Apply a run-detail response returned by a task transition: refresh the
  // expanded task list AND patch the run's progress in the list.
  function applyDetail(d: { run: IrPlaybookRun; tasks: IrPlaybookRunTask[] }) {
    setTasks(d.tasks);
    setRuns(prev => prev.map(r => (r.id === d.run.id ? d.run : r)));
  }

  async function doStart(runId: number, taskId: number) {
    setBusyTaskId(taskId);
    try {
      applyDetail(await socopsApi.runStartTask(runId, taskId));
    } catch (e) {
      toast.danger((e as Error)?.message ?? 'Start failed');
    } finally {
      setBusyTaskId(null);
    }
  }

  async function doComplete(runId: number, taskId: number) {
    setBusyTaskId(taskId);
    try {
      const d = await socopsApi.runCompleteTask(runId, taskId);
      applyDetail(d);
      if (d.run.status === 'COMPLETED') {
        toast.success(isAr ? 'اكتمل تنفيذ الدليل' : 'Playbook run completed');
      }
    } catch (e) {
      toast.danger((e as Error)?.message ?? 'Complete failed');
    } finally {
      setBusyTaskId(null);
    }
  }

  return (
    <div style={{ marginTop: 16 }}>
    <Card title={isAr ? 'عمليات التنفيذ (Runs)' : 'Execution runs'} Icon={ListChecks}
      action={
        <Button variant="ghost" size="sm" onClick={() => void loadRuns()}>
          <RefreshCw size={14} /> {isAr ? 'تحديث' : 'Refresh'}
        </Button>
      }>
      {loading && (
        <div style={{ padding: 24, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>
          {isAr ? 'جارٍ التحميل…' : 'Loading…'}
        </div>
      )}
      {err && !loading && (
        <div style={{ padding: 24, textAlign: 'center', color: 'var(--danger-fg)', fontSize: 12 }}>{err}</div>
      )}
      {!loading && !err && runs.length === 0 && (
        <div style={{ padding: 24, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>
          {isAr ? 'لم يتم تفعيل هذا الدليل بعد — اضغط «تفعيل» في سجل الأدلة لإنشاء عملية بمهام.'
                : 'No runs yet — invoke this playbook from the register to spawn a run with tasks.'}
        </div>
      )}
      {!loading && !err && runs.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {runs.map(r => {
            const open = openRunId === r.id;
            const pct = r.totalTasks > 0 ? Math.round((r.completedTasks / r.totalTasks) * 100) : 0;
            return (
              <div key={r.id} style={{ border: '1px solid var(--border)', borderRadius: 'var(--r-sm)' }}>
                <button onClick={() => void openRun(r.id)}
                  style={{
                    width: '100%', display: 'flex', alignItems: 'center', gap: 10, padding: '10px 12px',
                    background: 'var(--surface-1)', border: 'none', cursor: 'pointer', textAlign: 'start',
                    borderRadius: 'var(--r-sm)',
                  }}>
                  {open ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
                  <span className="mono" style={{ fontSize: 11, color: 'var(--ink-2)' }}>#{r.id}</span>
                  <Badge tone={RUN_STATUS_TONE[r.status] ?? 'neutral'}>{r.status}</Badge>
                  <span style={{ fontSize: 12, color: 'var(--ink-2)' }}>
                    {r.completedTasks}/{r.totalTasks} {isAr ? 'مهمة' : 'tasks'} · {pct}%
                  </span>
                  <span className="mono" style={{ fontSize: 10, color: 'var(--ink-3)', marginInlineStart: 'auto' }}>
                    {fmt(r.startedAt)}{r.invokedBy ? ` · ${r.invokedBy}` : ''}
                  </span>
                </button>

                {open && (
                  <div style={{ padding: '4px 12px 12px', borderTop: '1px solid var(--border)' }}>
                    {tasksLoading && (
                      <div style={{ padding: 12, color: 'var(--ink-3)', fontSize: 12 }}>
                        {isAr ? 'جارٍ التحميل…' : 'Loading tasks…'}
                      </div>
                    )}
                    {!tasksLoading && (
                      <table className="tbl" style={{ width: '100%' }}>
                        <thead>
                          <tr>
                            <th style={{ width: 28 }}>#</th>
                            <th>{isAr ? 'المرحلة' : 'Phase'}</th>
                            <th>{isAr ? 'المهمة' : 'Task'}</th>
                            <th>{isAr ? 'المسؤول' : 'Owner'}</th>
                            <th>{isAr ? 'الاستحقاق' : 'Due'}</th>
                            <th>{isAr ? 'الحالة' : 'Status'}</th>
                            <th></th>
                          </tr>
                        </thead>
                        <tbody>
                          {tasks.map(t => (
                            <tr key={t.id}>
                              <td className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>{t.stepOrder}</td>
                              <td><Badge tone={PHASE_TONE[t.phase] ?? 'neutral'}>{t.phase}</Badge></td>
                              <td style={{ fontSize: 11 }}>
                                {isAr ? (t.titleAr ?? t.titleEn) : t.titleEn}
                                {t.isCritical && (
                                  <span style={{ marginInlineStart: 6 }}>
                                    <Badge tone="risk-high" size="sm">{isAr ? 'حرجة' : 'critical'}</Badge>
                                  </span>
                                )}
                              </td>
                              <td style={{ fontSize: 10, color: 'var(--ink-2)' }}>{t.ownerRole ?? '—'}</td>
                              <td className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>
                                <Clock size={10} style={{ verticalAlign: '-1px' }} /> {fmt(t.dueAt)}
                              </td>
                              <td><Badge tone={TASK_STATUS_TONE[t.status] ?? 'neutral'}>{t.status}</Badge></td>
                              <td style={{ whiteSpace: 'nowrap' }}>
                                {t.status === 'PENDING' && (
                                  <Button size="sm" variant="ghost" disabled={busyTaskId === t.id}
                                    onClick={() => void doStart(r.id, t.id)}>
                                    <Play size={11} /> {isAr ? 'بدء' : 'Start'}
                                  </Button>
                                )}
                                {t.status !== 'DONE' && (
                                  <Button size="sm" variant="primary" disabled={busyTaskId === t.id}
                                    onClick={() => void doComplete(r.id, t.id)}>
                                    <CheckCircle2 size={11} /> {isAr ? 'إنهاء' : 'Complete'}
                                  </Button>
                                )}
                                {t.status === 'DONE' && t.completedBy && (
                                  <span style={{ fontSize: 10, color: 'var(--ink-3)' }}>
                                    {isAr ? 'بواسطة ' : 'by '}{t.completedBy}
                                  </span>
                                )}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    )}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </Card>
    </div>
  );
}
