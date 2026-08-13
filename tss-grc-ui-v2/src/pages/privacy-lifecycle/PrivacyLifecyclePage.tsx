import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Files, ShieldAlert, Globe, UserCheck, Clock,
  AlertTriangle, CheckCircle2, FileText, Activity,
} from 'lucide-react';
import { LifecyclePipeline, type PipelineStage, type PipelineCard, dueLabel } from '../../components/lifecycle/LifecyclePipeline';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  loadRopa, saveRopa, loadDpia, loadCbt, subscribePrivacy,
  detectHighRisk, aggregatePrivacy,
  ADEQUACY_BY_COUNTRY, ADEQUACY_TONE, ADEQUACY_LABEL, SAFEGUARD_LABEL,
  ROPA_STATUS_LABEL, ROPA_STATUS_TONE, LAWFUL_BASIS_LABEL,
  transitionDpia, createDpia, transitionCbt, upgradeCbtSafeguard,
  type RopaActivity,
  type DpiaAssessment, type DpiaStatus, type CrossBorderTransfer, type CbtStatus,
  type SafeguardMechanism,
} from '../../lib/privacyLifecycle';
// F2-202 — the DSR half of this page reads the real register at /api/privacy-lc/dsr-cases.
// Everything imported above is still browser-local; see `lib/lifecycleStores.ts`.
import {
  dsrApi, aggregateDsr, daysToDeadline, isPastDeadline, mayTransition,
  isOpen as isOpenDsr, DSR_ALLOWED,
  DSR_TYPE_LABEL, DSR_STATUS_LABEL, DSR_STATUS_TONE,
  type DsrCase, type DsrStatus, type DsrType,
} from '../../lib/privacyLcApi';
import { ApiError } from '../../lib/api';
import { useRealtimeOrgEvent } from '../../lib/useRealtimeEntity';
import '../../styles/pages/privacy-lifecycle.css';

type PrcTab = 'pipeline' | 'ropa' | 'dpia' | 'cbt' | 'dsr';

export function PrivacyLifecyclePage() {
  const { t, lang } = useI18n();
  const toast = useToast();

  const [tab, setTab] = useState<PrcTab>('pipeline');
  const [_tick, setTick] = useState(0);
  useEffect(() => subscribePrivacy(() => setTick(t => t + 1)), []);

  const stats = useMemo(() => aggregatePrivacy(), [_tick]);
  const ropa = useMemo(() => loadRopa(), [_tick]);
  const dpias = useMemo(() => loadDpia(), [_tick]);
  const cbts = useMemo(() => loadCbt(), [_tick]);

  // ─── DSR: the real register ────────────────────────────────────────────────
  //
  // F2-202. `dsrs` is `null` until the first response lands and stays `null` if the request
  // fails, because the tiles below distinguish "not yet known" from "zero" and a failed load
  // must not render as an empty-but-healthy register.
  const [dsrs, setDsrs] = useState<DsrCase[] | null>(null);
  const [dsrError, setDsrError] = useState<string | null>(null);
  const [busyDsr, setBusyDsr] = useState<string | null>(null);
  // One clock for every deadline comparison on the page, refreshed each minute.
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const id = window.setInterval(() => setNow(new Date()), 60_000);
    return () => window.clearInterval(id);
  }, []);

  const loadDsrCases = useCallback(async () => {
    setDsrError(null);
    try {
      // No fallback on any path. `useApiList` prefers a demo corpus when the server returns
      // zero rows — the load-bearing fallback that hid F2-158's 500 for the whole Role API.
      setDsrs(await dsrApi.list());
    } catch (e) {
      setDsrError(e instanceof ApiError ? e.message
        : (lang === 'ar' ? 'تعذّر تحميل سجل طلبات أصحاب البيانات' : 'Could not load the data-subject request register'));
      setDsrs(null);
    }
  }, [lang]);

  useEffect(() => { void loadDsrCases(); }, [loadDsrCases]);

  // Sprint 4 §2.7 — `privacy_lc.DsrCaseService` broadcasts on every DSR transition. Before
  // F2-202 this handler ticked a counter that re-read localStorage, so the page listened for
  // the server's own event and answered by re-reading the browser's copy of the data. Now it
  // refetches the register the event is about.
  useRealtimeOrgEvent('changed.dsr_case', () => { void loadDsrCases(); });

  const dsrStats = useMemo(() => dsrs && aggregateDsr(dsrs, now), [dsrs, now]);

  /** Decide through the server and replace the row from ITS response, never a local guess. */
  const runTransition = useCallback(async (id: string, status: DsrStatus, okMsg: string) => {
    setBusyDsr(id);
    try {
      const updated = await dsrApi.transition(id, { status });
      setDsrs(prev => prev ? prev.map(x => x.id === updated.id ? updated : x) : prev);
      toast.success(okMsg);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (lang === 'ar' ? 'فشل تحديث الطلب' : 'Could not update the request'));
    } finally {
      setBusyDsr(null);
    }
  }, [lang, toast]);

  return (
    <Shell title={t('prcTitle')} sub={t('prcSub')}>
      <PageHeader
        eyebrow={t('prcEyebrow')}
        title={t('prcTitle')}
        sub={t('prcPageSub')}
      />

      <div className="kpi-grid mb-lg">
        <Kpi Icon={Files}          label={t('prcKpiRopa')}          value={`${stats.ropaApproved} / ${stats.ropaTotal}`} tone="primary" to="/privacy?tab=ropa" />
        <Kpi Icon={ShieldAlert}    label={t('prcKpiDpiaHighRisk')}  value={String(stats.dpiaHighRisk)} tone={stats.dpiaHighRisk > 0 ? 'warning' : 'success'} to="/data-officer/dpia" />
        <Kpi Icon={Globe}          label={t('prcKpiCbt')}            value={`${stats.cbtAdequate} / ${stats.cbtTotal}`} tone="info" to="/privacy/data-flow" />
        <Kpi Icon={Globe}          label={t('prcKpiCbtBlocked')}     value={String(stats.cbtBlocked)} tone={stats.cbtBlocked > 0 ? 'danger' : 'success'} to="/privacy/data-flow" />
        {/*
          F2-202 — these three now count over the rows fetched from /api/privacy-lc/dsr-cases,
          and each links to the DSR tab on THIS page rather than to /data-officer/dsr. That
          route serves `data_officer.dsr_request`, a different table with a different status
          vocabulary (F2-196 measured three server-side DSR registers), so a figure counted
          here and a list opened there could never be expected to agree.

          `value={null}` while `dsrStats` is null renders UX-008's em-dash with `emptyHint` as
          the title and aria-label. A 0 would assert "no open requests" on a register we have
          not read yet, and an `AlertTriangle` tile falling to `success` would assert "nothing
          is overdue" — F2-197's invented all-clear, on a statutory clock.
        */}
        <Kpi Icon={UserCheck} label={t('prcKpiDsrOpen')}
          value={dsrStats ? String(dsrStats.open) : null}
          emptyHint={t('prcKpiDsrUnread')}
          tone={dsrStats && dsrStats.open > 0 ? 'info' : 'neutral'} to="?tab=dsr" />
        <Kpi Icon={AlertTriangle} label={t('prcKpiDsrOverdue')}
          value={dsrStats ? String(dsrStats.pastDeadline) : null}
          emptyHint={t('prcKpiDsrUnread')}
          tone={dsrStats && dsrStats.pastDeadline > 0 ? 'danger' : 'neutral'} to="?tab=dsr" />
        {/*
          Open, past the Article 4 deadline, and the platform has stamped no breach. F2-196
          found the SLA sweep excluding EXTENDED cases, so the requests most likely to breach
          were the only ones never escalated. This tile is the difference between the two
          questions, and it is measured — in prod both EXTENDED cases still carry no stamp.
        */}
        <Kpi Icon={Clock} label={t('prcKpiDsrUnrecorded')}
          value={dsrStats ? String(dsrStats.pastDeadlineUnrecorded) : null}
          emptyHint={t('prcKpiDsrUnread')}
          tone={dsrStats && dsrStats.pastDeadlineUnrecorded > 0 ? 'warning' : 'neutral'} to="?tab=dsr" />
      </div>

      {dsrError && (
        <Card title={t('prcDsrTitle')} Icon={UserCheck}>
          <div role="status" className="prc-sub">{dsrError}</div>
        </Card>
      )}

      <div className="prc-tab-row">
        {([
          { id: 'pipeline', labelKey: 'lpcTabPipeline', Icon: Activity },
          { id: 'ropa', labelKey: 'prcTabRopa', Icon: Files },
          { id: 'dpia', labelKey: 'prcTabDpia', Icon: ShieldAlert },
          { id: 'cbt',  labelKey: 'prcTabCbt',  Icon: Globe },
          { id: 'dsr',  labelKey: 'prcTabDsr',  Icon: UserCheck },
        ] as { id: PrcTab; labelKey: string; Icon: typeof Files }[]).map(tabDef => (
          <button key={tabDef.id} onClick={() => setTab(tabDef.id)}
            className={`prc-tab-btn${tab === tabDef.id ? ' prc-tab-btn--on' : ''}`}>
            <tabDef.Icon size={13} />
            {t(tabDef.labelKey)}
          </button>
        ))}
      </div>

      {tab === 'pipeline' && <DsrPipeline dsrs={dsrs ?? []} now={now}
        onAdvance={(id, fromStatus) => {
          const next: Record<string, DsrStatus> = {
            INTAKE: 'VERIFYING_IDENTITY',
            VERIFYING_IDENTITY: 'COLLECTING',
            COLLECTING: 'REDACTING',
            REDACTING: 'DELIVERED',
          };
          const target = next[fromStatus];
          // The server answers 409 outside its own machine; asking first only avoids offering
          // a button that will be refused. It is not a second opinion about what is legal.
          if (!target || !mayTransition(fromStatus, target)) return;
          void runTransition(id, target, t('lpcAdvancedToast'));
        }}
        onCardClick={() => setTab('dsr')} />}

      {tab === 'ropa' && <RopaTab ropa={ropa}
        onCreate={() => {
          const id = `ROPA-${new Date().getFullYear()}-${String(ropa.length + 1).padStart(3, '0')}`;
          saveRopa({
            id,
            nameEn: 'New processing activity', nameAr: 'نشاط معالجة جديد',
            lawfulBasis: 'CONSENT',
            purposesEn: '', purposesAr: '',
            dataCategories: [], subjectCategories: [],
            recipientsEn: '', recipientsAr: '',
            storageLocation: 'Riyadh primary',
            retentionPeriodEn: '', retentionPeriodAr: '',
            securityMeasures: [],
            owner: '',
            status: 'DRAFT',
            createdAt: new Date().toISOString(),
          });
          toast.info(t('prcRopaCreatedToast'));
        }}
        onApprove={(r) => {
          saveRopa({ ...r, status: 'APPROVED', approvedBy: 'DPO + CRO', approvedAt: new Date().toISOString() });
          toast.success(t('prcRopaApprovedToast'));
        }} />}
      {tab === 'dpia' && <DpiaTab dpias={dpias} ropa={ropa}
        onCreate={(ropaId) => { createDpia(ropaId); toast.info(t('prcDpiaCreatedToast')); }}
        onTransition={(d, status) => {
          const by = status === 'APPROVED' ? 'Layla Al-Ghamdi (DPO)' : undefined;
          transitionDpia(d.id, status, by);
          toast.info(`DPIA ${status}`);
        }} />}
      {tab === 'cbt'  && <CbtTab  cbts={cbts}  ropa={ropa}
        onTransition={(c, status) => {
          const by = status === 'APPROVED' ? 'Layla Al-Ghamdi (DPO)' : undefined;
          transitionCbt(c.id, status, by);
          toast.info(`CBT ${status}`);
        }}
        onUpgradeSafeguard={(c, sg) => {
          upgradeCbtSafeguard(c.id, sg);
          toast.success(t('prcCbtUpgradedToast'));
        }} />}
      {tab === 'dsr'  && <DsrTab dsrs={dsrs} now={now} busyId={busyDsr}
        onCreate={async (type, name, idLast4, email) => {
          try {
            // No id and no deadline in this payload. The server mints the reference code and
            // the PDPL Article 4 deadline; F2-172 settled that only the server may issue
            // either, and the browser copy minted both.
            const created = await dsrApi.create({
              type, subjectName: name, nationalIdLast4: idLast4, contactEmail: email,
            });
            setDsrs(prev => prev ? [created, ...prev] : [created]);
            toast.success(`${t('prcDsrCreatedToast')} — ${created.id}`);
          } catch (e) {
            toast.danger(e instanceof ApiError ? e.message
              : (lang === 'ar' ? 'تعذّر تسجيل الطلب' : 'Could not file the request'));
          }
        }}
        onTransition={(d, status) =>
          void runTransition(d.id, status,
            `${t('prcDsrStatusUpdated')}: ${DSR_STATUS_LABEL[status][lang]}`)}
        onExtend={(d) =>
          // Just the transition. The server sets `extended` and adds the 30 days itself
          // (DsrCaseService: "Article 4: extend to 60 days total"). The old handler recomputed
          // that deadline in the browser and wrote its own extension justification — inventing
          // a reason on the operator's behalf for a statutory extension.
          void runTransition(d.id, 'EXTENDED', t('prcDsrExtendedToast'))} />}
    </Shell>
  );
}

// ─── ROPA tab ───────────────────────────────────────────────────────────────

function RopaTab({ ropa, onCreate, onApprove }: {
  ropa: RopaActivity[];
  onCreate: () => void;
  onApprove: (r: RopaActivity) => void;
}) {
  const { t, lang } = useI18n();
  return (
    <Card title={t('prcRopaTitle')} Icon={Files}
      action={<Button size="sm" variant="secondary" onClick={onCreate}>{t('prcRopaCreate')}</Button>}>
      <div className="prc-card-sub">
        {t('prcRopaSub')}
      </div>
      {ropa.map(r => {
        const hr = detectHighRisk(r);
        return (
          <div key={r.id} className="prc-item">
            <div className="prc-item-head">
              <div className="prc-item-main">
                <div className="prc-item-badges">
                  <span className="mono prc-item-code">{r.id}</span>
                  <Badge tone={ROPA_STATUS_TONE[r.status]}>{ROPA_STATUS_LABEL[r.status][lang]}</Badge>
                  <Badge tone="info">{LAWFUL_BASIS_LABEL[r.lawfulBasis][lang]}</Badge>
                  {hr.isHigh && <Badge tone="warning"><AlertTriangle size={11} /> {t('prcHighRisk')}</Badge>}
                  {r.dpiaId && <Badge tone="primary">{r.dpiaId}</Badge>}
                  {r.cbtId && <Badge tone="primary">{r.cbtId}</Badge>}
                </div>
                <div className="prc-item-title">{lang === 'ar' ? r.nameAr : r.nameEn}</div>
                <div className="prc-item-owner">
                  {t('prcRopaOwner')}: <b>{r.owner || '—'}</b>
                </div>
              </div>
              {r.status === 'IN_REVIEW' && <Button size="sm" variant="primary" Icon={CheckCircle2} onClick={() => onApprove(r)}>{t('prcApprove')}</Button>}
            </div>

            {/* Article 22 9-field grid */}
            <div className="prc-ropa-grid">
              <FieldRow label={t('prcRopaPurposes')} value={lang === 'ar' ? r.purposesAr : r.purposesEn} />
              <FieldRow label={t('prcRopaRecipients')} value={lang === 'ar' ? r.recipientsAr : r.recipientsEn} />
              <FieldRow label={t('prcRopaDataCats')} value={r.dataCategories.join(', ') || '—'} />
              <FieldRow label={t('prcRopaSubjectCats')} value={r.subjectCategories.join(', ') || '—'} />
              <FieldRow label={t('prcRopaStorage')} value={r.storageLocation} />
              <FieldRow label={t('prcRopaRetention')} value={lang === 'ar' ? r.retentionPeriodAr : r.retentionPeriodEn} />
              <FieldRow label={t('prcRopaSecurity')} value={r.securityMeasures.join(' · ') || '—'} colspan={2} />
            </div>

            {hr.isHigh && (
              <div className="prc-risk-banner">
                <div className="prc-risk-banner-label">
                  {t('prcArticle25')}
                </div>
                <div className="prc-risk-banner-chips">
                  {hr.triggers.map((t_, i) => (
                    <Badge key={i} tone="warning">{lang === 'ar' ? t_.ar : t_.en}</Badge>
                  ))}
                </div>
              </div>
            )}
          </div>
        );
      })}
    </Card>
  );
}

function FieldRow({ label, value, colspan }: { label: string; value: string; colspan?: number }) {
  return (
    <div className={colspan === 2 ? 'prc-field--span2' : undefined}>
      <div className="prc-field-label">
        {label}
      </div>
      <div className="prc-field-value">{value}</div>
    </div>
  );
}

// ─── DPIA tab ───────────────────────────────────────────────────────────────

function DpiaTab({ dpias, ropa, onCreate, onTransition }: {
  dpias: DpiaAssessment[]; ropa: RopaActivity[];
  onCreate: (ropaId: string) => void;
  onTransition: (d: DpiaAssessment, status: DpiaStatus) => void;
}) {
  const { t, lang } = useI18n();
  // Find ROPA activities flagged high-risk that have no DPIA yet.
  const candidates = ropa.filter(r => detectHighRisk(r).isHigh && !dpias.some(d => d.ropaId === r.id));
  return (
    <Card title={t('prcDpiaTitle')} Icon={ShieldAlert}
      action={candidates.length > 0
        ? <Button size="sm" variant="primary" onClick={() => onCreate(candidates[0].id)}>{t('prcDpiaCreate')} ({candidates.length})</Button>
        : null}>
      <div className="prc-card-sub">
        {t('prcDpiaSub')}
      </div>
      {dpias.length === 0
        ? <div className="prc-empty">
            {t('prcDpiaEmpty')}
          </div>
        : dpias.map(d => {
            const r = ropa.find(x => x.id === d.ropaId);
            return (
              <div key={d.id} className="prc-item">
                <div className="prc-item-head">
                  <div className="prc-item-main">
                    <div className="prc-item-badges">
                      <span className="mono prc-item-code">{d.id}</span>
                      <Badge tone={d.status === 'APPROVED' ? 'success' : d.status === 'CONSULTATION' ? 'warning' : 'info'}>
                        {d.status}
                      </Badge>
                      {d.isHighRisk && <Badge tone="warning"><AlertTriangle size={11} /> {t('prcHighRisk')}</Badge>}
                      {d.needsSdaiaConsultation && <Badge tone="danger">SDAIA consultation</Badge>}
                    </div>
                    <div className="prc-item-title--sm">
                      {r ? (lang === 'ar' ? r.nameAr : r.nameEn) : d.ropaId}
                    </div>
                    {d.dpoSignedBy && (
                      <div className="prc-dpo-sign">
                        {t('prcDpoSign')}: {d.dpoSignedBy}
                      </div>
                    )}
                  </div>
                  <div className="prc-item-stats">
                    <Stat label={t('prcDpiaInherent')} value={String(d.riskScore)} tone={d.riskScore > 12 ? 'danger' : 'warning'} />
                    <Stat label={t('prcDpiaResidual')} value={String(d.residualScore)} tone={d.residualScore > 6 ? 'warning' : 'success'} />
                  </div>
                </div>
                {/* Transition buttons */}
                <div className="prc-dpia-trans-row">
                  {d.status === 'DRAFT' && <Button size="sm" variant="primary" onClick={() => onTransition(d, 'RISK_ASSESSMENT')}>{t('prcDpiaToRiskAssess')}</Button>}
                  {d.status === 'RISK_ASSESSMENT' && d.needsSdaiaConsultation && <Button size="sm" variant="primary" onClick={() => onTransition(d, 'CONSULTATION')}>{t('prcDpiaToSdaiaConsult')}</Button>}
                  {(d.status === 'RISK_ASSESSMENT' || d.status === 'CONSULTATION') && <Button size="sm" variant="primary" onClick={() => onTransition(d, 'APPROVED')}>{t('prcDpiaDpoApprove')}</Button>}
                  {d.status === 'APPROVED' && <Button size="sm" variant="ghost" onClick={() => onTransition(d, 'REJECTED')}>{t('prcDpiaReject')}</Button>}
                </div>
                <div className="prc-section-mb">
                  <div className="prc-section-label">
                    {t('prcDpiaTriggers')}
                  </div>
                  <div className="prc-section-chips">
                    {(lang === 'ar' ? d.triggersAr : d.triggersEn).map((trg, i) => (
                      <Badge key={i} tone="warning">{trg}</Badge>
                    ))}
                  </div>
                </div>
                <div className="prc-mitigations">
                  <div className="prc-section-label">
                    {t('prcDpiaMitigations')}
                  </div>
                  {lang === 'ar' ? d.mitigationsAr : d.mitigationsEn}
                </div>
              </div>
            );
          })
      }
    </Card>
  );
}

// ─── Cross-border tab ───────────────────────────────────────────────────────

function CbtTab({ cbts, ropa, onTransition, onUpgradeSafeguard }: {
  cbts: CrossBorderTransfer[]; ropa: RopaActivity[];
  onTransition: (c: CrossBorderTransfer, status: CbtStatus) => void;
  onUpgradeSafeguard: (c: CrossBorderTransfer, sg: SafeguardMechanism) => void;
}) {
  const { t, lang } = useI18n();
  return (
    <>
      <Card title={t('prcCbtTitle')} Icon={Globe}>
        <div className="prc-card-sub">
          {t('prcCbtSub')}
        </div>
        {cbts.map(c => {
          const r = ropa.find(x => x.id === c.ropaId);
          return (
            <div key={c.id} className="prc-item">
              <div className="prc-item-head">
                <div className="prc-item-main">
                  <div className="prc-item-badges">
                    <span className="mono prc-item-code">{c.id}</span>
                    <Badge tone={ADEQUACY_TONE[c.adequacy]}>{ADEQUACY_LABEL[c.adequacy][lang]}</Badge>
                    <Badge tone="info">{SAFEGUARD_LABEL[c.safeguard][lang]}</Badge>
                    <Badge tone="primary">{c.destinationCountry}</Badge>
                  </div>
                  <div className="prc-item-title--sm">
                    {r ? (lang === 'ar' ? r.nameAr : r.nameEn) : c.ropaId}
                  </div>
                  <div className="prc-cbt-purpose">
                    {lang === 'ar' ? c.purposeAr : c.purposeEn}
                  </div>
                  {c.nextReviewAt && (
                    <div className="prc-cbt-review">
                      {t('prcCbtNextReview')}: <span className="mono">{c.nextReviewAt}</span>
                      {' · '} {t('prcCbtInterval')}: {c.reviewIntervalMonths}m
                    </div>
                  )}
                </div>
                <div className="prc-item-stats">
                  <Stat label={t('prcCbtPreRisk')} value={String(c.preRiskScore)} tone="warning" />
                  <Stat label={t('prcCbtPostRisk')} value={String(c.postRiskScore)} tone={c.postRiskScore <= 6 ? 'success' : 'warning'} />
                </div>
              </div>
              {/* Action row */}
              <div className="prc-cbt-actions">
                {c.status === 'DRAFT' && <Button size="sm" variant="primary" onClick={() => onTransition(c, 'ASSESSING')}>{t('prcCbtStartAssess')}</Button>}
                {c.status === 'ASSESSING' && c.adequacy !== 'PROHIBITED' && <Button size="sm" variant="primary" onClick={() => onTransition(c, 'APPROVED')}>{t('prcCbtApprove')}</Button>}
                {c.status === 'ASSESSING' && c.adequacy === 'PROHIBITED' && <Button size="sm" variant="danger" onClick={() => onTransition(c, 'BLOCKED')}>{t('prcCbtBlock')}</Button>}
                {c.status === 'APPROVED' && <Button size="sm" variant="secondary" onClick={() => onTransition(c, 'PERIODIC_REVIEW')}>{t('prcCbtTrigger Review')}</Button>}
                {c.adequacy === 'NOT_ADEQUATE' && c.safeguard !== 'BINDING_CORPORATE_RULES' && (
                  <select
                    onChange={e => { if (e.target.value) onUpgradeSafeguard(c, e.target.value as SafeguardMechanism); }}
                    defaultValue=""
                    className="prc-cbt-select">
                    <option value="">{t('prcCbtUpgradeSelect')}</option>
                    {(['STANDARD_CLAUSES','BINDING_CORPORATE_RULES','CERTIFICATION','EXPLICIT_CONSENT'] as SafeguardMechanism[])
                      .filter(s => s !== c.safeguard)
                      .map(s => <option key={s} value={s}>{SAFEGUARD_LABEL[s][lang]}</option>)}
                  </select>
                )}
                <Badge tone={c.status === 'APPROVED' ? 'success' : c.status === 'BLOCKED' ? 'danger' : c.status === 'PERIODIC_REVIEW' ? 'warning' : 'info'}>
                  {c.status}
                </Badge>
              </div>
            </div>
          );
        })}
      </Card>

      <div className="prc-section-mt">
        <Card title={t('prcAdequacyTitle')} Icon={Globe}>
          <div className="prc-card-sub">
            {t('prcAdequacySub')}
          </div>
          <div className="prc-adequacy-grid">
            {(['ADEQUATE', 'CONDITIONAL', 'NOT_ADEQUATE', 'PROHIBITED'] as const).map(level => (
              <div key={level} className="prc-adequacy-card">
                <div className="prc-adequacy-card-head">
                  <Badge tone={ADEQUACY_TONE[level]}>{ADEQUACY_LABEL[level][lang]}</Badge>
                </div>
                <ul className="prc-adequacy-list">
                  {Object.entries(ADEQUACY_BY_COUNTRY)
                    .filter(([, l]) => l === level)
                    .map(([country]) => <li key={country}>{country}</li>)}
                </ul>
              </div>
            ))}
          </div>
        </Card>
      </div>
    </>
  );
}

// ─── DSR tab ────────────────────────────────────────────────────────────────

function DsrTab({ dsrs, now, busyId, onCreate, onTransition, onExtend }: {
  /** `null` while the register has not been read — never conflated with an empty register. */
  dsrs: DsrCase[] | null;
  now: Date;
  busyId: string | null;
  onCreate: (type: DsrType, name: string, idLast4: string, email: string) => void;
  onTransition: (d: DsrCase, status: DsrStatus) => void;
  onExtend: (d: DsrCase) => void;
}) {
  const { t, lang } = useI18n();
  const [showForm, setShowForm] = useState(false);
  const [newType, setNewType] = useState<DsrType>('ACCESS');
  const [newName, setNewName] = useState('');
  const [newId, setNewId] = useState('');
  const [newEmail, setNewEmail] = useState('');
  function submit() {
    if (!newName || !newId || !newEmail) return;
    onCreate(newType, newName, newId, newEmail);
    setNewName(''); setNewId(''); setNewEmail(''); setShowForm(false);
  }
  return (
    <Card title={t('prcDsrTitle')} Icon={UserCheck}
      action={<Button size="sm" variant="secondary" onClick={() => setShowForm(s => !s)}>{showForm ? t('prcDsrCancelNew') : t('prcDsrNewBtn')}</Button>}>
      <div className="prc-card-sub">
        {t('prcDsrSub')}
      </div>
      {showForm && (
        <div className="prc-dsr-form">
          <select value={newType} onChange={e => setNewType(e.target.value as DsrType)} className="prc-dsr-input">
            {(['ACCESS', 'RECTIFICATION', 'ERASURE', 'OBJECTION', 'RESTRICTION', 'PORTABILITY', 'WITHDRAW_CONSENT'] as DsrType[]).map(tp =>
              <option key={tp} value={tp}>{DSR_TYPE_LABEL[tp][lang]}</option>
            )}
          </select>
          <input value={newName} onChange={e => setNewName(e.target.value)} placeholder={t('prcDsrFormName')} className="prc-dsr-input" />
          <input value={newId} onChange={e => setNewId(e.target.value)} placeholder={t('prcDsrFormId')} maxLength={4} className="mono prc-dsr-input" />
          <input value={newEmail} onChange={e => setNewEmail(e.target.value)} placeholder={t('prcDsrFormEmail')} type="email" className="prc-dsr-input" />
          <Button size="sm" variant="primary" onClick={submit}>{t('prcDsrFormSubmit')}</Button>
        </div>
      )}
      {/*
        Three distinct states, never collapsed: not read yet, read and empty, read with rows.
        A "no requests yet" line over an unread register would be the same class of claim as
        F2-197's green tile — an all-clear nothing measured.
      */}
      {dsrs === null && (
        <div role="status" className="prc-card-sub">{t('prcDsrUnread')}</div>
      )}
      {dsrs !== null && dsrs.length === 0 && (
        <div className="prc-card-sub">{t('prcDsrEmpty')}</div>
      )}
      {(dsrs ?? []).map(d => {
        const days = daysToDeadline(d, now);
        const late = isPastDeadline(d, now);
        // Elapsed share of the window from filing to deadline, for the bar only.
        const filed = new Date(d.filedAt).getTime();
        const due = new Date(d.slaDeadline).getTime();
        const pctElapsed = Number.isNaN(filed) || Number.isNaN(due) || due <= filed
          ? 0
          : Math.min(100, Math.max(0, ((now.getTime() - filed) / (due - filed)) * 100));
        const nextStates = DSR_ALLOWED[d.status] ?? [];
        const busy = busyId === d.id;
        return (
          <div key={d.id} className="prc-dsr-card">
            <div className="prc-item-head">
              <div className="prc-item-main">
                <div className="prc-item-badges">
                  <span className="mono prc-item-code">{d.id}</span>
                  <Badge tone={DSR_STATUS_TONE[d.status]}>{DSR_STATUS_LABEL[d.status][lang]}</Badge>
                  <Badge tone="info">{DSR_TYPE_LABEL[d.type][lang]}</Badge>
                  {d.verificationMethod && <Badge tone="primary">{d.verificationMethod}</Badge>}
                  {d.extended && <Badge tone="warning">{t('prcDsrExtendedBadge')}</Badge>}
                </div>
                <div className="prc-item-title--sm">
                  {d.subjectName} · National ID …{d.nationalIdLast4}
                </div>
                <div className="mono prc-dsr-subject-email">
                  {d.contactEmail}
                </div>
                {d.dataSummaryEn && (
                  <div className="prc-dsr-summary">
                    {lang === 'ar' ? d.dataSummaryAr : d.dataSummaryEn}
                  </div>
                )}
                {d.extensionReasonEn && (
                  <div className="prc-dsr-extension">
                    {lang === 'ar' ? d.extensionReasonAr : d.extensionReasonEn}
                  </div>
                )}
                {/*
                  The lawful basis for refusing the request. PDPL Article 4 requires a refusal
                  to be reasoned; F2-202 put these columns on the wire and gave the transition
                  endpoint a field to write them, so a REJECTED case can now say why.
                */}
                {(d.rejectionReasonEn || d.rejectionReasonAr) && (
                  <div className="prc-dsr-extension">
                    {(lang === 'ar' ? d.rejectionReasonAr : d.rejectionReasonEn) ?? ''}
                  </div>
                )}
              </div>
              <div className="prc-dsr-sla-col">
                {/* SLA bar — the deadline is the server's `sla_deadline` column, never computed here. */}
                <div className="prc-dsr-sla-wrap">
                  <div className={`prc-dsr-sla-time${late ? ' prc-dsr-sla-time--late' : ''}`}>
                    {late
                      ? `${t('prcDsrOverdue')}${days == null ? '' : ` (${Math.abs(days)}d)`}`
                      : days == null ? '—' : `${days}d ${t('prcDsrRemaining')}`}
                  </div>
                  <div className="prc-dsr-sla-bar">
                    <div className="prc-dsr-sla-fill"
                      data-tone={late ? 'danger' : pctElapsed > 80 ? 'warning' : 'success'}
                      style={{ width: `${pctElapsed}%` }} />
                  </div>
                  <div className="prc-dsr-sla-foot">
                    {t('prcDsrDeadline')}: <span className="mono">{d.slaDeadline}</span>
                  </div>
                  {/*
                    Past the deadline with nothing recorded. F2-196 found the sweep skipping
                    EXTENDED cases, so a case could sit weeks past its statutory deadline with
                    no breach row, no event and no audit trail. That state is now visible on
                    the case rather than only in the database — measured live in prod.
                  */}
                  {late && !d.slaBreachedAt && (
                    <div className="prc-dsr-sla-foot" role="status">
                      <Badge tone="warning">{t('prcDsrBreachUnrecorded')}</Badge>
                    </div>
                  )}
                  {d.slaBreachedAt && (
                    <div className="prc-dsr-sla-foot">
                      {t('prcDsrBreachRecorded')}: <span className="mono">{d.slaBreachedAt.slice(0, 10)}</span>
                    </div>
                  )}
                </div>
                {/*
                  Action buttons come from `DSR_ALLOWED[d.status]`, the mirror of the server's
                  own machine — so the UI cannot offer a step the server answers 409 to. The
                  old block hard-coded four `status === '…'` tests plus a three-way `!==` chain
                  for Extend, which is a second lifecycle written in JSX.
                */}
                <div className="prc-dsr-actions">
                  {nextStates.includes('VERIFYING_IDENTITY') && <Button size="sm" variant="primary" disabled={busy} onClick={() => onTransition(d, 'VERIFYING_IDENTITY')}>{t('prcDsrVerify')}</Button>}
                  {nextStates.includes('COLLECTING') && <Button size="sm" variant="primary" disabled={busy} onClick={() => onTransition(d, 'COLLECTING')}>{t('prcDsrCollect')}</Button>}
                  {nextStates.includes('REDACTING') && <Button size="sm" variant="primary" disabled={busy} onClick={() => onTransition(d, 'REDACTING')}>{t('prcDsrRedact')}</Button>}
                  {nextStates.includes('DELIVERED') && <Button size="sm" variant="primary" Icon={CheckCircle2} disabled={busy} onClick={() => onTransition(d, 'DELIVERED')}>{t('prcDsrDeliver')}</Button>}
                  {nextStates.includes('EXTENDED') && <Button size="sm" variant="ghost" disabled={busy} onClick={() => onExtend(d)}>{t('prcDsrExtend')}</Button>}
                  {nextStates.includes('REJECTED') && <Button size="sm" variant="ghost" disabled={busy} onClick={() => onTransition(d, 'REJECTED')}>{t('prcDsrReject')}</Button>}
                </div>
              </div>
            </div>
            {/* Workflow stages */}
            <div className="prc-dsr-stages">
              {[
                { code: 'INTAKE',             label: t('prcDsrSt1'), at: d.filedAt },
                { code: 'VERIFYING_IDENTITY', label: t('prcDsrSt2'), at: d.identityVerifiedAt },
                { code: 'COLLECTING',          label: t('prcDsrSt3'), at: d.dataCollectedAt },
                { code: 'REDACTING',           label: t('prcDsrSt4'), at: d.redactionCompletedAt },
                { code: 'DELIVERED',           label: t('prcDsrSt5'), at: d.deliveredAt },
              ].map((s, i, arr) => {
                const done = !!s.at;
                return (
                  <div key={s.code} className="prc-dsr-stage">
                    <div className={`prc-dsr-stage-dot${done ? ' prc-dsr-stage-dot--done' : ''}`}>
                      {done ? <CheckCircle2 size={10} /> : i + 1}
                    </div>
                    <div className="prc-dsr-stage-text">
                      <div className="prc-dsr-stage-label">{s.label}</div>
                      <div className="mono prc-dsr-stage-date">
                        {s.at ? s.at.slice(0, 10) : '—'}
                      </div>
                    </div>
                    {i < arr.length - 1 && (
                      <div className="prc-dsr-stage-sep" />
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        );
      })}
    </Card>
  );
}

// ─── Helpers ────────────────────────────────────────────────────────────────

function Stat({ label, value, tone }: { label: string; value: string; tone: 'primary' | 'warning' | 'danger' | 'success' }) {
  return (
    <div className="prc-stat" data-tone={tone === 'primary' ? undefined : tone}>
      <div className="prc-stat-label">{label}</div>
      <div className="mono prc-stat-value">{value}</div>
    </div>
  );
}

// ─── DSR pipeline (interactive kanban) ─────────────────────────────────────

function DsrPipeline({ dsrs, now, onAdvance, onCardClick }: {
  dsrs: DsrCase[];
  now: Date;
  onAdvance: (id: string, fromStatus: string, toStatus: string | null) => void;
  onCardClick: (id: string) => void;
}) {
  const stages: PipelineStage[] = [
    { code: 'INTAKE',              labelEn: 'Intake',              labelAr: 'استلام',                tone: 'neutral', Icon: FileText },
    { code: 'VERIFYING_IDENTITY',  labelEn: 'Verifying identity',   labelAr: 'التحقق من الهوية',      tone: 'info',    Icon: UserCheck },
    { code: 'COLLECTING',          labelEn: 'Collecting data',      labelAr: 'جمع البيانات',           tone: 'info',    Icon: Files },
    { code: 'REDACTING',           labelEn: 'Redacting',             labelAr: 'إخفاء البيانات',         tone: 'warning', Icon: ShieldAlert },
    { code: 'DELIVERED',           labelEn: 'Delivered',            labelAr: 'مُسلَّم',                 tone: 'success', Icon: CheckCircle2 },
    { code: 'EXTENDED',            labelEn: 'Extended (60d)',        labelAr: 'مُمدَّد (60 يوم)',         tone: 'warning', Icon: Clock },
    { code: 'REJECTED',            labelEn: 'Rejected',              labelAr: 'مرفوض',                  tone: 'danger',  Icon: AlertTriangle },
  ];
  const cards: PipelineCard[] = dsrs.map(d => {
    const due = dueLabel(d.slaDeadline, () => '');
    return {
      id: d.id, status: d.status, refCode: d.id,
      titleEn: `${d.subjectName} · ${d.type}`,
      titleAr: `${d.subjectName} · ${d.type}`,
      subtitleEn: `NID …${d.nationalIdLast4} · ${d.contactEmail}`,
      subtitleAr: `هوية …${d.nationalIdLast4} · ${d.contactEmail}`,
      badges: [
        { label: d.verificationMethod ?? '—', tone: 'primary' as const },
        ...(d.extended ? [{ label: 'EXT', tone: 'warning' as const }] : []),
      ],
      // F2-202: `isPastDeadline` already tests openness as the COMPLEMENT of the derived
      // terminal set. The old expression was `due?.urgent && status !== 'DELIVERED' &&
      // status !== 'REJECTED'` — a hand-written closed-status list, the exact shape F2-196
      // found in the SLA sweep, where a forgotten token meant a breach was never escalated.
      urgent: isPastDeadline(d, now) || (due?.urgent === true && isOpenDsr(d.status)),
      dueLabel: due?.label,
    };
  });
  return (
    <LifecyclePipeline
      stages={stages}
      cards={cards}
      introEn="Live data subject request pipeline. PDPL Article 4 30-day SLA tracked per case; complex requests can be EXTENDED to 60 days. Click a card to drill into the DSR detail."
      introAr="مسار طلبات أصحاب البيانات الحيّ. التزام 30 يوماً من المادة 4 لكل حالة؛ الطلبات المعقّدة يمكن تمديدها إلى 60 يوماً. انقر على بطاقة لفتح تفاصيل الطلب."
      onAdvance={onAdvance}
      onCardClick={onCardClick}
      emptyEn="No data subject requests yet."
      emptyAr="لا طلبات أصحاب بيانات بعد."
    />
  );
}
