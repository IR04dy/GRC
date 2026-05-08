import { useMemo, useState } from 'react';
import {
  Newspaper, Filter, Landmark, AlertOctagon, Activity, FileText,
  ExternalLink, ChevronRight, Calendar,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  REGULATORY_CHANGES, classifyByDeadline, aggregateRegChange,
  IMPACT_TONE, STATUS_TONE, KIND_LABEL, STATUS_LABEL,
  type RegulatoryChange, type ChangeStatus,
} from '../../lib/regulatoryChange';
import { executeChain } from '../../lib/workflowChains';
import { formatHijri } from '../../lib/sla';
import type { RegulatorScope } from '../../lib/assets';

/**
 * RegulatoryChangePage — Sprint 5 Saudi-first regulatory-change feed.
 *
 * Left rail: filterable list grouped by regulator, sorted by recency.
 * Right pane: selected change detail with impact assessment + "Triage" /
 * "Mark applicable" / "Spawn assessment workflow" actions. The latter
 * fires the chain engine (POLICY_PUBLISHED is the closest existing trigger
 * — production would add REG_CHANGE_PUBLISHED).
 */
export function RegulatoryChangePage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const [items, setItems] = useState<RegulatoryChange[]>(REGULATORY_CHANGES);
  const [selected, setSelected] = useState<string>(REGULATORY_CHANGES[0].id);
  const [regulatorFilter, setRegulatorFilter] = useState<RegulatorScope | 'ALL'>('ALL');
  const [statusFilter, setStatusFilter] = useState<ChangeStatus | 'ALL'>('ALL');

  const stats = useMemo(() => aggregateRegChange(items), [items]);

  const filtered = useMemo(() => items
    .filter(c => regulatorFilter === 'ALL' || c.regulator === regulatorFilter)
    .filter(c => statusFilter === 'ALL' || c.status === statusFilter)
    .sort((a, b) => new Date(b.publishedAt).getTime() - new Date(a.publishedAt).getTime()),
  [items, regulatorFilter, statusFilter]);

  const current = items.find(c => c.id === selected) ?? items[0];

  function setStatus(id: string, status: ChangeStatus) {
    setItems(prev => prev.map(c => c.id === id ? { ...c, status } : c));
    const label = lang === 'ar' ? STATUS_LABEL[status].ar : STATUS_LABEL[status].en;
    toast.success(t('regChangeStatusToast').replace('{status}', label));
  }

  function spawnAssessment(c: RegulatoryChange) {
    // Reuse POLICY_PUBLISHED chain rule for now — production would add
    // a dedicated REG_CHANGE_PUBLISHED trigger.
    const fired = executeChain({
      type: 'POLICY_PUBLISHED',
      sourceRef: c.externalRef,
      module: 'Compliance',
      payload: { regulator: c.regulator, impact: c.impact },
      raisedAt: new Date().toISOString(),
    });
    setStatus(c.id, 'APPLICABLE');
    if (fired.length > 0) {
      toast.info(t('regChangeChainToast').replace('{n}', String(fired.length)));
    }
  }

  return (
    <Shell title={t('regChangeTitle')} sub={t('regChangeSub')}>
      <PageHeader
        eyebrow={t('regChangeEyebrow')}
        title={t('regChangeTitle')}
        sub={t('regChangePageSub')}
      />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Newspaper}    label={t('regChangeKpiTotal')}        value={String(stats.total)}            tone="primary" />
        <Kpi Icon={Activity}     label={t('regChangeKpiNew')}          value={String(stats.newCount)}         tone="info" />
        <Kpi Icon={Filter}       label={t('regChangeKpiTriaged')}      value={String(stats.triagedCount)}     tone="warning" />
        <Kpi Icon={AlertOctagon} label={t('regChangeKpiApplicable')}    value={String(stats.applicableCount)}  tone="danger" />
        <Kpi Icon={FileText}     label={t('regChangeKpiImplemented')}   value={String(stats.implementedCount)} tone="success" />
        <Kpi Icon={Calendar}     label={t('regChangeKpiOverdue')}       value={String(stats.overdueCount)}     tone={stats.overdueCount > 0 ? 'danger' : 'success'} />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '380px 1fr', gap: 16 }}>
        {/* ── Feed ─────────────────────────────────────────── */}
        <Card padded={false} title={t('regChangeFeedTitle')} Icon={Newspaper}
          action={<Badge tone="primary">{filtered.length}</Badge>}>
          <div style={{ padding: '8px 14px', borderBottom: '1px solid var(--border)', display: 'flex', gap: 6, flexWrap: 'wrap' }}>
            <select value={regulatorFilter} onChange={e => setRegulatorFilter(e.target.value as RegulatorScope | 'ALL')}
              style={{ padding: '4px 6px', fontSize: 11, border: '1px solid var(--border)', borderRadius: 'var(--r-sm)', background: 'var(--surface-2)' }}>
              <option value="ALL">{t('regChangeFilterRegulatorAll')}</option>
              {(['NCA', 'SAMA', 'PDPL', 'CMA', 'ZATCA', 'CITC', 'NCEMA', 'HRSD'] as RegulatorScope[]).map(r =>
                <option key={r} value={r}>{r}</option>)}
            </select>
            <select value={statusFilter} onChange={e => setStatusFilter(e.target.value as ChangeStatus | 'ALL')}
              style={{ padding: '4px 6px', fontSize: 11, border: '1px solid var(--border)', borderRadius: 'var(--r-sm)', background: 'var(--surface-2)' }}>
              <option value="ALL">{t('regChangeFilterStatusAll')}</option>
              {(Object.keys(STATUS_LABEL) as ChangeStatus[]).map(s =>
                <option key={s} value={s}>{lang === 'ar' ? STATUS_LABEL[s].ar : STATUS_LABEL[s].en}</option>)}
            </select>
          </div>

          <div style={{ maxHeight: 660, overflowY: 'auto' }}>
            {filtered.map(c => {
              const deadlineCat = classifyByDeadline(c);
              const isSelected = c.id === selected;
              return (
                <button key={c.id} onClick={() => setSelected(c.id)}
                  style={{
                    width: '100%', textAlign: 'start',
                    padding: '10px 14px', border: 'none', cursor: 'pointer',
                    borderBottom: '1px solid var(--border)',
                    background: isSelected ? 'var(--primary-50)' : 'transparent',
                    display: 'flex', flexDirection: 'column', gap: 4,
                  }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                    <Badge tone="primary">{c.regulator}</Badge>
                    <Badge tone={IMPACT_TONE[c.impact]}>{c.impact}</Badge>
                    <Badge tone={STATUS_TONE[c.status]}>{lang === 'ar' ? STATUS_LABEL[c.status].ar : STATUS_LABEL[c.status].en}</Badge>
                    {deadlineCat === 'OVERDUE' && <Badge tone="danger">!</Badge>}
                    {deadlineCat === 'WITHIN_30_DAYS' && <Badge tone="warning">≤30d</Badge>}
                  </div>
                  <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink-1)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {lang === 'ar' ? c.titleAr : c.titleEn}
                  </div>
                  <div className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>
                    {c.externalRef} · {c.publishedAt}
                  </div>
                </button>
              );
            })}
          </div>
        </Card>

        {/* ── Detail ──────────────────────────────────────── */}
        <ChangeDetail change={current} t={t} lang={lang}
          onTriage={() => setStatus(current.id, 'TRIAGED')}
          onApplicable={() => setStatus(current.id, 'APPLICABLE')}
          onNotApplicable={() => setStatus(current.id, 'NOT_APPLICABLE')}
          onImplement={() => setStatus(current.id, 'IMPLEMENTED')}
          onSpawnAssessment={() => spawnAssessment(current)} />
      </div>
    </Shell>
  );
}

function ChangeDetail({
  change, t, lang, onTriage, onApplicable, onNotApplicable, onImplement, onSpawnAssessment,
}: {
  change: RegulatoryChange; t: (k: string) => string; lang: 'en' | 'ar';
  onTriage: () => void; onApplicable: () => void; onNotApplicable: () => void;
  onImplement: () => void; onSpawnAssessment: () => void;
}) {
  const deadlineCat = classifyByDeadline(change);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <Card>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap', marginBottom: 8 }}>
          <Badge tone="primary"><Landmark size={11} style={{ marginInlineEnd: 4, verticalAlign: 'middle' }} />{change.regulator}</Badge>
          <Badge tone={IMPACT_TONE[change.impact]}>{change.impact}</Badge>
          <Badge tone={STATUS_TONE[change.status]}>{lang === 'ar' ? STATUS_LABEL[change.status].ar : STATUS_LABEL[change.status].en}</Badge>
          <Badge tone="info">{lang === 'ar' ? KIND_LABEL[change.kind].ar : KIND_LABEL[change.kind].en}</Badge>
          <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>{change.externalRef}</span>
        </div>
        <div style={{ fontSize: 18, fontWeight: 700, color: 'var(--ink-1)', marginBottom: 6, lineHeight: 1.4 }}>
          {lang === 'ar' ? change.titleAr : change.titleEn}
        </div>
        <div style={{ fontSize: 13, color: 'var(--ink-2)', lineHeight: 1.6, marginBottom: 12 }}>
          {lang === 'ar' ? change.summaryAr : change.summaryEn}
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 16, marginBottom: 12, fontSize: 12 }}>
          <div>
            <div style={{ fontSize: 10, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>{t('regChangePublished')}</div>
            <div className="mono" style={{ marginTop: 2 }}>{change.publishedAt}</div>
            <div style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 2 }}>{formatHijri(change.publishedAt, lang)}</div>
          </div>
          {change.deadline && (
            <div>
              <div style={{ fontSize: 10, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>{t('regChangeDeadline')}</div>
              <div className="mono" style={{ marginTop: 2 }}>
                {change.deadline}
                {deadlineCat === 'OVERDUE' && <Badge tone="danger">{t('regChangeOverdueTag')}</Badge>}
                {deadlineCat === 'WITHIN_30_DAYS' && <Badge tone="warning">≤30d</Badge>}
              </div>
              <div style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 2 }}>{formatHijri(change.deadline, lang)}</div>
            </div>
          )}
          <div>
            <div style={{ fontSize: 10, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>{t('regChangeOwner')}</div>
            <div style={{ marginTop: 2 }}>{lang === 'ar' ? change.ownerNameAr : change.ownerNameEn}</div>
            <div style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 2 }} className="mono">{change.ownerEmail}</div>
          </div>
        </div>
        {change.sourceUrl && (
          <a href={change.sourceUrl} target="_blank" rel="noreferrer" style={{ fontSize: 11, color: 'var(--primary)', display: 'inline-flex', alignItems: 'center', gap: 4 }}>
            <ExternalLink size={11} /> {t('regChangeOpenSource')}
          </a>
        )}
      </Card>

      {/* ── Impact assessment ─────────────────────────────── */}
      <Card title={t('regChangeImpactTitle')} Icon={AlertOctagon}>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
          <div>
            <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: 6 }}>
              {t('regChangeAffectedControls')}
            </div>
            {change.affectedControls.length === 0
              ? <div style={{ fontSize: 12, color: 'var(--ink-3)' }}>{t('regChangeNoControls')}</div>
              : <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                  {change.affectedControls.map(c => (
                    <div key={c} style={{ fontSize: 12, padding: '4px 8px', background: 'var(--surface-2)', borderRadius: 'var(--r-sm)' }}>
                      <ChevronRight size={11} style={{ display: 'inline-block', verticalAlign: 'middle', marginInlineEnd: 4 }} />
                      <span className="mono">{c}</span>
                    </div>
                  ))}
                </div>}
          </div>
          <div>
            <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: 6 }}>
              {t('regChangeAffectedPolicies')}
            </div>
            {change.affectedPolicies.length === 0
              ? <div style={{ fontSize: 12, color: 'var(--ink-3)' }}>{t('regChangeNoPolicies')}</div>
              : <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                  {change.affectedPolicies.map(p => (
                    <div key={p} style={{ fontSize: 12, padding: '4px 8px', background: 'var(--surface-2)', borderRadius: 'var(--r-sm)' }}>
                      <ChevronRight size={11} style={{ display: 'inline-block', verticalAlign: 'middle', marginInlineEnd: 4 }} />
                      <span className="mono">{p}</span>
                    </div>
                  ))}
                </div>}
          </div>
        </div>
      </Card>

      {/* ── Actions ──────────────────────────────────────── */}
      <Card title={t('regChangeActionsTitle')} Icon={Activity}>
        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 10 }}>
          {t('regChangeActionsSub')}
        </div>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          {change.status === 'NEW' && <Button onClick={onTriage}>{t('regChangeActTriage')}</Button>}
          {(change.status === 'NEW' || change.status === 'TRIAGED') && (
            <>
              <Button variant="primary" onClick={onSpawnAssessment}>{t('regChangeActSpawnAssessment')}</Button>
              <Button variant="secondary" onClick={onApplicable}>{t('regChangeActMarkApplicable')}</Button>
              <Button variant="secondary" onClick={onNotApplicable}>{t('regChangeActMarkNotApplicable')}</Button>
            </>
          )}
          {change.status === 'APPLICABLE' && (
            <Button variant="primary" onClick={onImplement}>{t('regChangeActMarkImplemented')}</Button>
          )}
          {change.status === 'NOT_APPLICABLE' && (
            <Button variant="secondary" onClick={onTriage}>{t('regChangeActReopen')}</Button>
          )}
          {change.status === 'IMPLEMENTED' && (
            <Badge tone="success">{t('regChangeActDone')}</Badge>
          )}
        </div>
      </Card>
    </div>
  );
}
