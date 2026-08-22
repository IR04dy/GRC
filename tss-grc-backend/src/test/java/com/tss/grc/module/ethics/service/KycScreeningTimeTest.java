package com.tss.grc.module.ethics.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ethics.entity.KycSubject;
import com.tss.grc.module.ethics.entity.ScreeningHit;
import com.tss.grc.module.ethics.repository.KycSubjectRepository;
import com.tss.grc.module.ethics.repository.ScreeningHitRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * F2-286 — when a sanctions screening ran is a fact somebody can now state.
 *
 * <p>{@code ethics.screening_hits.screened_at} was {@code insertable = false}, so no API
 * caller and no Hibernate write could ever set it: the column could only hold
 * {@code sysutcdatetime()} at the instant its row was inserted. Measured on the live
 * register, all 12 rows in both databases are microsecond-precision instants in three
 * seed-insert batches on one day, while {@code decided_at} beside them holds round business
 * times — so two hits read as decided <b>370 days before they were screened</b>.
 *
 * <p>Meanwhile {@code recordHit} accepted a {@code screenedAt} from the body, the database
 * discarded it, and the response echoed it back, because the method returns
 * {@code hitRepo.save(body)} — the caller's own instance ([[F2-285]]).
 *
 * <p>⚠ {@code KycService} had <b>no test of any kind</b> before this. That is not the same as
 * a path somebody decided was safe ([[F2-248]]).
 */
class KycScreeningTimeTest {

    private KycSubjectRepository subjects;
    private ScreeningHitRepository hits;
    private KycService service;

    @BeforeEach
    void setUp() {
        subjects = mock(KycSubjectRepository.class);
        hits = mock(ScreeningHitRepository.class);
        // A REAL directory over the mocked repository, never a mock of it: a mocked resolver
        // returns empty and every refusal below would pass while asserting nothing (F2-237).
        service = new KycService(subjects, hits, new KycSubjectDirectory(subjects));
        TenantContext.setCurrentTenant(1L);

        KycSubject s = new KycSubject();
        s.setId(7L);
        s.setOrganisationId(1L);
        s.setRiskRating("STANDARD");
        // KycSubjectDirectory#resolve loads by id and filters on the tenant in Java, so this
        // is the finder it reaches -- resolved by reading it, not from the method's name.
        when(subjects.findById(7L)).thenReturn(Optional.of(s));
        when(hits.save(any(ScreeningHit.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static ScreeningHit hit() {
        ScreeningHit h = new ScreeningHit();
        h.setListSource("OFAC_SDN");
        h.setMatchScore(91);
        h.setDisposition("NEEDS_REVIEW");
        return h;
    }

    /**
     * The default is not tidiness. The column is NOT NULL and now insertable, so an omitted
     * value would reach the INSERT as null — this is what stops the widening breaking the
     * create outright.
     */
    @Test
    void anOmittedScreeningTimeIsStampedFromTheServersClock() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(2);

        ScreeningHit saved = service.recordHit(7L, hit());

        assertThat(saved.getScreenedAt())
                .as("the column is NOT NULL, so something must fill it")
                .isNotNull()
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(LocalDateTime.now().plusSeconds(2));
    }

    /** The whole point: a screening run can state when it ran. */
    @Test
    void aStatedScreeningTimeIsKeptExactly() {
        LocalDateTime ran = LocalDateTime.of(2026, 3, 14, 9, 15, 0);
        ScreeningHit h = hit();
        h.setScreenedAt(ran);

        ScreeningHit saved = service.recordHit(7L, h);

        assertThat(saved.getScreenedAt())
                .as("before F2-286 the database discarded this and the response echoed it")
                .isEqualTo(ran);
    }

    /**
     * A screening cannot have happened in the future, and refusing that is what makes the
     * widening safe — it is the one value that could only ever be a forgery. Five minutes of
     * tolerance so a clock-skewed feed is not refused for being a second early.
     */
    @Test
    void aFutureScreeningTimeIsRefusedAndTheMessageNamesTheField() {
        ScreeningHit h = hit();
        h.setScreenedAt(LocalDateTime.now().plusDays(30));

        assertThatThrownBy(() -> service.recordHit(7L, h))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("screenedAt")
                .hasMessageContaining("future");
    }

    @Test
    void aScreeningTimeJustInsideTheToleranceIsAccepted() {
        LocalDateTime nearlyNow = LocalDateTime.now().plusMinutes(1);
        ScreeningHit h = hit();
        h.setScreenedAt(nearlyNow);

        assertThat(service.recordHit(7L, h).getScreenedAt()).isEqualTo(nearlyNow);
    }

    /**
     * F2-22b(vii)'s scrub must survive this change. A newly reported match has not been
     * adjudicated, and {@code sarFiled} asserts that a Suspicious Activity Report was filed
     * with the regulator — on an AML register that is the highest-value forgery there is.
     */
    @Test
    void aStatedScreeningTimeDoesNotSmuggleInADecision() {
        ScreeningHit h = hit();
        h.setScreenedAt(LocalDateTime.of(2026, 3, 14, 9, 15));
        h.setDecidedAt(LocalDateTime.of(2026, 3, 14, 10, 0));
        h.setDecidedByName("Nobody Who Decided This");
        h.setDispositionNotes("cleared");
        h.setSarFiled(true);
        h.setSarReference("STR-FORGED-1");

        ScreeningHit saved = service.recordHit(7L, h);

        assertThat(saved.getDecidedAt()).isNull();
        assertThat(saved.getDecidedByName()).isNull();
        assertThat(saved.getDispositionNotes()).isNull();
        assertThat(saved.getSarFiled()).isFalse();
        assertThat(saved.getSarReference()).isNull();
        assertThat(saved.getScreenedAt()).isEqualTo(LocalDateTime.of(2026, 3, 14, 9, 15));
    }
}
