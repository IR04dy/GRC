import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  CheckCheck, ChevronRight, ClipboardCheck, Download, FileText, Filter,
  PauseCircle, Workflow as WorkflowIcon, X as XIcon,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { LoadableSection } from '../../components/ui/LoadableSection';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  workflowSubmissionApi, type WorkflowSubmissionDto,
} from '../../lib/api';
import {
  WORKFLOWS, MODULE_LABEL, type WorkflowModule,
} from '../../lib/workflowCatalog';
import { downloadCsv, stampFilename, type ExportRow } from '../../lib/clientExport';

/**
 * Workflow Submissions register.
 *
 * <p>Every wizard submit lands here (via {@code POST /api/v1/workflow-submissions}).
 * The page lets a reviewer:
 *   - Filter by module (9 buckets) and by workflow_id (24 wizards)
 *   - See the computed summary + regulatory anchor at a glance
 *   - Inspect the full JSON payload in a collapsible details element
 *   - Approve / Reject / Close a SUBMITTED or UNDER_REVIEW row inline
 *
 * <p>Closes the loop opened by PR #13 — without this page the persisted
 * rows are invisible to the user once the wizard's toast clears.
 */
type ModuleFilter = WorkflowModule | 'ALL';

const STATUS_TONE: Record<string, BadgeTone> = {
  DRAFT:        'neutral',
  SUBMITTED:    'info',
  UNDER_REVIEW: 'warning',
  APPROVED:     'success',
  REJECTED:     'danger',
  CLOSED:       'neutral',
};

export function WorkflowSubmissionsPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [moduleFilter, setModuleFilter] = useState<ModuleFilter>('ALL');
  const [workflowFilter, setWorkflowFilter] = useState<string>('ALL');
  const [items, setItems] = useState<WorkflowSubmissionDto[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [reviewNote, setReviewNote] = useState<Record<number, string>>({});

  // Workflow id options for the second filter — narrowed when a module is
  // selected so reviewers don't see irrelevant catalog entries.
  const workflowOptions = useMemo(() =>
    WORKFLOWS.filter(w => moduleFilter === 'ALL' || w.module === moduleFilter),
  [moduleFilter]);

  async function reload() {
    setLoading(true); setError(null);
    try {
      const rows = await workflowSubmissionApi.list(
        workflowFilter === 'ALL' ? undefined : workflowFilter,
        // Only send module if no specific workflow is chosen; workflow_id
        // is the more-specific filter and the API treats them as exclusive.
        workflowFilter === 'ALL' && moduleFilter !== 'ALL' ? moduleFilter : undefined,
      );
      setItems(rows);
    } catch (e) {
      setError((e as Error).message);
    } finally { setLoading(false); }
  }
  useEffect(() => { void reload(); /* eslint-disable-next-line */ }, [moduleFilter, workflowFilter]);

  async function review(id: number, decision: 'APPROVE' | 'REJECT' | 'CLOSE') {
    setBusyId(id);
    try {
      await workflowSubmissionApi.review(id, {
        decision,
        reviewerNote: reviewNote[id],
      });
      toast.success(isAr
        ? `تم ${decision === 'APPROVE' ? 'اعتماد' : decision === 'REJECT' ? 'رفض' : 'إغلاق'} السجل`
        : `Submission ${decision.toLowerCase()}d`);
      void reload();
    } catch (e) {
      toast.danger((e as Error).message);
    } finally { setBusyId(null); }
  }

  const counts = useMemo(() => {
    if (!items) return { submitted: 0, underReview: 0, approved: 0, rejected: 0, closed: 0 };
    return {
      submitted:   items.filter(s => s.status === 'SUBMITTED').length,
      underReview: items.filter(s => s.status === 'UNDER_REVIEW').length,
      approved:    items.filter(s => s.status === 'APPROVED').length,
      rejected:    items.filter(s => s.status === 'REJECTED').length,
      closed:      items.filter(s => s.status === 'CLOSED').length,
    };
  }, [items]);

  /** Export the currently-filtered rows as CSV. Regulator-visible columns
   *  only; the full JSON payload is intentionally excluded (export is for
   *  offline review + audit retention, not data re-import). */
  function exportCsv() {
    if (!items || items.length === 0) return;
    const rows: ExportRow[] = items.map(s => {
      const cat = WORKFLOWS.find(w => w.id === s.workflowId);
      return {
        id:                 s.id,
        workflow_id:        s.workflowId,
        workflow_name:      cat?.titleEn ?? s.workflowId,
        workflow_name_ar:   cat?.titleAr ?? '',
        module:             s.workflowModule,
        title:              s.titleEn,
        title_ar:           s.titleAr ?? '',
        status:             s.status,
        regulatory_anchor:  s.regulatoryAnchor ?? '',
        summary:            s.summaryEn ?? '',
        summary_ar:         s.summaryAr ?? '',
        submitted_at:       s.submittedAt,
        reviewed_at:        s.reviewedAt ?? '',
        reviewer_note:      s.reviewerNote ?? '',
      };
    });
    // Stamp the filename so the file name itself records what filter the
    // export was taken under — auditors expect "I downloaded this slice".
    const suffix = workflowFilter !== 'ALL' ? `-${workflowFilter}`
                 : moduleFilter   !== 'ALL' ? `-${moduleFilter.toLowerCase()}`
                 : '';
    downloadCsv(rows, stampFilename(`workflow-submissions${suffix}`, 'csv'));
    toast.success(isAr
      ? `تم تصدير ${items.length} سجل بصيغة CSV`
      : `Exported ${items.length} submissions to CSV`);
  }

  return (
    <Shell>
      <PageHeader
        title={isAr ? 'سجلات المعالجات' : 'Workflow Submissions'}
        sub={isAr
          ? 'كل ما أُرسل من أي معالج — تصفية، مراجعة، اعتماد، رفض، إغلاق'
          : 'Every wizard submission across the platform — filter, review, approve, reject, close'}
        eyebrow={isAr ? 'مركز المعالجات' : 'Workflow Hub'}
        actions={
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
            <Button variant="secondary" size="sm" Icon={Download}
                    disabled={!items || items.length === 0}
                    onClick={exportCsv}>
              {isAr ? 'تصدير CSV' : 'Export CSV'}
              {items && items.length > 0 ? ` (${items.length})` : ''}
            </Button>
            <Link to="/workflows">
              <Button variant="ghost" size="sm" Icon={WorkflowIcon}>
                {isAr ? 'الكتالوج' : 'Catalog'}
              </Button>
            </Link>
          </div>
        }
      />

      {/* Filters */}
      <div className="wf-hub-filters" style={{ marginTop: 20 }}>
        <span className="wf-label" style={{ alignSelf: 'center' }}>
          <Filter size={12} style={{ display: 'inline-block', marginInlineEnd: 4 }}/>
          {isAr ? 'تصفية:' : 'Filter:'}
        </span>
        <select className="input"
                value={moduleFilter}
                onChange={e => { setModuleFilter(e.target.value as ModuleFilter); setWorkflowFilter('ALL'); }}>
          <option value="ALL">{isAr ? 'كل الوحدات' : 'All modules'}</option>
          {(Object.keys(MODULE_LABEL) as WorkflowModule[]).map(m => (
            <option key={m} value={m}>{isAr ? MODULE_LABEL[m].ar : MODULE_LABEL[m].en}</option>
          ))}
        </select>
        <select className="input"
                value={workflowFilter}
                onChange={e => setWorkflowFilter(e.target.value)}>
          <option value="ALL">{isAr ? 'كل المعالجات' : 'All wizards'}</option>
          {workflowOptions.map(w => (
            <option key={w.id} value={w.id}>
              {isAr ? w.titleAr : w.titleEn}
            </option>
          ))}
        </select>
      </div>

      {/* Stats row */}
      {items && items.length > 0 && (
        <div style={{ display: 'flex', gap: 12, margin: '16px 0', flexWrap: 'wrap' }}>
          <Badge tone="info">{isAr ? 'مُرسل' : 'Submitted'}: {counts.submitted}</Badge>
          <Badge tone="warning">{isAr ? 'قيد المراجعة' : 'Under review'}: {counts.underReview}</Badge>
          <Badge tone="success">{isAr ? 'مُعتمد' : 'Approved'}: {counts.approved}</Badge>
          <Badge tone="danger">{isAr ? 'مرفوض' : 'Rejected'}: {counts.rejected}</Badge>
          <Badge tone="neutral">{isAr ? 'مغلق' : 'Closed'}: {counts.closed}</Badge>
        </div>
      )}

      <LoadableSection
        loading={loading}
        error={error}
        onRetry={reload}
        data={items}
        emptyTitle={isAr ? 'لا سجلات بعد' : 'No submissions yet'}
        emptySub={isAr
          ? 'ابدأ من كتالوج المعالجات لإنشاء أول سجل.'
          : 'Walk a wizard from the catalog to create the first submission.'}>
        {(rows) => (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            {rows.map(s => {
              const cat = WORKFLOWS.find(w => w.id === s.workflowId);
              const isOpen = s.status === 'SUBMITTED' || s.status === 'UNDER_REVIEW';
              return (
                <Card key={s.id}
                      title={`#${s.id} · ${isAr ? (cat?.titleAr ?? s.workflowId)
                                                : (cat?.titleEn ?? s.workflowId)}`}
                      Icon={cat?.Icon ?? ClipboardCheck}
                      action={(
                        <div style={{ display: 'flex', gap: 6 }}>
                          <Badge tone={STATUS_TONE[s.status] ?? 'neutral'}>{s.status}</Badge>
                          <Badge tone="neutral">{s.workflowModule}</Badge>
                        </div>
                      )}>
                  <div className="text-xs" style={{ color: 'var(--ink-3)', marginBottom: 8 }}>
                    {formatDate(s.submittedAt)}
                    {s.regulatoryAnchor && (
                      <span style={{ marginInlineStart: 12 }}>
                        <Badge tone="info">{s.regulatoryAnchor}</Badge>
                      </span>
                    )}
                  </div>

                  <div style={{ fontWeight: 600, fontSize: 14, marginBottom: 6 }}>
                    <Link to={`/workflows/submissions/${s.id}`}
                          style={{ color: 'inherit', textDecoration: 'none' }}>
                      {s.titleEn}
                    </Link>
                  </div>
                  {s.summaryEn && (
                    <div className="wf-summary" style={{ marginBottom: 12 }}>
                      {s.summaryEn}
                    </div>
                  )}

                  <details>
                    <summary className="text-xs" style={{ cursor: 'pointer', color: 'var(--ink-3)' }}>
                      <FileText size={12} style={{ display: 'inline-block', marginInlineEnd: 4 }}/>
                      {isAr ? 'الحمولة الكاملة (JSON)' : 'Full payload (JSON)'}
                    </summary>
                    <pre className="ai-json-pre">{prettyJson(s.payloadJson)}</pre>
                  </details>

                  {isOpen ? (
                    <div className="wf-form-grid" style={{ marginTop: 12 }}>
                      <Field label={isAr ? 'ملاحظة المراجع (اختيارية)' : 'Reviewer note (optional)'} full>
                        <input className="input" value={reviewNote[s.id] ?? ''}
                               onChange={e => setReviewNote(r => ({ ...r, [s.id]: e.target.value }))}
                               placeholder={isAr ? 'سبب القرار…' : 'Reason for the decision…'}/>
                      </Field>
                      <div style={{ display: 'flex', gap: 8, gridColumn: '1 / -1', flexWrap: 'wrap' }}>
                        <Button variant="primary" Icon={CheckCheck}
                                disabled={busyId === s.id}
                                onClick={() => review(s.id, 'APPROVE')}>
                          {isAr ? 'اعتماد' : 'Approve'}
                        </Button>
                        <Button variant="danger" Icon={XIcon}
                                disabled={busyId === s.id}
                                onClick={() => review(s.id, 'REJECT')}>
                          {isAr ? 'رفض' : 'Reject'}
                        </Button>
                        <Button variant="ghost" Icon={PauseCircle}
                                disabled={busyId === s.id}
                                onClick={() => review(s.id, 'CLOSE')}>
                          {isAr ? 'إغلاق' : 'Close'}
                        </Button>
                        {cat?.route && (
                          <Link to={cat.route} style={{ marginInlineStart: 'auto' }}>
                            <Button variant="ghost" size="sm">
                              {isAr ? 'سجل آخر مماثل' : 'New submission'}
                              <ChevronRight size={12}/>
                            </Button>
                          </Link>
                        )}
                      </div>
                    </div>
                  ) : (
                    s.reviewerNote && (
                      <div className="text-xs" style={{ color: 'var(--ink-3)', marginTop: 12 }}>
                        <strong>{isAr ? 'ملاحظة المراجع:' : 'Reviewer:'}</strong> {s.reviewerNote}
                        {s.reviewedAt && (
                          <span style={{ marginInlineStart: 8 }}>
                            ({formatDate(s.reviewedAt)})
                          </span>
                        )}
                      </div>
                    )
                  )}
                </Card>
              );
            })}
          </div>
        )}
      </LoadableSection>
    </Shell>
  );
}

function prettyJson(raw: string): string {
  try { return JSON.stringify(JSON.parse(raw), null, 2); }
  catch { return raw; }
}

function formatDate(iso: string): string {
  try { return new Date(iso).toLocaleString(); }
  catch { return iso; }
}

function Field({ label, full, children }: {
  label: string; full?: boolean; children: React.ReactNode;
}) {
  return (
    <label className={'wf-field' + (full ? ' wf-field-full' : '')}>
      <span className="wf-label">{label}</span>
      {children}
    </label>
  );
}
