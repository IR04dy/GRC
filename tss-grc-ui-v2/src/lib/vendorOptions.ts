import { scmApi, tprmApi } from './api';

/**
 * The tenant's own vendor register, as picker options.
 *
 * ## Why this exists (F2-246)
 *
 * Four SCM registers — exit plans, alternate suppliers, supplier resilience tests and
 * fourth-party exposures — store a vendor **id** beside a copy of that vendor's **name**.
 * All four create forms offered a *required free-text* vendor-name box, no picker, and sent
 * no id at all. Measured identical in dev and prod: **32 of 53 live rows carry a vendor
 * label and no link**, and the labels are annotated prose (`'AWS (managed cloud services)'`,
 * `'TCS Saudi Arabia (payroll BPO)'`) — which is *why* they can never be joined.
 *
 * `ExitPlanRepository.findByOrganisationIdAndVendorId…` and
 * `AlternateSupplierRepository.findByOrganisationIdAndPrimaryVendorId…` are coverage queries
 * over that id, so *"which vendors have an exit plan?"* — a SAMA outsourcing question —
 * could never see 7 of 13 real plans.
 *
 * **A form that collects a label instead of a link produces a row nothing can join.** So the
 * four forms pick from the register and send the id; the server resolves it in-tenant and
 * derives the label from the row that resolved. Nothing here sends a vendor name any more.
 */
export interface VendorOption {
  value: string;
  label: string;
}

/**
 * Load the picker's options.
 *
 * `GET /api/v1/scm/vendors/all` already existed — no new endpoint was needed, which is the
 * fifth time in this programme a blocker turned out to be already solved.
 *
 * Arabic is the platform's primary language (house rule 7), so an Arabic name is preferred
 * when the UI is Arabic and the vendor has one; `nameEn` is the fallback because it is the
 * column the register requires.
 */
export async function loadVendorOptions(lang: 'en' | 'ar'): Promise<VendorOption[]> {
  const rows = await scmApi.allVendors();
  return rows
    .map((v) => ({
      value: String(v.id),
      label: lang === 'ar' && v.nameAr ? v.nameAr : v.nameEn,
    }))
    .sort((a, b) => a.label.localeCompare(b.label, lang === 'ar' ? 'ar' : 'en'));
}

/**
 * The TPRM vendor register, as picker options (F2-247).
 *
 * ⚠ **This is a DIFFERENT register from `loadVendorOptions` above, and the two live side by side
 * on purpose.** `scm.vendors` and `tprm.vendors` are two tables for one concept — F2-196's shape,
 * an owner decision nobody has taken — and a reader who finds one loader must be able to see that
 * the other exists rather than reaching for whichever they found first. F2-205 put the two
 * vulnerability clients adjacent for the same reason.
 *
 * `tprm.third_party_cyber_assessments.vendor_id` names a row of THIS register: resolved from
 * `TprmService.getVendor`, not guessed from the schema name, because a schema name does not predict
 * a parent (F2-202).
 *
 * Measured 2026-08-19: 28 live rows for organisation 1 in both dev and prod, so `size` comfortably
 * covers the register — the same check that made F2-246's picker safe to ship.
 */
export async function loadTprmVendorOptions(lang: 'en' | 'ar'): Promise<VendorOption[]> {
  const page = await tprmApi.vendors(0, 200);
  return (page.content ?? [])
    .map((v) => ({
      value: String(v.id),
      label: lang === 'ar' && v.vendorNameAr ? v.vendorNameAr : v.vendorName,
    }))
    .sort((a, b) => a.label.localeCompare(b.label, lang === 'ar' ? 'ar' : 'en'));
}
