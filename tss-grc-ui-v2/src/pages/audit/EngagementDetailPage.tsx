import { useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  Briefcase, ScrollText, AlertTriangle, FileCheck2,
  CalendarDays, ArrowRight, Play, Send, Lock, RefreshCw,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { DetailHeader } from '../../components/layout/DetailHeader';
import { DetailLayout } from '../../components/layout/DetailLayout';
import { RailField, RailFieldGroup } from '../../components/layout/DetailRail';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { EmptyState } from '../../components/ui/EmptyState';
import {
  AUDIT_ENGAGEMENTS, COMPLIANCE_FINDINGS, FRAMEWORKS,
} from '../compliance/mockCompliance';
import { EVIDENCE } from '../evidence/mockEvidence';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { CommsPanel } from '../../components/comms/CommsPanel';
import { EngagementMilestonePanel } from '../../components/audit/EngagementMilestonePanel';
import { HijriDate } from '../../components/ui/HijriDate';

type TabId = 'timeline' | 'scope' | 'findings' | 'workpapers' | 'comms';

const STATUS_TONE: Record<string, BadgeTone> = {
  PLANNED: 'info', FIELDWORK: 'warning', REPORT_ISSUED: 'accent',
  CLOSED: 'success',
};

export function EngagementDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { t } = useI18n();
  const toast = useToast();
  const [tab, setTab] = useState<TabId>('timeline');

  const seed = useMemo(
    () => AUDIT_ENGAGEMENTS.find(e => e.id === Number(id)) ?? null,
    [id],
  );
  const [statusOverlay, setStatusOverlay] = useState<string | null>(null);
  const engagement = seed ? { ...seed, status: statusOverlay ?? seed.status } : null;

  function transition(toStatus: string, msgKey: string) {
    if (!engagement) return;
    setStatusOverlay(toStatus);
    toast.success(t(msgKey));
  }

  if (!engagement) {
    return (
      <Shell title={t('engTitle')}>
        <div style={{ padding: 32, textAlign: 'center', color: 'var(--danger-fg)' }}>
          {t('engNotFound')} <Link to="/audit-workspace">{t('engReturnToWorkspace')}</Link>
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

  const tabs: TabItem<TabId>[] = [
    { id: 'timeline',   label: t('engTabTimeline') },
    { id: 'scope',      label: t('engTabScope') },
    { id: 'findings',   label: `${t('engTabFindings')} (${findings.length})` },
    { id: 'workpapers', label: `${t('engTabWorkpapers')} (${evidence.length})` },
    { id: 'comms',      label: t('detailTabComms') },
  ];

  return (
    <Shell title={t('engTitle')} sub={e.framework?.replace(/_/g, ' ')}>
      <DetailHeader
        breadcrumbs={[
          { label: t('modAuditWorkspace'), to: '/audit-workspace' },
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
        actions={<>
          {e.status === 'PLANNED' && <Button variant="primary" Icon={Play} onClick={() => transition('FIELDWORK', 'engActStartFieldwork')}>{t('engActStartFieldwork')}</Button>}
          {e.status === 'FIELDWORK' && <Button variant="primary" Icon={Send} onClick={() => transition('REPORT_ISSUED', 'engActIssueReport')}>{t('engActIssueReport')}</Button>}
          {e.status === 'REPORT_ISSUED' && <Button variant="primary" Icon={Lock} onClick={() => transition('CLOSED', 'engActCloseEngagement')}>{t('engActCloseEngagement')}</Button>}
          {e.status === 'CLOSED' && <Button variant="secondary" Icon={RefreshCw} onClick={() => toast.info(t('engActFollowupHint'))}>{t('engActScheduleFollowup')}</Button>}
        </>}
      />

      <DetailLayout
        rail={<>
          <Card title={t('detailAtAGlance')} Icon={Briefcase}>
            <RailFieldGroup>
              <RailField
                label={t('fldStatus')}
                accent
                value={<Badge tone={STATUS_TONE[e.status] ?? 'neutral'} dot>{e.status.replace(/_/g, ' ')}</Badge>}
              />
              <RailField label={t('fldCode')}        value={e.engagementCode ?? '—'} mono />
              <RailField label={t('engRailType')}    value={e.auditType.replace(/_/g, ' ')} />
              <RailField label={t('engRailFramework')} value={(e.framework ?? '').replace(/_/g, ' ')} />
              <RailField label={t('engRailFirm')}    value={e.auditorFirm ?? '—'} />
              <RailField label={t('engRailLead')}    value={e.leadAuditor ?? '—'} />
              <RailField label={t('engRailFindings')} value={String(findings.length)} mono />
            </RailFieldGroup>
          </Card>

          <Card title={t('engCardSchedule')} Icon={CalendarDays}>
            <RailFieldGroup>
              <RailField label={t('engRailLetter')}    value={<HijriDate iso={e.engagementLetterDate} layout="stacked" mono />} />
              <RailField label={t('engRailOpening')}   value={<HijriDate iso={e.openingMeetingDate} layout="stacked" mono />} />
              <RailField label={t('engRailFieldwork')} value={e.fieldworkStartDate && e.fieldworkEndDate
                ? <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                    <HijriDate iso={e.fieldworkStartDate} layout="stacked" mono />
                    <span style={{ fontSize: 10, color: 'var(--ink-3)' }}>→</span>
                    <HijriDate iso={e.fieldworkEndDate} layout="stacked" mono />
                  </div>
                : <HijriDate iso={e.fieldworkStartDate} layout="stacked" mono />} />
              <RailField label={t('engRailReport')}    value={<HijriDate iso={e.reportIssuedDate} layout="stacked" mono />} />
              <RailField label={t('engRailClosure')}   value={<HijriDate iso={e.closureDate} layout="stacked" mono />} />
              <RailField label={t('engRailFollowup')}  value={<HijriDate iso={e.nextFollowupDate} layout="stacked" mono />} />
            </RailFieldGroup>
          </Card>
        </>}
        main={<>
          <Tabs items={tabs} active={tab} onChange={setTab} />

          {tab === 'timeline' && (
            // Sprint 4 — replaces the static milestone checklist with a
            // live timeline that runs the current milestone through the
            // SLA engine (Saudi business calendar, Hijri tooltip,
            // escalation tier).
            <EngagementMilestonePanel engagement={e} />
          )}

          {tab === 'scope' && (
            <Card title={t('engCardScope')} Icon={ScrollText}>
              <div style={{ fontSize: 13, color: 'var(--ink-1)', lineHeight: 1.6, whiteSpace: 'pre-wrap' }}>
                {e.scopeSummary ?? t('engScopeMissing')}
              </div>
            </Card>
          )}

          {tab === 'findings' && (
            <Card title={`${t('engFindingsHeader')} ${(e.framework ?? '').replace(/_/g, ' ')}`} Icon={AlertTriangle}
              action={<Badge tone={findings.length > 0 ? 'warning' : 'success'}>{findings.length}</Badge>}>
              {findings.length === 0
                ? <EmptyState Icon={AlertTriangle} title={t('engFindingsEmpty')}
                    sub={t('engFindingsEmptySub')} />
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
            <Card title={`${t('engWorkpapersHeader')} ${(e.framework ?? '').replace(/_/g, ' ')}`} Icon={FileCheck2}
              action={<Badge tone="primary">{evidence.length}</Badge>}>
              {evidence.length === 0
                ? <EmptyState Icon={FileCheck2} title={t('engWorkpapersEmpty')}
                    sub={t('engWorkpapersEmptySub')} />
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

          {tab === 'comms' && (
            <CommsPanel module="AUDIT" />
          )}
        </>}
      />
    </Shell>
  );
}
