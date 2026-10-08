package com.interview.policyimport.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CanonicalEnrollment(
        String imei,
        String planCode,
        LocalDate effectiveDate,
        LocalDate expiryDate,
        BigDecimal premium,
        String currency
) {
}