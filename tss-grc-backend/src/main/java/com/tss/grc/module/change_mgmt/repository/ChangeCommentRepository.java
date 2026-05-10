package com.tss.grc.module.change_mgmt.repository;

import com.tss.grc.module.change_mgmt.entity.ChangeComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChangeCommentRepository extends JpaRepository<ChangeComment, Long> {
    List<ChangeComment> findByChangeRequestIdOrderByCreatedAtAsc(Long changeRequestId);
}
