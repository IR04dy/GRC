import { useCallback, useMemo, useState } from 'react';
import type { FieldType, Operator } from './operators';

/** A single filter condition. */
export interface FilterCondition {
  id: string;                    // stable React key
  field: string;                 // row property to filter on
  type: FieldType;               // controls operator + value widget
  op: Operator;
  value?: any;                   // single value for arity=1
  value2?: any;                  // upper bound for `between`
  values?: any[];                // for `in` / `not_in`
}

export type Combinator = 'AND' | 'OR';

/** Group of conditions joined by a combinator. Groups can nest. */
export interface FilterGroup {
  id: string;
  combinator: Combinator;
  conditions: (FilterCondition | FilterGroup)[];
}

export const EMPTY_GROUP: FilterGroup = { id: 'root', combinator: 'AND', conditions: [] };

function isGroup(x: FilterCondition | FilterGroup): x is FilterGroup {
  return 'combinator' in x;
}

/**
 * Evaluate a filter group against a single row. Returns true if the row
 * satisfies the entire group expression (recursive over nested groups).
 *
 * Used to filter `data` client-side. For server-side, serialize the group
 * via {@link serializeFilter} and POST to the backend.
 */
export function evaluateGroup<T extends Record<string, any>>(group: FilterGroup, row: T, ctx?: { userId?: string | number }): boolean {
  if (group.conditions.length === 0) return true;
  const fn = group.combinator === 'AND' ? Array.prototype.every : Array.prototype.some;
  return fn.call(group.conditions, (c: FilterCondition | FilterGroup) =>
    isGroup(c) ? evaluateGroup(c, row, ctx) : evaluateCondition(c, row, ctx)
  );
}

function evaluateCondition<T extends Record<string, any>>(c: FilterCondition, row: T, ctx?: { userId?: string | number }): boolean {
  const v = row[c.field];
  switch (c.op) {
    case 'eq':            return v === c.value;
    case 'ne':            return v !== c.value;
    case 'contains':      return typeof v === 'string' && v.toLowerCase().includes(String(c.value).toLowerCase());
    case 'starts_with':   return typeof v === 'string' && v.toLowerCase().startsWith(String(c.value).toLowerCase());
    case 'ends_with':     return typeof v === 'string' && v.toLowerCase().endsWith(String(c.value).toLowerCase());
    case 'lt':            return Number(v) < Number(c.value);
    case 'lte':           return Number(v) <= Number(c.value);
    case 'gt':            return Number(v) > Number(c.value);
    case 'gte':           return Number(v) >= Number(c.value);
    case 'between':       return Number(v) >= Number(c.value) && Number(v) <= Number(c.value2);
    case 'in':            return Array.isArray(c.values) && c.values.includes(v);
    case 'not_in':        return Array.isArray(c.values) && !c.values.includes(v);
    case 'is_empty':      return v == null || v === '';
    case 'is_not_empty':  return v != null && v !== '';
    case 'before':        return new Date(v).getTime() < new Date(c.value).getTime();
    case 'after':         return new Date(v).getTime() > new Date(c.value).getTime();
    case 'today': {
      const d = new Date(v); const t = new Date();
      return d.toDateString() === t.toDateString();
    }
    case 'last_n_days': {
      const t = Date.now() - (Number(c.value) * 86_400_000);
      return new Date(v).getTime() >= t;
    }
    case 'is_overdue':    return new Date(v).getTime() < Date.now();
    case 'assigned_to_me': return ctx?.userId != null && v === ctx.userId;
    case 'created_by_me':  return ctx?.userId != null && row['createdBy'] === ctx.userId;
  }
}

// ─── Serialization (URL ↔ FilterGroup) ────────────────────────────────────

/** Serialize to a URL-safe base64-encoded JSON string. */
export function serializeFilter(group: FilterGroup): string {
  if (typeof window === 'undefined') return '';
  return btoa(unescape(encodeURIComponent(JSON.stringify(group))));
}

export function deserializeFilter(encoded: string): FilterGroup | null {
  if (!encoded) return null;
  try {
    const json = decodeURIComponent(escape(atob(encoded)));
    return JSON.parse(json) as FilterGroup;
  } catch { return null; }
}

// ─── React hook ────────────────────────────────────────────────────────────

/**
 * State hook for an interactive filter builder. Read `predicate(row)` in your
 * data layer. Sync to URL with `urlParam`/`setUrlParam` if you want filters to
 * persist across reloads / be shareable.
 */
export function useFilterState(initial?: FilterGroup, ctx?: { userId?: string | number }) {
  const [group, setGroup] = useState<FilterGroup>(initial ?? EMPTY_GROUP);

  const predicate = useCallback(
    <T extends Record<string, any>>(row: T) => evaluateGroup(group, row, ctx),
    [group, ctx]
  );

  const isEmpty = useMemo(() => group.conditions.length === 0, [group]);
  const conditionCount = useMemo(() => countConditions(group), [group]);

  return { group, setGroup, predicate, isEmpty, conditionCount };
}

function countConditions(g: FilterGroup): number {
  return g.conditions.reduce((acc, c) => acc + (isGroup(c) ? countConditions(c) : 1), 0);
}
