package com.tss.grc.module.platform.persistence.repository;

import com.tss.grc.module.platform.persistence.entity.PersistenceRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface PersistenceRowRepository extends JpaRepository<PersistenceRow, Long> {

    /** All rows for a (tenant, store), ordered by sequence. */
    List<PersistenceRow> findByOrganisationIdAndStoreKeyOrderByRowSeqAsc(Long organisationId, String storeKey);

    /** Highest existing row_seq for an append. */
    @Query("select coalesce(max(p.rowSeq), 0) from PersistenceRow p " +
           "where p.organisationId = :orgId and p.storeKey = :key")
    Long maxRowSeq(@Param("orgId") Long orgId, @Param("key") String key);

    /** Used by setAll — soft-delete the existing rows, then insert the new set. */
    @Modifying
    @Transactional
    @Query("update PersistenceRow p set p.isDeleted = true, p.deletedAt = CURRENT_TIMESTAMP " +
           "where p.organisationId = :orgId and p.storeKey = :key")
    int softDeleteByOrgAndKey(@Param("orgId") Long orgId, @Param("key") String key);

    /** Count for the production-readiness inventory. */
    long countByOrganisationIdAndStoreKey(Long organisationId, String storeKey);
}
