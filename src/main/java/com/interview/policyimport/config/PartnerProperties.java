package com.interview.policyimport.config;

import com.interview.policyimport.model.PartnerDefinition;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "policy-import")
public record PartnerProperties(
        Map<String, PartnerDefinition> partners
) {
}