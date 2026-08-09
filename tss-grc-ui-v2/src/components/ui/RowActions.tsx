import { Pencil, Trash2, type LucideIcon } from 'lucide-react';
import { cn } from '../../lib/cn';
import { useI18n } from '../../lib/i18n';

export interface RowExtraAction {
  Icon: LucideIcon;
  onClick: () => void;
  title: string;
  /** "primary" (default), "success" (green), "accent" (gold). */
  tone?: 'primary' | 'success' | 'accent';
}

interface RowActionsProps {
  onEdit?: () => void;
  onDelete?: () => void;
  /** Extra workflow actions placed before edit/delete (e.g. Approve, Escalate, Complete). */
  extras?: RowExtraAction[];
  /**
   * F2-167 — the row this acts on is fabricated, so every button here must be inert.
   *
   * Pass the page's demo flag (`isDemo` from `useApiList`/`useApiResource`, or the hand-rolled
   * `showingDemo`). The platform substitutes demo rows on two paths — the list call **threw**, or it
   * succeeded and returned **zero rows** — and `demoFallbackContract.test.ts` already requires that
   * substitution to be disclosed with a badge. Disclosure was never enough here: the badge said the
   * data was fake while these buttons invited the user to act on it, and **the fabricated ids
   * collide with real ones**. Every mock file numbers its records from 1, and so does every identity
   * column: measured in dev, `iam.users` holds 7 rows at ids 1–8 against `mockAdmin`'s 1–15,
   * `tprm.vendors` 28 rows at 1–28 against `mockTprm`'s 1–501, `compliance.controls` 35 at 1–35.
   * On the *throw* path a tenant's real records are present, so `onDelete` on "demo user #3" sent a
   * real, tenant-scoped delete for **real user 3** — and the confirmation dialog showed the
   * fabricated name, confirming the wrong record.
   */
  disabled?: boolean;
}

/**
 * Inline row buttons. Workflow actions (extras) come first, then edit/delete.
 * The wrapper has 0.55 opacity by default and lifts to full on row hover.
 */
export function RowActions({ onEdit, onDelete, extras, disabled }: RowActionsProps) {
  const { lang } = useI18n();
  // Named rather than silent: a greyed button with no explanation reads as a broken page.
  const why = disabled
    ? (lang === 'ar'
        ? 'بيانات توضيحية — لا يمكن تنفيذ إجراء على سجل غير حقيقي'
        : 'Demo data — no action can be taken on a record that is not real')
    : undefined;
  /**
   * Never hand a fabricated id to a real endpoint.
   *
   * ⚠ Belt-and-braces, and honestly labelled as such: the enforced mechanism is the `disabled`
   * attribute below, which is what `RowActions.test.tsx` proves. Mutation testing showed this
   * wrapper is **not** independently observable — removing it leaves every test green, because
   * jsdom (like a browser) refuses to dispatch a click from a disabled button. So it is a second
   * line for the day someone renders one of these branches without `disabled`; it is not the
   * control. Do not read the passing tests as evidence that this line works.
   */
  const guard = (fn: () => void) => (disabled ? () => {} : fn);

  return (
    <div className="row-actions" onClick={e => e.stopPropagation()}>
      {extras?.map((a, i) => (
        <button
          key={i}
          type="button"
          className={cn('row-action-btn', a.tone === 'success' && 'success', a.tone === 'accent' && 'accent')}
          onClick={guard(a.onClick)}
          disabled={disabled}
          aria-disabled={disabled || undefined}
          aria-label={a.title}
          title={why ?? a.title}
        >
          <a.Icon size={14} />
        </button>
      ))}
      {onEdit && (
        <button type="button" className="row-action-btn" onClick={guard(onEdit)}
          disabled={disabled} aria-disabled={disabled || undefined}
          aria-label="Edit" title={why ?? 'Edit'}>
          <Pencil size={14} />
        </button>
      )}
      {onDelete && (
        <button type="button" className={cn('row-action-btn', 'danger')} onClick={guard(onDelete)}
          disabled={disabled} aria-disabled={disabled || undefined}
          aria-label="Delete" title={why ?? 'Delete'}>
          <Trash2 size={14} />
        </button>
      )}
    </div>
  );
}
