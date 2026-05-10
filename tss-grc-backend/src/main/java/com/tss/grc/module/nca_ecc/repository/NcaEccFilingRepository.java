package com.tss.grc.module.nca_ecc.repository;

import com.tss.grc.module.nca_ecc.entity.NcaEccFiling;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface NcaEccFilingRepository extends JpaRepository<NcaEccFiling, Long> {
    List<NcaEccFiling> findByOrganisationIdOrderByFiscalYearDesc(Long organisationId);
    Optional<NcaEccFiling> findByOrganisationIdAndExternalId(Long organisationId, String externalId);
    Optional<NcaEccFiling> findByOrganisationIdAndFiscalYear(Long organisationId, Integer fiscalYear);
}
