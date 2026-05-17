package com.tss.grc.module.tprm.repository;

import com.tss.grc.module.tprm.entity.QuestionnaireTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuestionnaireTemplateRepository extends JpaRepository<QuestionnaireTemplate, Long> {

    List<QuestionnaireTemplate> findByOrganisationIdOrderByTemplateCodeAscVersionDesc(Long org);

    List<QuestionnaireTemplate> findByOrganisationIdAndStatusOrderByTemplateCodeAscVersionDesc(Long org, String status);

    Optional<QuestionnaireTemplate> findByIdAndOrganisationId(Long id, Long org);

    Optional<QuestionnaireTemplate> findByOrganisationIdAndTemplateCodeAndVersion(Long org, String code, String version);
}
