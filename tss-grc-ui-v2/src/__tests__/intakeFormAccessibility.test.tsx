import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { I18nProvider } from '../lib/i18n';
import { ToastProvider } from '../lib/toast';
import { IntakeFormRenderer } from '../components/intake/IntakeFormRenderer';
import type { IntakeFieldDef } from '../lib/intakeForms';
import { stripComments } from './support/sourceScan';

/**
 * JRN-09 — the intake renderer must expose an accessible name per field and real group
 * semantics per radio group.
 *
 * <h3>What was wrong</h3>
 * `IntakeFormRenderer` hand-rolls its controls, so it bypassed every accessible-field mechanism
 * the platform already has. Measured over that file before the fix, all zero: `htmlFor`,
 * `fieldset`, `legend`, `useId`, `role=`, `aria-` of any kind, `aria-required`. The label was a
 * styled `<div>` with no association, and the two radio kinds were `<button>`s whose selected
 * state was carried by colour and font-weight alone.
 *
 * <p>⚠ The population is `IntakeFieldKind`, which has <b>EIGHT</b> members — `date` and
 * `datetime` share one `if` branch, so counting branches gives seven. Five had no accessible name
 * (text, textarea, select, date, datetime), two were groups with no grouping (radio-cards,
 * severity-radio), and `checkbox` was already correct, wrapping its input in a real `<label>` —
 * which is why `htmlFor === 0` did not imply zero label semantics.
 *
 * <h3>Why these legs render rather than scan</h3>
 * `IntakeFormRenderer` is exported, so the accessibility tree is directly observable — and the
 * claim is about that tree, not about the source. A `getByLabelText` passes only if the browser's
 * own association rules are satisfied, which no `contains('htmlFor')` check can establish
 * (F2-274: a leg that exercises a primitive says nothing about whether the component uses it
 * correctly). The one structural leg exists for the opposite reason: the hard zeros are what made
 * this finding legible, so a regression to a styled `<div>` should fail as itself.
 *
 * <h3>Which legs discriminate (F2-225)</h3>
 * Every leg below fires on the pre-fix component. None is a tripwire.
 */

const ROOT = join(process.cwd(), 'src');

/** One field of every kind the union declares, so no branch is unexercised. */
const FIELDS: IntakeFieldDef[] = [
  { key: 'title', kind: 'text', labelEn: 'Short title', labelAr: 'عنوان', required: true },
  { key: 'summary', kind: 'textarea', labelEn: 'Summary', labelAr: 'ملخص', hintEn: 'At least 30 characters.', hintAr: '٣٠ حرفًا على الأقل.' },
  { key: 'team', kind: 'select', labelEn: 'Team', labelAr: 'الفريق', options: [{ value: 'SOC', labelEn: 'SOC', labelAr: 'العمليات' }] },
  { key: 'occurredOn', kind: 'date', labelEn: 'Occurred on', labelAr: 'تاريخ الوقوع' },
  { key: 'detectedAt', kind: 'datetime', labelEn: 'Detected at', labelAr: 'وقت الاكتشاف' },
  {
    key: 'category', kind: 'radio-cards', labelEn: 'Category', labelAr: 'الفئة',
    options: [
      { value: 'PHISHING', labelEn: 'Phishing', labelAr: 'تصيد' },
      { value: 'MALWARE', labelEn: 'Malware', labelAr: 'برمجية خبيثة' },
    ],
  },
  {
    key: 'severity', kind: 'severity-radio', labelEn: 'Severity', labelAr: 'الخطورة',
    options: [
      { value: 'LOW', labelEn: 'Low', labelAr: 'منخفض' },
      { value: 'CRITICAL', labelEn: 'Critical', labelAr: 'حرج' },
    ],
  },
  { key: 'ack', kind: 'checkbox', labelEn: 'I confirm this is accurate', labelAr: 'أؤكد صحة ذلك' },
];

/**
 * ⚠ The provider reads `grc.lang` from localStorage and DEFAULTS TO ARABIC — Arabic is this
 * platform's primary language (house rule 7). The first draft of this gate assumed English and
 * resolved every label to nothing, so six legs failed for a reason unrelated to their claims
 * (F2-244). Driving the language explicitly is what makes the assertions mean what they say, and
 * it turned the mistake into a stronger gate: the association is now asserted in BOTH languages
 * rather than only the secondary one.
 *
 * ⚠⚠ And the helper must AWAIT, because `I18nProvider` gates the first paint on its locale's
 * dictionary chunk (PERF-FE-002: `if (!dict) return null`) and that chunk arrives through a
 * dynamic `import()`. So a synchronous query runs against an EMPTY DOM — which is
 * indistinguishable from the component under test rendering nothing, and reads as the fix having
 * failed. The Arabic leg passed while the English ones did not for exactly that reason: the
 * module kicks off `loadDict(readStoredLang())` at eval time, which with no stored language is
 * `ar`, so the Arabic dictionary is cached by the second test and English never is.
 */
async function renderForm(
  onSubmit = vi.fn(), fields: IntakeFieldDef[] = FIELDS, lang: 'en' | 'ar' = 'en') {
  localStorage.setItem('grc.lang', lang);
  render(
    <I18nProvider>
      <ToastProvider>
        <IntakeFormRenderer fields={fields} submitLabel="Submit" onSubmit={onSubmit} />
      </ToastProvider>
    </I18nProvider>,
  );
  // The submit label is a literal prop, so it is the one anchor that is identical in both
  // languages — waiting on it proves the gate lifted without asserting anything about the
  // dictionary's contents.
  await screen.findByRole('button', { name: /submit/i });
  return onSubmit;
}

describe('JRN-09 — every intake field must have an accessible name, and every group real grouping', () => {
  it('each of the five text-ish kinds has an accessible name', async () => {
    await renderForm();
    // getByLabelText resolves through the browser's own association rules, so it passes only if
    // the label really is wired to the control — not merely rendered above it.
    for (const [label, tag] of [
      ['Short title', 'INPUT'],
      ['Summary', 'TEXTAREA'],
      ['Team', 'SELECT'],
      ['Occurred on', 'INPUT'],
      ['Detected at', 'INPUT'],
    ] as const) {
      const el = screen.getByLabelText(label, { exact: false });
      expect(el.tagName, `${label} resolved to the wrong element`).toBe(tag);
    }
  });

  it('the association holds in Arabic too, which is the primary language', async () => {
    await renderForm(vi.fn(), FIELDS, 'ar');
    // House rule 7: a fix that names the field only in the secondary language is half a fix —
    // and Arabic is the one this provider defaults to, so it is what most users would meet.
    expect(screen.getByLabelText('عنوان', { exact: false }).tagName).toBe('INPUT');
    expect(screen.getByRole('group', { name: /الخطورة/ })).toBeTruthy();
  });

  it('the checkbox keeps the implicit association it already had', async () => {
    await renderForm();
    const box = screen.getByLabelText('I confirm this is accurate', { exact: false });
    expect(box).toHaveAttribute('type', 'checkbox');
  });

  it('each radio kind is a named group of real radios, and selection is in the a11y tree', async () => {
    await renderForm();

    for (const [name, first, second] of [
      ['Category', 'Phishing', 'Malware'],
      ['Severity', 'Low', 'Critical'],
    ] as const) {
      // A <fieldset> with a <legend> maps to role="group" with that legend as its name. Without
      // it a screen reader announces the options as loose controls with no idea what they belong
      // to — which is the "no group semantics" half of this finding.
      const group = screen.getByRole('group', { name: new RegExp(name, 'i') });
      const radios = Array.from(group.querySelectorAll('input[type="radio"]'));
      expect(radios, `${name} must be real radios, not buttons`).toHaveLength(2);

      // Selected state must be readable by assistive tech, not carried by colour alone.
      expect(radios.every(r => !(r as HTMLInputElement).checked), 'nothing selected yet').toBe(true);
      fireEvent.click(screen.getByLabelText(second, { exact: false }));
      expect(screen.getByLabelText(second, { exact: false })).toBeChecked();
      expect(screen.getByLabelText(first, { exact: false })).not.toBeChecked();
    }
  });

  it('a missing required field is reported ON the field and receives focus', async () => {
    const onSubmit = await renderForm();
    fireEvent.click(screen.getByRole('button', { name: /submit/i }));

    expect(onSubmit, 'an invalid form must not submit').not.toHaveBeenCalled();

    const title = screen.getByLabelText('Short title', { exact: false });
    expect(title, 'The toast named the field and gave no way to reach it. Focus is what turns '
      + '"which field" into "this field".').toHaveFocus();
    expect(title).toHaveAttribute('aria-invalid', 'true');

    // role="alert" is what makes the message announced rather than merely present.
    const alert = screen.getByRole('alert');
    expect(title.getAttribute('aria-describedby') ?? '',
      'the control must point at its own error').toContain(alert.id);
  });

  it('a hint and an error are both described, in reading order', async () => {
    const onSubmit = await renderForm(vi.fn(), [
      { key: 'summary', kind: 'textarea', labelEn: 'Summary', labelAr: 'ملخص', required: true, hintEn: 'At least 30 characters.', hintAr: '٣٠' },
    ]);
    fireEvent.click(screen.getByRole('button', { name: /submit/i }));
    expect(onSubmit).not.toHaveBeenCalled();

    const summary = screen.getByLabelText('Summary', { exact: false });
    const ids = (summary.getAttribute('aria-describedby') ?? '').split(/\s+/).filter(Boolean);
    expect(ids, 'A field can have a hint AND be invalid; aria-describedby takes a list precisely '
      + 'for that, and dropping the hint on error loses the instruction at the moment it is most '
      + 'needed.').toHaveLength(2);
    for (const id of ids) expect(document.getElementById(id), `${id} must exist`).not.toBeNull();
  });

  it('the error clears when the user acts on the field', async () => {
    await renderForm();
    fireEvent.click(screen.getByRole('button', { name: /submit/i }));
    const title = screen.getByLabelText('Short title', { exact: false });
    expect(title).toHaveAttribute('aria-invalid', 'true');

    fireEvent.change(title, { target: { value: 'A real title' } });
    expect(title, 'An error that outlives its cause reads as a control that cannot be satisfied.')
      .not.toHaveAttribute('aria-invalid');
  });

  it('the renderer uses the platform convention rather than a styled div', () => {
    // ⚠ Comments stripped: the fix documents the retired mechanism — the hard zeros, the styled
    // <div>, `role="radiogroup"` — by name, so a comment-blind scan reads the epitaph as the
    // corpse (F2-193) and fails on a correct tree.
    const src = stripComments(
      readFileSync(join(ROOT, 'components', 'intake', 'IntakeFormRenderer.tsx'), 'utf8'));

    for (const token of ['useId', 'htmlFor', '<fieldset', '<legend', 'aria-describedby', "role=\"alert\""]) {
      expect(src.includes(token), `${token} is part of the convention Input.tsx/Select.tsx already `
        + 'use; this component bypassed all of it.').toBe(true);
    }
    expect(/role="radio(group)?"/.test(src),
      'The ARIA radio pattern also owes arrow-key navigation and a roving tabindex. Declaring the '
      + 'role without them tells assistive tech this is a radio group and then does not behave '
      + 'like one — native inputs bring the keyboard model with them.').toBe(false);
    expect(src.length, 'anti-vacuity — a stripper that emptied the file would pass every '
      + 'assertion above (F2-198)').toBeGreaterThan(4000);
  });
});
