import { useMemo, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  ListChecks, ArrowLeft, AlertTriangle, Network, Send,
  Activity, Calendar, Briefcase,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { DetailHeader } from '../../components/layout/DetailHeader';
import { DetailLayout } from '../../components/layout/DetailLayout';
import { RailField, RailFieldGroup } from '../../components/layout/DetailRail';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Progress } from '../../components/ui/Progress';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { EmptyState } from '../../components/ui/EmptyState';
import { EntityActivityTimeline } from '../../components/audit/EntityActivityTimeline';
import { EntityAutomationRuns } from '../../components/audit/EntityAutomationRuns';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  useCapas, CAPA_STATUS_LABEL, CAPA_STATUS_TONE, CAPA_SEVERITY_TONE,
  CAPA_FORWARD_TRANSITIONS, daysUntilDue,
  type CapaStatus,
} from '../../lib/capa';
import { usePromoted } from '../../lib/promotedRecords';
import { toCapaFromObservation } from '../../lib/promotedAdapters';

type TabId = 'overview' | 'rca' | 'lifecycle' | 'history';

/**
 * CAPA detail page — read-mostly forensic surface for a single corrective /
 * preventive action. Pairs with the register at /capa and unlocks the
 * `EntityActivityTimeline` + `EntityAutomationRuns` drop-in pattern for
 * the CAPA module: as soon as a CAPA's {@link Capa.backendId} is populated
 * (when the register switches to /api/capa/tasks — next PR), the History
 * tab lights up with real audit_log rows + F8 rule_run entries keyed off
 * `entityType=CapaTask`, `entityId=backendId`.
 *
 * <p>The local {@code Capa.history} array is still rendered as a
 * fallback when there's no backendId — seed/localStorage CAPAs get a
 * timeline of their own without needing a backend round-trip.
 *
 * <p>Route: `/capa/:id`. Linked from CapaPage register rows + the
 * Workflow Submissions cross-link strip.
 */
export function CapaDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { t, lang } = useI18n();
  const toast = useToast();
  const { items: apiItems, advance } = useCapas();
  const promotedCapas = usePromoted('CAPA').map(toCapaFromObservation);
  const items = useMemo(() => [...promotedCapas, ...apiItems], [promotedCapas, apiItems]);
  const [tab, setTab] = useState<TabId>('overview');

  const c = items.find(x => x.id === id);

  if (!c) {
    return (
      <Shell title={t('modCapa')}>
        <div style={{ padding: 32, textAlign: 'center' }}>
          <EmptyState
            Icon={ListChecks}
            title={t('capaDetailNotFound')}
            sub={t('capaDetailNotFoundBody')}
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
  const overdue = dueIn < 0 && c.status !== 'CLOSED' && c.status !== 'CANCELLED';
  const allowedNext = CAPA_FORWARD_TRANSITIONS[c.status]
    .filter(s => s !== 'CANCELLED' && s !== 'VERIFICATION_FAILED');

  function quickAdvance(to: CapaStatus) {
    if (!c) return;
    advance(c.id, to, 'demo.user@tss.sa');
    toast.success(`${c.code} → ${CAPA_STATUS_LABEL[to][isAr ? 'ar' : 'en']}`);
  }

  const tabs: TabItem<TabId>[] = [
    { id: 'overview',  label: t('detailTabOverview') },
    { id: 'rca',       label: t('capaTabRcaShort') },
    { id: 'lifecycle', label: t('capaTabLifecycle') },
    { id: 'history',   label: t('detailTabHistory') },
  ];

  return (
    <Shell title={t('modCapa')} sub={t('capaSub')}>
      <DetailHeader
        breadcrumbs={[
          { label: t('modCapa'), to: '/capa' },
          { label: c.code },
        ]}
        eyebrow={c.code}
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
          <Button variant="ghost" Icon={ArrowLeft} onClick={() => window.history.back()}>
            {t('detailBack')}
          </Button>
          {allowedNext.length > 0 && c.status !== 'CLOSED' && (
            <Button variant="primary" Icon={Send} onClick={() => quickAdvance(allowedNext[0])}>
              {t('capaAdvance')}: {CAPA_STATUS_LABEL[allowedNext[0]][isAr ? 'ar' : 'en']}
            </Button>
          )}
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
              <RailField label={t('capaSourceLabel')} value={c.source.replace(/_/g, ' ')} />
              <RailField label={t('colDue')} value={c.dueDate} mono />
              <RailField
                label={t('capaProgressLabel')}
                value={<Progress label="" value={c.progressPct} />}
              />
            </RailFieldGroup>
          </Card>

          {c.sourceRecordTitle && (
            <Card title={t('capaSourceLinkTitle')} Icon={Briefcase}>
              <div style={{ padding: 8 }}>
                <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                  {c.sourceRecordKey ?? '—'}
                </div>
                <div style={{ fontSize: 13, color: 'var(--ink-1)', marginTop: 4, fontWeight: 600 }}>
                  {c.sourceRecordTitle}
                </div>
              </div>
            </Card>
          )}

          {(c.frameworkRefs ?? []).length > 0 && (
            <Card title={t('capaDetailFrameworkRefs')} Icon={Network}>
              <div style={{ padding: 8, display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                {(c.frameworkRefs ?? []).map(ref =>
                  <Badge key={ref} tone="neutral">{ref}</Badge>)}
              </div>
            </Card>
          )}
        </>}
        main={<>
          <Tabs items={tabs} active={tab} onChange={setTab} />

          {tab === 'overview' && (
            <Card title={t('detailTabOverview')} Icon={AlertTriangle}>
              <Field label={t('colTitle')} value={title} bold />
              <Field label={t('capaDetailDescription')} value={desc} multiline />
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12, marginTop: 12 }}>
                <Field label={t('capaDetailOpenedAt')}
                       value={c.openedAt} mono />
                <Field label={t('colDue')}
                       value={c.dueDate} mono />
                {c.closedAt && <Field label={t('capaDetailClosedAt')} value={c.closedAt} mono />}
                {c.effectivenessDueDate &&
                  <Field label={t('capaDetailEffectivenessDue')} value={c.effectivenessDueDate} mono />}
              </div>
              {c.parentCapaId && (
                <div style={{ marginTop: 12, padding: 10, background: 'var(--warning-bg)', borderRadius: 'var(--r-sm)', border: '1px solid var(--warning-fg)' }}>
                  <div style={{ fontSize: 11, color: 'var(--warning-fg)', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.06em' }}>
                    {t('capaRecurrenceOf')}
                  </div>
                  <div style={{ fontSize: 13, color: 'var(--ink-1)', marginTop: 4 }}>
                    {c.parentCapaId}
                  </div>
                </div>
              )}
            </Card>
          )}

          {tab === 'rca' && (
            <Card title={t('capaTabRcaShort')} Icon={Network}>
              {c.rcaMethod === 'NONE' && !c.rootCause
                ? <InlineEmpty compact>{t('capaRcaEmpty')}</InlineEmpty>
                : <div>
                    <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 12 }}>
                      <span style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
                        {t('capaRcaMethod')}
                      </span>
                      <Badge tone="primary">{c.rcaMethod.replace('_', '-')}</Badge>
                      {c.rootCauseCategory && (
                        <Badge tone="accent">{c.rootCauseCategory}</Badge>
                      )}
                    </div>

                    {c.rootCause && (
                      <Field label={t('capaRootCauseLabel')} value={c.rootCause} multiline bold />
                    )}

                    {c.rcaSteps.length > 0 && (
                      <div style={{ marginTop: 12 }}>
                        <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 8 }}>
                          {c.rcaMethod === 'FIVE_WHYS' ? t('capaRcaFiveWhys')
                           : c.rcaMethod === 'FISHBONE' ? t('capaRcaFishbone')
                           : c.rcaMethod === 'PARETO' ? t('capaRcaPareto')
                           : t('capaRcaSteps')}
                        </div>
                        <ol style={{ margin: 0, paddingInlineStart: 24 }}>
                          {c.rcaSteps.filter(s => s.trim()).map((s, i) =>
                            <li key={i} style={{ fontSize: 13, color: 'var(--ink-1)', marginBottom: 6, lineHeight: 1.5 }}>
                              {s}
                            </li>)}
                        </ol>
                      </div>
                    )}
                  </div>
              }
            </Card>
          )}

          {tab === 'lifecycle' && (
            <Card title={t('capaTabLifecycle')} Icon={Calendar}>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 12 }}>
                <Field
                  label={t('capaLifecycleOwnerTest')}
                  value={c.ownerTestResult
                    ? `${c.ownerTestResult} · ${c.ownerTestAt?.slice(0, 10) ?? ''}`
                    : '—'} />
                <Field
                  label={t('capaLifecycleIndependentTest')}
                  value={c.independentTestResult
                    ? `${c.independentTestResult} · ${c.independentTestAt?.slice(0, 10) ?? ''}`
                    : '—'} />
                <Field
                  label={t('capaLifecycleAuditSignoff')}
                  value={c.auditSignoffAt
                    ? `${c.auditSignedOffBy ?? ''} · ${c.auditSignoffAt.slice(0, 10)}`
                    : '—'} />
                <Field
                  label={t('capaLifecycleEffectiveness')}
                  value={c.effectivenessVerifiedAt
                    ? `${c.effectivenessVerified ? '✓' : '✗'} ${c.effectivenessVerifiedAt.slice(0, 10)}`
                    : c.effectivenessDueDate
                      ? `${t('capaLifecycleEffectivenessDue')}: ${c.effectivenessDueDate}`
                      : '—'} />
              </div>
              {c.customerDisclosureRequired && (
                <div style={{ marginTop: 16, padding: 10, background: 'var(--info-bg)', borderRadius: 'var(--r-sm)', border: '1px solid var(--info-fg)' }}>
                  <div style={{ fontSize: 11, color: 'var(--info-fg)', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.06em' }}>
                    {t('capaCustomerDisclosure')}
                  </div>
                  <div style={{ fontSize: 12, color: 'var(--ink-2)', marginTop: 4 }}>
                    {c.customerDisclosureDate
                      ? `${t('capaCustomerDisclosureSent')}: ${c.customerDisclosureDate}`
                      : t('capaCustomerDisclosurePending')}
                  </div>
                </div>
              )}
            </Card>
          )}

          {tab === 'history' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              {/* Real forensic history from platform.audit_log — populated by
                  CapaTaskService (PR #42) on every CREATE / transition /
                  DELETE, plus F8 rule fires from V128 (AR-26..AR-30).
                  Both cards self-hide on empty so a seed-only CAPA without
                  a backendId renders no phantom cards; the local-history
                  fallback below carries the timeline UX for those rows.
                  Lights up automatically once the register switches to
                  /api/capa/tasks (next PR) and backendId is populated. */}
              {c.backendId != null && (
                <>
                  <EntityActivityTimeline entityType="CapaTask" entityId={c.backendId} />
                  <EntityAutomationRuns   entityType="CapaTask" entityId={c.backendId} />
                </>
              )}

              {/* Local history fallback — every advance() through useCapas
                  appends a row, so this card is always populated even for
                  seed-only items. Once backendId lands, the audit-log
                  card above takes primacy. */}
              <Card title={t('capaDetailLocalTimeline')} Icon={Activity}
                action={<Badge tone="neutral">{c.history.length}</Badge>}>
                {c.history.length === 0
                  ? <InlineEmpty compact>{t('capaDetailLocalTimelineEmpty')}</InlineEmpty>
                  : <ol style={{ listStyle: 'none', margin: 0, padding: 0,
                                 display: 'flex', flexDirection: 'column', gap: 8 }}>
                      {c.history.slice().reverse().map((h, i) => (
                        <li key={i} style={{
                          display: 'grid',
                          gridTemplateColumns: '160px 140px 1fr',
                          gap: 12, padding: '8px 0',
                          borderBottom: '1px dashed var(--border)',
                          alignItems: 'flex-start',
                        }}>
                          <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                            {h.at.replace('T', ' ').slice(0, 16)}
                          </span>
                          <Badge tone={CAPA_STATUS_TONE[h.status]} dot>
                            {CAPA_STATUS_LABEL[h.status][isAr ? 'ar' : 'en']}
                          </Badge>
                          <div>
                            <div style={{ fontSize: 13, color: 'var(--ink-1)' }}>
                              {h.note ?? t('capaDetailLocalTimelineStatus')}
                            </div>
                            <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }}>
                              by {h.actor}
                            </div>
                          </div>
                        </li>
                      ))}
                    </ol>
                }
              </Card>
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
