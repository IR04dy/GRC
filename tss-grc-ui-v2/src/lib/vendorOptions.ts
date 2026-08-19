import { scmApi } from './api';

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
