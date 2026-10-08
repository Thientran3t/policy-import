package com.interview.policyimport.model;

import java.util.Map;

public record PartnerDefinition(
        String code,
        String format,
        String delimiter,
        boolean hasHeader,
        Map<String, String> columns
) {
}