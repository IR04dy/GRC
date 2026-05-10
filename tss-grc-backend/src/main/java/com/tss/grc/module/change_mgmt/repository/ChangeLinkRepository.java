package com.tss.grc.module.change_mgmt.repository;

import com.tss.grc.module.change_mgmt.entity.ChangeLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChangeLinkRepository extends JpaRepository<ChangeLink, Long> {
    List<ChangeLink> findByChangeRequestId(Long changeRequestId);
    List<ChangeLink> findByEntityTypeAndEntityId(String entityType, Long entityId);
}
