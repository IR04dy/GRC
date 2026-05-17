package com.tss.grc.module.tprm.repository;

import com.tss.grc.module.tprm.entity.QuestionnaireResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuestionnaireResponseRepository extends JpaRepository<QuestionnaireResponse, Long> {

    List<QuestionnaireResponse> findByOrganisationIdAndVendorAssessmentIdOrderByQuestionId(Long org, Long assessmentId);

    Optional<QuestionnaireResponse> findByOrganisationIdAndVendorAssessmentIdAndQuestionId(
        Long org, Long assessmentId, Long questionId);
}
