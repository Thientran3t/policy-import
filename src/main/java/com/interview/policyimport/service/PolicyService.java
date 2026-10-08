package com.interview.policyimport.service;

import com.interview.policyimport.entity.Policy;
import com.interview.policyimport.model.CanonicalEnrollment;
import com.interview.policyimport.repository.PolicyRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PolicyService {

    private final PolicyRepository policyRepository;

    public PolicyService(PolicyRepository policyRepository) {
        this.policyRepository = policyRepository;
    }

    public boolean createPolicy(
            String partnerCode,
            CanonicalEnrollment enrollment
    ) {

        if (policyRepository.existsByPartnerCodeAndImei(
                partnerCode,
                enrollment.imei())) {

            return false;
        }

        Policy policy = new Policy(
                "POL-" + UUID.randomUUID(),
                partnerCode,
                enrollment.imei(),
                enrollment.planCode(),
                enrollment.effectiveDate(),
                enrollment.expiryDate(),
                enrollment.premium(),
                enrollment.currency()
        );

        policyRepository.save(policy);

        return true;
    }
}