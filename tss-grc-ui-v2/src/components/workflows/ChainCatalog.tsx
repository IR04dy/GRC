import { useEffect, useMemo, useState } from 'react';
import { GitBranch, Zap, Landmark, ArrowRight, Eraser, Play } from 'lucide-react';
import { Card } from '../ui/Card';
import { Kpi } from '../ui/Kpi';
import { Badge } from '../ui/Badge';
import { Button } from '../ui/Button';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  CHAIN_RULES, executeChain, loadFires, subscribeFires, aggregateStats, clearFires,
  ACTION_TYPE_LABEL, TRIGGER_LABEL,
  type ChainFire, type ChainRule,
} from '../../lib/workflowChains';

/**
 * ChainCatalog — Sprint 5 cross-module orchestration view.
 *
 * Top: stats strip (rules / active / fires 24h / fires 7d).
 * Middle: rule catalogue — each row reads as a sentence:
 *   "When <trigger> [if <condition>] → <action> in <module>"
 *   with regulator citation + manual "Fire" button for demo.
 * Bottom: live fires log (most recent first).
 */
export function ChainCatalog() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const [fires, setFires] = useState<ChainFire[]>(() => loadFires());

  useEffect(() => subscribeFires(() => setFires(loadFires())), []);

  const stats = useMemo(() => aggregateStats(CHAIN_RULES, fires), [fires]);

  function manualFire(rule: ChainRule) {
    // Synthetic event payload — picks a realistic source ref from existing
    // demo data so the user sees a plausible chain in the log.
    const sample = SAMPLE_TRIGGERS[rule.trigger];
    const fired = executeChain({
      type: rule.trigger,
      sourceRef: sample.sourceRef,
      module: sample.module,
      payload: sample.payload,
      raisedAt: new Date().toISOString(),
    });
    if (fired.length === 0) {
      toast.info(t('chainNoMatch'));
    } else {
      toast.success(t('chainFiredToast').replace('{n}', String(fired.length)));
    }
  }

  return (
    <Card title={t('chainCatalogTitle')} Icon={GitBranch}
      action={
        <div style={{ display: 'inline-flex', gap: 6 }}>
          <Badge tone="success">{stats.activeRules} / {stats.totalRules} {t('chainRulesActiveSuffix')}</Badge>
          <Badge tone="primary">{stats.totalFires24h} {t('chainFires24h')}</Badge>
        </div>
      }>
      <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
        {t('chainCatalogSub')}
      </div>

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={GitBranch} label={t('chainKpiTotal')}    value={String(stats.totalRules)}      tone="primary" to="/workflows/chains" />
        <Kpi Icon={Zap}       label={t('chainKpiActive')}   value={String(stats.activeRules)}     tone="success" to="/workflows/chains" />
        <Kpi Icon={Zap}       label={t('chainKpiFires24h')} value={String(stats.totalFires24h)}   tone="info" to="/workflows/chains" />
        <Kpi Icon={Zap}       label={t('chainKpiFires7d')}  value={String(stats.totalFires7d)}    tone="accent" to="/workflows/chains" />
      </div>

      {/* ── Rule catalogue ───────────────────────────────────────── */}
      <div style={{ marginBottom: 16 }}>
        <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--ink-2)', textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: 8 }}>
          {t('chainRulesTitle')}
        </div>
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          {CHAIN_RULES.map(rule => {
            const trigger = TRIGGER_LABEL[rule.trigger];
            const action = ACTION_TYPE_LABEL[rule.action.type];
            const fireCount = stats.byRule[rule.id] ?? 0;
            return (
              <div key={rule.id} style={{
                padding: '12px 0', borderBottom: '1px dashed var(--border)',
                display: 'grid', gridTemplateColumns: '1fr auto', gap: 12, alignItems: 'flex-start',
              }}>
                <div style={{ minWidth: 0 }}>
                  <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)' }}>
                    {lang === 'ar' ? rule.nameAr : rule.nameEn}
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginTop: 6, flexWrap: 'wrap' }}>
                    <Badge tone="info">{lang === 'ar' ? trigger.ar : trigger.en}</Badge>
                    {rule.conditionLabelEn && (
                      <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                        {t('chainIf')} {lang === 'ar' ? rule.conditionLabelAr : rule.conditionLabelEn}
                      </span>
                    )}
                    <ArrowRight size={11} color="var(--ink-3)" />
                    <Badge tone="accent">{lang === 'ar' ? action.ar : action.en}</Badge>
                    <Badge tone="primary">{rule.action.targetModule}</Badge>
                    {rule.action.delayDays > 0 && (
                      <Badge tone="warning">+{rule.action.delayDays}d</Badge>
                    )}
                    <span className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>
                      SLA {rule.action.spawnedSlaDays}d
                    </span>
                  </div>
                  <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 6, lineHeight: 1.5 }}>
                    {lang === 'ar' ? rule.action.descriptionAr : rule.action.descriptionEn}
                  </div>
                  <div style={{ display: 'inline-flex', gap: 4, alignItems: 'center', marginTop: 4, fontSize: 11, color: 'var(--ink-3)' }}>
                    <Landmark size={11} /> {rule.regulatorCitation}
                  </div>
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 6, flexShrink: 0 }}>
                  <Badge tone={rule.active ? 'success' : 'neutral'} dot>
                    {rule.active ? t('chainRuleActive') : t('chainRuleInactive')}
                  </Badge>
                  {fireCount > 0 && <span style={{ fontSize: 10, color: 'var(--ink-3)' }}>{fireCount}× {t('chainFiredSuffix')}</span>}
                  <Button variant="secondary" Icon={Play} onClick={() => manualFire(rule)}>{t('chainManualFire')}</Button>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* ── Recent fires log ─────────────────────────────────────── */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
        <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--ink-2)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
          {t('chainFiresTitle')}
        </div>
        {fires.length > 0 && (
          <Button variant="secondary" Icon={Eraser} onClick={() => { clearFires(); toast.success(t('chainFiresCleared')); }}>
            {t('chainClearLog')}
          </Button>
        )}
      </div>
      {fires.length === 0
        ? <div style={{ padding: 16, fontSize: 12, color: 'var(--ink-3)', textAlign: 'center', background: 'var(--surface-2)', borderRadius: 'var(--r-sm)' }}>
            {t('chainFiresEmpty')}
          </div>
        : (
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            {fires.slice(0, 12).map(f => (
              <div key={f.id} style={{
                padding: '10px 0', borderBottom: '1px dashed var(--border)',
                display: 'grid', gridTemplateColumns: '1fr auto', gap: 8, alignItems: 'flex-start',
              }}>
                <div style={{ minWidth: 0 }}>
                  <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink-1)' }}>
                    {lang === 'ar' ? f.ruleNameAr : f.ruleNameEn}
                  </div>
                  <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }}>
                    <span className="mono">{f.triggerModule} · {f.triggerSourceRef}</span>
                    <ArrowRight size={10} style={{ display: 'inline-block', verticalAlign: 'middle', margin: '0 4px' }} />
                    <span className="mono">{f.spawnedModule} · {f.spawnedRef}</span>
                  </div>
                  <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 4, lineHeight: 1.5 }}>
                    {lang === 'ar' ? f.spawnedTitleAr : f.spawnedTitleEn}
                  </div>
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 4 }}>
                  <Badge tone="primary">{t('chainDueLabel')} {f.spawnedDueDate}</Badge>
                  <span className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>
                    {agoLabel(f.firedAt, t)}
                  </span>
                </div>
              </div>
            ))}
          </div>
        )
      }
    </Card>
  );
}

// Sample triggers for the manual-fire button so the demo always lands on a
// plausible-looking source record.
const SAMPLE_TRIGGERS: Record<string, { sourceRef: string; module: string; payload?: Record<string, string | number | boolean> }> = {
  APPROVAL_APPROVED:    { sourceRef: 'AP-001',          module: 'Compliance' },
  APPROVAL_REJECTED:    { sourceRef: 'AP-001',          module: 'Compliance' },
  WORKFLOW_CLOSED:      { sourceRef: 'CWF-201',         module: 'Compliance' },
  INCIDENT_CLOSED:      { sourceRef: 'CYB-INC-2026-019', module: 'Cyber', payload: { severity: 'HIGH' } },
  BREACH_CONTAINED:     { sourceRef: 'BR-10',           module: 'Privacy' },
  DPIA_APPROVED:        { sourceRef: 'DPIA-42',         module: 'Privacy' },
  AUDIT_FINDING_CLOSED: { sourceRef: 'AUD-F-038',       module: 'Audit' },
  APPETITE_EXCEEDED:    { sourceRef: 'AS-5104',         module: 'Risk' },
  EVIDENCE_EXPIRED:     { sourceRef: 'cn-evidence-aws.security-group-open', module: 'Compliance' },
  POLICY_PUBLISHED:     { sourceRef: 'POL-AI-001',      module: 'Compliance' },
  VENDOR_ONBOARDED:     { sourceRef: 'V-204',           module: 'TPRM', payload: { tier: 1 } },
  CHANGE_DEPLOYED:      { sourceRef: 'CHG-2026-099',    module: 'Change' },
  RISK_ACCEPTED:        { sourceRef: 'AS-5104',         module: 'Risk' },
  RISK_CLOSED:          { sourceRef: 'R-0042',          module: 'Risk' },
};

function agoLabel(iso: string, t: (k: string) => string): string {
  const ms = Date.now() - new Date(iso).getTime();
  const min = ms / 60_000;
  if (min < 1) return t('chainAgoJustNow');
  if (min < 60) return t('chainAgoMin').replace('{n}', String(Math.round(min)));
  const hours = min / 60;
  if (hours < 24) return t('chainAgoHours').replace('{n}', String(Math.round(hours)));
  const days = hours / 24;
  return t('chainAgoDays').replace('{n}', String(Math.round(days)));
}
