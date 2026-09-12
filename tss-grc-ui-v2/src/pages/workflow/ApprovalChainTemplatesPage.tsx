import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  GitBranch, ShieldCheck, Star, Layers, ArrowRight, CornerDownRight,
  CheckCircle2, ListChecks,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import {
  SEED_TEMPLATES, SEED_INSTANCES, STEP_TYPE_LABEL,
  type ChainTemplate, type ChainStep,
} from '../../lib/approvalChains';

/**
 * Sprint 13 — Approval-chain template register (admin CRUD landing).
 *
 * <p>The register is the bank's catalogue of authorised approval
 * ladders. Each row is a slowly-changing template — Compliance Officer
 * + Platform Admin write, every dashboard reader can read.
 *
 * <p>Regulatory anchor: SAMA Corporate Governance §5.3 requires the
 * board-approved approval matrix to be documented, versioned, and
 * inspectable. NCA ECC-2 control 1-3-2 adds the same requirement for
 * cybersecurity exceptions.
 */
export function ApprovalChainTemplatesPage() {
  const { t, lang } = useI18n();
  const [templates] = useState<ChainTemplate[]>(SEED_TEMPLATES);

  const inflight = useMemo(
    () => SEED_INSTANCES.filter(i => i.status === 'PENDING' || i.status === 'IN_PROGRESS'),
    [],
  );
  const totalSteps = templates.reduce((acc, t2) => acc + t2.steps.length, 0);
  const defaults = templates.filter(t2 => t2.isDefault).length;
  const parallelTemplates = templates.filter(t2 => t2.steps.some(s => s.stepType === 'PARALLEL')).length;

  return (
    <Shell title={t('approvalChainTitle', 'Approval chains')}
           sub={t('approvalChainSub', 'Configurable multi-step approval ladders')}>
      <PageHeader
        eyebrow={t('approvalChainEyebrow', 'Approval Chains')}
        title={t('approvalChainTitle', 'Approval chains')}
        sub={t('approvalChainPageSub', 'Configure the multi-step approval ladders enforced before any CAPA closure, cross-border data transfer, or material change can be finalised. Anchored on SAMA Corporate Governance §5.3, NCA ECC-2 control 1-3-2, and PDPL Article 32.')}
        actions={
          <Link to="/workflows/chains/pending">
            <span className="btn btn--secondary btn--sm" style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}>
              <ListChecks size={14} />
              {t('approvalChainPendingForMe', 'My pending approvals')}
            </span>
          </Link>
        }
      />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={GitBranch}   label={t('approvalChainKpiTemplates',  'Templates')}            value={String(templates.length)}    tone="primary" to="/workflows" />
        <Kpi Icon={ListChecks}  label={t('approvalChainKpiTotalSteps', 'Total steps')}          value={String(totalSteps)}          tone="info"    to="/workflows" />
        <Kpi Icon={Layers}      label={t('approvalChainKpiParallel',   'Parallel-rung chains')} value={String(parallelTemplates)}   tone="accent"  to="/workflows" />
        <Kpi Icon={Star}        label={t('approvalChainKpiDefaults',   'Default per module')}   value={String(defaults)}            tone="warning" to="/workflows" />
        <Kpi Icon={ShieldCheck} label={t('approvalChainKpiInflight',   'In-flight')}            value={String(inflight.length)}     tone="success" to="/workflows/chains/pending" />
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        {templates.length === 0
          ? <InlineEmpty>{t('approvalChainTemplatesEmpty', 'No approval-chain templates configured yet.')}</InlineEmpty>
          : templates.map(tpl => (
              <TemplateCard key={tpl.id} tpl={tpl} lang={lang} t={t} />
            ))}
      </div>
    </Shell>
  );
}

function TemplateCard({
  tpl, lang, t,
}: {
  tpl: ChainTemplate;
  lang: 'en' | 'ar';
  t: (key: string, fallback?: string) => string;
}) {
  // Group steps by step_order so PARALLEL rungs render as one row
  // with the parallel rows nested under it.
  const orders = Array.from(new Set(tpl.steps.map(s => s.stepOrder))).sort((a, b) => a - b);
  const inflight = SEED_INSTANCES.filter(i => i.templateId === tpl.id && (i.status === 'IN_PROGRESS' || i.status === 'PENDING')).length;

  return (
    <Card
      title={lang === 'ar' ? tpl.titleAr : tpl.titleEn}
      Icon={GitBranch}
      action={
        <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
          <Badge tone="primary">{tpl.templateCode}</Badge>
          {tpl.appliesToModule && <Badge tone="neutral">{tpl.appliesToModule}</Badge>}
          {tpl.isDefault && <Badge tone="warning">{t('approvalChainDefaultBadge', 'Default')}</Badge>}
          {inflight > 0 && (
            <Badge tone="info">
              {inflight} {t('approvalChainInflightSuffix', 'in flight')}
            </Badge>
          )}
        </div>
      }>
      <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12, lineHeight: 1.6 }}>
        {lang === 'ar' ? tpl.descriptionAr : tpl.descriptionEn}
      </div>
      {tpl.appliesToWorkflowCodes && (
        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 12 }}>
          <span style={{ fontWeight: 600 }}>{t('approvalChainAppliesTo', 'Applies to')}:</span>{' '}
          <span className="mono">{tpl.appliesToWorkflowCodes}</span>
        </div>
      )}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        {orders.map((order, idx) => {
          const rowsAtOrder = tpl.steps.filter(s => s.stepOrder === order);
          const isParallel = rowsAtOrder[0]?.stepType === 'PARALLEL';
          return (
            <div key={order} style={{
              border: '1px solid var(--border)',
              borderRadius: 'var(--r-sm)',
              padding: '10px 12px',
              background: isParallel ? 'var(--accent-50, var(--surface-2, var(--surface)))' : 'var(--surface)',
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontWeight: 600 }}>
                <Badge tone="accent">#{order}</Badge>
                <Badge tone={isParallel ? 'accent' : 'neutral'}>
                  {lang === 'ar' ? STEP_TYPE_LABEL[rowsAtOrder[0].stepType].ar : STEP_TYPE_LABEL[rowsAtOrder[0].stepType].en}
                </Badge>
                {idx < orders.length - 1 && <ArrowRight size={14} color="var(--ink-3)" />}
              </div>
              <div style={{ marginTop: 8, display: 'flex', flexDirection: 'column', gap: 6 }}>
                {rowsAtOrder.map(s => <StepRow key={s.id} step={s} lang={lang} t={t} />)}
              </div>
            </div>
          );
        })}
      </div>
    </Card>
  );
}

function StepRow({
  step, lang, t,
}: {
  step: ChainStep;
  lang: 'en' | 'ar';
  t: (key: string, fallback?: string) => string;
}) {
  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: 'auto 1fr auto',
      gap: 10,
      alignItems: 'center',
      padding: '6px 8px',
      borderInlineStart: '3px solid var(--primary-fg, var(--primary))',
      borderRadius: 'var(--r-sm)',
      background: 'var(--surface)',
    }}>
      <CornerDownRight size={14} color="var(--ink-3)" />
      <div>
        <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)' }}>
          {lang === 'ar' ? step.titleAr : step.titleEn}
        </div>
        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2, display: 'flex', flexWrap: 'wrap', gap: 8 }}>
          {step.requiredRole && (
            <span><b>{t('approvalChainStepRole', 'Role')}:</b> <span className="mono">{step.requiredRole}</span></span>
          )}
          {step.requiredUserId != null && (
            <span><b>{t('approvalChainStepUser', 'User')}:</b> <span className="mono">#{step.requiredUserId}</span></span>
          )}
          {step.slaHours != null && (
            <span><b>{t('approvalChainStepSla', 'SLA')}:</b> {step.slaHours}h</span>
          )}
          {step.escalationRole && (
            <span><b>{t('approvalChainStepEsc', 'Escalation')}:</b> <span className="mono">{step.escalationRole}</span></span>
          )}
        </div>
      </div>
      <div style={{ display: 'flex', gap: 4 }}>
        {step.isMandatory
          ? <Badge tone="danger">{t('approvalChainStepMandatory', 'Mandatory')}</Badge>
          : <Badge tone="neutral">{t('approvalChainStepOptional', 'Optional')}</Badge>}
        {step.canDelegate
          ? <Badge tone="info">{t('approvalChainStepDelegable', 'Delegable')}</Badge>
          : <Badge tone="warning">{t('approvalChainStepNonDelegable', 'Non-delegable')}</Badge>}
        {step.isMandatory && <CheckCircle2 size={14} color="var(--success-fg, var(--success))" />}
      </div>
    </div>
  );
}
