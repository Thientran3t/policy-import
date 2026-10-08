package com.interview.policyimport.service;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PartnerFileParserFactory {

    private final List<PartnerFileParser> parsers;

    public PartnerFileParserFactory(List<PartnerFileParser> parsers) {
        this.parsers = parsers;
    }

    public PartnerFileParser getParser(String format) {
        return parsers.stream()
                .filter(parser -> parser.supports(format))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Unsupported file format: " + format
                        )
                );
    }
}