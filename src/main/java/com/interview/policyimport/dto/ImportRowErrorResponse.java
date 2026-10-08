package com.interview.policyimport.dto;

import com.interview.policyimport.entity.ImportRow;

public record ImportRowErrorResponse(
        int rowNumber,
        String imei,
        String errorCode,
        String errorMessage
) {

    public static ImportRowErrorResponse from(
            ImportRow row
    ) {
        return new ImportRowErrorResponse(
                row.getRowNumber(),
                row.getImei(),
                row.getErrorCode(),
                row.getErrorMessage()
        );
    }
}