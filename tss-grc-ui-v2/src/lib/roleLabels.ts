/**
 * Human labels for the IAM role names this product shows to a person.
 *
 * <h3>Why this exists</h3>
 * `INTAKE_CATALOG` routes each submission kind to a `defaultAssignee`, and F2-174 established that
 * value is a ROLE NAME from `iam.roles` — it renamed ten codes that matched nothing and pinned the
 * rest at a hard zero (`IntakeAssigneeRoleResolutionTest`). That is correct and must not change.
 *
 * What was wrong is that the raw code reached the screen. Three surfaces rendered it under a human
 * label — the intake rerouting hint, the post-submit acknowledgement, and the HRSD process card —
 * so a submitter read "This will route to RISK_MANAGER for triage", and in Arabic an English
 * SCREAMING_SNAKE token sat inside Arabic copy on an Arabic-primary platform (house rule 7).
 *
 * <h3>⚠ Why a client-side map rather than a server lookup, measured rather than assumed</h3>
 * The server already carries the labels: `iam.roles.display_name_en` / `display_name_ar` are
 * populated for all eleven. But `GET /api/v1/iam/roles` is
 * `hasRole('PLATFORM_ADMIN') or hasPermission('IAM','READ')`, while `POST /api/v1/intake` is
 * `isAuthenticated()` — every employee. So the endpoint would 403 for exactly the audience that
 * reads the hint (F2-250: measure the picker's guard before reaching for one).
 *
 * <h3>⚠ These values are COPIED from that column, not invented</h3>
 * Every label below was read out of `iam.roles` rather than composed, because four of eleven
 * first-draft guesses disagreed with what the platform actually ships — `مسؤول الامتثال` not
 * `الالتزام`, `رئيس` not `مدير` for the HR and IR leads, `مدقق داخلي` for the internal auditor. A
 * second Arabic name for one role is the two-artefacts-nobody-maintains shape (F2-177), and the
 * cheapest way to avoid it is to read the first one.
 *
 * <p>That leaves a copy, and the honest account of what guards it: the CODES are pinned
 * server-side at a hard zero, so a renamed role fails that build; what can drift is a re-worded
 * LABEL, which is cosmetic. `roleLabelContract.test.ts` pins this map against the catalogue in both
 * directions so a new assignee cannot ship unlabelled and a stale entry cannot outlive its code.
 */

export type RoleLabel = { en: string; ar: string };

/** Read from `iam.roles.display_name_en` / `display_name_ar` on 2026-09-12. */
export const ROLE_LABEL: Record<string, RoleLabel> = {
  BCM_MANAGER:        { en: 'BCM Manager',                        ar: 'مدير استمرارية الأعمال' },
  CHANGE_MANAGER:     { en: 'Change Manager',                     ar: 'مدير التغيير' },
  CISO:               { en: 'Chief Information Security Officer', ar: 'رئيس أمن المعلومات' },
  COMPLIANCE_OFFICER: { en: 'Compliance Officer',                 ar: 'مسؤول الامتثال' },
  DPO:                { en: 'Data Protection Officer',            ar: 'مسؤول حماية البيانات' },
  ETHICS_OFFICER:     { en: 'Ethics Officer',                     ar: 'مسؤول الأخلاقيات' },
  HR_LEAD:            { en: 'HR Lead',                            ar: 'رئيس الموارد البشرية' },
  INTERNAL_AUDITOR:   { en: 'Internal Auditor',                   ar: 'مدقق داخلي' },
  IR_LEAD:            { en: 'Incident Response Lead',             ar: 'رئيس الاستجابة للحوادث' },
  RISK_MANAGER:       { en: 'Risk Manager',                       ar: 'مدير المخاطر' },
  SUPPLIER_MANAGER:   { en: 'Supplier Manager',                   ar: 'مدير الموردين' },
};

/**
 * The label for a role name, in the caller's language.
 *
 * ⚠ An unmapped code falls back to a title-cased rendering (`FOO_BAR` → `Foo Bar`) rather than to
 * the raw code, because returning the raw code is the defect this file exists to remove. The gate
 * makes that branch unreachable for every `INTAKE_CATALOG` assignee; it is here for any other
 * caller, and it degrades to readable rather than to a screaming identifier.
 */
export function roleLabel(code: string | null | undefined, lang: string): string {
  if (!code) return '';
  const hit = ROLE_LABEL[code];
  if (hit) return lang === 'ar' ? hit.ar : hit.en;
  return code
    .split('_')
    .filter(Boolean)
    .map(w => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase())
    .join(' ');
}
