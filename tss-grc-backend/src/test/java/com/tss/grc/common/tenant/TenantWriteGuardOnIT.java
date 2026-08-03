package com.tss.grc.common.tenant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <h2>F2-92 — does the tenant write guard, switched ON, break inserts?</h2>
 *
 * <h3>Why this class exists</h3>
 * On 2026-08-02 production could not log in. {@code POST /api/auth/login} returned <b>500</b>
 * because {@code platform.refresh_token} could not be written — {@code "A result set was generated
 * for update."} — and it had been that way since 2026-07-28. Five days, found by a probe rather
 * than by monitoring. The mitigation was to set {@code grc.security.tenant-write-guard.enabled=false}
 * in the deployed {@code external.properties}, so <b>production currently runs with no
 * Hibernate-level cross-tenant write backstop at all</b>, and the finding recorded the cause as
 * <i>"it is the interceptor being registered that breaks inserts, not it blocking"</i>.
 *
 * <p>That explanation is load-bearing in the worst way: believed, it says a P0 control is
 * inherently unsafe and must stay off. Re-measured 2026-08-03, it does not survive:
 * <ul>
 *   <li>The flag has been {@code true} in {@code application-dev.yml} since {@code 695c907c}
 *       (<b>2026-07-26</b>) — seven days <em>before</em> the finding was filed, which contradicts
 *       its own premise that the flag was "unset in dev, which is why the insert-leg gap could not
 *       surface from local use".</li>
 *   <li>The dev backend logs {@code [TenantWriteGuard] interceptor WIRED} on every boot and
 *       {@code platform.refresh_token} holds <b>2,075 rows, the newest written that morning</b>.</li>
 *   <li>{@code TenantWriteGuardInterceptor} and {@code TenantWriteGuardConfig} are <b>byte-identical
 *       between the deployed commit {@code 473da706} and HEAD</b>.</li>
 *   <li>Both databases live on the same SQL Server instance and match on schema, compatibility
 *       level (150), collation, snapshot options, the single trigger in either database (on an
 *       unrelated table), and {@code refresh_token}'s exact {@code IDENTITY} + {@code ROWVERSION}
 *       shape.</li>
 * </ul>
 *
 * <h3>What this test does, and what it deliberately does not settle</h3>
 * It boots the real application with the guard <b>explicitly ON</b> and asserts the three things the
 * finding's explanation predicts would fail: a login writes a refresh-token row, an ordinary
 * tenant-scoped insert succeeds, and the guard is genuinely wired rather than silently absent. If
 * registering the interceptor broke inserts, this class could not go green.
 *
 * <p>It does <b>not</b> explain the production failure, and it must not be read as doing so. The
 * remaining difference is the deployed runtime — Flyway on, {@code external.properties}, the
 * service account, the named-instance JDBC URL — and narrowing that further needs the flag switched
 * back on in production, which is an operator decision and not one a test may take. What this class
 * removes is the ability to keep the control off on an explanation that was never reproduced: from
 * now on "the guard breaks inserts" is a claim the build answers in 20 seconds.
 *
 * <p>Lives in the {@code it} profile ({@code mvn -o verify -Pit}) with W1-5's harness, for the same
 * reason: it needs a real database and a real filter chain, and {@code mvn -o clean package} stays
 * hermetic.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        // The whole point of the class. Set here rather than relying on the dev profile so the
        // test still asserts what it claims if application-dev.yml ever flips the flag back.
        "grc.security.tenant-write-guard.enabled=true",
        // F2-74 — a booted context starts the audit-sealing scheduler against the shared dev
        // database; a previous session sealed 5,000 rows with no integrity key that way.
        "grc.audit.sealing-enabled=false",
        "spring.jpa.show-sql=false"
    }
)
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("F2-92 — the tenant write guard, ON, must not break inserts")
class TenantWriteGuardOnIT {

    @DynamicPropertySource
    static void requireALocalStack(DynamicPropertyRegistry registry) {
        String dbPassword = System.getenv("DB_PASSWORD");
        String jwtSecret = System.getenv("JWT_SECRET");
        if (isBlank(dbPassword) || isBlank(jwtSecret)) {
            throw new IllegalStateException("""

                F2-92 integration test needs the local stack. Set both, then re-run:

                  DB_PASSWORD  — dev SQL Server 'sa' password (see CLAUDE.md)
                  JWT_SECRET   — >=32 chars, no 'change-this'/'placeholder'

                  JWT_SECRET=... DB_PASSWORD=... mvn -o verify -Pit

                A failure and not a skip on purpose: a SKIPPED integration test inside a green
                build is an accurate signal nobody consumes.
                """);
        }
        registry.add("spring.datasource.password", () -> dbPassword);
        registry.add("grc.jwt.secret", () -> jwtSecret);
    }

    private static final String USER = "compliance";
    private static final String PASSWORD = "Admin@12345";
    /** Never `[REVIEW-TEST]` in a LIKE — brackets are a SQL character class (CHARINDEX instead). */
    private static final String TAG = "[REVIEW-TEST]";

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired(required = false) TenantWriteGuardInterceptor interceptor;

    @Test
    @DisplayName("the guard bean exists and the flag under test is genuinely on")
    void theGuardIsActuallyWiredSoTheOtherAssertionsMeanSomething() {
        // Anti-vacuity. If the bean were absent or the flag ignored, every assertion below would
        // pass while proving nothing about the guard — the shape of an inert control that reports
        // healthy (F2-62, F2-96).
        assertThat(interceptor)
            .as("TenantWriteGuardInterceptor must be a bean, or this class tests an absent control")
            .isNotNull();
    }

    @Test
    @DisplayName("login writes a refresh token — the exact insert that 500'd in production")
    void loginStillWritesARefreshTokenWithTheGuardOn() {
        Long before = jdbc.queryForObject(
            "SELECT COUNT(*) FROM platform.refresh_token", Long.class);

        ResponseEntity<String> res = rest.postForEntity("/api/auth/login",
            new HttpEntity<>(Map.of("username", USER, "password", PASSWORD), jsonHeaders(null)),
            String.class);

        assertThat(res.getStatusCode())
            .as("""
                POST /api/auth/login returned 500 in production for five days with this flag on, \
                reported as "A result set was generated for update." while inserting \
                platform.refresh_token — a table carrying both an IDENTITY and a ROWVERSION \
                column. If registering the interceptor is what breaks inserts, this is where it \
                breaks.""")
            .isEqualTo(HttpStatus.OK);

        Long after = jdbc.queryForObject(
            "SELECT COUNT(*) FROM platform.refresh_token", Long.class);
        assertThat(after)
            .as("a 200 with no row written would be the same defect wearing a success code")
            .isGreaterThan(before);
    }

    @Test
    @DisplayName("an ordinary tenant-scoped insert succeeds through the full stack")
    void anOrdinaryInsertSucceedsWithTheGuardOn() {
        String token = login();

        JsonNode created = post(token, "/api/v1/changes", Map.of(
            "changeCode", "CHG-GUARDON-IT",
            "titleEn", TAG + " F2-92 guard-on insert",
            "titleAr", TAG + " اختبار حارس الكتابة",
            "changeType", "STANDARD",
            "riskLevel", "LOW",
            "category", "APPLICATION"));

        long id = created.path("id").asLong();
        assertThat(id).as("the insert must return the identity Hibernate had to retrieve").isPositive();

        Long org = jdbc.queryForObject(
            "SELECT organisation_id FROM change_mgmt.change_requests WHERE id = ?", Long.class, id);
        assertThat(org)
            .as("the row must be stamped with the request's tenant — the guard's whole invariant")
            .isEqualTo(1L);

        // Clean up after itself: CHARINDEX for a literal, never LIKE with brackets.
        jdbc.update("DELETE FROM platform.audit_log WHERE entity_type = 'ChangeRequest' AND entity_id = ?", id);
        jdbc.update("DELETE FROM change_mgmt.change_requests WHERE id = ?", id);
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM change_mgmt.change_requests "
          + "WHERE CHARINDEX('F2-92 guard-on insert', ISNULL(title_en,'')) > 0", Long.class))
            .as("the walk must leave the shared dev database as it found it")
            .isZero();
    }

    @Test
    @DisplayName("the guard's insert leg still refuses a cross-tenant row (it inspects, it is not inert)")
    void theInsertLegStillBlocksACrossTenantWrite() {
        // Exercised through decideInsert rather than HTTP: every controller overwrites the body's
        // tenant, so no request can reach the interceptor with a foreign organisationId — which is
        // correct, and is also why this leg has no HTTP-reachable proof. The unit tests cover the
        // decision table; what matters here is that the SAME bean the context wired still refuses,
        // so "wired" and "enforcing" cannot drift apart.
        TenantContext.setCurrentTenant(1L);
        try {
            Object[] state = {2L};
            String[] props = {"organisationId"};
            assertThat(
                org.junit.jupiter.api.Assertions.assertThrows(SecurityException.class,
                    () -> interceptor.decideInsert(new Object(), 99L, state, props))
                    .getMessage())
                .as("a row destined for org 2 on an org-1 request must be refused, and the message "
                  + "must name both organisations so the operator can act")
                .contains("org 2")
                .contains("org 1");
        } finally {
            TenantContext.clear();
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String login() {
        ResponseEntity<String> res = rest.postForEntity("/api/auth/login",
            new HttpEntity<>(Map.of("username", USER, "password", PASSWORD), jsonHeaders(null)),
            String.class);
        assertThat(res.getStatusCode())
            .as("login as %s — a 401 here is usually a lockout counter, not a wrong password", USER)
            .isEqualTo(HttpStatus.OK);
        return readTree(res.getBody()).path("accessToken").asText();
    }

    private JsonNode post(String token, String path, Object body) {
        ResponseEntity<String> res = rest.exchange(path, HttpMethod.POST,
            new HttpEntity<>(body, jsonHeaders(token)), String.class);
        assertThat(res.getStatusCode().is2xxSuccessful())
            .as("POST %s -> %s %s", path, res.getStatusCode(), res.getBody())
            .isTrue();
        return readTree(res.getBody());
    }

    private HttpHeaders jsonHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null && !token.isBlank()) h.setBearerAuth(token);
        return h;
    }

    private JsonNode readTree(String body) {
        try {
            return json.readTree(body == null ? "{}" : body);
        } catch (Exception e) {
            throw new IllegalStateException("unparseable response: " + body, e);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
