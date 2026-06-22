import { Link } from 'react-router-dom';
import { Activity, AlertTriangle, Flame, Clock, CheckCheck, ShieldAlert, Briefcase } from 'lucide-react';
import { DirectionalArrow } from '../rtl/DirectionalArrow';
import { Card } from '../ui/Card';
import { Kpi } from '../ui/Kpi';
import { Badge } from '../ui/Badge';
import { Stepper } from '../ui/Stepper';
import { ESCALATION_LABEL, ESCALATION_TONE, type WorkflowInstance } from '../../lib/workflows';

/**
 * ActiveWorkflowsPanel renders the "what is in flight right now?" view for a
 * module. Five KPIs at the top (Total / On-track / Due soon / Overdue /
 * Escalated) followed by an inline Stepper per workflow, with owner, due
 * date, and an escalation tier chip.
 *
 * Used on every module's Workflows tab.
 */
export function ActiveWorkflowsPanel({
  workflows, title, sub, t,
}: {
  workflows: WorkflowInstance[];
  title: string;
  sub?: string;
  t: (k: string) => string;
}) {
  const total       = workflows.length;
  const onTrack     = workflows.filter(w => w.escalation === 'ON_TRACK').length;
  const dueSoon     = workflows.filter(w => w.escalation === 'AT_RISK').length;
  const overdue     = workflows.filter(w => w.escalation === 'OVERDUE' || w.escalation === 'MANAGER_ESC').length;
  const escalated   = workflows.filter(w => w.escalation === 'DIRECTOR_ESC' || w.escalation === 'EXEC_ESC').length;

  // Group workflows by their workflow definition name so the user sees
  // "CAPA Closure (n) → list" rather than a flat unscoped list.
  const groups = new Map<string, WorkflowInstance[]>();
  for (const w of workflows) {
    if (!groups.has(w.workflow)) groups.set(w.workflow, []);
    groups.get(w.workflow)!.push(w);
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <div className="kpi-grid" style={{ gridTemplateColumns: 'repeat(5, minmax(140px, 1fr))' }}>
        <Kpi Icon={Activity}       label={t('workflowsKpiTotal') ?? 'In flight'}      value={total}     tone="primary" to="/workflows" />
        <Kpi Icon={CheckCheck}     label={t('workflowsKpiOnTrack') ?? 'On track'}     value={onTrack}   tone="success" to="/workflows" />
        <Kpi Icon={Clock}          label={t('workflowsKpiDueSoon') ?? 'Due soon'}     value={dueSoon}   tone="info" to="/workflows" />
        <Kpi Icon={AlertTriangle}  label={t('workflowsKpiOverdue') ?? 'Overdue'}      value={overdue}   tone="warning" to="/workflows/chains/pending" />
        <Kpi Icon={Flame}          label={t('workflowsKpiEscalated') ?? 'Escalated'}  value={escalated} tone="danger" to="/workflows/chains/pending" />
      </div>

      {workflows.length === 0
        ? <Card><div style={{ padding: 24, textAlign: 'center', color: 'var(--ink-3)' }}>
            {t('workflowsEmpty') ?? 'No workflows currently in flight.'}
          </div></Card>
        : Array.from(groups.entries()).map(([defName, ws]) => (
          <Card key={defName} title={defName} Icon={Briefcase}
            action={<Badge tone="primary">{ws.length}</Badge>}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {ws.map(w => <WorkflowRow key={w.id} w={w} />)}
            </div>
          </Card>
        ))
      }

      {sub && <div style={{ fontSize: 11, color: 'var(--ink-3)', padding: '0 4px' }}>{sub}</div>}
      {/* keep title prop in case a Shell heading needs it */}
      <span style={{ display: 'none' }}>{title}</span>
    </div>
  );
}

function WorkflowRow({ w }: { w: WorkflowInstance }) {
  const tone = ESCALATION_TONE[w.escalation];
  const label = ESCALATION_LABEL[w.escalation];
  const isEscalated = w.escalation === 'MANAGER_ESC' || w.escalation === 'DIRECTOR_ESC' || w.escalation === 'EXEC_ESC';
  const stepperSteps = w.stages.map(s => ({ id: s, label: s.replace(/_/g, ' ') }));

  const body = (
    <div style={{
      padding: '10px 4px',
      borderBottom: '1px dashed var(--border)',
      transition: 'background var(--dur-fast) var(--ease-standard)',
    }}>
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'minmax(220px, 2fr) minmax(140px, 1fr) minmax(120px, 1fr) auto',
        gap: 12, alignItems: 'center', marginBottom: 8,
      }}>
        <div>
          <div style={{ fontWeight: 600, fontSize: 13, color: 'var(--ink-1)' }}>
            <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)', marginInlineEnd: 6 }}>
              {w.entityRef}
            </span>
            {w.title}
          </div>
          <div style={{ fontSize: 11, color: 'var(--ink-3)', display: 'flex', gap: 6, alignItems: 'center', flexWrap: 'wrap', marginTop: 2 }}>
            <Badge tone="info">{w.module}</Badge>
            <span>· {w.entityType}</span>
            {w.reason && <span style={{ fontStyle: 'italic' }}>· {w.reason}</span>}
          </div>
        </div>
        <div>
          <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>Owner</div>
          <div style={{ fontSize: 12, color: 'var(--ink-1)', fontWeight: 500 }}>{w.owner}</div>
          {w.ownerRole && <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>{w.ownerRole}</div>}
        </div>
        <div>
          <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>Due</div>
          <div className="mono" style={{ fontSize: 12, color: 'var(--ink-1)' }}>{w.dueDate ?? '—'}</div>
          <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>
            {w.ageDays}d open · {w.slaDays}d SLA
          </div>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4, alignItems: 'flex-end' }}>
          <Badge tone={tone} dot>{label}</Badge>
          {isEscalated && <Badge tone="danger"><ShieldAlert size={10} style={{ marginInlineEnd: 3, verticalAlign: 'middle' }} />Escalated</Badge>}
        </div>
      </div>
      <Stepper steps={stepperSteps} active={w.currentStage} />
      {w.nextStep && (
        <div style={{ marginTop: 6, fontSize: 11, color: 'var(--primary-700)', display: 'inline-flex', alignItems: 'center', gap: 4 }}>
          <DirectionalArrow size={11} /> Next: {w.nextStep}
        </div>
      )}
    </div>
  );

  return w.href
    ? <Link to={w.href} style={{ textDecoration: 'none', color: 'inherit', display: 'block' }}>{body}</Link>
    : body;
}
