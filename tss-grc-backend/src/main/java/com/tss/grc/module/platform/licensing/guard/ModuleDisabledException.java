package com.tss.grc.module.platform.licensing.guard;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown by {@link LicenseGuardAspect} when a {@link RequireModule}-annotated method
 * is invoked while the tenant has not licensed that module.
 *
 * <p>Mapped to HTTP 403 Forbidden so the frontend can distinguish licensing failures
 * from authentication failures (401) and authorisation failures (which already use 403
 * but with different error codes). The message is safe to surface to operators because
 * it only names the missing module, never any credential or token state.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class ModuleDisabledException extends RuntimeException {

    private final String moduleCode;
    private final Long organisationId;

    public ModuleDisabledException(String moduleCode, Long organisationId) {
        super("Module not enabled for this tenant: " + moduleCode
              + " (org=" + organisationId + ")");
        this.moduleCode = moduleCode;
        this.organisationId = organisationId;
    }

    public String getModuleCode()      { return moduleCode; }
    public Long   getOrganisationId()  { return organisationId; }
}
