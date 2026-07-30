package com.tss.grc.module.platform.licensing.guard;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller or service method as requiring a specific licensed module.
 *
 * <p>The {@link LicenseGuardAspect} intercepts the call, looks up the current tenant
 * from {@code TenantContext}, asks {@code LicensingService.isModuleEnabled(...)}, and
 * throws {@link ModuleDisabledException} (translated to HTTP 403) when the module is
 * not enabled.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * @RestController
 * @RequestMapping("/api/v1/bcm/contacts")
 * @RequireModule("BCM")
 * public class BcmCommunicationContactController { ... }
 *
 * @Service
 * public class FooService {
 *     @RequireModule("RISK")
 *     public Foo create(Foo body) { ... }
 * }
 * }</pre>
 *
 * <p>Placed on the class, every method inherits the requirement; placed on a method,
 * only that method is guarded (a method-level annotation wins over a class-level one).
 *
 * <p><b>The value must be a ratified SKU code</b> — one of the 15 in
 * {@code TSS-GRC-Platform-Bible/Product/SKU_CATALOGUE.md} (ADR-018): RISK, COMPLIANCE,
 * AUDIT_LC, CYBER, SOCOPS, BCM, PDPL, TPRM, ESG, ETHICS, SAUDI_FS, DATA_AI_GOV, BOARD,
 * ACCESS_GOV, CHANGE. {@code SkuCatalogueRatchetTest} enforces this.
 *
 * <p>Not {@code LicensingService.KNOWN_MODULES}, which this Javadoc previously pointed at and
 * then listed <em>19</em> codes from — omitting {@code SOCOPS}, the only code the platform
 * actually enforces, in all 23 places it is used. That set is a legacy code registry; seven of
 * its entries price Base Platform capabilities and are being retired into Base.
 *
 * <p>Never name the Base Platform: ADR-018 R4 makes it non-licensable, so no code represents it
 * and a guard against one would be unsatisfiable for every tenant.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RequireModule {
    /**
     * The module code that must be enabled for the current tenant. Required.
     * Must match one of {@code LicensingService.KNOWN_MODULES}.
     */
    String value();
}
