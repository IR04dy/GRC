package com.tss.grc.security;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-27 export-authorisation RATCHET — a handler that hands the caller a FILE must require
 * {@code MODULE:EXPORT}, not {@code MODULE:READ}.
 *
 * <p><b>The defect this test exists to prevent from recurring.</b> 24 {@code MODULE:EXPORT}
 * permissions were catalogued in {@code iam.permissions} and granted to as many as 55 of 58
 * roles, and exactly <b>one</b> of the platform's 39 file-returning endpoints enforced any of
 * them. Every register export — risk, change, CAB pack, board pack, regulator ZIP,
 * sustainability annex, climate report, green-finance evidence package, SGI, BCP binder, NDI
 * reports — was authorised by a {@code READ} permission or a raw role-name list, so the right
 * to view one record on screen conferred the right to extract the whole register as a file. A
 * tenant administrator granting or revoking {@code CHANGE:EXPORT} in the role screen changed
 * nothing, which is worse than the permission not existing: it manufactures false assurance
 * in the customer's own access review. This is the exact mirror of F2-23 — a permission that
 * no role held guarding 59 methods — and, like F2-23, it survived because <em>no test asked
 * the question</em>. That is what this class is.
 *
 * <p><b>The rule, stated mechanically so it is checkable.</b> A handler returns a file when
 * its return type is {@code ResponseEntity<byte[]>}, {@code byte[]}, {@code Resource} /
 * {@code ResponseEntity<Resource>} or {@code StreamingResponseBody}. The HTTP verb is
 * irrelevant — {@code POST /reporting/packet.xlsx} takes a request body and still returns a
 * spreadsheet, so it is an export. Such a handler's effective {@code @PreAuthorize} must
 * contain at least one {@code hasPermission('MODULE','EXPORT')}. Additional AND-ed
 * conditions are welcome (three endpoints keep a narrowing role list); an {@code EXPORT}-free
 * guard is the failure.
 *
 * <p><b>Why reflection and not a source grep.</b> The discovery scan for F2-27 was a regex
 * over the sources and it reported 8 endpoints as unguarded. They were not: they carry
 * {@code @PreAuthorize(READ_GUARD)} where {@code READ_GUARD} is a {@code private static final
 * String} in the same class, which a naive pattern for {@code @PreAuthorize("...")} cannot
 * see. Reading the annotation through reflection resolves the constant at compile time and
 * cannot make that mistake — which matters, because "this endpoint is unguarded" is exactly
 * the kind of claim that must not be wrong.
 *
 * <p><b>Companion migration.</b> V320 makes the permissions satisfiable before the code
 * requires them: it catalogues {@code AI:EXPORT} (which did not exist — annotating against it
 * unmigrated would have produced a guard no tenant could satisfy, the F2-23 trap for a fourth
 * time) and repairs the two measured grant gaps ({@code CHANGE:EXPORT} for the assurance
 * personas, {@code BCM:EXPORT} for CISO/CFO). The per-tenant impact query that decided every
 * line of it ran <em>before</em> any annotation changed.
 *
 * @see ControllerAuthorizationRatchetTest the sibling ratchet for unguarded write endpoints
 */
class ExportAuthorizationRatchetTest {

    /** {@code hasPermission('ANY_MODULE','EXPORT')}, tolerating the whitespace variants in use. */
    private static final Pattern EXPORT_GUARD =
        Pattern.compile("hasPermission\\(\\s*'[A-Z_]+'\\s*,\\s*'EXPORT'\\s*\\)");

    /**
     * The only file-returning handlers allowed to authorise on something other than
     * {@code EXPORT}. Two entries, each a deliberate decision with a stated reason — not a
     * parking space for whatever fails. Anything added here should carry the same standard of
     * justification, because an allowlist is how a ratchet quietly stops ratcheting.
     *
     * <ul>
     *   <li><b>{@code CommitteeCatalogController#exportForm}</b> — {@code ADMIN:READ}. Returns
     *       a <em>blank governance form template</em> ({@code CommitteeFormTemplate} rendered
     *       to PDF): a form definition with no tenant records in it. Requiring
     *       {@code ADMIN:EXPORT} would strip 14 roles (CISO, DPO, BCM_MANAGER, CRISIS_MANAGER,
     *       RISK_MANAGER, LEGAL_COUNSEL…) of a blank PDF download, because ADR-014 deliberately
     *       narrowed {@code ADMIN:EXPORT} to the 7-role secretariat. Fourteen roles broken for
     *       zero data-loss benefit is a worse outcome than the inconsistency.</li>
     *   <li><b>{@code AttachmentController#download}</b> — {@code EVIDENCE:READ}. Returns ONE
     *       named object the caller already resolved by id, not an aggregation. Treating it as
     *       an export would require {@code EVIDENCE:EXPORT} for every inline document preview
     *       and evidence open across the 14 modules that use the attachment subsystem, which
     *       conflates "open a file" with "extract a register". F2-27's own recommendation
     *       reached the same conclusion.</li>
     * </ul>
     */
    private static final Set<String> ALLOWLIST = Set.of(
        "CommitteeCatalogController#exportForm",
        "AttachmentController#download"
    );

    @Test
    void everyFileReturningEndpointRequiresModuleExport() {
        List<String> offenders = scanExportsWithoutExportPermission();
        Collections.sort(offenders);
        System.out.println("[F2-27 export ratchet] file-returning endpoints without MODULE:EXPORT = "
            + offenders.size());
        offenders.forEach(s -> System.out.println("  - " + s));

        assertThat(offenders)
            .as("A handler returns a file (byte[]/Resource/StreamingResponseBody) but does not "
                + "require hasPermission('MODULE','EXPORT'). Bulk extraction must not be "
                + "authorised by a READ permission — that is F2-27, and it made 23 catalogued "
                + "EXPORT permissions enforce nothing. Add the EXPORT guard; grant the "
                + "permission by migration FIRST if no role holds it (see V320). Do not add to "
                + "ALLOWLIST without a stated reason of the same standard as the two there.")
            .isEmpty();
    }

    /**
     * The population itself is asserted: if a refactor stopped the scan from seeing
     * file-returning handlers, {@link #everyFileReturningEndpointRequiresModuleExport} would
     * pass vacuously and the ratchet would be silently dead. 40 measured 2026-07-26; the
     * assertion is a floor, so adding exports is fine and gutting the scan is not.
     *
     * <p>That 40 is itself the argument for reflection over grepping. The F2-27 source scan
     * found 39 and missed {@code BcmEvidencePackageController#download}, which returns
     * {@code ResponseEntity<ByteArrayResource>} — a {@code Resource} subclass the regex did
     * not enumerate. {@code Resource.class.isAssignableFrom(...)} cannot miss it. The endpoint
     * it missed is the BCM audit evidence ZIP: by its own author's comment, "the most
     * sensitive export on the platform", and it was authorised by {@code BCM:READ}.
     */
    @Test
    void theScanStillFindsTheExportSurface() {
        int found = scanFileReturningEndpoints().size();
        System.out.println("[F2-27 export ratchet] file-returning endpoints discovered = " + found);
        assertThat(found)
            .as("The export scan found %d file-returning handlers; 40 were measured when F2-27 "
                + "shipped. A sharp drop means the detector broke, not that the exports left — "
                + "check the return-type rule in isFileReturning().", found)
            .isGreaterThanOrEqualTo(40);
    }

    static List<String> scanExportsWithoutExportPermission() {
        List<String> offenders = new ArrayList<>();
        for (String[] hit : scanFileReturningEndpoints()) {
            String id = hit[0];
            String guard = hit[1];
            if (ALLOWLIST.contains(id)) {
                continue;
            }
            if (guard == null || !EXPORT_GUARD.matcher(guard).find()) {
                offenders.add(id + "  guard=" + (guard == null ? "(none)" : guard));
            }
        }
        return offenders;
    }

    /** @return {@code [SimpleName#method, effective @PreAuthorize value or null]} per handler. */
    static List<String[]> scanFileReturningEndpoints() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<String[]> hits = new ArrayList<>();
        for (var bd : scanner.findCandidateComponents("com.tss.grc")) {
            Class<?> c;
            try {
                c = Class.forName(bd.getBeanClassName());
            } catch (Throwable t) {
                continue;
            }
            for (Method m : c.getDeclaredMethods()) {
                if (!isMapped(m) || !isFileReturning(m)) {
                    continue;
                }
                PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
                if (pa == null) {
                    pa = c.getAnnotation(PreAuthorize.class);
                }
                hits.add(new String[] { c.getSimpleName() + "#" + m.getName(),
                                        pa == null ? null : pa.value() });
            }
        }
        return hits;
    }

    private static boolean isMapped(Method m) {
        return m.isAnnotationPresent(GetMapping.class) || m.isAnnotationPresent(PostMapping.class)
            || m.isAnnotationPresent(PutMapping.class) || m.isAnnotationPresent(PatchMapping.class)
            || m.isAnnotationPresent(DeleteMapping.class)
            || m.isAnnotationPresent(RequestMapping.class);
    }

    /**
     * A handler hands the caller a file when it returns raw bytes, a {@link Resource} or a
     * stream — directly or wrapped in {@link ResponseEntity}. Deliberately type-based rather
     * than header-based: {@code Content-Disposition} is set deep inside shared helpers
     * ({@code ExportController} funnels 19 handlers through 4 of them), so a scan for the
     * header under-counts the surface by 15.
     */
    private static boolean isFileReturning(Method m) {
        if (isBinaryType(m.getReturnType())) {
            return true;
        }
        if (!ResponseEntity.class.isAssignableFrom(m.getReturnType())) {
            return false;
        }
        Type generic = m.getGenericReturnType();
        if (!(generic instanceof ParameterizedType pt) || pt.getActualTypeArguments().length != 1) {
            return false;
        }
        Type arg = pt.getActualTypeArguments()[0];
        if (arg instanceof Class<?> cls) {
            return isBinaryType(cls);
        }
        // e.g. ResponseEntity<byte[]> surfaces as a GenericArrayType on some compilers
        return arg.getTypeName().equals("byte[]");
    }

    private static boolean isBinaryType(Class<?> t) {
        return t == byte[].class
            || Resource.class.isAssignableFrom(t)
            || InputStreamResource.class.isAssignableFrom(t)
            || StreamingResponseBody.class.isAssignableFrom(t);
    }
}
