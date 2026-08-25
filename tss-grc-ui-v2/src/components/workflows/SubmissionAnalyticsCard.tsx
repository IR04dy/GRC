import { useMemo } from 'react';
import { Activity, BarChart3, PieChart as PieIcon, Timer, type LucideIcon } from 'lucide-react';
import { Card } from '../ui/Card';
import { Badge } from '../ui/Badge';
import { BarChart, PieChart, LineChart } from '../charts';
import { useI18n } from '../../lib/i18n';
import type { WorkflowSubmissionDto } from '../../lib/api';
import { MODULE_LABEL, type WorkflowModule } from '../../lib/workflowCatalog';

/**
 * Submission analytics — visible-at-a-glance KPIs computed entirely
 * from the already-fetched submissions list. No new endpoints.
 *
 * <p>Four widgets in one card:
 *   - Avg time-to-review (mean of reviewedAt − submittedAt) + count of
 *     unreviewed-but-open
 *   - Per-module bar chart (count submitted vs approved per module)
 *   - Per-status pie (5 slices)
 *   - 30-day submission-volume trend (line)
 *
 * <p>Hides itself when items.length === 0 so a freshly-bootstrapped
 * tenant doesn't see empty axes.
 */
const STATUS_COLOR: Record<string, string> = {
  SUBMITTED:    '#3b82f6',
  UNDER_REVIEW: '#f59e0b',
  APPROVED:     '#10b981',
  REJECTED:     '#ef4444',
  CLOSED:       '#9ca3af',
  DRAFT:        '#d1d5db',
};

export interface TtrStats {
  /** Mean (reviewedAt − submittedAt) in hours, or null when nothing has been reviewed. */
  avgHours: number | null;
  /** Number of submissions that have been reviewed (the mean's denominator). */
  count: number;
  /** Number of submissions awaiting a reviewer decision (SUBMITTED | UNDER_REVIEW). */
  openCount: number;
}

/**
 * F2-358 — `openCount` (submissions awaiting a reviewer decision) is independent of whether
 * anything has yet been reviewed. Only `avgHours` needs `reviewed.length > 0` as its denominator,
 * so the divide-by-zero guard is scoped to the mean. Computing `openCount` BEFORE that early
 * return is what stops the "Open for review" KPI reading 0 / "No pending submissions" on a fresh
 * tenant (or a SUBMITTED-only view) that holds pending-but-unreviewed submissions.
 */
export function computeTtrStats(items: WorkflowSubmissionDto[]): TtrStats {
  const openCount = items.filter(s => s.status === 'SUBMITTED' || s.status === 'UNDER_REVIEW').length;
  const reviewed = items.filter(s => s.reviewedAt);
  if (reviewed.length === 0) return { avgHours: null, count: 0, openCount };
  const totalMs = reviewed.reduce((acc, s) => {
    const sub = new Date(s.submittedAt).getTime();
    const rev = new Date(s.reviewedAt!).getTime();
    return Number.isNaN(sub) || Number.isNaN(rev) ? acc : acc + (rev - sub);
  }, 0);
  const avgHours = (totalMs / reviewed.length) / 3_600_000;
  return { avgHours, count: reviewed.length, openCount };
}

export function SubmissionAnalyticsCard({ items }: { items: WorkflowSubmissionDto[] }) {
  const { lang } = useI18n();
  const isAr = lang === 'ar';

  // ── Time-to-review average + open-for-review count ─────────────
  const ttrStats = useMemo(() => computeTtrStats(items), [items]);

  // ── Per-module breakdown ───────────────────────────────────────
  const byModule = useMemo(() => {
    const map = new Map<string, { module: string; submitted: number; approved: number }>();
    items.forEach(s => {
      const key = s.workflowModule;
      const row = map.get(key) ?? { module: key, submitted: 0, approved: 0 };
      row.submitted++;
      if (s.status === 'APPROVED') row.approved++;
      map.set(key, row);
    });
    // Sort highest-volume first; replace module codes with display labels.
    return Array.from(map.values())
      .sort((a, b) => b.submitted - a.submitted)
      .map(r => {
        const label = MODULE_LABEL[r.module as WorkflowModule];
        return {
          module: isAr && label ? label.ar : (label?.en ?? r.module),
          submitted: r.submitted,
          approved:  r.approved,
        };
      });
  }, [items, isAr]);

  // ── Per-status pie ──────────────────────────────────────────────
  const byStatus = useMemo(() => {
    const counts: Record<string, number> = {};
    items.forEach(s => { counts[s.status] = (counts[s.status] ?? 0) + 1; });
    return Object.entries(counts)
      .map(([name, value]) => ({ name, value, color: STATUS_COLOR[name] }))
      .sort((a, b) => b.value - a.value);
  }, [items]);

  // ── 30-day submission-volume trend ─────────────────────────────
  const trend = useMemo(() => {
    const days = 30;
    const buckets: { date: string; count: number }[] = [];
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    for (let i = days - 1; i >= 0; i--) {
      const d = new Date(today);
      d.setDate(d.getDate() - i);
      buckets.push({ date: d.toISOString().slice(5, 10), count: 0 });
    }
    items.forEach(s => {
      const d = s.submittedAt ? s.submittedAt.slice(0, 10) : null;
      if (!d) return;
      const key = d.slice(5);
      const bucket = buckets.find(b => b.date === key);
      if (bucket) bucket.count++;
    });
    return buckets;
  }, [items]);

  if (items.length === 0) return null;

  return (
    <Card title={isAr ? 'تحليلات السجلات' : 'Submission analytics'}
          Icon={Activity}
          action={<Badge tone="neutral">{items.length}</Badge>}>

      {/* KPI strip ────────────────────────────────────────────── */}
      <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', marginBottom: 16 }}>
        <KpiBlock
          Icon={Timer}
          label={isAr ? 'متوسط زمن المراجعة' : 'Avg time to review'}
          value={ttrStats.avgHours == null
            ? '—'
            : ttrStats.avgHours < 24
              ? `${ttrStats.avgHours.toFixed(1)} ${isAr ? 'س' : 'h'}`
              : `${(ttrStats.avgHours / 24).toFixed(1)} ${isAr ? 'ي' : 'd'}`}
          sub={ttrStats.count === 0
            ? (isAr ? 'لا توجد سجلات مراجعة بعد' : 'No reviewed submissions yet')
            : `${ttrStats.count} ${isAr ? 'تم مراجعتها' : 'reviewed'}`}/>
        <KpiBlock
          Icon={BarChart3}
          label={isAr ? 'مفتوحة للمراجعة' : 'Open for review'}
          value={String(ttrStats.openCount)}
          sub={ttrStats.openCount === 0
            ? (isAr ? 'لا توجد سجلات معلّقة' : 'No pending submissions')
            : (isAr ? 'بانتظار قرار المراجع' : 'awaiting reviewer decision')}/>
        <KpiBlock
          Icon={PieIcon}
          label={isAr ? 'الوحدات المُشاركة' : 'Active modules'}
          value={String(byModule.length)}
          sub={isAr ? 'من أصل ٩' : 'of 9'}/>
      </div>

      {/* Three-up chart grid ─────────────────────────────────── */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
        gap: 16,
      }}>
        <ChartFrame title={isAr ? 'حسب الوحدة' : 'By module'}>
          <BarChart
            data={byModule}
            xKey="module"
            series={[
              { key: 'submitted', name: isAr ? 'مُرسلة'   : 'Submitted', color: '#3b82f6' },
              { key: 'approved',  name: isAr ? 'مُعتمدة' : 'Approved',  color: '#10b981' },
            ]}
            height={180}
            legend
          />
        </ChartFrame>
        <ChartFrame title={isAr ? 'حسب الحالة' : 'By status'}>
          <PieChart data={byStatus} height={180} legend innerRadius={50} />
        </ChartFrame>
        <ChartFrame title={isAr ? 'آخر ٣٠ يوماً' : 'Last 30 days'}>
          <LineChart
            data={trend}
            xKey="date"
            series={[
              { key: 'count', name: isAr ? 'سجلات' : 'Submissions', color: '#0e5e5e' },
            ]}
            height={180}
          />
        </ChartFrame>
      </div>
    </Card>
  );
}

function KpiBlock({
  Icon, label, value, sub,
}: {
  Icon: LucideIcon;
  label: string;
  value: string;
  sub: string;
}) {
  return (
    <div style={{
      display: 'flex', flexDirection: 'column', gap: 4,
      minWidth: 140, padding: '10px 14px',
      background: 'var(--surface-2, var(--surface))',
      border: '1px solid var(--border)',
      borderRadius: 'var(--radius-md, 8px)',
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 6,
                    font: '600 11px var(--font-sans)', color: 'var(--ink-3)',
                    textTransform: 'uppercase', letterSpacing: '0.04em' }}>
        <Icon size={12} strokeWidth={2} />
        {label}
      </div>
      <div style={{ font: '700 22px var(--font-sans)', color: 'var(--ink-1)' }}>{value}</div>
      <div style={{ font: '500 11px var(--font-sans)', color: 'var(--ink-3)' }}>{sub}</div>
    </div>
  );
}

function ChartFrame({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div style={{
      display: 'flex', flexDirection: 'column', gap: 6,
      padding: '12px 14px',
      background: 'var(--surface-2, var(--surface))',
      border: '1px solid var(--border)',
      borderRadius: 'var(--radius-md, 8px)',
    }}>
      <div style={{
        font: '600 12px var(--font-sans)', color: 'var(--ink-3)',
        textTransform: 'uppercase', letterSpacing: '0.04em',
      }}>{title}</div>
      {children}
    </div>
  );
}
