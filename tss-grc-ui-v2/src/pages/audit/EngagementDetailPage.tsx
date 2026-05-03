import { useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  Briefcase, Stamp, ScrollText, AlertTriangle, FileCheck2,
  CalendarDays, Activity, ArrowRight,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { DetailHeader } from '../../components/layout/DetailHeader';
import { DetailLayout } from '../../components/layout/DetailLayout';
import { RailField, RailFieldGroup } from '../../components/layout/DetailRail';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { EmptyState } from '../../components/ui/EmptyState';
import {
  AUDIT_ENGAGEMENTS, COMPLIANCE_FINDINGS, FRAMEWORKS,
} from '../compliance/mockCompliance';
import { EVIDENCE } from '../evidence/mockEvidence';
import { useI18n } from '../../lib/i18n';

type TabId = 'timeline' | 'scope' | 'findings' | 'workpapers';

const STATUS_TONE: Record<string, BadgeTone> = {
  PLANNED: 'info', FIELDWORK: 'warning', REPORT_ISSUED: 'accent',
  CLOSED: 'success',
};

interface Milestone {
  key: string;
  labelEn: string;
  date?: string;
  Icon: typeof CalendarDays;
}

export function EngagementDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { t } = useI18n();
  void t;
  const [tab, setTab] = useState<TabId>('timeline');

  const engagement = useMemo(
    () => AUDIT_ENGAGEMENTS.find(e => e.id === Number(id)) ?? null,
    [id],
  );

  if (!engagement) {
    return (
      <Shell title="Audit Engagement">
        <div style={{ padding: 32, textAlign: 'center', color: 'var(--danger-fg)' }}>
          Engagement not found. <Link to="/audit-workspace">Return to workspace</Link>
        </div>
      </Shell>
    );
  }

  const e = engagement;
  const frameworkId = useMemo(
    () => FRAMEWORKS.find(fw => fw.code === e.framework)?.id,
    [e.framework],
  );
  const findings = COMPLIANCE_FINDINGS.filter(f => frameworkId != null && f.frameworkId === frameworkId);
  const evidence = EVIDENCE.filter(ev => !!e.framework && ev.frameworkCode === e.framework);

  const milestones: Milestone[] = [
    { key: 'letter',     labelEn: 'Engagement letter',  date: e.engagementLetterDate,  Icon: ScrollText  },
    { key: 'opening',    labelEn: 'Opening meeting',    date: e.openingMeetingDate,    Icon: CalendarDays },
    { key: 'fwStart',    labelEn: 'Fieldwork start',    date: e.fieldworkStartDate,    Icon: Activity    },
    { key: 'fwEnd',      labelEn: 'Fieldwork end',      date: e.fieldworkEndDate,      Icon: Activity    },
    { key: 'report',     labelEn: 'Report issued',      date: e.reportIssuedDate,      Icon: ScrollText  },
    { key: 'closure',    labelEn: 'Closure',            date: e.closureDate,           Icon: Stamp       },
    { key: 'followup',   labelEn: 'Next follow-up',     date: e.nextFollowupDate,      Icon: CalendarDays },
  ];

  const tabs: TabItem<TabId>[] = [
    { id: 'timeline',   label: 'Timeline' },
    { id: 'scope',      label: 'Scope' },
    { id: 'findings',   label: `Findings (${findings.length})` },
    { id: 'workpapers', label: `Workpapers (${evidence.length})` },
  ];

  return (
    <Shell title="Audit Engagement" sub={e.framework?.replace(/_/g, ' ')}>
      <DetailHeader
        breadcrumbs={[
          { label: 'Audit Workspace', to: '/audit-workspace' },
          { label: e.engagementCode ?? `#${e.id}` },
        ]}
        eyebrow={e.engagementCode}
        title={e.titleEn}
        sub={e.scopeSummary}
        badges={<>
          <Badge tone={STATUS_TONE[e.status] ?? 'neutral'} dot>{e.status.replace(/_/g, ' ')}</Badge>
          <Badge tone="primary">{e.auditType.replace(/_/g, ' ')}</Badge>
          {e.framework && <Badge tone="info">{e.framework.replace(/_/g, ' ')}</Badge>}
        </>}
      />

      <DetailLayout
        rail={<>
          <Card title="At a glance" Icon={Briefcase}>
            <RailFieldGroup>
              <RailField
                label="Status"
                accent
                value={<Badge tone={STATUS_TONE[e.status] ?? 'neutral'} dot>{e.status.replace(/_/g, ' ')}</Badge>}
              />
              <RailField label="Code"          value={e.engagementCode ?? '—'} mono />
              <RailField label="Type"          value={e.auditType.replace(/_/g, ' ')} />
              <RailField label="Framework"     value={(e.framework ?? '').replace(/_/g, ' ')} />
              <RailField label="Auditor firm"  value={e.auditorFirm ?? '—'} />
              <RailField label="Lead auditor"  value={e.leadAuditor ?? '—'} />
              <RailField label="Findings"      value={String(findings.length)} mono />
            </RailFieldGroup>
          </Card>

          <Card title="Schedule" Icon={CalendarDays}>
            <RailFieldGroup>
              <RailField label="Letter"         value={e.engagementLetterDate ?? '—'} mono />
              <RailField label="Opening"        value={e.openingMeetingDate ?? '—'}  mono />
              <RailField label="Fieldwork"      value={e.fieldworkStartDate && e.fieldworkEndDate
                ? `${e.fieldworkStartDate} → ${e.fieldworkEndDate}`
                : (e.fieldworkStartDate ?? '—')} mono />
              <RailField label="Report"         value={e.reportIssuedDate ?? '—'}    mono />
              <RailField label="Closure"        value={e.closureDate ?? '—'}         mono />
              <RailField label="Follow-up"      value={e.nextFollowupDate ?? '—'}    mono />
            </RailFieldGroup>
          </Card>
        </>}
        main={<>
          <Tabs items={tabs} active={tab} onChange={setTab} />

          {tab === 'timeline' && (
            <Card title="Engagement timeline" Icon={Activity}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
                {milestones.map((m, i) => {
                  const passed = !!m.date;
                  const M = m.Icon;
                  return (
                    <div key={m.key} style={{
                      display: 'grid',
                      gridTemplateColumns: '32px 1fr 130px',
                      gap: 12,
                      alignItems: 'center',
                      padding: '10px 0',
                      borderBottom: i < milestones.length - 1 ? '1px dashed var(--border)' : 'none',
                    }}>
                      <span style={{
                        width: 28, height: 28,
                        borderRadius: '50%',
                        background: passed ? 'var(--success)' : 'var(--surface-3)',
                        color: passed ? '#fff' : 'var(--ink-3)',
                        display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
                      }}>
                        <M size={14} />
                      </span>
                      <div>
                        <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)' }}>{m.labelEn}</div>
                        <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                          {passed ? 'Recorded' : 'Pending'}
                        </div>
                      </div>
                      <span className="mono" style={{
                        fontSize: 12,
                        color: passed ? 'var(--ink-1)' : 'var(--ink-4)',
                        fontWeight: 600,
                        textAlign: 'end',
                      }}>{m.date ?? '—'}</span>
                    </div>
                  );
                })}
              </div>
            </Card>
          )}

          {tab === 'scope' && (
            <Card title="Scope summary" Icon={ScrollText}>
              <div style={{ fontSize: 13, color: 'var(--ink-1)', lineHeight: 1.6, whiteSpace: 'pre-wrap' }}>
                {e.scopeSummary ?? 'Scope summary not yet recorded for this engagement.'}
              </div>
            </Card>
          )}

          {tab === 'findings' && (
            <Card title={`Findings raised on ${(e.framework ?? '').replace(/_/g, ' ')}`} Icon={AlertTriangle}
              action={<Badge tone={findings.length > 0 ? 'warning' : 'success'}>{findings.length}</Badge>}>
              {findings.length === 0
                ? <EmptyState Icon={AlertTriangle} title="No findings yet"
                    sub="No compliance findings are linked to this engagement's framework." />
                : <div style={{ display: 'flex', flexDirection: 'column' }}>
                    {findings.map(f => (
                      <Link key={f.id} to={`/findings/${f.id}`} style={{
                        display: 'grid',
                        gridTemplateColumns: '1fr auto auto auto',
                        gap: 10, alignItems: 'center',
                        padding: '8px 0', borderBottom: '1px dashed var(--border)',
                        textDecoration: 'none', color: 'inherit',
                      }}>
                        <div style={{ minWidth: 0 }}>
                          <div style={{ fontSize: 13, fontWeight: 600,
                                        whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                            {f.title}
                          </div>
                          <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                            {f.owner ?? '—'} · {f.clauseRef ?? '—'} · due {f.dueDate ?? '—'}
                          </div>
                        </div>
                        <Badge tone={f.severity === 'CRITICAL' ? 'danger' : f.severity === 'HIGH' ? 'warning' : f.severity === 'MEDIUM' ? 'info' : 'neutral'}>
                          {f.severity}
                        </Badge>
                        <Badge tone={f.status === 'CLOSED' ? 'success' : 'warning'} dot>{f.status}</Badge>
                        <ArrowRight size={14} style={{ color: 'var(--ink-3)' }} />
                      </Link>
                    ))}
                  </div>}
            </Card>
          )}

          {tab === 'workpapers' && (
            <Card title={`Evidence on ${(e.framework ?? '').replace(/_/g, ' ')}`} Icon={FileCheck2}
              action={<Badge tone="primary">{evidence.length}</Badge>}>
              {evidence.length === 0
                ? <EmptyState Icon={FileCheck2} title="No workpapers"
                    sub="No evidence items are tagged to this engagement's framework." />
                : <div style={{ display: 'flex', flexDirection: 'column' }}>
                    {evidence.map(ev => (
                      <div key={ev.id} style={{
                        display: 'grid',
                        gridTemplateColumns: '110px 1fr 110px 90px 80px',
                        gap: 10, alignItems: 'center',
                        padding: '8px 0', borderBottom: '1px dashed var(--border)',
                      }}>
                        <span className="mono" style={{ fontSize: 12, color: 'var(--ink-3)' }}>{ev.evidenceCode}</span>
                        <span style={{ fontSize: 13, fontWeight: 600,
                                       whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                          {ev.titleEn}
                        </span>
                        <Badge tone="neutral">{(ev.evidenceType ?? '').replace(/_/g, ' ')}</Badge>
                        <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>{ev.expiryDate ?? '—'}</span>
                        <Badge tone={ev.status === 'APPROVED' ? 'success' : ev.status === 'PENDING_REVIEW' ? 'warning' : 'neutral'}>
                          {(ev.status ?? '').replace(/_/g, ' ')}
                        </Badge>
                      </div>
                    ))}
                  </div>}
            </Card>
          )}
        </>}
      />
    </Shell>
  );
}
