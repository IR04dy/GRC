// Audit-lifecycle tab views — split out of `AuditLifecyclePage.tsx` under F2-16.
//
// F2-203 pushed that page to 816 lines and `sourceFileSizeRatchet` refused it with
// "Split it rather than adding it", which is the right answer: the container owns the
// data and the handlers, these own the rendering. Nothing here talks to the server —
// every mutation arrives as a callback prop, so a decision cannot be written into local
// state from a tab view even by accident.

import { useNavigate } from 'react-router-dom';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import {
  BookOpen, AlertTriangle, CheckCircle2, Wrench, GitBranch,
  AlertOctagon, Send, Eye,
} from 'lucide-react';
import { LifecyclePipeline, type PipelineStage, type PipelineCard, dueLabel } from '../../components/lifecycle/LifecyclePipeline';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import {
  aggregateAuditLifecycle,
  nextFindingStatuses, isFindingStatusManaged, isFindingOpen, findingStatusLabel,
  nextCapaStatuses, isCapaStatusManaged, capaStatusLabel,
  planStatusLabel, isPlanStatusManaged, PLAN_STATUS_TONE,
  SEVERITY_TONE,
  CAPA_STATUS_TONE,
  type AnnualPlan, type Finding, type CapaTask,
  type FindingSeverity, type ManagedFindingStatus, type ManagedCapaStatus,
} from '../../lib/auditLifecycle';

// ─── Findings tab ───────────────────────────────────────────────────────────

/**
 * The button label for advancing INTO each state. Keyed on the target, so adding an edge
 * to FINDING_ALLOWED surfaces as a missing key here rather than as a silently absent
 * button — the four `if`s this replaced could each go stale independently.
 */
const FINDING_ADVANCE_LABEL_KEY: Record<ManagedFindingStatus, string> = {
  OPEN:                  'alcStatus',
  MGMT_RESPONSE:         'alcAwaitMgmt',
  IN_REMEDIATION:        'alcStartRemediation',
  AWAITING_VERIFICATION: 'alcAwaitVerif',
  CLOSED:                'alcCloseFinding',
};

export function FindingsTab({ findings, engagementCode: _ec, onTransition, onCreate, onChainToRisk }: {
  findings: Finding[];
  engagementCode: string;
  /** Only a status the machine declares reachable — see nextFindingStatuses. */
  onTransition: (f: Finding, status: ManagedFindingStatus) => void;
  onCreate: (titleEn: string, titleAr: string, severity: FindingSeverity,
             conditionEn: string, conditionAr: string, ownerRole: string) => void;
  onChainToRisk: (f: Finding) => void;
}) {
  const { t, lang } = useI18n();
  const navigate = useNavigate();
  return (
    <div className="alc-section">
      <Card title={t('alcFindingTitle')} Icon={AlertTriangle}
        action={<Button size="sm" variant="primary" onClick={() => onCreate(
          'New finding', 'ملاحظة جديدة', 'MEDIUM',
          'Condition observed during fieldwork.', 'حالة مرصودة أثناء العمل الميداني.',
          // F2-203: the page used to pass the literal string 'Owner role' here. INTERNAL_AUDIT
          // is a real role in iam.roles and is the default owner of an audit finding until
          // management accepts it; the AC-packet "Owner" column reads this column.
          'INTERNAL_AUDIT')}>{t('alcFindingCreate')}</Button>}>
        <div className="alc-card-sub">
          {t('alcFindingSub')}
        </div>
        {findings.length === 0
          ? <InlineEmpty>{t('alcFindingEmpty')}</InlineEmpty>
          : findings.map(f => (
              <div key={f.id} className="alc-item">
                <div className="alc-item-head">
                  <div className="alc-item-main">
                    <div className="alc-item-badges">
                      <span className="mono alc-item-code">{f.refNumber}</span>
                      <Badge tone={SEVERITY_TONE[f.severity]}>{f.severity}</Badge>
                      <Badge tone={!isFindingOpen(f.status) ? 'success' : f.status === 'OPEN' ? 'danger' : 'warning'}>
                        {findingStatusLabel(f.status, lang)}
                      </Badge>
                      {/* F2-203: 7 of 20 rows in dev and prod carry a status the server's
                          machine has no edge out of, so they cannot be advanced or closed
                          by any route. Say so rather than showing a dead row. */}
                      {!isFindingStatusManaged(f.status) && (
                        <span role="status">
                          <Badge tone="warning">
                            {lang === 'ar' ? 'لا يوجد انتقال مُعلَن' : 'No declared transition'}
                          </Badge>
                        </span>
                      )}
                      {f.targetDate && <Badge tone="info">{t('alcDue')}: <span className="mono">{f.targetDate}</span></Badge>}
                    </div>
                    <div className="alc-item-title--strong">
                      {lang === 'ar' ? f.titleAr : f.titleEn}
                    </div>
                    <div className="alc-item-sub--plain">
                      {f.ownerRole && <>{t('alcOwner')}: <b>{f.ownerRole}</b></>}
                    </div>
                  </div>
                  <div className="alc-item-actions alc-item-actions--wrap">
                    {/* Sprint 11 — open the Risk-Control-Finding pivot view in a new tab. */}
                    <Button size="sm" variant="ghost" Icon={GitBranch}
                            onClick={() => navigate(`/governance/pivot/finding/${f.id}`)}>
                      {lang === 'ar' ? 'محور' : 'Pivot'}
                    </Button>
                    {/* F2-203: these were four hard-coded `f.status === …` conditions — a
                        third copy of the machine, and one the server could disagree with.
                        Derived from the one table now, so a button can only ever offer a
                        transition the server will accept. */}
                    {nextFindingStatuses(f.status).map(next => (
                      <Button key={next} size="sm" variant="primary"
                              Icon={next === 'CLOSED' ? CheckCircle2 : undefined}
                              onClick={() => onTransition(f, next)}>
                        {t(FINDING_ADVANCE_LABEL_KEY[next])}
                      </Button>
                    ))}
                    {(f.severity === 'CRITICAL' || f.severity === 'HIGH') && isFindingOpen(f.status) && (
                      <Button size="sm" variant="ghost" onClick={() => onChainToRisk(f)}>{t('alcChainToRisk')}</Button>
                    )}
                  </div>
                </div>

                {/* Crosswalk */}
                {f.controlCrosswalk.length > 0 && (
                  <div className="alc-crosswalk">
                    <span className="alc-controls-label">{t('alcCrosswalk')}:</span>
                    {f.controlCrosswalk.map(c => (
                      <Badge key={c.controlCode} tone="info">{c.framework}/{c.controlCode}</Badge>
                    ))}
                  </div>
                )}

                {/* Condition / Criteria / Cause / Recommendation */}
                <div className="alc-field-grid alc-field-grid--mb">
                  <FieldBlock label={t('alcCondition')} en={f.conditionEn} ar={f.conditionAr} lang={lang} />
                  <FieldBlock label={t('alcCriteria')}  en={f.criteriaEn}  ar={f.criteriaAr}  lang={lang} />
                  <FieldBlock label={t('alcCause')}     en={f.causeEn}     ar={f.causeAr}     lang={lang} />
                  <FieldBlock label={t('alcRecommendation')} en={f.recommendationEn} ar={f.recommendationAr} lang={lang} />
                </div>

                {/* Mgmt response */}
                {f.mgmtResponseEn && (
                  <div className="alc-mgmt-response" data-accepted={String(!!f.mgmtAccepts)}>
                    <div className="alc-mgmt-head">
                      <span className="alc-mgmt-head-label">
                        {t('alcMgmtResponse')}
                      </span>
                      <Badge tone={f.mgmtAccepts ? 'success' : 'warning'}>
                        {f.mgmtAccepts ? t('alcAccepted') : t('alcDisagreed')}
                      </Badge>
                      {f.mgmtResponseAuthor && <span className="alc-mgmt-head-author">· <b>{f.mgmtResponseAuthor}</b></span>}
                    </div>
                    {lang === 'ar' ? f.mgmtResponseAr : f.mgmtResponseEn}
                  </div>
                )}
              </div>
            ))
        }
      </Card>
    </div>
  );
}

function FieldBlock({ label, en, ar, lang }: { label: string; en: string; ar: string; lang: 'en' | 'ar' }) {
  return (
    <div>
      <div className="alc-field-label">{label}</div>
      <div className="alc-field-value">
        {lang === 'ar' ? ar : en}
      </div>
    </div>
  );
}

// ─── CAPA tab ──────────────────────────────────────────────────────────────

export const CAPA_ADVANCE_LABEL_KEY: Record<ManagedCapaStatus, string> = {
  OPEN:                  'alcStart',
  IN_PROGRESS:           'alcStart',
  AWAITING_VERIFICATION: 'alcSubmit',
  CLOSED:                'alcVerify',
  OVERDUE:               'alcStatus',
};

export function CapaTab({ findings, allCapas, onTransition }: {
  findings: Finding[];
  allCapas: CapaTask[];
  onTransition: (c: CapaTask, status: ManagedCapaStatus) => void;
}) {
  const { t, lang } = useI18n();
  // CAPAs for the engagement's findings.
  // F2-203: this was `new Set(findings.map(f => f.id))` — external ids — while every
  // seeded CAPA carries the finding's REF NUMBER in `finding_id`. `audit_lc.capa_tasks`
  // has no foreign key, and this module's own createCapa writes the id, so the column has
  // two writers with different key spaces and all 12 CAPAs were invisible here.
  const findingKeys = new Set(findings.flatMap(f => [f.id, f.refNumber]));
  const relevantCapas = allCapas.filter(c => findingKeys.has(c.findingId));

  return (
    <div className="alc-section">
      <Card title={t('alcCapaTitle')} Icon={Wrench}>
        <div className="alc-card-sub">
          {t('alcCapaSub')}
        </div>
        {relevantCapas.length === 0
          ? <InlineEmpty>{t('alcCapaEmpty')}</InlineEmpty>
          : (
            <table className="tbl tbl--full">
              <thead>
                <tr>
                  <th>{t('alcCapaId')}</th>
                  <th>{t('alcCapaKind')}</th>
                  <th>{t('alcCapaTitleCol')}</th>
                  <th>{t('alcCapaOwner')}</th>
                  <th>{t('alcCapaTarget')}</th>
                  <th>{t('alcCapaStatus')}</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {relevantCapas.map(c => {
                  const overdue = new Date(c.targetDate) < new Date() && c.status !== 'CLOSED';
                  return (
                    <tr key={c.id}>
                      <td className="mono alc-cell-evidence">{c.id}</td>
                      <td>
                        <Badge tone={c.kind === 'CORRECTIVE' ? 'warning' : 'info'}>
                          {c.kind === 'CORRECTIVE' ? t('alcCorrective') : t('alcPreventive')}
                        </Badge>
                      </td>
                      <td className="alc-cell-narrow">
                        <div className="alc-cell-strong">{lang === 'ar' ? c.titleAr : c.titleEn}</div>
                        {c.evidenceRef && <div className="mono alc-cell-evidence">{c.evidenceRef}</div>}
                      </td>
                      <td className="alc-item-sub--plain">{c.ownerRole}</td>
                      <td className={`mono alc-item-sub--plain${overdue ? ' alc-cell-due-late' : ''}`}>
                        {c.targetDate}
                      </td>
                      <td>
                        <Badge tone={overdue ? 'danger' : (CAPA_STATUS_TONE[c.status] ?? 'neutral')}>
                          {overdue ? t('alcCapaOverdueBadge') : capaStatusLabel(c.status, lang)}
                        </Badge>
                        {/* F2-203: 3 of the 12 CAPAs hold VERIFIED, which CAPA_TRANSITIONS has
                            no edge out of. Indexing the label map directly on it was the same
                            TypeError as the Plan tab — dodged only when the row was ALSO past
                            its target date and the "overdue" branch won this ternary. */}
                        {!isCapaStatusManaged(c.status) && (
                          <span role="status">
                            <Badge tone="warning">
                              {lang === 'ar' ? 'لا يوجد انتقال مُعلَن' : 'No declared transition'}
                            </Badge>
                          </span>
                        )}
                      </td>
                      <td>
                        {nextCapaStatuses(c.status).map(to => (
                          <Button key={to} size="sm" variant={to === 'CLOSED' ? 'primary' : 'ghost'}
                                  Icon={to === 'CLOSED' ? CheckCircle2 : undefined}
                                  onClick={() => onTransition(c, to)}>
                            {t(CAPA_ADVANCE_LABEL_KEY[to])}
                          </Button>
                        ))}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
      </Card>
    </div>
  );
}

// ─── AC packet tab ─────────────────────────────────────────────────────────

export function PacketTab({ plan, findings, capas: _capas, stats }: { plan: AnnualPlan | null; findings: Finding[]; capas: CapaTask[]; stats: ReturnType<typeof aggregateAuditLifecycle> }) {
  const { t, lang } = useI18n();
  const sevCounts = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'OBSERVATION'].map(s => ({
    sev: s,
    count: findings.filter(f => f.severity === s).length,
  }));
  return (
    <div className="alc-section">
      <Card title={t('alcPacketTitle')} Icon={BookOpen}>
        <div className="alc-card-sub">
          {t('alcPacketSub')}
        </div>

        <div className="alc-packet-grid">
          <div>
            <div className="alc-packet-head">{t('alcPacketSevSection')}</div>
            <table className="tbl tbl--full">
              <thead>
                <tr><th>{t('alcSev')}</th><th>{t('alcCount')}</th></tr>
              </thead>
              <tbody>
                {sevCounts.map(s => (
                  <tr key={s.sev}>
                    <td>
                      <Badge tone={s.sev === 'CRITICAL' || s.sev === 'HIGH' ? 'danger' : s.sev === 'MEDIUM' ? 'warning' : 'info'}>{s.sev}</Badge>
                    </td>
                    <td className="mono alc-cell-strong">{s.count}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div>
            <div className="alc-packet-head">{t('alcPacketCapaSection')}</div>
            <div className={`stat-tile stat-tile-${stats.closureRate >= 80 ? 'success' : 'warning'} alc-packet-tile`}>
              <div className="stat-tile-label">{t('alcCapaClosureRate')}</div>
              <div className="stat-tile-value">{stats.closureRate}%</div>
              <div className="alc-packet-tile-meta">
                <span>{t('alcCapaClosed')}: <b>{stats.capaClosed}</b></span>
                <span>{t('alcCapaTotal')}: <b>{stats.capaTotal}</b></span>
                <span className={stats.capaOverdue > 0 ? 'alc-packet-overdue' : undefined}>
                  {t('alcCapaOverdueLabel')}: <b>{stats.capaOverdue}</b>
                </span>
              </div>
            </div>
          </div>
        </div>

        <div className="alc-packet-section-head">{t('alcPacketPlanSection')}</div>
        {!plan ? (
          <InlineEmpty>
            {lang === 'ar'
              ? 'لا توجد خطة تدقيق سنوية مُسجَّلة لهذه الجهة.'
              : 'No annual audit plan is recorded for this organisation.'}
          </InlineEmpty>
        ) : (
          <div className="alc-packet-plan">
            <div><b>{plan.id}</b> · <Badge tone={PLAN_STATUS_TONE[plan.status] ?? 'neutral'}>{planStatusLabel(plan.status, lang)}</Badge>
              {!isPlanStatusManaged(plan.status) && (
                <span role="status"> <Badge tone="warning">
                  {lang === 'ar' ? 'لا يوجد انتقال مُعلَن' : 'No declared transition'}
                </Badge></span>
              )}</div>
            {plan.acApprovedBy && <div className="alc-packet-plan-row">{t('alcApprovedBy')}: {plan.acApprovedBy}</div>}
            <div className="alc-packet-plan-meta">
              <span>Q1: {plan.q1.length}</span>
              <span>Q2: {plan.q2.length}</span>
              <span>Q3: {plan.q3.length}</span>
              <span>Q4: {plan.q4.length}</span>
              <span>· {plan.budgetHours.toLocaleString()} {t('alcHours')}</span>
            </div>
          </div>
        )}

        <div className="alc-packet-hint">
          {t('alcPacketHint')}
        </div>
      </Card>
    </div>
  );
}

// ─── Findings pipeline (interactive kanban) ────────────────────────────────

export function FindingsPipeline({ allFindings, onAdvance, onCardClick }: {
  allFindings: Finding[];
  onAdvance: (id: string, fromStatus: string, toStatus: string | null) => void;
  onCardClick: (id: string) => void;
}) {
  const stages: PipelineStage[] = [
    { code: 'OPEN',                  labelEn: 'Open',                 labelAr: 'مفتوحة',           tone: 'danger',  Icon: AlertOctagon },
    { code: 'MGMT_RESPONSE',         labelEn: 'Mgmt response',        labelAr: 'ردّ الإدارة',      tone: 'warning', Icon: Send },
    { code: 'IN_REMEDIATION',        labelEn: 'In remediation',       labelAr: 'قيد المعالجة',     tone: 'info',    Icon: Wrench },
    { code: 'AWAITING_VERIFICATION', labelEn: 'Awaiting verification', labelAr: 'بانتظار التحقق',  tone: 'warning', Icon: Eye },
    { code: 'CLOSED',                labelEn: 'Closed',               labelAr: 'مُغلَقة',          tone: 'success', Icon: CheckCircle2 },
  ];
  const cards: PipelineCard[] = allFindings.map(f => {
    const due = dueLabel(f.targetDate, () => '');
    return {
      id: f.id, status: f.status, refCode: f.refNumber,
      titleEn: f.titleEn, titleAr: f.titleAr,
      subtitleEn: f.engagementCode + (f.ownerRole ? ' · ' + f.ownerRole : ''),
      subtitleAr: f.engagementCode + (f.ownerRole ? ' · ' + f.ownerRole : ''),
      badges: [
        { label: f.severity, tone: f.severity === 'CRITICAL' || f.severity === 'HIGH' ? 'danger' : f.severity === 'MEDIUM' ? 'warning' : 'info' as const },
        ...f.controlCrosswalk.slice(0, 1).map(c => ({ label: c.framework, tone: 'info' as const })),
      ],
      urgent: due?.urgent && f.status !== 'CLOSED',
      dueLabel: due?.label,
    };
  });
  return (
    <LifecyclePipeline
      stages={stages}
      cards={cards}
      introEn="Live findings pipeline across all engagements. Click a card to drill into its engagement; click the arrow to advance the finding to the next stage."
      introAr="مسار الملاحظات الحيّ عبر جميع الارتباطات. انقر على بطاقة للانتقال إلى ارتباطها، وانقر السهم لتقدّم الملاحظة إلى المرحلة التالية."
      onAdvance={onAdvance}
      onCardClick={onCardClick}
      emptyEn="No findings raised yet across any engagement."
      emptyAr="لا ملاحظات مرفوعة بعد عبر أي ارتباط."
    />
  );
}
