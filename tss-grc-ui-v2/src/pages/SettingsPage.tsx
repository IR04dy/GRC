import { useCallback } from 'react';
import { Palette, Pipette, Sliders, RotateCcw, Check } from 'lucide-react';
import { Shell } from '../components/layout/Shell';
import { PageHeader } from '../components/layout/PageHeader';
import { Card } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { ColorField } from '../components/ui/ColorField';
import { RangeField } from '../components/ui/RangeField';
import { useTheme, TOKEN_DEFS, PRESETS, DEFAULT_VALUES, BRAND_SWATCHES, FONT_SCALES, type Preset, type TokenKey } from '../lib/theme';
import { useI18n } from '../lib/i18n';
import { useToast } from '../lib/toast';
import { failsAaContrast } from '../lib/contrast';
import { cn } from '../lib/cn';

/**
 * UX-007: tokens whose colour sits directly under body text — the page
 * background (`bg`) and the card/identity surface (`surface`), i.e. the
 * #F2EEE6 / #FAF8F4 swatches called out in the finding. A near-ink pick here
 * silently makes `--ink-1` body text unreadable, so these get an inline AA
 * contrast guard. The brand `primary`/`accent` fills are intentionally NOT
 * guarded against `--ink-1`: they carry `--on-primary` (white) text, not body
 * ink, so an ink-1 check would false-positive on the default teal brand.
 */
const CONTRAST_GUARDED: ReadonlySet<TokenKey> = new Set<TokenKey>(['bg', 'surface']);

/** Read the live `--ink-1` body-ink colour so the guard tracks light/dark mode. */
function currentInk(): string {
  if (typeof window === 'undefined') return '#15201D';
  const v = getComputedStyle(document.documentElement).getPropertyValue('--ink-1').trim();
  return v || '#15201D';
}

export function SettingsPage() {
  const { t } = useI18n();
  const toast = useToast();
  const { overrides, setOverride, applyPreset, reset, resolve, isCustom } = useTheme();

  const surfaceTokens = TOKEN_DEFS.filter(d => d.group === 'surface');
  const brandTokens   = TOKEN_DEFS.filter(d => d.group === 'brand');
  const sidebarTokens = TOKEN_DEFS.filter(d => d.group === 'sidebar');
  const layoutTokens  = TOKEN_DEFS.filter(d => d.group === 'layout');

  const totalCustomized = Object.keys(overrides).length;

  // UX-007: per-change Undo. Capture the previous override value before applying
  // the new one and surface a Gmail-style toast with a single-step "Undo", so a
  // user can revert just this change without nuking all customisation via Reset.
  const setOverrideUndoable = useCallback((key: TokenKey, value: string | undefined) => {
    const prev = overrides[key]; // undefined when the token was at its default
    setOverride(key, value);
    toast.undoable(t('settingsColorChanged'), () => setOverride(key, prev));
  }, [overrides, setOverride, toast, t]);

  // Inline AA contrast guard for the guarded colour tokens. Returns the badge
  // copy when the resolved colour fails AA against the live body ink, else
  // undefined (no badge).
  const contrastWarning = useCallback((key: TokenKey) => {
    if (!CONTRAST_GUARDED.has(key)) return undefined;
    return failsAaContrast(resolve(key), currentInk())
      ? { label: t('settingsContrastWarn'), title: t('settingsContrastWarnTitle') }
      : undefined;
  }, [resolve, t]);

  function activePresetId(): string {
    const stamp = JSON.stringify(overrides);
    for (const p of PRESETS) if (JSON.stringify(p.overrides) === stamp) return p.id;
    return ''; // user has a hand-tuned theme, no preset matches
  }
  const activePreset = activePresetId();

  // One-click brand recolour — sets primary + accent together, leaving
  // surfaces/sidebar untouched so it layers cleanly on any preset or mode.
  const applySwatch = (primary: string, accent: string) => {
    setOverride('primary', primary);
    setOverride('accent', accent);
  };
  const eq = (a: string, b: string) => a.toLowerCase() === b.toLowerCase();
  const activeSwatch = BRAND_SWATCHES.find(
    s => eq(resolve('primary'), s.primary) && eq(resolve('accent'), s.accent),
  );
  const fontScale = resolve('ui-scale');

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
          <SectionLabel>{t('settingsQuickColors')}</SectionLabel>
          <div className="swatch-row">
            {BRAND_SWATCHES.map(s => (
              <button
                key={s.id}
                type="button"
                className={cn('brand-swatch', activeSwatch?.id === s.id && 'active')}
                style={{ '--sw-primary': s.primary, '--sw-accent': s.accent } as React.CSSProperties}
                onClick={() => applySwatch(s.primary, s.accent)}
                aria-label={s.id}
                aria-pressed={activeSwatch?.id === s.id}
                title={s.id}
              >
                {activeSwatch?.id === s.id && <Check size={14} strokeWidth={3} />}
              </button>
            ))}
          </div>

          <SectionLabel>{t('secSurface')}</SectionLabel>
          {surfaceTokens.map(d => (
            <ColorField
              key={d.key}
              label={t(d.labelKey, d.key)}
              value={resolve(d.key)}
              onChange={v => setOverrideUndoable(d.key, v)}
              isCustom={isCustom(d.key)}
              onReset={() => setOverrideUndoable(d.key, undefined)}
              warning={contrastWarning(d.key)}
            />
          ))}

          <SectionLabel>{t('secBrand')}</SectionLabel>
          {brandTokens.map(d => (
            <ColorField
              key={d.key}
              label={t(d.labelKey, d.key)}
              value={resolve(d.key)}
              onChange={v => setOverrideUndoable(d.key, v)}
              isCustom={isCustom(d.key)}
              onReset={() => setOverrideUndoable(d.key, undefined)}
              warning={contrastWarning(d.key)}
            />
          ))}

          <SectionLabel>{t('secSidebar')}</SectionLabel>
          {sidebarTokens.map(d => (
            <ColorField
              key={d.key}
              label={t(d.labelKey, d.key)}
              value={resolve(d.key)}
              onChange={v => setOverrideUndoable(d.key, v)}
              isCustom={isCustom(d.key)}
              onReset={() => setOverrideUndoable(d.key, undefined)}
              warning={contrastWarning(d.key)}
            />
          ))}
        </Card>

        <Card title={t('settingsLayout')} Icon={Sliders}>
          <SectionLabel>{t('sizeFontScale')}</SectionLabel>
          <div className="scale-seg" role="group" aria-label={t('sizeFontScale')}>
            {FONT_SCALES.map(s => (
              <button
                key={s.id}
                type="button"
                className={cn('scale-seg-btn', fontScale === s.value && 'active')}
                onClick={() => setOverride('ui-scale', s.value === '1' ? undefined : s.value)}
                aria-pressed={fontScale === s.value}
              >
                <span className="scale-seg-aa" data-size={s.id}>Aa</span>
                <span className="scale-seg-label">{t(s.labelKey, s.id)}</span>
              </button>
            ))}
          </div>

          <SectionLabel>{t('secSizing')}</SectionLabel>
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
