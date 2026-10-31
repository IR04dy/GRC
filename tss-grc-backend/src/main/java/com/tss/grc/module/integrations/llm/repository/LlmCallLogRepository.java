package com.tss.grc.module.integrations.llm.repository;

import com.tss.grc.module.integrations.llm.entity.LlmCallLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface LlmCallLogRepository extends JpaRepository<LlmCallLog, Long> {

    @Query("SELECT COALESCE(SUM(CAST(c.inputTokens AS long) + CAST(c.outputTokens AS long)), 0L) "
         + "FROM LlmCallLog c "
         + "WHERE c.organisationId = :organisationId "
         + "AND c.createdAt >= :since "
         + "AND c.status = 'SUCCEEDED'")
    long sumTokensSince(
        @Param("organisationId") Long organisationId,
        @Param("since") LocalDateTime since
    );
}
