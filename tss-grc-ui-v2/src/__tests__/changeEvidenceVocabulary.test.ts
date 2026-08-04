import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { EVIDENCE_TONE, evidenceCalls } from '../pages/change/ChangeEvidence';
import { en } from '../translations/en';
import { ar } from '../translations/ar';

/**
 * S8a / CE-9 — the frontend's half of "evidence, not prose".
 *
 * <p>Two things can go wrong here and neither would fail a typecheck. The client can call an endpoint
 * the controller does not declare (dead button), and the UI can render an unreviewed attachment
 * exactly like a verified one — which would make the server's satisfy refusal look like a bug, since
 * the operator can see the evidence they just attached sitting right there.
 */

const BACKEND = join(__dirname, '..', '..', '..', 'tss-grc-backend', 'src', 'main');
const CONTROLLER = join(BACKEND, 'java', 'com', 'tss', 'grc', 'module', 'change_mgmt',
                        'controller', 'ChangeManagementController.java');
const ADAPTER = join(BACKEND, 'java', 'com', 'tss', 'grc', 'module', 'change_adapters',
                     'EvidenceAdapter.java');
const CONDITIONS_CARD = join(__dirname, '..', 'pages', 'change', 'ChangeConditionsCard.tsx');
const API = join(__dirname, '..', 'lib', 'api.ts');

describe('S8a/CE-9 — change evidence in the UI', () => {
  it('the client calls the four endpoints the controller declares, with the guards it declares', () => {
    const controller = readFileSync(CONTROLLER, 'utf8');
    const api = readFileSync(API, 'utf8');
    const block = api.slice(api.indexOf('evidence:            (id: number)'),
                            api.indexOf('comments:    (id'));
    expect(block, 'the S8a client block was not found').not.toBe('');
    for (const path of ['/evidence', '/conditions/${conditionId}/evidence']) {
      expect(block, `the client does not call ${path}`).toContain(path);
    }

    // Read FORWARD from the mapping — @GetMapping/@PostMapping precede @PreAuthorize, so a window
    // taken backwards lands in the previous handler's annotations (the mistake this suite already
    // made once, in conditionRegisterVocabulary).
    const guardAfter = (annotation: string, mapping: string) => {
      const at = controller.indexOf(`${annotation}("${mapping}")`);
      expect(at, `no ${annotation} for ${mapping}`).toBeGreaterThan(-1);
      return controller.slice(at, at + 300);
    };
    // Reading is CHANGE:READ; attaching is CHANGE:WRITE and NOT APPROVE — attaching an artefact is
    // clerical, and what makes it count is the evidence subsystem's own review, not this guard.
    expect(guardAfter('@GetMapping', '/{id}/evidence')).toContain("hasPermission('CHANGE','READ')");
    expect(guardAfter('@PostMapping', '/{id}/evidence')).toContain("hasPermission('CHANGE','WRITE')");
    expect(guardAfter('@GetMapping', '/conditions/{conditionId}/evidence'))
      .toContain("hasPermission('CHANGE','READ')");
    expect(guardAfter('@PostMapping', '/conditions/{conditionId}/evidence'))
      .toContain("hasPermission('CHANGE','WRITE')");
  });

  it('each subject routes to its own pair of endpoints', () => {
    // One helper decides, so the card and the condition row cannot drift onto the same URL — which
    // would silently show a change's evidence on every condition.
    expect(evidenceCalls('change').list).not.toBe(evidenceCalls('condition').list);
    expect(evidenceCalls('change').attach).not.toBe(evidenceCalls('condition').attach);
  });

  it('only APPROVED renders as success — the tone map cannot flatten the review states', () => {
    expect(EVIDENCE_TONE.APPROVED).toBe('success');
    for (const state of ['PENDING_REVIEW', 'DRAFT', 'EXPIRED', 'REJECTED']) {
      expect(EVIDENCE_TONE[state], `${state} must not read as verified`).not.toBe('success');
    }
    // And the adapter agrees: `verified` is APPROVED and nothing else. If somebody widens it there,
    // this pins the reason the UI drew the line where it did.
    expect(readFileSync(ADAPTER, 'utf8')).toContain('"APPROVED".equals(e.getStatus())');
  });

  it('Satisfy is disabled until verified evidence is attached, and the reason is on screen', () => {
    const card = readFileSync(CONDITIONS_CARD, 'utf8');
    // ⚠ Strip comments first. An earlier structural assertion in this programme stayed green against
    // commented-out code, which is not a test.
    const code = card.replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '');
    expect(code, 'the Satisfy button no longer depends on verified evidence')
      .toMatch(/disabled=\{busy \|\| !verified\}/);
    expect(code, 'the hint that explains the disabled button is gone')
      .toContain("t('changeCondNeedsEvidence')");
    expect(code, 'the condition row no longer reads its own evidence')
      .toContain("useAttachedEvidence('condition'");
  });

  it('every new key exists in both languages, and the Arabic is Arabic', () => {
    const KEYS = [
      'changeEvidenceTitle', 'changeEvidenceSub', 'changeEvidenceNone', 'changeEvidenceAttach',
      'changeEvidenceAttachTitle', 'changeEvidenceItem', 'changeEvidenceNotes',
      'changeEvidenceAttached', 'changeEvidencePickOne', 'changeEvidenceEmptyPool',
      'changeEvidenceSomeVerified', 'changeEvidenceNoneVerified', 'changeCondNeedsEvidence',
    ];
    for (const key of KEYS) {
      for (const [lang, dict] of [['en', en], ['ar', ar]] as const) {
        expect((dict as Record<string, string>)[key], `${key} missing from ${lang}`).toBeTruthy();
      }
      // House rule 7 both ways: an English string copied into ar.ts satisfies "present" and fails
      // the actual requirement.
      expect((ar as Record<string, string>)[key], `${key} is not translated`).toMatch(/[؀-ۿ]/);
      expect((ar as Record<string, string>)[key]).not.toBe((en as Record<string, string>)[key]);
    }
  });

  it('the empty-pool message says this screen attaches rather than uploads', () => {
    // The single most likely support question: "where is the upload button?". There is none, on
    // purpose — hashing, versioning and four-eyes review belong to the evidence workspace.
    expect((en as Record<string, string>).changeEvidenceEmptyPool).toMatch(/does not upload/i);
    expect((en as Record<string, string>).changeEvidenceSub).toMatch(/APPROVED/);
    expect((ar as Record<string, string>).changeEvidenceEmptyPool).toMatch(/[؀-ۿ]/);
  });
});
