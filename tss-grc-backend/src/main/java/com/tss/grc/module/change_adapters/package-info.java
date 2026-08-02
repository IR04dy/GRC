/**
 * S5 / CE-1 — the adapters that connect Change Enablement to its peers, living
 * <b>outside</b> both modules so neither depends on the other (§6c, ADR-012).
 *
 * <h3>Why a separate package and not an inner class</h3>
 * An adapter placed inside {@code module/change} would re-import the peer and defeat the
 * boundary in the same commit that declared it; placed inside {@code module/admin} it would
 * make the kernel depend on a commercial SKU, which is worse. Held here, the dependency
 * arrows both point <em>inward</em> to interfaces: {@code change → change.spi ← adapters →
 * peer}. Only this package knows both sides.
 *
 * <h3>ADR-018 homing — why this package needed a decision, not just a directory</h3>
 * {@code SkuRegistry.requiredSkuForPackage} <b>throws</b> for any {@code com.tss.grc.module.*}
 * package that ADR-018 never homed, because "silently treating it as Base is exactly how seven
 * packages became unsold revenue". So creating a new module package is an entitlement decision.
 * This one is homed to {@code CHANGE}: the adapters exist only to serve Change Enablement, they
 * ship and are licensed with it, and homing them to Base would put change-specific glue in the
 * mandatory platform. Recorded in {@code SkuRegistry.PACKAGE_TO_SKU} and in ADR-018 §3.
 *
 * <h3>No controllers here, and that is load-bearing</h3>
 * Adapters carry no {@code @RestController} and therefore no {@code @RequireModule}: the guarded
 * surface stays in {@code module/change}, where the SKU annotation already is. An adapter with an
 * endpoint would be a second, unguarded door into the same behaviour — the F2-04 shape.
 */
package com.tss.grc.module.change_adapters;
