package com.tss.grc.module.signature.repository;

import com.tss.grc.module.signature.entity.Signature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SignatureRepository extends JpaRepository<Signature, Long> {

    List<Signature> findByOrganisationIdAndEntityTypeAndEntityIdOrderBySignedAtDesc(
        Long organisationId, String entityType, Long entityId);
}
