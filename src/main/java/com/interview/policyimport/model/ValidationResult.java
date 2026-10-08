package com.interview.policyimport.model;

import java.util.List;

public record ValidationResult(
        boolean isValid,
        List<ValidationError> errors
) {

    public static ValidationResult valid() {
        return new ValidationResult(true, List.of());
    }

    public static ValidationResult invalid(List<ValidationError> errors) {
        return new ValidationResult(false, errors);
    }
}