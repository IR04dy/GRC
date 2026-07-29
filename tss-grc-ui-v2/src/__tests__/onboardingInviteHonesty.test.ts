import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { en } from '../translations/en';
import { ar } from '../translations/ar';

/**
 * F2-33 — the onboarding invite step may not promise delivery it cannot perform.
 *
 * <p>What it used to say, in both languages: *"They will receive an email with a one-time sign-in
 * link"*, and *"In production they trigger an email via your configured provider (AWS SES /
 * SendGrid)"*. Neither was true. No invitation path exists — no token, no accept flow, nothing that
 * calls a sender — and the second sentence is the more damaging, because it describes a
 * configuration toggle that does not exist on a screen a prospect gets shown.
 *
 * <p>This is a **wording ratchet**, and wording is exactly the kind of thing that regresses: the
 * copy is easy to "improve" back toward a promise, and nothing about a false sentence breaks a build.
 * The `/mock/i` CI rule from W2-1b cannot see this class of defect at all — nothing is imported, no
 * fake data appears; the screen simply asserts something about the world. So the assertion here is on
 * the claim, not on the implementation.
 *
 * <p>It deliberately does NOT assert the step is wired to user creation. It cannot be, without a
 * decision: `POST /api/v1/admin/users` is `hasRole('PLATFORM_ADMIN')` and requires a username, a
 * non-blank full name and a strong initial password, while this step collects an email and a role.
 * Closing that gap means an invitation subsystem or generating other people's credentials, and both
 * belong to the owner.
 */

const INVITE_KEYS = ['onbStep3Sub', 'onbInviteHint', 'onbInviteEmpty', 'onbInstalledInvites'] as const;

/**
 * Claims about outbound delivery or credential issuance, in either language.
 *
 * ⚠ The negative lookbehinds are not decoration. The first version of this list flagged the
 * *corrected* copy, because `/email (?:is|will be) sent/` matches "no email **is sent** from here" —
 * a detector that cannot tell a promise from its denial fails on exactly the sentence it is meant to
 * protect. `assertsNothingSent` below then forces the copy to keep saying it, so removing the
 * negation cannot pass either.
 */
const DELIVERY_CLAIMS: { pattern: RegExp; what: string }[] = [
  { pattern: /will receive/i,                 what: 'promises the invitee receives something' },
  { pattern: /one-time sign-?in|magic link/i, what: 'promises a sign-in link that does not exist' },
  { pattern: /SES|SendGrid/i,                 what: 'names an email provider integration that does not exist' },
  { pattern: /trigger an email/i,             what: 'claims an email is triggered' },
  { pattern: /(?<!no |not |never )sends? an email/i,                 what: 'claims an email is sent' },
  { pattern: /(?<!no )(?<!not )(?<!never )email (?:is|will be) sent/i, what: 'claims an email is sent' },
  { pattern: /ستصلهم|ستصل إليهم/, what: 'AR: promises the invitee receives something' },
  { pattern: /رابط دخول/,     what: 'AR: promises a sign-in link that does not exist' },
  { pattern: /(?<!لا )تُرسل عبر|سيتم إرسال/, what: 'AR: claims an email is sent' },
];

/** The exact sentences that shipped, so the detector is checked against the real defect. */
const THE_CLAIMS_THAT_SHIPPED = [
  'Add teammates who should have access. They will receive an email with a one-time sign-in link.',
  'Invitations are stored locally in this demo. In production they trigger an email via your '
    + 'configured provider (AWS SES / SendGrid).',
  'أضف زملاءك الذين سيستخدمون المنصة. ستصلهم رسالة بريد فيها رابط دخول لمرّة واحدة.',
  'تُحفظ الدعوات محلياً في هذا العرض التجريبي. في الإنتاج تُرسل عبر مزوّد البريد المُعدّ (AWS SES / SendGrid).',
];

describe('F2-33 — the invite step describes what it actually does', () => {
  for (const [lang, dict] of [['en', en], ['ar', ar]] as const) {
    it(`${lang}: no invite copy claims an email or sign-in link is delivered`, () => {
      for (const key of INVITE_KEYS) {
        const text = (dict as Record<string, string>)[key];
        expect(text, `${lang}.${key} is missing — the ratchet must not pass vacuously`).toBeTruthy();
        for (const { pattern, what } of DELIVERY_CLAIMS) {
          expect(pattern.test(text),
            `${lang}.${key} ${what}:\n  "${text}"\n`
            + 'Nothing here creates an account or sends mail. Say what the step does, or build the '
            + 'invitation subsystem first.')
            .toBe(false);
        }
      }
    });
  }

  it('the detector catches the sentences that actually shipped', () => {
    // Anti-vacuity. A wording ratchet whose patterns no longer match anything passes forever while
    // protecting nothing, and the copy it guards is a moving target. So it is checked against the
    // real historical strings, in both languages, rather than trusted.
    for (const sentence of THE_CLAIMS_THAT_SHIPPED) {
      const caught = DELIVERY_CLAIMS.some(c => c.pattern.test(sentence));
      expect(caught, `the ratchet no longer detects a claim it was written for:
  "${sentence}"`)
        .toBe(true);
    }
  });

  it('says plainly that nothing is created or sent, in both languages', () => {
    // The negative assertions above stop the promise coming back; this one stops the copy going
    // silent instead, which would leave the operator to assume the old behaviour.
    expect(en.onbStep3Sub).toMatch(/no email is sent|nothing is created/i);
    expect(ar.onbStep3Sub).toMatch(/لا يُنشأ أي حساب|لا تُرسل/);
  });

  it('routes the operator to where accounts are really created', () => {
    const page = readFileSync(
      join(__dirname, '..', 'pages', 'onboarding', 'OnboardingPage.tsx'), 'utf8');
    // /admin opens on its Users tab, so this is a working destination rather than a gesture.
    expect(page).toContain('to="/admin"');
    expect(page).toContain('onbInviteOpenAdmin');
    // And the collected rows can be carried there instead of being retyped from a screenshot.
    expect(page).toContain('onbInviteCopy');
    expect(page).toContain('navigator.clipboard.writeText');
    // A failed clipboard write must not report success — that is the same defect class again.
    expect(page).toContain('onbInviteCopyFailed');
  });
});
