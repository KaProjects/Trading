package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.kaleta.model.TradeSaleSummary;
import org.kaleta.persistence.api.CompanyDao;
import org.kaleta.persistence.api.TradeDao;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.Portfolio;
import org.kaleta.persistence.entity.Trade;
import org.kaleta.rest.dto.TradeCreateDto;
import org.kaleta.rest.dto.TradeImportDto;
import org.kaleta.rest.dto.TradeImportPreviewDto;
import org.kaleta.rest.dto.TradeSellDto;
import org.kaleta.rest.error.InvalidInputException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@ApplicationScoped
public class TradeImportService
{
    private static final List<String> CSV_HEADERS =
            List.of("date", "type", "ticker", "quantity", "price", "fees", "portfolio");
    private static final int MAX_ROWS = 1000;
    private static final Pattern ISO_DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Comparator<VirtualLot> FIFO = Comparator
            .comparing(VirtualLot::purchaseDate)
            .thenComparingInt(VirtualLot::sourceRank)
            .thenComparingLong(VirtualLot::sourceOrder);

    @Inject
    CompanyDao companyDao;
    @Inject
    TradeDao tradeDao;
    @Inject
    TradeService tradeService;
    @Inject
    RecordService recordService;

    public TradeImportPreviewDto preview(String csv)
    {
        ParsedCsv parsed = parseCsv(csv);
        return analyze(parsed.rows(), parsed.errors()).preview();
    }

    @Transactional
    public TradeImportPreviewDto importTrades(TradeImportDto dto)
    {
        Analysis analysis = analyze(dto.getRows(), List.of());
        if (!analysis.preview().isValid()) {
            return analysis.preview();
        }

        execute(analysis.candidates());
        return analysis.preview();
    }

    private Analysis analyze(List<TradeImportDto.Row> rows, List<TradeImportPreviewDto.Error> initialErrors)
    {
        TradeImportPreviewDto preview = new TradeImportPreviewDto();
        preview.getErrors().addAll(initialErrors);

        if (rows == null || rows.isEmpty()) {
            preview.getErrors().add(new TradeImportPreviewDto.Error(null, "file", "CSV contains no trade rows"));
            return new Analysis(preview, List.of());
        }
        if (rows.size() > MAX_ROWS) {
            preview.getErrors().add(new TradeImportPreviewDto.Error(
                    null, "file", "CSV contains more than " + MAX_ROWS + " trade rows"));
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

        Map<LotKey, List<VirtualLot>> lots = activeLots();
        for (Candidate candidate : sorted) {
            if (hasErrors(preview, candidate.row.getRowNumber())) {
                continue;
            }

            LotKey key = new LotKey(candidate.company.getId(), candidate.portfolio);
            List<VirtualLot> availableLots = lots.computeIfAbsent(key, ignored -> new ArrayList<>());
            availableLots.sort(FIFO);

            if (candidate.type == OperationType.BUY) {
                availableLots.add(VirtualLot.csv(candidate));
                availableLots.sort(FIFO);
                continue;
            }

            previewSale(candidate, availableLots, preview);
        }

        preview.setValid(preview.getErrors().isEmpty());
        return new Analysis(preview, sorted);
    }

    private Candidate validate(TradeImportDto.Row input, int inputOrder, Map<String, Company> companies,
                               TradeImportPreviewDto preview)
    {
        TradeImportDto.Row source = input == null ? new TradeImportDto.Row() : input;
        int rowNumber = source.getRowNumber() == null ? inputOrder + 2 : source.getRowNumber();
        TradeImportPreviewDto.Row row = new TradeImportPreviewDto.Row();
        row.setRowNumber(rowNumber);
        row.setDate(trim(source.getDate()));
        row.setType(trim(source.getType()).toUpperCase(Locale.ROOT));
        row.setTicker(trim(source.getTicker()).toUpperCase(Locale.ROOT));
        row.setQuantity(trim(source.getQuantity()));
        row.setPrice(trim(source.getPrice()));
        row.setFees(trim(source.getFees()));
        row.setPortfolio(trim(source.getPortfolio()).toUpperCase(Locale.ROOT));

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

        try {
            candidate.type = OperationType.valueOf(row.getType());
        } catch (IllegalArgumentException exception) {
            addError(preview, rowNumber, "type", "must be BUY or SELL");
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

        candidate.quantity = parseDecimal(row.getQuantity(), 4, 4, true, rowNumber, "quantity", preview);
        candidate.price = parseDecimal(row.getPrice(), 6, 4, false, rowNumber, "price", preview);
        candidate.fees = parseDecimal(row.getFees(), 3, 2, false, rowNumber, "fees", preview);
        if (candidate.quantity != null) row.setQuantity(candidate.quantity.toPlainString());
        if (candidate.price != null) row.setPrice(candidate.price.toPlainString());
        if (candidate.fees != null) row.setFees(candidate.fees.toPlainString());

        if (row.getPortfolio().isBlank()) {
            addError(preview, rowNumber, "portfolio", "must not be blank");
        } else {
            try {
                candidate.portfolio = Portfolio.valueOf(row.getPortfolio());
            } catch (IllegalArgumentException exception) {
                addError(preview, rowNumber, "portfolio", "portfolio '" + row.getPortfolio() + "' was not found");
            }
        }

        return candidate;
    }

    private BigDecimal parseDecimal(String value, int integerDigits, int decimalDigits, boolean positive,
                                    int rowNumber, String field, TradeImportPreviewDto preview)
    {
        String expression = "^\\d{1," + integerDigits + "}(?:\\.\\d{1," + decimalDigits + "})?$";
        if (!Pattern.matches(expression, value)) {
            addError(preview, rowNumber, field,
                    "must be a " + (positive ? "positive" : "non-negative")
                            + " decimal with at most " + integerDigits + " integer and "
                            + decimalDigits + " decimal digits");
            return null;
        }

        BigDecimal decimal = new BigDecimal(value);
        if (positive && decimal.compareTo(BigDecimal.ZERO) <= 0) {
            addError(preview, rowNumber, field, "must be greater than zero");
            return null;
        }
        return decimal;
    }

    private Map<LotKey, List<VirtualLot>> activeLots()
    {
        Map<LotKey, List<VirtualLot>> lots = new HashMap<>();
        for (Trade trade : tradeDao.list(true, null, null, null, null, null)) {
            if (trade.getPortfolio() == null || trade.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            LotKey key = new LotKey(trade.getCompany().getId(), trade.getPortfolio());
            lots.computeIfAbsent(key, ignored -> new ArrayList<>()).add(VirtualLot.existing(trade));
        }
        lots.values().forEach(values -> values.sort(FIFO));
        return lots;
    }

    private void previewSale(Candidate candidate, List<VirtualLot> lots, TradeImportPreviewDto preview)
    {
        BigDecimal required = candidate.quantity;
        List<PlannedAllocation> planned = new ArrayList<>();

        for (VirtualLot lot : lots) {
            if (required.compareTo(BigDecimal.ZERO) == 0) break;
            if (lot.remaining().compareTo(BigDecimal.ZERO) <= 0 || lot.purchaseDate().isAfter(candidate.date)) {
                continue;
            }
            BigDecimal allocated = lot.remaining().min(required);
            planned.add(new PlannedAllocation(lot, allocated));
            required = required.subtract(allocated);
        }

        for (PlannedAllocation allocation : planned) {
            TradeImportPreviewDto.Allocation dto = new TradeImportPreviewDto.Allocation();
            dto.setSource(allocation.lot().source());
            dto.setPurchaseDate(allocation.lot().purchaseDate().toString());
            dto.setQuantity(formatDecimal(allocation.quantity(), 4));
            candidate.row.getAllocations().add(dto);
        }

        BigDecimal available = candidate.quantity.subtract(required);
        if (required.compareTo(BigDecimal.ZERO) > 0) {
            candidate.row.setRemainingQuantity(formatDecimal(totalRemaining(lots), 4));
            addError(preview, candidate.row.getRowNumber(), "quantity",
                    "cannot sell " + formatDecimal(candidate.quantity, 4) + "; only "
                            + formatDecimal(available, 4) + " is available for "
                            + candidate.row.getTicker() + " in " + candidate.portfolio
                            + " on " + candidate.date);
            return;
        }

        planned.forEach(allocation -> allocation.lot().consume(allocation.quantity()));
        candidate.row.setRemainingQuantity(formatDecimal(totalRemaining(lots), 4));
    }

    private void execute(List<Candidate> candidates)
    {
        for (Candidate candidate : candidates) {
            if (candidate.type == OperationType.BUY) {
                TradeCreateDto create = new TradeCreateDto();
                create.setCompanyId(candidate.company.getId());
                create.setDate(candidate.date.toString());
                create.setQuantity(candidate.quantity.toPlainString());
                create.setPrice(candidate.price.toPlainString());
                create.setFees(candidate.fees.toPlainString());
                create.setPortfolio(candidate.portfolio.name());
                tradeService.createTrade(create);

                recordService.createCurrent(
                        candidate.company.getId(),
                        "bought " + formatDecimal(candidate.quantity, 4),
                        candidate.date.toString(),
                        formatDecimal(candidate.price, 4));
                continue;
            }

            TradeSellDto sell = createSellDto(candidate);
            TradeSaleSummary summary = tradeService.sellTrade(sell);
            recordService.createCurrent(
                    candidate.company.getId(),
                    "sold " + formatDecimal(summary.quantity(), 5),
                    candidate.date.toString(),
                    formatDecimal(candidate.price, 5),
                    summary);
        }
    }

    private TradeSellDto createSellDto(Candidate candidate)
    {
        List<Trade> active = tradeDao.list(
                        true, candidate.company.getId(), null, null, null, null, candidate.portfolio.name())
                .stream()
                .filter(trade -> !trade.getPurchaseDate().toLocalDate().isAfter(candidate.date))
                .sorted(Comparator.comparing(Trade::getPurchaseDate).thenComparing(Trade::getId))
                .toList();

        BigDecimal required = candidate.quantity;
        List<TradeSellDto.Trade> selected = new ArrayList<>();
        for (Trade trade : active) {
            if (required.compareTo(BigDecimal.ZERO) == 0) break;
            BigDecimal quantity = trade.getQuantity().min(required);
            selected.add(new TradeSellDto.Trade(trade.getId(), quantity.toPlainString()));
            required = required.subtract(quantity);
        }
        if (required.compareTo(BigDecimal.ZERO) > 0) {
            throw new InvalidInputException("trade availability changed after preview for CSV row '"
                    + candidate.row.getRowNumber() + "'");
        }

        TradeSellDto sell = new TradeSellDto();
        sell.setCompanyId(candidate.company.getId());
        sell.setDate(candidate.date.toString());
        sell.setPrice(candidate.price.toPlainString());
        sell.setFees(candidate.fees.toPlainString());
        sell.setTrades(selected);
        return sell;
    }

    private ParsedCsv parseCsv(String csv)
    {
        List<TradeImportDto.Row> rows = new ArrayList<>();
        List<TradeImportPreviewDto.Error> errors = new ArrayList<>();
        for (CsvImportParser.CsvRecord record : CsvImportParser.parse(csv, CSV_HEADERS, MAX_ROWS, "trade")) {
            if (record.values().size() != CSV_HEADERS.size()) {
                errors.add(new TradeImportPreviewDto.Error(record.lineNumber(), "row",
                        "expected " + CSV_HEADERS.size() + " columns but found " + record.values().size()));
            }

            TradeImportDto.Row row = new TradeImportDto.Row();
            row.setRowNumber(record.lineNumber());
            row.setDate(valueAt(record.values(), 0));
            row.setType(valueAt(record.values(), 1));
            row.setTicker(valueAt(record.values(), 2));
            row.setQuantity(valueAt(record.values(), 3));
            row.setPrice(valueAt(record.values(), 4));
            row.setFees(valueAt(record.values(), 5));
            row.setPortfolio(valueAt(record.values(), 6));
            rows.add(row);
        }
        return new ParsedCsv(rows, errors);
    }

    private boolean hasErrors(TradeImportPreviewDto preview, Integer rowNumber)
    {
        return preview.getErrors().stream().anyMatch(error -> rowNumber.equals(error.getRowNumber()));
    }

    private void addError(TradeImportPreviewDto preview, Integer rowNumber, String field, String message)
    {
        preview.getErrors().add(new TradeImportPreviewDto.Error(rowNumber, field, message));
    }

    private boolean sameOrder(List<Candidate> original, List<Candidate> sorted)
    {
        for (int index = 0; index < original.size(); index++) {
            if (original.get(index).inputOrder != sorted.get(index).inputOrder) return false;
        }
        return true;
    }

    private BigDecimal totalRemaining(List<VirtualLot> lots)
    {
        return lots.stream().map(VirtualLot::remaining).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String valueAt(List<String> values, int index)
    {
        return index < values.size() ? values.get(index) : "";
    }

    private String trim(String value)
    {
        return value == null ? "" : value.trim();
    }

    private String formatDecimal(BigDecimal value, int maxScale)
    {
        return value.setScale(maxScale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private enum OperationType
    {
        BUY,
        SELL
    }

    private static class Candidate
    {
        private final int inputOrder;
        private final TradeImportPreviewDto.Row row;
        private LocalDate date;
        private OperationType type;
        private Company company;
        private BigDecimal quantity;
        private BigDecimal price;
        private BigDecimal fees;
        private Portfolio portfolio;

        private Candidate(int inputOrder, TradeImportPreviewDto.Row row)
        {
            this.inputOrder = inputOrder;
            this.row = row;
        }
    }

    private record Analysis(TradeImportPreviewDto preview, List<Candidate> candidates) {}

    private record ParsedCsv(List<TradeImportDto.Row> rows, List<TradeImportPreviewDto.Error> errors) {}

    private record LotKey(Long companyId, Portfolio portfolio) {}

    private record PlannedAllocation(VirtualLot lot, BigDecimal quantity) {}

    private static class VirtualLot
    {
        private final LocalDate purchaseDate;
        private final String source;
        private final int sourceRank;
        private final long sourceOrder;
        private BigDecimal remaining;

        private VirtualLot(LocalDate purchaseDate, String source, int sourceRank, long sourceOrder,
                           BigDecimal remaining)
        {
            this.purchaseDate = purchaseDate;
            this.source = source;
            this.sourceRank = sourceRank;
            this.sourceOrder = sourceOrder;
            this.remaining = remaining;
        }

        private static VirtualLot existing(Trade trade)
        {
            return new VirtualLot(
                    trade.getPurchaseDate().toLocalDate(),
                    "Existing trade #" + trade.getId(),
                    0,
                    trade.getId(),
                    trade.getQuantity());
        }

        private static VirtualLot csv(Candidate candidate)
        {
            return new VirtualLot(
                    candidate.date,
                    "CSV row " + candidate.row.getRowNumber(),
                    1,
                    candidate.inputOrder,
                    candidate.quantity);
        }

        private LocalDate purchaseDate() { return purchaseDate; }
        private String source() { return source; }
        private int sourceRank() { return sourceRank; }
        private long sourceOrder() { return sourceOrder; }
        private BigDecimal remaining() { return remaining; }

        private void consume(BigDecimal quantity)
        {
            remaining = remaining.subtract(quantity);
        }
    }
}
