package com.interview.policyimport.repository;

import com.interview.policyimport.entity.Policy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PolicyRepository extends JpaRepository<Policy, Long> {

    Optional<Policy> findByPartnerCodeAndImei(
            String partnerCode,
            String imei
    );

    boolean existsByPartnerCodeAndImei(
            String partnerCode,
            String imei
    );
}