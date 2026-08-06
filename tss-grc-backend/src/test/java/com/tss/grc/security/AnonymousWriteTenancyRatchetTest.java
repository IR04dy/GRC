package com.tss.grc.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * F2-83 — an anonymous write must be able to name the tenant it is writing to.
 *
 * <p><b>The defect this exists to prevent.</b> A {@code permitAll} write has no JWT, so
 * {@code TenantContext} is empty and nothing on the thread says which customer the row belongs to.
 * Two regulator-mandated channels answered that with a constant — {@code PUBLIC_PORTAL_DEFAULT_ORG
 * = 1L} — and because an anonymous caller <i>always</i> takes the fallback, every whistleblower
 * report and every consumer complaint on the platform was filed into organisation 1. Measured: 9
 * cases and 12 complaints, all org 1, on a database with three active tenants.
 *
 * <p><b>Why an invariant rather than a ceiling.</b> The house rule is explicit that a ceiling
 * tolerates the population it measures, so once a sweep lands, leaving a ceiling in place makes a
 * tolerated case indistinguishable from a reviewed one. There is no acceptable number of public
 * writes that cannot name their tenant, so this asserts <b>zero</b>, with named exceptions that each
 * have to say why.
 *
 * <p><b>What counts as being able to name the tenant.</b> Either the handler takes an {@code org}
 * request parameter (the portal-link short code — see
 * {@code com.tss.grc.module.platform.tenancy.PublicPortalTenantResolver}), or it takes an opaque
 * token as a path variable, in which case the token identifies the row and the row carries the
 * tenant. Both are real answers. A constant is not.
 */
class AnonymousWriteTenancyRatchetTest {

    /**
     * Public writes that resolve their tenant from an opaque token rather than an {@code org} code.
     *
     * <p>Each entry is a claim that the handler reaches its tenant <i>through the row the token
     * identifies</i>. That is why they are safe and why they are listed individually instead of being
     * matched by a name pattern: a pattern would silently absorb the next
     * {@code postSomethingPublic} that resolves nothing at all.
     */
    private static final Set<String> TOKEN_CREDENTIALED = Set.of(
            // WF-7 external workflow-request recipient. Exempt only because its token is opaque in
            // fact and not merely in name: WorkflowRequestService:1353 sets it to
            // UUID.randomUUID().toString().replace("-", "") — 32 random hex characters, stored in
            // workflow.request_recipients.access_token, and it carries an expiry. It needs an entry
            // here purely because the parameter is typed String rather than UUID, so the automatic
            // rule below cannot see that it is a token. Checked, not assumed.
            "PublicWorkflowRequestController#respond");

    @Test
    @DisplayName("every anonymous write can name its tenant — by ?org= code or by opaque token")
    void noAnonymousWriteResolvesItsTenantByDefault() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<String> offenders = new ArrayList<>();
        Set<String> anonymousWrites = new TreeSet<>();

        for (var bd : scanner.findCandidateComponents("com.tss.grc")) {
            Class<?> c;
            try {
                c = Class.forName(bd.getBeanClassName());
            } catch (Throwable t) {
                continue;
            }
            for (Method m : c.getDeclaredMethods()) {
                if (!isWriteMapping(m) || !isPermitAll(m)) {
                    continue;
                }
                String id = c.getSimpleName() + "#" + m.getName();
                anonymousWrites.add(id);
                if (TOKEN_CREDENTIALED.contains(id) || hasOrgParam(m) || hasPathToken(m)) {
                    continue;
                }
                offenders.add(id);
            }
        }

        // A collapsed scan must never read as a clean one (F2-69). If this finds no anonymous
        // writes at all, the scanner broke — the platform has public portals by regulatory
        // requirement and always will.
        assertThat(anonymousWrites)
                .as("the scan found no permitAll writes at all, so it is not measuring anything")
                .isNotEmpty();

        assertThat(offenders)
                .as("these endpoints accept an anonymous write but have no way to know which tenant "
                    + "the row belongs to, so they must be resolving it from a constant — which is "
                    + "F2-83 exactly: every whistleblower report and consumer complaint on the "
                    + "platform was filed into organisation 1 because an anonymous caller always "
                    + "takes the fallback. Give the handler an 'org' request param and resolve it "
                    + "with PublicPortalTenantResolver, or — if an opaque token identifies the row "
                    + "and therefore the tenant — add it to TOKEN_CREDENTIALED with a reason. "
                    + "Anonymous writes found: %s", anonymousWrites)
                .isEmpty();
    }

    @Test
    @DisplayName("the token-credentialed exemptions all still exist, so the list cannot rot")
    void theExemptionsAreReal() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        Set<String> found = new TreeSet<>();
        for (var bd : scanner.findCandidateComponents("com.tss.grc")) {
            Class<?> c;
            try {
                c = Class.forName(bd.getBeanClassName());
            } catch (Throwable t) {
                continue;
            }
            for (Method m : c.getDeclaredMethods()) {
                found.add(c.getSimpleName() + "#" + m.getName());
            }
        }
        List<String> stale = TOKEN_CREDENTIALED.stream().filter(e -> !found.contains(e)).sorted().toList();
        assertThat(stale)
                .as("TOKEN_CREDENTIALED names handlers that no longer exist. An exemption list that "
                    + "outlives its entries stops describing the code and starts hiding it — remove "
                    + "these, and if the endpoint moved, re-justify it under its new name.")
                .isEmpty();
    }

    // ─── helpers ────────────────────────────────────────────────────────────────────────────

    private static boolean isWriteMapping(Method m) {
        if (m.isAnnotationPresent(PostMapping.class) || m.isAnnotationPresent(PutMapping.class)
                || m.isAnnotationPresent(PatchMapping.class) || m.isAnnotationPresent(DeleteMapping.class)) {
            return true;
        }
        RequestMapping rm = m.getAnnotation(RequestMapping.class);
        if (rm != null) {
            for (RequestMethod meth : rm.method()) {
                if (meth == RequestMethod.POST || meth == RequestMethod.PUT
                        || meth == RequestMethod.PATCH || meth == RequestMethod.DELETE) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Read off the compiled annotation, never the source text — F2-27/F2-69's lesson. */
    private static boolean isPermitAll(Method m) {
        PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
        return pa != null && pa.value().replace(" ", "").contains("permitAll");
    }

    private static boolean hasOrgParam(Method m) {
        for (Parameter p : m.getParameters()) {
            RequestParam rp = p.getAnnotation(RequestParam.class);
            if (rp == null) {
                continue;
            }
            String name = !rp.name().isBlank() ? rp.name() : (!rp.value().isBlank() ? rp.value() : p.getName());
            if ("org".equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A {@code UUID} path variable on a public write is an unguessable token, and the row it names
     * carries the tenant. A {@code Long} id is not — it is enumerable, which is the very thing
     * SEC-PRIV-003 was raised for — so it does not count here.
     */
    private static boolean hasPathToken(Method m) {
        for (Parameter p : m.getParameters()) {
            if (p.isAnnotationPresent(PathVariable.class) && p.getType() == java.util.UUID.class) {
                return true;
            }
        }
        return false;
    }
}
