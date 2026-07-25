/**
 * Shared export toolkit — <b>kernel</b>, not a business module.
 *
 * <p>PDF theming and fonts (including the bundled Amiri face and the RTL Arabic shaping
 * that 22 generators depend on), XLSX styling, export file naming, the generic register
 * exporter and the tabular export service. None of it carries business semantics.
 *
 * <p><b>Why it lives here (F2-08a / ADR-012).</b> These classes used to sit in
 * {@code module/reporting/service}, and measurement found that <em>all 25</em> inbound
 * edges to {@code reporting} from eight peer modules — {@code bcm}, {@code sust},
 * {@code iam}, {@code socops}, {@code ndi}, {@code cyber}, {@code ai}, {@code admin} —
 * imported only these seven classes. Not one imported reporting logic. So the graph
 * showed eight modules "depending on reporting" when what they actually depended on was
 * shared styling that happened to live in a business module; it also manufactured an
 * apparent {@code socops -> reporting} cycle, since reporting consumes socops data.
 *
 * <p>Promoting them removed every one of those edges with no behaviour change. Bilingual
 * RTL export quality is a genuine platform asset used across the product, so it belongs
 * in the kernel on merit — the graph hygiene is a consequence, not the reason.
 *
 * <p><b>Rule:</b> nothing in this package may import a business module. It may use
 * {@code common.*} and other kernel packages (it already depends on
 * {@code platform.branding} for tenant theming). A boundary test enforces this.
 */
package com.tss.grc.module.platform.export;
