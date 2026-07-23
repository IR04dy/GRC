// Aggregated cross-entity search index for the Cmd/Ctrl+K command palette.
// Pulls from every mock dataset so the palette can find risks, findings,
// controls, policies, evidence, authorities, vendors, DPIAs, KRIs,
// engagements, incidents, and the module landing pages themselves.
import type { LucideIcon } from 'lucide-react';
import {
  AlertTriangle, ClipboardCheck, BookCopy, FileCheck2, Truck,
  Fingerprint, Activity, Briefcase, Landmark, Flame, AlertOctagon,
  LockKeyhole, RefreshCw, Workflow, ShieldAlert, Files, GitBranch,
  Stamp, LayoutDashboard, Calendar, Bell, ListChecks,
} from 'lucide-react';

import { MOCK_RISKS, KRIS } from '../pages/risk/mockRisks';
import {
  COMPLIANCE_CONTROLS, COMPLIANCE_FINDINGS, POLICIES, AUDIT_ENGAGEMENTS,
} from '../pages/compliance/mockCompliance';
import { EVIDENCE } from '../pages/evidence/mockEvidence';
import { AUTHORITIES } from './authorities';
import { VENDORS } from '../pages/tprm/mockTprm';
import { DPIAS } from '../pages/privacy/mockPrivacy';
import { INCIDENTS as CYBER_INCIDENTS_MOCK } from '../pages/cyber/mockCyber';
import { INCIDENTS } from '../pages/incidents/mockIncidents';
import { CHANGE_REQUESTS } from '../pages/changes/mockChanges';

export type EntityKind =
  | 'PAGE' | 'RISK' | 'FINDING' | 'CONTROL' | 'POLICY' | 'EVIDENCE'
  | 'AUTHORITY' | 'VENDOR' | 'DPIA' | 'KRI' | 'ENGAGEMENT' | 'INCIDENT' | 'CHANGE';

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

// CH-9: policy search items are hydrated from the canonical policy API at
// runtime (see hydratePolicySearch) so results link to a real /policies/:id.
// Until that runs, the mock POLICIES set is used as a fallback.
let LIVE_POLICY_ITEMS: SearchItem[] | null = null;

function buildEntityItems(): SearchItem[] {
  const items: SearchItem[] = [];

  for (const r of MOCK_RISKS) {
    items.push({
      id: `risk-${r.id}`, kind: 'RISK',
      title: r.titleEn,
      subtitle: `${r.categoryNameEn ?? '—'} · owner ${r.ownerFullNameEn ?? '—'}`,
      ref: r.riskCode,
      href: `/risk/${r.id}`,
      Icon: AlertTriangle,
      keywords: [r.status, r.riskLevel ?? '', r.categoryNameEn ?? ''],
    });
  }

  for (const f of COMPLIANCE_FINDINGS) {
    items.push({
      id: `finding-${f.id}`, kind: 'FINDING',
      title: f.title,
      subtitle: `${f.severity} · owner ${f.owner ?? '—'} · ${f.status}`,
      ref: `AUD-F-${String(f.id).padStart(3, '0')}`,
      href: `/findings/${f.id}`,
      Icon: AlertTriangle,
      keywords: [f.severity, f.status, f.capaStage ?? '', f.clauseRef ?? ''],
    });
  }

  for (const c of COMPLIANCE_CONTROLS) {
    items.push({
      id: `control-${c.id}`, kind: 'CONTROL',
      title: c.nameEn,
      subtitle: `${c.framework} · ${c.status} · L${c.maturityLevel ?? 0}`,
      ref: c.code,
      href: `/controls/${c.id}`,
      Icon: ClipboardCheck,
      keywords: [c.framework, c.status, c.domain ?? '', c.owner ?? ''],
    });
  }

  // CH-9: prefer canonical policies (real ids → working /policies/:id links);
  // fall back to the mock set until hydratePolicySearch has run.
  if (LIVE_POLICY_ITEMS) {
    items.push(...LIVE_POLICY_ITEMS);
  } else {
    for (const p of POLICIES) {
      items.push({
        id: `policy-${p.id}`, kind: 'POLICY',
        title: p.titleEn,
        subtitle: `v${p.version} · ${p.status}${p.category ? ` · ${p.category}` : ''}`,
        ref: p.code,
        href: `/policies/${p.id}`,
        Icon: BookCopy,
        keywords: [p.status, p.category ?? '', p.code],
      });
    }
  }

  for (const e of EVIDENCE) {
    items.push({
      id: `evidence-${e.id}`, kind: 'EVIDENCE',
      title: e.titleEn,
      subtitle: `${(e.evidenceType ?? '').replace(/_/g, ' ')} · ${e.status} · ${e.frameworkCode ?? ''}`,
      ref: e.evidenceCode,
      href: `/evidence`,
      Icon: FileCheck2,
      keywords: [e.evidenceType ?? '', e.status ?? '', e.frameworkCode ?? '', e.controlCode ?? ''],
    });
  }

  for (const a of AUTHORITIES) {
    items.push({
      id: `authority-${a.code}`, kind: 'AUTHORITY',
      title: a.nameEn,
      subtitle: `${a.taglineEn ?? ''} · ${a.jurisdiction}`,
      ref: a.code,
      href: `/authority/${a.code}`,
      Icon: Landmark,
      keywords: [a.code, a.nameAr, a.jurisdiction, ...a.domains, ...a.frameworkCodes],
    });
  }

  for (const v of VENDORS) {
    items.push({
      id: `vendor-${v.id}`, kind: 'VENDOR',
      title: v.vendorName,
      subtitle: `${v.vendorType ?? '—'} · ${v.riskRating} · ${v.status}`,
      href: `/vendors/${v.id}`,
      Icon: Truck,
      keywords: [v.riskRating, v.status, v.vendorType ?? '', v.country ?? ''],
    });
  }

  for (const d of DPIAS) {
    items.push({
      id: `dpia-${d.id}`, kind: 'DPIA',
      title: d.titleEn,
      subtitle: `${d.triggerReason.replace(/_/g, ' ')} · ${d.status}${d.overallResidualRisk ? ` · residual ${d.overallResidualRisk}` : ''}`,
      ref: `DPIA-${d.id}`,
      href: `/dpias/${d.id}`,
      Icon: Fingerprint,
      keywords: [d.status, d.triggerReason, d.overallResidualRisk ?? ''],
    });
  }

  for (const k of KRIS) {
    items.push({
      id: `kri-${k.id}`, kind: 'KRI',
      title: k.nameEn,
      subtitle: `${k.frequency} · owner ${k.owner ?? '—'} · ${k.unit ?? ''}`,
      ref: k.code,
      href: `/risk?tab=kri`,
      Icon: Activity,
      keywords: [k.code ?? '', k.framework ?? '', k.direction],
    });
  }

  for (const eng of AUDIT_ENGAGEMENTS) {
    items.push({
      id: `engagement-${eng.id}`, kind: 'ENGAGEMENT',
      title: eng.titleEn,
      subtitle: `${eng.auditorFirm ?? '—'} · ${eng.framework ?? ''} · ${eng.status}`,
      ref: eng.engagementCode,
      href: `/audit-workspace/engagement/${eng.id}`,
      Icon: Briefcase,
      keywords: [eng.auditType, eng.framework ?? '', eng.status, eng.leadAuditor ?? ''],
    });
  }

  for (const i of CYBER_INCIDENTS_MOCK) {
    items.push({
      id: `cyber-incident-${i.id}`, kind: 'INCIDENT',
      title: i.titleEn,
      subtitle: `${i.severity} · ${i.status}${i.affectedAssetName ? ` · ${i.affectedAssetName}` : ''}`,
      ref: i.incidentNumber,
      href: `/cyber?tab=incidents`,
      Icon: Flame,
      keywords: [i.severity ?? '', i.status ?? '', i.incidentType ?? ''],
    });
  }

  for (const i of INCIDENTS) {
    items.push({
      id: `incident-${i.id}`, kind: 'INCIDENT',
      title: i.title,
      subtitle: `${i.severity} · ${i.category ?? '—'} · ${i.status}`,
      href: `/incidents`,
      Icon: AlertOctagon,
      keywords: [i.severity, i.status, i.category ?? '', i.assignee ?? ''],
    });
  }

  for (const ch of CHANGE_REQUESTS) {
    items.push({
      id: `change-${ch.id}`, kind: 'CHANGE',
      title: ch.titleEn,
      subtitle: `${ch.changeType} · ${ch.status}${ch.riskLevel ? ` · ${ch.riskLevel}` : ''}`,
      ref: ch.changeCode,
      href: `/changes`,
      Icon: GitBranch,
      keywords: [ch.changeType, ch.status, ch.riskLevel ?? '', ch.category ?? ''],
    });
  }

  return items;
}

let CACHE: SearchItem[] | null = null;
function getIndex(): SearchItem[] {
  if (CACHE) return CACHE;
  CACHE = [...PAGE_ITEMS, ...buildEntityItems()];
  return CACHE;
}

/**
 * CH-9: replace the mock policy search items with canonical policies fetched
 * from the policy API, so the command palette and any search links resolve to
 * a real /policies/:id detail page. Invalidates the cache so the next search
 * rebuilds the index with live data. Safe to call repeatedly.
 */
export function hydratePolicySearch(
  docs: Array<{ id?: number; policyCode: string; version: string; titleEn: string; status: string; category?: string }>,
): void {
  LIVE_POLICY_ITEMS = docs
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

/** Reset for tests / live data refresh — not used in app code today. */
export function _resetSearchIndex() { CACHE = null; }
