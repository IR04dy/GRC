package com.tss.grc.module.platform.tenancy;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.entity.Organisation;
import com.tss.grc.module.platform.repository.OrganisationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resolves the owning tenant for a request that arrives on a {@code permitAll} public portal.
 *
 * <p><b>Why this exists (F2-83).</b> A genuinely anonymous caller carries no JWT, so
 * {@link TenantContext} is empty and there is nothing on the thread to scope the write to. Two
 * regulator-mandated channels answered that by naming a constant:
 *
 * <pre>
 *   WhistleblowerCaseService.PUBLIC_PORTAL_DEFAULT_ORG = 1L
 *   ComplaintService.PUBLIC_PORTAL_DEFAULT_ORG         = 1L
 * </pre>
 *
 * <p>For an anonymous caller that fallback is <b>the only path, not an edge case</b>. Measured on
 * the dev database with three active tenants: all 9 whistleblower cases and all 12 complaints sat
 * in {@code organisation_id = 1}. Bank B's employee reports misconduct on Bank B's portal and the
 * case lands in Bank A's tenant, where Bank A's ethics officer can list it and Bank B's cannot see
 * their own report. Nazaha, SAMA CG Art.28 and CMA CG Art.87 require a *confidential* reporting
 * channel, and SAMA's Consumer Protection Principles say the same for complaints — so this is a
 * confidentiality failure, not a routing bug.
 *
 * <p><b>The platform had already solved it once.</b> SEC-PRIV-003 hardened
 * {@code SubprocessorController#publicList}, which had trusted a client-supplied *numeric* org id
 * defaulting to 1 — letting an unauthenticated caller iterate {@code org=1,2,3,…}. The fix was to
 * demand the tenant's opaque, non-enumerable short code, require it to exist and be active, and
 * return 404 otherwise. That fix was never carried to the two places where the data is most
 * confidential; F2-83's lesson is that <b>a fix applied to one endpoint is not a pattern until it
 * is applied to the others that share the flaw</b>. This class is that pattern, extracted once and
 * used by all three call sites, so the next public portal inherits it instead of reinventing a
 * constant.
 *
 * <h3>Resolution order</h3>
 * <ol>
 *   <li><b>{@link TenantContext}</b>, when set — a logged-in employee filing through the same form
 *       is already scoped, and their own tenant beats anything in the query string.</li>
 *   <li><b>The {@code org} short code</b> — must exist and be active.</li>
 *   <li><b>Refuse.</b> Never a default.</li>
 * </ol>
 *
 * <p><b>Why 404 and one identical message for every refusal.</b> Missing, unknown, and inactive are
 * indistinguishable to the caller by design: short codes are the public
 * {@code ethics.<bank>.sa}-style identifier, and answering "that tenant exists but is inactive"
 * would turn this into an oracle. It is the same status and the same silence
 * {@code SubprocessorController} already returns.
 *
 * <p><b>What this deliberately does not do.</b> Host-based resolution ({@code ethics.<bank>.sa}) is
 * the better long-term answer and is what both original TODOs proposed, but nothing in the platform
 * resolves a tenant from a host today. When it lands it belongs <i>here</i>, ahead of step 2 — which
 * is the point of having one resolver rather than three constants.
 */
@Component
@RequiredArgsConstructor
public class PublicPortalTenantResolver {

    /**
     * Identical for missing, unknown and inactive. See the class Javadoc — distinguishing them
     * would let an anonymous caller enumerate tenants.
     */
    public static final String REFUSAL =
            "Unknown or inactive organisation. This public portal link must carry a valid "
            + "'org' short code.";

    private final OrganisationRepository organisations;

    /**
     * For a <b>write</b> arriving on a public form: the caller's own tenant when they happen to be
     * logged in, otherwise the named one.
     *
     * @param orgShortCode the tenant's public short code, from the portal link's {@code ?org=}
     * @return the organisation id to write the submission to — never a default
     * @throws ResponseStatusException {@code 404} when no tenant can be resolved
     */
    public Long resolve(String orgShortCode) {
        Long fromContext = TenantContext.getCurrentTenant();
        if (fromContext != null) {
            return fromContext;
        }
        return resolveNamed(orgShortCode);
    }

    /**
     * For a <b>public read of a named tenant's published data</b>: always the tenant named by the
     * short code, never the caller's.
     *
     * <p>The distinction is deliberate and is not a style choice. On a submission,
     * {@link TenantContext} taking precedence is correct — an employee filing through their own
     * bank's form means their own bank. On a published-list read it would be wrong: an authenticated
     * caller asking for tenant X's PDPL Article 12 list would silently receive their own instead of
     * the one they asked for. Same lookup, same 404, one step fewer, so the two cases share the
     * lookup without sharing a behaviour neither of them wants.
     *
     * @throws ResponseStatusException {@code 404} when the code is missing, unknown or inactive
     */
    public Long resolveNamed(String orgShortCode) {
        if (orgShortCode == null || orgShortCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, REFUSAL);
        }
        // Soft deletes are handled by @Where(is_deleted = 0) on Organisation; isActive is not.
        return organisations.findByShortCode(orgShortCode.trim())
                .filter(o -> Boolean.TRUE.equals(o.getIsActive()))
                .map(Organisation::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, REFUSAL));
    }
}
