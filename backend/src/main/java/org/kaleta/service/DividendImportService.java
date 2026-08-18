package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.kaleta.persistence.api.CompanyDao;
import org.kaleta.persistence.entity.Company;
import org.kaleta.rest.dto.DividendCreateDto;
import org.kaleta.rest.dto.DividendImportDto;
import org.kaleta.rest.dto.DividendImportPreviewDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@ApplicationScoped
public class DividendImportService
{
    private static final List<String> CSV_HEADERS = List.of("date", "ticker", "dividend", "tax");
    private static final int MAX_ROWS = 1000;
    private static final Pattern ISO_DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    @Inject
    CompanyDao companyDao;
    @Inject
    DividendService dividendService;

    public DividendImportPreviewDto preview(String csv)
    {
        ParsedCsv parsed = parseCsv(csv);
        return analyze(parsed.rows(), parsed.errors()).preview();
    }

    @Transactional
    public DividendImportPreviewDto importDividends(DividendImportDto dto)
    {
        Analysis analysis = analyze(dto.getRows(), List.of());
        if (!analysis.preview().isValid()) {
            return analysis.preview();
        }

        for (Candidate candidate : analysis.candidates()) {
            DividendCreateDto create = new DividendCreateDto();
            create.setCompanyId(candidate.company.getId());
            create.setDate(candidate.date.toString());
            create.setDividend(candidate.dividend.toPlainString());
            create.setTax(candidate.tax.toPlainString());
            dividendService.createDividend(create);
        }
        return analysis.preview();
    }

    private Analysis analyze(List<DividendImportDto.Row> rows,
                             List<DividendImportPreviewDto.Error> initialErrors)
    {
        DividendImportPreviewDto preview = new DividendImportPreviewDto();
        preview.getErrors().addAll(initialErrors);

        if (rows == null || rows.isEmpty()) {
            preview.getErrors().add(new DividendImportPreviewDto.Error(
                    null, "file", "CSV contains no dividend rows"));
            return new Analysis(preview, List.of());
        }
        if (rows.size() > MAX_ROWS) {
            preview.getErrors().add(new DividendImportPreviewDto.Error(
                    null, "file", "CSV contains more than " + MAX_ROWS + " dividend rows"));
            return new Analysis(preview, List.of());
        }

        Map<String, Company> companies = companyDao.list().stream().collect(Collectors.toMap(
                company -> company.getTicker().toLowerCase(Locale.ROOT),
                company -> company,
                (first, ignored) -> first,
                LinkedHashMap::new));

        List<Candidate> candidates = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            candidates.add(validate(rows.get(index), index, companies, preview));
        }

        List<Candidate> sorted = candidates.stream()
                .sorted(Comparator
                        .comparing((Candidate candidate) -> candidate.date == null ? LocalDate.MAX : candidate.date)
                        .thenComparingInt(candidate -> candidate.inputOrder))
                .toList();
        preview.setReordered(!sameOrder(candidates, sorted));
        sorted.forEach(candidate -> preview.getRows().add(candidate.row));
        preview.setValid(preview.getErrors().isEmpty());
        return new Analysis(preview, sorted);
    }

    private Candidate validate(DividendImportDto.Row input, int inputOrder, Map<String, Company> companies,
                               DividendImportPreviewDto preview)
    {
        DividendImportDto.Row source = input == null ? new DividendImportDto.Row() : input;
        int rowNumber = source.getRowNumber() == null ? inputOrder + 2 : source.getRowNumber();
        DividendImportPreviewDto.Row row = new DividendImportPreviewDto.Row();
        row.setRowNumber(rowNumber);
        row.setDate(trim(source.getDate()));
        row.setTicker(trim(source.getTicker()).toUpperCase(Locale.ROOT));
        row.setDividend(trim(source.getDividend()));
        row.setTax(trim(source.getTax()));

        Candidate candidate = new Candidate(inputOrder, row);
        if (!ISO_DATE.matcher(row.getDate()).matches()) {
            addError(preview, rowNumber, "date", "must match YYYY-MM-DD");
        } else {
            try {
                candidate.date = LocalDate.parse(row.getDate());
            } catch (DateTimeParseException exception) {
                addError(preview, rowNumber, "date", "is not a valid calendar date");
            }
        }

        if (row.getTicker().isBlank()) {
            addError(preview, rowNumber, "ticker", "must not be blank");
        } else {
            candidate.company = companies.get(row.getTicker().toLowerCase(Locale.ROOT));
            if (candidate.company == null) {
                addError(preview, rowNumber, "ticker", "company '" + row.getTicker() + "' was not found");
            } else {
                row.setTicker(candidate.company.getTicker());
            }
        }

        candidate.dividend = parseDecimal(row.getDividend(), 5, rowNumber, "dividend", preview);
        candidate.tax = parseDecimal(row.getTax(), 4, rowNumber, "tax", preview);
        if (candidate.dividend != null) row.setDividend(candidate.dividend.toPlainString());
        if (candidate.tax != null) row.setTax(candidate.tax.toPlainString());
        if (candidate.dividend != null && candidate.tax != null) {
            row.setNet(candidate.dividend.subtract(candidate.tax).stripTrailingZeros().toPlainString());
        }
        return candidate;
    }

    private BigDecimal parseDecimal(String value, int integerDigits, int rowNumber, String field,
                                    DividendImportPreviewDto preview)
    {
        String expression = "^\\d{1," + integerDigits + "}(?:\\.\\d{1,2})?$";
        if (!Pattern.matches(expression, value)) {
            addError(preview, rowNumber, field,
                    "must be a non-negative decimal with at most " + integerDigits
                            + " integer and 2 decimal digits");
            return null;
        }
        return new BigDecimal(value);
    }

    private ParsedCsv parseCsv(String csv)
    {
        List<DividendImportDto.Row> rows = new ArrayList<>();
        List<DividendImportPreviewDto.Error> errors = new ArrayList<>();
        for (CsvImportParser.CsvRecord record : CsvImportParser.parse(csv, CSV_HEADERS, MAX_ROWS, "dividend")) {
            if (record.values().size() != CSV_HEADERS.size()) {
                errors.add(new DividendImportPreviewDto.Error(record.lineNumber(), "row",
                        "expected " + CSV_HEADERS.size() + " columns but found " + record.values().size()));
            }

            DividendImportDto.Row row = new DividendImportDto.Row();
            row.setRowNumber(record.lineNumber());
            row.setDate(valueAt(record.values(), 0));
            row.setTicker(valueAt(record.values(), 1));
            row.setDividend(valueAt(record.values(), 2));
            row.setTax(valueAt(record.values(), 3));
            rows.add(row);
        }
        return new ParsedCsv(rows, errors);
    }

    private void addError(DividendImportPreviewDto preview, Integer rowNumber, String field, String message)
    {
        preview.getErrors().add(new DividendImportPreviewDto.Error(rowNumber, field, message));
    }

    private boolean sameOrder(List<Candidate> original, List<Candidate> sorted)
    {
        for (int index = 0; index < original.size(); index++) {
            if (original.get(index).inputOrder != sorted.get(index).inputOrder) return false;
        }
        return true;
    }

    private String valueAt(List<String> values, int index)
    {
        return index < values.size() ? values.get(index) : "";
    }

    private String trim(String value)
    {
        return value == null ? "" : value.trim();
    }

    private static class Candidate
    {
        private final int inputOrder;
        private final DividendImportPreviewDto.Row row;
        private LocalDate date;
        private Company company;
        private BigDecimal dividend;
        private BigDecimal tax;

        private Candidate(int inputOrder, DividendImportPreviewDto.Row row)
        {
            this.inputOrder = inputOrder;
            this.row = row;
        }
    }

    private record Analysis(DividendImportPreviewDto preview, List<Candidate> candidates) {}

    private record ParsedCsv(List<DividendImportDto.Row> rows, List<DividendImportPreviewDto.Error> errors) {}
}
