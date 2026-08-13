// Risk-lifecycle card views — split out of `RiskLifecyclePage.tsx` under F2-16.
//
// F2-204 took that page to 807 lines and `sourceFileSizeRatchet` refused it with "Split it
// rather than adding it", the same way it refused `AuditLifecyclePage` in F2-203. The
// container owns the data and the server calls; these own the rendering. Nothing here talks to
// a server — every mutation arrives as a callback prop, so a decision cannot be written into
// local state from a card even by accident.

import { InlineEmpty } from '../../components/ui/InlineEmpty';
import {
  GitBranch, Send, ShieldCheck, ThumbsDown, ThumbsUp, Play,
  FileText as FileTextIcon, Send as SendIcon, ThumbsUp as ThumbsUpIcon,
  CheckCircle2 as CheckCircle2Icon, ThumbsDown as ThumbsDownIcon,
} from 'lucide-react';
import { LifecyclePipeline, type PipelineStage, type PipelineCard } from '../../components/lifecycle/LifecyclePipeline';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import {
  controlsForRisk,
  STRATEGY_LABEL, TREATMENT_STATUS_LABEL, TREATMENT_STATUS_TONE,
  ACCEPTANCE_STATUS_LABEL, ACCEPTANCE_STATUS_TONE,
  type TreatmentPlan, type AcceptanceRequest,
} from '../../lib/riskLifecycle';

// ─── Treatment card ─────────────────────────────────────────────────────────

export function TreatmentCard({ riskId: _riskId, plans, onCreate, onSubmit, onApprove, onReject, onProgress }: {
  riskId: number;
  plans: TreatmentPlan[];
  onCreate: () => void;
  onSubmit: (p: TreatmentPlan) => void;
  onApprove: (p: TreatmentPlan) => void;
  onReject: (p: TreatmentPlan) => void;
  onProgress: (p: TreatmentPlan) => void;
}) {
  const { t, lang } = useI18n();
  return (
    <div className="rlc-section">
      <Card title={t('rlcTreatTitle')} Icon={ShieldCheck}
        action={<Button size="sm" variant="secondary" onClick={onCreate}>{t('rlcCreatePlan')}</Button>}>
        <div className="rlc-card-intro">
          {t('rlcTreatSub')}
        </div>
        {plans.length === 0
          ? <InlineEmpty>{t('rlcTreatEmpty')}</InlineEmpty>
          : plans.map(p => {
              const completedActions = (p.actions ?? []).filter(a => a.status === 'COMPLETED').length;
              const totalReduction = (p.actions ?? []).filter(a => a.status === 'COMPLETED').reduce((acc, a) => acc + a.expectedRiskReduction, 0);
              return (
                <div key={p.id} className="rlc-plan-card">
                  <div className="rlc-plan-card__head">
                    <div className="rlc-plan-card__main">
                      <div className="rlc-plan-card__chips">
                        <span className="mono rlc-plan-card__id">{p.id}</span>
                        <Badge tone={TREATMENT_STATUS_TONE[p.status]}>{TREATMENT_STATUS_LABEL[p.status][lang]}</Badge>
                        <Badge tone="info">{STRATEGY_LABEL[p.strategy][lang]}</Badge>
                        {p.costSarK > 0 && <Badge tone="primary">{p.costSarK.toLocaleString()}k SAR</Badge>}
                      </div>
                      <div className="rlc-plan-card__rationale">
                        {lang === 'ar' ? p.rationaleAr : p.rationaleEn}
                      </div>
                      {p.costBenefitEn && (
                        <div className="rlc-plan-card__cb">
                          {lang === 'ar' ? p.costBenefitAr : p.costBenefitEn}
                        </div>
                      )}
                      {p.approvedBy && (
                        <div className="rlc-plan-card__approval">
                          {t('rlcApprovedBy')}: {p.approvedBy}
                        </div>
                      )}
                    </div>
                    <div className="rlc-plan-card__actions">
                      {p.status === 'DRAFT' && <Button size="sm" variant="primary" Icon={Send} onClick={() => onSubmit(p)}>{t('rlcSubmit')}</Button>}
                      {p.status === 'SUBMITTED' && (
                        <>
                          <Button size="sm" variant="primary" Icon={ThumbsUp} onClick={() => onApprove(p)}>{t('rlcApprove')}</Button>
                          <Button size="sm" variant="ghost" Icon={ThumbsDown} onClick={() => onReject(p)}>{t('rlcReject')}</Button>
                        </>
                      )}
                      {p.status === 'APPROVED' && <Button size="sm" variant="primary" onClick={() => onProgress(p)}>{t('rlcStartWork')}</Button>}
                    </div>
                  </div>
                  {/* Progress bar */}
                  {(p.actions ?? []).length > 0 && (
                    <div className="rlc-progress-wrap">
                      <div className="rlc-progress-head">
                        <span>{t('rlcActions')} {completedActions}/{(p.actions ?? []).length}</span>
                        <span>{t('rlcExpectedReduction')}: {(totalReduction * 100).toFixed(0)}%</span>
                      </div>
                      <div className="rlc-progress-track">
                        <div className="rlc-progress-fill" style={{ width: `${(completedActions / (p.actions ?? []).length) * 100}%` }} />
                      </div>
                    </div>
                  )}
                  {/* Actions — real server rows (all 10 of `risk_lc.treatment_actions`), read
                      only, because the controller exposes no way to change one. */}
                  {(p.actions ?? []).length > 0 && (
                    <div className="rlc-action-list">
                      {(p.actions ?? []).map(a => (
                        <div key={a.id} className="rlc-action-row">
                          <div>
                            <div className="rlc-action-title">{lang === 'ar' ? a.titleAr : a.titleEn}</div>
                            <div className="rlc-action-meta">
                              {a.ownerRole} · {t('rlcDue')} {a.targetDate} · −{(a.expectedRiskReduction * 100).toFixed(0)}%
                            </div>
                          </div>
                          <Badge tone={a.status === 'COMPLETED' ? 'success' : a.status === 'IN_PROGRESS' ? 'info' : a.status === 'BLOCKED' ? 'danger' : 'neutral'}>
                            {a.status}
                          </Badge>
                          {/* ⚠⚠ F2-204: the "Mark done" control is gone. `updateActionStatus`
                              wrote to localStorage, and `TreatmentPlanController` has no actions
                              sub-resource to replace it with — so no client can change an
                              action's status, and a button that reports one complete while
                              writing nothing is F2-201's defect. The rows themselves ARE real:
                              the server returns all 10, which an earlier draft of this comment
                              denied. Read-only until the endpoint exists. Filed. */}
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              );
            })
        }
      </Card>
    </div>
  );
}

// ─── Acceptance card ────────────────────────────────────────────────────────

export function AcceptanceCard({ riskId: _riskId, risk: _risk, accepts, onCreate, onSubmitToCro, onPromoteToBoard, onApprove }: {
  riskId: number;
  risk: any;
  accepts: AcceptanceRequest[];
  onCreate: () => void;
  onSubmitToCro: (a: AcceptanceRequest) => void;
  onPromoteToBoard: (a: AcceptanceRequest) => void;
  onApprove: (a: AcceptanceRequest) => void;
}) {
  const { t, lang } = useI18n();
  return (
    <div className="rlc-section">
      <Card title={t('rlcAcceptTitle')} Icon={GitBranch}
        action={<Button size="sm" variant="secondary" onClick={onCreate}>{t('rlcCreateAcceptance')}</Button>}>
        <div className="rlc-card-intro">
          {t('rlcAcceptSub')}
        </div>
        {accepts.length === 0
          ? <InlineEmpty>{t('rlcAcceptEmpty')}</InlineEmpty>
          : accepts.map(a => (
              <div key={a.id} className="rlc-accept-card">
                <div className="rlc-accept-card__head">
                  <div className="rlc-accept-card__main">
                    <div className="rlc-accept-card__chips">
                      <span className="mono rlc-plan-card__id">{a.id}</span>
                      <Badge tone={ACCEPTANCE_STATUS_TONE[a.status]}>{ACCEPTANCE_STATUS_LABEL[a.status][lang]}</Badge>
                      <Badge tone="warning">{t('rlcResidual')}: {a.residualScore}</Badge>
                      <Badge tone="info">{t('rlcAppetite')}: ≤ {a.appetiteUpper}</Badge>
                    </div>
                    <div className="rlc-accept-card__rationale">
                      {lang === 'ar' ? a.rationaleAr : a.rationaleEn}
                    </div>
                    <div className="rlc-accept-card__expiry">
                      {t('rlcExpiresAt')}: <span className="mono">{a.expiresAt}</span>
                      {a.expiresAtHijri && <> · <span className="mono">{a.expiresAtHijri}</span></>}
                    </div>
                  </div>
                  <div className="rlc-accept-card__actions">
                    {a.status === 'DRAFT' && <Button size="sm" variant="primary" Icon={Send} onClick={() => onSubmitToCro(a)}>{t('rlcSendToCro')}</Button>}
                    {a.status === 'CRO_REVIEW' && <Button size="sm" variant="primary" onClick={() => onPromoteToBoard(a)}>{t('rlcPromoteBoard')}</Button>}
                    {a.status === 'BOARD_APPROVAL' && <Button size="sm" variant="primary" Icon={ThumbsUp} onClick={() => onApprove(a)}>{t('rlcBoardSign')}</Button>}
                  </div>
                </div>
                {/* Signatures */}
                <div className="rlc-signatures">
                  <div>
                    <div className="rlc-signature-label">{t('rlcCroSignoff')}</div>
                    {a.croSignedBy
                      ? <div><b>{a.croSignedBy}</b><div className="mono rlc-signature-date">{a.croSignedAt?.slice(0, 10)}</div></div>
                      : <span className="rlc-signature-pending">{t('rlcPending')}</span>}
                  </div>
                  <div>
                    <div className="rlc-signature-label">{t('rlcBoardSignoff')}</div>
                    {a.boardSignedBy
                      ? <div><b>{a.boardSignedBy}</b><div className="mono rlc-signature-date">{a.boardSignedAt?.slice(0, 10)}</div></div>
                      : <span className="rlc-signature-pending">{t('rlcPending')}</span>}
                  </div>
                </div>
              </div>
            ))
        }
      </Card>
    </div>
  );
}

// ─── Control linkage card ──────────────────────────────────────────────────

export function ControlLinkageCard({ controls }: { controls: ReturnType<typeof controlsForRisk> }) {
  const { t, lang } = useI18n();
  return (
    <div className="rlc-section">
      <Card title={t('rlcControlsTitle')} Icon={ShieldCheck}>
        <div className="rlc-card-intro">
          {t('rlcControlsSub')}
        </div>
        {controls.length === 0
          ? <InlineEmpty>{t('rlcControlsEmpty')}</InlineEmpty>
          : (
            <table className="tbl rlc-table-full">
              <thead>
                <tr>
                  <th>{t('rlcControlCode')}</th>
                  <th>{t('rlcControlTitle')}</th>
                  <th>{t('rlcControlFw')}</th>
                  <th>{t('rlcControlMaturity')}</th>
                  <th>{t('rlcControlCcm')}</th>
                  <th>{t('rlcControlEvidence')}</th>
                </tr>
              </thead>
              <tbody>
                {controls.map(c => {
                  const tone = c.ccmCoveragePct >= 80 ? 'success' : c.ccmCoveragePct >= 50 ? 'warning' : 'danger';
                  return (
                  <tr key={c.controlCode}>
                    <td className="mono rlc-ccm-text">{c.controlCode}</td>
                    <td>{lang === 'ar' ? c.controlTitleAr : c.controlTitleEn}</td>
                    <td><Badge tone="info">{c.framework}</Badge></td>
                    <td>
                      <Badge tone={c.maturity >= 4 ? 'success' : c.maturity >= 3 ? 'info' : 'warning'}>
                        {c.maturity}/5
                      </Badge>
                    </td>
                    <td>
                      <div className="rlc-ccm-row">
                        <div className="rlc-ccm-track">
                          <div className="rlc-ccm-fill" data-tone={tone} style={{ width: `${c.ccmCoveragePct}%` }} />
                        </div>
                        <span className="mono rlc-ccm-text">{c.ccmCoveragePct}%</span>
                      </div>
                    </td>
                    <td className="mono rlc-ccm-text">{c.lastEvidenceAt ?? '—'}</td>
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

// ─── Treatment plan pipeline (interactive kanban) ──────────────────────────

export function RiskTreatmentPipeline({ allPlans, onAdvance, onBulkAdvance, onCardHistory, onCardClick }: {
  allPlans: TreatmentPlan[];
  onAdvance: (planId: string, fromStatus: string, toStatus: string | null) => void;
  onBulkAdvance?: (planIds: string[]) => void;
  onCardHistory?: (planId: string) => void;
  onCardClick: (planId: string) => void;
}) {
  const { t } = useI18n();
  const stages: PipelineStage[] = [
    { code: 'DRAFT',       labelEn: 'Draft',         labelAr: 'مسوّدة',         tone: 'neutral', Icon: FileTextIcon },
    { code: 'SUBMITTED',   labelEn: 'Submitted',     labelAr: 'مُقدَّمة',        tone: 'warning', Icon: SendIcon },
    { code: 'APPROVED',    labelEn: 'Approved',      labelAr: 'مُعتمَدة',        tone: 'info',    Icon: ThumbsUpIcon },
    { code: 'IN_PROGRESS', labelEn: 'In progress',    labelAr: 'قيد التنفيذ',    tone: 'primary', Icon: Play },
    { code: 'COMPLETED',   labelEn: 'Completed',     labelAr: 'مُنجَزة',         tone: 'success', Icon: CheckCircle2Icon },
    { code: 'REJECTED',    labelEn: 'Rejected',      labelAr: 'مرفوضة',          tone: 'danger',  Icon: ThumbsDownIcon },
  ];
  const cards: PipelineCard[] = allPlans.map(p => {
    const completed = (p.actions ?? []).filter(a => a.status === 'COMPLETED').length;
    return {
      id: p.id, status: p.status,
      refCode: p.id,
      titleEn: p.rationaleEn.slice(0, 80) + (p.rationaleEn.length > 80 ? '…' : ''),
      titleAr: p.rationaleAr.slice(0, 80) + (p.rationaleAr.length > 80 ? '…' : ''),
      subtitleEn: `Strategy: ${p.strategy} · ${p.costSarK.toLocaleString()}k SAR`,
      subtitleAr: `الاستراتيجية: ${p.strategy} · ${p.costSarK.toLocaleString()} ألف ريال`,
      badges: [
        { label: `${completed}/${(p.actions ?? []).length} ${t('rlcActions')}`, tone: completed === (p.actions ?? []).length && (p.actions ?? []).length > 0 ? 'success' : 'neutral' as const },
      ],
    };
  });

  return (
    <LifecyclePipeline
      stages={stages}
      cards={cards}
      introEn="Live treatment-plan pipeline across all risks. Click a card to jump to that risk; click the arrow to advance. Hold Shift+click for multi-select; click the clock icon for activity history."
      introAr="مسار خطط المعالجة الحيّ عبر جميع المخاطر. انقر على بطاقة للانتقال إلى تلك المخاطرة، وانقر السهم لتقدّم الخطة. اضغط Shift+نقر للتحديد المتعدّد؛ انقر أيقونة الساعة للسجل."
      onAdvance={onAdvance}
      onCardClick={onCardClick}
      onBulkAdvance={onBulkAdvance}
      onCardHistory={onCardHistory}
      emptyEn="No treatment plans yet."
      emptyAr="لا خطط معالجة بعد."
    />
  );
}

// ─── Helpers ───────────────────────────────────────────────────────────────

export function Stat({ label, value, unit, tone }: { label: string; value: string; unit?: string; tone: 'primary' | 'warning' | 'danger' | 'info' }) {
  return (
    <div className="rlc-stat-tile" data-tone={tone}>
      <div className="rlc-stat-tile__label">{label}</div>
      <div className="mono rlc-stat-tile__value">
        {value}{unit && <span className="rlc-stat-tile__unit">{unit}</span>}
      </div>
    </div>
  );
}
