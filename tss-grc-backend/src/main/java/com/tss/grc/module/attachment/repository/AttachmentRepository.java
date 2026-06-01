package com.tss.grc.module.attachment.repository;

import com.tss.grc.module.attachment.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    /** Current (non-superseded) attachments for an entity. */
    List<Attachment> findByOrganisationIdAndEntityTypeAndEntityIdAndSupersededFalseOrderByUploadedAtDesc(
        Long organisationId, String entityType, Long entityId);

    /** Every version in a document's version chain, newest first. */
    List<Attachment> findByOrganisationIdAndVersionGroupIdOrderByVersionDesc(
        Long organisationId, Long versionGroupId);
}
