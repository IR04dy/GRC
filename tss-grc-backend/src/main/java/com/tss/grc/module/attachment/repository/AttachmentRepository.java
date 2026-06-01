package com.tss.grc.module.attachment.repository;

import com.tss.grc.module.attachment.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    List<Attachment> findByOrganisationIdAndEntityTypeAndEntityIdOrderByUploadedAtDesc(
        Long organisationId, String entityType, Long entityId);
}
