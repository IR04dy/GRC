package com.tss.grc.module.privacy.repository;

import com.tss.grc.module.privacy.entity.DpiaQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DpiaQuestionRepository extends JpaRepository<DpiaQuestion, Long> {
    List<DpiaQuestion> findByDpiaIdOrderBySortOrder(Long dpiaId);
}
