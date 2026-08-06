import { describe, it, expect, vi, beforeEach } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { I18nProvider } from '../lib/i18n';
import { ToastProvider } from '../lib/toast';
import { ThemeProvider } from '../lib/theme';
import { stripComments } from './support/sourceScan';

/**
 * F2-147 — a cascade that sent nothing may not render as a send.
 *
 * <p>The defect: `SmsChannelSender`, `TeamsChannelSender` and `WhatsAppChannelSender` log a line
 * and return success; `EmailChannelSender` does the same whenever no `JavaMailSender` bean is
 * configured. The server was honest about it — each stub put its status in `SendResult`'s payload
 * string — and the screen showed `Dispatched — 12/12`, a green `Success` KPI and a `COMPLETED`
 * badge with a tick. During an actual crisis that is an operator believing twelve people were told.
 *
 * <h3>What was actually wrong, measured rather than assumed</h3>
 * The finding as filed said `DispatchResult` had "no field for it" and that there was "no surface
 * anywhere" showing the truth. Both overstate it: `CascadeResponse.responsePayload` carried the
 * stub string, the service populated it, and the wizard printed it in a Details cell. Three things
 * were really wrong, and they are what these tests pin:
 *
 * <ul>
 *   <li>the fact lived only in free-text English, so it could not be filtered, totalled, or read
 *       by an Arabic-first operator — this platform's primary locale;</li>
 *   <li>the aggregates an operator actually reads (the toast, the Success KPI, the status badge)
 *       were computed without consulting it;</li>
 *   <li>it was never persisted, so the cascade row — the BCM evidence, read months later by
 *       someone who never saw this screen — could not express it at all.</li>
 * </ul>
 *
 * <h3>⚠ Why every assertion runs in both locales</h3>
 * The first version of this file queried English text only, and all four render tests failed on
 * "Unable to find … `/Next/i`". `I18nProvider` defaults to **Arabic** unless `grc.lang` says
 * otherwise, so the wizard under test was rendering Arabic throughout — and the one query that did
 * succeed was a hard-coded placeholder that is identical in both languages. An English-only test of
 * this screen would assert nothing about what a Saudi crisis operator actually sees, which is the
 * audience the finding is about.
 */

// ── Mocks: the four API calls, with the real label maps and helpers kept ──────

const listTemplates = vi.fn();
const dispatchCascade = vi.fn();
const listCascades = vi.fn();
const listChannelReadiness = vi.fn();

vi.mock('../lib/crisisComms', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../lib/crisisComms')>();
  return {
    ...actual,
    listTemplates: (...a: unknown[]) => listTemplates(...a),
    dispatchCascade: (...a: unknown[]) => dispatchCascade(...a),
    listCascades: (...a: unknown[]) => listCascades(...a),
    listChannelReadiness: (...a: unknown[]) => listChannelReadiness(...a),
  };
});

// Imported after the mock so the component picks up the stubs.
const { CrisisCommsDispatchWizard } = await import('../pages/bcm/CrisisCommsDispatchWizard');

const TEMPLATE = {
  id: 501,
  externalId: 'aaaaaaaa-0000-0000-0000-000000000001',
  organisationId: 1,
  templateCode: 'CCT-CUS-OUTAGE-008',
  titleEn: 'Customer outage notice',
  titleAr: 'إشعار انقطاع الخدمة للعملاء',
  audience: 'CUSTOMERS',
  scenarioCategory: 'OUTAGE',
  severityBand: 'HIGH',
  subjectEn: 'Service update',
  subjectAr: 'تحديث الخدمة',
  bodyEn: 'Service is unavailable.',
  bodyAr: 'الخدمة غير متاحة.',
  approvalStatus: 'APPROVED_BY_CISO',
};

function cascade(channel: string, simulated: boolean, payload: string) {
  return {
    id: channel === 'SMS' ? 11 : 12,
    externalId: `bbbbbbbb-0000-0000-0000-00000000000${channel === 'SMS' ? 1 : 2}`,
    organisationId: 1,
    crisisEventId: 777,
    templateId: 501,
    channel,
    audience: 'CUSTOMERS',
    renderedSubject: 'Service update',
    recipientCount: 12,
    successCount: 12,
    failureCount: 0,
    dispatchedAt: '2026-08-06T14:00:00',
    completedAt: '2026-08-06T14:00:01',
    dispatchedByName: 'Faisal Al-Subaie',
    status: 'COMPLETED',
    responsePayload: payload,
    simulated,
  };
}

/** The user-visible strings per locale. Arabic first — it is the platform default. */
const LOCALES = {
  ar: {
    next: 'التالي',
    preview: 'معاينة',
    send: 'أرسل الآن',
    step2: /اختيار القنوات/,
    result: 'نتيجة الإرسال',
    banner: /لم يتم إرسال هذه الرسائل فعليًا/,
    simBadge: /COMPLETED · محاكاة/,
    simToast: /تم التسجيل دون إرسال فعلي/,
    sentToast: /^تم الإرسال — /,
    loggedKpi: /مُسجَّل \(محاكاة\)/,
    successWord: /^نجح$/,
    preSend: /لا توجد بوابة مُهيّأة/,
    history: /تدفّقات سابقة لهذا الحدث/,
    simChip: 'محاكاة',
  },
  en: {
    next: 'Next',
    preview: 'Preview',
    send: 'Send now',
    step2: /pick channels/i,
    result: 'Dispatch result',
    banner: /were not actually sent/i,
    simBadge: /COMPLETED · SIMULATED/,
    simToast: /Recorded, not sent/i,
    sentToast: /^Dispatched — /,
    loggedKpi: /Logged \(simulated\)/i,
    successWord: /^Success$/,
    preSend: /no gateway is configured/i,
    history: /already dispatched for this event/i,
    simChip: 'SIMULATED',
  },
} as const;

type LocaleKey = keyof typeof LOCALES;

function renderWizard(lang: LocaleKey) {
  localStorage.setItem('grc.lang', lang);
  return render(
    <MemoryRouter>
      <ThemeProvider>
        <I18nProvider>
          <ToastProvider>
            <CrisisCommsDispatchWizard />
          </ToastProvider>
        </I18nProvider>
      </ThemeProvider>
    </MemoryRouter>,
  );
}

/** Fill step 1 — the crisis-event id and the template. */
async function fillStepOne() {
  fireEvent.change(await screen.findByPlaceholderText('e.g. 777'), { target: { value: '777' } });
  const selects = document.querySelectorAll('select');
  fireEvent.change(selects[selects.length - 1], { target: { value: '501' } });
}

/** Walk the wizard from step 1 to the result panel. */
async function dispatchThrough(lang: LocaleKey) {
  const L = LOCALES[lang];
  renderWizard(lang);
  await waitFor(() => expect(listTemplates).toHaveBeenCalled());
  await fillStepOne();

  fireEvent.click(screen.getByRole('button', { name: new RegExp(L.next) }));
  await screen.findByText(L.step2);
  fireEvent.click(screen.getByRole('button', { name: new RegExp(L.next) }));
  fireEvent.click(await screen.findByRole('button', { name: new RegExp(L.preview) }));
  fireEvent.click(await screen.findByRole('button', { name: new RegExp(L.send) }));
}

/**
 * Is the word "Success" being used as the roll-up KPI's label, as opposed to the
 * per-channel table's column header? Both render the same word, so an unscoped
 * query cannot tell "the counter is still called a success" from "the table has
 * a Success column" — and only the first is the assertion that matters.
 *
 * <p>Split on element type: the wizard's KPI is a local component built from
 * inline-styled `div`s (it does not use the shared `Kpi`, so there is no `.kpi`
 * class to scope to — the first version of this helper looked for one and found
 * zero matches, reporting "not a success label" for the right reason by accident
 * and the wrong reason in fact). The column header is a `th`.
 */
function successLabelHosts(labels: readonly HTMLElement[]): string[] {
  return labels.map(el => el.tagName);
}
function successIsAKpiLabel(labels: readonly HTMLElement[]): boolean {
  return successLabelHosts(labels).includes('DIV');
}

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
  listTemplates.mockResolvedValue([TEMPLATE]);
  listCascades.mockResolvedValue([]);
  listChannelReadiness.mockResolvedValue([
    { channelType: 'EMAIL', registered: true, healthy: false, deliveryMode: 'SIMULATED' },
    { channelType: 'SMS', registered: true, healthy: true, deliveryMode: 'SIMULATED' },
  ]);
});

describe.each(['ar', 'en'] as const)(
  'F2-147 [%s] — a simulated cascade cannot render as a delivered one', (lang) => {

  const L = LOCALES[lang];

  it('labels the result SIMULATED and refuses to call it a success', async () => {
    dispatchCascade.mockResolvedValue({
      crisisEventId: 777, templateId: 501,
      cascades: [cascade('EMAIL', true, 'stub: JavaMailSender not configured; logged 12 recipients')],
      totalRecipients: 12, totalSuccess: 12, totalFailure: 0, elapsedMillis: 8,
      simulatedChannels: ['EMAIL'],
    });

    await dispatchThrough(lang);
    await screen.findByText(L.result);

    // The headline states it in plain language, above the counters.
    expect(screen.getByText(L.banner)).toBeTruthy();
    // The toast is the one thing every operator reads. It must not say "sent",
    // and it must not carry the green tick that the success variant renders.
    expect(screen.getByText(L.simToast)).toBeTruthy();
    expect(screen.queryByText(L.sentToast)).toBeNull();
    expect(document.querySelector('.toast.success')).toBeNull();
    expect(document.querySelector('.toast.warning')).toBeTruthy();
    // The badge keeps COMPLETED — a true fact about the attempt — but qualified,
    // and carries the warning tone rather than the status's usual one. Tone is
    // load-bearing here: a green "COMPLETED" beside the word SIMULATED is the
    // kind of mixed signal an operator resolves in favour of the colour.
    const simBadge = screen.getByText(L.simBadge);
    expect(simBadge).toBeTruthy();
    expect(simBadge.className).toContain('badge-warning');
    // And the counter is no longer labelled a success.
    expect(screen.getByText(L.loggedKpi)).toBeTruthy();
    const simHosts = screen.queryAllByText(L.successWord);
    // Anti-vacuity: the per-channel table still has its Success column, so the
    // query below is matching something. Without this, deleting the word from the
    // page entirely would read as the fix.
    expect(successLabelHosts(simHosts)).toContain('TH');
    expect(successIsAKpiLabel(simHosts)).toBe(false);
  });

  it('still reports a genuine delivery as a success', async () => {
    dispatchCascade.mockResolvedValue({
      crisisEventId: 777, templateId: 501,
      cascades: [cascade('EMAIL', false, 'email: 12 ok, 0 failed')],
      totalRecipients: 12, totalSuccess: 12, totalFailure: 0, elapsedMillis: 120,
      simulatedChannels: [],
    });

    await dispatchThrough(lang);
    await screen.findByText(L.result);

    // The converse control. Without it, a page that always warns would pass the
    // test above while telling the operator nothing.
    const liveHosts = screen.queryAllByText(L.successWord);
    expect(successLabelHosts(liveHosts)).toContain('TH');
    expect(successIsAKpiLabel(liveHosts)).toBe(true);
    expect(screen.queryByText(L.banner)).toBeNull();
    expect(screen.queryByText(L.loggedKpi)).toBeNull();
    expect(screen.queryByText(L.simBadge)).toBeNull();
    expect(screen.getByText(L.sentToast)).toBeTruthy();
    expect(document.querySelector('.toast.success')).toBeTruthy();
  });

  it('warns before the trigger, on the channel picker', async () => {
    renderWizard(lang);
    await waitFor(() => expect(listChannelReadiness).toHaveBeenCalled());
    await fillStepOne();
    fireEvent.click(screen.getByRole('button', { name: new RegExp(L.next) }));

    await screen.findByText(L.step2);
    // EMAIL is pre-selected and simulated, so the pre-send warning must be present
    // before anything is dispatched — the surface the finding had none of.
    expect(await screen.findByText(L.preSend)).toBeTruthy();
    expect(screen.getAllByText(L.simChip).length).toBeGreaterThanOrEqual(1);
    expect(dispatchCascade).not.toHaveBeenCalled();
  });

  it('shows the cascades already dispatched for the event', async () => {
    listCascades.mockResolvedValue([cascade('SMS', true, 'stub-sms: 12 recipients logged')]);
    renderWizard(lang);

    fireEvent.change(await screen.findByPlaceholderText('e.g. 777'), { target: { value: '777' } });
    await waitFor(() => expect(listCascades).toHaveBeenCalledWith(777));

    // Before F2-147 `listCascades` had no caller at all, so a cascade could only
    // ever be seen once, in a panel that "Send another" throws away.
    expect(await screen.findByText(L.history)).toBeTruthy();
    // Text and tone, on this table too. The history badge and the result badge
    // are written identically, which is exactly why both need asserting: a
    // mutation aimed at one anchor lands on whichever appears first in the file.
    const histBadge = screen.getByText(L.simBadge);
    expect(histBadge.className).toContain('badge-warning');
  });
});

describe('F2-147 — the honesty is not optional in the source', () => {

  const WIZARD = join(__dirname, '..', 'pages', 'bcm', 'CrisisCommsDispatchWizard.tsx');

  it('never calls toast.success without first consulting simulatedChannels', () => {
    const code = stripComments(readFileSync(WIZARD, 'utf8'));

    // Anti-vacuity: the file must still contain the call this test is about.
    expect(code).toContain('toast.success');
    expect(code).toContain('simulatedChannels');

    // The success toast must sit inside a branch that has already read the flag.
    expect(code.indexOf('simulatedChannels')).toBeLessThan(code.indexOf('toast.success'));
  });

  it('has no drill-through to the retired /notifications/channels stub', () => {
    const code = stripComments(readFileSync(WIZARD, 'utf8'));
    // F2-142 turned that route into a redirect onto the notification inbox, where a
    // cascade's channel count does not live. Two of the three KPIs had their links
    // removed at the time and the comment explaining why covered all three; the
    // Channels KPI kept its `to` regardless.
    expect(code).not.toContain('/notifications/channels');
  });
});
