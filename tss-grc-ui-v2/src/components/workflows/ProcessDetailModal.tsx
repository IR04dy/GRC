import { Workflow, Play, ArrowRight, BookOpen, User, Clock, AlertTriangle, CheckCircle2 } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { Modal } from '../ui/Modal';
import { Button } from '../ui/Button';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import type { ProcessLifecycle } from './ProcessLifecycleBar';

/**
 * ProcessDetailModal — DD-65.
 *
 * Opens when a user clicks a process chip on any module's middle-bar.
 * Until DD-65 chip clicks just navigated to a tab URL — felt like a
 * page refresh. Now they open this modal which:
 *   - explains what the process is (name + standards anchor)
 *   - shows the canonical stages as a visual stepper
 *   - shows live counters (active / overdue) so the user knows
 *     where the work is in flight
 *   - exposes two action buttons: "Open register" (navigates to
 *     the filtered list) and "Start new" (kicks off the workflow,
 *     wired to the lifecycle's onClick if present, otherwise a
 *     toast-info hint pointing at the upcoming wizard)
 *
 * Modelled on ServiceNow IRM "Process Details" pop-out and
 * Diligent HighBond's process dictionary.
 */
export function ProcessDetailModal({ lc, onClose }: {
  lc: ProcessLifecycle;
  onClose: () => void;
}) {
  const { t, lang } = useI18n();
  const toast = useToast();
  const navigate = useNavigate();

  const name = lang === 'ar' && lc.nameAr ? lc.nameAr : lc.name;
  const primaryLabel = lang === 'ar' && lc.primary.labelAr ? lc.primary.labelAr : lc.primary.label;
  const secondaryLabel = lc.secondary
    ? (lang === 'ar' && lc.secondary.labelAr ? lc.secondary.labelAr : lc.secondary.label)
    : undefined;

  function startWorkflow() {
    if (lc.primary.onClick) {
      lc.primary.onClick();
      onClose();
      return;
    }
    // Demo-mode fallback: explain that the start-flow modal would open here.
    toast.info(t('processDetailStartHint').replace('{name}', name));
  }

  function openRegister() {
    if (lc.primary.href) {
      navigate(lc.primary.href);
    } else if (lc.secondary?.href) {
      navigate(lc.secondary.href);
    }
    onClose();
  }

  return (
    <Modal open onClose={onClose} size="lg" title={name}
      footer={<>
        <div style={{ flex: 1 }} />
        <Button variant="ghost" onClick={onClose}>{t('actionCancel')}</Button>
        {(lc.primary.href || lc.secondary?.href) && (
          <Button variant="ghost" Icon={ArrowRight} onClick={openRegister}>
            {t('processDetailOpenRegister')}
          </Button>
        )}
        <Button variant="primary" Icon={Play} onClick={startWorkflow}>
          {primaryLabel}
        </Button>
      </>}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        {/* Header — standards anchor + steward + SLA */}
        <div style={{
          display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 10,
        }}>
          <Stat
            Icon={BookOpen}
            label={t('processDetailReference')}
            value={lc.reference ?? '—'}
          />
          <Stat
            Icon={User}
            label={t('processDetailSteward')}
            value={lc.steward ?? '—'}
          />
          <Stat
            Icon={Clock}
            label={t('processDetailSla')}
            value={lc.sla ?? '—'}
          />
        </div>

        {/* Lifecycle stages as a visual stepper */}
        {lc.stages && lc.stages.length > 0 && (
          <div>
            <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 8 }}>
              {t('processDetailStages')}
            </div>
            <div style={{
              display: 'flex', alignItems: 'center', gap: 4, flexWrap: 'wrap',
              padding: 12,
              background: 'var(--surface-2)',
              border: '1px solid var(--border)',
              borderRadius: 'var(--r-sm)',
            }}>
              {lc.stages.map((stage, i) => (
                <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                  <div style={{
                    display: 'inline-flex', alignItems: 'center', gap: 6,
                    padding: '6px 12px',
                    background: 'var(--surface-1)',
                    border: '1px solid var(--border)',
                    borderRadius: 'var(--r-pill)',
                    fontSize: 12, fontWeight: 600, color: 'var(--ink-1)',
                  }}>
                    <span style={{
                      width: 18, height: 18, borderRadius: '50%',
                      background: 'var(--primary-50)', color: 'var(--primary-700)',
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                      fontSize: 10, fontWeight: 700,
                    }}>{i + 1}</span>
                    {stage}
                  </div>
                  {i < (lc.stages?.length ?? 0) - 1 && (
                    <ArrowRight size={12} style={{ color: 'var(--ink-3)' }} />
                  )}
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Live counters */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 10 }}>
          <Counter
            tone={lc.activeCount > 0 ? 'info' : 'neutral'}
            Icon={Workflow}
            label={t('processDetailActive')}
            value={lc.activeCount}
          />
          <Counter
            tone={lc.overdueCount > 0 ? 'danger' : 'success'}
            Icon={lc.overdueCount > 0 ? AlertTriangle : CheckCircle2}
            label={t('processDetailOverdue')}
            value={lc.overdueCount}
          />
        </div>

        {/* Actions explainer */}
        <div style={{
          background: 'var(--info-bg)', border: '1px solid var(--info-fg)',
          padding: 12, borderRadius: 'var(--r-sm)',
          fontSize: 12, color: 'var(--ink-2)', lineHeight: 1.5,
        }}>
          <div style={{ fontWeight: 700, color: 'var(--info-fg)', marginBottom: 4 }}>
            {t('processDetailHowToUse')}
          </div>
          <div>
            <strong>{primaryLabel}</strong> — {t('processDetailStartExplain').replace('{name}', name)}
          </div>
          {secondaryLabel && (
            <div style={{ marginTop: 4 }}>
              <strong>{t('processDetailOpenRegister')}</strong> — {t('processDetailRegisterExplain')}
            </div>
          )}
        </div>
      </div>
    </Modal>
  );
}

function Stat({ Icon, label, value }: { Icon: typeof Clock; label: string; value: string }) {
  return (
    <div style={{
      padding: 10,
      background: 'var(--surface-2)',
      border: '1px solid var(--border)',
      borderRadius: 'var(--r-sm)',
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
        <Icon size={12} color="var(--ink-3)" />
        <span style={{ fontSize: 10, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
          {label}
        </span>
      </div>
      <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink-1)' }}>
        {value}
      </div>
    </div>
  );
}

function Counter({ tone, Icon, label, value }: {
  tone: 'info' | 'neutral' | 'danger' | 'success';
  Icon: typeof Workflow;
  label: string;
  value: number;
}) {
  const palette: Record<string, { bg: string; fg: string }> = {
    info:    { bg: 'var(--info-bg)',    fg: 'var(--info-fg)' },
    neutral: { bg: 'var(--surface-2)',  fg: 'var(--ink-2)' },
    danger:  { bg: 'var(--danger-bg)',  fg: 'var(--danger-fg)' },
    success: { bg: 'var(--success-bg)', fg: 'var(--success-fg)' },
  };
  const { bg, fg } = palette[tone];
  return (
    <div style={{
      padding: 14, background: bg, borderRadius: 'var(--r-sm)',
      display: 'flex', alignItems: 'center', gap: 12,
    }}>
      <Icon size={20} color={fg} />
      <div>
        <div className="mono" style={{ fontSize: 22, fontWeight: 700, color: fg }}>{value}</div>
        <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>{label}</div>
      </div>
    </div>
  );
}
