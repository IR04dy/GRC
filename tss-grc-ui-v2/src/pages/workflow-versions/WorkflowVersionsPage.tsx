import { useMemo, useState } from 'react';
import {
  Layers, GitBranch, AlertOctagon, FileText, Workflow,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { useI18n } from '../../lib/i18n';
import { HijriDate } from '../../components/ui/HijriDate';
import {
  buildFamilies, aggregateVersioning,
  STATUS_TONE, STATUS_LABEL, CHANGE_KIND_LABEL,
  type WorkflowFamily, type VersionChange,
} from '../../lib/workflowVersioning';

/**
 * WorkflowVersionsPage — Sprint 6 follow-on (A3).
 *
 * "Publish v2.0 without breaking in-flight v1.0 instances." Per workflow
 * family: timeline of versions (DRAFT / PUBLISHED / DEPRECATED / ARCHIVED),
 * change diffs vs previous, and instance counts per version showing
 * legacy in-flight workloads still bound to older versions.
 */
export function WorkflowVersionsPage() {
  const { t, lang } = useI18n();
  const families = useMemo(() => buildFamilies(), []);
  const stats = useMemo(() => aggregateVersioning(families), [families]);
  const [selected, setSelected] = useState<string>(families[0]?.code ?? '');
  const current = families.find(f => f.code === selected) ?? families[0];

  return (
    <Shell title={t('wvTitle')} sub={t('wvSub')}>
      <PageHeader
        eyebrow={t('wvEyebrow')}
        title={t('wvTitle')}
        sub={t('wvPageSub')}
      />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Workflow}      label={t('wvKpiFamilies')}     value={String(stats.totalFamilies)} tone="primary" />
        <Kpi Icon={Layers}         label={t('wvKpiVersions')}     value={String(stats.totalVersions)} tone="info" />
        <Kpi Icon={GitBranch}      label={t('wvKpiPublished')}    value={String(stats.publishedCount)} tone="success" />
        <Kpi Icon={FileText}       label={t('wvKpiDraft')}        value={String(stats.draftCount)} tone="info" />
        <Kpi Icon={AlertOctagon}   label={t('wvKpiDeprecated')}   value={String(stats.deprecatedCount)} tone="warning" />
        <Kpi Icon={Layers}         label={t('wvKpiLegacy')}        value={String(stats.legacyInflight)} tone={stats.legacyInflight > 0 ? 'warning' : 'success'} />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '320px 1fr', gap: 16 }}>
        <Card padded={false} title={t('wvFamiliesTitle')} Icon={Workflow}
          action={<Badge tone="primary">{families.length}</Badge>}>
          <div style={{ maxHeight: 660, overflowY: 'auto' }}>
            {families.map(f => {
              const isSelected = f.code === selected;
              const publishedVersion = f.versions.find(v => v.status === 'PUBLISHED');
              const olderInflight = Object.entries(f.instancesByVersion)
                .filter(([v]) => v !== publishedVersion?.version)
                .reduce((s, [, c]) => s + c, 0);
              return (
                <button key={f.code} onClick={() => setSelected(f.code)}
                  style={{
                    width: '100%', textAlign: 'start',
                    padding: '12px 14px', border: 'none', cursor: 'pointer',
                    borderBottom: '1px solid var(--border)',
                    background: isSelected ? 'var(--primary-50)' : 'transparent',
                    display: 'flex', flexDirection: 'column', gap: 4,
                  }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    <span className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>{f.code}</span>
                    {publishedVersion && <Badge tone="success">v{publishedVersion.version}</Badge>}
                    {olderInflight > 0 && <Badge tone="warning">{olderInflight} {t('wvLegacySuffix')}</Badge>}
                  </div>
                  <div style={{ fontSize: 13, fontWeight: 600 }}>
                    {lang === 'ar' ? f.nameAr : f.nameEn}
                  </div>
                  <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>
                    {f.versions.length} {t('wvVersionsSuffix')} · {f.targetModule}
                  </div>
                </button>
              );
            })}
          </div>
        </Card>

        {/* Detail */}
        {current && <FamilyDetail family={current} t={t} lang={lang} />}
      </div>
    </Shell>
  );
}

function FamilyDetail({ family, t, lang }: { family: WorkflowFamily; t: (k: string) => string; lang: 'en' | 'ar' }) {
  const totalInstances = Object.values(family.instancesByVersion).reduce((s, c) => s + c, 0);
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <Card>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
          <span className="mono" style={{ fontSize: 11, color: 'var(--primary)' }}>{family.code}</span>
          <Badge tone="info">{family.targetModule}</Badge>
          <Badge tone="primary">{family.versions.length} {t('wvVersionsSuffix')}</Badge>
          <Badge tone="success">{totalInstances} {t('wvInstancesTotalSuffix')}</Badge>
        </div>
        <div style={{ fontSize: 18, fontWeight: 700 }}>
          {lang === 'ar' ? family.nameAr : family.nameEn}
        </div>
      </Card>

      <Card title={t('wvTimelineTitle')} Icon={GitBranch}>
        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 12 }}>
          {t('wvTimelineSub')}
        </div>
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          {family.versions.map(v => {
            const inflight = family.instancesByVersion[v.version] ?? 0;
            return (
              <div key={v.version} style={{
                padding: '14px 0', borderBottom: '1px dashed var(--border)',
                display: 'grid', gridTemplateColumns: '110px 1fr', gap: 14,
              }}>
                <div>
                  <div className="mono" style={{
                    fontSize: 22, fontWeight: 800,
                    color: v.status === 'PUBLISHED' ? 'var(--primary)' : 'var(--ink-2)',
                  }}>
                    v{v.version}
                  </div>
                  <Badge tone={STATUS_TONE[v.status]}>
                    {lang === 'ar' ? STATUS_LABEL[v.status].ar : STATUS_LABEL[v.status].en}
                  </Badge>
                  {inflight > 0 && (
                    <div style={{ marginTop: 6, fontSize: 10, color: 'var(--ink-3)' }}>
                      <Badge tone={v.status === 'PUBLISHED' ? 'success' : 'warning'}>
                        {inflight} {t('wvInflightSuffix')}
                      </Badge>
                    </div>
                  )}
                </div>
                <div>
                  <div style={{ fontSize: 13, color: 'var(--ink-1)', lineHeight: 1.6 }}>
                    {lang === 'ar' ? v.changeSummaryAr : v.changeSummaryEn}
                  </div>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16, marginTop: 8, fontSize: 11, color: 'var(--ink-3)' }}>
                    {v.publishedAt && (
                      <div>
                        <div style={{ textTransform: 'uppercase', letterSpacing: '0.04em' }}>{t('wvPublished')}</div>
                        <HijriDate iso={v.publishedAt} layout="stacked" mono />
                      </div>
                    )}
                    {v.supersededAt && (
                      <div>
                        <div style={{ textTransform: 'uppercase', letterSpacing: '0.04em' }}>{t('wvSuperseded')}</div>
                        <HijriDate iso={v.supersededAt} layout="stacked" mono />
                      </div>
                    )}
                    {v.approvedBy && (
                      <div>
                        <div style={{ textTransform: 'uppercase', letterSpacing: '0.04em' }}>{t('wvApprover')}</div>
                        <span style={{ color: 'var(--ink-2)' }}>{v.approvedBy}</span>
                      </div>
                    )}
                    <div>
                      <div style={{ textTransform: 'uppercase', letterSpacing: '0.04em' }}>{t('wvShape')}</div>
                      <span className="mono">{v.stateCount} {t('wvStates')} · {v.transitionCount} {t('wvTransitions')}</span>
                    </div>
                  </div>
                  {v.changes.length > 0 && (
                    <div style={{ marginTop: 10 }}>
                      <div style={{ fontSize: 10, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: 4 }}>
                        {t('wvDiff')}
                      </div>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                        {v.changes.map((c, i) => <ChangeRow key={i} change={c} lang={lang} t={t} />)}
                      </div>
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </Card>
    </div>
  );
}

function ChangeRow({ change, lang, t }: { change: VersionChange; lang: 'en' | 'ar'; t: (k: string) => string }) {
  const tone = change.kind === 'SLA_TIGHTENED' ? 'warning'
    : change.kind === 'SLA_RELAXED' ? 'info'
    : change.kind === 'STATE_REMOVED' || change.kind === 'TRANSITION_REMOVED' ? 'danger'
    : 'success';
  return (
    <div style={{
      padding: '6px 10px', background: 'var(--surface-2)',
      borderRadius: 'var(--r-sm)',
      display: 'grid', gridTemplateColumns: 'auto 1fr', gap: 10, alignItems: 'center',
    }}>
      <Badge tone={tone}>
        {lang === 'ar' ? CHANGE_KIND_LABEL[change.kind].ar : CHANGE_KIND_LABEL[change.kind].en}
      </Badge>
      <span style={{ fontSize: 12, color: 'var(--ink-2)' }}>
        {lang === 'ar' ? change.detailAr : change.detailEn}
      </span>
    </div>
  );
}
