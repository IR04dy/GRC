import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { I18nProvider } from '../../lib/i18n';
import { RowActions, type RowExtraAction } from './RowActions';
import { Check } from 'lucide-react';

/**
 * F2-167 — a fabricated row's buttons must be inert, and say why.
 *
 * <p>`demoFallbackContract.test.ts` already requires a demo substitution to be <em>disclosed</em>.
 * F2-166 satisfied that contract and was still a P2, because disclosure is not the property that
 * matters here: the badge said the rows were fake while these buttons invited the user to act on
 * them, and **the fabricated ids collide with real ones**. Every mock file numbers records from 1
 * and so does every identity column — measured in dev, `iam.users` holds 7 rows at ids 1–8 against
 * `mockAdmin`'s 1–15, `tprm.vendors` 28 at 1–28 against `mockTprm`'s 1–501. Substitution also fires
 * when the list call <em>threw</em>, and on that path the tenant's real records are present, so
 * Delete on "demo user #3" sent a real tenant-scoped delete for real user 3 — with the confirmation
 * dialog showing the fabricated name.
 *
 * <p>⚠ Every test here awaits its first query. `I18nProvider` ends with `if (!dict) return null`
 * and loads `translations/{en,ar}` by dynamic import, so a component test's first synchronous
 * `getByRole` sees `<body><div /></body>` — an empty container, reported as "there are no accessible
 * roles", which reads exactly like a component that rendered nothing of its own.
 *
 * <p>These tests are behavioural rather than static on purpose. ⚠ But be precise about <em>what</em>
 * they prove: the enforced mechanism is the `disabled` attribute, and the `guard()` wrapper in the
 * component is redundant. Mutation testing said so — replacing `guard` with the identity function
 * left all six green, because jsdom (like a browser) will not dispatch a click from a disabled
 * button. The first draft of this comment claimed the opposite, that the callback was the asserted
 * boundary. 🔑 <b>A missed mutation on redundant code is fine; a comment claiming it was covered is
 * not</b> — that is how a green suite starts vouching for a line nothing exercises (F2-159's decoy,
 * in miniature). The pair that does have teeth is *attribute present* + *callback not called*.
 */

const noop = () => {};

function mount(props: Parameters<typeof RowActions>[0]) {
  return render(<I18nProvider><RowActions {...props} /></I18nProvider>);
}

beforeEach(() => {
  // I18nProvider defaults to 'ar' on this Arabic-primary platform.
  localStorage.setItem('grc.lang', 'en');
});

describe('RowActions — enabled', () => {
  it('fires edit, delete and extras', async () => {
    const onEdit = vi.fn(), onDelete = vi.fn(), extra = vi.fn();
    const extras: RowExtraAction[] = [{ Icon: Check, onClick: extra, title: 'Approve' }];
    mount({ onEdit, onDelete, extras });
    fireEvent.click(await screen.findByRole('button', { name: 'Approve' }));
    fireEvent.click(screen.getByRole('button', { name: 'Edit' }));
    fireEvent.click(screen.getByRole('button', { name: 'Delete' }));
    expect(extra).toHaveBeenCalledTimes(1);
    expect(onEdit).toHaveBeenCalledTimes(1);
    expect(onDelete).toHaveBeenCalledTimes(1);
  });
});

describe('RowActions — disabled (the row is fabricated)', () => {
  it('does not call delete, edit or any extra', async () => {
    const onEdit = vi.fn(), onDelete = vi.fn(), extra = vi.fn();
    const extras: RowExtraAction[] = [{ Icon: Check, onClick: extra, title: 'Approve' }];
    mount({ onEdit, onDelete, extras, disabled: true });
    await screen.findByRole('button', { name: 'Approve' });
    for (const name of ['Approve', 'Edit', 'Delete']) {
      fireEvent.click(screen.getByRole('button', { name }));
    }
    // The whole point: no fabricated id reaches a real endpoint.
    expect(onDelete).not.toHaveBeenCalled();
    expect(onEdit).not.toHaveBeenCalled();
    expect(extra).not.toHaveBeenCalled();
  });

  it('marks every button disabled for assistive tech, not just visually', async () => {
    const extras: RowExtraAction[] = [{ Icon: Check, onClick: noop, title: 'Approve' }];
    mount({ onEdit: noop, onDelete: noop, extras, disabled: true });
    await screen.findByRole('button', { name: 'Approve' });
    for (const name of ['Approve', 'Edit', 'Delete']) {
      const btn = screen.getByRole('button', { name });
      expect(btn).toBeDisabled();
      expect(btn.getAttribute('aria-disabled')).toBe('true');
    }
  });

  it('explains why in the tooltip — a greyed button with no reason reads as a broken page', async () => {
    mount({ onDelete: noop, disabled: true });
    expect((await screen.findByRole('button', { name: 'Delete' })).getAttribute('title'))
      .toMatch(/Demo data/i);
  });

  it('leaves the tooltip alone when the row is real', async () => {
    const extras: RowExtraAction[] = [{ Icon: Check, onClick: noop, title: 'Approve' }];
    mount({ onEdit: noop, onDelete: noop, extras });
    expect((await screen.findByRole('button', { name: 'Delete' })).getAttribute('title')).toBe('Delete');
    expect(screen.getByRole('button', { name: 'Approve' }).getAttribute('title')).toBe('Approve');
  });

  it('says it in Arabic when the platform is in Arabic', async () => {
    // The bilingual rule applies to anything a user reads, and this string is the whole
    // explanation for why the button will not work.
    localStorage.setItem('grc.lang', 'ar');
    mount({ onDelete: noop, disabled: true });
    expect((await screen.findByRole('button', { name: 'Delete' })).getAttribute('title'))
      .toMatch(/بيانات توضيحية/);
  });
});
