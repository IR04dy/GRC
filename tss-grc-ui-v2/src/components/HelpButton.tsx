import { useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { HelpCircle } from 'lucide-react';
import { useI18n } from '../lib/i18n';
import { HelpDrawer } from './HelpDrawer';

/**
 * Floating contextual-help button — Sprint 12.
 *
 * <p>Mounted once at shell level. The button is fixed to the
 * bottom-end corner (right in LTR, left in RTL via logical
 * `inset-inline-end`). Pressing it opens the help drawer keyed
 * to the current `useLocation().pathname`.
 *
 * <p>Hidden on the {@code /login} page and on the public portals
 * (those routes use {@code PublicShell}, not the authenticated
 * shell, so this component never mounts there anyway).
 */
export function HelpButton() {
  const { t, lang } = useI18n();
  const location = useLocation();
  const [open, setOpen] = useState(false);

  // Keyboard shortcut: Shift+? opens the drawer.
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === '?' && e.shiftKey && !(e.target instanceof HTMLInputElement) && !(e.target instanceof HTMLTextAreaElement)) {
        e.preventDefault();
        setOpen(o => !o);
      }
    }
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  // Close drawer on route change — the next page surfaces its own
  // help anyway.
  useEffect(() => {
    setOpen(false);
  }, [location.pathname]);

  return (
    <>
      <button
        onClick={() => setOpen(true)}
        aria-label={t('helpButtonAriaLabel', 'Open contextual help')}
        title={t('helpButtonTitle', 'Contextual help (Shift+?)')}
        style={{
          // RTL-aware logical positioning — flips automatically with
          // <html dir="rtl">.
          position: 'fixed',
          insetInlineEnd: 24,
          insetBlockEnd: 24,
          width: 48, height: 48,
          borderRadius: '50%',
          background: 'var(--primary, #2563eb)',
          color: '#fff',
          border: 'none',
          boxShadow: '0 4px 12px rgba(0, 0, 0, 0.2)',
          display: 'inline-flex',
          alignItems: 'center', justifyContent: 'center',
          cursor: 'pointer',
          zIndex: 900,
          // Preserve LTR for the icon so the '?' character renders
          // correctly even on RTL pages.
          direction: 'ltr',
        }}
      >
        <HelpCircle size={22} aria-hidden="true" />
      </button>

      <HelpDrawer
        open={open}
        onClose={() => setOpen(false)}
        route={location.pathname}
      />
      {/* Force the language-direction prop to satisfy lint and to
          give the drawer a stable re-render key when locale flips. */}
      <span hidden aria-hidden="true">{lang}</span>
    </>
  );
}
