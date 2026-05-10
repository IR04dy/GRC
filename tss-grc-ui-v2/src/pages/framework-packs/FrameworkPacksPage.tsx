import { useEffect, useState } from 'react';
import {
  Package, Download, Trash2, Check, Layers, Files,
  ShieldCheck, Building2, ChevronDown, ChevronUp, type LucideIcon,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  FRAMEWORK_PACKS, loadInstalled, installPack, uninstallPack,
  subscribeFrameworkPacks, packStats,
  type FrameworkPack,
} from '../../lib/frameworkPacks';

/**
 * FrameworkPacksPage — Track D1 (content moat).
 *
 * Browseable catalog of regulator-blessed framework packs that ship with TSS
 * GRC. Each pack carries domains + controls + evidence templates. Installing
 * a pack lights it up across the platform (controls library, regulator
 * pivot view, board pack KPIs, onboarding wizard pre-checks).
 */
export function FrameworkPacksPage() {
  const { t } = useI18n();
  const toast = useToast();
  const [installed, setInstalled] = useState(() => loadInstalled());
  useEffect(() => subscribeFrameworkPacks(() => setInstalled(loadInstalled())), []);

  const [openPack, setOpenPack] = useState<string | null>(null);

  const stats = packStats();

  function install(code: string) {
    installPack(code);
    const pack = FRAMEWORK_PACKS.find(p => p.code === code);
    toast.success(`${t('fpInstalledToast')} — ${pack?.code}`);
  }
  function uninstall(code: string) {
    if (!confirm(t('fpUninstallConfirm'))) return;
    uninstallPack(code);
    toast.info(t('fpUninstalledToast'));
  }

  return (
    <Shell title={t('fpTitle')} sub={t('fpSub')}>
      <PageHeader
        eyebrow={t('fpEyebrow')}
        title={t('fpTitle')}
        sub={t('fpPageSub')}
      />

      {/* Stats */}
      <Card>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12 }}>
          <Stat label={t('fpKpiInstalled')}  value={`${stats.installedCount} / ${stats.availableCount}`} Icon={Package} />
          <Stat label={t('fpKpiControls')}   value={String(stats.totalControls)}                          Icon={ShieldCheck} />
          <Stat label={t('fpKpiEvidence')}   value={String(stats.totalEvidence)}                          Icon={Files} />
          <Stat label={t('fpKpiAvailable')}  value={String(stats.availableCount - stats.installedCount)}  Icon={Download} />
        </div>
      </Card>

      <div style={{ marginTop: 16, display: 'grid', gridTemplateColumns: '1fr', gap: 12 }}>
        {FRAMEWORK_PACKS.map(pack => {
          const isOn = installed.some(i => i.code === pack.code);
          const installedAt = installed.find(i => i.code === pack.code)?.installedAt;
          return (
            <PackCard
              key={pack.code}
              pack={pack}
              installed={isOn}
              installedAt={installedAt}
              expanded={openPack === pack.code}
              onToggleExpand={() => setOpenPack(openPack === pack.code ? null : pack.code)}
              onInstall={() => install(pack.code)}
              onUninstall={() => uninstall(pack.code)}
            />
          );
        })}
      </div>
    </Shell>
  );
}

// ─── Stat tile ───────────────────────────────────────────────────────────────

function Stat({ label, value, Icon }: { label: string; value: string; Icon: LucideIcon }) {
  return (
    <div style={{
      display: 'flex', flexDirection: 'column', gap: 4,
      padding: 12, borderRadius: 'var(--r-md)',
      background: 'var(--surface-2)', border: '1px solid var(--border)',
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 6, color: 'var(--ink-3)' }}>
        <Icon size={13} />
        <span style={{ fontSize: 10, textTransform: 'uppercase', letterSpacing: '0.05em' }}>{label}</span>
      </div>
      <div style={{ fontSize: 22, fontWeight: 700, color: 'var(--ink-1)' }}>{value}</div>
    </div>
  );
}

// ─── Pack card ───────────────────────────────────────────────────────────────

function PackCard({ pack, installed, installedAt, expanded, onToggleExpand, onInstall, onUninstall }: {
  pack: FrameworkPack;
  installed: boolean;
  installedAt?: string;
  expanded: boolean;
  onToggleExpand: () => void;
  onInstall: () => void;
  onUninstall: () => void;
}) {
  const { t, lang } = useI18n();
  const mandateTone = pack.mandate === 'mandatory' ? 'danger'
    : pack.mandate === 'recommended' ? 'warning' : 'neutral';
  const mandateKey = `fpMandate_${pack.mandate}`;

  return (
    <Card>
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 14, alignItems: 'flex-start' }}>
        <div style={{ flex: 1 }}>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 6, flexWrap: 'wrap' }}>
            <span style={{ fontSize: 16, fontWeight: 700 }}>
              {lang === 'ar' ? pack.nameAr : pack.nameEn}
            </span>
            <Badge tone="neutral">{pack.regulator}</Badge>
            <Badge tone={mandateTone}>{t(mandateKey)}</Badge>
            <Badge tone="info">v{pack.version}</Badge>
            {installed && <Badge tone="success"><Check size={11} /> {t('fpInstalledBadge')}</Badge>}
          </div>
          <div style={{ fontSize: 12, color: 'var(--ink-2)', marginBottom: 10, lineHeight: 1.6 }}>
            {lang === 'ar' ? pack.blurbAr : pack.blurbEn}
          </div>

          {/* Outcomes */}
          <div style={{ marginBottom: 10 }}>
            <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: 4 }}>
              {t('fpOutcomes')}
            </div>
            <ul style={{ margin: 0, paddingInlineStart: 18, fontSize: 12, color: 'var(--ink-2)', lineHeight: 1.7 }}>
              {(lang === 'ar' ? pack.outcomesAr : pack.outcomesEn).map((o, i) => (
                <li key={i}>{o}</li>
              ))}
            </ul>
          </div>

          {/* Bundle counts */}
          <div style={{ display: 'flex', gap: 14, fontSize: 11, color: 'var(--ink-3)' }}>
            <span><Layers size={11} style={{ display: 'inline-block', verticalAlign: 'middle', marginInlineEnd: 4 }} />
              {pack.domains.length} {t('fpDomains')}</span>
            <span><ShieldCheck size={11} style={{ display: 'inline-block', verticalAlign: 'middle', marginInlineEnd: 4 }} />
              {pack.controls.length} {t('fpControlsCount')}</span>
            <span><Files size={11} style={{ display: 'inline-block', verticalAlign: 'middle', marginInlineEnd: 4 }} />
              {pack.evidenceTemplates.length} {t('fpEvidenceCount')}</span>
            {installedAt && (
              <span style={{ color: 'var(--success)' }}>
                <Building2 size={11} style={{ display: 'inline-block', verticalAlign: 'middle', marginInlineEnd: 4 }} />
                {t('fpInstalledAt')} {new Date(installedAt).toLocaleDateString()}
              </span>
            )}
          </div>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6, minWidth: 160 }}>
          {installed
            ? <Button variant="danger" Icon={Trash2} onClick={onUninstall}>{t('fpUninstall')}</Button>
            : <Button variant="primary" Icon={Download} onClick={onInstall}>{t('fpInstall')}</Button>}
          <Button variant="secondary" Icon={expanded ? ChevronUp : ChevronDown} onClick={onToggleExpand}>
            {expanded ? t('fpHideContents') : t('fpShowContents')}
          </Button>
        </div>
      </div>

      {expanded && (
        <div style={{ marginTop: 16, paddingTop: 16, borderTop: '1px solid var(--border)' }}>
          {/* Controls table */}
          <div style={{ marginBottom: 16 }}>
            <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 8 }}>
              {t('fpControlsCount')} ({pack.controls.length})
            </div>
            <div style={{ maxHeight: 320, overflow: 'auto', border: '1px solid var(--border)', borderRadius: 'var(--r-sm)' }}>
              <table className="tbl" style={{ width: '100%' }}>
                <thead>
                  <tr>
                    <th style={{ width: 130 }}>{t('fpControlCode')}</th>
                    <th style={{ width: 100 }}>{t('fpControlDomain')}</th>
                    <th>{t('fpControlTitle')}</th>
                    <th style={{ width: 80 }}>{t('fpControlTier')}</th>
                  </tr>
                </thead>
                <tbody>
                  {pack.controls.map(c => {
                    const domain = pack.domains.find(d => d.code === c.domain);
                    return (
                      <tr key={c.code}>
                        <td className="mono" style={{ fontSize: 11 }}>{c.code}</td>
                        <td style={{ fontSize: 11 }}>
                          {domain ? (lang === 'ar' ? domain.nameAr : domain.nameEn) : c.domain}
                        </td>
                        <td>{lang === 'ar' ? c.titleAr : c.titleEn}</td>
                        <td>
                          {c.tier === 1
                            ? <Badge tone="primary">T1</Badge>
                            : <Badge tone="warning">T2</Badge>}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>

          {/* Evidence templates */}
          <div>
            <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 8 }}>
              {t('fpEvidenceCount')} ({pack.evidenceTemplates.length})
            </div>
            <table className="tbl" style={{ width: '100%' }}>
              <thead>
                <tr>
                  <th style={{ width: 130 }}>{t('fpEvCode')}</th>
                  <th>{t('fpEvName')}</th>
                  <th>{t('fpEvCadence')}</th>
                </tr>
              </thead>
              <tbody>
                {pack.evidenceTemplates.map(e => (
                  <tr key={e.code}>
                    <td className="mono" style={{ fontSize: 11 }}>{e.code}</td>
                    <td>{lang === 'ar' ? e.nameAr : e.nameEn}</td>
                    <td>
                      <Badge tone="info">{lang === 'ar' ? e.cadenceAr : e.cadenceEn}</Badge>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </Card>
  );
}
