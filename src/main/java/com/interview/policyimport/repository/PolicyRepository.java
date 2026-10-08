package com.interview.policyimport.repository;

import com.interview.policyimport.entity.Policy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    @Modifying
    @Query(value = """
            INSERT INTO policy (
                policy_number,
                partner_code,
                imei,
                plan_code,
                effective_date,
                expiry_date,
                premium,
                currency,
                status,
                created_at
            )
            VALUES (
                :policyNumber,
                :partnerCode,
                :imei,
                :planCode,
                :effectiveDate,
                :expiryDate,
                :premium,
                :currency,
                'ACTIVE',
                CURRENT_TIMESTAMP
            )
            ON CONFLICT (partner_code, imei)
            DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("policyNumber") String policyNumber,
            @Param("partnerCode") String partnerCode,
            @Param("imei") String imei,
            @Param("planCode") String planCode,
            @Param("effectiveDate") LocalDate effectiveDate,
            @Param("expiryDate") LocalDate expiryDate,
            @Param("premium") BigDecimal premium,
            @Param("currency") String currency
    );
}