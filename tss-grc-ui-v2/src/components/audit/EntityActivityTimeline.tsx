import { useEffect, useState } from 'react';
import { Activity } from 'lucide-react';
import { Card } from '../ui/Card';
import { Badge, type BadgeTone } from '../ui/Badge';
import { useI18n } from '../../lib/i18n';
import { auditLogApi, type AuditLogEntryDto } from '../../lib/api';

/**
 * Reusable activity-timeline card — renders every audit-log row for any
 * tenant-scoped entity inline on its detail page.
 *
 * <p>Originally inline in WorkflowSubmissionDetailPage (PR #24); extracted
 * here so AarDetailPage, RiskDetailPage, FindingDetailPage, etc. can drop
 * it in with just an {@code entityType} prop.
 *
 * <p>Backend endpoint: {@code GET /api/v1/audit-log/history?entityType=...&entityId=...}
 * Tenant-scoped, newest first, returns top 50.
 *
 * <p>Hides itself on empty / fetch error so a freshly-created entity with
 * no audit history yet doesn't show a phantom card.
 */
export interface EntityActivityTimelineProps {
  /** Entity type as written to {@code platform.audit_log.entity_type}.
   *  Examples: "WorkflowSubmission", "CrisisAar", "Risk", "Finding". */
  entityType: string;
  /** Tenant-scoped primary key of the entity. */
  entityId: number;
  /** Override the default title ("Activity timeline" / "سجل النشاط"). */
  titleEn?: string;
  titleAr?: string;
}

const ACTION_TONE: Record<string, BadgeTone> = {
  CREATE:       'info',
  GENERATE:     'info',
  UPDATE:       'info',
  APPROVED:     'success',
  APPROVE:      'success',
  PUBLISH:      'success',
  REJECTED:     'danger',
  REJECT:       'danger',
  RESCIND:      'danger',
  CLOSED:       'neutral',
  CLOSE:        'neutral',
  CANCELLED:    'neutral',
  DELETE:       'danger',
  TRANSITION:   'warning',
  UNDER_REVIEW: 'warning',
  SUBMIT:       'warning',
  // CAPA-stage transitions on AuditFinding (PR #44). actionType encodes
  // the test result, not just the stage, so PASS rows render green and
  // FAIL rows render red — without these the rework loop would be
  // invisible (owner FAIL keeps stage at IMPLEMENTING).
  IMPLEMENTING:           'info',
  OWNER_TESTED:           'info',
  INDEPENDENT_TESTED:     'info',
  AUDIT_SIGNED_OFF:       'warning',
  OWNER_TEST_PASS:        'success',
  OWNER_TEST_PARTIAL:     'warning',
  OWNER_TEST_FAIL:        'danger',
  INDEPENDENT_TEST_PASS:  'success',
  INDEPENDENT_TEST_PARTIAL: 'warning',
  INDEPENDENT_TEST_FAIL:  'danger',
};

export function EntityActivityTimeline({
  entityType, entityId, titleEn, titleAr,
}: EntityActivityTimelineProps) {
  const { lang } = useI18n();
  const isAr = lang === 'ar';

  const [entries, setEntries] = useState<AuditLogEntryDto[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const rows = await auditLogApi.historyFor(entityType, entityId);
        if (!cancelled) setEntries(rows);
      } catch (e) {
        if (!cancelled) setError((e as Error).message);
      }
    }
    void load();
    return () => { cancelled = true; };
  }, [entityType, entityId]);

  if (error || entries === null) return null;
  if (entries.length === 0) return null;

  return (
    <Card title={isAr ? (titleAr ?? 'سجل النشاط') : (titleEn ?? 'Activity timeline')}
          Icon={Activity}
          action={<Badge tone="neutral">{entries.length}</Badge>}>
      <ol style={{ listStyle: 'none', margin: 0, padding: 0,
                   display: 'flex', flexDirection: 'column', gap: 8 }}>
        {entries.map(e => (
          <li key={e.id} style={{
            display: 'grid',
            gridTemplateColumns: '160px 110px 1fr',
            gap: 12,
            padding: '8px 0',
            borderBottom: '1px dashed var(--border)',
            alignItems: 'baseline',
          }}>
            <span style={{ font: '500 12px var(--font-mono, var(--font-sans))',
                           color: 'var(--ink-3)' }}>
              {formatDate(e.createdAt)}
            </span>
            <Badge tone={ACTION_TONE[e.actionType] ?? 'neutral'}>{e.actionType}</Badge>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
              <span style={{ font: '500 13px var(--font-sans)', color: 'var(--ink-1)' }}>
                {e.message ?? '—'}
              </span>
              {(e.userUsername || e.userRole) && (
                <span style={{ font: '500 11px var(--font-sans)', color: 'var(--ink-3)' }}>
                  {[e.userUsername, e.userRole].filter(Boolean).join(' · ')}
                </span>
              )}
            </div>
          </li>
        ))}
      </ol>
    </Card>
  );
}

function formatDate(iso: string): string {
  try { return new Date(iso).toLocaleString(); } catch { return iso; }
}
