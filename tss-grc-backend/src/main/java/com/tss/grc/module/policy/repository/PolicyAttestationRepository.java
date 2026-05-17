package com.tss.grc.module.policy.repository;

import com.tss.grc.module.policy.entity.PolicyAttestation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PolicyAttestationRepository extends JpaRepository<PolicyAttestation, Long> {

    List<PolicyAttestation> findByOrganisationIdOrderByCreatedAtDesc(Long org);

    List<PolicyAttestation> findByOrganisationIdAndPolicyIdOrderByUserUsernameAsc(Long org, Long policyId);

    List<PolicyAttestation> findByOrganisationIdAndUserUsernameOrderByCreatedAtDesc(Long org, String username);

    List<PolicyAttestation> findByOrganisationIdAndStatusOrderByCreatedAtDesc(Long org, String status);

    Optional<PolicyAttestation> findByIdAndOrganisationId(Long id, Long org);

    Optional<PolicyAttestation> findByOrganisationIdAndPolicyIdAndUserUsername(Long org, Long policyId, String username);
}
