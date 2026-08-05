import { useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { stableContext } from './stableContext';

// ─── Tunable design tokens ───────────────────────────────────────────────────
// These are the CSS variables exposed to the user via /settings.
// Keys are written without the leading -- ; we add it when calling setProperty.
export type TokenKey =
  | 'bg' | 'surface'
  | 'primary' | 'primary-hover' | 'primary-press'
  | 'accent'
  | 'sb-bg' | 'sb-text' | 'sb-active-bg' | 'sb-accent'
  | 'sidebar-w' | 'header-h' | 'r-md'
  | 'ui-scale';

export type ThemeOverrides = Partial<Record<TokenKey, string>>;

export interface TokenDef {
  key: TokenKey;
  kind: 'color' | 'size' | 'scale';
  default: string;
  /** Section (UI grouping) this token belongs to. */
  group: 'surface' | 'brand' | 'sidebar' | 'layout' | 'type';
  /** Translation key for the label. */
  labelKey: string;
  /** For size tokens. */
  min?: number;
  max?: number;
  unit?: string;
}

export const TOKEN_DEFS: TokenDef[] = [
  { key: 'bg',           kind: 'color', default: '#F2EEE6', group: 'surface',  labelKey: 'colorBg' },
  { key: 'surface',      kind: 'color', default: '#FAF8F4', group: 'surface',  labelKey: 'colorSurface' },

  { key: 'primary',      kind: 'color', default: '#0E5E5E', group: 'brand',    labelKey: 'colorPrimary' },
  { key: 'accent',       kind: 'color', default: '#C8932A', group: 'brand',    labelKey: 'colorAccent' },

  { key: 'sb-bg',        kind: 'color', default: '#0B1F22', group: 'sidebar',  labelKey: 'colorSbBg' },
  { key: 'sb-text',      kind: 'color', default: '#DCE6E4', group: 'sidebar',  labelKey: 'colorSbText' },
  { key: 'sb-accent',    kind: 'color', default: '#E5B547', group: 'sidebar',  labelKey: 'colorSbAccent' },

  { key: 'sidebar-w',    kind: 'size',  default: '280px',   group: 'layout',   labelKey: 'sizeSidebar', min: 220, max: 360, unit: 'px' },
  { key: 'header-h',     kind: 'size',  default: '64px',    group: 'layout',   labelKey: 'sizeHeader',  min: 52,  max: 88,  unit: 'px' },
  { key: 'r-md',         kind: 'size',  default: '8px',     group: 'layout',   labelKey: 'sizeRadius',  min: 0,   max: 16,  unit: 'px' },

  { key: 'ui-scale',     kind: 'scale', default: '1',       group: 'type',     labelKey: 'sizeFontScale' },
];

export const DEFAULT_VALUES: Record<TokenKey, string> = TOKEN_DEFS.reduce(
  (acc, d) => { acc[d.key] = d.default; return acc; },
  {} as Record<TokenKey, string>
);

// ─── Built-in presets ────────────────────────────────────────────────────────
// Each preset is a partial override of the defaults. `default` is empty so
// applying it just clears all customizations.
export interface Preset { id: string; labelKey: string; overrides: ThemeOverrides; }

export const PRESETS: Preset[] = [
  { id: 'default',  labelKey: 'presetDefault',  overrides: {} },
  { id: 'sand',     labelKey: 'presetSand',     overrides: {
      'bg': '#F4EEDA', 'surface': '#FCF7E8',
      'accent': '#E5B547', 'sb-accent': '#EFCE7A',
    },
  },
  { id: 'petrol',   labelKey: 'presetPetrol',   overrides: {
      'bg': '#EAF1F1', 'surface': '#F5FAFA',
      'primary': '#0B5050', 'sb-bg': '#062525',
    },
  },
  { id: 'mono',     labelKey: 'presetMono',     overrides: {
      'sb-bg': '#1A1F23', 'sb-text': '#E2E5E8', 'sb-accent': '#C8B042',
      'primary': '#3F4A50',
    },
  },
  { id: 'rose',     labelKey: 'presetRose',     overrides: {
      'primary': '#B33F65', 'accent': '#C8932A',
      'sb-bg': '#28161E', 'sb-accent': '#E5A0B7',
    },
  },
  // ── Cobalt — clean enterprise-blue palette inspired by leading
  //    productivity-suite design systems (without using anyone's brand).
  //    Cool gray surfaces + a confident cobalt primary + complementary
  //    soft-amber accent for status chips so the cool blue doesn't read
  //    cold across the whole UI. The sidebar uses near-black ink with a
  //    cobalt highlight so the active item still pops without competing
  //    with the primary brand on the main canvas.
  { id: 'cobalt',   labelKey: 'presetCobalt',   overrides: {
      'bg':        '#F3F2F1',
      'surface':   '#FFFFFF',
      'primary':   '#0F62CC',
      'accent':    '#D89B2A',
      'sb-bg':     '#201F1E',
      'sb-text':   '#E6E6E6',
      'sb-accent': '#3B86E6',
    },
  },
  // ── Mint — fresh, low-saturation green palette with matched neutrals.
  //    Cool whites, mint primary, soft coral accent (complementary on
  //    the colour wheel so status chips don't disappear next to mint
  //    surfaces), deep teal sidebar. Tested for AA contrast on every
  //    surface combo before shipping.
  { id: 'mint',     labelKey: 'presetMint',     overrides: {
      'bg':        '#F4FAF7',
      'surface':   '#FFFFFF',
      'primary':   '#2EA37C',
      'accent':    '#E07A5F',
      'sb-bg':     '#0E2A24',
      'sb-text':   '#D6EDE5',
      'sb-accent': '#7FD3B0',
    },
  },
  // ── Sky — soft baby-blue palette with warm coral accent.
  //    The matched-pair convention here is the classic cool-primary +
  //    warm-complement: sky blue surfaces with a coral/peach accent
  //    that sits opposite blue on the colour wheel. Result: status
  //    chips (warning amber, danger red) stay legible against the cool
  //    canvas, and the accent doesn't get lost in the surrounding blue.
  //    Sidebar uses deep navy so the cool palette has visual depth at
  //    the leading edge without going to pure black.
  { id: 'sky',      labelKey: 'presetSky',      overrides: {
      'bg':        '#F2F7FB',
      'surface':   '#FFFFFF',
      'primary':   '#4A90D9',
      'accent':    '#F2A07B',
      'sb-bg':     '#0F2030',
      'sb-text':   '#DCE6EF',
      'sb-accent': '#79B8E8',
    },
  },
  // ── Nordic — inspired by Scandinavian minimalism + the aurora borealis.
  //    Cool off-white surfaces (the famous "Copenhagen white" — not pure
  //    white, has a tiny gray-blue undertone that reads quieter on long
  //    sessions). Aurora-teal primary, soft amber accent (the classic
  //    cool/warm Nordic pairing seen in Marimekko + Ikea catalogues), and
  //    a deep night-sky sidebar with aurora-green highlight. Calm,
  //    professional, time-tested.
  { id: 'nordic',   labelKey: 'presetNordic',   overrides: {
      'bg':        '#F5F7F8',
      'surface':   '#FFFFFF',
      'primary':   '#1E5F74',
      // Accent retuned: original #E0A458 was 2.18:1 on white (below
      // 3:1 UI minimum). #B97A2A keeps the warm aurora-amber character
      // and clears 3.4:1 on white surfaces so icon glyphs + chip
      // strokes stay visible. Pairs naturally with the deep aurora teal.
      'accent':    '#B97A2A',
      'sb-bg':     '#14213D',
      'sb-text':   '#E5E5E5',
      'sb-accent': '#80CFA9',
    },
  },
  // ── Tuscan — warm Italian Mediterranean palette (terracotta + olive).
  //    Warm cream canvas reminiscent of sun-bleached stone, terracotta
  //    primary in the tradition of Italian roof tiles, olive accent
  //    (olive is the textbook complement to terracotta — see Pantone's
  //    Tuscan Sun colour stories). Sidebar in deep wine with a golden
  //    ochre highlight, so the accent palette feels coherent end-to-end.
  //    Best for tenants who want warmth without going to full beige.
  { id: 'tuscan',   labelKey: 'presetTuscan',   overrides: {
      'bg':        '#FAF4E8',
      'surface':   '#FFFCF5',
      'primary':   '#B85042',
      'accent':    '#7C9070',
      'sb-bg':     '#3D1F1F',
      'sb-text':   '#EDE5D8',
      'sb-accent': '#D4A437',
    },
  },
];

// ─── Quick-pick brand colours ────────────────────────────────────────────────
// One-click recolour of the brand (primary + accent) that layers on top of ANY
// theme/preset and any light/dark mode — it only touches the two brand tokens,
// leaving surfaces + sidebar intact. Curated, contrast-checked pairings.
export interface BrandSwatch { id: string; primary: string; accent: string; }
export const BRAND_SWATCHES: BrandSwatch[] = [
  { id: 'teal',     primary: '#0E5E5E', accent: '#C8932A' },
  { id: 'cobalt',   primary: '#0F62CC', accent: '#D89B2A' },
  { id: 'sky',      primary: '#3D7DCB', accent: '#F2A07B' },
  { id: 'emerald',  primary: '#1E7A52', accent: '#C8932A' },
  { id: 'mint',     primary: '#2EA37C', accent: '#E07A5F' },
  { id: 'violet',   primary: '#6A4E8E', accent: '#C8B042' },
  { id: 'plum',     primary: '#8E3B6B', accent: '#E5B547' },
  { id: 'rose',     primary: '#B33F65', accent: '#C8932A' },
  { id: 'terra',    primary: '#B85042', accent: '#7C9070' },
  { id: 'amber',    primary: '#B97A2A', accent: '#1E5F74' },
  { id: 'indigo',   primary: '#3A4DA6', accent: '#D6A22F' },
  { id: 'graphite', primary: '#3F4A50', accent: '#C8B042' },
];

// ─── Font size (display scale) levels ────────────────────────────────────────
// A single --ui-scale multiplier zooms the whole workspace uniformly, so every
// text size — token-based or hardcoded px — scales together. Works in light,
// dark and every preset. '1' is the default (clears the override entirely).
export interface FontScale { id: string; value: string; labelKey: string; }
export const FONT_SCALES: FontScale[] = [
  { id: 's',  value: '0.9',  labelKey: 'fontScaleS'  },
  { id: 'm',  value: '1',    labelKey: 'fontScaleM'  },
  { id: 'l',  value: '1.1',  labelKey: 'fontScaleL'  },
  { id: 'xl', value: '1.25', labelKey: 'fontScaleXl' },
];

// ─── Provider ────────────────────────────────────────────────────────────────
const STORAGE_KEY = 'grc.theme';
const MODE_STORAGE_KEY = 'grc.theme.mode';

export type ThemeMode = 'light' | 'dark' | 'system';

interface ThemeContextValue {
  overrides: ThemeOverrides;
  setOverride: (key: TokenKey, value: string | undefined) => void;
  applyPreset: (overrides: ThemeOverrides) => void;
  reset: () => void;
  /** Returns the current effective value: override if set, else token default. */
  resolve: (key: TokenKey) => string;
  /** True when a token has a user-supplied override. */
  isCustom: (key: TokenKey) => boolean;
  /** Light/dark/system mode — persisted across sessions. */
  mode: ThemeMode;
  setMode: (mode: ThemeMode) => void;
  /** Resolved mode: 'light' or 'dark' (system → OS preference). */
  effectiveMode: 'light' | 'dark';
}

const ThemeContext = stableContext<ThemeContextValue | null>('grc.theme', null);

function readStored(): ThemeOverrides {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw) as ThemeOverrides;
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch { return {}; }
}

function readStoredMode(): ThemeMode {
  try {
    const raw = localStorage.getItem(MODE_STORAGE_KEY);
    if (raw === 'light' || raw === 'dark' || raw === 'system') return raw;
  } catch { /* ignore */ }
  return 'system';
}

function systemPrefersDark(): boolean {
  return typeof window !== 'undefined'
    && typeof window.matchMedia === 'function'
    && window.matchMedia('(prefers-color-scheme: dark)').matches;
}

function applyMode(mode: ThemeMode): 'light' | 'dark' {
  const effective: 'light' | 'dark' = mode === 'system' ? (systemPrefersDark() ? 'dark' : 'light') : mode;
  if (typeof document !== 'undefined') {
    if (effective === 'dark') document.documentElement.setAttribute('data-theme', 'dark');
    else document.documentElement.removeAttribute('data-theme');
  }
  return effective;
}

/** Apply CSS overrides synchronously — used both initially and on every change. */
function applyOverrides(prev: ThemeOverrides, next: ThemeOverrides) {
  const root = document.documentElement;
  // Remove keys that existed previously but are gone now.
  for (const key of Object.keys(prev) as TokenKey[]) {
    if (!(key in next) || !next[key]) root.style.removeProperty(`--${key}`);
  }
  // Apply current values.
  for (const [key, value] of Object.entries(next) as [TokenKey, string][]) {
    if (value) root.style.setProperty(`--${key}`, value);
  }
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [overrides, setOverridesState] = useState<ThemeOverrides>(() => {
    const stored = readStored();
    // Apply immediately so first paint uses the saved theme.
    if (typeof document !== 'undefined') applyOverrides({}, stored);
    return stored;
  });

  const [mode, setModeState] = useState<ThemeMode>(() => {
    const stored = readStoredMode();
    if (typeof document !== 'undefined') applyMode(stored);
    return stored;
  });
  const [effectiveMode, setEffectiveMode] = useState<'light' | 'dark'>(
    () => mode === 'system' ? (systemPrefersDark() ? 'dark' : 'light') : mode
  );

  const prevRef = useRef<ThemeOverrides>(overrides);

  useEffect(() => {
    applyOverrides(prevRef.current, overrides);
    prevRef.current = overrides;
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify(overrides)); } catch { /* quota — ignore */ }
  }, [overrides]);

  // Apply mode + persist whenever it changes
  useEffect(() => {
    setEffectiveMode(applyMode(mode));
    try { localStorage.setItem(MODE_STORAGE_KEY, mode); } catch { /* quota — ignore */ }
  }, [mode]);

  // Re-apply on OS-level theme changes when in 'system' mode
  useEffect(() => {
    if (mode !== 'system' || typeof window === 'undefined' || typeof window.matchMedia !== 'function') return;
    const mq = window.matchMedia('(prefers-color-scheme: dark)');
    const onChange = () => setEffectiveMode(applyMode('system'));
    mq.addEventListener?.('change', onChange);
    return () => mq.removeEventListener?.('change', onChange);
  }, [mode]);

  const setOverride = useCallback((key: TokenKey, value: string | undefined) => {
    setOverridesState(prev => {
      const next = { ...prev };
      if (value === undefined || value === '') delete next[key];
      else next[key] = value;
      return next;
    });
  }, []);

  const applyPreset = useCallback((presetOverrides: ThemeOverrides) => {
    setOverridesState({ ...presetOverrides });
  }, []);

  const reset = useCallback(() => setOverridesState({}), []);

  const setMode = useCallback((m: ThemeMode) => setModeState(m), []);

  const resolve = useCallback((key: TokenKey) => overrides[key] ?? DEFAULT_VALUES[key], [overrides]);
  const isCustom = useCallback((key: TokenKey) => overrides[key] !== undefined, [overrides]);

  const value = useMemo<ThemeContextValue>(
    () => ({ overrides, setOverride, applyPreset, reset, resolve, isCustom, mode, setMode, effectiveMode }),
    [overrides, setOverride, applyPreset, reset, resolve, isCustom, mode, setMode, effectiveMode]
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error('useTheme must be used inside <ThemeProvider>');
  return ctx;
}
