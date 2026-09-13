import { useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { stableContext } from './stableContext';

export type Lang = 'en' | 'ar';
export type Dir  = 'ltr' | 'rtl';

type Dictionary = Record<string, string>;
/**
 * Translation key. Loosened from `keyof typeof en` to plain `string` so the full
 * dictionaries no longer need a static (value) import here — that is what keeps
 * them OUT of the entry bundle (PERF-FE-002). `t()` already accepts any string.
 */
export type DictKey = string;

interface I18nContextValue {
  lang: Lang;
  dir: Dir;
  setLang: (lang: Lang) => void;
  toggleLang: () => void;
  /** Returns the localized string for the key, or the fallback / key itself if missing. */
  t: (key: string, fallback?: string) => string;
}

// ⚠ stableContext, not createContext: an HMR update that reloaded this module used to give the
// provider and its consumers two DIFFERENT context objects, so `ToastProvider` — a child of
// `I18nProvider` — threw "useI18n must be used inside <I18nProvider>" on every hot reload. See
// stableContext.ts for the module-timestamp evidence.
const I18nContext = stableContext<I18nContextValue | null>('grc.i18n', null);
const LANG_KEY = 'grc.lang';

// PERF-FE-002: load only the ACTIVE locale's dictionary instead of statically
// importing both (en ~325 KB + ar ~418 KB of source) into the entry bundle. Each
// import() specifier below is static, so Vite emits one separately-cacheable
// chunk per locale and only the selected one is ever fetched. i18n.tsx is the
// sole importer of these modules, so this fully removes the inactive locale from
// the critical path.
const loaders: Record<Lang, () => Promise<Dictionary>> = {
  en: () => import('../translations/en').then(m => m.en as Dictionary),
  ar: () => import('../translations/ar').then(m => m.ar as Dictionary),
};
const dictCache: Partial<Record<Lang, Dictionary>> = {};
const dictPromises: Partial<Record<Lang, Promise<Dictionary>>> = {};

function loadDict(lang: Lang): Promise<Dictionary> {
  if (!dictPromises[lang]) {
    dictPromises[lang] = loaders[lang]()
      .then(d => { dictCache[lang] = d; return d; })
      .catch(err => {
        // A failed chunk fetch must not hang the app on a blank screen — fall
        // back to an empty dictionary so the gate lifts and keys render.
        console.error('[i18n] failed to load dictionary for', lang, err);
        dictCache[lang] = {};
        return {};
      });
  }
  return dictPromises[lang]!;
}

/**
 * The stored language, read straight from `localStorage` with no React involved.
 *
 * ⚠ Exported for `ErrorBoundary`, which is mounted ABOVE this provider (App.tsx) and therefore
 * has no context to read. It needs the same answer this provider seeds itself from, so this is
 * one declaration with two readers — a second copy of the key and of the Arabic-primary default
 * would be free to drift from this one, and nobody would be maintaining the agreement (F2-217).
 */
export function readStoredLang(): Lang {
  // Arabic-primary platform: default first load to 'ar' (RTL) unless the user
  // explicitly chose English in a previous session.
  try {
    return localStorage.getItem(LANG_KEY) === 'en' ? 'en' : 'ar';
  } catch {
    return 'ar';
  }
}

// Kick off the active locale's fetch at module-eval time (in parallel with the
// React bootstrap) so the first-paint gate below waits as little as possible.
loadDict(readStoredLang());

export function I18nProvider({ children }: { children: ReactNode }) {
  const [lang, setLangState] = useState<Lang>(readStoredLang);
  const [dict, setDict] = useState<Dictionary | null>(() => dictCache[lang] ?? null);
  const dir: Dir = lang === 'ar' ? 'rtl' : 'ltr';

  useEffect(() => {
    document.documentElement.lang = lang;
    document.documentElement.dir = dir;
    try { localStorage.setItem(LANG_KEY, lang); } catch { /* storage blocked */ }
  }, [lang, dir]);

  // Load (or swap to) the active locale's dictionary whenever the language changes.
  useEffect(() => {
    if (dictCache[lang]) { setDict(dictCache[lang]!); return; }
    let cancelled = false;
    loadDict(lang).then(d => { if (!cancelled) setDict(d); });
    return () => { cancelled = true; };
  }, [lang]);

  const setLang = useCallback((l: Lang) => setLangState(l), []);
  const toggleLang = useCallback(() => setLangState(l => (l === 'en' ? 'ar' : 'en')), []);

  const t = useCallback((key: string, fallback?: string) => {
    const value = dict ? dict[key] : undefined;
    if (typeof value === 'string') return value;
    return fallback ?? key;
  }, [dict]);

  const value = useMemo(() => ({ lang, dir, setLang, toggleLang, t }), [lang, dir, setLang, toggleLang, t]);

  // PERF-FE-002: gate the first paint until the active dictionary is ready, so the
  // UI never flashes raw keys. On a LANGUAGE SWITCH `dict` still holds the prior
  // locale (it's already loaded), so there is no blank — the new strings swap in
  // when that locale's chunk resolves (instant once it has been cached).
  if (!dict) return null;

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n(): I18nContextValue {
  const ctx = useContext(I18nContext);
  if (!ctx) throw new Error('useI18n must be used inside <I18nProvider>');
  return ctx;
}
