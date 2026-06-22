import { useEffect, useMemo, useState } from 'react';
import { Activity, ShieldCheck, Clock, AlertTriangle, AlertOctagon, Flame, Calendar } from 'lucide-react';
import { Card } from '../ui/Card';
import { Kpi } from '../ui/Kpi';
import { Badge } from '../ui/Badge';
import { useI18n } from '../../lib/i18n';
import { ESCALATION_TONE } from '../../lib/workflows';
import {
  deriveAllLive, aggregatePulse, formatHijri, isSaudiBusinessDay,
  type LiveWorkflowInstance,
} from '../../lib/sla';
import type { WorkflowInstance } from '../../lib/workflows';

/**
 * LiveSlaPulse — top-of-WorkflowsPage runtime panel.
 *
 * Sprint 4 deliverable. Turns the `slaHours` + `dueDate` data that already
 * exists into a live escalation tier. Re-derives every minute so a transition
 * about to breach surfaces in real time, not on next page load.
 *
 *   ┌─────────────────────────────────────────────────────────────┐
 *   │  Total | On track | Due soon | Overdue | Escalated  (KPIs)  │
 *   ├─────────────────────────────────────────────────────────────┤
 *   │  Top-5 about to breach (next 7 days)                        │
 *   │  Top-5 already breached (sorted by % over budget)           │
 *   └─────────────────────────────────────────────────────────────┘
 */
export function LiveSlaPulse({ instances }: { instances: ReadonlyArray<WorkflowInstance> }) {
  const { t, lang } = useI18n();
  const [now, setNow] = useState<Date>(() => new Date());

  // Re-tick every minute so the badge / tier reflects the wall clock.
  useEffect(() => {
    const id = window.setInterval(() => setNow(new Date()), 60_000);
    return () => window.clearInterval(id);
  }, []);

  const live = useMemo(() => deriveAllLive(instances, undefined, now), [instances, now]);
  const pulse = useMemo(() => aggregatePulse(live), [live]);

  const aboutToBreach = useMemo(
    () => live
      .filter(w => w.sla.tier === 'AT_RISK')
      .sort((a, b) => a.sla.daysRemaining - b.sla.daysRemaining)
      .slice(0, 5),
    [live],
  );

  const breached = useMemo(
    () => live
      .filter(w => w.sla.tier !== 'ON_TRACK' && w.sla.tier !== 'AT_RISK')
      .sort((a, b) => b.sla.percentBreach - a.sla.percentBreach)
      .slice(0, 5),
    [live],
  );

  const todayLabel = `${now.toLocaleDateString(lang === 'ar' ? 'ar-SA' : 'en-GB', { day: 'numeric', month: 'short', year: 'numeric' })} · ${formatHijri(now, lang)}`;
  const isWorkday = isSaudiBusinessDay(now);

  return (
    <Card title={t('slaPulseTitle')} Icon={Activity}
      action={
        <div style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
          <Calendar size={12} color="var(--ink-3)" />
          <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>{todayLabel}</span>
          <Badge tone={isWorkday ? 'success' : 'neutral'}>
            {isWorkday ? t('slaSaudiWorkday') : t('slaSaudiWeekend')}
          </Badge>
        </div>
      }>

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Activity}      label={t('slaKpiTotal')}     value={String(pulse.total)} tone="primary" to="/workflows/submissions" />
        <Kpi Icon={ShieldCheck}   label={t('slaKpiOnTrack')}   value={String(pulse.onTrack)} tone="success" to="/workflows/submissions" />
        <Kpi Icon={Clock}         label={t('slaKpiAtRisk')}    value={String(pulse.atRisk)} tone="info" to="/workflows/submissions" />
        <Kpi Icon={AlertTriangle} label={t('slaKpiOverdue')}   value={String(pulse.byTier.OVERDUE)} tone="warning" to="/workflows/submissions" />
        <Kpi Icon={AlertOctagon}  label={t('slaKpiManager')}   value={String(pulse.byTier.MANAGER_ESC)} tone="warning" to="/workflows/submissions" />
        <Kpi Icon={AlertOctagon}  label={t('slaKpiDirector')}  value={String(pulse.byTier.DIRECTOR_ESC)} tone="danger" to="/workflows/submissions" />
        <Kpi Icon={Flame}         label={t('slaKpiExec')}      value={String(pulse.byTier.EXEC_ESC)} tone="danger" to="/workflows/submissions" />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
        <SlaList
          title={t('slaAboutToBreachTitle')}
          subtitle={t('slaAboutToBreachSub')}
          items={aboutToBreach}
          emptyKey="slaAboutToBreachEmpty"
          showRemaining
          t={t}
          lang={lang}
        />
        <SlaList
          title={t('slaBreachedTitle')}
          subtitle={t('slaBreachedSub')}
          items={breached}
          emptyKey="slaBreachedEmpty"
          showRemaining={false}
          t={t}
          lang={lang}
        />
      </div>
    </Card>
  );
}

function SlaList({
  title, subtitle, items, emptyKey, showRemaining, t, lang,
}: {
  title: string;
  subtitle: string;
  items: LiveWorkflowInstance[];
  emptyKey: string;
  showRemaining: boolean;
  t: (k: string) => string;
  lang: 'en' | 'ar';
}) {
  return (
    <div>
      <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--ink-2)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
        {title}
      </div>
      <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2, marginBottom: 8 }}>{subtitle}</div>

      {items.length === 0
        ? <div style={{ padding: 16, fontSize: 12, color: 'var(--ink-3)', background: 'var(--surface-2)', borderRadius: 'var(--r-sm)', textAlign: 'center' }}>
            {t(emptyKey)}
          </div>
        : (
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            {items.map(item => {
              const remaining = item.sla.daysRemaining;
              const breach = item.sla.percentBreach;
              const trailing = showRemaining
                ? remaining === 0 ? t('slaDueToday') : t('slaDaysRemaining').replace('{n}', String(remaining))
                : `+${breach}% · ${t('slaOverdueByDays').replace('{n}', String(-remaining))}`;
              return (
                <div key={item.id} style={{
                  padding: '10px 0', borderBottom: '1px dashed var(--border)',
                  display: 'grid', gridTemplateColumns: '1fr auto', gap: 8, alignItems: 'center',
                }}>
                  <div style={{ minWidth: 0 }}>
                    <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink-1)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {item.title}
                    </div>
                    <div style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 2 }} className="mono">
                      {item.module} · {item.entityRef} · {item.currentStage}
                    </div>
                    <div style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 2 }}>
                      {t('slaHijriLabel')} {formatHijri(item.dueDate ?? '', lang)}
                    </div>
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 4 }}>
                    <Badge tone={ESCALATION_TONE[item.sla.tier]}>{trailing}</Badge>
                    <span className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>{item.owner}</span>
                  </div>
                </div>
              );
            })}
          </div>
        )}
    </div>
  );
}
