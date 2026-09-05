// Corrective & Preventive Action (CAPA) — the client for the real register.
//
// Standards anchor (unchanged — these are why the module exists):
//   ISO 9001:2015 §10.2      — Nonconformity & corrective action
//   ISO 27001:2022 §10.1-2   — Continual improvement + nonconformity
//   FDA 21 CFR §820.100      — CAPA procedure incl. verification of effectiveness
//   IIA IPPF 2500            — Monitoring of management's action
//   NCA ECC 1.5              — Continual cybersecurity improvement
//
// ─────────────────────────────────────────────────────────────────────────────
// ⚠⚠ F2-208 — WHAT THIS FILE USED TO BE, because the shape recurs.
//
// It was 1,008 lines, of which 507 were `CAPAS`: twenty invented corrective
// actions with eleven invented owner mailboxes at the real company domain, two
// of them carrying `auditSignedOffBy: 'audit.lead@tss.sa'` — an invented auditor
// recorded as having signed off remediation. `useCapas()` MERGED those with the
// real rows from `GET /api/capa/tasks` and with a `localStorage` overlay, so:
//
//   • the register showed 34 rows where 14 exist (measured, dev = prod), with
//     nothing distinguishing the invented ones;
//   • `create()` appended to the overlay and returned. `POST /api/capa/tasks`
//     had existed the whole time;
//   • `advance()` wrote a patch to the overlay. `POST /{id}/transition` had
//     existed the whole time — and the patch was applied on top of the REAL
//     server rows when the list was assembled, so advancing a real CAPA to
//     "audit signed-off" changed what the screen said about a row the server
//     had never been told about.
//
// 🔑 The part worth carrying forward: a localStorage overlay is DURABLE, so the
// reload that would expose the fabrication confirms it instead. F2-201's
// fabricated approvals page reverted on refresh, which is what eventually gave
// it away; this one did not.
//
// 🔑 And the vocabulary was the tell, if anyone had compared it: the local
// machine declared EIGHT states (IDENTIFIED / IMPLEMENTING / OWNER_TESTED /
// INDEPENDENT_TESTED / AUDIT_SIGNED_OFF / CLOSED / VERIFICATION_FAILED /
// CANCELLED) against the server's FIVE. Three of them could not arrive from the
// server at all — `fromCapaTaskDto`'s STATUS_MAP had no source for them — so
// they could only ever be produced by a local write. A state only the browser
// can reach is a state only the browser knows about.
//
// F2-207's rule, applied in the other direction: there a browser corpus could
// not be wired because its tokens were REFUSED by the target column's CHECK, so
// the answer was retire. Here the server's machine is simply COARSER, and the
// answer is to drive the server's five and stop advertising eight — the same
// judgement F2-201 made when a fabricated approval chain met a server that
// carries one assignee. A fabrication that is more granular than the platform's
// own model is what makes it look more finished (F2-199).
//
// DELETED with the corpus, because no column exists for any of them anywhere in
// `capa.tasks` (26 columns, read from sys.columns — not inferred):
//   rcaMethod / rcaSteps / rootCause / rootCauseCategory  (the RCA tab)
//   effectivenessDueDate / effectivenessVerified*         (the 90-day re-test)
//   parentCapaId                                          (recurrence chains)
//   customerDisclosureRequired / customerDisclosureDate   (21 CFR §820.100)
//   frameworkRefs, ownerTest*, independentTest*, auditSignedOffBy, history
// Capturing any of them would have needed a migration and a product decision;
// keeping them as client state was the defect, and inventing values to fill the
// resulting gaps would have been F2-196's. They are filed, not silently dropped.
//
// RESTORED at the same time — three real columns the old client never read
// (F2-198's `crossBorderTransfer` shape, where the value is on the wire and the
// client's own type is what never mentioned it):
//   progressNotesEn / progressNotesAr / evidenceRefs
// plus `createdAt`, which the server DTO did not declare, so a CAPA's age was
// being computed from `startedAt ?? targetDate`.
// ─────────────────────────────────────────────────────────────────────────────

import { useCallback, useEffect, useState } from 'react';
import {
  capaTaskApi,
  type CapaTaskDto,
  type CapaTaskTransitionRequest,
} from './api';

// ── Domain types ────────────────────────────────────────────────────

export type CapaType = 'CORRECTIVE' | 'PREVENTIVE';

/**
 * The server's status vocabulary — `CapaTaskService.ALLOWED`'s key set, and the
 * exact five tokens `TransitionRequest.status`'s `@Pattern` admits.
 *
 * ⚠ Do not add a state the server cannot store. `CapaMachineParityTest` compares
 * this union and CAPA_ALLOWED with the service's own table by reflection, in both
 * directions, and fails the build on a difference. That gate exists because the
 * eight-state version of this union outlived the register it was supposed to
 * describe.
 */
export type CapaStatus =
  | 'OPEN'
  | 'IN_PROGRESS'
  | 'AWAITING_VERIFICATION'
  | 'CLOSED'
  | 'CANCELLED';

export type CapaSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

/**
 * Display grouping over the real `origin_type` column, which is free text: the
 * fourteen live rows carry `control_test`, `audit_finding`, `dpia`, `incident`,
 * `inspection_finding`, `risk_treatment`, `rca` and `processor_assessment`.
 *
 * ⚠ This is a LABEL MAP, not a vocabulary — `origin_type` has no CHECK
 * constraint (the whole `capa` schema has none), so an unrecognised origin must
 * fall through to OTHER rather than crash. F2-203's landmine: a bare
 * `MAP[row.field][lang]` on a column nothing constrains is a TypeError waiting
 * for a row somebody else wrote.
 */
export type CapaSource =
  | 'AUDIT_FINDING'
  | 'RISK_TREATMENT'
  | 'DPIA_MITIGATION'
  | 'INCIDENT_LL'
  | 'VENDOR_FINDING'
  | 'CONTROL_GAP'
  | 'KRI_BREACH'
  | 'OTHER';

/** A CAPA as the register renders it. Every field is a column on `capa.tasks`. */
export interface Capa {
  /** `external_id` — the reference an auditor cites. Server-minted (F2-208). */
  id: string;
  /** `id` (Long PK) — threads EntityActivityTimeline + EntityAutomationRuns. */
  backendId?: number;
  titleEn: string;
  titleAr: string;
  descriptionEn?: string;
  descriptionAr?: string;

  type: CapaType;
  /** Derived from `originType` for grouping; the raw value is kept beside it. */
  source: CapaSource;
  originType: string;
  originId: string;

  severity: CapaSeverity;
  owner: string;
  status: CapaStatus;

  /** `created_at` — when the CAPA was raised. */
  createdAt?: string;
  /** `target_date` — the committed completion date. */
  dueDate: string;
  startedAt?: string;
  submittedForVerificationAt?: string;
  /** Independent verification, stamped when the server closes the CAPA. */
  verifiedAt?: string;
  /** The authenticated principal who closed it — never a client-supplied name. */
  verifiedBy?: string;
  closedAt?: string;
  cancelledAt?: string;
  cancellationReason?: string;

  progressNotesEn?: string;
  progressNotesAr?: string;
  evidenceRefs?: string;
}

// ── The machine ─────────────────────────────────────────────────────

/**
 * `CapaTaskService.ALLOWED`, mirrored. The server answers 409 outside it.
 *
 * Terminal states (CLOSED, CANCELLED) are present with empty edge lists so the
 * table is total: `CAPA_ALLOWED[status]` is never undefined, and CAPA_TERMINAL
 * below is DERIVED from it rather than typed a second time (F2-196 — an
 * inclusion list over a vocabulary silently drops whatever nobody remembered).
 */
export const CAPA_ALLOWED: Record<CapaStatus, readonly CapaStatus[]> = {
  OPEN:                  ['IN_PROGRESS', 'CANCELLED'],
  IN_PROGRESS:           ['AWAITING_VERIFICATION', 'CANCELLED'],
  AWAITING_VERIFICATION: ['CLOSED', 'IN_PROGRESS', 'CANCELLED'],
  CLOSED:                [],
  CANCELLED:             [],
};

/** DERIVED: the states with no outgoing edge. Never written out by hand. */
export const CAPA_TERMINAL: readonly CapaStatus[] =
  (Object.keys(CAPA_ALLOWED) as CapaStatus[]).filter(s => CAPA_ALLOWED[s].length === 0);

/** "Still needs work" is the COMPLEMENT of the terminal set, never a second list. */
export function isCapaOpen(c: Capa): boolean {
  return !CAPA_TERMINAL.includes(c.status);
}

/**
 * DERIVED — the state nothing transitions INTO.
 *
 * F2-291's rule: an initial state is a property of the machine, not a second literal
 * beside it. ⚠ It can legitimately be ABSENT — a machine with a back-edge to its own
 * root has no such state, which is what F2-292 measured on the disclosure register —
 * so this returns null rather than guessing, and every caller handles that.
 */
function capaRootState(): CapaStatus | null {
  const states = Object.keys(CAPA_ALLOWED) as CapaStatus[];
  const targets = new Set<CapaStatus>(states.flatMap(s => [...CAPA_ALLOWED[s]]));
  const roots = states.filter(s => !targets.has(s));
  return roots.length === 1 ? roots[0] : null;
}

/**
 * DERIVED — the abandonment exit: the terminal state EVERY working state can reach
 * directly, as against the completion exit, which only the last working state reaches.
 *
 * That asymmetry is what distinguishes the two without naming either, which matters
 * because a literal here would be the second copy of the vocabulary F2-196 warns about.
 * Null when the machine has no such state, or more than one.
 */
export function capaAbandonState(): CapaStatus | null {
  const working = (Object.keys(CAPA_ALLOWED) as CapaStatus[])
    .filter(s => CAPA_ALLOWED[s].length > 0);
  if (working.length === 0) return null;
  const universal = CAPA_TERMINAL.filter(t => working.every(s => CAPA_ALLOWED[s].includes(t)));
  return universal.length === 1 ? universal[0] : null;
}

/**
 * DERIVED — the forward path: the working states in breadth-first distance order from
 * the root, ignoring the abandonment exit, ending at the completion exit.
 *
 * This exists so the workflow sentence the register renders is COMPOSED from the machine
 * rather than written beside it. F2-465: the prose named six stages — Identified,
 * Implementing, Owner-tested (1LoD), Independent-tested (2LoD), Audit signed-off (3LoD),
 * Closed — of which this register has one, and promised "a 90-day post-closure check
 * fires VERIFICATION_FAILED on regression" when `module/capa` carries no @Scheduled
 * method at all. It sat fourteen lines under the comment recording that the LANES had
 * been moved onto the machine for exactly that reason.
 *
 * ⚠ Distance order is used rather than a longest-path walk because the longest path is
 * NOT unique here: OPEN→IN_PROGRESS→AWAITING_VERIFICATION→CLOSED and the same prefix
 * ending at CANCELLED are both length 4. Distance from the root is a total order only
 * while the depths are distinct, so that is checked — and both fail-safe branches return
 * every real state instead, which claims no order but can never name a state the
 * register cannot store.
 */
export function capaForwardPath(): readonly CapaStatus[] {
  const states = Object.keys(CAPA_ALLOWED) as CapaStatus[];
  const root = capaRootState();
  if (!root) return states;
  const abandon = capaAbandonState();
  const depth = new Map<CapaStatus, number>([[root, 0]]);
  const queue: CapaStatus[] = [root];
  while (queue.length > 0) {
    const s = queue.shift() as CapaStatus;
    for (const next of CAPA_ALLOWED[s]) {
      if (next === abandon || depth.has(next)) continue;
      depth.set(next, (depth.get(s) as number) + 1);
      queue.push(next);
    }
  }
  if (new Set(depth.values()).size !== depth.size) return states;
  return [...depth.entries()].sort((a, b) => a[1] - b[1]).map(e => e[0]);
}

export const CAPA_STATUS_LABEL: Record<CapaStatus, { en: string; ar: string }> = {
  OPEN:                  { en: 'Open',                  ar: 'مفتوحة' },
  IN_PROGRESS:           { en: 'In progress',           ar: 'قيد التنفيذ' },
  AWAITING_VERIFICATION: { en: 'Awaiting verification', ar: 'بانتظار التحقق' },
  CLOSED:                { en: 'Closed',                ar: 'مغلقة' },
  CANCELLED:             { en: 'Cancelled',             ar: 'ملغاة' },
};

export const CAPA_STATUS_TONE: Record<CapaStatus, 'neutral' | 'info' | 'primary' | 'accent' | 'success' | 'warning' | 'danger'> = {
  OPEN:                  'neutral',
  IN_PROGRESS:           'info',
  AWAITING_VERIFICATION: 'accent',
  CLOSED:                'success',
  CANCELLED:             'neutral',
};

export const CAPA_TYPE_LABEL: Record<CapaType, { en: string; ar: string }> = {
  CORRECTIVE: { en: 'Corrective', ar: 'تصحيحي' },
  PREVENTIVE: { en: 'Preventive', ar: 'وقائي' },
};

export const CAPA_SEVERITY_TONE: Record<CapaSeverity, 'risk-low' | 'risk-med' | 'risk-high' | 'risk-crit'> = {
  LOW: 'risk-low', MEDIUM: 'risk-med', HIGH: 'risk-high', CRITICAL: 'risk-crit',
};

export const CAPA_SOURCE_LABEL: Record<CapaSource, { en: string; ar: string }> = {
  AUDIT_FINDING:   { en: 'Audit finding',        ar: 'ملاحظة مراجعة' },
  RISK_TREATMENT:  { en: 'Risk treatment',       ar: 'معالجة مخاطر' },
  DPIA_MITIGATION: { en: 'DPIA mitigation',      ar: 'تخفيف أثر الخصوصية' },
  INCIDENT_LL:     { en: 'Incident lesson',      ar: 'درس من حادث' },
  VENDOR_FINDING:  { en: 'Vendor finding',       ar: 'ملاحظة مورّد' },
  CONTROL_GAP:     { en: 'Control gap',          ar: 'فجوة ضابط' },
  KRI_BREACH:      { en: 'KRI breach',           ar: 'تجاوز مؤشر' },
  OTHER:           { en: 'Other',                ar: 'أخرى' },
};

// ── Derived, from real columns only ─────────────────────────────────

/** Days until / since the committed target date (negative = overdue). */
export function daysUntilDue(c: Capa, today: Date = new Date()): number {
  const due = new Date(c.dueDate);
  return Math.round((due.getTime() - today.getTime()) / 86_400_000);
}

/**
 * Aging bucket for the register.
 *
 * ⚠ Age is measured from `createdAt` — the column that records when the CAPA was
 * raised. The previous version used `startedAt ?? targetDate`, i.e. when work
 * began or, failing that, the deadline, because the server DTO did not expose
 * `created_at`. A CAPA that had not been started yet therefore aged from its own
 * due date and could never be old.
 *
 * Returns 'UNKNOWN' rather than guessing when the origin date is absent: an
 * unmeasurable age is not the same fact as a young CAPA (F2-197).
 */
export function agingBucket(
  c: Capa, today: Date = new Date(),
): '0-30' | '31-60' | '61-90' | '90+' | 'OVERDUE' | 'CLOSED' | 'UNKNOWN' {
  if (!isCapaOpen(c)) return 'CLOSED';
  if (daysUntilDue(c, today) < 0) return 'OVERDUE';
  if (!c.createdAt) return 'UNKNOWN';
  const ageDays = Math.round((today.getTime() - new Date(c.createdAt).getTime()) / 86_400_000);
  if (ageDays <= 30) return '0-30';
  if (ageDays <= 60) return '31-60';
  if (ageDays <= 90) return '61-90';
  return '90+';
}

/** Count by status, over the server's five. */
export function capaStatusCounts(items: Capa[]): Record<CapaStatus, number> {
  const empty: Record<CapaStatus, number> = {
    OPEN: 0, IN_PROGRESS: 0, AWAITING_VERIFICATION: 0, CLOSED: 0, CANCELLED: 0,
  };
  for (const c of items) {
    if (empty[c.status] !== undefined) empty[c.status] += 1;
  }
  return empty;
}

// ── Projection ──────────────────────────────────────────────────────

const SOURCE_BY_ORIGIN: Record<string, CapaSource> = {
  audit_finding:        'AUDIT_FINDING',
  auditfinding:         'AUDIT_FINDING',
  finding:              'AUDIT_FINDING',
  inspection_finding:   'AUDIT_FINDING',
  risk_treatment:       'RISK_TREATMENT',
  risk:                 'RISK_TREATMENT',
  dpia:                 'DPIA_MITIGATION',
  incident:             'INCIDENT_LL',
  cyberincident:        'INCIDENT_LL',
  rca:                  'INCIDENT_LL',
  vendor:               'VENDOR_FINDING',
  processor_assessment: 'VENDOR_FINDING',
  control_test:         'CONTROL_GAP',
  bcmexercise:          'CONTROL_GAP',
  dr_test:              'CONTROL_GAP',
  kri:                  'KRI_BREACH',
};

/** Widen an unrecognised server status rather than crashing on it. */
function statusOf(raw: string | undefined): CapaStatus {
  const s = (raw ?? 'OPEN').trim().toUpperCase();
  return (s in CAPA_ALLOWED ? s : 'OPEN') as CapaStatus;
}

function severityOf(raw: string | undefined): CapaSeverity {
  const s = (raw ?? '').trim().toUpperCase();
  return (['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'] as const).includes(s as CapaSeverity)
    ? (s as CapaSeverity) : 'MEDIUM';
}

export function fromCapaTaskDto(dto: CapaTaskDto): Capa {
  return {
    id: dto.id,
    backendId: dto.numericId,
    titleEn: dto.titleEn,
    titleAr: dto.titleAr,
    descriptionEn: dto.descriptionEn ?? undefined,
    descriptionAr: dto.descriptionAr ?? undefined,
    type: dto.capaType === 'PREVENTIVE' ? 'PREVENTIVE' : 'CORRECTIVE',
    source: SOURCE_BY_ORIGIN[(dto.originType ?? '').trim().toLowerCase()] ?? 'OTHER',
    originType: dto.originType,
    originId: dto.originId,
    severity: severityOf(dto.severity),
    owner: dto.owner,
    status: statusOf(dto.status),
    createdAt: dto.createdAt ?? undefined,
    dueDate: dto.targetDate,
    startedAt: dto.startedAt ?? undefined,
    submittedForVerificationAt: dto.submittedForVerificationAt ?? undefined,
    verifiedAt: dto.verifiedAt ?? undefined,
    verifiedBy: dto.verifiedBy ?? undefined,
    closedAt: dto.closedAt ?? undefined,
    cancelledAt: dto.cancelledAt ?? undefined,
    cancellationReason: dto.cancellationReasonEn ?? undefined,
    progressNotesEn: dto.progressNotesEn ?? undefined,
    progressNotesAr: dto.progressNotesAr ?? undefined,
    evidenceRefs: dto.evidenceRefs ?? undefined,
  };
}

// ── The hook ────────────────────────────────────────────────────────

export interface CapaRegister {
  items: Capa[];
  loading: boolean;
  /** Non-null when the register could not be read. The list is then EMPTY. */
  error: string | null;
  refresh: () => Promise<void>;
  transition: (id: string, body: CapaTaskTransitionRequest) => Promise<Capa>;
}

/**
 * The CAPA register, server-backed end to end.
 *
 * ⚠ Three properties this hook must keep, each of which was violated before:
 *
 *  1. **No fallback corpus.** A read failure surfaces as `error` and an empty
 *     list. It must never substitute rows — F2-158's mock fallback made a 500
 *     invisible for the whole life of an endpoint.
 *  2. **No local status write.** `transition` replaces the row with what the
 *     server returned. Nothing computes the next status locally, so an illegal
 *     edge surfaces as the server's 409 instead of a screen that disagrees with
 *     the register.
 *  3. **No create.** See the note above `capaTaskApi`: the register is filled by
 *     the intake substrate and by other modules' automation, and the only caller
 *     a `create` would have had was a modal fed by fabricated triggers.
 */
export function useCapas(): CapaRegister {
  const [items, setItems] = useState<Capa[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const dtos = await capaTaskApi.list();
      setItems(dtos.map(fromCapaTaskDto));
      setError(null);
    } catch (e) {
      // Empty + an error, never a substituted corpus.
      setItems([]);
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void refresh(); }, [refresh]);

  const transition = useCallback(async (id: string, body: CapaTaskTransitionRequest) => {
    const saved = fromCapaTaskDto(await capaTaskApi.transition(id, body));
    setItems(prev => prev.map(c => (c.id === id ? saved : c)));
    return saved;
  }, []);

  return { items, loading, error, refresh, transition };
}
