package com.tss.grc.module.ethics.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ethics.entity.AmlAlert;
import com.tss.grc.module.ethics.repository.AmlAlertRepository;
import com.tss.grc.module.ethics.repository.KycSubjectRepository;
import com.tss.grc.module.iam.repository.UserRepository;
import com.tss.grc.module.iam.service.TenantUserDirectory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * F2-287 — when the TM engine detected the activity is a fact somebody can now state.
 *
 * <p>{@code ethics.aml_alerts.first_detected_at} was {@code insertable = false}, which refuses
 * <em>Hibernate</em> and not just the caller ([[F2-286]]), so the column could only ever hold
 * {@code sysutcdatetime()} at the instant its row was inserted — while the alert "surfaces from
 * the bank's TM engine" and the value is the start of the FIU reporting clock.
 *
 * <p>🔑 The precision was the tell in the opposite direction from {@code screened_at}: measured
 * on both databases, <b>0 of 10</b> rows carry sub-second precision, <b>0</b> equal
 * {@code created_at}, and 8 distinct round business times are stored — the signature of a value
 * somebody <em>stated</em>. The seed also derived every {@code sla_due_at} from it at exactly
 * +30 days, which is the register's own statement that this is the anchor.
 *
 * <p>⚠ These legs cannot see the {@code insertable} mapping at all — a mocked repository has no
 * schema, so {@code save(a)} returns whatever the service set whatever the column would accept
 * (F2-286's own lesson). They pin the service's contract; the mapping is pinned structurally by
 * {@code UpdateRefusedColumnWireContractTest} and was proved by the live probe.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AmlDetectionTimeTest {

    private static final Long ORG = 1L;
    private static final Long ALERT = 900L;

    @Mock AmlAlertRepository repo;
    @Mock UserRepository users;
    @Mock KycSubjectRepository subjectRepo;

    AmlAlertService service;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG);
        // Real directories over mocked repositories, never mocks of the directories: a mocked
        // resolver returns empty and every refusal leg would pass asserting nothing (F2-237).
        service = new AmlAlertService(repo, new TenantUserDirectory(users),
                new KycSubjectDirectory(subjectRepo));
        when(repo.save(any(AmlAlert.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static AmlAlert req() {
        AmlAlert a = new AmlAlert();
        a.setAlertType("STRUCTURING");
        a.setTitleEn("[REVIEW-TEST] rapid cash deposits below threshold");
        a.setDescription("Nine deposits of SAR 45,000 over four days.");
        return a;
    }

    /**
     * The default is not tidiness: the column is NOT NULL and now insertable, so an omitted
     * value would reach the INSERT as null. Supplying the clock is what stops the widening
     * breaking the create outright.
     */
    @Test
    void anOmittedDetectionTimeIsStampedFromTheServersClock() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(2);

        AmlAlert saved = service.create(req());

        assertThat(saved.getFirstDetectedAt())
                .as("NOT NULL, so something must fill it")
                .isNotNull()
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(LocalDateTime.now().plusSeconds(2));
    }

    /** The whole point: the TM engine's own detection time survives the create. */
    @Test
    void aStatedDetectionTimeIsKeptExactly() {
        LocalDateTime detected = LocalDateTime.of(2026, 4, 21, 3, 30, 0);
        AmlAlert a = req();
        a.setFirstDetectedAt(detected);

        assertThat(service.create(a).getFirstDetectedAt())
                .as("before F2-287 the database discarded this and the response echoed it")
                .isEqualTo(detected);
    }

    /**
     * Activity that has not happened cannot have been detected, and refusing that is what makes
     * the widening safe — it is the one value that could only ever be a forgery.
     */
    @Test
    void aFutureDetectionTimeIsRefusedAndTheMessageNamesTheField() {
        AmlAlert a = req();
        a.setFirstDetectedAt(LocalDateTime.now().plusDays(30));

        assertThatThrownBy(() -> service.create(a))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("firstDetectedAt")
                .hasMessageContaining("future");
    }

    @Test
    void aDetectionTimeJustInsideTheToleranceIsAccepted() {
        LocalDateTime nearlyNow = LocalDateTime.now().plusMinutes(1);
        AmlAlert a = req();
        a.setFirstDetectedAt(nearlyNow);

        assertThat(service.create(a).getFirstDetectedAt()).isEqualTo(nearlyNow);
    }

    // ─── the update half: the mapping refuses a change, so the wire must too ────────

    private void stored(LocalDateTime detected) {
        AmlAlert live = new AmlAlert();
        live.setId(ALERT);
        live.setOrganisationId(ORG);
        live.setStatus("OPEN");
        live.setFirstDetectedAt(detected);
        when(repo.findById(ALERT)).thenReturn(Optional.of(live));
    }

    /** A form that round-trips the whole row must keep working (F2-231/F2-234). */
    @Test
    void anEchoOfTheStoredDetectionTimeIsAcceptedOnUpdate() {
        LocalDateTime detected = LocalDateTime.of(2026, 4, 21, 3, 30);
        stored(detected);
        AmlAlert patch = req();
        patch.setFirstDetectedAt(detected);

        assertThat(service.update(ALERT, patch).getFirstDetectedAt()).isEqualTo(detected);
    }

    /**
     * 🔑 Not "it is ignored" — it is <b>refused</b>. Hibernate omits an
     * {@code updatable = false} column from the UPDATE, so a different value here would look
     * accepted and change nothing, which is the shape this whole slice is about.
     */
    @Test
    void aDifferentDetectionTimeIsRefusedOnUpdateRatherThanSilentlyDropped() {
        stored(LocalDateTime.of(2026, 4, 21, 3, 30));
        AmlAlert patch = req();
        patch.setFirstDetectedAt(LocalDateTime.of(2020, 1, 1, 0, 0));

        assertThatThrownBy(() -> service.update(ALERT, patch))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("firstDetectedAt")
                .hasMessageContaining("cannot be updated");
    }

    @Test
    void anOmittedDetectionTimeOnUpdateLeavesTheStoredValueAlone() {
        LocalDateTime detected = LocalDateTime.of(2026, 4, 21, 3, 30);
        stored(detected);

        assertThat(service.update(ALERT, req()).getFirstDetectedAt()).isEqualTo(detected);
    }
}
