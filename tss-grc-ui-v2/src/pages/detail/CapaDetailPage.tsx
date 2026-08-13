import { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  ListChecks, ArrowLeft, AlertTriangle, Send, Calendar, Briefcase, FileText,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { DetailHeader } from '../../components/layout/DetailHeader';
import { DetailLayout } from '../../components/layout/DetailLayout';
import { RaiseRequestButton } from '../../components/workflow/RaiseRequestButton';
import { RailField, RailFieldGroup } from '../../components/layout/DetailRail';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { EmptyState } from '../../components/ui/EmptyState';
import { EntityActivityTimeline } from '../../components/audit/EntityActivityTimeline';
import { EntityAutomationRuns } from '../../components/audit/EntityAutomationRuns';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { getErrorDisplayMessage } from '../../lib/apiError';
import {
  useCapas, CAPA_STATUS_LABEL, CAPA_STATUS_TONE, CAPA_SEVERITY_TONE,
  CAPA_ALLOWED, CAPA_SOURCE_LABEL, daysUntilDue, isCapaOpen,
  type CapaStatus,
} from '../../lib/capa';

type TabId = 'overview' | 'lifecycle' | 'history';

/**
 * CAPA detail — `/capa/:id`.
 *
 * ⚠⚠ F2-208. Two things changed here and both were governance defects.
 *
 * 1. **Transitions go to the server.** "Advance" called
 *    `advance(c.id, to, 'demo.user@tss.sa')`, which wrote a localStorage patch
 *    attributed to a literal address. `POST /api/capa/tasks/{id}/transition` now
 *    does it, and the row is replaced from the response, so the CLOSED step is
 *    stamped with the authenticated principal as verifier — which is what
 *    `capa.tasks.verified_by` is for and what an ISO 9001 §10.2 / IIA 2500 trail
 *    needs to mean anything.
 *
 * 2. **The local timeline is gone.** This page rendered `c.history`, an array the
 *    browser appended to on every local advance, in a card titled "timeline" —
 *    beside the real `EntityActivityTimeline` over `platform.audit_log`, which was
 *    hidden behind `c.backendId != null` and therefore invisible for exactly the
 *    twenty invented rows the fabricated history described. The real cards are now
 *    unconditional; every row has a `backendId` because every row is a server row.
 *
 * The RCA tab is deleted with the rest of the root-cause surface: no column exists
 * on `capa.tasks` for a method, a step list, a root cause or a category, so the tab
 * could only ever have shown what the browser had put there.
 */
export function CapaDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { t, lang } = useI18n();
  const toast = useToast();
  const { items, loading, error, transition } = useCapas();
  const [tab, setTab] = useState<TabId>('overview');
  const [busy, setBusy] = useState(false);

  const c = items.find(x => x.id === id);

  if (!c) {
    return (
      <Shell title={t('modCapa')}>
        <div style={{ padding: 32, textAlign: 'center' }}>
          <EmptyState
            Icon={ListChecks}
            title={loading ? t('capaLoading') : t('capaDetailNotFound')}
            sub={error ?? (loading ? '' : t('capaDetailNotFoundBody'))}
          />
          <div style={{ marginTop: 16 }}>
            <Link to="/capa" style={{ color: 'var(--primary)', fontSize: 13 }}>
              ← {t('capaDetailBackToRegister')}
            </Link>
          </div>
        </div>
      </Shell>
    );
  }

  const isAr = lang === 'ar';
  const title = isAr && c.titleAr ? c.titleAr : c.titleEn;
  const desc  = isAr && c.descriptionAr ? c.descriptionAr : c.descriptionEn;
  const dueIn = daysUntilDue(c);
  const open = isCapaOpen(c);
  const overdue = dueIn < 0 && open;
  // Every legal edge is offered, including CANCELLED — the machine decides, not a
  // filter here. The old version dropped CANCELLED and a state that no longer
  // exists, so a CAPA could not be cancelled from its own detail page.
  const allowedNext = CAPA_ALLOWED[c.status];
  const notes = isAr && c.progressNotesAr ? c.progressNotesAr : c.progressNotesEn;

  async function go(to: CapaStatus) {
    if (!c) return;
    setBusy(true);
    try {
      const saved = await transition(c.id, { status: to });
      toast.success(`${saved.id} → ${CAPA_STATUS_LABEL[saved.status][isAr ? 'ar' : 'en']}`);
    } catch (e) {
      toast.danger(getErrorDisplayMessage(e));
    } finally {
      setBusy(false);
    }
  }

  const tabs: TabItem<TabId>[] = [
    { id: 'overview',  label: t('detailTabOverview') },
    { id: 'lifecycle', label: t('capaTabLifecycle') },
    { id: 'history',   label: t('detailTabHistory') },
  ];

  return (
    <Shell title={t('modCapa')} sub={t('capaSub')}>
      <DetailHeader
        breadcrumbs={[
          { label: t('modCapa'), to: '/capa' },
          { label: c.id },
        ]}
        eyebrow={c.id}
        title={title}
        sub={desc}
        badges={<>
          <Badge tone={CAPA_SEVERITY_TONE[c.severity]} dot>{c.severity}</Badge>
          <Badge tone={CAPA_STATUS_TONE[c.status]} dot>
            {CAPA_STATUS_LABEL[c.status][isAr ? 'ar' : 'en']}
          </Badge>
          <Badge tone={c.type === 'PREVENTIVE' ? 'info' : 'primary'}>
            {c.type === 'PREVENTIVE' ? 'PA' : 'CA'}
          </Badge>
          {overdue && <Badge tone="danger" dot>{Math.abs(dueIn)}d {t('capaOverdue')}</Badge>}
        </>}
        actions={<>
          <RaiseRequestButton sourceModule="COMPLIANCE" sourceRecordType="Capa"
            sourceRecordId={c.backendId} />
          <Button variant="ghost" Icon={ArrowLeft} onClick={() => window.history.back()}>
            {t('detailBack')}
          </Button>
          {allowedNext.map(next => (
            <Button key={next}
              variant={next === 'CANCELLED' ? 'ghost' : 'primary'}
              Icon={Send} disabled={busy} onClick={() => void go(next)}>
              {CAPA_STATUS_LABEL[next][isAr ? 'ar' : 'en']}
            </Button>
          ))}
        </>}
      />

      <DetailLayout
        rail={<>
          <Card title={t('detailAtAGlance')} Icon={AlertTriangle}>
            <RailFieldGroup>
              <RailField
                label={t('fldStatus')}
                accent
                value={<Badge tone={CAPA_STATUS_TONE[c.status]} dot>
                  {CAPA_STATUS_LABEL[c.status][isAr ? 'ar' : 'en']}
                </Badge>}
              />
              <RailField
                label={t('colSeverity')}
                value={<Badge tone={CAPA_SEVERITY_TONE[c.severity]} dot>{c.severity}</Badge>}
              />
              <RailField label={t('colOwner')} value={c.owner} />
              <RailField label={t('capaSourceLabel')}
                value={CAPA_SOURCE_LABEL[c.source][isAr ? 'ar' : 'en']} />
              <RailField label={t('colDue')} value={c.dueDate} mono />
              {/* Verification attribution: the server's, or nothing. */}
              <RailField label={t('capaVerifiedBy')} value={c.verifiedBy ?? '—'} />
            </RailFieldGroup>
          </Card>

          <Card title={t('capaSourceLinkTitle')} Icon={Briefcase}>
            <div style={{ padding: 8 }}>
              <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>{c.originType}</div>
              <div style={{ fontSize: 13, color: 'var(--ink-1)', marginTop: 4, fontWeight: 600 }}>
                {c.originId}
              </div>
            </div>
          </Card>

          {c.evidenceRefs && (
            <Card title={t('capaDetailEvidenceRefs')} Icon={FileText}>
              <div style={{ padding: 8, fontSize: 12, color: 'var(--ink-1)', whiteSpace: 'pre-wrap' }}>
                {c.evidenceRefs}
              </div>
            </Card>
          )}
        </>}
        main={<>
          <Tabs items={tabs} active={tab} onChange={setTab} />

          {tab === 'overview' && (
            <Card title={t('detailTabOverview')} Icon={AlertTriangle}>
              <Field label={t('colTitle')} value={title} bold />
              <Field label={t('capaDetailDescription')} value={desc ?? '—'} multiline />
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12, marginTop: 12 }}>
                <Field label={t('capaDetailRaisedAt')} value={c.createdAt?.slice(0, 10) ?? '—'} mono />
                <Field label={t('colDue')} value={c.dueDate} mono />
                {c.closedAt && <Field label={t('capaDetailClosedAt')} value={c.closedAt.slice(0, 10)} mono />}
                {c.cancelledAt && <Field label={t('capaDetailCancelledAt')} value={c.cancelledAt.slice(0, 10)} mono />}
              </div>
              {notes && <div style={{ marginTop: 12 }}>
                <Field label={t('capaDetailProgressNotes')} value={notes} multiline />
              </div>}
              {c.cancellationReason && (
                <div style={{ marginTop: 12, padding: 10, background: 'var(--warning-bg)', borderRadius: 'var(--r-sm)', border: '1px solid var(--warning-fg)' }}>
                  <div style={{ fontSize: 11, color: 'var(--warning-fg)', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.06em' }}>
                    {t('capaDetailCancellationReason')}
                  </div>
                  <div style={{ fontSize: 13, color: 'var(--ink-1)', marginTop: 4 }}>
                    {c.cancellationReason}
                  </div>
                </div>
              )}
            </Card>
          )}

          {tab === 'lifecycle' && (
            <Card title={t('capaTabLifecycle')} Icon={Calendar}>
              {/* The four stamps the register actually keeps. The previous version
                  showed owner-test / independent-test / audit-signoff /
                  effectiveness — four gates with no column, so the values could
                  only come from the browser corpus. */}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 12 }}>
                <Field label={t('capaLifecycleRaised')} value={c.createdAt?.slice(0, 16).replace('T', ' ') ?? '—'} mono />
                <Field label={t('capaLifecycleStarted')} value={c.startedAt?.slice(0, 16).replace('T', ' ') ?? '—'} mono />
                <Field label={t('capaLifecycleSubmitted')}
                  value={c.submittedForVerificationAt?.slice(0, 16).replace('T', ' ') ?? '—'} mono />
                <Field label={t('capaLifecycleVerified')}
                  value={c.verifiedAt
                    ? `${c.verifiedAt.slice(0, 16).replace('T', ' ')} · ${c.verifiedBy ?? '—'}`
                    : '—'} />
              </div>
              <div style={{ marginTop: 16, fontSize: 11, color: 'var(--ink-3)', lineHeight: 1.6 }}>
                {t('capaLifecycleFootnote')}
              </div>
            </Card>
          )}

          {tab === 'history' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              {/* The real forensic trail: platform.audit_log rows written by
                  CapaTaskService on every create and transition, plus F8 rule
                  fires. Unconditional now — every row in this register is a
                  server row, so `backendId` is always present. It used to be
                  gated on that field being set, which meant the twenty invented
                  rows showed a fabricated timeline instead and the real one was
                  never seen for them. */}
              {c.backendId != null
                ? <>
                    <EntityActivityTimeline entityType="CapaTask" entityId={c.backendId} />
                    <EntityAutomationRuns   entityType="CapaTask" entityId={c.backendId} />
                  </>
                : <InlineEmpty compact>{t('capaDetailNoBackendId')}</InlineEmpty>}
            </div>
          )}
        </>}
      />
    </Shell>
  );
}

function Field({ label, value, bold, mono, multiline }: {
  label: string; value: string; bold?: boolean; mono?: boolean; multiline?: boolean;
}) {
  return (
    <div style={{ marginBottom: 8 }}>
      <div style={{ fontSize: 11, color: 'var(--ink-3)',
                    textTransform: 'uppercase', letterSpacing: '0.06em' }}>
        {label}
      </div>
      <div style={{ fontSize: 13, color: 'var(--ink-1)',
                    fontWeight: bold ? 600 : 500,
                    fontFamily: mono ? 'var(--font-mono)' : undefined,
                    whiteSpace: multiline ? 'pre-wrap' : 'normal',
                    marginTop: 2 }}>
        {value}
      </div>
    </div>
  );
}
