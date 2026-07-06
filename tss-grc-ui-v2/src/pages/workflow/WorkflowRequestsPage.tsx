// WF-3 — Workflow Requests: the §6 request register + "my inbox" + the New Request composer
// built around the reusable WorkflowActorSelector (§5). Every send knows: to whom, why, due
// when, routed how, escalated where — no more generic buttons.
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Send, RefreshCw, Plus, Inbox as InboxIcon, ListChecks, AlarmClock, Flame } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { Input, Textarea } from '../../components/ui/Input';
import { Select } from '../../components/ui/Select';
import { Modal } from '../../components/ui/Modal';
import { EmptyState } from '../../components/ui/EmptyState';
import { WorkflowActorSelector, validateRecipients } from '../../components/workflow/WorkflowActorSelector';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  workflowRequestsApi, type WorkflowRequestDto, type WorkflowRecipientInput, type RoutingMode,
} from '../../lib/workflowRequests';

export const WF_STATUS_TONE: Record<string, BadgeTone> = {
  DRAFT: 'neutral', SENT: 'info', ASSIGNED: 'info', IN_PROGRESS: 'info', WAITING_RESPONSE: 'info',
  SUBMITTED: 'warning', UNDER_REVIEW: 'warning', RETURNED: 'warning', RESUBMITTED: 'warning',
  APPROVED: 'success', REJECTED: 'danger', ESCALATED: 'danger', OVERDUE: 'danger',
  CLOSED: 'neutral', REOPENED: 'info', ARCHIVED: 'neutral',
};
const COUNTDOWN_TONE: Record<string, BadgeTone> = {
  ON_TRACK: 'success', DUE_TODAY: 'warning', OVERDUE: 'danger', NONE: 'neutral',
};
const REQUEST_TYPES = ['EVIDENCE_REQUEST', 'APPROVAL', 'REVIEW', 'TASK', 'ACKNOWLEDGEMENT',
  'INFORMATION', 'REPORT_DISTRIBUTION', 'FOLLOW_UP'];
const MODULES = ['PLATFORM', 'RISK', 'BCM', 'CYBER', 'COMPLIANCE', 'PRIVACY', 'ESG', 'SCM', 'EVIDENCE', 'SOCOPS'];
const STATUS_FILTERS = ['', 'SENT', 'IN_PROGRESS', 'SUBMITTED', 'UNDER_REVIEW', 'RETURNED',
  'APPROVED', 'REJECTED', 'OVERDUE', 'ESCALATED', 'CLOSED'];

const EMPTY_FORM = {
  titleEn: '', titleAr: '', requestType: 'EVIDENCE_REQUEST', sourceModule: 'PLATFORM',
  routingMode: 'PARALLEL' as RoutingMode, completionRule: 'ALL' as 'ALL' | 'ANY',
  dueDate: '', priority: 'MEDIUM', reminderDaysBefore: 3, escalationRole: '', description: '',
};

export function WorkflowRequestsPage() {
  const { lang } = useI18n();
  const ar = lang === 'ar';
  const toast = useToast();
  const [view, setView] = useState<'inbox' | 'all'>('inbox');
  const [statusFilter, setStatusFilter] = useState('');
  const [rows, setRows] = useState<WorkflowRequestDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [composerOpen, setComposerOpen] = useState(false);
  const [form, setForm] = useState({ ...EMPTY_FORM });
  const [recipients, setRecipients] = useState<WorkflowRecipientInput[]>([]);
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    setLoading(true);
    const p = view === 'inbox'
      ? workflowRequestsApi.inbox()
      : workflowRequestsApi.list(statusFilter || undefined, 0, 100).then(r => r.content);
    p.then(setRows).catch(() => setRows([])).finally(() => setLoading(false));
  }, [view, statusFilter]);
  useEffect(() => { load(); }, [load]);

  const kpis = useMemo(() => ({
    open: rows.filter(r => !['CLOSED', 'ARCHIVED', 'APPROVED', 'REJECTED'].includes(r.status)).length,
    overdue: rows.filter(r => r.countdownStatus === 'OVERDUE').length,
    escalated: rows.filter(r => r.escalatedAt).length,
    total: rows.length,
  }), [rows]);

  function set<K extends keyof typeof EMPTY_FORM>(k: K, v: (typeof EMPTY_FORM)[K]) {
    setForm(f => ({ ...f, [k]: v }));
  }

  async function submit() {
    const recErr = validateRecipients(recipients, form.routingMode, ar);
    if (!form.titleEn.trim()) { toast.danger(ar ? 'العنوان مطلوب' : 'Title is required'); return; }
    if (recErr) { toast.danger(recErr); return; }
    if (!form.dueDate) { toast.danger(ar ? 'تاريخ الاستحقاق مطلوب' : 'Due date is required'); return; }
    if ((form.priority === 'HIGH' || form.priority === 'CRITICAL') && !form.escalationRole
        && !recipients.some(r => r.actorRole === 'ESCALATION')) {
      toast.danger(ar ? 'الأولوية العالية تتطلب مسار تصعيد' : 'HIGH/CRITICAL priority requires an escalation path');
      return;
    }
    setBusy(true);
    try {
      const created = await workflowRequestsApi.create({
        sourceModule: form.sourceModule, requestType: form.requestType,
        titleEn: form.titleEn, titleAr: form.titleAr || undefined,
        description: form.description || undefined,
        routingMode: form.routingMode,
        completionRule: form.routingMode === 'PARALLEL' ? form.completionRule : 'ALL',
        dueDate: form.dueDate, priority: form.priority as never,
        reminderDaysBefore: form.reminderDaysBefore,
        escalationRole: form.escalationRole || undefined,
        responseRequired: true, recipients,
      });
      await workflowRequestsApi.send(created.id);
      toast.success(ar ? `أُرسل الطلب ${created.requestCode}` : `Request ${created.requestCode} sent`);
      setComposerOpen(false); setForm({ ...EMPTY_FORM }); setRecipients([]);
      load();
    } catch (e) {
      toast.danger((e as Error)?.message ?? (ar ? 'فشل الإرسال' : 'Send failed'));
    } finally { setBusy(false); }
  }

  return (
    <Shell title={ar ? 'طلبات سير العمل' : 'Workflow requests'}
      sub={ar ? 'محرك الطلبات المنصّي — مستلمون وتوجيه ومهل وتصعيد' : 'Platform request engine — actors, routing, SLA, escalation'}>
      <PageHeader eyebrow={ar ? 'أعمالي' : 'My Work'}
        title={ar ? 'طلبات سير العمل' : 'Workflow requests'}
        sub={ar ? 'كل إرسال يعرف: إلى مَن، لماذا، متى يستحق، وكيف يُصعَّد' : 'Every send knows: to whom, why, when due, and how it escalates'}
        actions={<>
          <Button variant="ghost" size="sm" onClick={load}><RefreshCw size={14} /> {ar ? 'تحديث' : 'Refresh'}</Button>
          <Button variant="primary" size="sm" onClick={() => setComposerOpen(true)}>
            <Plus size={14} /> {ar ? 'طلب جديد' : 'New request'}
          </Button>
        </>} />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={ListChecks}  label={ar ? 'مفتوحة' : 'Open'}      value={String(kpis.open)}      tone="primary" />
        <Kpi Icon={AlarmClock}  label={ar ? 'متأخرة' : 'Overdue'}   value={String(kpis.overdue)}   tone={kpis.overdue ? 'danger' : 'success'} />
        <Kpi Icon={Flame}       label={ar ? 'مصعَّدة' : 'Escalated'} value={String(kpis.escalated)} tone={kpis.escalated ? 'warning' : 'success'} />
        <Kpi Icon={InboxIcon}   label={ar ? 'الإجمالي' : 'Total'}    value={String(kpis.total)}     tone="neutral" />
      </div>

      <div style={{ display: 'flex', gap: 8, marginBottom: 12, alignItems: 'center', flexWrap: 'wrap' }}>
        <Button size="sm" variant={view === 'inbox' ? 'primary' : 'secondary'} onClick={() => setView('inbox')}>
          <InboxIcon size={13} /> {ar ? 'واردي' : 'My inbox'}
        </Button>
        <Button size="sm" variant={view === 'all' ? 'primary' : 'secondary'} onClick={() => setView('all')}>
          <ListChecks size={13} /> {ar ? 'كل الطلبات' : 'All requests'}
        </Button>
        {view === 'all' && (
          <div style={{ minWidth: 180 }}>
            <Select value={statusFilter} onChange={e => setStatusFilter(e.target.value)}
              options={STATUS_FILTERS.map(s => ({ value: s, label: s === '' ? (ar ? 'كل الحالات' : 'All statuses') : s }))} />
          </div>
        )}
      </div>

      <Card padded={false}>
        {loading ? (
          <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>{ar ? 'جارٍ التحميل…' : 'Loading…'}</div>
        ) : rows.length === 0 ? (
          <EmptyState Icon={Send} title={ar ? 'لا توجد طلبات' : 'No workflow requests'}
            sub={ar ? 'أنشئ طلباً وحدد المستلمين والتوجيه والمهلة.' : 'Create a request and pick recipients, routing, and a due date.'} />
        ) : (
          <table className="tbl" style={{ width: '100%' }}>
            <thead><tr>
              <th>{ar ? 'الرمز' : 'Code'}</th>
              <th>{ar ? 'العنوان' : 'Title'}</th>
              <th>{ar ? 'النوع' : 'Type'}</th>
              <th>{ar ? 'التوجيه' : 'Routing'}</th>
              <th>{ar ? 'الاستحقاق' : 'Due'}</th>
              <th>{ar ? 'العدّاد' : 'Countdown'}</th>
              <th>{ar ? 'الأولوية' : 'Priority'}</th>
              <th>{ar ? 'الحالة' : 'Status'}</th>
            </tr></thead>
            <tbody>{rows.map(r => (
              <tr key={r.id}>
                <td className="mono" style={{ fontSize: 11 }}>
                  <Link to={`/workflows/requests/${r.id}`} style={{ color: 'var(--primary)', textDecoration: 'none', fontWeight: 700 }}>
                    {r.requestCode}
                  </Link>
                </td>
                <td style={{ fontSize: 12, maxWidth: 320 }}>
                  <strong>{ar && r.titleAr ? r.titleAr : r.titleEn}</strong>
                  <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>{r.sourceModule}{r.initiatedByName ? ` · ${r.initiatedByName}` : ''}</div>
                </td>
                <td><Badge tone="info">{r.requestType.replace(/_/g, ' ')}</Badge></td>
                <td style={{ fontSize: 11 }}>
                  {r.routingMode}{r.routingMode === 'PARALLEL' ? `/${r.completionRule}` : ''}
                  {r.routingMode === 'SEQUENTIAL' && <span style={{ color: 'var(--ink-3)' }}> · {ar ? 'خطوة' : 'step'} {r.currentStep}</span>}
                </td>
                <td className="mono" style={{ fontSize: 11 }}>{r.dueDate ?? '—'}</td>
                <td><Badge tone={COUNTDOWN_TONE[r.countdownStatus] ?? 'neutral'}>{r.countdownStatus}</Badge></td>
                <td><Badge tone={r.priority === 'CRITICAL' || r.priority === 'HIGH' ? 'danger' : 'neutral'}>{r.priority}</Badge></td>
                <td><Badge tone={WF_STATUS_TONE[r.status] ?? 'neutral'} dot>{r.status}</Badge></td>
              </tr>))}</tbody>
          </table>
        )}
      </Card>

      <Modal open={composerOpen} title={ar ? 'طلب سير عمل جديد' : 'New workflow request'} size="lg"
        onClose={() => setComposerOpen(false)}
        footer={<>
          <Button variant="ghost" onClick={() => setComposerOpen(false)}>{ar ? 'إلغاء' : 'Cancel'}</Button>
          <Button variant="primary" onClick={() => void submit()} disabled={busy}>
            <Send size={14} /> {busy ? (ar ? 'جارٍ الإرسال…' : 'Sending…') : (ar ? 'إنشاء وإرسال' : 'Create & send')}
          </Button>
        </>}>
        <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr 1fr', gap: 10, marginBottom: 10 }}>
          <Input label={(ar ? 'العنوان (EN)' : 'Title (EN)') + ' *'} value={form.titleEn} onChange={e => set('titleEn', e.target.value)} />
          <Select label={ar ? 'نوع الطلب' : 'Request type'} value={form.requestType}
            onChange={e => set('requestType', e.target.value)}
            options={REQUEST_TYPES.map(t => ({ value: t, label: t.replace(/_/g, ' ') }))} />
          <Select label={ar ? 'الوحدة' : 'Module'} value={form.sourceModule}
            onChange={e => set('sourceModule', e.target.value)}
            options={MODULES.map(m => ({ value: m, label: m }))} />
        </div>
        <div style={{ marginBottom: 10 }}>
          <Input label={ar ? 'العنوان (AR)' : 'Title (AR)'} value={form.titleAr} onChange={e => set('titleAr', e.target.value)} dir="rtl" />
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 10, marginBottom: 10 }}>
          <Select label={ar ? 'التوجيه' : 'Routing'} value={form.routingMode}
            onChange={e => set('routingMode', e.target.value as RoutingMode)}
            options={[{ value: 'PARALLEL', label: ar ? 'متوازٍ' : 'Parallel' },
                      { value: 'SEQUENTIAL', label: ar ? 'تسلسلي' : 'Sequential' },
                      { value: 'BROADCAST_ACK', label: ar ? 'تعميم بإقرار' : 'Broadcast + ack' }]} />
          {form.routingMode === 'PARALLEL' ? (
            <Select label={ar ? 'قاعدة الاكتمال' : 'Completion'} value={form.completionRule}
              onChange={e => set('completionRule', e.target.value as 'ALL' | 'ANY')}
              options={[{ value: 'ALL', label: ar ? 'استجابة الجميع' : 'ALL respond' },
                        { value: 'ANY', label: ar ? 'استجابة أي أحد' : 'ANY one responds' }]} />
          ) : <div />}
          <Input label={(ar ? 'الاستحقاق' : 'Due date') + ' *'} type="date" value={form.dueDate} onChange={e => set('dueDate', e.target.value)} />
          <Select label={ar ? 'الأولوية' : 'Priority'} value={form.priority}
            onChange={e => set('priority', e.target.value)}
            options={['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map(p => ({ value: p, label: p }))} />
          <Input label={ar ? 'تذكير قبل (أيام)' : 'Remind before (days)'} type="number" min={0}
            value={form.reminderDaysBefore}
            onChange={e => set('reminderDaysBefore', e.target.value ? Number(e.target.value) : 0)} />
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: 10, marginBottom: 12 }}>
          <Input label={ar ? 'دور التصعيد (للأولوية العالية)' : 'Escalation role (for HIGH/CRITICAL)'}
            value={form.escalationRole} placeholder="CISO"
            onChange={e => set('escalationRole', e.target.value)} />
          <Textarea label={ar ? 'الوصف' : 'Description'} rows={2} value={form.description}
            onChange={e => set('description', e.target.value)} />
        </div>
        <WorkflowActorSelector recipients={recipients} onChange={setRecipients} routingMode={form.routingMode} />
      </Modal>
    </Shell>
  );
}
