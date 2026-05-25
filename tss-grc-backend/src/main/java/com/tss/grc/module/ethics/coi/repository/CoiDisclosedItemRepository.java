package com.tss.grc.module.ethics.coi.repository;

import com.tss.grc.module.ethics.coi.entity.CoiDisclosedItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CoiDisclosedItemRepository extends JpaRepository<CoiDisclosedItem, Long> {

    List<CoiDisclosedItem> findByDeclarationIdAndIsDeletedFalse(Long declarationId);

    List<CoiDisclosedItem> findByDeclarationIdAndIsActiveAndIsDeletedFalse(Long declarationId, Boolean isActive);
}
