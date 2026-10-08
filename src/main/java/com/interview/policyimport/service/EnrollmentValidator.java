package com.interview.policyimport.service;

import com.interview.policyimport.model.CanonicalEnrollment;
import com.interview.policyimport.model.ValidationError;
import com.interview.policyimport.model.ValidationResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class EnrollmentValidator {

    private static final Pattern IMEI_PATTERN =
            Pattern.compile("\\d{15}");

    private static final Pattern CURRENCY_PATTERN =
            Pattern.compile("[A-Z]{3}");

    public ValidationResult validate(CanonicalEnrollment enrollment) {

        List<ValidationError> errors = new ArrayList<>();

        validateImei(enrollment, errors);
        validatePlan(enrollment, errors);
        validateDates(enrollment, errors);
        validatePremium(enrollment, errors);
        validateCurrency(enrollment, errors);

        if (errors.isEmpty()) {
            return ValidationResult.valid();
        }

        return ValidationResult.invalid(errors);
    }

    private void validateImei(
            CanonicalEnrollment enrollment,
            List<ValidationError> errors
    ) {
        if (enrollment.imei() == null ||
                enrollment.imei().isBlank()) {

            errors.add(new ValidationError(
                    "IMEI_REQUIRED",
                    "IMEI is required"
            ));
            return;
        }

        if (!IMEI_PATTERN.matcher(enrollment.imei()).matches()) {
            errors.add(new ValidationError(
                    "IMEI_INVALID",
                    "IMEI must contain exactly 15 digits"
            ));
        }
    }

    private void validatePlan(
            CanonicalEnrollment enrollment,
            List<ValidationError> errors
    ) {
        if (enrollment.planCode() == null ||
                enrollment.planCode().isBlank()) {

            errors.add(new ValidationError(
                    "PLAN_REQUIRED",
                    "Plan code is required"
            ));
        }
    }

    private void validateDates(
            CanonicalEnrollment enrollment,
            List<ValidationError> errors
    ) {
        if (enrollment.effectiveDate() == null) {
            errors.add(new ValidationError(
                    "EFFECTIVE_DATE_REQUIRED",
                    "Effective date is required"
            ));
        }

        if (enrollment.expiryDate() == null) {
            errors.add(new ValidationError(
                    "EXPIRY_DATE_REQUIRED",
                    "Expiry date is required"
            ));
        }

        if (enrollment.effectiveDate() != null &&
                enrollment.expiryDate() != null &&
                enrollment.expiryDate()
                        .isBefore(enrollment.effectiveDate())) {

            errors.add(new ValidationError(
                    "INVALID_DATE_RANGE",
                    "Expiry date must not be before effective date"
            ));
        }
    }

    private void validatePremium(
            CanonicalEnrollment enrollment,
            List<ValidationError> errors
    ) {
        if (enrollment.premium() == null) {
            errors.add(new ValidationError(
                    "PREMIUM_REQUIRED",
                    "Premium is required"
            ));
            return;
        }

        if (enrollment.premium().signum() < 0) {
            errors.add(new ValidationError(
                    "PREMIUM_INVALID",
                    "Premium must not be negative"
            ));
        }
    }

    private void validateCurrency(
            CanonicalEnrollment enrollment,
            List<ValidationError> errors
    ) {
        if (enrollment.currency() == null ||
                enrollment.currency().isBlank()) {

            errors.add(new ValidationError(
                    "CURRENCY_REQUIRED",
                    "Currency is required"
            ));
            return;
        }

        if (!CURRENCY_PATTERN.matcher(enrollment.currency()).matches()) {
            errors.add(new ValidationError(
                    "CURRENCY_INVALID",
                    "Currency must be a 3-letter uppercase code"
            ));
        }
    }
}