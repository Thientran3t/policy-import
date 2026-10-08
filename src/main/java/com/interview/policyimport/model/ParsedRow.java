package com.interview.policyimport.model;

public record ParsedRow(
        int rowNumber,
        CanonicalEnrollment enrollment,
        String errorCode,
        String errorMessage
) {

    public boolean isValid() {
        return enrollment != null && errorCode == null;
    }

    public boolean hasError() {
        return errorCode != null;
    }

}