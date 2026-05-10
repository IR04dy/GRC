import { useEffect, useState } from 'react';
import { Lock, Smartphone, Building2, ShieldCheck, AlertOctagon, Power } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  loadConfig, configureProvider, disableProvider, subscribeConfig,
  aggregateSso, listNafathTransactions, subscribeTransactions,
  STATUS_TONE, STATUS_LABEL,
  type SsoProviderKind,
} from '../../lib/sso';
import { Link } from 'react-router-dom';

/**
 * SsoConfigPage — admin surface for the two production SSO modes:
 *   • Nafath (Saudi National SSO) — register an Nafath app id
 *   • SAML 2.0 (corporate IdP) — paste IdP metadata URL + entity id
 *
 * Once either is configured, the production-readiness checklist's SSO
 * row flips to Ready. Recent Nafath transactions appear at the bottom
 * for an audit-friendly trail.
 */
export function SsoConfigPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const [config, setConfig] = useState(() => loadConfig());
  const [stats, setStats] = useState(() => aggregateSso());
  const [transactions, setTransactions] = useState(() => listNafathTransactions());

  useEffect(() => subscribeConfig(() => {
    setConfig(loadConfig());
    setStats(aggregateSso());
  }), []);
  useEffect(() => subscribeTransactions(() => setTransactions(listNafathTransactions())), []);

  const nafath = config.find(c => c.kind === 'NAFATH')!;
  const saml = config.find(c => c.kind === 'SAML')!;

  // Local form state for Nafath
  const [nafathAppId, setNafathAppId] = useState(nafath.nafathAppId ?? '');
  // Local form state for SAML
  const [samlMetadataUrl, setSamlMetadataUrl] = useState(saml.samlMetadataUrl ?? '');
  const [samlIssuer, setSamlIssuer] = useState(saml.samlIssuerEntityId ?? '');

  function saveNafath() {
    if (!nafathAppId.trim()) {
      toast.danger(t('ssoNafathMissingApp'));
      return;
    }
    configureProvider('NAFATH', { nafathAppId: nafathAppId.trim() });
    toast.success(t('ssoNafathConfiguredToast'));
  }

  function saveSaml() {
    if (!samlMetadataUrl.trim() || !samlIssuer.trim()) {
      toast.danger(t('ssoSamlMissingFields'));
      return;
    }
    configureProvider('SAML', {
      samlMetadataUrl: samlMetadataUrl.trim(),
      samlIssuerEntityId: samlIssuer.trim(),
    });
    toast.success(t('ssoSamlConfiguredToast'));
  }

  function disable(kind: SsoProviderKind) {
    if (!confirm(t('ssoDisableConfirm'))) return;
    disableProvider(kind);
    toast.info(t('ssoDisabledToast'));
  }

  return (
    <Shell title={t('ssoTitle')} sub={t('ssoSub')}>
      <PageHeader
        eyebrow={t('ssoEyebrow')}
        title={t('ssoTitle')}
        sub={t('ssoPageSub')}
      />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Lock}            label={t('ssoKpiProviders')}    value={String(stats.totalProviders)} tone="primary" />
        <Kpi Icon={ShieldCheck}      label={t('ssoKpiConfigured')}    value={String(stats.configuredProviders)} tone={stats.ssoReady ? 'success' : 'warning'} />
        <Kpi Icon={Smartphone}       label={t('ssoKpiNafath')}        value={stats.nafathReady ? t('ssoReady') : t('ssoNotReady')} tone={stats.nafathReady ? 'success' : 'warning'} />
        <Kpi Icon={AlertOctagon}      label={t('ssoKpiTransactions')}  value={String(stats.recentTransactions)} tone="info" />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 16 }}>
        {/* Nafath card */}
        <Card title={t('ssoNafathTitle')} Icon={Smartphone}
          action={<Badge tone={nafath.configured ? 'success' : 'neutral'} dot>
            {nafath.configured ? t('ssoConfiguredBadge') : t('ssoNotConfiguredBadge')}
          </Badge>}>
          <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12, lineHeight: 1.6 }}>
            {t('ssoNafathDesc')}
          </div>
          <label style={{ display: 'flex', flexDirection: 'column', gap: 6, marginBottom: 10 }}>
            <span style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
              {t('ssoNafathAppLabel')}
            </span>
            <input value={nafathAppId} onChange={e => setNafathAppId(e.target.value)}
              placeholder="00000000"
              className="mono"
              style={{
                padding: '10px 12px', fontSize: 13,
                border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
                background: 'var(--surface-1)', fontFamily: 'var(--font-mono)',
              }} />
          </label>
          <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 12 }}>
            {t('ssoNafathHint')}
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            <Button variant="primary" Icon={Power} onClick={saveNafath}>
              {nafath.configured ? t('ssoUpdateBtn') : t('ssoConnectBtn')}
            </Button>
            {nafath.configured && (
              <Button variant="ghost" Icon={AlertOctagon} onClick={() => disable('NAFATH')}>
                {t('ssoDisableBtn')}
              </Button>
            )}
            <span style={{ flex: 1 }} />
            <Link to="/sso/nafath" style={{ alignSelf: 'center', fontSize: 12, color: 'var(--primary)' }}>
              {t('ssoTryFlow')} →
            </Link>
          </div>
        </Card>

        {/* SAML card */}
        <Card title={t('ssoSamlTitle')} Icon={Building2}
          action={<Badge tone={saml.configured ? 'success' : 'neutral'} dot>
            {saml.configured ? t('ssoConfiguredBadge') : t('ssoNotConfiguredBadge')}
          </Badge>}>
          <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12, lineHeight: 1.6 }}>
            {t('ssoSamlDesc')}
          </div>
          <label style={{ display: 'flex', flexDirection: 'column', gap: 6, marginBottom: 10 }}>
            <span style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
              {t('ssoSamlMetadataLabel')}
            </span>
            <input value={samlMetadataUrl} onChange={e => setSamlMetadataUrl(e.target.value)}
              placeholder="https://login.microsoftonline.com/{tenant}/federationmetadata/2007-06/federationmetadata.xml"
              style={{
                padding: '10px 12px', fontSize: 12,
                border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
                background: 'var(--surface-1)',
              }} />
          </label>
          <label style={{ display: 'flex', flexDirection: 'column', gap: 6, marginBottom: 10 }}>
            <span style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
              {t('ssoSamlIssuerLabel')}
            </span>
            <input value={samlIssuer} onChange={e => setSamlIssuer(e.target.value)}
              placeholder="https://sts.windows.net/{tenant-id}/"
              className="mono"
              style={{
                padding: '10px 12px', fontSize: 12,
                border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
                background: 'var(--surface-1)', fontFamily: 'var(--font-mono)',
              }} />
          </label>
          <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 12 }}>
            {t('ssoSamlHint')}
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            <Button variant="primary" Icon={Power} onClick={saveSaml}>
              {saml.configured ? t('ssoUpdateBtn') : t('ssoConnectBtn')}
            </Button>
            {saml.configured && (
              <Button variant="ghost" Icon={AlertOctagon} onClick={() => disable('SAML')}>
                {t('ssoDisableBtn')}
              </Button>
            )}
          </div>
        </Card>
      </div>

      {/* Transaction log */}
      <div style={{ marginTop: 16 }}>
        <Card title={t('ssoTxLogTitle')} Icon={Smartphone}
          action={<Badge tone="primary">{transactions.length}</Badge>}>
          <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
            {t('ssoTxLogSub')}
          </div>
          {transactions.length === 0
            ? <div style={{ padding: 16, fontSize: 12, color: 'var(--ink-3)', textAlign: 'center', background: 'var(--surface-2)', borderRadius: 'var(--r-sm)' }}>
                {t('ssoTxLogEmpty')}
              </div>
            : <div style={{ display: 'flex', flexDirection: 'column' }}>
                {transactions.slice(0, 10).map(tx => (
                  <div key={tx.id} style={{
                    padding: '10px 0', borderBottom: '1px dashed var(--border)',
                    display: 'grid', gridTemplateColumns: '1fr auto auto auto', gap: 10, alignItems: 'center',
                  }}>
                    <div className="mono" style={{ fontSize: 11 }}>{tx.id}</div>
                    <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>••••••{tx.nationalId.slice(-4)}</span>
                    <Badge tone="primary">#{tx.randomNumber}</Badge>
                    <Badge tone={STATUS_TONE[tx.status]}>{lang === 'ar' ? STATUS_LABEL[tx.status].ar : STATUS_LABEL[tx.status].en}</Badge>
                  </div>
                ))}
              </div>
          }
        </Card>
      </div>
    </Shell>
  );
}
