/**
 * F2-172 — the outbound seam intake promotion is built on.
 *
 * <h3>The defect this exists to close</h3>
 * Pressing <em>Promote</em> on an intake submission used to write a row to the triager's
 * {@code localStorage} and to nothing else (F2-171). The server half was no better: {@code
 * IntakeService.triage()} took {@code promotedToEntityType} and {@code promotedToRecordId}
 * <b>from the client</b> and created nothing, so "promotion creates the target row" was
 * implemented on neither side. A promoted DPIA had no {@code privacy.dpia} row, was invisible
 * to every other user, and died with the browser's site data — while the intake's permanent
 * history note read <em>"Promoted to DPIA #482913"</em>, naming nothing.
 *
 * <h3>Why the ports live here and the adapters do not</h3>
 * ADR-018 homes {@code module/intake} in {@link
 * com.tss.grc.module.platform.licensing.SkuRegistry#BASE_PACKAGES Base}. Intake is the platform's
 * front door, so promotion fans out into fourteen registers — and almost every one of them is a
 * <em>sold</em> SKU (privacy → PDPL, tprm → TPRM, bcm → BCM, ethics → ETHICS…). Importing them
 * from here would make the mandatory kernel depend on commercial modules, which the
 * {@code change_adapters} package-info calls out as the worse of the two boundary mistakes.
 *
 * <p>So the arrows point <b>inward</b>: {@code intake → intake.spi ← <target module>.intake}.
 * Each adapter lives in the module that owns the record it creates, where the entity, the
 * service and the SKU homing already are. A target module depending on a Base interface is the
 * correct direction; Base depending on fourteen SKUs is not. {@code IntakePromotionBoundaryTest}
 * enforces it — {@code module/intake} may import nothing from a peer module.
 *
 * <h3>What an adapter is not allowed to do</h3>
 * Return an id for a row it did not write. That is the entire defect, and {@link
 * com.tss.grc.module.intake.spi.RegisterPromotionPort#promote} is typed so the only way to
 * satisfy it is to hand back the identifier of a record that now exists. An adapter that cannot
 * create the record must throw; {@link com.tss.grc.module.intake.spi.PromotionRegistry} turns a
 * missing adapter into a 409 that names the target, because a promotion that quietly does
 * nothing is what this whole package exists to make impossible.
 */
package com.tss.grc.module.intake.spi;
