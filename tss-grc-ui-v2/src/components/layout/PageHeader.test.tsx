import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { I18nProvider } from '../../lib/i18n';
import { PageHeader } from './PageHeader';

/**
 * JRN-13 — the denial has to reach the screen, not only the hook.
 *
 * <p>`useApiList` set `error` on the 403 path since the day it was written, and measured across the
 * tree **0 of the 9 real consumers read it**. A flag nothing renders is the defect, not the fix, so
 * the static guard in {@code __tests__/demoFallbackContract.test.ts} (every consumer passes
 * `denied`) needs this at the other end: the prop it forces pages to pass must actually put a
 * sentence on screen.
 *
 * <p>The negative assertion is the load-bearing one. What a denied analyst saw was not *nothing* —
 * it was *"Demo mode · using sample data"* over a full register of records that do not exist. So it
 * is not enough that a banner appears; it must not read as the demo badge it replaces.
 *
 * <p>⚠ Every query here is `findBy`, not `getBy`. The dictionary is a separately-fetched chunk
 * (PERF-FE-002) so `I18nProvider` renders nothing on the first tick — a `getByRole` written against
 * it fails on the header's own `<h2>`, which is how the first draft of this file reported the
 * denial banner missing when what was missing was the whole subtree.
 */
function mount(props: Partial<Parameters<typeof PageHeader>[0]> = {}) {
  return render(
    <MemoryRouter>
      <I18nProvider>
        <PageHeader title="Cyber" {...props} />
      </I18nProvider>
    </MemoryRouter>,
  );
}

describe('PageHeader — the denial banner (JRN-13)', () => {
  // ⚠ The locale is part of the fixture, not of the environment: `I18nProvider` defaults to 'ar'
  // on this Arabic-primary platform, so an English assertion without this line tests nothing.
  beforeEach(() => { localStorage.setItem('grc.lang', 'en'); });

  it('says nothing when nothing was denied', async () => {
    mount();
    // Anti-vacuity: prove the header rendered before concluding the banner did not.
    expect(await screen.findByRole('heading', { name: 'Cyber' })).toBeTruthy();
    expect(screen.queryByRole('status')).toBeNull();
  });

  it('names the denial — and does not call it demo data', async () => {
    mount({ denied: true });
    const el = await screen.findByRole('status');
    expect(el).toHaveTextContent(/do not have permission/i);
    // The sentence that explains the empty table. Without it the fix trades a plausible lie for a
    // silent one, which F2-169 named as "the same lie in a quieter voice".
    expect(el).toHaveTextContent(/not because the register is empty/i);
    expect(el.textContent ?? '').not.toMatch(/demo|sample data/i);
  });

  it('renders in Arabic, like everything a user reads', async () => {
    localStorage.setItem('grc.lang', 'ar');
    mount({ denied: true });
    expect(await screen.findByRole('status')).toHaveTextContent('ليس لديك صلاحية لعرض هذا السجل');
  });

  it('leaves the header itself intact', async () => {
    mount({ denied: true, eyebrow: 'NCA ECC', actions: <button>Export</button> });
    expect(await screen.findByRole('heading', { name: 'Cyber' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Export' })).toBeTruthy();
    expect(screen.getByText('NCA ECC')).toBeTruthy();
  });
});
