import { useCallback, useEffect, useState } from 'react';
import { ClipboardList, CircleDot, Loader, Ban, AlertOctagon, Plus, RefreshCw, ChevronLeft, ChevronRight } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  issuesApi, ApiError,
  ISSUE_TRANSITIONS, ISSUE_RESOLUTIONS, ISSUE_SOURCE_MODULES,
  type IssueDto,
} from '../../lib/api';

const STATUS_TONE: Record<string, BadgeTone> = {
  OPEN: 'info', IN_PROGRESS: 'primary', BLOCKED: 'warning',
  RESOLVED: 'success', CLOSED: 'neutral', CANCELLED: 'neutral',
};
const SEV_TONE: Record<string, BadgeTone> = {
  LOW: 'neutral', MEDIUM: 'info', HIGH: 'warning', CRITICAL: 'danger',
};
const PRIORITY_TONE: Record<string, BadgeTone> = {
  LOW: 'neutral', NORMAL: 'info', HIGH: 'warning', URGENT: 'danger',
};
const STATUS_LABEL_AR: Record<string, string> = {
  OPEN: 'مفتوح', IN_PROGRESS: 'قيد المعالجة', BLOCKED: 'مُعطَّل',
  RESOLVED: 'مُعالَج', CLOSED: 'مُغلَق', CANCELLED: 'مُلغى',
};
const SELECT_STYLE: React.CSSProperties = {
  padding: '6px 10px', fontSize: 12, minWidth: 130, border: '1px solid var(--border)',
  borderRadius: 'var(--r-sm)', background: 'var(--surface-1)', color: 'var(--ink-1)',
};
const PAGE_SIZE = 50;

interface IssueForm {
  titleEn: string; titleAr: string; description: string;
  sourceModule: string; severity: string; priority: string;
  assigneeRole: string; dueDate: string;
}
interface ResolveForm { resolution: string; }

/**
 * Cross-module Issue tracker console — surfaces /api/v1/issues (a thin generic
 * finding record used by audit / cyber / BCM / manual sources). Create, filter
 * by source module, and drive the OPEN→…→CLOSED state machine inline.
 */
export function IssuesPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [rows, setRows] = useState<IssueDto[]>([]);
  const [stats, setStats] = useState<Record<string, number> | null>(null);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [total, setTotal] = useState(0);
  const [moduleFilter, setModuleFilter] = useState('');

  const [formOpen, setFormOpen] = useState(false);
  const [resolveTarget, setResolveTarget] = useState<{ issue: IssueDto; to: string } | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  const statusLabel = (s: string) => (isAr ? (STATUS_LABEL_AR[s] ?? s) : s.replace('_', ' '));

  const load = useCallback(async () => {
    setLoading(true);
    try {
      if (moduleFilter) {
        const list = await issuesApi.bySource(moduleFilter);
        setRows(list); setTotalPages(1); setTotal(list.length);
      } else {
        const p = await issuesApi.list(page, PAGE_SIZE);
        setRows(p.content ?? []); setTotalPages(p.totalPages ?? 0); setTotal(p.totalElements ?? 0);
      }
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'تعذّر تحميل القضايا' : 'Failed to load issues'));
      setRows([]);
    } finally { setLoading(false); }
  }, [moduleFilter, page]);  // eslint-disable-line react-hooks/exhaustive-deps

  const loadStats = useCallback(() => { issuesApi.stats().then(setStats).catch(() => setStats(null)); }, []);
  useEffect(() => { void load(); }, [load]);
  useEffect(() => { loadStats(); }, [loadStats]);

  async function createIssue(v: IssueForm): Promise<void> {
    if (!v.titleEn.trim()) { toast.danger(isAr ? 'العنوان مطلوب' : 'Title is required'); return; }
    try {
      await issuesApi.create({
        titleEn: v.titleEn.trim(), titleAr: v.titleAr.trim() || undefined,
        description: v.description.trim() || undefined,
        sourceModule: v.sourceModule, severity: v.severity, priority: v.priority,
        assigneeRole: v.assigneeRole.trim() || undefined,
        dueDate: v.dueDate || undefined,
      });
      toast.success(isAr ? 'تم إنشاء القضية' : 'Issue created');
      setFormOpen(false); setPage(0);
      await load(); loadStats();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل الإنشاء' : 'Create failed'));
    }
  }

  async function onTransition(issue: IssueDto, to: string) {
    if (!to) return;
    if (to === 'RESOLVED' || to === 'CLOSED') { setResolveTarget({ issue, to }); return; }
    setBusyId(issue.id);
    try {
      await issuesApi.transition(issue.id, to);
      toast.success(isAr ? `${issue.issueCode} → ${statusLabel(to)}` : `${issue.issueCode} → ${to.replace('_', ' ')}`);
      await load(); loadStats();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل التحويل' : 'Transition failed'));
    } finally { setBusyId(null); }
  }

  async function submitResolve(v: ResolveForm): Promise<void> {
    if (!resolveTarget) return;
    try {
      await issuesApi.transition(resolveTarget.issue.id, resolveTarget.to, v.resolution);
      toast.success(isAr ? 'تم تحديث القضية' : 'Issue updated');
      setResolveTarget(null);
      await load(); loadStats();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل التحويل' : 'Transition failed'));
    }
  }

  const formFields: FieldDef[] = [
    { kind: 'text', key: 'titleEn', label: isAr ? 'العنوان (EN) *' : 'Title (EN) *', required: true, span: 12, maxLength: 500 },
    { kind: 'text', key: 'titleAr', label: isAr ? 'العنوان (AR)' : 'Title (AR)', span: 12, maxLength: 500 },
    { kind: 'select', key: 'sourceModule', label: isAr ? 'الوحدة المصدر *' : 'Source module *', required: true, span: 4,
      options: ISSUE_SOURCE_MODULES.map(m => ({ value: m, label: m })) },
    { kind: 'select', key: 'severity', label: isAr ? 'الخطورة' : 'Severity', span: 4,
      options: ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map(s => ({ value: s, label: s })) },
    { kind: 'select', key: 'priority', label: isAr ? 'الأولوية' : 'Priority', span: 4,
      options: ['LOW', 'NORMAL', 'HIGH', 'URGENT'].map(s => ({ value: s, label: s })) },
    { kind: 'text', key: 'assigneeRole', label: isAr ? 'الدور المسؤول' : 'Assignee role', span: 6 },
    { kind: 'date', key: 'dueDate', label: isAr ? 'تاريخ الاستحقاق' : 'Due date', span: 6 },
    { kind: 'textarea', key: 'description', label: isAr ? 'الوصف' : 'Description', rows: 3, span: 12 },
  ];
  const formInitial: IssueForm = { titleEn: '', titleAr: '', description: '', sourceModule: 'MANUAL', severity: 'MEDIUM', priority: 'NORMAL', assigneeRole: '', dueDate: '' };

  const resolveFields: FieldDef[] = [
    { kind: 'select', key: 'resolution', label: isAr ? 'القرار *' : 'Resolution *', required: true, span: 12,
      options: ISSUE_RESOLUTIONS.map(r => ({ value: r, label: r })) },
  ];

  return (
    <Shell title={isAr ? 'القضايا' : 'Issues'} sub={isAr ? 'متعقّب القضايا عبر الوحدات' : 'Cross-module issue tracker'}>
      <PageHeader
        eyebrow={isAr ? 'المنصّة' : 'Platform'}
        title={isAr ? 'القضايا' : 'Issues'}
        sub={isAr ? 'سجل قضايا عام يربط ملاحظات التدقيق والأمن والاستمرارية بدورة معالجة موحّدة.' : 'A generic finding register linking audit, cyber and BCM observations to one remediation lifecycle.'}
      />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={CircleDot}     label={isAr ? 'مفتوحة' : 'Open'} value={stats?.status_open ?? '—'} tone="info" to="/admin" />
        <Kpi Icon={Loader}        label={isAr ? 'قيد المعالجة' : 'In progress'} value={stats?.status_in_progress ?? '—'} tone="primary" to="/admin" />
        <Kpi Icon={Ban}           label={isAr ? 'مُعطَّلة' : 'Blocked'} value={stats?.status_blocked ?? '—'} tone="warning" to="/admin" />
        <Kpi Icon={AlertOctagon}  label={isAr ? 'حرجة' : 'Critical'} value={stats?.severity_critical ?? '—'} tone="danger" to="/admin" />
      </div>

      <Card title={isAr ? 'سجل القضايا' : 'Issue register'} Icon={ClipboardList}
        action={
          <div style={{ display: 'inline-flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
            <select value={moduleFilter} onChange={e => { setPage(0); setModuleFilter(e.target.value); }} style={SELECT_STYLE}
              aria-label={isAr ? 'الوحدة المصدر' : 'Source module'}>
              <option value="">{isAr ? 'كل الوحدات' : 'All modules'}</option>
              {ISSUE_SOURCE_MODULES.map(m => <option key={m} value={m}>{m}</option>)}
            </select>
            <Button size="sm" variant="ghost" Icon={RefreshCw} onClick={() => { void load(); loadStats(); }} disabled={loading}>{isAr ? 'تحديث' : 'Refresh'}</Button>
            <Button size="sm" variant="primary" Icon={Plus} onClick={() => setFormOpen(true)}>{isAr ? 'قضية جديدة' : 'New issue'}</Button>
          </div>
        }>
        {loading && rows.length === 0
          ? <div style={{ padding: 16, color: 'var(--ink-3)', fontSize: 13 }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div>
          : rows.length === 0
            ? <div style={{ padding: 16, color: 'var(--ink-3)', fontSize: 13 }}>{isAr ? 'لا توجد قضايا' : 'No issues'}</div>
            : <table className="grc-table">
                <thead><tr>
                  <th>{isAr ? 'الكود' : 'Code'}</th>
                  <th>{isAr ? 'العنوان' : 'Title'}</th>
                  <th>{isAr ? 'المصدر' : 'Source'}</th>
                  <th>{isAr ? 'الخطورة' : 'Severity'}</th>
                  <th>{isAr ? 'الأولوية' : 'Priority'}</th>
                  <th>{isAr ? 'الحالة' : 'Status'}</th>
                  <th style={{ textAlign: 'end' }}>{isAr ? 'نقل إلى' : 'Move to'}</th>
                </tr></thead>
                <tbody>
                  {rows.map(r => {
                    const nexts = ISSUE_TRANSITIONS[r.status] ?? [];
                    return (
                      <tr key={r.id}>
                        <td className="mono text-2xs">{r.issueCode}</td>
                        <td>
                          <div style={{ fontWeight: 600, fontSize: 13, maxWidth: 360, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
                            title={isAr && r.titleAr ? r.titleAr : r.titleEn}>
                            {isAr && r.titleAr ? r.titleAr : r.titleEn}
                          </div>
                          {r.assigneeRole && <div className="text-2xs text-muted">{r.assigneeRole}</div>}
                        </td>
                        <td className="text-2xs">{r.sourceModule}</td>
                        <td><Badge tone={SEV_TONE[r.severity] ?? 'neutral'}>{r.severity}</Badge></td>
                        <td><Badge tone={PRIORITY_TONE[r.priority] ?? 'neutral'}>{r.priority}</Badge></td>
                        <td>
                          <Badge tone={STATUS_TONE[r.status] ?? 'neutral'} dot>{statusLabel(r.status)}</Badge>
                          {r.resolution && <span className="text-2xs text-muted" style={{ marginInlineStart: 6 }}>{r.resolution}</span>}
                        </td>
                        <td style={{ textAlign: 'end' }}>
                          {nexts.length === 0
                            ? <span className="text-2xs text-muted">{isAr ? 'نهائي' : 'terminal'}</span>
                            : <select value="" disabled={busyId === r.id}
                                onChange={e => { const v = e.target.value; e.currentTarget.value = ''; void onTransition(r, v); }}
                                style={{ ...SELECT_STYLE, minWidth: 130 }}
                                aria-label={isAr ? 'نقل إلى' : 'Move to'}>
                                <option value="">{isAr ? 'نقل إلى…' : 'Move to…'}</option>
                                {nexts.map(s => <option key={s} value={s}>{statusLabel(s)}</option>)}
                              </select>}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>}

        {totalPages > 1 && !moduleFilter && (
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 10, marginTop: 12 }}>
            <span className="text-2xs text-muted">
              {isAr ? `صفحة ${page + 1} من ${totalPages} · ${total} قضية` : `Page ${page + 1} of ${totalPages} · ${total} issues`}
            </span>
            <Button size="sm" variant="ghost" Icon={ChevronLeft} disabled={page === 0 || loading} onClick={() => setPage(p => Math.max(0, p - 1))}>{isAr ? 'السابق' : 'Prev'}</Button>
            <Button size="sm" variant="ghost" Icon={ChevronRight} disabled={page >= totalPages - 1 || loading} onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}>{isAr ? 'التالي' : 'Next'}</Button>
          </div>
        )}
      </Card>

      <FormModal<IssueForm>
        open={formOpen}
        title={isAr ? 'قضية جديدة' : 'New issue'}
        fields={formFields}
        initial={formInitial}
        onClose={() => setFormOpen(false)}
        onSubmit={createIssue}
        submitLabel={isAr ? 'إنشاء' : 'Create'}
      />
      <FormModal<ResolveForm>
        open={!!resolveTarget}
        title={resolveTarget
          ? (isAr ? `${resolveTarget.to === 'CLOSED' ? 'إغلاق' : 'معالجة'} ${resolveTarget.issue.issueCode}` : `${resolveTarget.to === 'CLOSED' ? 'Close' : 'Resolve'} ${resolveTarget.issue.issueCode}`)
          : ''}
        fields={resolveFields}
        initial={{ resolution: 'FIXED' }}
        onClose={() => setResolveTarget(null)}
        onSubmit={submitResolve}
        submitLabel={isAr ? 'تأكيد' : 'Confirm'}
        size="sm"
      />
    </Shell>
  );
}
