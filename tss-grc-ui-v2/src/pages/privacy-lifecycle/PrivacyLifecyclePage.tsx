import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import {
  Files, ShieldAlert, Globe, UserCheck, Clock,
  AlertTriangle, CheckCircle2, FileText, Activity, ArrowRight,
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
  dsrApi, aggregateDsr, daysToDeadline, isPastDeadline, mayTransition,
  isOpen as isOpenDsr, DSR_ALLOWED,
  DSR_TYPE_LABEL, DSR_STATUS_LABEL, DSR_STATUS_TONE,
  type DsrCase, type DsrStatus, type DsrType,
} from '../../lib/privacyLcApi';
import { ApiError } from '../../lib/api';
import { useRealtimeOrgEvent } from '../../lib/useRealtimeEntity';
import '../../styles/pages/privacy-lifecycle.css';

/*
 * ⚠⚠ F2-207 — THIS PAGE HAD FIVE TABS AND THREE OF THEM WERE STATUTORY REGISTERS KEPT IN
 * `localStorage`. They are retired, not rewired, and the distinction is the whole finding.
 *
 * `lib/privacyLifecycle.ts` is DELETED. It held `SEED_ROPA` (PDPL Art. 31 processing
 * activities), `SEED_DPIA` (Art. 25 impact assessments) and `SEED_CBT` (Art. 29 cross-border
 * transfers) under `grc.privacy.ropa.v1` / `.dpia.v1` / `.cbt.v1`, and this page rendered and
 * MUTATED them: approving a ROPA, signing off a DPIA and approving a transfer out of the
 * Kingdom all wrote to the operator's own browser.
 *
 * All three real registers are populated AND already have a complete server-backed surface,
 * measured 2026-08-13 in dev:
 *
 *   Art. 31 ROPA  → /privacy?tab=ropa          `privacy.processing_activities`      11 rows
 *   Art. 25 DPIA  → /privacy?tab=dpia          `privacy.dpia`                        6 rows
 *   Art. 29 CBT   → /data-lifecycle?tab=cbt   `privacy.cross_border_transfers`      9 rows
 *
 * So building a client here would have made a SECOND real surface for each — F2-196's own
 * defect — and ADR-017 §D-5 already ratified "retire the fabricated page" for this shape in
 * F2-42, F2-198 and F2-199.
 *
 * ⚠ Wiring the old handlers was not an option even in principle. `CbtStatus` declared
 * DRAFT/ASSESSING/APPROVED/BLOCKED/PERIODIC_REVIEW and `CK_cbt_status` admits only
 * REQUESTED/ASSESSED/APPROVED/REJECTED/EXPIRED, so 4 of the 5 tokens are refused by a CHECK
 * constraint; `SafeguardMechanism` was refused 4 of 5 the same way, three of them being the
 * same concept differently spelled (STANDARD_CLAUSES/SCC, BINDING_CORPORATE_RULES/BCR,
 * NONE_NEEDED/ADEQUACY). A naive rewire converts today's silent local writes into constraint
 * violations — F2-183's rule.
 *
 * ⚠⚠ And the retired handlers passed INVENTED OFFICERS as the author of statutory decisions:
 * `'Layla Al-Ghamdi (DPO)'` as the Data Protection Officer signing an Article 25 DPIA and
 * approving an Article 29 transfer, and the literal `'DPO + CRO'` as the ROPA approver. Those
 * names must never reach a real endpoint; `privacyRegisterRetirement.test.ts` refuses them.
 *
 * ⚠ THE FOUR RETIRED KPI TILES. `prcKpiRopa` / `prcKpiDpiaHighRisk` / `prcKpiCbt` /
 * `prcKpiCbtBlocked` counted over the three fabricated corpora — F2-198's shape, a CORRECT
 * predicate over an INVENTED corpus, which is why F2-195's `kpiProvenance` gate passed them
 * (the value came from a function, not a literal). They are deleted rather than rewired
 * because each one's real equivalent was checked to exist on the surface this page now points
 * at: `/privacy?tab=ropa` renders `privacyKpiActivities` (the register count) and
 * `privacyKpiCrossBorder` (5 of 11) from `privacyApi.ropa()`; `/data-lifecycle?tab=cbt`
 * renders four real status tiles over `listCrossBorderTransfers()`; and the DPIA tab shows
 * `overallResidualRisk` per row. So no real signal is dropped by removing them — F2-198's
 * other rule — and no fourth place computes a PDPL figure.
 *
 * ⚠ Three of them also LINKED somewhere other than the register they counted
 * (`/data-officer/dpia` is a different table in a different module, `/privacy/data-flow` is
 * not the transfer register at all), which is F2-197's "a tile whose figure disagrees with
 * the list it navigates to is worse than a tile with no figure."
 */

type PrcTab = 'pipeline' | 'dsr';

/**
 * The tabs this page can actually open.
 *
 * ⚠⚠ F2-207 also fixed a defect that had nothing to do with the fabrication: the three DSR
 * KPI tiles below link `to="?tab=dsr"`, and this page held its tab in `useState` and never
 * read the query string — so every one of those tiles navigated, re-mounted the page on the
 * PIPELINE tab, and looked like it had done nothing. A census across the app resolved all 22
 * `?tab=` links to their target page: 5 landed on a page that ignores `?tab`, and the other
 * two sites are fixed in this commit as well. `tabLinkTargets.test.ts` holds it at zero.
 */
const PRC_TABS: readonly PrcTab[] = ['pipeline', 'dsr'] as const;

export function PrivacyLifecyclePage() {
  const { t, lang } = useI18n();
  const toast = useToast();

  // Derived from the URL, not synchronised with it — the idiom `PrivacyPage` already uses.
  // Two sources of truth for one tab is how a tile link and the tab row come to disagree.
  const [searchParams, setSearchParams] = useSearchParams();
  const urlTab = searchParams.get('tab') as PrcTab | null;
  const tab: PrcTab = (urlTab && PRC_TABS.includes(urlTab)) ? urlTab : 'pipeline';
  const selectTab = useCallback((next: PrcTab) => {
    const updated = new URLSearchParams(searchParams);
    if (next === 'pipeline') updated.delete('tab');
    else updated.set('tab', next);
    setSearchParams(updated, { replace: true });
  }, [searchParams, setSearchParams]);

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
  // refetches the register the event is about. (F2-205 found the same shape live in the cyber
  // workspace: a real broadcast whose handler refreshed a browser constant.)
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
        {/*
          F2-202 — these count over the rows fetched from /api/privacy-lc/dsr-cases, and each
          links to the DSR tab on THIS page rather than to /data-officer/dsr. That route serves
          `data_officer.dsr_request`, a different table with a different status vocabulary
          (F2-196 measured three server-side DSR registers), so a figure counted here and a
          list opened there could never be expected to agree.

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
          {/* `prc-sub` was never a declared class — the load error rendered unstyled. */}
          <div role="status" className="prc-card-sub">{dsrError}</div>
        </Card>
      )}

      <PdplRegisterLinks />

      <div className="prc-tab-row">
        {([
          { id: 'pipeline', labelKey: 'lpcTabPipeline', Icon: Activity },
          { id: 'dsr',  labelKey: 'prcTabDsr',  Icon: UserCheck },
        ] as { id: PrcTab; labelKey: string; Icon: typeof Files }[]).map(tabDef => (
          <button key={tabDef.id} onClick={() => selectTab(tabDef.id)}
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
        onCardClick={() => selectTab('dsr')} />}

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

// ─── Where the other three PDPL registers live ──────────────────────────────

/**
 * F2-207. The ROPA, DPIA and cross-border tabs used to sit on this page over browser-local
 * corpora. Retiring a surface must still leave a reader somewhere true — F2-137's rule for
 * routes, applied here to a tab — so this names each real register and links to it.
 *
 * ⚠ Deliberately carries NO counts. This page does not fetch those three registers, and a
 * number here would either have to be invented or make this a fourth surface for a statutory
 * object. The linked pages each show their own real figures.
 */
function PdplRegisterLinks() {
  const { t } = useI18n();
  const targets = [
    { to: '/privacy?tab=ropa',        Icon: Files,       label: t('prcOtherRopa'), sub: t('prcOtherRopaSub') },
    { to: '/privacy?tab=dpia',        Icon: ShieldAlert, label: t('prcOtherDpia'), sub: t('prcOtherDpiaSub') },
    { to: '/data-lifecycle?tab=cbt', Icon: Globe,       label: t('prcOtherCbt'),  sub: t('prcOtherCbtSub') },
  ];
  return (
    <div className="prc-section-mb">
      <Card title={t('prcOtherTitle')} Icon={FileText}>
        <div className="prc-card-sub">{t('prcOtherSub')}</div>
        <div className="prc-other-grid">
          {targets.map(x => (
            <Link key={x.to} to={x.to} className="prc-other-link">
              <x.Icon size={14} />
              <div className="prc-other-text">
                <div className="prc-other-label">{x.label}</div>
                <div className="prc-other-sub">{x.sub}</div>
              </div>
              <ArrowRight size={13} className="prc-other-arrow" />
            </Link>
          ))}
        </div>
      </Card>
    </div>
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
