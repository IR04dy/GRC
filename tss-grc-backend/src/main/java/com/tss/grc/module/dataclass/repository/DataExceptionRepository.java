package com.tss.grc.module.dataclass.repository;

import com.tss.grc.module.dataclass.entity.DataException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DataExceptionRepository extends JpaRepository<DataException, Long> {
    List<DataException> findByOrganisationIdOrderByCreatedAtDesc(Long org);
    Optional<DataException> findByOrganisationIdAndId(Long org, Long id);
    Optional<DataException> findByOrganisationIdAndReference(Long org, String reference);
    long countByOrganisationIdAndStatus(Long org, String status);
}
