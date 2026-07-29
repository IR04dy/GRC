package com.tss.grc.module.attachment.service;

import com.tss.grc.security.GrcUserDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * SCR-004 — an attachment is exactly as confidential as the record it hangs off.
 *
 * <h3>What was wrong</h3>
 * <p>The generic attachment endpoints carried one coarse guard — {@code EVIDENCE:READ} — and
 * scoped the row to the tenant. Nothing checked whether the caller could read the
 * {@code entityType/entityId} the file belongs to, and {@code entityType} is a free-form string
 * supplied by the client. <b>Reproduced live before this fix</b> with {@code so_officer}, a persona
 * holding {@code EVIDENCE_READ} but not {@code COMPLIANCE_READ}:
 *
 * <pre>
 *   GET /api/policy/documents/46            -> 403   "you may not see this policy"
 *   GET /api/v1/attachments/9/download      -> 200   ...here are its bytes
 *   GET /api/v1/attachments/PolicyDocument/46 -> 200  ...and everything else attached to it
 *   GET /api/v1/attachments/NOT_A_TYPE/1    -> 200   (an oracle for enumerating types and ids)
 * </pre>
 *
 * <h3>The rule</h3>
 * <p><b>An attachment inherits its parent record's own read guard.</b> Not a new access-control
 * model of its own — the parent's, whatever that happens to be. That choice is deliberate: a
 * stricter rule of my own invention would 403 users who can legitimately open the record, which is
 * how six of seven prescriptions in this programme were falsified when measured. So each entry
 * below was read off the parent controller rather than inferred from the entity's name.
 *
 * <p>The platform guards records in three different ways, and the registry has to speak all three:
 * <ul>
 *   <li>{@code hasPermission('MODULE','READ')} — the 94-permission catalogue (most modules)</li>
 *   <li>{@code hasAnyRole(...)} — the Saudi/financial-sector modules (cpty, fraud, openbanking,
 *       opres, rrp, regreporting, outsourcing, ccm, aml, ethics, dataclass, ai-lifecycle) and a
 *       number of BCM and privacy sub-controllers</li>
 *   <li>{@code isAuthenticated()} — a handful of read endpoints have no authority to inherit</li>
 * </ul>
 *
 * <h3>The residue, deliberately visible</h3>
 * <p>Where the parent's read endpoint is {@code isAuthenticated()}, there is nothing to inherit and
 * this class does <b>not</b> invent a gate: it records the fact via {@link ParentGuard#note()} and
 * {@link #authenticatedOnlyTypes()} counts them, so the number can be tracked down instead of
 * hiding inside a class comment. The real defect for those types is the parent endpoint's missing
 * guard, which belongs to the authorization ratchet, not here.
 *
 * <h3>Classification is NOT enforced here, and that is a decision, not an oversight</h3>
 * <p>The report also asks for "classification clearance". There is no clearance model to enforce:
 * {@code iam.users} has no clearance column and no table anywhere in the schema does
 * ({@code SELECT ... WHERE COLUMN_NAME LIKE '%clearance%'} returns nothing). Enforcing
 * {@code RESTRICTED} would mean inventing an access-control dimension and guessing which of the 55
 * personas clears it — a feature, not a fix, and one that silently locks people out of their own
 * evidence when guessed wrong. The label stays advisory until someone decides what it means; the
 * decision is recorded in the plan.
 */
@Slf4j
@Component
public class AttachmentAccessPolicy {

    /**
     * Every authority that can see the parent record. {@code modules} are permission-catalogue codes
     * ({@code MODULE_READ} / {@code MODULE_WRITE} authorities); {@code roles} are bare role names as
     * they appear in {@code hasAnyRole(...)} (matched as {@code ROLE_x}). Either may be empty; both
     * empty means the parent read is authenticated-only and {@code note} says so.
     *
     * <p>It is a <b>union</b>, and getting that wrong is how this slice nearly shipped a regression.
     * A record can be served by more than one module's controllers, and a single-route mapping
     * under-counts who may legitimately see it — the check then 403s a user on their own screen.
     * The measured case: the cyber workspace's policy panel is wired to the shared policy service, so
     * {@code PolicyDocument} rows arrive through {@code CYBER:READ} as well as {@code COMPLIANCE:READ}.
     * Same reason a parent may list both a module and roles: different endpoints over the same rows.
     */
    public record ParentGuard(Set<String> modules, Set<String> roles, String note) {

        /**
         * One or more module codes. More than one is not redundancy — some records are served by
         * two modules' controllers, and "who can see this record" is the union. {@code PolicyDocument}
         * is the measured case: {@code /api/policy/documents} is {@code COMPLIANCE:READ} and
         * {@code /api/v1/cyber/policies} is {@code CYBER:READ}, both over the same rows.
         */
        static ParentGuard perm(String... modules) {
            return new ParentGuard(Set.of(modules), Set.of(), null);
        }

        static ParentGuard roles(String... roles) {
            return new ParentGuard(Set.of(), Set.of(roles), null);
        }

        static ParentGuard permOrRoles(String module, String... roles) {
            return new ParentGuard(Set.of(module), Set.of(roles), null);
        }

        /** The parent's read endpoint is {@code isAuthenticated()} — nothing to inherit. */
        static ParentGuard authenticated(String note) {
            return new ParentGuard(Set.of(), Set.of(), note);
        }

        public boolean inheritsNoAuthority() {
            return modules.isEmpty() && roles.isEmpty();
        }
    }

    // Role lists reused across several entity types, named so a drift shows up in one place.
    private static final String[] CPTY_READ = {
        "PLATFORM_ADMIN", "RISK_MANAGER", "COMPLIANCE_OFFICER", "INTERNAL_AUDITOR",
        "CFO", "CRO", "CREDIT_OFFICER"};
    private static final String[] OBLIGATION_READ = {
        "COMPLIANCE_OFFICER", "RISK_MANAGER", "AUDITOR", "CISO", "PLATFORM_ADMIN", "BCM_MANAGER"};
    private static final String[] AI_LC_READ = {
        "PLATFORM_ADMIN", "RISK_MANAGER", "COMPLIANCE_OFFICER", "AUDITOR", "CISO", "DPO",
        "CIO", "CRO"};
    private static final String[] DATACLASS_READ = {
        "PLATFORM_ADMIN", "COMPLIANCE_OFFICER", "AUDITOR", "DPO", "CISO", "CIO", "RISK_MANAGER"};

    /**
     * entityType (as the client sends it) → the parent's read guard.
     *
     * <p>Keys are normalised by {@link #norm(String)}, so {@code PolicyDocument},
     * {@code POLICY_DOCUMENT} and {@code policydocument} are the same entry — the codebase genuinely
     * uses both conventions for the same records ({@code PolicyDocument} from the policy pages,
     * {@code BCM_CAPA} from BCM), and a registry that distinguished them would fail closed on a
     * spelling difference.
     */
    private static final Map<String, ParentGuard> DECLARED = new LinkedHashMap<>();
    static {
        // ── AI / model governance ────────────────────────────────────────────
        DECLARED.put("AI_MODEL", ParentGuard.roles(
            "RISK_MANAGER", "CISO", "MODEL_OWNER", "DPO", "AUDITOR", "PLATFORM_ADMIN"));
        DECLARED.put("AI_RECERTIFICATION", ParentGuard.roles(AI_LC_READ));
        DECLARED.put("AI_RETRAINING",      ParentGuard.roles(AI_LC_READ));
        DECLARED.put("AI_VALIDATION",      ParentGuard.roles(AI_LC_READ));
        DECLARED.put("AI_ETHICS_REVIEW",   ParentGuard.perm("PRIVACY"));   // data-officer register

        // ── Audit ───────────────────────────────────────────────────────────
        DECLARED.put("AUDIT_ENGAGEMENT", ParentGuard.perm("AUDIT"));
        DECLARED.put("AUDIT_FINDING",    ParentGuard.perm("AUDIT"));
        DECLARED.put("IA_ENGAGEMENT",    ParentGuard.roles(
            "PLATFORM_ADMIN", "CHIEF_AUDIT_EXECUTIVE", "AUDIT_DIRECTOR", "INTERNAL_AUDITOR_LEAD",
            "INTERNAL_AUDITOR", "AUDIT_COMMITTEE_MEMBER", "BOARD_MEMBER", "RISK_MANAGER",
            "COMPLIANCE_OFFICER", "EXTERNAL_AUDITOR", "AUDITOR", "CISO"));

        // ── BCM ─────────────────────────────────────────────────────────────
        DECLARED.put("BCM_CAPA",       ParentGuard.perm("BCM"));
        DECLARED.put("BCM_GOVERNANCE", ParentGuard.perm("BCM"));
        DECLARED.put("BCP_PLAN",       ParentGuard.perm("BCM"));
        DECLARED.put("BIA_ASSESSMENT", ParentGuard.perm("BCM"));
        DECLARED.put("CRISIS_EVENT",   ParentGuard.perm("BCM"));
        DECLARED.put("DR_PLAN",        ParentGuard.perm("BCM"));
        DECLARED.put("EXERCISE_SCENARIO", ParentGuard.permOrRoles("BCM",
            "BCM_MANAGER", "CISO", "RISK_MANAGER", "AUDITOR", "PLATFORM_ADMIN"));
        // CrisisAarController's three read endpoints are isAuthenticated() — measured, not assumed.
        DECLARED.put("CRISIS_AAR", ParentGuard.authenticated(
            "CrisisAarController GET endpoints are isAuthenticated(); no authority to inherit"));

        // ── Privacy / PDPL (privacy + data_officer registers) ────────────────
        DECLARED.put("BREACH",                 ParentGuard.perm("PRIVACY"));
        DECLARED.put("CONSENT",                ParentGuard.perm("PRIVACY"));
        DECLARED.put("DATA_CATALOG",           ParentGuard.perm("PRIVACY"));
        DECLARED.put("DATA_QUALITY_RULE",      ParentGuard.perm("PRIVACY"));
        DECLARED.put("DATA_SHARING_AGREEMENT", ParentGuard.perm("PRIVACY"));
        DECLARED.put("DPIA",                   ParentGuard.perm("PRIVACY"));
        DECLARED.put("DSR",                    ParentGuard.perm("PRIVACY"));
        DECLARED.put("NDR",                    ParentGuard.perm("PRIVACY"));
        DECLARED.put("PRIVACY_NOTICE",         ParentGuard.perm("PRIVACY"));
        DECLARED.put("PROCESSOR_ASSESSMENT",   ParentGuard.perm("PRIVACY"));
        DECLARED.put("RETENTION_RULE",         ParentGuard.perm("PRIVACY"));
        DECLARED.put("ROPA",                   ParentGuard.perm("PRIVACY"));
        DECLARED.put("SUBPROCESSOR",           ParentGuard.roles(
            "DPO", "COMPLIANCE_OFFICER", "PRIVACY_OFFICER", "CISO", "RISK_MANAGER",
            "AUDITOR", "INTERNAL_AUDITOR", "PLATFORM_ADMIN"));

        // ── Compliance / policy / controls ───────────────────────────────────
        DECLARED.put("POLICY",                ParentGuard.perm("COMPLIANCE", "CYBER"));
        DECLARED.put("PolicyDocument",        ParentGuard.perm("COMPLIANCE", "CYBER"));
        DECLARED.put("CONTROL",               ParentGuard.perm("COMPLIANCE"));
        DECLARED.put("Obligation",            ParentGuard.roles(OBLIGATION_READ));
        DECLARED.put("ObligationAttestation", ParentGuard.roles(OBLIGATION_READ));
        DECLARED.put("CCM_TEST",              ParentGuard.roles(
            "CISO", "RISK_MANAGER", "COMPLIANCE_OFFICER", "INTERNAL_AUDITOR", "AUDITOR",
            "PLATFORM_ADMIN"));

        // ── Risk / change / incident ──────────────────────────────────────────
        DECLARED.put("RISK",           ParentGuard.perm("RISK"));
        DECLARED.put("Kri",            ParentGuard.roles(
            "RISK_MANAGER", "CISO", "COMPLIANCE_OFFICER", "INTERNAL_AUDITOR", "AUDITOR",
            "BCM_MANAGER", "DPO", "PLATFORM_ADMIN"));
        DECLARED.put("CHANGE_REQUEST", ParentGuard.perm("CHANGE"));
        DECLARED.put("INCIDENT",       ParentGuard.perm("INCIDENT"));

        // ── Cyber ────────────────────────────────────────────────────────────
        DECLARED.put("CYBER_EVIDENCE", ParentGuard.perm("CYBER"));
        DECLARED.put("ASM_ASSET",      ParentGuard.roles(
            "CISO", "SOC_LEAD", "RISK_MANAGER", "COMPLIANCE_OFFICER", "AUDITOR",
            "INTERNAL_AUDITOR", "PLATFORM_ADMIN"));

        // ── Third party / supply chain ────────────────────────────────────────
        DECLARED.put("TPRM_VENDOR",                  ParentGuard.perm("TPRM"));
        DECLARED.put("THIRD_PARTY_CYBER_ASSESSMENT", ParentGuard.permOrRoles("TPRM",
            "CISO", "RISK_MANAGER", "PLATFORM_ADMIN"));
        DECLARED.put("FOURTH_PARTY_EXPOSURE",        ParentGuard.perm("SCM"));
        DECLARED.put("VENDOR_BCP_CAMPAIGN",          ParentGuard.roles(
            "TPRM_LEAD", "RISK_MANAGER", "AUDITOR", "CISO", "BCM_MANAGER", "PLATFORM_ADMIN"));
        DECLARED.put("OUTSOURCING", ParentGuard.roles(
            "PLATFORM_ADMIN", "RISK_MANAGER", "COMPLIANCE_OFFICER", "AUDITOR", "CISO",
            "CIO", "CRO"));

        // ── Ethics / conduct ─────────────────────────────────────────────────
        DECLARED.put("WHISTLEBLOWER_CASE", ParentGuard.roles(
            "ETHICS_OFFICER", "COMPLIANCE_OFFICER", "INTERNAL_AUDITOR", "AUDIT_COMMITTEE_MEMBER",
            "PLATFORM_ADMIN"));
        DECLARED.put("COI_CAMPAIGN", ParentGuard.roles(
            "PLATFORM_ADMIN", "ETHICS_OFFICER", "BOARD_SECRETARY", "COMPLIANCE_OFFICER"));
        DECLARED.put("COMPLAINT", ParentGuard.roles(
            "PLATFORM_ADMIN", "CONSUMER_PROTECTION_OFFICER", "COMPLIANCE_OFFICER"));
        DECLARED.put("FRAUD_CASE", ParentGuard.roles(
            "PLATFORM_ADMIN", "FRAUD_OFFICER", "FRAUD_INVESTIGATOR", "COMPLIANCE_OFFICER",
            "INTERNAL_AUDITOR", "RISK_MANAGER"));
        DECLARED.put("SANCTIONS_CHECK", ParentGuard.roles(
            "PLATFORM_ADMIN", "AML_OFFICER", "COMPLIANCE_OFFICER", "CISO", "INTERNAL_AUDITOR",
            "AUDIT_COMMITTEE_MEMBER", "KYC_OFFICER", "ONBOARDING_AGENT", "AUDITOR"));

        // ── Saudi financial-sector registers (role-guarded parents) ───────────
        DECLARED.put("COUNTERPARTY", ParentGuard.roles(CPTY_READ));
        DECLARED.put("OB_TPP", ParentGuard.roles(
            "PLATFORM_ADMIN", "COMPLIANCE_OFFICER", "RISK_MANAGER", "INTERNAL_AUDITOR", "CISO",
            "OPEN_BANKING_OFFICER"));
        DECLARED.put("OPRES_ASSESSMENT", ParentGuard.roles(
            "PLATFORM_ADMIN", "RISK_MANAGER", "COMPLIANCE_OFFICER", "INTERNAL_AUDITOR",
            "BCM_MANAGER", "BOARD_SECRETARY"));
        DECLARED.put("RRP_CYCLE", ParentGuard.roles(
            "PLATFORM_ADMIN", "RISK_MANAGER", "COMPLIANCE_OFFICER", "INTERNAL_AUDITOR",
            "BCM_MANAGER", "BOARD_SECRETARY", "CFO", "CRO"));
        DECLARED.put("REG_FILING", ParentGuard.roles(
            "PLATFORM_ADMIN", "RISK_MANAGER", "COMPLIANCE_OFFICER", "INTERNAL_AUDITOR",
            "BOARD_SECRETARY", "CFO", "CISO", "DPO"));

        // ── Data classification (NDMO) ───────────────────────────────────────
        DECLARED.put("DATACLASS_ASSET", ParentGuard.roles(DATACLASS_READ));
        DECLARED.put("DATACLASS_FLOW",  ParentGuard.roles(DATACLASS_READ));
    }

    /** Normalised view — the one consulted at runtime. */
    private static final Map<String, ParentGuard> GUARDS;
    static {
        Map<String, ParentGuard> m = new HashMap<>();
        DECLARED.forEach((k, v) -> m.put(norm(k), v));
        GUARDS = Collections.unmodifiableMap(m);
    }

    /**
     * Case- and separator-insensitive key: {@code PolicyDocument}, {@code POLICY_DOCUMENT} and
     * {@code policy-document} all collapse to {@code POLICYDOCUMENT}. Both conventions are in live
     * use for the same records, so this is a correctness requirement, not tidiness.
     */
    private static String norm(String entityType) {
        if (entityType == null) return "";
        StringBuilder sb = new StringBuilder(entityType.length());
        for (int i = 0; i < entityType.length(); i++) {
            char c = entityType.charAt(i);
            if (Character.isLetterOrDigit(c)) sb.append(Character.toUpperCase(c));
        }
        return sb.toString();
    }

    // ── the check ────────────────────────────────────────────────────────────

    /**
     * The caller must be able to <b>see the parent record</b>. Every attachment operation goes
     * through this, and {@code operation} only labels the log line.
     *
     * <p><b>Why visibility and not the parent's write authority.</b> My first cut required the
     * parent module's WRITE for uploads, and the live probe falsified it: {@code so_officer} (a
     * CISO holding {@code EVIDENCE_WRITE} and {@code RISK_READ}, no {@code RISK_WRITE}) could no
     * longer attach anything to a risk, and {@code auditor} ({@code EVIDENCE_WRITE} +
     * {@code BCM_READ}) could no longer attach evidence to a BCM plan. Both are the
     * evidence-custodian pattern the platform models on purpose — {@code EVIDENCE:WRITE} exists
     * precisely so reviewers can file evidence against records they review but do not own. A rule
     * that required parent WRITE would have made that permission nearly redundant and broken two
     * seeded personas to close a finding that is about <em>disclosure</em>.
     *
     * <p>So the pair is: this method (may you see the record?) plus the endpoint's own
     * {@code EVIDENCE:READ / WRITE / DELETE} (may you handle evidence at all?). What remains is
     * that a holder of {@code EVIDENCE:WRITE} can attach a file to any record they can read,
     * including one they cannot edit. That is deliberate and bounded: every row records
     * {@code uploaded_by}, which the response and the audit trail carry, so a planted file is
     * attributable rather than anonymous. Tightening it further is a decision about who may file
     * evidence, not a fix for SCR-004, and it belongs with whoever owns the permission matrix.
     */
    public void requireAccess(String entityType, String operation) {
        require(entityType, operation);
    }

    private void require(String entityType, String operation) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            // Off-request or unauthenticated: nothing to check against, so refuse. No caller
            // reaches these endpoints without a session today; a future scheduler that needs bytes
            // should use the storage service directly rather than acquire an implicit exemption.
            log.warn("Attachment {} refused for entityType '{}' — no authentication in context",
                operation, entityType);
            throw new AccessDeniedException("Not authorised for this attachment.");
        }
        // The unknown-type check runs BEFORE the platform-admin bypass, deliberately. `admin` is the
        // persona everything gets tested with, so if admins skipped this check a missing registry
        // entry would pass every dev walkthrough and 403 only for the customer. Refusing it for
        // everyone makes a gap fail for the person most likely to notice — and an admin loses
        // nothing real, since an unrecognised entity type is a typo, not a record.
        ParentGuard guard = GUARDS.get(norm(entityType));
        if (guard == null) {
            // Fail closed. `entityType` is client-supplied, so an unknown value is either a typo
            // in a new UI panel — caught by AttachmentEntityTypeCoverageTest before it ships — or
            // someone probing the surface. Previously it answered 200 with an empty list, which
            // made the endpoint an oracle for guessing types and ids.
            log.warn("Attachment {} refused: unknown entityType '{}' (user={})",
                operation, entityType, username(auth));
            throw new AccessDeniedException("Unknown attachment entity type.");
        }
        if (isPlatformAdmin(auth)) {
            return;                       // same bypass ModulePermissionEvaluator applies
        }
        if (guard.inheritsNoAuthority()) {
            return;                       // measured residue — see the class comment
        }

        // MODULE_WRITE counts as visibility too: a persona that may change the record can
        // self-evidently see it, and the seeded matrix never grants WRITE without READ. Accepting
        // both means a future grant that does would not produce an absurd denial.
        if (guard.modules().stream().anyMatch(m -> hasAuthority(auth, m + "_READ")
                                                   || hasAuthority(auth, m + "_WRITE"))) {
            return;
        }
        if (guard.roles().stream().anyMatch(r -> hasAuthority(auth, "ROLE_" + r))) {
            return;
        }

        log.warn("Attachment {} refused for entityType '{}': user={} cannot see the parent record "
                 + "(needs {}{})", operation, entityType, username(auth),
            guard.modules().isEmpty() ? "" : guard.modules() + "_READ",
            guard.roles().isEmpty() ? "" : " or one of " + guard.roles());
        throw new AccessDeniedException("You do not have access to this record's attachments.");
    }

    // ── read-only views, for the tests and for reporting ─────────────────────

    /** The registry as declared (raw keys), for coverage tests and documentation. */
    public static Map<String, ParentGuard> declared() {
        return Collections.unmodifiableMap(DECLARED);
    }

    /** Whether an entityType resolves at all — what the FE-coverage ratchet asserts. */
    public static Optional<ParentGuard> guardFor(String entityType) {
        return Optional.ofNullable(GUARDS.get(norm(entityType)));
    }

    /**
     * Entity types whose parent read endpoint is {@code isAuthenticated()}, so the attachment has
     * no authority to inherit. This number is the honest measure of what SCR-004 leaves open; the
     * ratchet test pins it so it can only shrink.
     */
    public static Set<String> authenticatedOnlyTypes() {
        return DECLARED.entrySet().stream()
            .filter(e -> e.getValue().inheritsNoAuthority())
            .map(Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static boolean isPlatformAdmin(Authentication auth) {
        return hasAuthority(auth, "ROLE_PLATFORM_ADMIN");
    }

    private static boolean hasAuthority(Authentication auth, String authority) {
        return auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(authority::equals);
    }

    private static String username(Authentication auth) {
        return auth.getPrincipal() instanceof GrcUserDetails u ? u.getUsername() : auth.getName();
    }
}
