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
 * <p>Module codes are the canonical strings in {@code LicensingService.KNOWN_MODULES}
 * (RISK, BCM, SCM, CYBER, PDPL, TPRM, POLICY, NDI, COMPLIANCE, AUDIT_LC, INCIDENT,
 * EVIDENCE, REPORTING, REGULATORY, CONTROL_LIB, POSTURE, AUTOMATION, AUDIT_LOG,
 * PLATFORM_ADMIN).
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
