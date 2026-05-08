// Generic record registry — Sprint 4 §2.4.
//
// Each entity type registers a `search`, `display`, `icon` so the generic
// `<RecordPicker entity="risk" />` knows how to find rows + render them.
// Pages no longer reinvent the wheel for "pick a Risk / Vendor / Control / Asset".

import type { ComponentType } from 'react';
import type { LucideProps } from 'lucide-react';
import { AlertTriangle, Building, Server, Shield, User, BookOpen } from 'lucide-react';

export type EntityKind = 'risk' | 'vendor' | 'control' | 'asset' | 'user' | 'kb_article';

/** A row returned by a record search — minimal shape every entity must conform to. */
export interface PickerRecord {
  id: string;
  label: string;          // primary display
  sublabel?: string;      // secondary display (e.g. role, code)
  iconHint?: string;      // optional emoji or short text for avatar
}

export interface RecordEntityDef {
  /** Bilingual display name of the entity. */
  labelEn: string;
  labelAr: string;
  /** Icon (Lucide). */
  icon: ComponentType<LucideProps>;
  /** Async search function. */
  search: (query: string) => Promise<PickerRecord[]>;
  /** Default page size for results. */
  resultLimit?: number;
}

const REGISTRY: Partial<Record<EntityKind, RecordEntityDef>> = {};

/** Register an entity for use with `<RecordPicker entity="...">`. Idempotent. */
export function registerRecord(kind: EntityKind, def: RecordEntityDef): void {
  REGISTRY[kind] = def;
}

export function getRecordDef(kind: EntityKind): RecordEntityDef | undefined {
  return REGISTRY[kind];
}

// ─── Built-in registrations ────────────────────────────────────────────────
//
// These wire up the existing user directory + a few common entity stubs.
// Other modules can call `registerRecord('risk', { ... })` from their own
// initialisation code. Once a real backend is wired, the search functions
// become network calls instead of local-array filters.

import { searchUsers } from './userDirectory';

registerRecord('user', {
  labelEn: 'User',
  labelAr: 'مستخدم',
  icon: User,
  resultLimit: 8,
  search: async (q) => searchUsers(q).map(u => ({
    id: u.id,
    label: u.nameEn,
    sublabel: `${u.role} · ${u.department}`,
    iconHint: u.initials,
  })),
});

// Stubs — to be wired when their search APIs ship. Until then `RecordPicker`
// reports "no results" gracefully.

registerRecord('risk', {
  labelEn: 'Risk', labelAr: 'مخاطر', icon: AlertTriangle, resultLimit: 8,
  search: async () => [],     // wire to riskApi.search when ready
});

registerRecord('vendor', {
  labelEn: 'Vendor', labelAr: 'مورّد', icon: Building, resultLimit: 8,
  search: async () => [],     // wire to tprmApi.search when ready
});

registerRecord('control', {
  labelEn: 'Control', labelAr: 'ضابط', icon: Shield, resultLimit: 10,
  search: async () => [],     // wire to complianceApi.searchControls when ready
});

registerRecord('asset', {
  labelEn: 'Asset', labelAr: 'أصل', icon: Server, resultLimit: 8,
  search: async () => [],     // wire to cyberApi.searchAssets when ready
});

registerRecord('kb_article', {
  labelEn: 'Article', labelAr: 'مقال', icon: BookOpen, resultLimit: 6,
  search: async () => [],     // wire to kbApi.search when KB ships (§2.9)
});

// ─── Recents (per-user, per-entity, localStorage) ─────────────────────────

const RECENTS_KEY = (kind: EntityKind) => `grc.picker.recents.${kind}.v1`;
const RECENTS_MAX = 5;

export function recordRecents(kind: EntityKind): PickerRecord[] {
  if (typeof window === 'undefined') return [];
  try {
    const raw = window.localStorage.getItem(RECENTS_KEY(kind));
    return raw ? JSON.parse(raw) as PickerRecord[] : [];
  } catch { return []; }
}

export function pushRecord(kind: EntityKind, record: PickerRecord): void {
  if (typeof window === 'undefined') return;
  const existing = recordRecents(kind).filter(r => r.id !== record.id);
  const next = [record, ...existing].slice(0, RECENTS_MAX);
  try { window.localStorage.setItem(RECENTS_KEY(kind), JSON.stringify(next)); } catch { /* ignore */ }
}
