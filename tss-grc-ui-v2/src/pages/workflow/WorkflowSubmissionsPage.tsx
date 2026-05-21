import { useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
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
import { SubmissionAnalyticsCard } from '../../components/workflows/SubmissionAnalyticsCard';

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
type StatusFilter = 'ALL' | 'OPEN' | 'SUBMITTED' | 'UNDER_REVIEW' | 'APPROVED' | 'REJECTED' | 'CLOSED';

const STATUS_TONE: Record<string, BadgeTone> = {
  DRAFT:        'neutral',
  SUBMITTED:    'info',
  UNDER_REVIEW: 'warning',
  APPROVED:     'success',
  REJECTED:     'danger',
  CLOSED:       'neutral',
};

/** Whether a row matches the status filter. {@code OPEN} is the union
 *  shown on the Hub's "1 open" badge: SUBMITTED + UNDER_REVIEW. */
function matchesStatus(rowStatus: string, filter: StatusFilter): boolean {
  if (filter === 'ALL')  return true;
  if (filter === 'OPEN') return rowStatus === 'SUBMITTED' || rowStatus === 'UNDER_REVIEW';
  return rowStatus === filter;
}

export function WorkflowSubmissionsPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  // Filters survive page reloads + Hub drill-downs via URL search params.
  // Hub badges link here as ?workflowId=<id>&status=OPEN, so the register
  // opens already scoped to "the 1 open RoPA submission" instead of
  // "all 380 submissions, find your needle." Updating a filter rewrites
  // the URL so the back-button and shared links both behave.
  const [searchParams, setSearchParams] = useSearchParams();
  const moduleFilter   = (searchParams.get('module')     as ModuleFilter) ?? 'ALL';
  const workflowFilter =  searchParams.get('workflowId') ?? 'ALL';
  const statusFilter   = (searchParams.get('status')     as StatusFilter) ?? 'ALL';

  function setModuleFilter(m: ModuleFilter) {
    const next = new URLSearchParams(searchParams);
    if (m === 'ALL') next.delete('module'); else next.set('module', m);
    // Clear workflowId when module changes — the old workflowId may not
    // belong to the new module (catalog UX rule).
    next.delete('workflowId');
    setSearchParams(next, { replace: true });
  }
  function setWorkflowFilter(w: string) {
    const next = new URLSearchParams(searchParams);
    if (w === 'ALL') next.delete('workflowId'); else next.set('workflowId', w);
    setSearchParams(next, { replace: true });
  }
  function setStatusFilter(s: StatusFilter) {
    const next = new URLSearchParams(searchParams);
    if (s === 'ALL') next.delete('status'); else next.set('status', s);
    setSearchParams(next, { replace: true });
  }
  function clearFilters() { setSearchParams(new URLSearchParams(), { replace: true }); }

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
      // Reset selection so we never point at stale row IDs after a
      // server-side state change.
      setSelectedIds(new Set());
    } catch (e) {
      setError((e as Error).message);
    } finally { setLoading(false); }
  }
  useEffect(() => { void reload(); /* eslint-disable-next-line */ }, [moduleFilter, workflowFilter]);

  // Status is filtered client-side. The backend endpoint takes workflowId
  // and module but not status — adding a server filter would only save a
  // few KB per page-load and the bigger win was the per-wizard aggregate
  // query (PR #33). Keep the surface area small.
  const visibleItems = useMemo(
    () => (items ?? []).filter(s => matchesStatus(s.status, statusFilter)),
    [items, statusFilter]);
  // Drop selection when the status filter changes so we never bulk-act
  // on rows the user can't see right now.
  useEffect(() => { setSelectedIds(new Set()); }, [statusFilter]);

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

  // ── Bulk review ──────────────────────────────────────────────────────
  // Selection is a Set<number> of submission PKs. We only let OPEN rows
  // (SUBMITTED + UNDER_REVIEW) be selected — anything else is terminal
  // from the state-machine perspective and would just fail server-side.
  // Selection is dropped on reload so it never points at stale rows.
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [bulkBusy, setBulkBusy] = useState(false);
  function isOpen(s: WorkflowSubmissionDto): boolean {
    return s.status === 'SUBMITTED' || s.status === 'UNDER_REVIEW';
  }
  const openVisibleItems = useMemo(() => visibleItems.filter(isOpen), [visibleItems]);
  function toggleSelected(id: number) {
    setSelectedIds(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  }
  function selectAllOpen() {
    setSelectedIds(new Set(openVisibleItems.map(s => s.id)));
  }
  function clearSelection() { setSelectedIds(new Set()); }

  async function bulkReview(decision: 'APPROVE' | 'REJECT' | 'CLOSE') {
    if (selectedIds.size === 0) return;
    // Quick confirm — bulk reject/close is irreversible from a workflow
    // perspective even though the row stays in place.
    const verb = isAr
      ? (decision === 'APPROVE' ? 'اعتماد' : decision === 'REJECT' ? 'رفض' : 'إغلاق')
      : decision.toLowerCase();
    const ok = window.confirm(isAr
      ? `${verb} ${selectedIds.size} سجل دفعةً واحدة؟`
      : `${verb[0].toUpperCase() + verb.slice(1)} ${selectedIds.size} submission${selectedIds.size === 1 ? '' : 's'} in bulk?`);
    if (!ok) return;
    setBulkBusy(true);
    // Promise.allSettled so one failure doesn't abandon the rest. We
    // surface the worst-case status in the toast at the end.
    const results = await Promise.allSettled(
      [...selectedIds].map(id => workflowSubmissionApi.review(id, { decision })));
    const ok_ = results.filter(r => r.status === 'fulfilled').length;
    const fail = results.length - ok_;
    if (fail === 0) {
      toast.success(isAr
        ? `تم ${verb} ${ok_} سجل`
        : `${ok_} submission${ok_ === 1 ? '' : 's'} ${decision.toLowerCase()}d`);
    } else if (ok_ === 0) {
      toast.danger(isAr
        ? `فشل ${fail} سجل`
        : `${fail} submission${fail === 1 ? '' : 's'} failed`);
    } else {
      toast.danger(isAr
        ? `نجح ${ok_} وفشل ${fail}`
        : `${ok_} succeeded, ${fail} failed`);
    }
    clearSelection();
    setBulkBusy(false);
    void reload();
  }

  const counts = useMemo(() => {
    return {
      submitted:   visibleItems.filter(s => s.status === 'SUBMITTED').length,
      underReview: visibleItems.filter(s => s.status === 'UNDER_REVIEW').length,
      approved:    visibleItems.filter(s => s.status === 'APPROVED').length,
      rejected:    visibleItems.filter(s => s.status === 'REJECTED').length,
      closed:      visibleItems.filter(s => s.status === 'CLOSED').length,
    };
  }, [visibleItems]);

  /** Export the currently-filtered rows as CSV. Regulator-visible columns
   *  only; the full JSON payload is intentionally excluded (export is for
   *  offline review + audit retention, not data re-import). */
  function exportCsv() {
    if (visibleItems.length === 0) return;
    const rows: ExportRow[] = visibleItems.map(s => {
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
    const statusSuffix = statusFilter !== 'ALL' ? `-${statusFilter.toLowerCase()}` : '';
    downloadCsv(rows, stampFilename(`workflow-submissions${suffix}${statusSuffix}`, 'csv'));
    toast.success(isAr
      ? `تم تصدير ${visibleItems.length} سجل بصيغة CSV`
      : `Exported ${visibleItems.length} submissions to CSV`);
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
                    disabled={visibleItems.length === 0}
                    onClick={exportCsv}>
              {isAr ? 'تصدير CSV' : 'Export CSV'}
              {visibleItems.length > 0 ? ` (${visibleItems.length})` : ''}
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
        <select className="input"
                value={statusFilter}
                onChange={e => setStatusFilter(e.target.value as StatusFilter)}>
          <option value="ALL">{isAr ? 'كل الحالات' : 'All statuses'}</option>
          <option value="OPEN">{isAr ? 'مفتوحة (مُرسل + قيد المراجعة)' : 'Open (Submitted + Under review)'}</option>
          <option value="SUBMITTED">{isAr ? 'مُرسل' : 'Submitted'}</option>
          <option value="UNDER_REVIEW">{isAr ? 'قيد المراجعة' : 'Under review'}</option>
          <option value="APPROVED">{isAr ? 'مُعتمد' : 'Approved'}</option>
          <option value="REJECTED">{isAr ? 'مرفوض' : 'Rejected'}</option>
          <option value="CLOSED">{isAr ? 'مغلق' : 'Closed'}</option>
        </select>
        {(moduleFilter !== 'ALL' || workflowFilter !== 'ALL' || statusFilter !== 'ALL') && (
          <Button variant="ghost" size="sm" Icon={XIcon} onClick={clearFilters}>
            {isAr ? 'مسح الكل' : 'Clear all'}
          </Button>
        )}
      </div>

      {/* Stats row */}
      {visibleItems.length > 0 && (
        <div style={{ display: 'flex', gap: 12, margin: '16px 0', flexWrap: 'wrap' }}>
          <Badge tone="info">{isAr ? 'مُرسل' : 'Submitted'}: {counts.submitted}</Badge>
          <Badge tone="warning">{isAr ? 'قيد المراجعة' : 'Under review'}: {counts.underReview}</Badge>
          <Badge tone="success">{isAr ? 'مُعتمد' : 'Approved'}: {counts.approved}</Badge>
          <Badge tone="danger">{isAr ? 'مرفوض' : 'Rejected'}: {counts.rejected}</Badge>
          <Badge tone="neutral">{isAr ? 'مغلق' : 'Closed'}: {counts.closed}</Badge>
        </div>
      )}

      {/* Analytics card — visible-at-a-glance KPIs + 3 charts.
       *  Computed from the already-filtered visibleItems, so picking
       *  PDPL narrows the bar chart to only PDPL wizards and picking
       *  status=OPEN narrows to only in-flight rows. */}
      {visibleItems.length > 0 && (
        <div style={{ marginBottom: 16 }}>
          <SubmissionAnalyticsCard items={visibleItems} />
        </div>
      )}

      {/* Bulk action bar — appears only when there are reviewable rows
       *  in view. A reviewer narrows the register to "OPEN · RoPA" (4
       *  rows), ticks the boxes, hits Approve, and clears the queue in
       *  one click rather than four. Selection drops on filter change
       *  or reload so we never act on stale rows. */}
      {openVisibleItems.length > 0 && (
        <div className="wf-bulkbar" style={{
          display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap',
          padding: '10px 14px', marginBottom: 14,
          background: 'var(--surface-2, #f5f6f8)',
          border: '1px solid var(--border)', borderRadius: 8,
        }}>
          <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13 }}>
            <input type="checkbox"
                   checked={selectedIds.size > 0 && selectedIds.size === openVisibleItems.length}
                   ref={el => {
                     // Tri-state visual: partial → indeterminate, all → checked
                     if (el) el.indeterminate = selectedIds.size > 0
                                            && selectedIds.size < openVisibleItems.length;
                   }}
                   onChange={() => {
                     if (selectedIds.size === openVisibleItems.length) clearSelection();
                     else selectAllOpen();
                   }}/>
            <span style={{ color: 'var(--ink-2)' }}>
              {selectedIds.size === 0
                ? (isAr
                    ? `اختر من ${openVisibleItems.length} سجل قابل للمراجعة`
                    : `Select up to ${openVisibleItems.length} reviewable submission${openVisibleItems.length === 1 ? '' : 's'}`)
                : (isAr
                    ? `${selectedIds.size} / ${openVisibleItems.length} محدد`
                    : `${selectedIds.size} of ${openVisibleItems.length} selected`)}
            </span>
          </label>
          {selectedIds.size > 0 && (
            <div style={{ display: 'flex', gap: 8, marginInlineStart: 'auto', flexWrap: 'wrap' }}>
              <Button variant="primary" size="sm" Icon={CheckCheck}
                      disabled={bulkBusy} onClick={() => void bulkReview('APPROVE')}>
                {isAr ? 'اعتماد المحدد' : 'Approve selected'} ({selectedIds.size})
              </Button>
              <Button variant="danger" size="sm" Icon={XIcon}
                      disabled={bulkBusy} onClick={() => void bulkReview('REJECT')}>
                {isAr ? 'رفض' : 'Reject'}
              </Button>
              <Button variant="ghost" size="sm" Icon={PauseCircle}
                      disabled={bulkBusy} onClick={() => void bulkReview('CLOSE')}>
                {isAr ? 'إغلاق' : 'Close'}
              </Button>
              <Button variant="ghost" size="sm" disabled={bulkBusy} onClick={clearSelection}>
                {isAr ? 'إلغاء التحديد' : 'Clear'}
              </Button>
            </div>
          )}
        </div>
      )}

      <LoadableSection
        loading={loading}
        error={error}
        onRetry={reload}
        data={visibleItems}
        emptyTitle={
          // When filters narrow to zero rows, show a filter-specific
          // empty state so the user understands *why* it's empty.
          (items?.length ?? 0) > 0 && visibleItems.length === 0
            ? (isAr ? 'لا سجلات تطابق المرشحات' : 'No submissions match the filters')
            : (isAr ? 'لا سجلات بعد' : 'No submissions yet')}
        emptySub={
          (items?.length ?? 0) > 0 && visibleItems.length === 0
            ? (isAr
                ? `إزالة المرشحات تكشف ${items?.length ?? 0} سجل.`
                : `Clearing filters reveals ${items?.length ?? 0} submission${(items?.length ?? 0) === 1 ? '' : 's'}.`)
            : (isAr
                ? 'ابدأ من كتالوج المعالجات لإنشاء أول سجل.'
                : 'Walk a wizard from the catalog to create the first submission.')}>
        {(rows) => (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            {rows.map(s => {
              const cat = WORKFLOWS.find(w => w.id === s.workflowId);
              const rowOpen = isOpen(s);
              const checked = selectedIds.has(s.id);
              return (
                <Card key={s.id}
                      title={`#${s.id} · ${isAr ? (cat?.titleAr ?? s.workflowId)
                                                : (cat?.titleEn ?? s.workflowId)}`}
                      Icon={cat?.Icon ?? ClipboardCheck}
                      action={(
                        <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                          {rowOpen && (
                            <label title={isAr ? 'تحديد للمراجعة الجماعية' : 'Select for bulk review'}
                                   style={{ display: 'inline-flex', alignItems: 'center', cursor: 'pointer' }}>
                              <input type="checkbox" checked={checked} disabled={bulkBusy}
                                     onChange={() => toggleSelected(s.id)}
                                     aria-label={`Select submission ${s.id}`}/>
                            </label>
                          )}
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

                  {rowOpen ? (
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
