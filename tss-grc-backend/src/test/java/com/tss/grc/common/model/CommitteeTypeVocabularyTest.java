package com.tss.grc.common.model;

import com.tss.grc.module.admin.committee.dto.CommitteeDtos;
import com.tss.grc.module.board_pack.dto.BoardPackDto;
import jakarta.validation.constraints.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W1-5 — every Java statement of the committee-type vocabulary must be the same statement.
 *
 * <h3>Why this is a ratchet and not a one-off fix</h3>
 * The vocabulary was stated in three places and disagreed in two of them: the database CHECK held 22
 * values, {@code BoardPackDto.audience}'s {@code @Pattern} held 17, and
 * {@code CommitteeCreateRequest.committeeType} held none. Each copy looked correct on its own — which
 * is why it survived a slice (V331) that was specifically about this drift and still landed five short.
 *
 * <p>D-11's guard checks stored rows against the vocabulary. This checks the vocabulary's definitions
 * against each other. Both directions are needed, and only one of them existed.
 *
 * <p>The remaining direction — Java against {@code CK_com_type} — needs a database, so it lives in
 * {@code CommitteeGoldenPathIT}. That split is the point of having an integration test at all.
 */
@DisplayName("W1-5 — committee-type vocabulary is stated once")
class CommitteeTypeVocabularyTest {

    @Test
    @DisplayName("the regexp and the list are the same vocabulary")
    void theRegexpAndTheListAgree() {
        // @Pattern needs a compile-time constant, so REGEX cannot be built from VOCABULARY at
        // runtime. That is exactly how two copies drift, so assert they still match.
        List<String> fromRegex = Arrays.asList(CommitteeTypes.REGEX.split("\\|"));
        assertThat(fromRegex)
            .as("CommitteeTypes.REGEX must list exactly CommitteeTypes.VOCABULARY, in order")
            .containsExactlyElementsOf(CommitteeTypes.VOCABULARY);
    }

    @Test
    @DisplayName("the vocabulary has no duplicates and no blanks")
    void theVocabularyIsWellFormed() {
        assertThat(CommitteeTypes.VOCABULARY).doesNotHaveDuplicates().isNotEmpty();
        assertThat(CommitteeTypes.VOCABULARY).allSatisfy(v ->
            assertThat(v).isNotBlank().matches("[A-Z][A-Z_]*"));
        assertThat(CommitteeTypes.VALUES).hasSameSizeAs(CommitteeTypes.VOCABULARY);
    }

    @Test
    @DisplayName("board-pack audience validates against the shared vocabulary, not a private copy")
    void boardPackAudienceUsesTheSharedVocabulary() throws Exception {
        Field audience = BoardPackDto.class.getDeclaredField("audience");
        Pattern pattern = audience.getAnnotation(Pattern.class);
        assertThat(pattern)
            .as("BoardPackDto.audience must still be vocabulary-validated — before V331 it accepted "
                + "four values against five stored ones and silently mis-scoped packs")
            .isNotNull();
        assertThat(pattern.regexp())
            .as("a private copy of the vocabulary is how this drifted to 17 of 22")
            .isEqualTo(CommitteeTypes.REGEX);
    }

    @Test
    @DisplayName("committee creation validates its type at the boundary, not at the database")
    void committeeCreationValidatesAtTheBoundary() throws Exception {
        // Read the annotation off the record's accessor, not off its RecordComponent.
        // jakarta.validation's @Pattern has no RECORD_COMPONENT target, so
        // RecordComponent.getAnnotation() answers null even when the annotation is plainly there —
        // and a reflective check that cannot see what it is looking for reports a clean absence,
        // which is the more dangerous of the two ways to be wrong.
        assertThat(Arrays.stream(CommitteeDtos.CommitteeCreateRequest.class.getRecordComponents())
                .map(RecordComponent::getName))
            .as("the component must still exist for the accessor lookup below to mean anything")
            .contains("committeeType");

        Pattern pattern = CommitteeDtos.CommitteeCreateRequest.class
            .getDeclaredMethod("committeeType").getAnnotation(Pattern.class);
        assertThat(pattern)
            .as("without this, CK_com_type is the only enforcement and an off-vocabulary type is a "
                + "500 'An unexpected database error occurred.' — W1-5's first finding")
            .isNotNull();
        assertThat(pattern.regexp()).isEqualTo(CommitteeTypes.REGEX);
    }

    @Test
    @DisplayName("CAB and ECAB are not committee types — ADR-017 D-2 makes them roles")
    void cabIsARoleNotACommitteeType() {
        // The golden-path test's first draft tried to constitute a committee of type CAB, and the
        // database refused it. Widening the vocabulary would have been the obvious "fix" and would
        // have quietly reversed the decision that unblocked this entire epic: a tenant NOMINATES an
        // existing committee as its CAB through admin.committee_gate_map. Pinning it here means the
        // next person to reach for that fix reads the reason first.
        assertThat(CommitteeTypes.VALUES)
            .as("ADR-017 D-2 — 'the CAB is a role, not necessarily a standing committee'")
            .doesNotContain("CAB", "ECAB");
        assertThat(CommitteeTypes.isValid("CAB")).isFalse();
        assertThat(CommitteeTypes.isValid("IT_STEERING")).isTrue();
    }

    @Test
    @DisplayName("the five values V331 missed are in the vocabulary")
    void theFiveValuesV331MissedArePresent() {
        // Named individually rather than counted, so the assertion says which — a count would pass
        // again the moment someone swapped one value for another.
        assertThat(CommitteeTypes.VALUES)
            .as("present in CK_com_type since V154, absent from the board-pack pattern until W1-5")
            .contains("OTHER", "ARCH_REVIEW", "INVESTMENT", "ALCO", "AML");
    }
}
