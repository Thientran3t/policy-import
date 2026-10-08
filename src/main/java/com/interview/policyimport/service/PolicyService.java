package com.interview.policyimport.service;

import com.interview.policyimport.model.CanonicalEnrollment;
import com.interview.policyimport.repository.PolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PolicyService {

    private final PolicyRepository policyRepository;

    public PolicyService(PolicyRepository policyRepository) {
        this.policyRepository = policyRepository;
    }

    @Transactional
    public boolean createPolicy(
            String partnerCode,
            CanonicalEnrollment enrollment
    ) {
        String policyNumber =
                "POL-" + UUID.randomUUID();

        int inserted = policyRepository.insertIfAbsent(
                policyNumber,
                partnerCode,
                enrollment.imei(),
                enrollment.planCode(),
                enrollment.effectiveDate(),
                enrollment.expiryDate(),
                enrollment.premium(),
                enrollment.currency()
        );

        return inserted == 1;
    }
}