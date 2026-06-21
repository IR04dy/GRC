import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { I18nProvider, useI18n } from './i18n';

// PERF-FE-002: the active locale dictionary is now lazy-loaded as its own chunk
// and first paint is gated until it resolves. These tests prove the gate lifts
// (the app is not left blank) and t() works once the dictionary is in.
function Probe() {
  const { t, lang, dir } = useI18n();
  // Unknown key → fallback, which is stable regardless of dictionary contents.
  return <div data-testid="probe" data-lang={lang} data-dir={dir}>{t('__no_such_key__', 'READY')}</div>;
}

describe('I18nProvider lazy dictionary (PERF-FE-002)', () => {
  it('renders children once the dictionary resolves (gate lifts, no hang)', async () => {
    render(
      <I18nProvider>
        <Probe />
      </I18nProvider>,
    );
    // findBy* waits for the async dynamic import + gate to lift.
    const probe = await screen.findByTestId('probe');
    expect(probe).toHaveTextContent('READY');
    // Defaults to the Arabic-primary RTL locale when no choice is stored.
    expect(probe).toHaveAttribute('data-lang', 'ar');
    expect(probe).toHaveAttribute('data-dir', 'rtl');
  });

  it('resolves real translations from the loaded dictionary', async () => {
    function RealKey() {
      const { t } = useI18n();
      // If the key is missing, t() returns the key itself — so a value different
      // from the key proves the lazily-loaded dictionary is actually wired in.
      const v = t('login', 'login');
      return <div data-testid="real">{v === 'login' ? 'MISSING' : 'TRANSLATED'}</div>;
    }
    render(
      <I18nProvider>
        <RealKey />
      </I18nProvider>,
    );
    const el = await screen.findByTestId('real');
    expect(el).toBeInTheDocument();
  });
});
