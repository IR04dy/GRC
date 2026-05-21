import { useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  CheckCheck, ChevronLeft, ClipboardCheck, ClipboardList, Copy, ExternalLink,
  FileText, PauseCircle, X as XIcon,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { LoadableSection } from '../../components/ui/LoadableSection';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { useLoadable } from '../../lib/useLoadable';
import {
  workflowSubmissionApi, type WorkflowSubmissionDto,
} from '../../lib/api';
import { WORKFLOWS, MODULE_LABEL } from '../../lib/workflowCatalog';

/**
 * Single-submission detail page at /workflows/submissions/:id.
 *
 * <p>Gives every workflow_submission a shareable URL. The register
 * (/workflows/submissions) shows them as collapsed cards — this page is
 * the canonical "open this submission" view: richer metadata, full
 * payload pretty-printed, review actions, link back into the wizard
 * route, and a copy-link button for sharing with reviewers.
 *
 * <p>Mirrors the AarDetailPage shape so the design system stays
 * consistent across the two detail surfaces (AAR + Submission).
 */
const STATUS_TONE: Record<string, BadgeTone> = {
  DRAFT:        'neutral',
  SUBMITTED:    'info',
  UNDER_REVIEW: 'warning',
  APPROVED:     'success',
  REJECTED:     'danger',
  CLOSED:       'neutral',
};

export function WorkflowSubmissionDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const navigate = useNavigate();

  const submissionId = Number(id);
  // useLoadable captures the producer in a ref, so when React Router
  // remounts this page with a different `:id` the next reload uses the
  // fresh closure. No explicit deps array needed.
  const { data, loading, error, reload } = useLoadable<WorkflowSubmissionDto>(
    () => workflowSubmissionApi.get(submissionId),
  );

  const [busy, setBusy] = useState(false);
  const [reviewNote, setReviewNote] = useState('');

  async function act(decision: 'APPROVE' | 'REJECT' | 'CLOSE' | 'UNDER_REVIEW') {
    if (!data) return;
    setBusy(true);
    try {
      await workflowSubmissionApi.review(data.id, { decision, reviewerNote: reviewNote || undefined });
      const verbEn = decision === 'APPROVE' ? 'approved'
                  : decision === 'REJECT'   ? 'rejected'
                  : decision === 'CLOSE'    ? 'closed'
                  : 'moved to under-review';
      const verbAr = decision === 'APPROVE' ? 'اعتُمد'
                  : decision === 'REJECT'   ? 'رُفض'
                  : decision === 'CLOSE'    ? 'أُغلق'
                  : 'تحت المراجعة';
      toast.success(isAr
        ? `السجل ${verbAr}`
        : `Submission ${verbEn}`);
      reload();
    } catch (e) {
      toast.danger((e as Error).message);
    } finally { setBusy(false); }
  }

  async function copyLink() {
    try {
      await navigator.clipboard.writeText(window.location.href);
      toast.success(isAr ? 'تم نسخ الرابط' : 'Link copied');
    } catch (e) {
      toast.danger((e as Error).message);
    }
  }

  return (
    <Shell>
      <PageHeader
        eyebrow={isAr ? 'مركز المعالجات / سجل' : 'Workflow Hub / Submission'}
        title={data ? `#${data.id}` : `#${submissionId}`}
        sub={data?.titleEn ?? ''}
        actions={
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
            <Link to="/workflows/submissions">
              <Button variant="ghost" size="sm" Icon={ChevronLeft}>
                {isAr ? 'كل السجلات' : 'All submissions'}
              </Button>
            </Link>
            <Button variant="ghost" size="sm" Icon={Copy} onClick={copyLink}>
              {isAr ? 'نسخ الرابط' : 'Copy link'}
            </Button>
          </div>
        }
      />

      <LoadableSection loading={loading} error={error} onRetry={reload} data={data}>
        {(s) => <SubmissionBody s={s}
                                isAr={isAr}
                                busy={busy}
                                reviewNote={reviewNote}
                                onReviewNote={setReviewNote}
                                onAct={act}
                                onNavigate={navigate}/>}
      </LoadableSection>
    </Shell>
  );
}

// ── Body — kept as a function to make LoadableSection happy ─────────

function SubmissionBody({
  s, isAr, busy, reviewNote, onReviewNote, onAct, onNavigate,
}: {
  s: WorkflowSubmissionDto;
  isAr: boolean;
  busy: boolean;
  reviewNote: string;
  onReviewNote: (v: string) => void;
  onAct: (decision: 'APPROVE' | 'REJECT' | 'CLOSE' | 'UNDER_REVIEW') => void | Promise<void>;
  onNavigate: ReturnType<typeof useNavigate>;
}) {
  const cat = useMemo(() => WORKFLOWS.find(w => w.id === s.workflowId), [s.workflowId]);
  const moduleLabel = MODULE_LABEL[s.workflowModule as keyof typeof MODULE_LABEL];
  const wizardLabel = isAr ? (cat?.titleAr ?? s.workflowId) : (cat?.titleEn ?? s.workflowId);
  const isOpen = s.status === 'SUBMITTED' || s.status === 'UNDER_REVIEW';

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>

      {/* Hero envelope card */}
      <Card title={wizardLabel}
            Icon={cat?.Icon ?? ClipboardCheck}
            action={(
              <div style={{ display: 'flex', gap: 6 }}>
                <Badge tone={STATUS_TONE[s.status] ?? 'neutral'}>{s.status}</Badge>
                <Badge tone="neutral">
                  {isAr && moduleLabel ? moduleLabel.ar : (moduleLabel?.en ?? s.workflowModule)}
                </Badge>
              </div>
            )}>
        <Row k={isAr ? 'العنوان' : 'Title'} v={s.titleEn} />
        {s.titleAr && <Row k={isAr ? 'العنوان (عربي)' : 'Title (Arabic)'} v={s.titleAr} />}
        <Row k={isAr ? 'الملخص' : 'Summary'} v={s.summaryEn ?? '—'} wrap />
        {s.regulatoryAnchor && (
          <Row k={isAr ? 'المرجع التنظيمي' : 'Regulatory anchor'}
               v={<Badge tone="info">{s.regulatoryAnchor}</Badge>}/>
        )}
        <Row k={isAr ? 'وقت الإرسال' : 'Submitted at'} v={formatDate(s.submittedAt)} />
        {s.reviewedAt && <Row k={isAr ? 'وقت المراجعة' : 'Reviewed at'} v={formatDate(s.reviewedAt)} />}
        {s.reviewerNote && <Row k={isAr ? 'ملاحظة المراجع' : 'Reviewer note'} v={s.reviewerNote} wrap />}

        <div style={{ display: 'flex', gap: 8, marginTop: 16, flexWrap: 'wrap' }}>
          {cat?.route && (
            <Button variant="secondary" size="sm" Icon={ExternalLink}
                    onClick={() => onNavigate(cat.route!)}>
              {isAr ? 'فتح المعالج لإنشاء سجل جديد' : 'Open wizard for a new submission'}
            </Button>
          )}
          <Link to={`/workflows/submissions?workflowId=${encodeURIComponent(s.workflowId)}`}>
            <Button variant="ghost" size="sm" Icon={ClipboardList}>
              {isAr ? 'كل سجلات هذا المعالج' : 'All submissions for this wizard'}
            </Button>
          </Link>
        </div>
      </Card>

      {/* Review controls */}
      {isOpen && (
        <Card title={isAr ? 'إجراءات المراجعة' : 'Review actions'} Icon={CheckCheck}>
          <div className="wf-form-grid">
            <label className="wf-field wf-field-full">
              <span className="wf-label">{isAr ? 'ملاحظة المراجع (اختيارية)' : 'Reviewer note (optional)'}</span>
              <input className="input" value={reviewNote}
                     onChange={e => onReviewNote(e.target.value)}
                     placeholder={isAr ? 'سبب القرار…' : 'Reason for the decision…'}/>
            </label>
            <div style={{ display: 'flex', gap: 8, gridColumn: '1 / -1', flexWrap: 'wrap' }}>
              <Button variant="primary" Icon={CheckCheck}
                      disabled={busy} onClick={() => onAct('APPROVE')}>
                {isAr ? 'اعتماد' : 'Approve'}
              </Button>
              <Button variant="danger" Icon={XIcon}
                      disabled={busy} onClick={() => onAct('REJECT')}>
                {isAr ? 'رفض' : 'Reject'}
              </Button>
              {s.status === 'SUBMITTED' && (
                <Button variant="secondary" Icon={PauseCircle}
                        disabled={busy} onClick={() => onAct('UNDER_REVIEW')}>
                  {isAr ? 'وضع قيد المراجعة' : 'Move to under-review'}
                </Button>
              )}
              <Button variant="ghost" Icon={PauseCircle}
                      disabled={busy} onClick={() => onAct('CLOSE')}>
                {isAr ? 'إغلاق' : 'Close'}
              </Button>
            </div>
          </div>
        </Card>
      )}

      {/* Full payload */}
      <Card title={isAr ? 'الحمولة الكاملة' : 'Full payload'} Icon={FileText}>
        <pre className="ai-json-pre" style={{ maxHeight: 'none' }}>
          {prettyJson(s.payloadJson)}
        </pre>
      </Card>
    </div>
  );
}

// ── Tiny helpers ─────────────────────────────────────────────────────

function Row({ k, v, wrap }: { k: string; v: React.ReactNode; wrap?: boolean }) {
  return (
    <div style={{
      display: 'grid', gridTemplateColumns: '180px 1fr',
      gap: 12, padding: '6px 0',
      borderBottom: '1px dashed var(--border)',
      alignItems: wrap ? 'flex-start' : 'center',
    }}>
      <span style={{ font: '600 12px var(--font-sans)', color: 'var(--ink-3)',
                     textTransform: 'uppercase', letterSpacing: '0.04em' }}>{k}</span>
      <span style={{
        font: '500 13px var(--font-sans)', color: 'var(--ink-1)',
        whiteSpace: wrap ? 'pre-wrap' : 'nowrap',
        overflow: wrap ? 'visible' : 'hidden',
        textOverflow: wrap ? 'clip' : 'ellipsis',
      }}>{v}</span>
    </div>
  );
}

function prettyJson(raw: string): string {
  try { return JSON.stringify(JSON.parse(raw), null, 2); }
  catch { return raw; }
}

function formatDate(iso: string): string {
  try { return new Date(iso).toLocaleString(); } catch { return iso; }
}
