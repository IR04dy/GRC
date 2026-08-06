/**
 * F2-83 — which tenant a public portal submission belongs to.
 *
 * A `permitAll` submit endpoint receives no JWT, so nothing on the request says which customer the
 * report is for. Both portals used to let the server pick a default, and the default was org 1:
 * measured on the dev database, all 9 whistleblower cases and all 12 consumer complaints were filed
 * into organisation 1 while three tenants were active. Bank B's employee reports misconduct on Bank
 * B's portal and Bank A's ethics officer can read it. Nazaha, SAMA CG Art.28 and CMA CG Art.87
 * require a *confidential* channel, so that is a confidentiality failure rather than a routing bug.
 *
 * The tenant is now named in the portal link — `/public/complaint?org=STCB` — using the same opaque,
 * non-enumerable short code `SubprocessorController#publicList` has required since SEC-PRIV-003. The
 * server refuses an unknown or inactive code with 404 and never defaults.
 *
 * Which means the link itself is now load-bearing, and a bank that publishes it without `?org=` takes
 * its own hotline offline. `readPortalOrg` exists so both portals detect that **before** the reporter
 * types anything: a dead form that accepts a long confidential report and then throws it away is a
 * worse failure than a page that says up front it is misconfigured.
 */

/** The short code named by the current URL's `?org=`, or `null`. */
export function readPortalOrg(search?: string): string | null {
  const raw = new URLSearchParams(search ?? window.location.search).get('org');
  const trimmed = raw?.trim();
  return trimmed ? trimmed : null;
}

/**
 * Bilingual message for a portal link that names no tenant, or names one the server rejects.
 *
 * Deliberately addressed to whoever published the link rather than to the reporter — the reporter
 * has done nothing wrong and cannot fix it, and telling them "invalid organisation" invites them to
 * retype something. It also says nothing about whether any particular tenant exists: the server
 * answers missing, unknown and inactive codes identically so the endpoint cannot be used to
 * enumerate customers.
 */
export function portalLinkMisconfigured(lang: string): { title: string; body: string } {
  return lang === 'ar'
    ? {
        title: 'رابط البوابة غير مكتمل',
        body: 'هذا الرابط لا يحدّد الجهة المستقبِلة، لذلك لا يمكن إرسال البلاغ إلى الجهة الصحيحة. '
            + 'يُرجى استخدام الرابط الرسمي المنشور من جهتك، أو التواصل مع مسؤول الالتزام لديها. '
            + 'لم يتم إرسال أي بيانات.',
      }
    : {
        title: 'This portal link is incomplete',
        body: 'The link does not identify the receiving organisation, so a submission could not be '
            + 'routed to the right one. Please use the official link published by your organisation, '
            + 'or contact its compliance team. Nothing has been sent.',
      };
}
