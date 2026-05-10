import { Palette, Pipette, Sliders, RotateCcw, Check } from 'lucide-react';
import { Shell } from '../components/layout/Shell';
import { PageHeader } from '../components/layout/PageHeader';
import { Card } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { ColorField } from '../components/ui/ColorField';
import { RangeField } from '../components/ui/RangeField';
import { useTheme, TOKEN_DEFS, PRESETS, DEFAULT_VALUES, type Preset, type TokenKey } from '../lib/theme';
import { useI18n } from '../lib/i18n';
import { cn } from '../lib/cn';

export function SettingsPage() {
  const { t } = useI18n();
  const { overrides, setOverride, applyPreset, reset, resolve, isCustom } = useTheme();

  const surfaceTokens = TOKEN_DEFS.filter(d => d.group === 'surface');
  const brandTokens   = TOKEN_DEFS.filter(d => d.group === 'brand');
  const sidebarTokens = TOKEN_DEFS.filter(d => d.group === 'sidebar');
  const layoutTokens  = TOKEN_DEFS.filter(d => d.group === 'layout');

  const totalCustomized = Object.keys(overrides).length;

  function activePresetId(): string {
    const stamp = JSON.stringify(overrides);
    for (const p of PRESETS) if (JSON.stringify(p.overrides) === stamp) return p.id;
    return ''; // user has a hand-tuned theme, no preset matches
  }
  const activePreset = activePresetId();

  return (
    <Shell title={t('itemSettings')} sub={t('settingsSub')}>
      <PageHeader
        eyebrow={t('settingsEyebrow')}
        title={t('settingsTitle')}
        sub={t('settingsSub')}
        actions={
          <Button
            variant="secondary"
            Icon={RotateCcw}
            onClick={reset}
            disabled={totalCustomized === 0}
          >
            {t('settingsReset')}
          </Button>
        }
      />

      <Card title={t('settingsPresets')} Icon={Palette}>
        <div className="presets-grid">
          {PRESETS.map(p => (
            <PresetCard
              key={p.id}
              preset={p}
              label={t(p.labelKey, p.id)}
              active={p.id === activePreset}
              onClick={() => applyPreset(p.overrides)}
            />
          ))}
        </div>
      </Card>

      <div style={{ height: 16 }} />

      <div className="dash-grid">
        <Card title={t('settingsColors')} Icon={Pipette}>
          <SectionLabel>{t('secSurface')}</SectionLabel>
          {surfaceTokens.map(d => (
            <ColorField
              key={d.key}
              label={t(d.labelKey, d.key)}
              value={resolve(d.key)}
              onChange={v => setOverride(d.key, v)}
              isCustom={isCustom(d.key)}
              onReset={() => setOverride(d.key, undefined)}
            />
          ))}

          <SectionLabel>{t('secBrand')}</SectionLabel>
          {brandTokens.map(d => (
            <ColorField
              key={d.key}
              label={t(d.labelKey, d.key)}
              value={resolve(d.key)}
              onChange={v => setOverride(d.key, v)}
              isCustom={isCustom(d.key)}
              onReset={() => setOverride(d.key, undefined)}
            />
          ))}

          <SectionLabel>{t('secSidebar')}</SectionLabel>
          {sidebarTokens.map(d => (
            <ColorField
              key={d.key}
              label={t(d.labelKey, d.key)}
              value={resolve(d.key)}
              onChange={v => setOverride(d.key, v)}
              isCustom={isCustom(d.key)}
              onReset={() => setOverride(d.key, undefined)}
            />
          ))}
        </Card>

        <Card title={t('settingsLayout')} Icon={Sliders}>
          {layoutTokens.map(d => (
            <RangeField
              key={d.key}
              label={t(d.labelKey, d.key)}
              value={resolve(d.key)}
              min={d.min ?? 0}
              max={d.max ?? 100}
              unit={d.unit ?? 'px'}
              onChange={v => setOverride(d.key, v)}
              isCustom={isCustom(d.key)}
              onReset={() => setOverride(d.key, undefined)}
            />
          ))}
          <div className="settings-hint">{t('settingsHint')}</div>
        </Card>
      </div>
    </Shell>
  );
}

function SectionLabel({ children }: { children: React.ReactNode }) {
  return <div className="cf-section">{children}</div>;
}

function PresetCard({
  preset, label, active, onClick,
}: {
  preset: Preset;
  label: string;
  active: boolean;
  onClick: () => void;
}) {
  // The preset card shows a sample of how the theme would look:
  // canvas → surface → primary → accent swatch row + sidebar bg strip.
  const sample: Record<TokenKey, string> = { ...DEFAULT_VALUES, ...preset.overrides };

  return (
    <button type="button" onClick={onClick} className={cn('preset-card', active && 'active')}>
      <div className="preset-name">
        {label}
        {active && <Check size={12} color="var(--primary)" />}
      </div>
      <div className="preset-swatches">
        <span className="preset-swatch" style={{ background: sample['bg'] }} />
        <span className="preset-swatch" style={{ background: sample['surface'] }} />
        <span className="preset-swatch" style={{ background: sample['primary'] }} />
        <span className="preset-swatch" style={{ background: sample['accent'] }} />
      </div>
      <div className="preset-sidebar-strip" style={{ background: sample['sb-bg'] }}>
        <span className="preset-sidebar-dot" style={{ background: sample['sb-accent'] }} />
      </div>
    </button>
  );
}
