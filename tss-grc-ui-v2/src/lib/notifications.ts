// Notifications — the real server inbox.
//
// F2-38. This replaces `lib/notificationOutbox`'s localStorage store as the source for the
// notification bell. `platform.notifications` holds 445 real rows; `admin` alone had 57 unread of
// 75, served by a working `isAuthenticated()` endpoint that the UI had never called. CE-10 shipped
// the change engine's ability to reach people and nobody could see it, because the bell read a
// different store.
//
// ⚠ `lib/notificationOutbox` is NOT retired by this file, and the distinction matters: it models a
// multi-channel *delivery* subsystem (EMAIL, SMS, WHATSAPP, NAFATH, TADAWUL_PORTAL) with a
// QUEUED/SENDING/SENT/FAILED lifecycle that has no server behind it at all — the browser calls
// `markSent` on itself. That is F2-142 and it needs an owner decision, not a repoint: the real DTO
// has no recipient, no channel and no delivery status, so substituting this inbox for that outbox
// would change the screen without changing the truth.

import { api, type PageResponse } from './api';

/**
 * A notification as the server serves it.
 *
 * Mirrors `NotificationResponse` after F2-38 removed `severity` and `linkUrl` — neither was a column
 * on `platform.notifications`, and both were hard-coded null for the endpoint's whole life. Do not
 * reintroduce them here expecting values.
 */
export interface ServerNotification {
  id: number;
  titleEn: string;
  titleAr?: string | null;
  bodyEn: string;
  /** ⚠ Populated on only 42 of 445 rows — see `messageAr` in the entity for why. */
  bodyAr?: string | null;
  /** ⚠ Two vocabularies in one field: severity-shaped for 48 rows, event-shaped for 397. */
  type: string;
  isRead: boolean;
  entityType?: string | null;
  entityId?: number | null;
  createdAt: string;
}

/** Which route a notification's subject lives on, and whether it can address the record itself. */
interface EntityRoute {
  /** `{id}` is substituted with `entityId` when `record` is true. */
  path: string;
  /**
   * `true` when the route addresses the individual record. `false` means the best available
   * destination is the owning register — the user still has to find the row.
   */
  record: boolean;
}

/**
 * Every `entity_type` value present in the live data, mapped to a real route in `App.tsx`.
 *
 * <p>⚠ **Two casings, and a map handling only one would silently produce no link for half the
 * rows.** Nine values are CamelCase JPA class names (`EvidenceRequest` alone is 324 of 445 rows) and
 * eight are SCREAMING_CASE domain names. That is a data defect worth its own migration, not
 * something to normalise here — so both are listed, and the vocabulary is gated by test.
 *
 * <p>⚠ **`KRI` is deliberately list-level, and this is the measurement that decided it.** The detail
 * route is `/risk/kri/:externalId`, and a notification carries a numeric `entity_id`. Building
 * `/risk/kri/6` from it would address the wrong record or none. A link that goes somewhere wrong is
 * worse than no link, so KRI resolves to its dashboard. The same question — *does the data exist for
 * what this would claim?* — is why `record` is a field here rather than an assumption.
 */
export const ENTITY_ROUTES: Readonly<Record<string, EntityRoute>> = Object.freeze({
  // ── CamelCase: JPA entity names, written by the engines ──────────────────────────────────
  EvidenceRequest:          { path: '/evidence',                      record: false },
  WorkflowRequest:          { path: '/workflows/requests/{id}',       record: true  },
  CorrectiveAction:         { path: '/capa/{id}',                     record: true  },
  VulnerabilitySlaFinding:  { path: '/cyber/vuln-sla/dashboard',      record: false },
  CyberEvidence:            { path: '/cyber',                         record: false },
  CyberActionPlan:          { path: '/cyber-lifecycle',               record: false },
  ShariaNonComplianceEvent: { path: '/sharia/nc-events',              record: false },
  ShariaAuditFinding:       { path: '/sharia/audit-findings',         record: false },
  BreakGlassEvent:          { path: '/access-governance',             record: false },

  // ── SCREAMING_CASE: domain names, written by the seeders and the older schedulers ─────────
  VENDOR:                   { path: '/vendors/{id}',                  record: true  },
  CHANGE:                   { path: '/changes',                       record: false },
  ENGAGEMENT:               { path: '/audit/ia/engagements/{id}',      record: true  },
  AML_ALERT:                { path: '/ethics',                        record: false },
  FINDING:                  { path: '/findings/{id}',                 record: true  },
  KRI:                      { path: '/risk/kri/dashboard',            record: false },
  OUTSOURCING_ARRANGEMENT:  { path: '/tprm/outsourcing/{id}',         record: true  },
  POLICY:                   { path: '/policies/{id}',                 record: true  },
});

/**
 * The in-app route for a notification's subject, or `null` when there is nothing honest to link to.
 *
 * Returning `null` is a real outcome, not a fallback: an unmapped `entityType` renders as plain text
 * rather than as a link to somewhere arbitrary. The gate in `notificationsBackedByServer.test.ts`
 * is what stops `null` from becoming the common case as new entity types appear.
 */
export function routeForEntity(
  entityType: string | null | undefined,
  entityId: number | null | undefined,
): string | null {
  if (!entityType) return null;
  const route = ENTITY_ROUTES[entityType];
  if (!route) return null;
  if (!route.record) return route.path;
  if (entityId === null || entityId === undefined) return null;
  return route.path.replace('{id}', String(entityId));
}

/** Title in the reader's language, falling back to English — `title_ar` is populated on all rows. */
export function notificationTitle(n: ServerNotification, lang: string): string {
  return (lang === 'ar' ? n.titleAr : n.titleEn) || n.titleEn;
}

/**
 * Body in the reader's language, falling back to English.
 *
 * ⚠ The fallback is load-bearing rather than defensive: `message_ar` is populated on 42 of 445 rows
 * because the three server-side notify methods take `titleAr` and no Arabic body, so an Arabic
 * reader sees English for most notifications. That is a content gap, tracked with F2-38, and showing
 * English is the honest behaviour — an empty body would hide the notification entirely.
 */
export function notificationBody(n: ServerNotification, lang: string): string {
  return (lang === 'ar' ? n.bodyAr : n.bodyEn) || n.bodyEn || '';
}

export const notificationsApi = {
  unreadCount: () =>
    api.get<{ count: number }>('/api/v1/notifications/unread-count'),

  list: (page = 0, size = 20) =>
    api.get<PageResponse<ServerNotification>>(
      `/api/v1/notifications?page=${page}&size=${size}`),

  markRead: (id: number) =>
    api.patch<void>(`/api/v1/notifications/${id}/read`),

  markAllRead: () =>
    api.patch<void>('/api/v1/notifications/read-all'),
};
