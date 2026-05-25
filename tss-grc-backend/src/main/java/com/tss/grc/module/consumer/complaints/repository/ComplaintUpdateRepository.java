package com.tss.grc.module.consumer.complaints.repository;

import com.tss.grc.module.consumer.complaints.entity.ComplaintUpdate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComplaintUpdateRepository extends JpaRepository<ComplaintUpdate, Long> {

    List<ComplaintUpdate> findByComplaintIdOrderByOccurredOnAsc(Long complaintId);

    List<ComplaintUpdate> findByComplaintIdAndCustomerVisibleTrueOrderByOccurredOnAsc(Long complaintId);
}
