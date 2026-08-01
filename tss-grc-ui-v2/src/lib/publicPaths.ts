// Paths that run OUTSIDE the authenticated application shell.
//
// This list was previously a private `BARE_PREFIXES` const inside AppLayout,
// where it answered one question: "does this route get the sidebar + topbar?".
// F2-04b needs the same set to answer a second question — "may this route be
// licence-gated?" — and the two answers must never diverge, so the list moved
// here instead of being copied.
//
// ─── Why one list serves both questions ───────────────────────────────────
// A path is bare because it either has no session at all (the public portals,
// login) or deliberately runs full-screen outside the shell (the mobile crisis
// room). In BOTH cases a licence gate has nothing it can honestly read:
// `GET /api/v1/licensing/me/enabled` is `isAuthenticated()`, so for an
// anonymous caller there is no cache to consult and no tenant to entitle.
//
// This mirrors the rule F2-04a adopted server-side, and it exists for the same
// reason: four controllers mix `permitAll` endpoints in with authenticated ones
// — the Nazaha / SAMA / CMA whistleblower portal, the SAMA consumer-complaints
// portal, the vendor BCP attestation portal and the PDPL Art.12 transparency
// list. `LicenseGuardAspect` lets an anonymous caller through precisely so a
// regulator-mandated channel cannot 403; a frontend gate that blocked
// `/ethics/whistleblower/submit` because the *host* tenant lacks `ETHICS` would
// reintroduce the same outage one layer up, where no server fix can reach it.
//
// ⚠ Keep this in step with SecurityConfig's `permitAll` matchers. A path listed
// here but authenticated server-side is merely un-gated in the UI (harmless —
// the server still enforces). A public path MISSING from here is the failure
// that matters: the UI refuses a submission the server would have accepted.

/**
 * Derived from the actual page set: the 9 `PublicShell` pages + `LoginPage`,
 * `MobileCrisisRoomPage`, `PublicSubprocessorListPage` and
 * `VendorAttestationSubmitPage` (which render neither `Shell` nor
 * `PublicShell`). Everything else renders `<Shell>` and gets the persistent
 * chrome.
 */
export const BARE_PREFIXES: readonly string[] = [
  '/login',
  '/public/',
  '/portal/',
  '/privacy/public/',
  '/ethics/whistleblower/submit',
  '/ethics/whistleblower/status',
  '/tprm/attestations/',
  '/crisis/mobile',
  '/bcm/crisis/mobile',
];

/**
 * True when this route runs without the authenticated shell — and therefore
 * without a sidebar, a topbar, or a licence gate.
 */
export function isBarePath(pathname: string): boolean {
  if (pathname === '/') return true; // redirects to /login
  return BARE_PREFIXES.some(pre => pathname === pre || pathname.startsWith(pre));
}
