import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  FileCheck2, Users, Clock, ListChecks,
  CheckCircle2, BookOpen, Send,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Progress } from '../../components/ui/Progress';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  ATTESTATION_CAMPAIGNS, campaignProgress,
  CAMPAIGN_STATUS_TONE, CAMPAIGN_STATUS_LABEL, CAMPAIGN_KIND_LABEL,
  RESPONDENT_STATUS_TONE, RESPONDENT_STATUS_LABEL,
  type AttestationCampaign,
} from '../../lib/attestationCampaigns';

/**
 * Attestation campaigns — DD-63.
 *
 * Campaign engine that fans out attestation requests to a defined
 * population (control owners / employees / suppliers), tracks
 * per-respondent completion, and surfaces a single dashboard.
 *
 * Modelled on industry-standard tooling Issue Campaigns + the industry pattern GRC Attestation +
 * industry-standard tooling Privacy Awareness campaigns.
 */
type TabId = 'in_flight' | 'closing' | 'completed' | 'draft' | 'all';

export function AttestationCampaignsPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const navigate = useNavigate();
  const [tab, setTab] = useState<TabId>('in_flight');
  const [activeId, setActiveId] = useState<string | null>(ATTESTATION_CAMPAIGNS[0]?.id ?? null);
  // Per-session "reminded" stamps. Keyed `${campaignId}:${responder}` so we
  // can show the row as just-reminded without a backend round-trip.
  const [reminded, setReminded] = useState<Record<string, string>>({});

  function sendReminder(campaignId: string, responder: string, name?: string) {
    setReminded(prev => ({ ...prev, [`${campaignId}:${responder}`]: new Date().toISOString() }));
    toast.success(t('campaignsRemindToast').replace('{r}', name ?? responder));
  }

  const campaigns = ATTESTATION_CAMPAIGNS;
  const inFlight  = campaigns.filter(c => c.status === 'IN_FLIGHT');
  const closing   = campaigns.filter(c => c.status === 'CLOSING');
  const completed = campaigns.filter(c => c.status === 'COMPLETED');
  const draft     = campaigns.filter(c => c.status === 'DRAFT');

  const visible = tab === 'all'
    ? campaigns
    : tab === 'in_flight' ? inFlight
    : tab === 'closing'   ? closing
    : tab === 'completed' ? completed
    : draft;

  const tabs: TabItem<TabId>[] = [
    { id: 'in_flight', label: `${t('campaignsTabInFlight')}${inFlight.length ? ' · ' + inFlight.length : ''}` },
    { id: 'closing',   label: `${t('campaignsTabClosing')}${closing.length ? ' · ' + closing.length : ''}` },
    { id: 'completed', label: `${t('campaignsTabCompleted')}${completed.length ? ' · ' + completed.length : ''}` },
    { id: 'draft',     label: `${t('campaignsTabDraft')}${draft.length ? ' · ' + draft.length : ''}` },
    { id: 'all',       label: `${t('campaignsTabAll')}${campaigns.length ? ' · ' + campaigns.length : ''}` },
  ];

  const active = campaigns.find(c => c.id === activeId) ?? null;

  return (
    <Shell title={t('campaignsTitle')} sub={t('campaignsSub')}>
      <PageHeader
        eyebrow={t('campaignsEyebrow')}
        title={t('campaignsTitle')}
        sub={t('campaignsPageSub')}
        actions={<>
          <Button variant="primary" onClick={() => navigate('/admin?tab=compliance&action=campaign-new')}>
            {t('campaignsNew')}
          </Button>
        </>}
      />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={FileCheck2} label={t('campaignsKpiInFlight')}     value={inFlight.length}     tone="info" />
        <Kpi Icon={CheckCircle2} label={t('campaignsKpiCompleted')}  value={completed.length}    tone="success" />
        <Kpi Icon={Clock}      label={t('campaignsKpiClosing')}      value={closing.length}      tone="warning" />
        <Kpi Icon={ListChecks} label={t('campaignsKpiTotalRespondents')}
          value={campaigns.reduce((acc, c) => acc + c.respondents.length, 0)} tone="primary" />
      </div>

      <Tabs items={tabs} active={tab} onChange={setTab} />

      <div style={{ display: 'grid', gridTemplateColumns: '380px 1fr', gap: 16 }}>
        {/* Left rail — campaign list */}
        <Card title={t('campaignsListTitle')} Icon={FileCheck2} padded={false}>
          <div style={{ maxHeight: 600, overflowY: 'auto' }}>
            {visible.map(c => {
              const prog = campaignProgress(c);
              return (
                <button key={c.id} onClick={() => setActiveId(c.id)}
                  style={{
                    display: 'block', width: '100%', textAlign: 'start', cursor: 'pointer',
                    padding: 12, border: 'none', borderBottom: '1px solid var(--border)',
                    background: c.id === activeId ? 'var(--primary-50)' : 'transparent',
                    transition: 'background var(--dur-fast)',
                  }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 8 }}>
                    <div style={{ minWidth: 0 }}>
                      <div className="mono" style={{ fontSize: 11, fontWeight: 700, color: 'var(--ink-2)' }}>{c.code}</div>
                      <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)', marginTop: 2, lineHeight: 1.4 }}>
                        {lang === 'ar' ? c.titleAr : c.titleEn}
                      </div>
                    </div>
                    <Badge tone={CAMPAIGN_STATUS_TONE[c.status]} dot>
                      {CAMPAIGN_STATUS_LABEL[c.status][lang === 'ar' ? 'ar' : 'en']}
                    </Badge>
                  </div>
                  <div style={{ marginTop: 8 }}>
                    <Progress label={t('campaignsProgress')} value={prog.completedPct} tone={prog.completedPct === 100 ? 'success' : 'primary'} />
                  </div>
                  <div style={{ display: 'flex', gap: 8, marginTop: 6, fontSize: 11, color: 'var(--ink-3)' }}>
                    <span>{prog.completed}/{prog.total} {t('campaignsCompletedLabel')}</span>
                    {prog.overdue > 0 && <span style={{ color: 'var(--danger-fg)' }}>· {prog.overdue} {t('campaignsOverdueLabel')}</span>}
                  </div>
                </button>
              );
            })}
          </div>
        </Card>

        {/* Right pane — selected campaign detail */}
        <div>
          {active ? <CampaignDetail c={active} t={t} lang={lang} reminded={reminded} onRemind={(responder, name) => sendReminder(active.id, responder, name)} /> : (
            <Card>
              <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)' }}>
                {t('campaignsPickOne')}
              </div>
            </Card>
          )}
        </div>
      </div>
    </Shell>
  );
}

function CampaignDetail({ c, t, lang, reminded, onRemind }: {
  c: AttestationCampaign;
  t: (k: string) => string;
  lang: 'en' | 'ar';
  reminded: Record<string, string>;
  onRemind: (responder: string, name?: string) => void;
}) {
  const prog = campaignProgress(c);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <Card>
        <div style={{ marginBottom: 12 }}>
          <div className="mono" style={{ fontSize: 12, fontWeight: 700, color: 'var(--ink-2)' }}>{c.code}</div>
          <h2 style={{ margin: '4px 0 0 0', fontSize: 18, fontWeight: 700, color: 'var(--ink-1)', lineHeight: 1.3 }}>
            {lang === 'ar' ? c.titleAr : c.titleEn}
          </h2>
          {c.descriptionEn && (
            <p style={{ marginTop: 8, fontSize: 13, color: 'var(--ink-2)', lineHeight: 1.6 }}>
              {lang === 'ar' && c.descriptionAr ? c.descriptionAr : c.descriptionEn}
            </p>
          )}
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 10, marginBottom: 12 }}>
          <Stat Icon={BookOpen}       label={t('campaignsStatKind')}      value={CAMPAIGN_KIND_LABEL[c.kind][lang === 'ar' ? 'ar' : 'en']} />
          <Stat Icon={ListChecks}     label={t('campaignsStatReference')} value={c.reference} />
          <Stat Icon={Users}          label={t('campaignsStatOwner')}     value={c.owner} />
          <Stat Icon={Clock}          label={t('campaignsStatDue')}       value={c.dueDate} mono />
        </div>

        {/* Progress strip */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 8 }}>
          <Counter tone="success" label={t('respCompleted')} value={prog.completed} />
          <Counter tone="info"    label={t('respInProgress')} value={prog.inProgress} />
          <Counter tone="neutral" label={t('respPending')} value={prog.pending} />
          <Counter tone="danger"  label={t('respOverdue')} value={prog.overdue} />
          <Counter tone="neutral" label={t('respExempt')} value={prog.exempt} />
        </div>
      </Card>

      <Card title={t('campaignsRespondentsTitle')} Icon={Users}
        action={<Badge tone="primary">{c.respondents.length}</Badge>}>
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          {c.respondents.map(r => (
            <div key={r.id} style={{
              display: 'grid',
              gridTemplateColumns: '1fr 1fr 110px 130px 120px',
              gap: 12, padding: '10px 0',
              borderBottom: '1px dashed var(--border)',
              alignItems: 'center',
            }}>
              <div>
                <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)' }}>{r.responderName ?? r.responder}</div>
                <div className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>{r.responder}</div>
              </div>
              <div style={{ fontSize: 12, color: 'var(--ink-2)' }}>{r.scope ?? '—'}</div>
              <Badge tone={RESPONDENT_STATUS_TONE[r.status]} dot>
                {RESPONDENT_STATUS_LABEL[r.status][lang === 'ar' ? 'ar' : 'en']}
              </Badge>
              <div className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                {r.submittedAt
                  ? r.submittedAt.slice(0, 10)
                  : r.lastSeenAt
                    ? `seen ${r.lastSeenAt.slice(0, 10)}`
                    : '—'}
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', flexDirection: 'column', alignItems: 'flex-end', gap: 2 }}>
                {r.status === 'PENDING' || r.status === 'OVERDUE' ? (
                  <>
                    <Button variant="primary" size="sm" Icon={Send}
                      onClick={() => onRemind(r.responder, r.responderName)}>
                      {t('campaignsRemind')}
                    </Button>
                    {reminded[`${c.id}:${r.responder}`] && (
                      <span style={{ fontSize: 10, color: 'var(--success-fg)' }} className="mono">
                        ✓ {reminded[`${c.id}:${r.responder}`].slice(11, 16)}
                      </span>
                    )}
                  </>
                ) : r.status === 'COMPLETED' && r.intakeRefCode ? (
                  <Badge tone="success">{r.intakeRefCode}</Badge>
                ) : null}
              </div>
            </div>
          ))}
        </div>
      </Card>
    </div>
  );
}

function Stat({ Icon, label, value, mono }: { Icon: typeof Clock; label: string; value: string; mono?: boolean }) {
  return (
    <div style={{ padding: 10, background: 'var(--surface-2)', border: '1px solid var(--border)', borderRadius: 'var(--r-sm)' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
        <Icon size={12} color="var(--ink-3)" />
        <span style={{ fontSize: 10, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>{label}</span>
      </div>
      <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink-1)', fontFamily: mono ? 'var(--font-mono)' : undefined }}>{value}</div>
    </div>
  );
}

function Counter({ tone, label, value }: { tone: 'info' | 'neutral' | 'danger' | 'success' | 'warning'; label: string; value: number }) {
  const palette: Record<string, { bg: string; fg: string }> = {
    info: { bg: 'var(--info-bg)', fg: 'var(--info-fg)' },
    neutral: { bg: 'var(--surface-2)', fg: 'var(--ink-2)' },
    danger: { bg: 'var(--danger-bg)', fg: 'var(--danger-fg)' },
    success: { bg: 'var(--success-bg)', fg: 'var(--success-fg)' },
    warning: { bg: 'var(--warning-bg)', fg: 'var(--warning-fg)' },
  };
  const c = palette[tone];
  return (
    <div style={{ padding: 12, background: c.bg, borderRadius: 'var(--r-sm)' }}>
      <div className="mono" style={{ fontSize: 22, fontWeight: 700, color: c.fg }}>{value}</div>
      <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }}>{label}</div>
    </div>
  );
}
