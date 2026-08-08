import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { I18nProvider } from '../../lib/i18n';
import { ToastProvider } from '../../lib/toast';
import { DialogProvider } from '../../lib/dialog';
import { ApiError, type RiskAcceptanceDto } from '../../lib/api';
import { RiskAcceptancesPanel } from './RiskAcceptancesPanel';

/**
 * F2-164 — the four acceptance-API methods that had no caller, exercised.
 *
 * <p>`listForRisk`, `approve`, `reject` and `revoke` were dead. That is why the
 * ungoverned `POST /risks/{id}/accept` was load-bearing: **the governed path could
 * raise an acceptance and then nothing could approve it**, and it is the approval
 * that flips the risk to `ACCEPTED`. Retiring the bypass without this panel would
 * have left the platform unable to accept a risk at all.
 *
 * <p>⚠ These are the first tests over any part of the acceptance surface.
 */

const listForRisk = vi.fn();
const approve = vi.fn();
const reject = vi.fn();
const revoke = vi.fn();

vi.mock('../../lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../lib/api')>();
  return {
    ...actual,
    riskAcceptanceApi: {
      ...actual.riskAcceptanceApi,
      listForRisk: (...a: unknown[]) => listForRisk(...a),
      approve: (...a: unknown[]) => approve(...a),
      reject: (...a: unknown[]) => reject(...a),
      revoke: (...a: unknown[]) => revoke(...a),
    },
  };
});

/**
 * A typed fixture, deliberately. `RiskAcceptanceDto` is what the server sends, so a
 * field this panel reads under a name the DTO does not carry fails here at compile
 * time — the cheapest field-name check there is, and the one that caught
 * `injectType: 'SCENARIO_UPDATE'` in the F2-163 slice.
 */
function acc(over: Partial<RiskAcceptanceDto> = {}): RiskAcceptanceDto {
  return {
    id: 41,
    externalId: 'e1f5c2a0-0000-4000-8000-000000000001',
    riskId: 21,
    justificationEn: 'Remediation cost exceeds the modelled annual loss expectancy.',
    expiresAt: '2027-03-31',
    status: 'SUBMITTED',
    requiresBoard: false,
    ...over,
  };
}

function mount(riskId = 21) {
  return render(
    <MemoryRouter>
      <I18nProvider>
        <ToastProvider>
          <DialogProvider>
            <RiskAcceptancesPanel riskId={riskId} />
          </DialogProvider>
        </ToastProvider>
      </I18nProvider>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  // ⚠ This is an Arabic-primary platform: `I18nProvider` defaults to `'ar'` unless
  // localStorage says otherwise, so every English assertion below rendered against an
  // RTL tree and 6 of 9 failed until this line existed. Same trap as guessing an
  // Arabic label — the locale is part of the fixture, not part of the environment.
  localStorage.setItem('grc.lang', 'en');
  listForRisk.mockReset();
  approve.mockReset();
  reject.mockReset();
  revoke.mockReset();
  approve.mockResolvedValue(acc({ status: 'APPROVED' }));
  reject.mockResolvedValue(acc({ status: 'REJECTED' }));
  revoke.mockResolvedValue(acc({ status: 'REVOKED' }));
});

describe('RiskAcceptancesPanel', () => {
  it('lists a risk\'s acceptances — the read that had no caller', async () => {
    listForRisk.mockResolvedValue([acc()]);
    mount();
    expect(await screen.findByText('#41')).toBeTruthy();
    expect(listForRisk).toHaveBeenCalledWith(21);
    expect(await screen.findByText(/Remediation cost exceeds/)).toBeTruthy();
  });

  it('offers Approve and Reject on a SUBMITTED request, and Revoke on none', async () => {
    listForRisk.mockResolvedValue([acc({ status: 'SUBMITTED' })]);
    mount();
    expect(await screen.findByRole('button', { name: /Approve/i })).toBeTruthy();
    expect(screen.getByRole('button', { name: /Reject/i })).toBeTruthy();
    expect(screen.queryByRole('button', { name: /Revoke/i })).toBeNull();
  });

  it('approving without a Board requirement calls approve with no signatory', async () => {
    listForRisk.mockResolvedValue([acc({ status: 'SUBMITTED', requiresBoard: false })]);
    mount();
    fireEvent.click(await screen.findByRole('button', { name: /Approve/i }));
    await waitFor(() => expect(approve).toHaveBeenCalledWith(41, undefined));
  });

  it('asks for Board sign-off first when residual is above appetite, and passes it', async () => {
    // The server refuses the approval outright without it ("Board sign-off is
    // required — residual exceeds appetite"), so a panel that just posted would turn
    // a legitimate approval into a 400 the approver cannot act on.
    listForRisk.mockResolvedValue([acc({ status: 'SUBMITTED', requiresBoard: true })]);
    mount();
    fireEvent.click(await screen.findByRole('button', { name: /Approve/i }));

    const field = await screen.findByLabelText(/Board signatory/i);
    fireEvent.change(field, { target: { value: 'Audit Committee' } });
    // `PromptModal`'s confirm button is `opts.submitLabel ?? t('save')` — "Save" in
    // English, not "OK". Reading the component beats guessing the string.
    fireEvent.click(screen.getByRole('button', { name: /^(Save|Submit|OK|Confirm)$/i }));

    await waitFor(() => expect(approve).toHaveBeenCalledWith(41, 'Audit Committee'));
  });

  it('offers Revoke — not Approve — once APPROVED, and flags the live time-box', async () => {
    listForRisk.mockResolvedValue([acc({
      status: 'APPROVED', approvedBy: 'so_officer', expiresAt: '2027-03-31',
    })]);
    mount();
    expect(await screen.findByRole('button', { name: /Revoke/i })).toBeTruthy();
    expect(screen.queryByRole('button', { name: /Approve/i })).toBeNull();
    // The live badge is what the close gate keys on — an APPROVED row inside its
    // expiry. Showing it is how an approver knows the exemption is real.
    expect(await screen.findByText(/Live acceptance until/i)).toBeTruthy();
  });

  it('does not call it live when the APPROVED acceptance has lapsed', async () => {
    listForRisk.mockResolvedValue([acc({ status: 'APPROVED', expiresAt: '2020-01-01' })]);
    mount();
    await screen.findByText('#41');
    expect(screen.queryByText(/Live acceptance until/i)).toBeNull();
  });

  it('surfaces a 403 verbatim and renders no rows — no client-side stand-in', async () => {
    // F2-158: a fallback array kept a 500 invisible for an endpoint's whole life,
    // because the screen always rendered plausible rows. An acceptance panel that
    // invents rows is worse than one that says it could not read them.
    listForRisk.mockRejectedValue(new ApiError(403, 'Access is denied'));
    mount();
    expect(await screen.findByText('Access is denied')).toBeTruthy();
    expect(screen.queryByText('#41')).toBeNull();
  });

  it('falls back to its own wording for a non-ApiError failure', async () => {
    listForRisk.mockRejectedValue(new Error('Failed to fetch'));
    mount();
    expect(await screen.findByText(/Could not load acceptance requests/i)).toBeTruthy();
  });

  it('says so when a risk has no acceptance at all', async () => {
    listForRisk.mockResolvedValue([]);
    mount();
    expect(await screen.findByText(/No acceptance requests for this risk/i)).toBeTruthy();
  });
});
