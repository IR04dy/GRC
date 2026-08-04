package com.tss.grc.module.change_mgmt.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.change_mgmt.dto.ChangeModelRequest;
import com.tss.grc.module.change_mgmt.entity.ChangeModel;
import com.tss.grc.module.change_mgmt.entity.ChangeModelStatus;
import com.tss.grc.module.change_mgmt.entity.ChangeModelUsage;
import com.tss.grc.module.change_mgmt.entity.ChangeRequest;
import com.tss.grc.module.change_mgmt.repository.ChangeModelRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeModelUsageRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeRequestRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * S8e / CE-2 — "pre-approved" must be a record, not a word.
 *
 * <p>Measured 2026-08-04 in both databases: <b>5</b> STANDARD changes, all on the {@code AUTO} cell
 * with {@code min_approvals = 0}, <b>0</b> ever referred to a committee. A change was pre-approved
 * because somebody typed {@code STANDARD} and {@code LOW}. These tests pin the three things that
 * change that, each of which a later tidy-up could quietly undo.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChangeModelCatalogueTest {

    private static final Long ORG = 1L;
    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration/sqlserver");

    @Mock ChangeModelRepository modelRepo;
    @Mock ChangeModelUsageRepository usageRepo;
    @Mock ChangeRequestRepository changeRepo;
    @InjectMocks ChangeModelService svc;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("so_officer", "x", List.of()));
        when(modelRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(usageRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(modelRepo.findByOrganisationIdAndCode(anyLong(), any())).thenReturn(Optional.empty());
    }

    @AfterEach void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    // ── fixtures ────────────────────────────────────────────────────────────

    private static ChangeModelRequest req(String code) {
        return new ChangeModelRequest(code, "Onboard a new server", "تهيئة خادم جديد",
            null, null, "STANDARD", "LOW", null, "1. patch 2. baseline", "restore snapshot",
            "smoke test", 24, "ANNUAL");
    }

    private static ChangeModel model(String status, boolean preApproved, LocalDateTime until) {
        ChangeModel m = new ChangeModel();
        m.setId(7L);
        m.setOrganisationId(ORG);
        m.setCode("STD-SRV-01");
        m.setNameEn("Onboard a new server");
        m.setNameAr("تهيئة خادم جديد");
        m.setChangeType("STANDARD");
        m.setStatus(status);
        m.setPreApproved(preApproved);
        m.setAuthorisedByName(preApproved ? "compliance" : null);
        m.setAuthorisedAt(preApproved ? LocalDateTime.now().minusDays(1) : null);
        m.setAuthorisedUntil(until);
        return m;
    }

    private static ChangeRequest change(String type) {
        ChangeRequest c = new ChangeRequest();
        c.setId(99L);
        c.setOrganisationId(ORG);
        c.setChangeType(type);
        return c;
    }

    private static String migration() {
        try (Stream<Path> walk = Files.walk(MIGRATIONS)) {
            return walk.filter(p -> p.getFileName().toString().startsWith("V354__"))
                       .findFirst()
                       .map(p -> {
                           try {
                               return Files.readString(p, StandardCharsets.UTF_8);
                           } catch (IOException e) {
                               throw new UncheckedIOException(e);
                           }
                       })
                       .orElseThrow(() -> new AssertionError(
                           "V354 is missing — the catalogue has no table"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ── the pre-approval predicate ──────────────────────────────────────────

    @Test
    @DisplayName("pre-approval needs the flag AND the status AND an unexpired date — all three")
    void livePreApprovalNeedsAllThree() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime future = now.plusDays(30);

        assertThat(model(ChangeModelStatus.APPROVED, true, future).isLivePreApproval(now))
            .as("the complete case").isTrue();
        assertThat(model(ChangeModelStatus.APPROVED, false, future).isLivePreApproval(now))
            .as("the flag alone is what G-2 is about — a label is not an authority").isFalse();
        assertThat(model(ChangeModelStatus.DRAFT, true, future).isLivePreApproval(now))
            .as("a DRAFT must not authorise work").isFalse();
        assertThat(model(ChangeModelStatus.SUSPENDED, true, future).isLivePreApproval(now))
            .as("a withdrawn authority confers nothing while it is withdrawn").isFalse();
        assertThat(model(ChangeModelStatus.APPROVED, true, null).isLivePreApproval(now))
            .as("no expiry is F2-62's shape: a control that can never come due").isFalse();
        assertThat(model(ChangeModelStatus.APPROVED, true, now.minusDays(1)).isLivePreApproval(now))
            .as("an EXPIRED pre-approval authorises nothing — the same refusal S8a made for EXPIRED "
                + "evidence, which is 29 of the 78 live evidence items")
            .isFalse();
    }

    @Test
    @DisplayName("an expired pre-approval is distinguishable from one that never existed")
    void expiryIsItsOwnState() {
        LocalDateTime now = LocalDateTime.now();
        assertThat(model(ChangeModelStatus.APPROVED, true, now.minusDays(1)).isExpiredPreApproval(now))
            .isTrue();
        assertThat(model(ChangeModelStatus.DRAFT, false, null).isExpiredPreApproval(now))
            .as("never authorised is not the same as lapsed, and the remedies differ").isFalse();
    }

    // ── the DTO is the four-eyes rule ───────────────────────────────────────

    @Test
    @DisplayName("the request DTO cannot carry a pre-approval — that is what makes the CHECK enough")
    void requestDtoCarriesNoAuthority() {
        List<String> fields = Stream.of(ChangeModelRequest.class.getRecordComponents())
            .map(java.lang.reflect.RecordComponent::getName).toList();
        assertThat(fields)
            .as("ck_cmodel_preapproval only checks that the four authorisation fields AGREE, not who "
                + "set them. If a caller could post them, a CHANGE:WRITE holder could self-issue a "
                + "pre-approval and the constraint would pass it.")
            .doesNotContain("status", "preApproved", "authorisedById", "authorisedByName",
                            "authorisedAt", "authorisedUntil", "isDeleted", "organisationId", "id");
        assertThat(fields).contains("code", "nameEn", "nameAr", "changeType");
    }

    @Test
    @DisplayName("authoring always produces a DRAFT that confers nothing")
    void createIsAlwaysDraft() {
        ChangeModel m = svc.create(req("STD-SRV-01"));
        assertThat(m.getStatus()).isEqualTo(ChangeModelStatus.DRAFT);
        assertThat(m.getPreApproved()).isFalse();
        assertThat(m.getAuthorisedUntil()).isNull();
        assertThat(m.getCreatedBy()).as("the author is the authenticated principal, never a body field")
            .isEqualTo("so_officer");
    }

    @Test
    @DisplayName("a duplicate code is refused — the code is the key a change cites")
    void duplicateCodeRefused() {
        when(modelRepo.findByOrganisationIdAndCode(ORG, "STD-SRV-01"))
            .thenReturn(Optional.of(model(ChangeModelStatus.APPROVED, true, LocalDateTime.now().plusDays(9))));
        assertThatThrownBy(() -> svc.create(req("STD-SRV-01")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already exists");
    }

    // ── the approval act ────────────────────────────────────────────────────

    @Test
    @DisplayName("the author may not approve their own model")
    void authorCannotApproveOwnModel() {
        ChangeModel m = model(ChangeModelStatus.DRAFT, false, null);
        m.setCreatedBy("so_officer");                       // same as the authenticated principal
        when(modelRepo.findByIdAndOrganisationId(7L, ORG)).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> svc.approve(7L, 180, null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Segregation of duty");
    }

    @Test
    @DisplayName("a pre-approval must state a bounded validity period")
    void validityIsRequiredAndBounded() {
        ChangeModel m = model(ChangeModelStatus.DRAFT, false, null);
        m.setCreatedBy("someone_else");
        when(modelRepo.findByIdAndOrganisationId(7L, ORG)).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> svc.approve(7L, null, null))
            .as("a pre-approval with no expiry is not a control")
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("how long it lasts");
        assertThatThrownBy(() -> svc.approve(7L, 3650, null))
            .as("'what stops a tenant choosing 3,650 days?' is the first examiner question")
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> svc.approve(7L, 1, null))
            .as("and a one-day pre-approval is a way of disabling the review, not of doing it")
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("approving stamps the authority, the moment and the expiry together")
    void approveStampsEverything() {
        ChangeModel m = model(ChangeModelStatus.DRAFT, false, null);
        m.setCreatedBy("someone_else");
        when(modelRepo.findByIdAndOrganisationId(7L, ORG)).thenReturn(Optional.of(m));

        ChangeModel out = svc.approve(7L, 180, "reviewed at the July CAB");
        assertThat(out.getStatus()).isEqualTo(ChangeModelStatus.APPROVED);
        assertThat(out.getPreApproved()).isTrue();
        assertThat(out.getAuthorisedByName()).isEqualTo("so_officer");
        assertThat(out.getAuthorisedAt()).isNotNull();
        assertThat(out.getAuthorisedUntil()).isAfter(out.getAuthorisedAt());
        assertThat(out.isLivePreApproval(LocalDateTime.now())).isTrue();
    }

    @Test
    @DisplayName("a RETIRED model cannot be re-approved, and suspending withdraws the authority")
    void retiredIsFinalAndSuspendWithdraws() {
        ChangeModel retired = model(ChangeModelStatus.RETIRED, false, null);
        retired.setCreatedBy("someone_else");
        when(modelRepo.findByIdAndOrganisationId(7L, ORG)).thenReturn(Optional.of(retired));
        assertThatThrownBy(() -> svc.approve(7L, 180, null))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("RETIRED");

        ChangeModel live = model(ChangeModelStatus.APPROVED, true, LocalDateTime.now().plusDays(90));
        when(modelRepo.findByIdAndOrganisationId(7L, ORG)).thenReturn(Optional.of(live));
        ChangeModel suspended = svc.setStatus(7L, ChangeModelStatus.SUSPENDED);
        assertThat(suspended.getPreApproved()).as("suspending withdraws the pre-approval").isFalse();
        assertThat(suspended.getAuthorisedByName())
            .as("but the history of who authorised it stays readable").isEqualTo("compliance");
    }

    @Test
    @DisplayName("APPROVED cannot be reached through the plain status endpoint")
    void statusEndpointCannotGrantPreApproval() {
        assertThatThrownBy(() -> svc.setStatus(7L, ChangeModelStatus.APPROVED))
            .as("granting an authority has to record its expiry, which a bare status change cannot")
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("approve endpoint");
    }

    @Test
    @DisplayName("an APPROVED model's procedure cannot be edited in place")
    void approvedModelIsImmutable() {
        when(modelRepo.findByIdAndOrganisationId(7L, ORG))
            .thenReturn(Optional.of(model(ChangeModelStatus.APPROVED, true, LocalDateTime.now().plusDays(30))));
        assertThatThrownBy(() -> svc.update(7L, req("STD-SRV-01")))
            .as("editing a live authority changes what was authorised without re-authorisation")
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("cannot be edited");
    }

    // ── applying a model ────────────────────────────────────────────────────

    @Test
    @DisplayName("only a live pre-approval may be applied to a change")
    void applyRefusesEverythingButLive() {
        when(changeRepo.findById(99L)).thenReturn(Optional.of(change("STANDARD")));
        when(usageRepo.findByOrganisationIdAndChangeRequestId(ORG, 99L)).thenReturn(Optional.empty());

        when(modelRepo.findByIdAndOrganisationId(7L, ORG))
            .thenReturn(Optional.of(model(ChangeModelStatus.DRAFT, false, null)));
        assertThatThrownBy(() -> svc.applyToChange(99L, 7L, null))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("confers no pre-approval");

        when(modelRepo.findByIdAndOrganisationId(7L, ORG))
            .thenReturn(Optional.of(model(ChangeModelStatus.APPROVED, true, LocalDateTime.now().minusDays(1))));
        assertThatThrownBy(() -> svc.applyToChange(99L, 7L, null))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("expired");

        ChangeModel normalModel = model(ChangeModelStatus.APPROVED, true, LocalDateTime.now().plusDays(30));
        normalModel.setChangeType("NORMAL");
        when(modelRepo.findByIdAndOrganisationId(7L, ORG)).thenReturn(Optional.of(normalModel));
        assertThatThrownBy(() -> svc.applyToChange(99L, 7L, null))
            .as("a model for NORMAL work never pre-approved a STANDARD change")
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("describes a NORMAL change");

        when(modelRepo.findByIdAndOrganisationId(7L, ORG))
            .thenReturn(Optional.of(model(ChangeModelStatus.APPROVED, true, LocalDateTime.now().plusDays(30))));
        ChangeModelUsage u = svc.applyToChange(99L, 7L, null);
        assertThat(u.getChangeModelId()).isEqualTo(7L);
        assertThat(u.getAppliedByName()).isEqualTo("so_officer");
        assertThat(u.hasDeviation()).isFalse();
    }

    @Test
    @DisplayName("a change that deviated from its model is NOT model-backed")
    void deviationBreaksTheBacking() {
        ChangeModelUsage u = new ChangeModelUsage();
        u.setChangeRequestId(99L);
        u.setChangeModelId(7L);
        u.setDeviationNotes("skipped the smoke test, prod incident in progress");
        when(usageRepo.findByOrganisationIdAndChangeRequestId(ORG, 99L)).thenReturn(Optional.of(u));
        when(modelRepo.findByIdAndOrganisationId(7L, ORG))
            .thenReturn(Optional.of(model(ChangeModelStatus.APPROVED, true, LocalDateTime.now().plusDays(30))));

        assertThat(svc.liveModelFor(99L))
            .as("citing a procedure and then departing from it is not doing the pre-approved thing")
            .isEmpty();
        assertThat(svc.backingFor(99L))
            .containsEntry("modelBacked", false)
            .containsEntry("reason", "DEVIATED_FROM_MODEL")
            .containsEntry("deviation", true);
    }

    @Test
    @DisplayName("backing always states a reason, never a bare false")
    void backingAlwaysExplainsItself() {
        when(usageRepo.findByOrganisationIdAndChangeRequestId(ORG, 99L)).thenReturn(Optional.empty());
        assertThat(svc.backingFor(99L))
            .as("F2-95's shape: a flag with no reason is a control nobody can act on")
            .containsEntry("modelBacked", false)
            .containsEntry("reason", "NO_MODEL_CITED");

        ChangeModelUsage u = new ChangeModelUsage();
        u.setChangeRequestId(99L);
        u.setChangeModelId(7L);
        when(usageRepo.findByOrganisationIdAndChangeRequestId(ORG, 99L)).thenReturn(Optional.of(u));
        when(modelRepo.findByIdAndOrganisationId(7L, ORG))
            .thenReturn(Optional.of(model(ChangeModelStatus.APPROVED, true, LocalDateTime.now().minusDays(2))));
        assertThat(svc.backingFor(99L)).containsEntry("reason", "PRE_APPROVAL_EXPIRED");
    }

    // ── the vocabulary and the migration must agree ─────────────────────────

    @Test
    @DisplayName("ChangeModelStatus and ck_cmodel_status name the same four states")
    void javaAndSqlVocabulariesAgree() {
        String sql = migration();
        for (String s : ChangeModelStatus.ALL) {
            assertThat(sql)
                .as("ck_cmodel_status must permit %s — the F2-91 pattern: a Java constant and a "
                    + "database CHECK holding different versions of one closed set", s)
                .contains("'" + s + "'");
        }
        assertThat(ChangeModelStatus.ALL).hasSize(4);
        assertThat(ChangeModelStatus.isApplicable(ChangeModelStatus.APPROVED)).isTrue();
        assertThat(ChangeModelStatus.isApplicable(ChangeModelStatus.SUSPENDED)).isFalse();
    }

    @Test
    @DisplayName("V354 arrives inert: the enforcement flag defaults off and is asserted off")
    void migrationArrivesInert() {
        String sql = migration();
        assertThat(sql).contains("requires_model BIT NOT NULL");
        assertThat(sql).contains("DEFAULT 0");
        assertThat(sql)
            .as("the measured-safety claim must be asserted by the migration, not only written in "
                + "its header — 5 STANDARD changes exist in each database and 3 are in flight")
            .contains("WHERE requires_model = 1");
        assertThat(sql)
            .as("and the catalogue must ship empty, like V352's register and V345's clock")
            .contains("change_model must ship empty");
    }

    @Test
    @DisplayName("the pre-approval CHECK is proven to reject, not merely to exist")
    void migrationProvesTheCheckEnforces() {
        String sql = migration();
        assertThat(sql).contains("ck_cmodel_preapproval");
        assertThat(sql)
            .as("V354 self-check 4 inserts a bad row inside a rolled-back transaction and THROWs if "
                + "the constraint accepted it. A CHECK that is present but disabled is a comment.")
            .contains("BEGIN TRANSACTION")
            .contains("ROLLBACK TRANSACTION")
            .contains("is_not_trusted = 1");
    }

    @Test
    @DisplayName("the entity defaults requires_model to false, matching the column default")
    void entityDefaultMatchesColumnDefault() {
        assertThat(new com.tss.grc.module.change_mgmt.entity.ChangeAuthorisationMatrix()
            .getRequiresModel())
            .as("a Java default of true against a column default of 0 would make every new cell "
                + "strict the moment somebody saved it through JPA")
            .isFalse();
    }
}
