import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';

// ─── Tunable design tokens ───────────────────────────────────────────────────
// These are the CSS variables exposed to the user via /settings.
// Keys are written without the leading -- ; we add it when calling setProperty.
export type TokenKey =
  | 'bg' | 'surface'
  | 'primary' | 'primary-hover' | 'primary-press'
  | 'accent'
  | 'sb-bg' | 'sb-text' | 'sb-active-bg' | 'sb-accent'
  | 'sidebar-w' | 'header-h' | 'r-md';

export type ThemeOverrides = Partial<Record<TokenKey, string>>;

export interface TokenDef {
  key: TokenKey;
  kind: 'color' | 'size';
  default: string;
  /** Section (UI grouping) this token belongs to. */
  group: 'surface' | 'brand' | 'sidebar' | 'layout';
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
];

// ─── Provider ────────────────────────────────────────────────────────────────
const STORAGE_KEY = 'grc.theme';

interface ThemeContextValue {
  overrides: ThemeOverrides;
  setOverride: (key: TokenKey, value: string | undefined) => void;
  applyPreset: (overrides: ThemeOverrides) => void;
  reset: () => void;
  /** Returns the current effective value: override if set, else token default. */
  resolve: (key: TokenKey) => string;
  /** True when a token has a user-supplied override. */
  isCustom: (key: TokenKey) => boolean;
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

function readStored(): ThemeOverrides {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw) as ThemeOverrides;
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch { return {}; }
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

  const prevRef = useRef<ThemeOverrides>(overrides);

  useEffect(() => {
    applyOverrides(prevRef.current, overrides);
    prevRef.current = overrides;
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify(overrides)); } catch { /* quota — ignore */ }
  }, [overrides]);

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

  const resolve = useCallback((key: TokenKey) => overrides[key] ?? DEFAULT_VALUES[key], [overrides]);
  const isCustom = useCallback((key: TokenKey) => overrides[key] !== undefined, [overrides]);

  const value = useMemo<ThemeContextValue>(
    () => ({ overrides, setOverride, applyPreset, reset, resolve, isCustom }),
    [overrides, setOverride, applyPreset, reset, resolve, isCustom]
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error('useTheme must be used inside <ThemeProvider>');
  return ctx;
}
