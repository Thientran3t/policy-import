package com.interview.policyimport.entity;

import com.interview.policyimport.model.PolicyStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "policy",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_policy_number",
                        columnNames = "policy_number"
                ),
                @UniqueConstraint(
                        name = "uk_policy_partner_imei",
                        columnNames = {"partner_code", "imei"}
                )
        }
)
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_number", nullable = false, length = 100)
    private String policyNumber;

    @Column(name = "partner_code", nullable = false, length = 50)
    private String partnerCode;

    @Column(nullable = false, length = 50)
    private String imei;

    @Column(name = "plan_code", nullable = false, length = 100)
    private String planCode;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal premium;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PolicyStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Policy() {
    }

    public Policy(
            String policyNumber,
            String partnerCode,
            String imei,
            String planCode,
            LocalDate effectiveDate,
            LocalDate expiryDate,
            BigDecimal premium,
            String currency
    ) {
        this.policyNumber = policyNumber;
        this.partnerCode = partnerCode;
        this.imei = imei;
        this.planCode = planCode;
        this.effectiveDate = effectiveDate;
        this.expiryDate = expiryDate;
        this.premium = premium;
        this.currency = currency;
        this.status = PolicyStatus.ACTIVE;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getPolicyNumber() {
        return policyNumber;
    }

    public String getPartnerCode() {
        return partnerCode;
    }

    public String getImei() {
        return imei;
    }

    public String getPlanCode() {
        return planCode;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public BigDecimal getPremium() {
        return premium;
    }

    public String getCurrency() {
        return currency;
    }

    public PolicyStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}