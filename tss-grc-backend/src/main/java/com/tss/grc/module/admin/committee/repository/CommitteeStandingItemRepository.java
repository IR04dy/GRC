package com.tss.grc.module.admin.committee.repository;

import com.tss.grc.module.admin.committee.entity.CommitteeStandingItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Standing agenda items pre-bound to a committee template. */
public interface CommitteeStandingItemRepository extends JpaRepository<CommitteeStandingItem, Long> {

    /** Items for one template, ordered by sequence — drives the
     *  annual-calendar generator's agenda pre-population. */
    List<CommitteeStandingItem> findByTemplateCodeOrderBySeqAsc(String templateCode);

    long countByTemplateCode(String templateCode);
}
