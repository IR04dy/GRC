import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { en } from '../translations/en';
import { ar } from '../translations/ar';

export type Lang = 'en' | 'ar';
export type Dir  = 'ltr' | 'rtl';

// `as const` on en/ar gives them divergent literal types, so we widen for the dictionaries map.
type Dictionary = Record<string, string>;
export type DictKey = keyof typeof en;

interface I18nContextValue {
  lang: Lang;
  dir: Dir;
  setLang: (lang: Lang) => void;
  toggleLang: () => void;
  /** Returns the localized string for the key, or the fallback / key itself if missing. */
  t: (key: string, fallback?: string) => string;
}

const I18nContext = createContext<I18nContextValue | null>(null);
const LANG_KEY = 'grc.lang';

const dictionaries: Record<Lang, Dictionary> = { en: en as Dictionary, ar: ar as Dictionary };

export function I18nProvider({ children }: { children: ReactNode }) {
  const [lang, setLangState] = useState<Lang>(() => {
    // Arabic-primary platform: default first load to 'ar' (RTL) unless the
    // user has explicitly chosen English in a previous session.
    const stored = localStorage.getItem(LANG_KEY) as Lang | null;
    return stored === 'en' ? 'en' : 'ar';
  });
  const dir: Dir = lang === 'ar' ? 'rtl' : 'ltr';

  useEffect(() => {
    document.documentElement.lang = lang;
    document.documentElement.dir = dir;
    localStorage.setItem(LANG_KEY, lang);
  }, [lang, dir]);

  const setLang = useCallback((l: Lang) => setLangState(l), []);
  const toggleLang = useCallback(() => setLangState(l => (l === 'en' ? 'ar' : 'en')), []);

  const t = useCallback((key: string, fallback?: string) => {
    const value = dictionaries[lang][key];
    if (typeof value === 'string') return value;
    return fallback ?? key;
  }, [lang]);

  const value = useMemo(() => ({ lang, dir, setLang, toggleLang, t }), [lang, dir, setLang, toggleLang, t]);
  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n(): I18nContextValue {
  const ctx = useContext(I18nContext);
  if (!ctx) throw new Error('useI18n must be used inside <I18nProvider>');
  return ctx;
}
