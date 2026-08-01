package com.tss.grc.common.exception;

import com.tss.grc.module.platform.licensing.guard.ModuleDisabledException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F2-84 — <b>an entitlement denial must be a 403 that names the module, not a 500.</b>
 *
 * <h3>Why this test is an MVC test and not a unit test</h3>
 * <p>The obvious test — call {@code handleModuleDisabled} and assert it returns a 403 ProblemDetail —
 * would have <b>passed while the platform was broken</b>, because the method did not exist and the
 * defect was never in a method body. {@link ModuleDisabledException} declared
 * {@code @ResponseStatus(FORBIDDEN)} and relied on Spring's {@code ResponseStatusExceptionResolver},
 * which runs <i>after</i> {@code @RestControllerAdvice}, so the advice's catch-all claimed the
 * exception first and answered 500. <b>The bug lived in resolver precedence</b>, and only something
 * that exercises the resolver chain can see it.
 *
 * <p>{@code MockMvcBuilders.standaloneSetup} registers
 * {@code ExceptionHandlerExceptionResolver → ResponseStatusExceptionResolver →
 * DefaultHandlerExceptionResolver} in that order — the same order as production — so removing the
 * handler in {@link GlobalExceptionHandler} makes this test fail with 500. That was verified by
 * doing it, not assumed.
 *
 * <p>The same shape guards {@link org.springframework.web.server.ResponseStatusException}, whose
 * handler carries a javadoc describing this exact hijack. It was written for one exception and never
 * generalised, which is why the licence guard — annotated the same way — went four months returning
 * 500 for "you have not bought this module". Any future exception that leans on
 * {@code @ResponseStatus} instead of an explicit handler will do the same.
 */
class ModuleDisabledStatusTest {

    /** Stands in for any of the 197 controllers the F2-04 sweep annotated. */
    @RestController
    static class GuardedStub {
        @GetMapping("/stub/bcm/thing")
        public String denied() {
            throw new ModuleDisabledException("BCM", 5L);
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        GlobalExceptionHandler advice = new GlobalExceptionHandler();
        // @Value field — false is the production default (SEC-CFG-001) and irrelevant here, but an
        // uninitialised primitive would differ from the real bean if that ever changes.
        ReflectionTestUtils.setField(advice, "verboseErrors", false);
        mvc = MockMvcBuilders.standaloneSetup(new GuardedStub())
                .setControllerAdvice(advice)
                .build();
    }

    @Test
    void anUnlicensedModuleIsForbiddenNotAServerError() throws Exception {
        mvc.perform(get("/stub/bcm/thing"))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.status").value(403))
           .andExpect(jsonPath("$.title").value("Module Not Licensed"))
           .andExpect(jsonPath("$.type").value("https://grc.tss.com/errors/module-not-licensed"));
    }

    /**
     * The body must name the module. A 403 that does not is the F2-80 shape — the thrower knew
     * exactly which SKU was missing and the handler threw that away, leaving the frontend to guess
     * and the customer with "forbidden" where "you have not bought BCM" was available.
     */
    @Test
    void theBodyNamesTheMissingModuleSoTheCallerNeedNotGuess() throws Exception {
        mvc.perform(get("/stub/bcm/thing"))
           .andExpect(jsonPath("$.moduleCode").value("BCM"))
           .andExpect(jsonPath("$.organisationId").value(5))
           .andExpect(jsonPath("$.detail").value(
               org.hamcrest.Matchers.containsString("not licensed for the BCM module")));
    }

    /**
     * Distinguishable from an authorisation failure. Support cannot act on "403" alone: one is a
     * commercial conversation and the other is an RBAC grant, and {@code errors/forbidden} is what
     * {@link GlobalExceptionHandler#handleAccessDenied} returns.
     */
    @Test
    void itIsNotConfusableWithAPermissionDenial() throws Exception {
        mvc.perform(get("/stub/bcm/thing").accept(MediaType.APPLICATION_JSON))
           .andExpect(jsonPath("$.type").value(
               org.hamcrest.Matchers.not("https://grc.tss.com/errors/forbidden")))
           .andExpect(jsonPath("$.title").value(org.hamcrest.Matchers.not("Access Denied")));
    }
}
