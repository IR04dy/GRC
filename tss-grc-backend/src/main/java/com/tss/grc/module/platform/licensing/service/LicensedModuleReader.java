package com.tss.grc.module.platform.licensing.service;

import com.tss.grc.module.platform.licensing.entity.OrganisationLicense;
import com.tss.grc.module.platform.licensing.repository.OrganisationLicenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Set;
import java.util.TreeSet;

/**
 * F2-412 — sole owner of the F9 {@code licenses} cache read, <b>in its own bean</b>.
 *
 * <p>It lives here rather than on {@link LicensingService} for one structural reason: Spring's
 * caching advice lives on the <em>proxy</em>, so a self-call inside a bean bypasses it entirely —
 * no error, no log, the annotation visibly present. {@code LicensingService.isModuleEnabled} did
 * exactly that: it called {@code this.enabledModulesFor(...)}, so the {@code @Cacheable} never
 * applied on the {@code LicenseGuard} path, which is the path the class javadoc calls
 * <i>"every authenticated request hot-path"</i> and the reason the cache exists at all.
 *
 * <p>With the cached read on a separate bean, every caller necessarily goes through the proxy and
 * the bypass is <b>unrepresentable</b> rather than merely fixed. That is the difference this
 * programme keeps paying for: {@code ProxyAdviceInterceptableContractTest} can then state the rule
 * as a hard zero over the whole codebase, because a self-invoked {@code @Cacheable} has no
 * legitimate form.
 *
 * <p>Staleness is bounded by {@code CacheConfig}'s {@code expireAfterWrite(5, MINUTES)} and the two
 * {@code @CacheEvict} sites on {@link LicensingService}, so routing the licence-validity check
 * through the cache cannot serve an expired entitlement for longer than the JWT already does — that
 * token carries the same module list for an hour.
 */
@Component
@RequiredArgsConstructor
public class LicensedModuleReader {

    private final OrganisationLicenseRepository repo;

    /**
     * The module codes this organisation is entitled to today: enabled, and inside its validity
     * window. {@code LocalDate.now()} is read per computation, so the answer is only as fresh as
     * the cache entry — see the staleness note on the class.
     */
    @Cacheable(value = "licenses", key = "#organisationId")
    @Transactional(readOnly = true)
    public Set<String> enabledModulesFor(Long organisationId) {
        LocalDate today = LocalDate.now();
        Set<String> out = new TreeSet<>();
        for (OrganisationLicense l : repo.findByOrganisationIdOrderByModuleCodeAsc(organisationId)) {
            if (Boolean.FALSE.equals(l.getEnabled())) continue;
            if (l.getValidFrom() != null && l.getValidFrom().isAfter(today)) continue;
            if (l.getValidTo()   != null && l.getValidTo().isBefore(today))  continue;
            out.add(l.getModuleCode());
        }
        return out;
    }
}
