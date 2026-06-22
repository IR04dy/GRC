import { useEffect, useMemo, useState } from 'react';
import {
  Server, Database, Cloud, ShieldCheck, Upload, AlertTriangle, CheckCheck,
  ArrowRight, Lock, Bell, FileCheck2, Cpu, Activity,
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
  PERSISTENCE_REGISTRY, inventoryLocalStorage, migrateStore,
  type MigrationResult,
} from '../../lib/persistence';
import {
  currentMode, setMode, MODE_LABEL, MODE_TONE, subscribeMode,
  type Mode,
} from '../../lib/productionMode';
import { isEnrolled, subscribe as subscribeMfa } from '../../lib/mfa';
import { aggregateFiles, subscribe as subscribeFiles } from '../../lib/fileUpload';
import { aggregateChannels, subscribe as subscribeChannels } from '../../lib/notificationChannels';
import { aggregateSso, subscribeConfig as subscribeSso } from '../../lib/sso';
import { aggregateMonitoring } from '../../lib/monitoring';
import { aggregateBackupDr } from '../../lib/backupDr';
import { getUser } from '../../lib/auth';

/**
 * ProductionReadinessPage — convert the demo platform to production
 * without wiping data.
 *
 * Three sections:
 *   1. Mode strip — current mode (demo / migrating / production) + toggle
 *   2. Store inventory — every localStorage-backed store with row count,
 *      backend-readiness, and a "Migrate" button per store. The bulk
 *      "Migrate all" runs them in sequence.
 *   3. Production checklist — auth (SSO/MFA), file upload service,
 *      monitoring, backup, what's still required before flipping mode
 *      to production.
 */
export function ProductionReadinessPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const [mode, setLocalMode] = useState<Mode>(() => currentMode());
  const [inventory, setInventory] = useState(() => inventoryLocalStorage());
  const [results, setResults] = useState<MigrationResult[]>([]);
  const [migrating, setMigrating] = useState(false);

  useEffect(() => subscribeMode(() => setLocalMode(currentMode())), []);

  // Reactive MFA-enrolled flag for the current user.
  const userRef = getUser()?.username ?? 'demo@tss.sa';
  const [mfaOk, setMfaOk] = useState(() => isEnrolled(userRef));
  useEffect(() => subscribeMfa(() => setMfaOk(isEnrolled(userRef))), [userRef]);

  // Reactive file-upload flag — Ready once at least one file has been uploaded.
  const [fileUploadOk, setFileUploadOk] = useState(() => aggregateFiles().total > 0);
  useEffect(() => subscribeFiles(() => setFileUploadOk(aggregateFiles().total > 0)), []);

  // Reactive notification-channels flag — Ready when all Saudi-mandatory channels are configured.
  const [channelsOk, setChannelsOk] = useState(() => aggregateChannels().saudiReady);
  useEffect(() => subscribeChannels(() => setChannelsOk(aggregateChannels().saudiReady)), []);

  // Reactive SSO flag — Ready when at least one provider is configured (Nafath or SAML).
  const [ssoOk, setSsoOk] = useState(() => aggregateSso().ssoReady);
  useEffect(() => subscribeSso(() => setSsoOk(aggregateSso().ssoReady)), []);

  // Monitoring is Ready when at least 5 services + on-call rotation set up (always true in seed).
  const monitoringOk = aggregateMonitoring().monitoringReady;
  // DR is Ready when no failed backups + no failed repl + at least one PASS drill (always true in seed).
  const drOk = aggregateBackupDr().drReady;

  // Re-read inventory when storage changes anywhere on the page.
  useEffect(() => {
    const handler = () => setInventory(inventoryLocalStorage());
    window.addEventListener('storage', handler);
    return () => window.removeEventListener('storage', handler);
  }, []);

  const totalRows = useMemo(() => inventory.reduce((s, e) => s + e.rows, 0), [inventory]);
  const totalBytes = useMemo(() => inventory.reduce((s, e) => s + e.bytes, 0), [inventory]);

  async function migrateOne(key: string) {
    setMigrating(true);
    try {
      const r = await migrateStore(key);
      setResults(prev => [r, ...prev.filter(x => x.key !== r.key)]);
      if (r.status === 'OK') toast.success(t('prMigrationOkToast').replace('{n}', String(r.rowsUploaded)));
      else if (r.status === 'SKIPPED') toast.info(t('prMigrationSkipToast'));
      else toast.danger(t('prMigrationFailToast').replace('{e}', r.error ?? '?'));
    } finally {
      setMigrating(false);
    }
  }

  async function migrateAll() {
    setMigrating(true);
    setMode('migrating');
    const concrete = inventory.map(e => e.key);
    const all: MigrationResult[] = [];
    for (const k of concrete) {
      try {
        const r = await migrateStore(k);
        all.push(r);
      } catch (e) {
        all.push({ key: k, rowsLocal: 0, rowsRemote: 0, rowsUploaded: 0, status: 'FAILED', error: String(e) });
      }
    }
    setResults(all);
    const ok = all.filter(r => r.status === 'OK').length;
    const failed = all.filter(r => r.status === 'FAILED').length;
    if (failed === 0 && ok > 0) {
      toast.success(t('prMigrationAllToast').replace('{ok}', String(ok)));
    } else {
      toast.danger(t('prMigrationAllFailToast').replace('{ok}', String(ok)).replace('{f}', String(failed)));
    }
    setMigrating(false);
  }

  function flipTo(target: Mode) {
    setMode(target);
    toast.success(t('prModeToast').replace('{mode}', lang === 'ar' ? MODE_LABEL[target].ar : MODE_LABEL[target].en));
  }

  const checklistItems = buildChecklist(t, {
    mfa: mfaOk, fileUpload: fileUploadOk,
    notifications: channelsOk, monitoring: monitoringOk, backup: drOk,
    sso: ssoOk,
  });
  const okItems = checklistItems.filter(c => c.status === 'OK').length;

  return (
    <Shell title={t('prTitle')} sub={t('prSub')}>
      <PageHeader
        eyebrow={t('prEyebrow')}
        title={t('prTitle')}
        sub={t('prPageSub')}
      />

      {/* Mode strip */}
      <Card title={t('prModeTitle')} Icon={Server}
        action={<Badge tone={MODE_TONE[mode]}>{lang === 'ar' ? MODE_LABEL[mode].ar : MODE_LABEL[mode].en}</Badge>}>
        <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
          {t('prModeSub')}
        </div>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          {(['demo', 'migrating', 'production'] as Mode[]).map(m => (
            <Button key={m}
              variant={m === mode ? 'primary' : 'secondary'}
              onClick={() => flipTo(m)}>
              {lang === 'ar' ? MODE_LABEL[m].ar : MODE_LABEL[m].en}
            </Button>
          ))}
        </div>
        {mode === 'demo' && (
          <div style={{ marginTop: 12, padding: 10, background: 'var(--surface-2)', borderRadius: 'var(--r-sm)', fontSize: 12, color: 'var(--ink-2)', lineHeight: 1.6 }}>
            {t('prModeDemoNote')}
          </div>
        )}
        {mode === 'production' && (
          <div style={{ marginTop: 12, padding: 10, background: 'var(--success-bg)', color: 'var(--success-fg)', borderRadius: 'var(--r-sm)', fontSize: 12, lineHeight: 1.6 }}>
            {t('prModeProdNote')}
          </div>
        )}
      </Card>

      {/* Inventory KPIs */}
      <div className="kpi-grid" style={{ margin: '16px 0' }}>
        <Kpi Icon={Database}    label={t('prKpiStores')}    value={String(inventory.length)} tone="primary"
          onClick={() => document.getElementById('pr-inventory-section')?.scrollIntoView({ behavior: 'smooth', block: 'start' })} />
        <Kpi Icon={Server}       label={t('prKpiRows')}       value={totalRows.toLocaleString()} tone="info"
          onClick={() => document.getElementById('pr-inventory-section')?.scrollIntoView({ behavior: 'smooth', block: 'start' })} />
        <Kpi Icon={Cloud}        label={t('prKpiBytes')}      value={`${(totalBytes / 1024).toFixed(1)}KB`} tone="info"
          onClick={() => document.getElementById('pr-inventory-section')?.scrollIntoView({ behavior: 'smooth', block: 'start' })} />
        <Kpi Icon={Upload}       label={t('prKpiBackendReady')} value={String(PERSISTENCE_REGISTRY.filter(r => r.backendReady).length)} tone="success"
          onClick={() => document.getElementById('pr-inventory-section')?.scrollIntoView({ behavior: 'smooth', block: 'start' })} />
        <Kpi Icon={CheckCheck}   label={t('prKpiChecklistOk')} value={`${okItems} / ${checklistItems.length}`} tone={okItems === checklistItems.length ? 'success' : 'warning'}
          onClick={() => document.getElementById('pr-checklist-section')?.scrollIntoView({ behavior: 'smooth', block: 'start' })} />
      </div>

      {/* Store inventory */}
      <div id="pr-inventory-section">
      <Card title={t('prInventoryTitle')} Icon={Database}
        action={
          <Button variant="primary" Icon={Upload} onClick={migrateAll} disabled={migrating || inventory.length === 0}>
            {t('prMigrateAll')}
          </Button>
        }>
        <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
          {t('prInventorySub')}
        </div>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
          <thead>
            <tr>
              <th style={th}>{t('prTblKey')}</th>
              <th style={th}>{t('prTblModule')}</th>
              <th style={th}>{t('prTblRows')}</th>
              <th style={th}>{t('prTblBackend')}</th>
              <th style={th}>{t('prTblResult')}</th>
              <th style={th}> </th>
            </tr>
          </thead>
          <tbody>
            {inventory.map(item => {
              const info = PERSISTENCE_REGISTRY.find(r =>
                r.key === item.key
                || (r.key.endsWith('*') && item.key.startsWith(r.key.slice(0, -1)))
              );
              const result = results.find(r => r.key === item.key);
              return (
                <tr key={item.key} style={{ borderBottom: '1px dashed var(--border)' }}>
                  <td style={td}>
                    <div className="mono" style={{ fontSize: 11 }}>{item.key}</div>
                    {info && (
                      <div style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 2 }}>
                        {lang === 'ar' ? info.labelAr : info.labelEn}
                      </div>
                    )}
                  </td>
                  <td style={td}>{info?.module ?? '—'}</td>
                  <td style={{ ...td, fontFamily: 'var(--font-mono)' }}>{item.rows}</td>
                  <td style={td}>
                    {info?.backendReady
                      ? <Badge tone="success">{t('prBackendReady')}</Badge>
                      : <Badge tone="warning">{t('prBackendPending')}</Badge>}
                  </td>
                  <td style={td}>
                    {result
                      ? <Badge tone={result.status === 'OK' ? 'success' : result.status === 'SKIPPED' ? 'neutral' : 'danger'}>
                          {result.status === 'OK'
                            ? `${result.rowsUploaded} ${t('prUploaded')}`
                            : result.status === 'SKIPPED'
                              ? t('prSkipped')
                              : t('prFailed')}
                        </Badge>
                      : <Badge tone="neutral">{t('prNotMigrated')}</Badge>}
                  </td>
                  <td style={td}>
                    <Button variant="secondary" Icon={Upload}
                      onClick={() => migrateOne(item.key)}
                      disabled={migrating || !info?.backendReady}>
                      {t('prMigrateOne')}
                    </Button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </Card>
      </div>

      {/* Production checklist */}
      <div id="pr-checklist-section" style={{ marginTop: 16 }}>
        <Card title={t('prChecklistTitle')} Icon={ShieldCheck}
          action={<Badge tone={okItems === checklistItems.length ? 'success' : 'warning'}>{okItems} / {checklistItems.length}</Badge>}>
          <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
            {t('prChecklistSub')}
          </div>
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            {checklistItems.map(item => {
              const Icon = item.icon;
              return (
                <div key={item.key} style={{
                  padding: '12px 0', borderBottom: '1px dashed var(--border)',
                  display: 'grid', gridTemplateColumns: '24px 1fr auto', gap: 12, alignItems: 'flex-start',
                }}>
                  <Icon size={18} color={item.status === 'OK' ? 'var(--success-fg)' : item.status === 'PENDING' ? 'var(--warning-fg)' : 'var(--ink-3)'} />
                  <div>
                    <div style={{ fontSize: 13, fontWeight: 600 }}>{item.title}</div>
                    <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 4, lineHeight: 1.6 }}>
                      {item.description}
                    </div>
                  </div>
                  <Badge tone={item.status === 'OK' ? 'success' : item.status === 'PENDING' ? 'warning' : 'neutral'}>
                    {item.statusLabel}
                  </Badge>
                </div>
              );
            })}
          </div>
        </Card>
      </div>
    </Shell>
  );
}

function buildChecklist(t: (k: string) => string, opts: {
  mfa?: boolean; fileUpload?: boolean; notifications?: boolean;
  monitoring?: boolean; backup?: boolean; sso?: boolean;
} = {}) {
  return [
    { key: 'persistence', icon: Database,
      title: t('prCkPersistenceTitle'),
      description: t('prCkPersistenceDesc'),
      status: 'OK' as const, statusLabel: t('prCkReady') },
    { key: 'sso', icon: Lock,
      title: t('prCkSsoTitle'),
      description: t('prCkSsoDesc'),
      status: opts.sso ? 'OK' as const : 'PENDING' as const,
      statusLabel: opts.sso ? t('prCkReady') : t('prCkPending') },
    { key: 'mfa', icon: ShieldCheck,
      title: t('prCkMfaTitle'),
      description: t('prCkMfaDesc'),
      status: opts.mfa ? 'OK' as const : 'PENDING' as const,
      statusLabel: opts.mfa ? t('prCkReady') : t('prCkPending') },
    { key: 'fileUpload', icon: FileCheck2,
      title: t('prCkFileTitle'),
      description: t('prCkFileDesc'),
      status: opts.fileUpload ? 'OK' as const : 'PENDING' as const,
      statusLabel: opts.fileUpload ? t('prCkReady') : t('prCkPending') },
    { key: 'notifications', icon: Bell,
      title: t('prCkNotifTitle'),
      description: t('prCkNotifDesc'),
      status: opts.notifications ? 'OK' as const : 'PENDING' as const,
      statusLabel: opts.notifications ? t('prCkReady') : t('prCkPending') },
    { key: 'monitoring', icon: Activity,
      title: t('prCkMonTitle'),
      description: t('prCkMonDesc'),
      status: opts.monitoring ? 'OK' as const : 'PENDING' as const,
      statusLabel: opts.monitoring ? t('prCkReady') : t('prCkPending') },
    { key: 'backup', icon: Cpu,
      title: t('prCkBackupTitle'),
      description: t('prCkBackupDesc'),
      status: opts.backup ? 'OK' as const : 'PENDING' as const,
      statusLabel: opts.backup ? t('prCkReady') : t('prCkPending') },
    { key: 'tls', icon: Lock,
      title: t('prCkTlsTitle'),
      description: t('prCkTlsDesc'),
      status: 'OK' as const, statusLabel: t('prCkReady') },
    { key: 'audit', icon: AlertTriangle,
      title: t('prCkAuditTitle'),
      description: t('prCkAuditDesc'),
      status: 'OK' as const, statusLabel: t('prCkReady') },
    { key: 'i18n', icon: ArrowRight,
      title: t('prCkI18nTitle'),
      description: t('prCkI18nDesc'),
      status: 'OK' as const, statusLabel: t('prCkReady') },
  ];
}

const th = {
  padding: '8px 10px', textAlign: 'start' as const, fontSize: 10,
  textTransform: 'uppercase' as const, letterSpacing: '0.05em',
  color: 'var(--ink-3)', background: 'var(--surface-2)',
  borderBottom: '2px solid var(--ink-3)',
};
const td = { padding: '10px', verticalAlign: 'top' as const, lineHeight: 1.5 };
