package com.interview.policyimport.service;

import com.interview.policyimport.model.CanonicalEnrollment;
import com.interview.policyimport.model.PartnerDefinition;
import com.interview.policyimport.model.ParsedRow;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Stream;

@Component
public class CsvPartnerFileParser implements PartnerFileParser {

    @Override
    public boolean supports(String format) {
        return "CSV".equalsIgnoreCase(format);
    }

    @Override
    public Stream<ParsedRow> parse(
            InputStream inputStream,
            PartnerDefinition definition
    ) throws IOException {

        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setDelimiter(definition.delimiter().charAt(0))
                .setHeader()
                .setSkipHeaderRecord(true)
                .get();

        Reader reader = new InputStreamReader(
                inputStream,
                StandardCharsets.UTF_8
        );

        CSVParser parser = csvFormat.parse(reader);

        return parser.stream()
                .map(record -> parseRow(record, definition));
    }

    private ParsedRow parseRow(
            CSVRecord record,
            PartnerDefinition definition
    ) {

        int rowNumber = (int) record.getRecordNumber();

        try {
            Map<String, String> columns = definition.columns();

            CanonicalEnrollment enrollment = new CanonicalEnrollment(
                    value(record, columns, "imei"),
                    value(record, columns, "plan-code"),
                    parseDate(record, columns, "effective-date"),
                    parseDate(record, columns, "expiry-date"),
                    parseDecimal(record, columns, "premium"),
                    value(record, columns, "currency")
            );

            return new ParsedRow(
                    rowNumber,
                    enrollment,
                    null,
                    null
            );

        } catch (Exception ex) {

            return new ParsedRow(
                    rowNumber,
                    null,
                    "PARSE_ERROR",
                    ex.getMessage()
            );
        }
    }

    private String value(
            CSVRecord record,
            Map<String, String> columns,
            String canonicalField
    ) {
        String columnName = columns.get(canonicalField);

        if (columnName == null) {
            throw new IllegalArgumentException(
                    "Missing column mapping: " + canonicalField
            );
        }

        return record.get(columnName).trim();
    }

    private LocalDate parseDate(
            CSVRecord record,
            Map<String, String> columns,
            String field
    ) {
        String value = value(record, columns, field);

        if (value.isBlank()) {
            return null;
        }

        return LocalDate.parse(value);
    }

    private BigDecimal parseDecimal(
            CSVRecord record,
            Map<String, String> columns,
            String field
    ) {
        String value = value(record, columns, field);

        if (value.isBlank()) {
            return null;
        }

        return new BigDecimal(value);
    }
}