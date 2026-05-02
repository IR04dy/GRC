package com.tss.grc.module.privacy.repository;

import com.tss.grc.module.privacy.entity.DpiaReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DpiaReviewRepository extends JpaRepository<DpiaReview, Long> {
    List<DpiaReview> findByDpiaIdOrderByDecidedAtDesc(Long dpiaId);
}
