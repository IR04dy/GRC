package com.tss.grc.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * F2-159 — the registered {@link EntityOwnershipResolver}s, keyed by {@code targetType}.
 *
 * <p>Populated by constructor injection, so adding an entity type is a {@code @Component} and nothing
 * else. Two things it deliberately does <b>not</b> do:
 *
 * <ul>
 *   <li><b>It does not tolerate a duplicate {@code targetType}.</b> Two resolvers claiming
 *       {@code ML_MODEL} is an ambiguity, and a map that silently keeps the last one would make the
 *       effective authorisation rule depend on bean ordering. It throws at startup instead —
 *       the same reason A17-D2 fails closed on an unconfigured gate map rather than guessing.</li>
 *   <li><b>It does not answer for an unregistered type.</b> {@link #isOwnedBy} returns
 *       {@code false} and logs, so a typo in a guard's {@code targetType} produces a 403 rather than
 *       falling through to something broader. That is the whole difference between this and the stub it
 *       replaced.</li>
 * </ul>
 */
@Slf4j
@Component
public class EntityOwnershipRegistry {

    private final Map<String, EntityOwnershipResolver> byType;

    public EntityOwnershipRegistry(List<EntityOwnershipResolver> resolvers) {
        Map<String, EntityOwnershipResolver> m = new HashMap<>();
        for (EntityOwnershipResolver r : resolvers) {
            String key = normalise(r.targetType());
            EntityOwnershipResolver clash = m.put(key, r);
            if (clash != null) {
                throw new IllegalStateException(
                    "Two EntityOwnershipResolvers claim targetType '" + key + "': "
                    + clash.getClass().getName() + " and " + r.getClass().getName()
                    + ". Ownership is an authorisation decision — it may not depend on bean ordering.");
            }
        }
        this.byType = Map.copyOf(m);
        log.info("[F2-159] entity-ownership resolvers registered: {}", registeredTypes());
    }

    /** Registered target types, for the guard-surface tests and the startup log line. */
    public Set<String> registeredTypes() {
        return new TreeSet<>(byType.keySet());
    }

    /**
     * @return {@code false} when the type is not registered, the id is null, or the caller is not the
     *         recorded owner. Never throws: an ownership check is one leg of an {@code or}, and a leg
     *         that throws takes the whole guard expression down with it.
     */
    public boolean isOwnedBy(String targetType, Serializable id, Long userId, Long organisationId) {
        if (targetType == null || id == null || userId == null || organisationId == null) {
            return false;
        }
        EntityOwnershipResolver r = byType.get(normalise(targetType));
        if (r == null) {
            // A guard naming an unregistered type is a bug in the guard. Refuse it and say so — the
            // stub this replaced answered the MODULE-level question instead, which is how an ownership
            // check silently became a module-wide one.
            log.warn("[F2-159] no ownership resolver for targetType '{}' (registered: {}) — refusing. "
                + "A guard using hasPermission(#id, '{}', 'OWNER') will 403 until a resolver exists.",
                targetType, registeredTypes(), targetType);
            return false;
        }
        try {
            return r.isOwnedBy(id, userId, organisationId);
        } catch (RuntimeException ex) {
            log.warn("[F2-159] ownership resolver for '{}' failed on id '{}': {} — refusing",
                targetType, id, ex.toString());
            return false;
        }
    }

    private static String normalise(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
    }
}
