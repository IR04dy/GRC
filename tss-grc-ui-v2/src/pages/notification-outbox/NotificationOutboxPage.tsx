import { useEffect, useMemo, useState } from 'react';
import { AlertOctagon, Clock, CheckCircle2, XCircle } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Avatar } from '../../components/ui/UserPicker';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  loadNotifs, subscribeNotifs, aggregateNotifs,
  markSent, markFailed, cancelNotification,
  CHANNEL_LABEL, STATUS_LABEL, STATUS_TONE,
  type NotificationItem,
} from '../../lib/notificationOutbox';
import { getUser } from '../../lib/userDirectory';

type NoxTab = 'all' | 'pending' | 'sent' | 'failed';

export function NotificationOutboxPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const [tab, setTab] = useState<NoxTab>('pending');
  const [_tick, setTick] = useState(0);
  useEffect(() => subscribeNotifs(() => setTick(t => t + 1)), []);

  const stats = useMemo(() => aggregateNotifs(), [_tick]);
  const all = useMemo(() => [...loadNotifs()].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()), [_tick]);

  const visible = useMemo(() => {
    if (tab === 'pending') return all.filter(n => n.status === 'QUEUED' || n.status === 'SENDING');
    if (tab === 'sent')    return all.filter(n => n.status === 'SENT');
    if (tab === 'failed')  return all.filter(n => n.status === 'FAILED');
    return all;
  }, [all, tab]);

  return (
    <Shell title={t('noxTitle')} sub={t('noxSub')}>
      <PageHeader
        eyebrow={t('noxEyebrow')}
        title={t('noxTitle')}
        sub={t('noxPageSub')}
      />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Clock}         label={t('noxKpiPending')}    value={String(stats.pending)}   tone={stats.pending > 0 ? 'warning' : 'success'} onClick={() => setTab('pending')} active={tab === 'pending'} />
        <Kpi Icon={CheckCircle2}  label={t('noxKpiSent')}        value={String(stats.sentToday)} tone="success" onClick={() => setTab('sent')} active={tab === 'sent'} />
        <Kpi Icon={XCircle}        label={t('noxKpiFailed')}      value={String(stats.failed)}    tone={stats.failed > 0 ? 'danger' : 'success'} onClick={() => setTab('failed')} active={tab === 'failed'} />
        <Kpi Icon={AlertOctagon}   label={t('noxKpiSlaAtRisk')}   value={String(stats.pending)}   tone={stats.pending > 0 ? 'warning' : 'success'} onClick={() => setTab('pending')} active={tab === 'pending'} />
      </div>

      <div style={{ display: 'flex', gap: 4, marginBottom: 12 }}>
        {([
          { id: 'pending', labelKey: 'noxTabPending', count: stats.pending },
          { id: 'sent',    labelKey: 'noxTabSent',     count: stats.sentToday },
          { id: 'failed',  labelKey: 'noxTabFailed',   count: stats.failed },
          { id: 'all',     labelKey: 'noxTabAll',      count: stats.total },
        ] as { id: NoxTab; labelKey: string; count: number }[]).map(td => (
          <button key={td.id} onClick={() => setTab(td.id)}
            style={{
              padding: '8px 12px', fontSize: 12, fontWeight: 600,
              border: `1px solid ${tab === td.id ? 'var(--primary)' : 'var(--border)'}`,
              background: tab === td.id ? 'var(--primary-50)' : 'var(--surface-1)',
              borderRadius: 'var(--r-sm)', cursor: 'pointer',
              display: 'inline-flex', alignItems: 'center', gap: 6,
            }}>
            {t(td.labelKey)}
            <Badge tone={tab === td.id ? 'primary' : 'neutral'}>{td.count}</Badge>
          </button>
        ))}
      </div>

      <Card>
        {visible.length === 0
          ? <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>—</div>
          : (
            <table className="tbl" style={{ width: '100%' }}>
              <thead>
                <tr>
                  <th>{t('noxColCreated')}</th>
                  <th>{t('noxColTo')}</th>
                  <th>{t('noxColChannel')}</th>
                  <th>{t('noxColSubject')}</th>
                  <th>{t('noxColStatus')}</th>
                  <th>{t('noxColSentAt')}</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {visible.map(n => <NotifRow key={n.id} n={n} lang={lang} t={t}
                  onSend={() => { markSent(n.id); toast.success('Sent'); }}
                  onFail={() => { markFailed(n.id, 'Manual fail'); toast.danger('Marked failed'); }}
                  onCancel={() => { cancelNotification(n.id); toast.info('Cancelled'); }}
                />)}
              </tbody>
            </table>
          )}
      </Card>
    </Shell>
  );
}

function NotifRow({ n, lang, t, onSend, onFail: _onFail, onCancel }: {
  n: NotificationItem;
  lang: 'en' | 'ar';
  t: (k: string) => string;
  onSend: () => void;
  onFail: () => void;
  onCancel: () => void;
}) {
  const recipient = getUser(n.recipientId);
  return (
    <tr>
      <td className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>
        {n.createdAt.slice(0, 16).replace('T', ' ')}
      </td>
      <td>
        {recipient ? (
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <Avatar user={recipient} size={20} />
            <div>
              <div style={{ fontSize: 11, fontWeight: 600 }}>
                {lang === 'ar' ? recipient.nameAr : recipient.nameEn}
              </div>
              <div className="mono" style={{ fontSize: 9, color: 'var(--ink-3)' }}>{recipient.email}</div>
            </div>
          </div>
        ) : <span className="mono" style={{ fontSize: 11 }}>{n.recipientId}</span>}
      </td>
      <td>
        <div style={{ display: 'flex', gap: 3, flexWrap: 'wrap' }}>
          {n.channels.map(c => (
            <Badge key={c} tone="info" title={CHANNEL_LABEL[c].provider}>
              {CHANNEL_LABEL[c][lang]}
            </Badge>
          ))}
        </div>
      </td>
      <td>
        <div style={{ fontSize: 12, fontWeight: 600 }}>
          {lang === 'ar' ? n.subjectAr : n.subjectEn}
        </div>
        <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>
          {n.source} · <Badge tone={n.priority === 'P1' ? 'danger' : n.priority === 'P2' ? 'warning' : 'neutral'}>{n.priority}</Badge>
        </div>
      </td>
      <td>
        <Badge tone={STATUS_TONE[n.status]}>{STATUS_LABEL[n.status][lang]}</Badge>
        {n.failureReason && (
          <div style={{ fontSize: 9, color: 'var(--danger-fg)', marginTop: 2 }}>{n.failureReason}</div>
        )}
      </td>
      <td className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>
        {n.sentAt ? n.sentAt.slice(0, 16).replace('T', ' ') : '—'}
      </td>
      <td>
        {n.status === 'QUEUED' && (
          <>
            <Button size="sm" variant="primary" onClick={onSend}>{t('noxRetryBtn')}</Button>
            <Button size="sm" variant="ghost" onClick={onCancel}>{t('noxCancelBtn')}</Button>
          </>
        )}
        {n.status === 'FAILED' && (
          <Button size="sm" variant="primary" onClick={onSend}>{t('noxRetryBtn')}</Button>
        )}
      </td>
    </tr>
  );
}
