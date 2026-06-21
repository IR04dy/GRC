package com.tss.grc.security;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WS2 authorization RATCHET (from the audit remediation plan).
 *
 * <p>Scans every {@code @RestController} and flags state-changing endpoints
 * (POST/PUT/PATCH/DELETE) whose effective {@code @PreAuthorize} is NOT a real
 * permission/role check — i.e. {@code isAuthenticated()}, {@code permitAll}, or
 * missing entirely. These are the Broken-Function-Level-Authorization (A01)
 * findings from the audit: any authenticated user can invoke them.
 *
 * <p>The current count is captured as {@link #BASELINE_UNGUARDED_WRITE_ENDPOINTS}.
 * This test FAILS if the count rises — i.e. a new unguarded write endpoint is
 * introduced — so the debt can only shrink. As WS2 fixes land, lower the
 * baseline; the goal is 0.
 */
class ControllerAuthorizationRatchetTest {

    /**
     * Known unguarded write endpoints at the time WS2 started (audit baseline,
     * measured 2026-06-21). MUST only ever decrease. Do NOT raise this to make a
     * new endpoint pass — add the correct {@code hasPermission('MODULE','ACTION')}
     * guard instead. The eventual floor is the small set of legitimately-public
     * write endpoints (auth login/refresh/logout, the public intake tracker,
     * inbound webhooks); everything else should carry a real permission/role check.
     */
    private static final int BASELINE_UNGUARDED_WRITE_ENDPOINTS = 422;

    @Test
    void noNewUnguardedWriteEndpoints() {
        List<String> unguarded = scanUnguardedWriteEndpoints();
        Collections.sort(unguarded);
        System.out.println("[WS2 ratchet] unguarded write endpoints = " + unguarded.size());
        unguarded.forEach(s -> System.out.println("  - " + s));

        assertThat(unguarded.size())
            .as("A write endpoint lacks a hasPermission/hasAnyRole guard. Add the correct "
                + "@PreAuthorize(\"hasPermission('MODULE','ACTION')\") — do not raise the baseline. "
                + "Offenders are printed above.")
            .isLessThanOrEqualTo(BASELINE_UNGUARDED_WRITE_ENDPOINTS);
    }

    static List<String> scanUnguardedWriteEndpoints() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<String> unguarded = new ArrayList<>();
        for (var bd : scanner.findCandidateComponents("com.tss.grc")) {
            Class<?> c;
            try {
                c = Class.forName(bd.getBeanClassName());
            } catch (Throwable t) {
                continue;
            }
            for (Method m : c.getDeclaredMethods()) {
                if (isWriteMapping(m) && !isGuarded(m, c)) {
                    unguarded.add(c.getSimpleName() + "#" + m.getName());
                }
            }
        }
        return unguarded;
    }

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

    /** A method is guarded if it (or its declaring class) carries a @PreAuthorize
     *  expressing a real permission/role check — not merely isAuthenticated/permitAll. */
    private static boolean isGuarded(Method m, Class<?> c) {
        PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
        if (pa == null) {
            pa = c.getAnnotation(PreAuthorize.class);
        }
        if (pa == null) {
            return false;
        }
        String v = pa.value().toLowerCase();
        return v.contains("haspermission") || v.contains("hasanyrole")
            || v.contains("hasrole") || v.contains("hasauthority");
    }
}
