import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { I18nProvider } from '../../lib/i18n';
import { ToastProvider } from '../../lib/toast';
import { DialogProvider } from '../../lib/dialog';
import { PolicyAiDraftTab } from './PolicyAiDraftTab';
import type { LlmStatusDto, PolicyDraftResponse } from '../../lib/policyDrafts';

const getPolicyDraftGatewayStatus = vi.fn();
const listPolicyDrafts = vi.fn();
const getPolicyDraft = vi.fn();
const requestPolicyDraft = vi.fn();
const updatePolicyDraft = vi.fn();
const acceptPolicyDraft = vi.fn();
const rejectPolicyDraft = vi.fn();

vi.mock('../../lib/policyDrafts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../lib/policyDrafts')>();
  return {
    ...actual,
    getPolicyDraftGatewayStatus: (...a: unknown[]) => getPolicyDraftGatewayStatus(...a),
    listPolicyDrafts: (...a: unknown[]) => listPolicyDrafts(...a),
    getPolicyDraft: (...a: unknown[]) => getPolicyDraft(...a),
    requestPolicyDraft: (...a: unknown[]) => requestPolicyDraft(...a),
    updatePolicyDraft: (...a: unknown[]) => updatePolicyDraft(...a),
    acceptPolicyDraft: (...a: unknown[]) => acceptPolicyDraft(...a),
    rejectPolicyDraft: (...a: unknown[]) => rejectPolicyDraft(...a),
  };
});

vi.mock('../../lib/policy', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../lib/policy')>();
  return {
    ...actual,
    listFrameworks: () => Promise.resolve([{ id: 1, nameEn: 'NCA ECC' }]),
    listControlCatalog: () => Promise.resolve([{ id: 10, code: 'ECC-1-1', nameEn: 'Cybersecurity Strategy' }]),
  };
});

function sampleDraft(over: Partial<PolicyDraftResponse> = {}): PolicyDraftResponse {
  return {
    id: 42,
    externalId: 'AID-2026-0042',
    status: 'GENERATED',
    policyType: 'Cloud Security Policy',
    category: 'CYBERSECURITY',
    tone: 'FORMAL_REGULATORY',
    generated: {
      titleEn: 'Cloud Security Policy',
      titleAr: 'سياسة أمن الحوسبة السحابية',
      summaryEn: 'Cloud governance summary',
      summaryAr: 'ملخص حوكمة السحابة',
      contentMdEn: '## Purpose\nCloud security baseline.',
      contentMdAr: '## الغرض\nالضوابط الأساسية لأمن السحابة.',
      citedControlIds: [10],
      suggestedReviewCadence: 'ANNUAL',
      assumptions: [],
    },
    hallucinatedCitations: [],
    promptTemplateId: 'policy_draft_bilingual',
    promptTemplateVersion: 'v1',
    provider: 'GEMINI',
    model: 'gemini-2.5-flash',
    inputTokens: 350,
    outputTokens: 610,
    latencyMs: 1200,
    requestedBy: 'compliance',
    ...over,
  };
}

function mount(onAccepted = vi.fn()) {
  return render(
    <MemoryRouter>
      <I18nProvider>
        <ToastProvider>
          <DialogProvider>
            <PolicyAiDraftTab onAccepted={onAccepted} />
          </DialogProvider>
        </ToastProvider>
      </I18nProvider>
    </MemoryRouter>,
  );
}

describe('PolicyAiDraftTab', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.setItem('grc.lang', 'en');
    const gw: LlmStatusDto = {
      enabled: true,
      mode: 'LIVE',
      provider: 'GEMINI',
      model: 'gemini-2.5-flash',
      credentialsConfigured: true,
      dailyTokenQuota: 500000,
      tokensUsedLast24h: 960,
    };
    getPolicyDraftGatewayStatus.mockResolvedValue(gw);
    listPolicyDrafts.mockResolvedValue([sampleDraft()]);
    getPolicyDraft.mockResolvedValue(sampleDraft());
    updatePolicyDraft.mockResolvedValue(sampleDraft());
    acceptPolicyDraft.mockResolvedValue(sampleDraft({ status: 'ACCEPTED', acceptedPolicyId: 88 }));
    requestPolicyDraft.mockResolvedValue(sampleDraft({ id: 43, externalId: 'AID-2026-0043', status: 'REQUESTED' }));
    rejectPolicyDraft.mockResolvedValue(sampleDraft({ status: 'REJECTED' }));
  });

  it('renders gateway status, reviews bilingual draft, and accepts into Policy Register', async () => {
    const onAccepted = vi.fn();
    mount(onAccepted);

    await waitFor(() => {
      expect(screen.getByText(/LIVE · GEMINI/i)).toBeTruthy();
      expect(screen.getByText('Cloud Security Policy')).toBeTruthy();
    });

    fireEvent.click(screen.getByRole('button', { name: /Review/i }));
    await waitFor(() => expect(getPolicyDraft).toHaveBeenCalledWith(42));

    fireEvent.click(screen.getByRole('button', { name: /Accept into Policy Register/i }));
    fireEvent.click(screen.getByRole('button', { name: /Create DRAFT Policy/i }));

    await waitFor(() => {
      expect(updatePolicyDraft).toHaveBeenCalled();
      expect(acceptPolicyDraft).toHaveBeenCalledWith(42, expect.objectContaining({
        policyCode: 'POL-CYBE-42',
        version: '1.0',
      }));
      expect(onAccepted).toHaveBeenCalled();
    });
  });

  it('queues an asynchronous AI policy draft request', async () => {
    mount();
    await waitFor(() => expect(screen.getByRole('button', { name: /Draft policy with AI/i })).toBeTruthy());

    fireEvent.click(screen.getByRole('button', { name: /Draft policy with AI/i }));
    const topicInput = screen.getByPlaceholderText(/Identity & Access Management Policy/i);
    fireEvent.change(topicInput, { target: { value: 'Cryptographic Key Management Policy' } });

    fireEvent.click(screen.getByRole('button', { name: /Generate asynchronously/i }));

    await waitFor(() => {
      expect(requestPolicyDraft).toHaveBeenCalledWith(expect.objectContaining({
        policyType: 'Cryptographic Key Management Policy',
        category: 'INFORMATION_SECURITY',
      }));
    });
  });

  it('renders Arabic studio labels when default RTL locale is active', async () => {
    localStorage.setItem('grc.lang', 'ar');
    mount();

    await waitFor(() => {
      expect(screen.getByText(/صياغة السياسات بالذكاء الاصطناعي/)).toBeTruthy();
      expect(screen.getByText('سياسة أمن الحوسبة السحابية')).toBeTruthy();
    });
  });
});
