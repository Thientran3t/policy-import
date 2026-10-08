package com.interview.policyimport.model;

public record ValidationError(
        String code,
        String message
) {
}