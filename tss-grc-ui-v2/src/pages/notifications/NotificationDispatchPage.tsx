import { useEffect, useState } from 'react';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import {
  Send, Mail, MessageCircle, Smartphone, Bell, Globe, Lock,
  Users, Megaphone, Phone, FileText, Hash, Eraser, Play, AlertTriangle,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import { statusLabel } from '../../lib/statusLabels';
import { useToast } from '../../lib/toast';
import {
  CHANNEL_CONFIG, CHANNEL_LABEL, STATUS_TONE,
  loadLog, subscribeLog, clearLog, dispatch, aggregateChannelStats,
  SAMPLE_DISPATCHES,
  type DeliveryRecord,
} from '../../lib/notificationDispatch';
import type { CommChannel } from '../../lib/stakeholders';

const CHANNEL_ICON: Record<CommChannel, typeof Send> = {
  EMAIL: Mail,
  WHATSAPP: MessageCircle,
  SMS: Smartphone,
  IN_APP: Bell,
  PORTAL: Globe,
  SECURE_MSG: Lock,
  IN_PERSON: Users,
  PUBLIC_DISCLOSURE: Megaphone,
  PHONE: Phone,
  FORMAL_LETTER: FileText,
  TEAMS_SLACK: Hash,
};

/**
 * NotificationDispatchPage — Sprint 6.
 *
 * Operator console for the notification dispatch runtime. Top: per-channel
 * status grid with provider, last-verified time, daily quota, and 24h
 * stats. Middle: "Fire sample" buttons that dispatch real demo messages
 * across the matrix events (PDPL breach → SDAIA, NCA cyber → portal,
 * Approval → CFO via WhatsApp, etc.). Bottom: live delivery log.
 */
export function NotificationDispatchPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const [log, setLog] = useState<DeliveryRecord[]>(() => loadLog());
  const [busy, setBusy] = useState(false);

  useEffect(() => subscribeLog(() => setLog(loadLog())), []);

  const stats = aggregateChannelStats(log);
  const totalDelivered24h = stats.reduce((acc, s) => acc + s.delivered24h, 0);
  const totalFailed24h = stats.reduce((acc, s) => acc + s.failed24h, 0);
  const inFlight = stats.reduce((acc, s) => acc + s.inFlight, 0);
  const successRate = totalDelivered24h + totalFailed24h > 0
    ? Math.round((totalDelivered24h / (totalDelivered24h + totalFailed24h)) * 100)
    : 100;

  async function fireSample(idx: number) {
    setBusy(true);
    try {
      await dispatch(SAMPLE_DISPATCHES[idx]);
      toast.success(t('ndDispatchedToast'));
    } catch (e) {
      toast.danger(String(e));
    } finally {
      setBusy(false);
    }
  }

  async function fireAllSamples() {
    setBusy(true);
    try {
      for (const s of SAMPLE_DISPATCHES) {
        await dispatch(s);
      }
      toast.success(t('ndDispatchedAllToast').replace('{n}', String(SAMPLE_DISPATCHES.length)));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Shell title={t('ndTitle')} sub={t('ndSub')}>
      <PageHeader
        eyebrow={t('ndEyebrow')}
        title={t('ndTitle')}
        sub={t('ndPageSub')}
        actions={
          <>
            <Button variant="secondary" Icon={Eraser} onClick={() => { clearLog(); toast.success(t('ndLogCleared')); }}>
              {t('ndClearLog')}
            </Button>
            <Button variant="primary" Icon={Play} onClick={fireAllSamples} disabled={busy}>
              {t('ndFireAll')}
            </Button>
          </>
        }
      />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Send}     label={t('ndKpiTotal24h')}    value={String(totalDelivered24h + totalFailed24h)} tone="primary" />
        <Kpi Icon={Bell}     label={t('ndKpiDelivered')}    value={String(totalDelivered24h)} tone="success" />
        <Kpi Icon={AlertTriangle} label={t('ndKpiFailed')}   value={String(totalFailed24h)} tone={totalFailed24h > 0 ? 'danger' : 'success'} />
        <Kpi Icon={Send}     label={t('ndKpiInFlight')}     value={String(inFlight)} tone="info" />
        <Kpi Icon={Bell}     label={t('ndKpiSuccess')}      value={`${successRate}%`} tone={successRate >= 95 ? 'success' : 'warning'} />
      </div>

      {/* ── Channel grid ──────────────────────────────────────── */}
      <Card title={t('ndChannelsTitle')} Icon={Globe}>
        <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
          {t('ndChannelsSub')}
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 10 }}>
          {Object.values(CHANNEL_CONFIG).map(cfg => {
            const Icon = CHANNEL_ICON[cfg.channel];
            const stat = stats.find(s => s.channel === cfg.channel);
            return (
              <div key={cfg.channel} style={{
                padding: 14,
                borderRadius: 'var(--r-md)',
                background: 'var(--surface-2)',
                border: '1px solid var(--border)',
              }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
                  <Icon size={18} color={cfg.enabled ? 'var(--primary)' : 'var(--ink-3)'} />
                  <div style={{ fontSize: 13, fontWeight: 700, flex: 1 }}>
                    {lang === 'ar' ? CHANNEL_LABEL[cfg.channel].ar : CHANNEL_LABEL[cfg.channel].en}
                  </div>
                  <Badge tone={cfg.enabled ? 'success' : 'neutral'} dot>
                    {cfg.enabled ? t('ndEnabled') : t('ndDisabled')}
                  </Badge>
                </div>
                <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 10, lineHeight: 1.5 }}>
                  {lang === 'ar' ? cfg.providerLabelAr : cfg.providerLabelEn}
                </div>
                <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', fontSize: 10 }}>
                  <Badge tone="success">{stat?.delivered24h ?? 0} {t('ndDelivered24h')}</Badge>
                  {(stat?.failed24h ?? 0) > 0 && <Badge tone="danger">{stat?.failed24h} {t('ndFailed24h')}</Badge>}
                  {(stat?.inFlight ?? 0) > 0 && <Badge tone="info">{stat?.inFlight} {t('ndInFlightLabel')}</Badge>}
                  {cfg.dailyQuota && <Badge tone="neutral">{t('ndDailyQuota')}: {cfg.dailyQuota.toLocaleString()}</Badge>}
                </div>
              </div>
            );
          })}
        </div>
      </Card>

      {/* ── Sample dispatches ─────────────────────────────────── */}
      <div style={{ marginTop: 16 }}>
        <Card title={t('ndSampleTitle')} Icon={Send}
          action={<Badge tone="primary">{SAMPLE_DISPATCHES.length}</Badge>}>
          <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
            {t('ndSampleSub')}
          </div>
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            {SAMPLE_DISPATCHES.map((s, idx) => {
              const Icon = CHANNEL_ICON[s.channel];
              return (
                <div key={idx} style={{
                  padding: '12px 0',
                  borderBottom: '1px dashed var(--border)',
                  display: 'grid',
                  gridTemplateColumns: '1fr auto',
                  gap: 12, alignItems: 'center',
                }}>
                  <div style={{ minWidth: 0 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                      <Badge tone="primary"><Icon size={10} style={{ marginInlineEnd: 4, verticalAlign: 'middle' }}/>{lang === 'ar' ? CHANNEL_LABEL[s.channel].ar : CHANNEL_LABEL[s.channel].en}</Badge>
                      <Badge tone="info">{s.recipientLabelEn}</Badge>
                      <span className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>{s.eventRef}</span>
                    </div>
                    <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)', marginTop: 4 }}>
                      {lang === 'ar' ? s.subjectAr : s.subjectEn}
                    </div>
                    {s.regulatorCitation && (
                      <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 4 }}>
                        {s.regulatorCitation}
                      </div>
                    )}
                  </div>
                  <Button variant="secondary" Icon={Send} onClick={() => fireSample(idx)} disabled={busy}>
                    {t('ndFire')}
                  </Button>
                </div>
              );
            })}
          </div>
        </Card>
      </div>

      {/* ── Delivery log ──────────────────────────────────────── */}
      <div style={{ marginTop: 16 }}>
        <Card title={t('ndLogTitle')} Icon={Bell}
          action={<Badge tone="primary">{log.length}</Badge>}>
          {log.length === 0
            ? <InlineEmpty>{t('ndLogEmpty')}</InlineEmpty>
            : <div style={{ display: 'flex', flexDirection: 'column' }}>
                {log.slice(0, 30).map(r => {
                  const Icon = CHANNEL_ICON[r.channel];
                  return (
                    <div key={r.id} style={{
                      padding: '10px 0', borderBottom: '1px dashed var(--border)',
                      display: 'grid', gridTemplateColumns: '1fr auto', gap: 8, alignItems: 'flex-start',
                    }}>
                      <div style={{ minWidth: 0 }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                          <Icon size={12} color="var(--ink-3)" />
                          <span style={{ fontSize: 12, fontWeight: 600 }}>
                            {lang === 'ar' ? r.subjectAr : r.subjectEn}
                          </span>
                        </div>
                        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 4 }}>
                          {lang === 'ar' ? r.recipientLabelAr : r.recipientLabelEn}
                          {' · '}<span className="mono">{r.eventRef}</span>
                          {' · '}<span className="mono">{r.queuedAt.slice(0, 16).replace('T', ' ')}</span>
                          {r.errorMessage && <> · <span style={{ color: 'var(--danger)' }}>{r.errorMessage}</span></>}
                        </div>
                      </div>
                      <Badge tone={STATUS_TONE[r.status]}>{statusLabel(r.status, lang)}</Badge>
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
