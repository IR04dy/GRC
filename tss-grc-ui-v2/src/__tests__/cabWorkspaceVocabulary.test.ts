/**
 * CE-13 — the CAB workspace's vocabulary, pinned against the server's, plus the two guards the
 * measurement earned.
 *
 * Two things here carry the slice. **The ladder order must match the server's**, because the screen
 * tells a CAB secretary what to fix first and a reordered ladder sends them to rebuild an agenda for a
 * change nobody referred. And **every reason and subject state must have a label in both languages**,
 * because an unlabelled code renders as the raw enum — which is how a governance screen ends up
 * showing an auditor `SUBJECT_NOT_FOUND`.
 *
 * The third guard is structural: this row is the one CE-0 deliberately withheld from the sidebar
 * ("nav-to-nowhere is the F2-02 defect"), so its route has to exist.
 */
import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import {
  CAB_REASON_TONE, CAB_REASON_LADDER, CAB_SUBJECT_STATES, isOrphanRow, isReferable,
} from '../pages/change/CabWorkspacePage';
import { agendaTopic, type AgendaItem } from '../lib/committees';
import { en } from '../translations/en';
import { ar } from '../translations/ar';
import type { CabPendingDto, CabQueueRowDto } from '../lib/api';

const BACKEND = join(__dirname, '..', '..', '..', 'tss-grc-backend', 'src', 'main', 'java',
                     'com', 'tss', 'grc', 'module');

function java(rel: string): string {
  return readFileSync(join(BACKEND, rel), 'utf8');
}

const row = (state: string): CabQueueRowDto => ({ referralId: 1, subjectState: state });
const pending = (reason: string): CabPendingDto =>
  ({ id: 1, titleEn: 't', status: 'CAB_PENDING', gate: 'CAB', reason });

describe('CE-13 the reason ladder', () => {
  it('matches the server ladder exactly, in order', () => {
    // Read out of ChangeCabQueueService.Pending.REASONS rather than restated, because a list restated
    // in two places is the mirror that eventually lies — and the ORDER is the meaning here.
    const src = java('change_mgmt/service/ChangeCabQueueService.java');
    const decl = /REASONS\s*=\s*List\.of\(([^;]*)\);/s.exec(src);
    expect(decl, 'Pending.REASONS must be readable from the service').toBeTruthy();
    const server = decl![1].split(',').map(s => s.trim()).filter(Boolean);

    expect(server.length).toBe(CAB_REASON_LADDER.length);
    // The Java list refers to the constants by name; each name's value is the screaming-snake literal.
    expect(server).toEqual([
      'GATE_UNMAPPED', 'NOT_REFERRED', 'SUBJECT_UNCITEABLE',
      'NO_SITTING_SCHEDULED', 'NOT_TABLED', 'TABLED',
    ]);
    expect([...CAB_REASON_LADDER]).toEqual(server);
  });

  it('tones every rung, and only TABLED is good news', () => {
    for (const r of CAB_REASON_LADDER) expect(CAB_REASON_TONE[r], r).toBeTruthy();
    const good = Object.entries(CAB_REASON_TONE).filter(([, tone]) => tone === 'success');
    expect(good.map(([k]) => k)).toEqual(['TABLED']);
    // With no committee named a change cannot even be referred, so this is danger, not warning.
    // ⚠ The rung was GATE_UNCONFIGURED until the live probe showed that name keyed on a flag folding
    // three faults together (F2-96) and labelled rows unfixable on a gate whose referral succeeded.
    expect(CAB_REASON_TONE.GATE_UNMAPPED).toBe('danger');
    expect(CAB_REASON_TONE.GATE_UNCONFIGURED, 'the collapsed rung must not come back').toBeUndefined();
    expect(CAB_REASON_TONE.NOT_REFERRED).toBe('danger');
  });

  it('labels every rung in both languages', () => {
    for (const r of CAB_REASON_LADDER) {
      expect(en[`cabReason_${r}` as keyof typeof en], `en ${r}`).toBeTruthy();
      expect(ar[`cabReason_${r}` as keyof typeof ar], `ar ${r}`).toBeTruthy();
    }
  });

  it('offers the referral action only on the rung it actually fixes', () => {
    expect(isReferable(pending('NOT_REFERRED'))).toBe(true);
    // On an unconfigured gate the referral throws; on NOT_TABLED the change is already referred and a
    // second referral would be a duplicate. Both would be buttons whose only outcome is an error.
    for (const r of ['GATE_UNMAPPED', 'SUBJECT_UNCITEABLE', 'NO_SITTING_SCHEDULED',
                     'NOT_TABLED', 'TABLED']) {
      expect(isReferable(pending(r)), r).toBe(false);
    }
  });
});

describe('CE-13 subject states', () => {
  it('matches the server states exactly', () => {
    const src = java('change_mgmt/service/ChangeCabQueueService.java');
    const decl = /STATES\s*=\s*List\.of\(([^;]*)\);/s.exec(src);
    expect(decl, 'Row.STATES must be readable from the service').toBeTruthy();
    expect(decl![1].split(',').map(s => s.trim()).filter(Boolean))
      .toEqual(['PRESENT', 'SUBJECT_NOT_STATED', 'SUBJECT_NOT_FOUND']);
    expect([...CAB_SUBJECT_STATES])
      .toEqual(['PRESENT', 'SUBJECT_NOT_STATED', 'SUBJECT_NOT_FOUND']);
  });

  it('counts only PRESENT as a real queue item', () => {
    expect(isOrphanRow(row('PRESENT'))).toBe(false);
    expect(isOrphanRow(row('SUBJECT_NOT_STATED'))).toBe(true);
    expect(isOrphanRow(row('SUBJECT_NOT_FOUND'))).toBe(true);
    // An unknown state the server might add later must count as an orphan, not silently as a real
    // item: a queue that flatters itself when it does not recognise a code is worse than one that
    // over-reports.
    expect(isOrphanRow(row('SOMETHING_NEW'))).toBe(true);
  });

  it('labels every state, and explains the two that need clearing', () => {
    for (const s of CAB_SUBJECT_STATES) {
      expect(en[`cabSubject_${s}` as keyof typeof en], `en ${s}`).toBeTruthy();
      expect(ar[`cabSubject_${s}` as keyof typeof ar], `ar ${s}`).toBeTruthy();
    }
    for (const k of ['cabSubjectNotFoundHint', 'cabSubjectNotStatedHint'] as const) {
      expect(en[k]).toBeTruthy();
      expect(ar[k]).toBeTruthy();
    }
  });
});

describe('CE-13 the agenda key mismatch', () => {
  const seeded: AgendaItem = { seq: 1, titleEn: 'Approve the portfolio', titleAr: 'اعتماد المحفظة' };
  const built: AgendaItem   = { seq: 1, topicEn: 'For decision: CHG-4', topicAr: 'للقرار: CHG-4' };

  it('reads both key shapes — 24 of 34 dev agendas were the one this list ignored', () => {
    expect(agendaTopic(seeded, false)).toBe('Approve the portfolio');
    expect(agendaTopic(seeded, true)).toBe('اعتماد المحفظة');
    expect(agendaTopic(built, false)).toBe('For decision: CHG-4');
    expect(agendaTopic(built, true)).toBe('للقرار: CHG-4');
  });

  it('falls through to the other language rather than to a blank bullet', () => {
    expect(agendaTopic({ seq: 1, topicEn: 'Matters arising' }, true)).toBe('Matters arising');
    expect(agendaTopic({ seq: 1, titleEn: 'Matters arising' }, true)).toBe('Matters arising');
  });

  it('returns empty for an item that names nothing, and never crashes', () => {
    expect(agendaTopic({ seq: 1 }, false)).toBe('');
    expect(agendaTopic({ seq: 1, topicEn: '   ' }, false)).toBe('');
  });

  it('the committee agenda list no longer reads one key directly', () => {
    const page = readFileSync(join(__dirname, '..', 'pages', 'committees', 'CommitteesPage.tsx'), 'utf8')
      .replace(/\/\*[\s\S]*?\*\//g, ' ').replace(/^\s*\/\/.*$/gm, ' ');
    expect(page).toContain('agendaTopic(a, lang === \'ar\')');
    expect(page, 'the direct read is what printed empty bullets for every real committee')
      .not.toContain('a.topicAr ? a.topicAr : a.topicEn');
  });
});

describe('CE-13 the row CE-0 withheld', () => {
  it('has a route, so the sidebar entry is not nav-to-nowhere', () => {
    const app = readFileSync(join(__dirname, '..', 'App.tsx'), 'utf8');
    expect(app).toContain('path="/changes/cab"');
    expect(app).toContain('CabWorkspacePage');

    const sidebar = readFileSync(join(__dirname, '..', 'components', 'layout', 'Sidebar.tsx'), 'utf8');
    expect(sidebar).toContain("to: '/changes/cab'");
    expect(sidebar).toContain("labelKey: 'cabNavQueue'");
    expect(en.cabNavQueue).toBeTruthy();
    expect(ar.cabNavQueue).toBeTruthy();
  });
});

describe('CE-13 the two gate flags', () => {
  it('labels both — a named-but-seatless committee is not an unmapped gate (F2-96)', () => {
    // The live probe's finding, pinned: `mapped` gates the ladder, `canDecide` warns that the
    // approval will be refused. One sentence for each, or the screen tells an operator to re-map a
    // mapping that is already correct.
    for (const k of ['cabGateUnmapped', 'cabCannotDecide'] as const) {
      expect(en[k], `en ${k}`).toBeTruthy();
      expect(ar[k], `ar ${k}`).toBeTruthy();
    }
    const page = readFileSync(join(__dirname, '..', 'pages', 'change', 'CabWorkspacePage.tsx'), 'utf8')
      .replace(/\/\*[\s\S]*?\*\//g, ' ').replace(/^\s*\/\/.*$/gm, ' ');
    expect(page).toContain('!gate.mapped');
    expect(page).toContain('gate.mapped && !gate.canDecide');
    expect(page, 'the collapsed flag must not gate anything').not.toContain('gate.configured');
  });
});
