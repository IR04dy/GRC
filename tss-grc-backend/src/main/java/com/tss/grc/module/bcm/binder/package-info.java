/**
 * Slice 10-F — Offline BCP/DRP plan PDF binder generator.
 *
 * <p>Crisis Room teams need to operate when the GRC platform itself is
 * offline (datacenter outage, network split). SAMA BCF Annex D
 * explicitly demands "offline-accessible business continuity
 * documentation" — this package turns every approved BCP/DRP plan into
 * a printable, off-line, RTL Arabic-supporting PDF binder.
 *
 * <h3>What's bundled into a binder</h3>
 * <ol>
 *   <li>Cover page — org name, plan title, version, generation date
 *       (Hijri + Gregorian), responsible officer, classification badge.</li>
 *   <li>Table of contents — the 9 ISO 22301 Annex A sections.</li>
 *   <li>Plan metadata + RTO/MTPD matrix (KPI strip).</li>
 *   <li>Per-section bodies — bilingual columns when language=BILINGUAL,
 *       sequential pages when EN or AR.</li>
 *   <li>Version history.</li>
 *   <li>Distribution roster.</li>
 *   <li>Appendices.</li>
 * </ol>
 *
 * <h3>Storage model</h3>
 * <p>The PDF bytes live on disk under
 * {@code ${grc.storage.base-path}/binders/<org_id>/<external_id>.pdf}.
 * The {@link com.tss.grc.module.bcm.binder.entity.BcpBinderExport} row
 * is the metadata index and audit trail — never the file contents
 * themselves. This keeps the DB small and lets the file be re-served
 * directly to any number of auditors without DB round-trips.
 *
 * <h3>F7 audit + F8 event emission</h3>
 * <p>Every {@code generate / download / archive} writes an audit row
 * via {@code AuditLogService} (F7) and emits a {@code BCP_BINDER_*}
 * domain event via the platform {@code ApplicationEventPublisher} (F8)
 * so the automation rule engine can fire downstream actions.
 */
package com.tss.grc.module.bcm.binder;
