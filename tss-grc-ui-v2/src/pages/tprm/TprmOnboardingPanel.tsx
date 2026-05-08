// TPRM Onboarding Pipeline + Reassessment Due dashboard — DD-74.
//
// Two panels that close the gap the Overview lifecycle cards have always
// hinted at but never delivered:
//
//   ┌─ Onboarding Pipeline (kanban) ──────────────────────────────────┐
//   │  Vendors moving through INTAKE → TIER_ASSESSMENT → DUE_DILIGENCE  │
//   │  → SECURITY_REVIEW → CONTRACT → EXEC_APPROVAL → ACTIVE.           │
//   │  Each lane shows count, owner, exit gate. Tier 1 cards have a      │
//   │  red rim so a critical vendor stuck in pipeline is visible.        │
//   └────────────────────────────────────────────────────────────────────┘
//
//   ┌─ Reassessment Due (cadence) ────────────────────────────────────┐
//   │  Active vendors bucketed into OVERDUE / DUE_SOON / ON_TRACK /     │
//   │  AD_HOC by tier-driven cadence (T1 annual, T2 biennial, T3       │
//   │  triennial, T4 ad-hoc). The OVERDUE list is what a TPRM lead's   │
//   │  Monday morning is about.                                         │
//   └────────────────────────────────────────────────────────────────────┘
//
// Architecture: ServiceNow Vendor Portal "Pipeline" board + OneTrust
// Vendorpedia "Vendor Risk Calendar".

import { useEffect, useState } from 'react';
import { Truck, AlertTriangle, Clock, CheckCircle2, ShieldCheck, ChevronRight } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Kpi } from '../../components/ui/Kpi';
import { EmptyState } from '../../components/ui/EmptyState';
import { useI18n } from '../../lib/i18n';
import {
  VENDOR_TIERS, ONBOARDING_STAGES, ONBOARDING_ORDER,
  nextReviewDate, daysUntilReview, reviewBucket,
  type VendorTier, type OnboardingStage,
} from '../../lib/vendorOnboarding';
import type { TprmVendorDto } from '../../lib/api';
import { VENDOR_TPRM_META, type VendorTprmMeta } from './mockTprm';

type EnrichedVendor = TprmVendorDto & { meta: VendorTprmMeta };

/** DD-78: when a vendor record has no entry in VENDOR_TPRM_META, default
 *  tier/stage/lastAssessedAt so promoted intake submissions land in the
 *  INTAKE lane of the kanban. Also reads the stage-advance overlay so any
 *  manual progression made through the pipeline UI persists across reloads. */
function enrich(vendors: TprmVendorDto[]): EnrichedVendor[] {
  const stageOverlay = readStageOverlay();
  return vendors.map(v => {
    const seeded = VENDOR_TPRM_META[v.id];
    const overlaid = stageOverlay[v.id];
    const meta: VendorTprmMeta = seeded ?? {
      tier: 'TIER_2',
      onboardingStage: 'INTAKE',
      lastAssessedAt: new Date().toISOString().slice(0, 10),
    };
    if (overlaid) meta.onboardingStage = overlaid;
    return { ...v, meta };
  });
}

// ── Stage-advance overlay (localStorage) ───────────────────────────────
//
// VENDOR_TPRM_META is a static seed; when a TPRM analyst clicks "Advance"
// on a card we need that change to persist. Same pattern as the promoted-
// records overlay (DD-69) — a small localStorage record keyed by vendor id.
//
// DD-80 adds a parallel audit-history overlay. Every advance writes a
// timestamped event so the platform can satisfy SAMA Outsourcing §5.3 +
// SOX 404 audit-trail requirements (who moved this vendor through which
// gate, and when). History is append-only.

const STAGE_OVERLAY_KEY = 'grc.tprm.stages';
const STAGE_HISTORY_KEY = 'grc.tprm.stageHistory';

export interface StageAdvanceEvent {
  at: string;
  actor: string;
  fromStage: OnboardingStage;
  toStage: OnboardingStage;
}

function readStageOverlay(): Record<number, OnboardingStage> {
  try {
    const raw = localStorage.getItem(STAGE_OVERLAY_KEY);
    if (!raw) return {};
    return JSON.parse(raw) as Record<number, OnboardingStage>;
  } catch { return {}; }
}

function writeStageOverlay(o: Record<number, OnboardingStage>) {
  try { localStorage.setItem(STAGE_OVERLAY_KEY, JSON.stringify(o)); } catch { /* ignore */ }
  try { window.dispatchEvent(new CustomEvent('grc.tprm.stages.changed')); } catch { /* ignore */ }
}

function readStageHistory(): Record<number, StageAdvanceEvent[]> {
  try {
    const raw = localStorage.getItem(STAGE_HISTORY_KEY);
    if (!raw) return {};
    return JSON.parse(raw) as Record<number, StageAdvanceEvent[]>;
  } catch { return {}; }
}

function writeStageHistory(h: Record<number, StageAdvanceEvent[]>) {
  try { localStorage.setItem(STAGE_HISTORY_KEY, JSON.stringify(h)); } catch { /* ignore */ }
}

function advanceVendorStage(vendorId: number, current: OnboardingStage): OnboardingStage | null {
  const next = ONBOARDING_STAGES[current].next;
  if (!next) return null;
  const overlay = readStageOverlay();
  overlay[vendorId] = next;
  writeStageOverlay(overlay);
  // DD-80: append audit event. In demo mode we use a fixed actor; once a
  // real auth context lands the JWT subject would feed in here.
  const history = readStageHistory();
  const event: StageAdvanceEvent = {
    at: new Date().toISOString(),
    actor: 'demo.analyst@tss.sa',
    fromStage: current,
    toStage: next,
  };
  history[vendorId] = [...(history[vendorId] ?? []), event];
  writeStageHistory(history);
  return next;
}

/** Read the audit history for one vendor — used to render the timeline
 *  on each pipeline card (collapsed by default). */
function getStageHistory(vendorId: number): StageAdvanceEvent[] {
  return readStageHistory()[vendorId] ?? [];
}

// ── Onboarding Pipeline ─────────────────────────────────────────────────

export function TprmOnboardingPipeline({ vendors }: { vendors: TprmVendorDto[] }) {
  const { t, lang } = useI18n();
  // DD-78: re-enrich when the stage overlay changes (advance button click).
  const [overlayTick, setOverlayTick] = useState(0);
  useEffect(() => {
    const refresh = () => setOverlayTick(x => x + 1);
    window.addEventListener('grc.tprm.stages.changed', refresh);
    window.addEventListener('storage', refresh);
    return () => {
      window.removeEventListener('grc.tprm.stages.changed', refresh);
      window.removeEventListener('storage', refresh);
    };
  }, []);
  const enriched = (() => { void overlayTick; return enrich(vendors); })();
  const inPipeline = enriched.filter(v => v.meta.onboardingStage !== 'ACTIVE' && v.meta.onboardingStage !== 'OFFBOARDING');

  // The pipeline shows the 6 lanes between INTAKE and EXEC_APPROVAL —
  // ACTIVE and OFFBOARDING are not pipeline lanes, they are end states.
  const pipelineLanes: OnboardingStage[] = ONBOARDING_ORDER.filter(s => s !== 'ACTIVE');

  return (
    <Card title={t('tprmOnboardingTitle')} Icon={Truck}
      action={<Badge tone="primary">{inPipeline.length} {t('tprmOnboardingInPipeline')}</Badge>}>
      <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12, lineHeight: 1.5 }}>
        {t('tprmOnboardingSub')}
      </div>
      <div style={{
        display: 'grid',
        gridTemplateColumns: `repeat(${pipelineLanes.length}, 1fr)`,
        gap: 8,
        overflowX: 'auto',
        paddingBottom: 4,
      }}>
        {pipelineLanes.map(stage => {
          const stageMeta = ONBOARDING_STAGES[stage];
          const inLane = inPipeline.filter(v => v.meta.onboardingStage === stage);
          return (
            <div key={stage} style={{
              minWidth: 180,
              background: 'var(--surface-2)',
              border: '1px solid var(--border)',
              borderRadius: 'var(--r-sm)',
              padding: 10,
              display: 'flex', flexDirection: 'column', gap: 8,
            }}>
              <div>
                <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--ink-1)', marginBottom: 2 }}>
                  {lang === 'ar' ? stageMeta.labelAr : stageMeta.labelEn}
                </div>
                <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>
                  {lang === 'ar' ? stageMeta.ownerAr : stageMeta.ownerEn} · SLA {stageMeta.slaDays}{t('tprmOnboardingSlaDays')}
                </div>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                {inLane.length === 0
                  ? <div style={{ fontSize: 11, color: 'var(--ink-3)', fontStyle: 'italic' }}>{t('tprmOnboardingLaneEmpty')}</div>
                  : inLane.map(v => <PipelineCard key={v.id} v={v} t={t} lang={lang} />)
                }
              </div>
            </div>
          );
        })}
      </div>
    </Card>
  );
}

function PipelineCard({ v, t, lang }: { v: EnrichedVendor; t: (k: string) => string; lang: 'en' | 'ar' }) {
  const tier = VENDOR_TIERS[v.meta.tier];
  const nextStage = ONBOARDING_STAGES[v.meta.onboardingStage].next;
  const nextStageLabel = nextStage ? (lang === 'ar' ? ONBOARDING_STAGES[nextStage].labelAr : ONBOARDING_STAGES[nextStage].labelEn) : null;

  // DD-80: read the audit history for this vendor so the card shows
  // who/when moved it through each gate (collapsed by default).
  const vid = typeof v.id === 'number' ? v.id : Number(v.id);
  const history = getStageHistory(vid);

  function onAdvance(e: React.MouseEvent) {
    e.preventDefault();
    e.stopPropagation();
    advanceVendorStage(vid, v.meta.onboardingStage);
  }

  return (
    <div style={{
      padding: '8px 10px',
      background: 'var(--surface-1)',
      borderRadius: 'var(--r-sm)',
      borderInlineStart: `3px solid var(--${tier.tone === 'danger' ? 'danger' : tier.tone === 'warning' ? 'warning' : tier.tone === 'info' ? 'info' : 'border'}-fg, var(--border))`,
    }}>
      <Link to={`/vendors/${v.id}`} style={{ display: 'block', textDecoration: 'none', color: 'inherit' }}>
        <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink-1)', marginBottom: 2 }}>
          {lang === 'ar' && v.vendorNameAr ? v.vendorNameAr : v.vendorName}
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 10 }}>
          <Badge tone={tier.tone}>{lang === 'ar' ? tier.labelAr : tier.labelEn}</Badge>
          {v.country && <span style={{ color: 'var(--ink-3)' }}>· {v.country}</span>}
        </div>
        <div style={{ marginTop: 4, fontSize: 10, color: 'var(--ink-3)', lineHeight: 1.3 }}>
          {t('tprmOnboardingExitGate')}: {lang === 'ar' ? ONBOARDING_STAGES[v.meta.onboardingStage].exitGateAr.slice(0, 60) : ONBOARDING_STAGES[v.meta.onboardingStage].exitGateEn.slice(0, 60)}…
        </div>
      </Link>
      {nextStage && (
        <button onClick={onAdvance} style={{
          display: 'inline-flex', alignItems: 'center', gap: 4,
          marginTop: 6, padding: '3px 8px',
          background: 'var(--primary-50, var(--info-bg))',
          color: 'var(--primary-700, var(--info-fg))',
          border: '1px solid var(--primary-100, var(--border))',
          borderRadius: 'var(--r-sm)',
          fontSize: 10, fontWeight: 600, cursor: 'pointer',
        }}>
          {t('tprmOnboardingAdvance')} → {nextStageLabel}
          <ChevronRight size={10} />
        </button>
      )}

      {history.length > 0 && (
        <details style={{ marginTop: 6 }}>
          <summary style={{ fontSize: 10, color: 'var(--ink-3)', cursor: 'pointer' }}>
            {t('tprmOnboardingHistoryToggle')} · {history.length}
          </summary>
          <div style={{ marginTop: 4, fontSize: 10, color: 'var(--ink-3)', lineHeight: 1.5 }}>
            {history.map((ev, i) => {
              const fromLbl = lang === 'ar' ? ONBOARDING_STAGES[ev.fromStage].labelAr : ONBOARDING_STAGES[ev.fromStage].labelEn;
              const toLbl   = lang === 'ar' ? ONBOARDING_STAGES[ev.toStage].labelAr   : ONBOARDING_STAGES[ev.toStage].labelEn;
              return (
                <div key={i} style={{ borderTop: i ? '1px dashed var(--border)' : 'none', paddingTop: i ? 4 : 0, marginTop: i ? 4 : 0 }}>
                  <span className="mono" style={{ color: 'var(--ink-2)' }}>{ev.at.slice(0, 16).replace('T', ' ')}</span>
                  {' · '}
                  <span style={{ color: 'var(--ink-2)' }}>{ev.actor}</span>
                  <div>{fromLbl} → {toLbl}</div>
                </div>
              );
            })}
          </div>
        </details>
      )}
    </div>
  );
}

// ── Reassessment Due dashboard ──────────────────────────────────────────

export function TprmReassessmentDashboard({ vendors }: { vendors: TprmVendorDto[] }) {
  const { t, lang } = useI18n();
  const enriched = enrich(vendors).filter(v => v.meta.onboardingStage === 'ACTIVE');

  const overdue   = enriched.filter(v => reviewBucket(v.meta.tier, v.meta.lastAssessedAt) === 'OVERDUE');
  const dueSoon   = enriched.filter(v => reviewBucket(v.meta.tier, v.meta.lastAssessedAt) === 'DUE_SOON');
  const onTrack   = enriched.filter(v => reviewBucket(v.meta.tier, v.meta.lastAssessedAt) === 'ON_TRACK');
  const adHoc     = enriched.filter(v => reviewBucket(v.meta.tier, v.meta.lastAssessedAt) === 'AD_HOC');

  return (
    <>
      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={AlertTriangle} label={t('tprmReassessKpiOverdue')}  value={overdue.length}  tone="danger" />
        <Kpi Icon={Clock}         label={t('tprmReassessKpiDueSoon')}  value={dueSoon.length}  tone="warning" />
        <Kpi Icon={CheckCircle2}  label={t('tprmReassessKpiOnTrack')}  value={onTrack.length}  tone="success" />
        <Kpi Icon={ShieldCheck}   label={t('tprmReassessKpiAdHoc')}    value={adHoc.length}    tone="info" />
      </div>

      <Card title={t('tprmReassessTitle')} Icon={Clock}>
        <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12, lineHeight: 1.5 }}>
          {t('tprmReassessSub')}
        </div>
        {enriched.length === 0
          ? <EmptyState Icon={Clock} title={t('tprmReassessEmpty')} sub={t('tprmReassessEmptySub')} />
          : <div style={{ display: 'flex', flexDirection: 'column' }}>
              <ReassessSection title={t('tprmReassessSecOverdue')} tone="danger" rows={overdue} t={t} lang={lang} />
              <ReassessSection title={t('tprmReassessSecDueSoon')} tone="warning" rows={dueSoon} t={t} lang={lang} />
              <ReassessSection title={t('tprmReassessSecOnTrack')} tone="success" rows={onTrack} t={t} lang={lang} />
              <ReassessSection title={t('tprmReassessSecAdHoc')} tone="neutral" rows={adHoc} t={t} lang={lang} />
            </div>
        }
      </Card>

      <Card title={t('tprmCadenceTitle')} Icon={ShieldCheck}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 10 }}>
          {(['TIER_1', 'TIER_2', 'TIER_3', 'TIER_4'] as VendorTier[]).map(t2 => {
            const meta = VENDOR_TIERS[t2];
            const count = enriched.filter(v => v.meta.tier === t2).length;
            return (
              <div key={t2} style={{
                padding: 12, background: 'var(--surface-2)',
                border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
              }}>
                <div style={{ marginBottom: 4 }}><Badge tone={meta.tone}>{lang === 'ar' ? meta.labelAr : meta.labelEn}</Badge></div>
                <div style={{ fontSize: 22, fontWeight: 700, color: 'var(--ink-1)' }}>{count}</div>
                <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }}>
                  {lang === 'ar' ? meta.cadenceLabelAr : meta.cadenceLabelEn}
                </div>
                <div style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 6, lineHeight: 1.4 }}>
                  {lang === 'ar' ? meta.profileAr : meta.profileEn}
                </div>
              </div>
            );
          })}
        </div>
      </Card>
    </>
  );
}

function ReassessSection({ title, tone, rows, t, lang }: {
  title: string;
  tone: 'danger' | 'warning' | 'success' | 'neutral';
  rows: EnrichedVendor[];
  t: (k: string) => string;
  lang: 'en' | 'ar';
}) {
  if (rows.length === 0) return null;
  return (
    <div style={{ marginTop: 6, marginBottom: 10 }}>
      <div style={{
        display: 'flex', alignItems: 'center', gap: 8,
        padding: '6px 0', marginBottom: 4,
      }}>
        <Badge tone={tone} dot>{title}</Badge>
        <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>· {rows.length}</span>
      </div>
      {rows.map(v => {
        const tier = VENDOR_TIERS[v.meta.tier];
        const days = daysUntilReview(v.meta.tier, v.meta.lastAssessedAt);
        const due = nextReviewDate(v.meta.tier, v.meta.lastAssessedAt);
        return (
          <Link key={v.id} to={`/vendors/${v.id}`} style={{
            display: 'grid',
            gridTemplateColumns: '1fr 110px 130px 120px 60px',
            gap: 10,
            padding: '8px 0',
            borderBottom: '1px dashed var(--border)',
            alignItems: 'center',
            textDecoration: 'none',
            color: 'inherit',
          }}>
            <div>
              <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)' }}>{lang === 'ar' && v.vendorNameAr ? v.vendorNameAr : v.vendorName}</div>
              <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>{v.vendorType ?? '—'}</div>
            </div>
            <Badge tone={tier.tone}>{lang === 'ar' ? tier.labelAr : tier.labelEn}</Badge>
            <span className="mono" style={{ fontSize: 12, color: 'var(--ink-2)' }}>
              {t('tprmReassessLast')}: {v.meta.lastAssessedAt}
            </span>
            <span className="mono" style={{
              fontSize: 12, fontWeight: 600,
              color: tone === 'danger' ? 'var(--danger-fg)' : tone === 'warning' ? 'var(--warning-fg)' : 'var(--ink-2)',
            }}>
              {due ? due.toISOString().slice(0, 10) : '—'}
            </span>
            <span className="mono" style={{
              fontSize: 11, textAlign: 'end',
              color: tone === 'danger' ? 'var(--danger-fg)' : 'var(--ink-3)',
            }}>
              {days != null ? (days < 0 ? `${Math.abs(days)}d ${t('capaOverdue')}` : `${days}d`) : '—'}
            </span>
          </Link>
        );
      })}
    </div>
  );
}
