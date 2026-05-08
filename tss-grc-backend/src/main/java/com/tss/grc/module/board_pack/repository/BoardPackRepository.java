package com.tss.grc.module.board_pack.repository;

import com.tss.grc.module.board_pack.entity.BoardPack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository("boardPackBoardPackRepository")
public interface BoardPackRepository extends JpaRepository<BoardPack, Long> {
    List<BoardPack> findByOrganisationIdOrderByPeriodStartDesc(Long organisationId);
    Optional<BoardPack> findByOrganisationIdAndExternalId(Long organisationId, String externalId);
    List<BoardPack> findByOrganisationIdAndStatus(Long organisationId, String status);
}
