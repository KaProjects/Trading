package org.kaleta.service;

import org.kaleta.rest.error.InvalidInputException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class CsvImportParser
{
    private static final int MAX_CSV_LENGTH = 1_000_000;

    private CsvImportParser() {}

    static List<CsvRecord> parse(String csv, List<String> expectedHeaders, int maxRows, String rowName)
    {
        if (csv == null || csv.isBlank()) {
            throw new InvalidInputException("CSV file is empty");
        }
        if (csv.length() > MAX_CSV_LENGTH) {
            throw new InvalidInputException(
                    "CSV file exceeds the maximum size of " + MAX_CSV_LENGTH + " characters");
        }

        List<CsvRecord> records = readRecords(csv).stream()
                .filter(record -> record.values().stream().anyMatch(value -> !value.isBlank()))
                .toList();
        if (records.isEmpty()) {
            throw new InvalidInputException("CSV file is empty");
        }

        List<String> actualHeaders = new ArrayList<>(records.getFirst().values());
        if (!actualHeaders.isEmpty()) {
            actualHeaders.set(0, actualHeaders.getFirst().replace("\uFEFF", ""));
        }
        actualHeaders = actualHeaders.stream()
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .toList();
        if (!actualHeaders.equals(expectedHeaders)) {
            throw new InvalidInputException("CSV header must be: " + String.join(",", expectedHeaders));
        }

        List<CsvRecord> rows = records.subList(1, records.size());
        if (rows.isEmpty()) {
            throw new InvalidInputException("CSV contains no " + rowName + " rows");
        }
        if (rows.size() > maxRows) {
            throw new InvalidInputException("CSV contains more than " + maxRows + " " + rowName + " rows");
        }
        return rows;
    }

    private static List<CsvRecord> readRecords(String csv)
    {
        String normalized = csv.replace("\r\n", "\n").replace('\r', '\n');
        List<CsvRecord> records = new ArrayList<>();
        List<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        int lineNumber = 1;
        int recordLineNumber = 1;

        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < normalized.length() && normalized.charAt(index + 1) == '"') {
                    value.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                values.add(value.toString());
                value.setLength(0);
            } else if (character == '\n' && !quoted) {
                values.add(value.toString());
                records.add(new CsvRecord(recordLineNumber, List.copyOf(values)));
                values.clear();
                value.setLength(0);
                lineNumber++;
                recordLineNumber = lineNumber;
            } else {
                value.append(character);
                if (character == '\n') lineNumber++;
            }
        }
        if (quoted) {
            throw new InvalidInputException(
                    "CSV contains an unclosed quoted value starting near line " + recordLineNumber);
        }
        if (!values.isEmpty() || !value.isEmpty()) {
            values.add(value.toString());
            records.add(new CsvRecord(recordLineNumber, List.copyOf(values)));
        }
        return records;
    }

    record CsvRecord(int lineNumber, List<String> values) {}
}
