import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Database, Globe, Fingerprint, ShieldCheck, AlertTriangle, ChevronRight, Users,
  FileText, Plane, Trash2, Plus, ArrowUpRight,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import {
  ROPA_ENTRIES, CONSENT_SUMMARIES, ropaSourceAsset, aggregateRopa,
  LAWFUL_BASIS_LABEL, CATEGORY_LABEL, SUBJECT_LABEL,
  type RopaEntry,
} from '../../lib/ropaConsent';
import { HijriDate } from '../../components/ui/HijriDate';
import {
  ropaWorkflowsCounts, buildDeepLink, type RopaCounts,
} from '../../lib/ropaWorkflows';

/**
 * RopaPage — Sprint 6 follow-on (B2).
 *
 * PDPL Art. 31 ROPA register + consent dashboard. Left rail: ROPA list
 * with lawful-basis filter; detail pane shows full processing record
 * (categories, subjects, source asset with auto-derived residency,
 * sub-processors, retention, last review). Bottom: consent rollup for
 * the consent-based activities.
 */
export function RopaPage() {
  const { t, lang } = useI18n();
  const [selected, setSelected] = useState<string>(ROPA_ENTRIES[0].code);

  const stats = useMemo(() => aggregateRopa(), []);
  const current = ROPA_ENTRIES.find(e => e.code === selected) ?? ROPA_ENTRIES[0];

  return (
    <Shell title={t('ropaTitle')} sub={t('ropaSub')}>
      <PageHeader
        eyebrow={t('ropaEyebrow')}
        title={t('ropaTitle')}
        sub={t('ropaPageSub')}
      />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Database}      label={t('ropaKpiTotal')}    value={String(stats.total)} tone="primary" />
        <Kpi Icon={Fingerprint}    label={t('ropaKpiConsent')}   value={String(stats.consentBased)} tone="info" />
        <Kpi Icon={Globe}          label={t('ropaKpiCrossBorder')} value={String(stats.crossBorder)} tone={stats.crossBorder > 0 ? 'warning' : 'success'} />
        <Kpi Icon={ShieldCheck}    label={t('ropaKpiReviewed')}    value={String(stats.reviewedLast90Days)} tone="success" />
        <Kpi Icon={AlertTriangle}  label={t('ropaKpiNeedsReview')} value={String(stats.needsReview)} tone={stats.needsReview > 0 ? 'danger' : 'success'} />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '350px 1fr', gap: 16 }}>
        {/* ROPA list */}
        <Card padded={false} title={t('ropaListTitle')} Icon={Database}
          action={<Badge tone="primary">{ROPA_ENTRIES.length}</Badge>}>
          <div style={{ maxHeight: 660, overflowY: 'auto' }}>
            {ROPA_ENTRIES.map(e => {
              const isSelected = e.code === selected;
              return (
                <button key={e.code} onClick={() => setSelected(e.code)}
                  style={{
                    width: '100%', textAlign: 'start',
                    padding: '12px 14px', border: 'none', cursor: 'pointer',
                    borderBottom: '1px solid var(--border)',
                    background: isSelected ? 'var(--primary-50)' : 'transparent',
                    display: 'flex', flexDirection: 'column', gap: 4,
                  }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    <span className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>{e.code}</span>
                    <Badge tone="info">{lang === 'ar' ? LAWFUL_BASIS_LABEL[e.lawfulBasis].ar : LAWFUL_BASIS_LABEL[e.lawfulBasis].en}</Badge>
                    {e.crossBorder && <Badge tone="warning">{t('ropaCrossBorderTag')}</Badge>}
                  </div>
                  <div style={{ fontSize: 13, fontWeight: 600 }}>
                    {lang === 'ar' ? e.activityNameAr : e.activityNameEn}
                  </div>
                  <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>
                    {e.dataSubjects.length} {t('ropaSubjectGroups')} · {e.categories.length} {t('ropaCategoriesSuffix')}
                  </div>
                </button>
              );
            })}
          </div>
        </Card>

        {/* Detail */}
        <RopaDetail entry={current} t={t} lang={lang} />
      </div>

      {/* Consent rollup */}
      <div style={{ marginTop: 16 }}>
        <Card title={t('ropaConsentTitle')} Icon={Fingerprint}>
          <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
            {t('ropaConsentSub')}
          </div>
          {CONSENT_SUMMARIES.length === 0
            ? <div style={{ padding: 16, fontSize: 12, color: 'var(--ink-3)', textAlign: 'center' }}>{t('ropaConsentEmpty')}</div>
            : <div style={{ display: 'flex', flexDirection: 'column' }}>
                {CONSENT_SUMMARIES.map(c => {
                  const ropa = ROPA_ENTRIES.find(r => r.code === c.ropaCode);
                  return (
                    <div key={c.ropaCode} style={{
                      padding: '12px 0', borderBottom: '1px dashed var(--border)',
                      display: 'grid', gridTemplateColumns: '1fr auto auto auto auto', gap: 12, alignItems: 'center',
                    }}>
                      <div>
                        <div style={{ fontSize: 13, fontWeight: 600 }}>
                          {ropa ? (lang === 'ar' ? ropa.activityNameAr : ropa.activityNameEn) : c.ropaCode}
                        </div>
                        <div className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>{c.ropaCode}</div>
                      </div>
                      <div style={{ textAlign: 'center' }}>
                        <div style={{ fontSize: 18, fontWeight: 700, color: 'var(--success-fg)' }} className="mono">{c.active.toLocaleString()}</div>
                        <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>{t('ropaConsentActive')}</div>
                      </div>
                      <div style={{ textAlign: 'center' }}>
                        <div style={{ fontSize: 18, fontWeight: 700, color: 'var(--warning-fg)' }} className="mono">{c.withdrawn.toLocaleString()}</div>
                        <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>{t('ropaConsentWithdrawn')}</div>
                      </div>
                      <div style={{ textAlign: 'center' }}>
                        <div style={{ fontSize: 18, fontWeight: 700, color: 'var(--ink-3)' }} className="mono">{c.expired.toLocaleString()}</div>
                        <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>{t('ropaConsentExpired')}</div>
                      </div>
                      <Badge tone={c.withdrawalRate >= 5 ? 'warning' : 'success'}>
                        {c.withdrawalRate.toFixed(1)}% {t('ropaConsentWithdrawRate')}
                      </Badge>
                    </div>
                  );
                })}
              </div>
          }
        </Card>
      </div>
    </Shell>
  );
}

function RopaDetail({ entry, t, lang }: { entry: RopaEntry; t: (k: string) => string; lang: 'en' | 'ar' }) {
  const sourceAsset = ropaSourceAsset(entry);
  const retentionYears = Math.round((entry.retentionDays / 365) * 10) / 10;

  // Live-fetched counts for the three PDPL workflows attached to this ROPA.
  const [counts, setCounts] = useState<RopaCounts | null>(null);
  useEffect(() => {
    let cancelled = false;
    setCounts(null);
    ropaWorkflowsCounts(entry.code)
      .then(c => { if (!cancelled) setCounts(c); })
      .catch(() => { if (!cancelled) setCounts({ notices: 0, crossBorder: 0, disposal: 0 }); });
    return () => { cancelled = true; };
  }, [entry.code]);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <Card>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
          <span className="mono" style={{ fontSize: 11, color: 'var(--primary)' }}>{entry.code}</span>
          <Badge tone="info">{lang === 'ar' ? LAWFUL_BASIS_LABEL[entry.lawfulBasis].ar : LAWFUL_BASIS_LABEL[entry.lawfulBasis].en}</Badge>
          {entry.crossBorder && <Badge tone="warning">{t('ropaCrossBorderTag')}</Badge>}
        </div>
        <div style={{ fontSize: 18, fontWeight: 700, marginBottom: 6 }}>
          {lang === 'ar' ? entry.activityNameAr : entry.activityNameEn}
        </div>
        <div style={{ fontSize: 13, color: 'var(--ink-2)', lineHeight: 1.6 }}>
          {lang === 'ar' ? entry.purposeAr : entry.purposeEn}
        </div>
      </Card>

      {/* Two-column attribute grid */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
        <Card title={t('ropaCardCategories')} Icon={Database}>
          <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 6 }}>{t('ropaCategoriesLabel')}</div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4, marginBottom: 12 }}>
            {entry.categories.map(c => (
              <Badge key={c} tone={c === 'SPECIAL_CATEGORY' || c === 'BIOMETRIC' ? 'danger' : c === 'FINANCIAL' || c === 'HEALTH' ? 'warning' : 'neutral'}>
                {lang === 'ar' ? CATEGORY_LABEL[c].ar : CATEGORY_LABEL[c].en}
              </Badge>
            ))}
          </div>
          <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 6 }}>{t('ropaSubjectsLabel')}</div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
            {entry.dataSubjects.map(s => (
              <Badge key={s} tone="info">
                <Users size={10} style={{ marginInlineEnd: 4, verticalAlign: 'middle' }} />
                {lang === 'ar' ? SUBJECT_LABEL[s].ar : SUBJECT_LABEL[s].en}
              </Badge>
            ))}
          </div>
        </Card>

        <Card title={t('ropaCardSource')} Icon={Database}>
          {sourceAsset ? (
            <>
              <div style={{ fontSize: 13, fontWeight: 600 }}>
                {lang === 'ar' ? sourceAsset.nameAr : sourceAsset.nameEn}
              </div>
              <div className="mono" style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 2 }}>
                {sourceAsset.code}
              </div>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4, marginTop: 8 }}>
                <Badge tone={sourceAsset.dataResidency === 'KSA' ? 'success' : sourceAsset.dataResidency === 'GCC' ? 'info' : 'warning'}>
                  {t('ropaResidency')} {sourceAsset.dataResidency}
                </Badge>
                {sourceAsset.regulatorScope.filter(r => r !== 'NONE').map(r => (
                  <Badge key={r} tone="primary">{r}</Badge>
                ))}
              </div>
            </>
          ) : (
            <div style={{ fontSize: 12, color: 'var(--ink-3)' }}>{t('ropaSourceUnknown')}</div>
          )}
        </Card>
      </div>

      {/* Retention + sub-processors + review */}
      <Card title={t('ropaCardOps')} Icon={ShieldCheck}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 16, marginBottom: 12 }}>
          <div>
            <div style={{ fontSize: 10, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>{t('ropaRetention')}</div>
            <div className="mono" style={{ fontSize: 16, fontWeight: 700, marginTop: 4 }}>
              {entry.retentionDays} {t('ropaDays')} ≈ {retentionYears} {t('ropaYears')}
            </div>
          </div>
          <div>
            <div style={{ fontSize: 10, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>{t('ropaLastReviewed')}</div>
            <HijriDate iso={entry.lastReviewedAt} layout="stacked" mono />
          </div>
          <div>
            <div style={{ fontSize: 10, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>{t('ropaOwner')}</div>
            <div className="mono" style={{ fontSize: 12, marginTop: 4 }}>{entry.ownerEmail}</div>
          </div>
        </div>
        <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: 6 }}>
          {t('ropaSubProcessors')}
        </div>
        {entry.subProcessors.length === 0
          ? <div style={{ fontSize: 12, color: 'var(--ink-3)' }}>{t('ropaSubProcessorsNone')}</div>
          : <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              {entry.subProcessors.map(sp => (
                <div key={sp} style={{
                  fontSize: 12, padding: '6px 10px',
                  background: 'var(--surface-2)', borderRadius: 'var(--r-sm)',
                  display: 'inline-flex', alignItems: 'center', gap: 6,
                }}>
                  <ChevronRight size={11} color="var(--ink-3)" />
                  {sp}
                </div>
              ))}
            </div>}
      </Card>

      {/* PDPL workflows attached to this ROPA — entry point for the three
       *  privacy-extension modules. Each chip shows live count + click
       *  deep-links to /data-lifecycle filtered by ropaCode. "+ New"
       *  opens the same page with the create modal pre-filled. */}
      <RopaWorkflowsCard ropaCode={entry.code} counts={counts} t={t} />
    </div>
  );
}

/**
 * Renders the three PDPL-workflow chips + action buttons for a ROPA.
 * Pure presentation — fetch happens upstream in RopaDetail's useEffect.
 */
function RopaWorkflowsCard({ ropaCode, counts, t }: {
  ropaCode: string;
  counts: RopaCounts | null;
  t: (k: string) => string;
}) {
  const loading = counts === null;
  const c = counts ?? { notices: 0, crossBorder: 0, disposal: 0 };

  return (
    <Card title={t('ropaPdplWorkflowsTitle')} Icon={Fingerprint}
      action={<Badge tone="info">PDPL Art. 12 · 18 · 29</Badge>}>
      <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12, lineHeight: 1.5 }}>
        {t('ropaPdplWorkflowsSub')}
      </div>
      <div style={{
        display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12,
      }}>
        <WorkflowChip
          Icon={FileText}
          label={t('ropaWfNotices')}
          articleLabel={t('ropaWfNoticesArticle')}
          count={c.notices}
          loading={loading}
          viewLink={buildDeepLink({ tab: 'notice', ropaCode })}
          newLink={buildDeepLink({ tab: 'notice', ropaCode, newRecord: true })}
          tone="success"
          t={t}
        />
        <WorkflowChip
          Icon={Plane}
          label={t('ropaWfCrossBorder')}
          articleLabel={t('ropaWfCrossBorderArticle')}
          count={c.crossBorder}
          loading={loading}
          viewLink={buildDeepLink({ tab: 'cbt', ropaCode })}
          newLink={buildDeepLink({ tab: 'cbt', ropaCode, newRecord: true })}
          tone="warning"
          t={t}
        />
        <WorkflowChip
          Icon={Trash2}
          label={t('ropaWfDisposal')}
          articleLabel={t('ropaWfDisposalArticle')}
          count={c.disposal}
          loading={loading}
          viewLink={buildDeepLink({ tab: 'disposal', ropaCode })}
          newLink={buildDeepLink({ tab: 'disposal', ropaCode, newRecord: true })}
          tone="primary"
          t={t}
        />
      </div>
    </Card>
  );
}

function WorkflowChip({ Icon, label, articleLabel, count, loading, viewLink, newLink, tone, t }: {
  Icon: typeof FileText;
  label: string;
  articleLabel: string;
  count: number;
  loading: boolean;
  viewLink: string;
  newLink: string;
  tone: 'success' | 'warning' | 'primary';
  t: (k: string) => string;
}) {
  const fg = tone === 'success' ? 'var(--success-fg)'
           : tone === 'warning' ? 'var(--warning-fg)'
           : 'var(--primary-700)';
  return (
    <div style={{
      padding: 12, background: 'var(--surface-2)',
      border: '1px solid var(--border)', borderRadius: 'var(--r-md)',
      display: 'flex', flexDirection: 'column', gap: 8,
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <Icon size={16} color={fg} />
        <div style={{ flex: 1, fontSize: 12, fontWeight: 600 }}>{label}</div>
        <span style={{
          fontSize: 18, fontWeight: 700, fontFamily: 'var(--font-mono)', color: fg,
        }}>
          {loading ? '…' : count}
        </span>
      </div>
      <div style={{ fontSize: 10, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
        {articleLabel}
      </div>
      <div style={{ display: 'flex', gap: 6 }}>
        <Link to={viewLink} style={{ textDecoration: 'none', flex: 1 }}>
          <Button size="sm" variant="ghost" Icon={ArrowUpRight} style={{ width: '100%' }}>
            {count > 0 ? t('ropaWfActOpen') : t('ropaWfActOpenEmpty')}
          </Button>
        </Link>
        <Link to={newLink} style={{ textDecoration: 'none' }}>
          <Button size="sm" variant="primary" Icon={Plus} title={t('ropaWfActNew')}>
            {t('ropaWfActNew')}
          </Button>
        </Link>
      </div>
    </div>
  );
}
