// Adoption slice 4 — the LIVE counterpart to the mock ActiveWorkflowsPanel.
// Fetches the real engine WorkflowRequests raised from a module (filtered server-side by
// sourceModule) and lists them with status/priority/due + a deep link to the request detail.
// This is what closes the loop: RaiseRequestButton creates a real request; this panel shows it.
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Send, ArrowRight } from 'lucide-react';
import { Card } from '../ui/Card';
import { Badge } from '../ui/Badge';
import { useI18n } from '../../lib/i18n';
import { workflowRequestsApi, type WorkflowRequestDto } from '../../lib/workflowRequests';

const COUNTDOWN_TONE: Record<string, 'danger' | 'warning' | 'success' | 'info'> = {
  OVERDUE: 'danger', DUE_TODAY: 'warning', ON_TRACK: 'success', NONE: 'info',
};
const OPEN_STATUSES = new Set([
  'DRAFT', 'SENT', 'IN_PROGRESS', 'REOPENED', 'ESCALATED', 'OVERDUE', 'SUBMITTED', 'UNDER_REVIEW',
]);
const STATUS_LABEL: Record<string, [string, string]> = {
  DRAFT: ['مسودة', 'Draft'], SENT: ['مُرسل', 'Sent'], IN_PROGRESS: ['قيد التنفيذ', 'In progress'],
  SUBMITTED: ['مُقدَّم', 'Submitted'], UNDER_REVIEW: ['قيد المراجعة', 'Under review'],
  APPROVED: ['معتمد', 'Approved'], REJECTED: ['مرفوض', 'Rejected'], RETURNED: ['مُعاد', 'Returned'],
  ESCALATED: ['مُصعّد', 'Escalated'], OVERDUE: ['متأخر', 'Overdue'], REOPENED: ['أُعيد فتحه', 'Reopened'],
  CLOSED: ['مغلق', 'Closed'], ARCHIVED: ['مؤرشف', 'Archived'], CANCELLED: ['ملغى', 'Cancelled'],
};
function statusText(status: string, ar: boolean): string {
  const e = STATUS_LABEL[status];
  return e ? (ar ? e[0] : e[1]) : status.replace(/_/g, ' ');
}

interface Props {
  /** Module whose engine requests to show (matches RaiseRequestButton's sourceModule). */
  sourceModule: string;
  /** Optional — narrow to one anchored record (detail pages). */
  sourceRecordType?: string;
  sourceRecordId?: number;
  limit?: number;
  titleEn?: string;
  titleAr?: string;
}

/** Live "workflow requests from this module" panel — real engine data, not mockWorkflows. */
export function ModuleEngineRequestsPanel({
  sourceModule, sourceRecordType, sourceRecordId, limit = 8, titleEn, titleAr,
}: Props) {
  const { lang } = useI18n();
  const ar = lang === 'ar';
  const [rows, setRows] = useState<WorkflowRequestDto[] | null>(null);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    setRows(null); setErr(null);
    workflowRequestsApi.list(undefined, 0, limit, sourceModule)
      .then(res => {
        if (!alive) return;
        let content = res.content ?? [];
        if (sourceRecordId != null)
          content = content.filter(r => r.sourceRecordId === sourceRecordId
            && (!sourceRecordType || r.sourceRecordType === sourceRecordType));
        setRows(content);
      })
      .catch(e => { if (alive) setErr((e as Error)?.message ?? 'Failed to load'); });
    return () => { alive = false; };
  }, [sourceModule, sourceRecordType, sourceRecordId, limit]);

  const title = ar ? (titleAr ?? 'طلبات سير العمل من هذه الوحدة')
                   : (titleEn ?? 'Workflow requests from this module');
  const openCount = (rows ?? []).filter(r => OPEN_STATUSES.has(r.status)).length;

  return (
    <Card title={title} Icon={Send}
      action={rows && rows.length > 0
        ? <Badge tone="primary">{openCount} {ar ? 'مفتوح' : 'open'}</Badge> : undefined}>
      {err && <div style={{ padding: 16, color: 'var(--danger)', fontSize: 13 }}>{err}</div>}
      {!err && rows === null && (
        <div style={{ padding: 16, color: 'var(--ink-3)', fontSize: 13 }}>
          {ar ? 'جارٍ التحميل…' : 'Loading…'}
        </div>
      )}
      {!err && rows !== null && rows.length === 0 && (
        <div style={{ padding: 16, color: 'var(--ink-3)', fontSize: 13 }}>
          {ar ? 'لا توجد طلبات سير عمل من هذه الوحدة بعد.'
              : 'No engine requests raised from this module yet.'}
        </div>
      )}
      {!err && rows !== null && rows.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          {rows.map(r => (
            <Link key={r.id} to={`/workflows/requests/${r.id}`}
              style={{ textDecoration: 'none', color: 'inherit', display: 'block',
                padding: '10px 4px', borderBottom: '1px dashed var(--border)' }}>
              <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 2fr) auto auto',
                gap: 10, alignItems: 'center' }}>
                <div style={{ minWidth: 0 }}>
                  <div style={{ fontWeight: 600, fontSize: 13, color: 'var(--ink-1)',
                    whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                    <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)', marginInlineEnd: 6 }}>
                      {r.requestCode}
                    </span>
                    {(ar && r.titleAr) ? r.titleAr : r.titleEn}
                  </div>
                  <div style={{ fontSize: 11, color: 'var(--ink-3)', display: 'flex', gap: 6,
                    alignItems: 'center', marginTop: 2, flexWrap: 'wrap' }}>
                    <span>{r.requestType.replace(/_/g, ' ')}</span>
                    {r.dueDate && <span>· {ar ? 'الاستحقاق' : 'due'} {r.dueDate}</span>}
                    {r.escalationLevel != null && r.escalationLevel > 0 && (
                      <Badge tone="danger">{ar ? 'مُصعّد' : 'esc'} L{r.escalationLevel}</Badge>
                    )}
                  </div>
                </div>
                <Badge tone={r.priority === 'CRITICAL' || r.priority === 'HIGH' ? 'warning' : 'info'}>
                  {r.priority}
                </Badge>
                <Badge tone={COUNTDOWN_TONE[r.countdownStatus] ?? 'info'} dot>
                  {statusText(r.status, ar)}
                </Badge>
              </div>
            </Link>
          ))}
          <Link to="/workflows/requests" style={{ fontSize: 12, color: 'var(--primary-700)',
            padding: '8px 4px', display: 'inline-flex', alignItems: 'center', gap: 4 }}>
            {ar ? 'عرض كل الطلبات' : 'View all requests'} <ArrowRight size={12} />
          </Link>
        </div>
      )}
    </Card>
  );
}
