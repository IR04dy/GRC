import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { ChevronRight, ClipboardList } from 'lucide-react';
import { useI18n } from '../../lib/i18n';
import {
  workflowSubmissionApi, type WorkflowSubmissionDto,
} from '../../lib/api';
import { Card } from '../ui/Card';
import { Badge, type BadgeTone } from '../ui/Badge';
import { Button } from '../ui/Button';
import { WORKFLOWS } from '../../lib/workflowCatalog';

/**
 * Dashboard widget — recent workflow submissions across every module.
 *
 * <p>The dashboard is the first thing a user sees after login. This card
 * advertises the new wizard surface (chip strips + register + ledger) by
 * showing the last eight submissions with a status-count strip across the
 * top. Per-row drill-through routes to the filtered register, "View all"
 * goes to the catalog-wide register.
 *
 * <p>Sibling to {@code ModuleSubmissionsPanel} but tuned for the dashboard
 * surface: wider, Card-wrapped, with the status counts as the primary
 * triage cue (instead of the chip-style relative date used in the
 * module-page panel).
 */
const STATUS_TONE: Record<string, BadgeTone> = {
  DRAFT:        'neutral',
  SUBMITTED:    'info',
  UNDER_REVIEW: 'warning',
  APPROVED:     'success',
  REJECTED:     'danger',
  CLOSED:       'neutral',
};

export function DashboardSubmissionsCard({ limit = 8 }: { limit?: number }) {
  const { lang } = useI18n();
  const isAr = lang === 'ar';

  const [items, setItems] = useState<WorkflowSubmissionDto[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const rows = await workflowSubmissionApi.list();
        if (!cancelled) setItems(rows);
      } catch (e) {
        if (!cancelled) setError((e as Error).message);
      }
    }
    void load();
    return () => { cancelled = true; };
  }, []);

  // Status counts across the entire submission set (not just the visible
  // window) so the strip stays a useful triage signal even when limit is low.
  const counts = useMemo(() => {
    const acc = { SUBMITTED: 0, UNDER_REVIEW: 0, APPROVED: 0, REJECTED: 0, CLOSED: 0 };
    (items ?? []).forEach(s => {
      if (s.status in acc) (acc as Record<string, number>)[s.status]++;
    });
    return acc;
  }, [items]);

  const total = (items ?? []).length;
  const visibleRows = (items ?? []).slice(0, limit);

  // Hide the entire card on initial fetch failure — the dashboard is busy
  // enough that a half-broken card is noisier than no card.
  if (error || items === null) return null;
  // First-login with zero submissions: skip rendering. (Demo orgs get V126
  // seed so this branch is only hit on a freshly-bootstrapped tenant.)
  if (total === 0) return null;

  return (
    <div style={{ marginTop: 24 }}>
      <Card
        title={isAr ? 'آخر سجلات المعالجات' : 'Recent workflow submissions'}
        Icon={ClipboardList}
        action={
          <Link to="/workflows/submissions" style={{ textDecoration: 'none' }}>
            <Button variant="ghost" size="sm">
              {isAr ? `عرض الكل (${total})` : `View all (${total})`}
              <ChevronRight size={12} />
            </Button>
          </Link>
        }>
        <div style={{
          display: 'flex', gap: 12, flexWrap: 'wrap',
          marginBottom: 14, paddingBottom: 10,
          borderBottom: '1px solid var(--border)',
        }}>
          <Badge tone="info">{isAr ? 'مُرسل' : 'Submitted'}: {counts.SUBMITTED}</Badge>
          <Badge tone="warning">{isAr ? 'قيد المراجعة' : 'Under review'}: {counts.UNDER_REVIEW}</Badge>
          <Badge tone="success">{isAr ? 'مُعتمد' : 'Approved'}: {counts.APPROVED}</Badge>
          <Badge tone="danger">{isAr ? 'مرفوض' : 'Rejected'}: {counts.REJECTED}</Badge>
          <Badge tone="neutral">{isAr ? 'مغلق' : 'Closed'}: {counts.CLOSED}</Badge>
        </div>

        <ul className="wf-submissions-panel-list">
          {visibleRows.map(s => {
            const cat = WORKFLOWS.find(w => w.id === s.workflowId);
            const wizardLabel = isAr
              ? (cat?.titleAr ?? s.workflowId)
              : (cat?.titleEn ?? s.workflowId);
            return (
              <li key={s.id} className="wf-submissions-panel-row">
                <Link to={`/workflows/submissions/${s.id}`}
                      className="wf-submissions-panel-row-link"
                      title={s.summaryEn ?? s.titleEn}>
                  <span className="wf-submissions-panel-id">#{s.id}</span>
                  <span className="wf-submissions-panel-title">
                    <span className="wf-submissions-panel-wizard">
                      {wizardLabel} · {s.workflowModule}
                    </span>
                    <span className="wf-submissions-panel-headline">{s.titleEn}</span>
                  </span>
                  <Badge tone={STATUS_TONE[s.status] ?? 'neutral'}>{s.status}</Badge>
                  <span className="wf-submissions-panel-date">{formatRelative(s.submittedAt, isAr)}</span>
                </Link>
              </li>
            );
          })}
        </ul>
      </Card>
    </div>
  );
}

function formatRelative(iso: string, isAr: boolean): string {
  if (!iso) return '—';
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) return iso;
  const diffSec = Math.round((Date.now() - then) / 1000);
  if (diffSec < 60)        return isAr ? 'الآن' : 'just now';
  if (diffSec < 3600)      return isAr ? `قبل ${Math.round(diffSec / 60)} د` : `${Math.round(diffSec / 60)}m ago`;
  if (diffSec < 86400)     return isAr ? `قبل ${Math.round(diffSec / 3600)} س` : `${Math.round(diffSec / 3600)}h ago`;
  if (diffSec < 86400 * 7) return isAr ? `قبل ${Math.round(diffSec / 86400)} ي` : `${Math.round(diffSec / 86400)}d ago`;
  try { return new Date(iso).toLocaleDateString(); } catch { return iso; }
}
