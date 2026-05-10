import { useEffect, useMemo, useState } from 'react';
import {
  Mail, MessageCircle, Smartphone, Bell, Globe, Lock, Users, Megaphone,
  Phone, FileText, Hash, Send, ShieldCheck, AlertOctagon, CheckCheck, Power,
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
  loadChannels, configureChannel, disableChannel, subscribe, aggregateChannels,
  HEALTH_TONE, HEALTH_LABEL,
  type ChannelProviderConfig,
} from '../../lib/notificationChannels';
import type { CommChannel } from '../../lib/stakeholders';

const CHANNEL_ICON: Record<CommChannel, typeof Send> = {
  EMAIL: Mail, WHATSAPP: MessageCircle, SMS: Smartphone, IN_APP: Bell,
  PORTAL: Globe, SECURE_MSG: Lock, IN_PERSON: Users,
  PUBLIC_DISCLOSURE: Megaphone, PHONE: Phone, FORMAL_LETTER: FileText, TEAMS_SLACK: Hash,
};

/**
 * NotificationChannelsPage — Sprint 6 production hardening.
 *
 * Wires the demo notification dispatch runtime to real channel providers.
 * Each channel has a provider, sender identity, region, and health
 * status. "Connect" simulates the provider handshake (in production this
 * exchanges OAuth / API keys). Saudi-mandatory channels (Email, WhatsApp,
 * SMS, Portal, Public Disclosure) drive the production-readiness flag.
 */
export function NotificationChannelsPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const [channels, setChannels] = useState<ChannelProviderConfig[]>(() => loadChannels());

  useEffect(() => subscribe(() => setChannels(loadChannels())), []);

  const stats = useMemo(() => aggregateChannels(channels), [channels]);

  function connect(c: CommChannel) {
    configureChannel(c);
    toast.success(t('ncToastConnected'));
  }
  function disconnect(c: CommChannel) {
    if (!confirm(t('ncDisconnectConfirm'))) return;
    disableChannel(c);
    toast.info(t('ncToastDisconnected'));
  }
  function connectAll() {
    for (const c of channels) {
      if (!c.configured) configureChannel(c.channel);
    }
    toast.success(t('ncToastAllConnected'));
  }

  const saudiChannels = channels.filter(c => c.saudiMandatory);
  const otherChannels = channels.filter(c => !c.saudiMandatory);

  return (
    <Shell title={t('ncTitle')} sub={t('ncSub')}>
      <PageHeader
        eyebrow={t('ncEyebrow')}
        title={t('ncTitle')}
        sub={t('ncPageSub')}
        actions={
          <Button variant="primary" Icon={Power} onClick={connectAll}>
            {t('ncConnectAll')}
          </Button>
        }
      />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Send}        label={t('ncKpiTotal')}      value={String(stats.total)} tone="primary" />
        <Kpi Icon={CheckCheck}  label={t('ncKpiConfigured')}  value={String(stats.configured)} tone="success" />
        <Kpi Icon={ShieldCheck} label={t('ncKpiSaudi')}        value={`${stats.saudiMandatoryConfigured} / ${stats.saudiMandatoryTotal}`} tone={stats.saudiReady ? 'success' : 'warning'} />
        <Kpi Icon={AlertOctagon} label={t('ncKpiPending')}     value={String(stats.total - stats.configured)} tone={stats.total === stats.configured ? 'success' : 'warning'} />
      </div>

      {/* Saudi-mandatory channels */}
      <Card title={t('ncSaudiTitle')} Icon={ShieldCheck}
        action={<Badge tone={stats.saudiReady ? 'success' : 'warning'}>{stats.saudiMandatoryConfigured} / {stats.saudiMandatoryTotal}</Badge>}>
        <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
          {t('ncSaudiSub')}
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: 12 }}>
          {saudiChannels.map(c => (
            <ChannelCard key={c.channel} channel={c} onConnect={connect} onDisconnect={disconnect} t={t} lang={lang} />
          ))}
        </div>
      </Card>

      {/* Other channels */}
      <div style={{ marginTop: 16 }}>
        <Card title={t('ncOtherTitle')} Icon={Send}>
          <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
            {t('ncOtherSub')}
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: 12 }}>
            {otherChannels.map(c => (
              <ChannelCard key={c.channel} channel={c} onConnect={connect} onDisconnect={disconnect} t={t} lang={lang} />
            ))}
          </div>
        </Card>
      </div>
    </Shell>
  );
}

function ChannelCard({ channel: c, onConnect, onDisconnect, t, lang }: {
  channel: ChannelProviderConfig;
  onConnect: (ch: CommChannel) => void;
  onDisconnect: (ch: CommChannel) => void;
  t: (k: string) => string;
  lang: 'en' | 'ar';
}) {
  const Icon = CHANNEL_ICON[c.channel];
  return (
    <div style={{
      padding: 16, borderRadius: 'var(--r-md)',
      background: c.configured ? 'var(--success-bg)' : 'var(--surface-2)',
      border: `1px solid ${c.configured ? 'var(--success-fg)' : 'var(--border)'}`,
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 10 }}>
        <Icon size={20} color={c.configured ? 'var(--success-fg)' : 'var(--ink-3)'} />
        <div style={{ fontSize: 14, fontWeight: 700, flex: 1 }}>{c.channel}</div>
        <Badge tone={HEALTH_TONE[c.health]} dot>
          {lang === 'ar' ? HEALTH_LABEL[c.health].ar : HEALTH_LABEL[c.health].en}
        </Badge>
      </div>
      <div style={{ fontSize: 12, color: 'var(--ink-2)', lineHeight: 1.5, marginBottom: 8 }}>
        {lang === 'ar' ? c.providerAr : c.providerEn}
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 11, color: 'var(--ink-3)', marginBottom: 12 }}>
        {c.senderIdentity && <div className="mono">{c.senderIdentity}</div>}
        {c.region && <div>{t('ncRegionLabel')}: {c.region}</div>}
        {c.dailyQuota && <div>{t('ncQuotaLabel')}: {c.dailyQuota.toLocaleString()}/day</div>}
      </div>
      {c.requiredFor && c.requiredFor.length > 0 && (
        <div style={{
          padding: 8, background: 'var(--surface-1)',
          borderRadius: 'var(--r-sm)', fontSize: 10, color: 'var(--ink-3)', marginBottom: 12,
          lineHeight: 1.5,
        }}>
          <strong>{t('ncRequiredFor')}:</strong>
          <ul style={{ margin: '4px 0 0', paddingInlineStart: 16 }}>
            {c.requiredFor.map(r => <li key={r}>{r}</li>)}
          </ul>
        </div>
      )}
      <div style={{ display: 'flex', gap: 6 }}>
        {c.configured
          ? <Button variant="ghost" Icon={AlertOctagon} onClick={() => onDisconnect(c.channel)}>{t('ncDisconnect')}</Button>
          : <Button variant="primary" Icon={Power} onClick={() => onConnect(c.channel)}>{t('ncConnect')}</Button>}
      </div>
    </div>
  );
}
