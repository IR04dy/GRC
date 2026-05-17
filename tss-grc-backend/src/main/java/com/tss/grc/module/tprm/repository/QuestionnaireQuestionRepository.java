package com.tss.grc.module.tprm.repository;

import com.tss.grc.module.tprm.entity.QuestionnaireQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuestionnaireQuestionRepository extends JpaRepository<QuestionnaireQuestion, Long> {

    List<QuestionnaireQuestion> findByOrganisationIdAndTemplateIdOrderBySequenceOrderAsc(Long org, Long templateId);

    Optional<QuestionnaireQuestion> findByIdAndOrganisationId(Long id, Long org);
}
