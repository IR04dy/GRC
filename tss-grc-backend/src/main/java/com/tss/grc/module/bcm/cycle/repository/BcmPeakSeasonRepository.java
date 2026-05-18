package com.tss.grc.module.bcm.cycle.repository;

import com.tss.grc.module.bcm.cycle.entity.BcmPeakSeason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BcmPeakSeasonRepository extends JpaRepository<BcmPeakSeason, Long> {
    List<BcmPeakSeason> findAllByOrderByGregorianStartDateAsc();
    List<BcmPeakSeason> findBySeasonTypeOrderByGregorianStartDateAsc(String seasonType);
    Optional<BcmPeakSeason> findBySeasonCode(String code);

    /**
     * Active windows: any season whose date range contains today, OR starts
     * within the next {@code daysAhead} days. Drives the "upcoming peaks"
     * widget on the BCM dashboard.
     */
    @Query("select s from BcmPeakSeason s where "
         + "(:today between s.gregorianStartDate and s.gregorianEndDate) "
         + "or (s.gregorianStartDate > :today and s.gregorianStartDate <= :horizon) "
         + "order by s.gregorianStartDate asc")
    List<BcmPeakSeason> findUpcoming(@Param("today") LocalDate today,
                                     @Param("horizon") LocalDate horizon);
}
