package com.tss.grc.common.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.AnnotatedField;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.module.SimpleModule;
import jakarta.persistence.Entity;
import jakarta.persistence.MappedSuperclass;
import org.springframework.stereotype.Component;

/**
 * F2-206 — the primary key of a JPA entity may never arrive from a request body, on ANY entity.
 *
 * <h2>What this closes</h2>
 *
 * <p>{@code POST /api/v1/privacy/ropa} with {@code {"id": 1, …}} answered <b>HTTP 201
 * Created</b> and <b>overwrote</b> processing activity 1 — because a non-null id makes
 * {@code CrudRepository#save} call {@code merge()} instead of {@code persist()}. Probed live
 * 2026-08-13 against a PDPL Article 31 record: {@code activity_name_ar}, {@code purpose_en},
 * {@code retention_period_months} and {@code data_categories_json} were nulled (the body did
 * not mention them), the lawful basis moved {@code LEGAL_OBLIGATION → CONSENT}, and
 * {@code special_category_flag} — the flag that makes the activity Article 25 high-risk —
 * was cleared. The response said a record had been created. The same call on
 * {@code POST /dsrs} converted an {@code ERASURE} request to {@code ACCESS} and nulled the
 * data subject's email.
 *
 * <p>Cross-tenant reach was <b>probed and refuted</b>: naming another organisation's row id
 * answers 500 and changes nothing, so the tenant boundary holds. The live defect is
 * destruction of the caller's own tenant's records through an endpoint that reports success.
 *
 * <h2>Why a platform-wide introspector and not 252 annotations</h2>
 *
 * <p>{@code BaseEntity} has carried {@code @JsonProperty(access = READ_ONLY)} on {@code id}
 * since F2-22a, and {@code MassAssignmentRatchetTest} proves it works — through
 * {@code class Probe extends BaseEntity}. So <b>the mitigation and its proof are both scoped
 * to the entities that extend that mapped superclass.</b> Measured: <b>252 of 407 entities do
 * not extend it, and none of them annotated its own id</b>, which is <b>177 of the 214
 * entity-bound write endpoints</b>. The gate's own risk profile could not report it either —
 * it breaks its field walk at {@code BaseEntity} on the assumption that everything it carries
 * is already covered, and {@code id} is in none of its risk vocabularies.
 *
 * <p>🔑 <b>An invariant enforced by a superclass covers exactly the subclasses, and a probe
 * that extends that superclass can only ever measure the covered half.</b> Same shape as
 * F2-198's finding about {@code demoFallbackContract} (the invariant is real, its POPULATION
 * is a filename) and F2-201's about the fabricated-directory guard (its population is a module
 * name). Here the population was an {@code extends} clause.
 *
 * <p>An introspector has no population to get wrong: it is asked about every property of every
 * type Jackson binds, so a new entity is covered on the day it is written and there is no
 * registry to forget. It reports the same {@code READ_ONLY} access the annotation does, so the
 * behaviour is identical to the half that was already proven — <b>serialise exactly as before,
 * refuse to deserialise</b>. That distinction is load-bearing and is not a style choice:
 * {@code @JsonIgnore} would have stripped {@code id} from every response and the entire
 * frontend resolves records by id.
 *
 * <h2>What this deliberately does NOT do</h2>
 *
 * <p>It does not remove the property from the deserialiser. Removing it would make an incoming
 * {@code "id"} an <em>unknown</em> property, and the frontend echoes whole records back on
 * update paths — under {@code FAIL_ON_UNKNOWN_PROPERTIES} that turns working requests into
 * 400s. {@code READ_ONLY} ignores the value, which is the requirement.
 *
 * <p>It does not touch {@code externalId}. A client-chosen business reference is a different
 * defect (F2-172(d)) fixed per service, because some references are legitimately supplied by
 * an integrating system while a database primary key never is.
 *
 * <p>It leaves the ~102 existing {@code req.setId(null)} calls in place. They are now
 * redundant rather than wrong, and deleting a hundred defensive lines to prove a point would
 * be churn with a real chance of removing one that guards something this does not.
 *
 * @see com.tss.grc.common.model.BaseEntity the mapped superclass this generalises
 * @see com.tss.grc.security.MassAssignmentRatchetTest the gate whose population this widens
 */
@Component
public class ServerOwnedIdentityModule extends SimpleModule {

    public ServerOwnedIdentityModule() {
        super("server-owned-identity");
    }

    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        // insert, not append: this introspector must be consulted BEFORE the default one so a
        // class that says nothing about `id` still gets READ_ONLY. Anything that explicitly
        // annotates the property keeps its own answer, because we defer to super for every
        // property that is not an entity primary key.
        context.insertAnnotationIntrospector(new EntityIdIsServerOwned());
    }

    /** Reports {@code READ_ONLY} for the {@code id} property of any mapped type. */
    static final class EntityIdIsServerOwned extends JacksonAnnotationIntrospector {

        @Override
        public JsonProperty.Access findPropertyAccess(Annotated a) {
            if (isEntityPrimaryKey(a)) {
                return JsonProperty.Access.READ_ONLY;
            }
            return super.findPropertyAccess(a);
        }

        /**
         * Matches the {@code id} FIELD only, and that is a measured decision, not an oversight.
         *
         * <p>The first draft also matched {@code getId}/{@code setId} on the theory that Jackson
         * might resolve the property through Lombok's accessors when the field is private. The
         * mutation harness disproved it: deleting the accessor branch left
         * {@code ServerOwnedIdentityInvariantTest} green across all 432 entities, so the branch
         * was <b>dead code that no assertion covered</b> — and {@code BaseEntity} is the standing
         * proof, since its own {@code READ_ONLY} annotation has always been on the field.
         *
         * <p>🔑 <b>A surviving mutation is not always a hole in the gate; sometimes it is the
         * gate telling you the code has a branch nothing needs.</b> Shipping it would have been
         * an untested path in a security control.
         */
        private static boolean isEntityPrimaryKey(Annotated a) {
            return a instanceof AnnotatedField f
                && "id".equals(f.getName())
                && isMapped(f.getDeclaringClass());
        }

        /**
         * {@code @Entity} covers the 407 concrete types; {@code @MappedSuperclass} covers an
         * {@code id} declared one level up, which is where {@code BaseEntity} keeps it — so the
         * check must ask about both or it would miss the very population that already works.
         */
        private static boolean isMapped(Class<?> c) {
            return c != null
                && (c.isAnnotationPresent(Entity.class) || c.isAnnotationPresent(MappedSuperclass.class));
        }
    }
}
