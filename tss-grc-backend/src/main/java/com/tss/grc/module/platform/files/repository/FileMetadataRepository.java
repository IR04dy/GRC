package com.tss.grc.module.platform.files.repository;

import com.tss.grc.module.platform.files.entity.FileMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long> {

    List<FileMetadata> findByOrganisationIdAndAttachedToOrderByUploadedAtDesc(Long organisationId, String attachedTo);

    Optional<FileMetadata> findByOrganisationIdAndSha256(Long organisationId, String sha256);

    long countByOrganisationIdAndScanStatus(Long organisationId, String scanStatus);
}
