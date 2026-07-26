// Aggregated cross-entity search index for the Cmd/Ctrl+K command palette.
//
// W2-1a (F2-02) — this file used to import EIGHT mock datasets (mockRisks,
// mockCompliance, mockEvidence, mockTprm, mockPrivacy, mockCyber, mockIncidents,
// mockChanges) and index them, so the palette confidently returned risks, findings,
// controls, vendors, DPIAs, incidents and changes that DO NOT EXIST in the tenant —
// with refs and hrefs that looked real and led to 404s or to another tenant's ids.
// A search box that invents records is worse than one that finds nothing, because the
// user cannot tell the difference.
//
// Entity items are now populated ONLY from the live API, by hydrateSearchIndex().
// Until that resolves the palette indexes pages and the static regulator catalogue and
// nothing else — an honest empty result rather than a fabricated one. CH-9 established
// this pattern for policies; this generalises it to every kind.
//
// AUTHORITIES is deliberately kept: lib/authorities.ts is the static catalogue of real
// Saudi regulators (NCA, SAMA, SDAIA, CMA…) and their frameworks. It is reference data
// that ships with the product, not fabricated tenant data.
import type { LucideIcon } from 'lucide-react';
import {
  AlertTriangle, ClipboardCheck, BookCopy, FileCheck2, Truck,
  Fingerprint, Activity, Landmark, AlertOctagon,
  LockKeyhole, RefreshCw, Workflow, ShieldAlert, Files, GitBranch,
  Stamp, LayoutDashboard, Calendar, Bell, ListChecks,
} from 'lucide-react';

import { AUTHORITIES } from './authorities';
import {
  riskApi, complianceApi, dpiaApi, tprmApi, incidentApi, changeApi,
} from './api';
import { listEvidenceItems } from './evidenceItems';
import { kriApi } from './kriEngine';

export type EntityKind =
  | 'PAGE' | 'RISK' | 'FINDING' | 'CONTROL' | 'POLICY' | 'EVIDENCE'
  | 'AUTHORITY' | 'VENDOR' | 'DPIA' | 'KRI' | 'ENGAGEMENT' | 'INCIDENT' | 'CHANGE';
// ENGAGEMENT is declared but never hydrated: audit engagements have no list endpoint in
// lib/ (auditEngagement.ts only derives milestones/workflows from one already fetched), so
// the honest state is zero indexed engagements rather than the mock set this file used to
// carry. The kind and its `audit:` search prefix stay so wiring it later is a one-entry
// change to the hydrate task list.

export interface SearchItem {
  id: string;             // unique-per-kind id
  kind: EntityKind;
  title: string;
  subtitle?: string;
  ref?: string;           // monospaced reference / code chip ("R-0042", "AUD-F-031")
  href: string;
  Icon: LucideIcon;
  /** Free-form tokens used for matching beyond title (status, owner, etc.). */
  keywords?: string[];
}

const PAGE_ITEMS: SearchItem[] = [
  { id: 'page-dashboard',  kind: 'PAGE', title: 'Dashboard',           href: '/dashboard',       Icon: LayoutDashboard, keywords: ['home', 'overview'] },
  { id: 'page-audit-ws',   kind: 'PAGE', title: 'Audit Workspace',     href: '/audit-workspace', Icon: Stamp,           keywords: ['auditor', 'sign-off', 'capa'] },
  { id: 'page-authorities',kind: 'PAGE', title: 'Regulatory Authorities', href: '/authorities',  Icon: Landmark,        keywords: ['regulators', 'nca', 'sama', 'sdaia'] },
  { id: 'page-comms',      kind: 'PAGE', title: 'Communications',      href: '/communications',  Icon: Stamp,           keywords: ['stakeholders', 'notifications', 'raci', 'comms', 'sla'] },
  { id: 'page-risk',       kind: 'PAGE', title: 'Enterprise Risk',     href: '/risk',            Icon: AlertTriangle,   keywords: ['register', 'iso 31000'] },
  { id: 'page-compliance', kind: 'PAGE', title: 'Compliance & Audit',  href: '/compliance',      Icon: ClipboardCheck,  keywords: ['controls', 'findings', 'capa'] },
  { id: 'page-cyber',      kind: 'PAGE', title: 'Cybersecurity',       href: '/cyber',           Icon: LockKeyhole,     keywords: ['nca ecc', 'iso 27001'] },
  { id: 'page-privacy',    kind: 'PAGE', title: 'Privacy & PDPL',      href: '/privacy',         Icon: Fingerprint,     keywords: ['pdpl', 'sdaia', 'dsr', 'breach'] },
  { id: 'page-bcm',        kind: 'PAGE', title: 'Business Continuity', href: '/bcm',             Icon: RefreshCw,       keywords: ['bcp', 'iso 22301', 'ncema'] },
  { id: 'page-tprm',       kind: 'PAGE', title: 'Third-Party Risk',    href: '/tprm',            Icon: Workflow,        keywords: ['vendor', 'tprm'] },
  { id: 'page-scm',        kind: 'PAGE', title: 'Supply Chain',        href: '/scm',             Icon: Truck,           keywords: ['supplier', 'tier mapping', 'nrc'] },
  { id: 'page-ethics',     kind: 'PAGE', title: 'Ethics & Compliance', href: '/ethics',          Icon: ShieldAlert,     keywords: ['aml', 'kyc', 'whistleblower', 'nazaha'] },
  { id: 'page-incidents',  kind: 'PAGE', title: 'Incident Management', href: '/incidents',       Icon: AlertOctagon,    keywords: ['nist 800-61', 'csirt'] },
  { id: 'page-evidence',   kind: 'PAGE', title: 'Evidence Management', href: '/evidence',        Icon: Files,           keywords: ['attestation', 'capa'] },
  { id: 'page-changes',    kind: 'PAGE', title: 'Change Management',   href: '/changes',         Icon: GitBranch,       keywords: ['itil', 'cab'] },
  { id: 'page-workflows',  kind: 'PAGE', title: 'Workflow Definitions',href: '/workflows',       Icon: Workflow },
  { id: 'page-wf-subs',    kind: 'PAGE', title: 'Workflow Submissions',href: '/workflows/submissions', Icon: ListChecks, keywords: ['submissions', 'register'] },
  { id: 'page-calendar',   kind: 'PAGE', title: 'Compliance Calendar', href: '/calendar',        Icon: Calendar,        keywords: ['outlook', 'deadlines', 'reminders', 'capa due', 'report deadline', 'hijri', 'saudi workdays'] },
  { id: 'page-inbox-notif',kind: 'PAGE', title: 'Notifications Inbox', href: '/notifications',   Icon: Bell,            keywords: ['inbox', 'notify', 'escalation', 'f8', 'automation'] },
  { id: 'page-capa',       kind: 'PAGE', title: 'CAPA Register',       href: '/capa',            Icon: ClipboardCheck,  keywords: ['corrective', 'preventive', 'remediation', 'actions'] },
  { id: 'page-findings',   kind: 'PAGE', title: 'Findings',            href: '/findings',        Icon: AlertTriangle,   keywords: ['audit findings', 'observation'] },
  { id: 'page-ndi',        kind: 'PAGE', title: 'NDI — National Digital Index', href: '/ndi',    Icon: Activity,        keywords: ['dga', 'national digital index', 'maturity', 'compliance', 'kpi'] },
  { id: 'page-licensing',  kind: 'PAGE', title: 'Module Licensing',    href: '/admin/licensing', Icon: ShieldAlert,     keywords: ['license', 'enabled modules', 'features', 'admin'] },
];

// ── live entity items, per kind ──────────────────────────────────────────────
// Populated by hydrateSearchIndex() from the real API. A kind absent from this map
// contributes NOTHING to the index — the palette then finds no risks rather than
// finding invented ones.
type LiveItems = Partial<Record<EntityKind, SearchItem[]>>;
let LIVE: LiveItems = {};

/** The regulator catalogue is static product reference data, always indexable. */
function authorityItems(): SearchItem[] {
  return AUTHORITIES.map(a => ({
    id: `authority-${a.code}`, kind: 'AUTHORITY' as const,
    title: a.nameEn,
    subtitle: `${a.taglineEn ?? ''} · ${a.jurisdiction}`,
    ref: a.code,
    href: `/authority/${a.code}`,
    Icon: Landmark,
    keywords: [a.code, a.nameAr, a.jurisdiction, ...a.domains, ...a.frameworkCodes],
  }));
}

let CACHE: SearchItem[] | null = null;
function getIndex(): SearchItem[] {
  if (CACHE) return CACHE;
  const live: SearchItem[] = [];
  for (const kind of Object.keys(LIVE) as EntityKind[]) {
    live.push(...(LIVE[kind] ?? []));
  }
  CACHE = [...PAGE_ITEMS, ...authorityItems(), ...live];
  return CACHE;
}

/** What the index currently holds — the palette shows this instead of pretending. */
export interface SearchIndexStatus {
  /** true once hydrateSearchIndex() has completed at least one pass. */
  hydrated: boolean;
  /** Entity kinds that returned data. */
  kinds: EntityKind[];
  /** Live entity items indexed (excludes pages and the regulator catalogue). */
  entityItems: number;
  /** Kinds whose fetch failed — the palette can say so rather than imply "none exist". */
  failed: EntityKind[];
}

let HYDRATED = false;
let FAILED: EntityKind[] = [];

export function searchIndexStatus(): SearchIndexStatus {
  const kinds = (Object.keys(LIVE) as EntityKind[]).filter(k => (LIVE[k] ?? []).length > 0);
  return {
    hydrated: HYDRATED,
    kinds,
    entityItems: kinds.reduce((n, k) => n + (LIVE[k] ?? []).length, 0),
    failed: [...FAILED],
  };
}

/** Longest field wins as the title; keeps the mappers below readable. */
function firstText(...vals: Array<string | null | undefined>): string {
  return vals.find(v => v != null && String(v).trim() !== '') ?? '—';
}

/**
 * Fetch every indexable kind from the live API and rebuild the index.
 *
 * Best-effort per kind: one module being unlicensed, empty or down must not blank the
 * whole palette, so each fetch is settled independently and a failure is RECORDED in
 * {@link searchIndexStatus} rather than silently treated as "no records". That
 * distinction is the point of W2-1a — the previous implementation could not tell
 * "nothing found" from "nothing loaded", because it always had mock rows to show.
 *
 * Page size is capped per kind: the palette is a jump-to, not a report. A tenant with
 * 5,000 risks does not need all of them in memory to satisfy a prefix search, and the
 * registers themselves are the place to browse exhaustively.
 */
export async function hydrateSearchIndex(): Promise<SearchIndexStatus> {
  const CAP = 200;
  const next: LiveItems = {};
  const failed: EntityKind[] = [];

  const tasks: Array<[EntityKind, () => Promise<SearchItem[]>]> = [
    ['RISK', async () => (await riskApi.list(undefined, 0, CAP)).content.map(r => ({
      id: `risk-${r.id}`, kind: 'RISK' as const,
      title: firstText(r.titleEn, r.titleAr),
      subtitle: `${r.categoryNameEn ?? '—'} · owner ${r.ownerFullNameEn ?? '—'}`,
      ref: r.riskCode, href: `/risk/${r.id}`, Icon: AlertTriangle,
      keywords: [r.status ?? '', r.riskLevel ?? '', r.categoryNameEn ?? ''],
    }))],
    ['FINDING', async () => (await complianceApi.findings(0, CAP)).content.map(f => ({
      id: `finding-${f.id}`, kind: 'FINDING' as const,
      title: firstText(f.title),
      subtitle: `${f.severity ?? '—'} · owner ${f.owner ?? '—'} · ${f.status ?? '—'}`,
      ref: `AUD-F-${String(f.id).padStart(3, '0')}`,
      href: `/findings/${f.id}`, Icon: AlertTriangle,
      keywords: [f.severity ?? '', f.status ?? '', f.capaStage ?? '', f.clauseRef ?? ''],
    }))],
    ['CONTROL', async () => (await complianceApi.controls(0, CAP)).content.map(c => ({
      id: `control-${c.id}`, kind: 'CONTROL' as const,
      title: firstText(c.nameEn, c.nameAr),
      subtitle: `${c.framework} · ${c.status} · L${c.maturityLevel ?? 0}`,
      ref: c.code, href: `/controls/${c.id}`, Icon: ClipboardCheck,
      keywords: [c.framework, c.status, c.domain ?? '', c.owner ?? ''],
    }))],
    ['EVIDENCE', async () => (await listEvidenceItems({ size: CAP })).content.map(e => ({
      id: `evidence-${e.id}`, kind: 'EVIDENCE' as const,
      title: firstText(e.titleEn, e.titleAr),
      subtitle: `${(e.evidenceType ?? '').replace(/_/g, ' ')} · ${e.status ?? ''}`,
      ref: e.evidenceCode, href: `/evidence`, Icon: FileCheck2,
      keywords: [e.evidenceType ?? '', e.status ?? ''],
    }))],
    ['VENDOR', async () => (await tprmApi.vendors(0, CAP)).content.map(v => ({
      id: `vendor-${v.id}`, kind: 'VENDOR' as const,
      title: firstText(v.vendorName, v.vendorNameAr),
      subtitle: `${v.vendorType ?? '—'} · ${v.riskRating ?? '—'} · ${v.status ?? ''}`,
      href: `/vendors/${v.id}`, Icon: Truck,
      keywords: [v.riskRating ?? '', v.status ?? '', v.vendorType ?? ''],
    }))],
    ['DPIA', async () => (await dpiaApi.list(0, CAP)).content.map(d => ({
      id: `dpia-${d.id}`, kind: 'DPIA' as const,
      title: firstText(d.titleEn, d.titleAr),
      subtitle: `${(d.triggerReason ?? '').replace(/_/g, ' ')} · ${d.status ?? ''}`,
      ref: `DPIA-${d.id}`, href: `/dpias/${d.id}`, Icon: Fingerprint,
      keywords: [d.status ?? '', d.triggerReason ?? ''],
    }))],
    ['INCIDENT', async () => (await incidentApi.list(0, CAP)).content.map(i => ({
      id: `incident-${i.id}`, kind: 'INCIDENT' as const,
      title: firstText(i.title, i.titleAr),
      subtitle: `${i.severity ?? '—'} · ${i.category ?? '—'} · ${i.status ?? ''}`,
      href: `/incidents`, Icon: AlertOctagon,
      keywords: [i.severity ?? '', i.status ?? '', i.category ?? '', i.assignee ?? ''],
    }))],
    ['KRI', async () => (await kriApi.listDefinitions()).slice(0, CAP).map(k => ({
      id: `kri-${k.id}`, kind: 'KRI' as const,
      title: firstText(k.titleEn, k.titleAr),
      subtitle: `${k.frequency ?? '—'} · ${k.category ?? ''}`,
      ref: k.kriCode, href: `/risk?tab=kri`, Icon: Activity,
      keywords: [k.kriCode ?? '', k.category ?? '', k.frequency ?? ''],
    }))],
    ['CHANGE', async () => (await changeApi.list({ size: CAP })).content.map(ch => ({
      id: `change-${ch.id}`, kind: 'CHANGE' as const,
      title: firstText(ch.titleEn, ch.titleAr),
      subtitle: `${ch.changeType ?? '—'} · ${ch.status ?? ''}`,
      ref: ch.changeCode, href: `/changes`, Icon: GitBranch,
      keywords: [ch.changeType ?? '', ch.status ?? '', ch.riskLevel ?? ''],
    }))],
  ];

  const settled = await Promise.allSettled(tasks.map(([, fn]) => fn()));
  settled.forEach((res, idx) => {
    const kind = tasks[idx][0];
    if (res.status === 'fulfilled') {
      next[kind] = res.value;
    } else {
      failed.push(kind);
    }
  });

  LIVE = next;
  FAILED = failed;
  HYDRATED = true;
  CACHE = null;
  return searchIndexStatus();
}

/**
 * CH-9 — kept as a named entry point because the command palette hydrates policies from
 * the canonical policy API (it is the one kind with a bespoke list call and a real
 * /policies/:id detail route). Merges into the same LIVE registry as everything else.
 */
export function hydratePolicySearch(
  docs: Array<{ id?: number; policyCode: string; version: string; titleEn: string; status: string; category?: string }>,
): void {
  LIVE.POLICY = docs
    .filter((p): p is typeof p & { id: number } => p.id != null)
    .map(p => ({
      id: `policy-${p.id}`, kind: 'POLICY' as const,
      title: p.titleEn,
      subtitle: `v${p.version} · ${p.status}${p.category ? ` · ${p.category}` : ''}`,
      ref: p.policyCode,
      href: `/policies/${p.id}`,
      Icon: BookCopy,
      keywords: [p.status, p.category ?? '', p.policyCode],
    }));
  CACHE = null;
}

/**
 * Search with type-prefix filtering.
 *   "risk: data"       → only risks matching "data"
 *   "auth: nca"        → only authorities matching "nca"
 *   "ev: pam"          → only evidence matching "pam"
 *   "data breach"      → all kinds, ranked
 */
const PREFIX_MAP: Record<string, EntityKind> = {
  risk: 'RISK', risks: 'RISK',
  finding: 'FINDING', findings: 'FINDING', f: 'FINDING',
  control: 'CONTROL', controls: 'CONTROL', cnt: 'CONTROL',
  policy: 'POLICY', policies: 'POLICY', pol: 'POLICY',
  evidence: 'EVIDENCE', ev: 'EVIDENCE',
  authority: 'AUTHORITY', auth: 'AUTHORITY',
  vendor: 'VENDOR', vendors: 'VENDOR',
  dpia: 'DPIA', dpias: 'DPIA',
  kri: 'KRI', kris: 'KRI',
  engagement: 'ENGAGEMENT', audit: 'ENGAGEMENT',
  incident: 'INCIDENT', incidents: 'INCIDENT',
  change: 'CHANGE', changes: 'CHANGE',
  page: 'PAGE',
};

export interface SearchOpts { limit?: number; }

export function searchIndex(query: string, opts: SearchOpts = {}): SearchItem[] {
  const limit = opts.limit ?? 30;
  const items = getIndex();
  const trimmed = query.trim();
  if (!trimmed) {
    // Empty query: show pages first, then 5 most-recent of each kind
    return items.filter(i => i.kind === 'PAGE').slice(0, limit);
  }

  // Type-prefix detection
  let kindFilter: EntityKind | null = null;
  let q = trimmed.toLowerCase();
  const colon = q.indexOf(':');
  if (colon > 0 && colon < 12) {
    const prefix = q.slice(0, colon).trim();
    const mapped = PREFIX_MAP[prefix];
    if (mapped) {
      kindFilter = mapped;
      q = q.slice(colon + 1).trim();
    }
  }

  // Score: title prefix (3) > title contains (2) > ref/keyword (1)
  const tokens = q.split(/\s+/).filter(Boolean);
  const scored: { it: SearchItem; score: number }[] = [];
  for (const it of items) {
    if (kindFilter && it.kind !== kindFilter) continue;
    if (tokens.length === 0) {
      scored.push({ it, score: 1 });
      continue;
    }
    const hay = [it.title, it.subtitle ?? '', it.ref ?? '', ...(it.keywords ?? [])].join(' ').toLowerCase();
    let score = 0;
    let allMatched = true;
    for (const tok of tokens) {
      if (it.title.toLowerCase().startsWith(tok)) score += 3;
      else if (it.title.toLowerCase().includes(tok)) score += 2;
      else if (hay.includes(tok)) score += 1;
      else { allMatched = false; break; }
    }
    if (allMatched && score > 0) scored.push({ it, score });
  }
  scored.sort((a, b) => b.score - a.score || a.it.title.localeCompare(b.it.title));
  return scored.slice(0, limit).map(s => s.it);
}

/** Direct lookup by `SearchItem.id` — used by the palette's recent-items list. */
export function getSearchItemById(id: string): SearchItem | undefined {
  return getIndex().find(it => it.id === id);
}

/** Reset for tests — drops live items and the cache so a test starts from pages only. */
export function _resetSearchIndex() { CACHE = null; LIVE = {}; HYDRATED = false; FAILED = []; }
