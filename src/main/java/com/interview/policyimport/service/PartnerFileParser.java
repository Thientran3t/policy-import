package com.interview.policyimport.service;

import com.interview.policyimport.model.PartnerDefinition;
import com.interview.policyimport.model.ParsedRow;

import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Stream;

public interface PartnerFileParser {

    boolean supports(String format);

    Stream<ParsedRow> parse(
            InputStream inputStream,
            PartnerDefinition definition
    ) throws IOException;
}