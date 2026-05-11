package com.tss.grc.module.privacy.extensions.repository;

import com.tss.grc.module.privacy.extensions.entity.CrossBorderTransfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CrossBorderTransferRepository extends JpaRepository<CrossBorderTransfer, Long> {

    Page<CrossBorderTransfer> findByOrganisationIdOrderByCreatedAtDesc(Long orgId, Pageable pageable);

    Optional<CrossBorderTransfer> findByIdAndOrganisationId(Long id, Long orgId);

    List<CrossBorderTransfer> findByOrganisationIdAndStatusOrderByCreatedAtDesc(Long orgId, String status);

    List<CrossBorderTransfer> findByOrganisationIdAndRopaCodeOrderByCreatedAtDesc(Long orgId, String ropaCode);
}
