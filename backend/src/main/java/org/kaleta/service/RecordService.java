package org.kaleta.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.kaleta.Utils;
import org.kaleta.model.Assets;
import org.kaleta.model.PeriodEstimates;
import org.kaleta.model.Periods;
import org.kaleta.model.PriceIndicators;
import org.kaleta.model.TargetStats;
import org.kaleta.model.TradeSaleSummary;
import org.kaleta.persistence.api.RecordDao;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.Estimate;
import org.kaleta.persistence.entity.Latest;
import org.kaleta.persistence.entity.Record;
import org.kaleta.rest.dto.RecordCreateDto;
import org.kaleta.rest.dto.RecordUpdateDto;
import org.kaleta.rest.error.InvalidInputException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class RecordService
{
    @Inject
    RecordDao recordDao;
    @Inject
    CompanyService companyService;
    @Inject
    ArithmeticService arithmeticService;
    @Inject
    PeriodService periodService;
    @Inject
    EstimateService estimateService;
    @Inject
    TargetService targetService;
    @Inject
    TradeService tradeService;
    @Inject
    ObjectMapper objectMapper;

    public void create(RecordCreateDto dto)
    {
        Record newRecord = new Record();

        newRecord.setCompany(companyService.findEntity(dto.getCompanyId()));
        newRecord.setDate(Date.valueOf(dto.getDate()));
        newRecord.setPrice(new BigDecimal(dto.getPrice()));

        newRecord.setPriceToRevenues(Utils.createNullableBigDecimal(dto.getPriceToRevenues()));
        newRecord.setPriceToGrossProfit(Utils.createNullableBigDecimal(dto.getPriceToGrossProfit()));
        newRecord.setPriceToOperatingIncome(Utils.createNullableBigDecimal(dto.getPriceToOperatingIncome()));
        newRecord.setPriceToNetIncome(Utils.createNullableBigDecimal(dto.getPriceToNetIncome()));
        newRecord.setPriceToFreeCashFlow(Utils.createNullableBigDecimal(dto.getPriceToFreeCashFlow()));

        newRecord.setForwardPe(Utils.createNullableBigDecimal(dto.getForwardPe()));

        newRecord.setDividendYield(Utils.createNullableBigDecimal(dto.getDividendYield()));

        newRecord.setSumAssetQuantity(Utils.createNullableBigDecimal(dto.getSumAssetQuantity()));
        newRecord.setAvgAssetPrice(Utils.createNullableBigDecimal(dto.getAvgAssetPrice()));

        newRecord.setTargets(dto.getTargets());

        recordDao.create(newRecord);
    }

    public void createCurrent(Long companyId, String titlePrefix, String date, String price)
    {
        createCurrent(companyId, titlePrefix, date, price, null);
    }

    public void createCurrent(Long companyId, String titlePrefix, String date, String price,
                              TradeSaleSummary sale)
    {
        Company company = companyService.findEntity(companyId);
        List<String> strategyDetails = new ArrayList<>();
        if (sale != null) {
            String currency = company.getCurrency().toString();
            strategyDetails.add("- " + formatDecimal(sale.quantity(), 5) + "@"
                    + formatDecimal(sale.averagePurchasePrice(), 5) + currency
                    + " - " + formatDecimal(sale.fees(), 2) + currency
                    + " = " + formatPerformance(sale.profit(), sale.profitPercentage(), currency));
        }

        createWithStrategy(companyId, titlePrefix + "@" + price + company.getCurrency(),
                date, price, strategyDetails);
    }

    public void createWithStrategy(Long companyId, String strategy, String date, String price,
                                   List<String> strategyDetails)
    {
        createBulleted(companyId, date, price, strategy, strategyDetails, false);
    }

    public void createWithContent(Long companyId, String content, String date, String price,
                                  List<String> contentDetails)
    {
        createBulleted(companyId, date, price, content, contentDetails, true);
    }

    private void createBulleted(Long companyId, String date, String price, String heading,
                                List<String> details, boolean asContent)
    {
        Company company = companyService.findEntity(companyId);
        Periods periods = periodService.getBy(companyId);
        String bulletedList = createBulletedList(heading, details);

        Record dayRecord = findRecord(companyId, Date.valueOf(date));
        String mergedContent = dayRecord == null
                ? null
                : mergeContent(asContent ? dayRecord.getContent() : dayRecord.getStrategy(), bulletedList);

        Record record = mergedContent == null ? new Record() : dayRecord;

        record.setCompany(company);
        if (asContent) {
            record.setContent(mergedContent == null ? bulletedList : mergedContent);
        } else {
            record.setStrategy(mergedContent == null ? bulletedList : mergedContent);
        }

        record.setDate(Date.valueOf(date));
        record.setPrice(new BigDecimal(price));

        Latest latest = new Latest(company, LocalDate.parse(date).atStartOfDay(), new BigDecimal(price));

        if (periods.getTtm() != null && periods.getTtm().getShares() != null) {
            PriceIndicators indicators = arithmeticService.computeIndicators(latest, periods.getTtm());

            record.setPriceToRevenues(indicators.getTtm().getMarketCapToRevenues());
            record.setPriceToGrossProfit(indicators.getTtm().getMarketCapToGrossProfit());
            record.setPriceToOperatingIncome(indicators.getTtm().getMarketCapToOperatingIncome());
            record.setPriceToNetIncome(indicators.getTtm().getMarketCapToNetIncome());
            record.setPriceToFreeCashFlow(indicators.getTtm().getMarketCapToFreeCashFlow());

            record.setDividendYield(indicators.getTtm().getDividendYield());
        }

        Periods.Period latestPeriod = periods.getPeriods().isEmpty() ? null : periods.getPeriods().get(0);

        record.setForwardPe(computeForwardPe(latestPeriod, latest.getPrice()));

        if (record.getTargets() == null || record.getTargets().isBlank()) {
            record.setTargets(formatTargetStats(latestPeriod, company.getCurrency().toString()));
        }

        Assets assets = tradeService.getAssets(companyId, latest.getPrice());

        if (assets.getAggregate() != null) {
            record.setSumAssetQuantity(assets.getAggregate().getQuantity());
            record.setAvgAssetPrice(assets.getAggregate().getPurchasePrice());
        }

        if (mergedContent == null) {
            recordDao.create(record);
        } else {
            recordDao.save(record);
        }
    }

    private Record findRecord(Long companyId, Date date)
    {
        return recordDao.list(companyId).stream()
                .filter(record -> date.equals(record.getDate()))
                .max((a, b) -> Long.compare(a.getId(), b.getId()))
                .orElse(null);
    }

    private String mergeContent(String existing, String addition)
    {
        if (existing == null || existing.isBlank()) return addition;

        try {
            JsonNode existingNode = objectMapper.readTree(existing);
            JsonNode additionNode = objectMapper.readTree(addition);

            if (!existingNode.isArray() || !additionNode.isArray()) return null;
            if (isEmptyContent(existingNode)) return addition;

            ArrayNode merged = objectMapper.createArrayNode();
            merged.addAll((ArrayNode) existingNode);
            merged.addAll((ArrayNode) additionNode);

            return objectMapper.writeValueAsString(merged);
        } catch (Exception exception) {
            return null;
        }
    }

    private boolean isEmptyContent(JsonNode node)
    {
        if (node.isObject() && !node.path("text").asText("").isBlank()) return false;

        for (JsonNode child : node) {
            if (!isEmptyContent(child)) return false;
        }
        return true;
    }

    private BigDecimal computeForwardPe(Periods.Period latestPeriod, BigDecimal price)
    {
        if (latestPeriod == null || latestPeriod.getFinancial() != null) return null;

        PeriodEstimates estimates = estimateService.getLatest(latestPeriod.getId(), Estimate.EPS).orElse(null);
        if (estimates == null) return null;

        List<BigDecimal> quarters = List.of();
        if (estimates.getCurrent() != null && estimates.getNext1() != null
                && estimates.getNext2() != null && estimates.getNext3() != null) {
            quarters = List.of(estimates.getCurrent(), estimates.getNext1(),
                    estimates.getNext2(), estimates.getNext3());
        }
        if (quarters.isEmpty()) return null;

        BigDecimal earnings = quarters.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (earnings.signum() == 0) return null;

        return price.divide(earnings, 2, RoundingMode.HALF_UP);
    }

    private String formatTargetStats(Periods.Period latestPeriod, String currency)
    {
        if (latestPeriod == null) return null;

        TargetStats stats = targetService.getStatistics(List.of(latestPeriod.getId())).get(latestPeriod.getId());
        if (stats == null || stats.count() < 1) return null;

        return stats.count() + "@(" + formatTarget(stats.maximum()) + "-" + formatTarget(stats.minimum())
                + ")~" + formatTarget(stats.average()) + currency;
    }

    private String formatTarget(BigDecimal value)
    {
        int scale = value.abs().compareTo(BigDecimal.TEN) < 0 ? 1 : 0;
        return value.setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatPerformance(BigDecimal profit, BigDecimal profitPercentage, String currency)
    {
        if (profitPercentage == null && profit == null) return "";
        if (profitPercentage == null) return formatSigned(profit, 2) + currency;
        if (profit == null) return "(" + formatSigned(profitPercentage, 2) + "%)";
        return formatSigned(profit, 2) + currency + " (" + formatSigned(profitPercentage, 2) + "%)";
    }

    private String formatSigned(BigDecimal value, int maxScale)
    {
        BigDecimal rounded = value.setScale(maxScale, RoundingMode.HALF_UP).stripTrailingZeros();
        return (rounded.signum() >= 0 ? "+" : "") + rounded.toPlainString();
    }

    private String formatDecimal(BigDecimal value, int maxScale)
    {
        return value.setScale(maxScale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private String createBulletedList(String text, List<String> innerTexts)
    {
        ObjectNode textNode = objectMapper.createObjectNode().put("text", text);

        ObjectNode listItem = objectMapper.createObjectNode().put("type", "list-item");
        listItem.set("children", objectMapper.createArrayNode().add(textNode));

        if (!innerTexts.isEmpty()) {
            ObjectNode innerList = objectMapper.createObjectNode().put("type", "bulleted-list");
            innerList.set("children", objectMapper.createArrayNode());
            for (String innerText : innerTexts) {
                ObjectNode innerTextNode = objectMapper.createObjectNode().put("text", innerText);
                ObjectNode innerListItem = objectMapper.createObjectNode().put("type", "list-item");
                innerListItem.set("children", objectMapper.createArrayNode().add(innerTextNode));
                innerList.withArray("children").add(innerListItem);
            }
            listItem.withArray("children").add(innerList);
        }

        ObjectNode bulletedList = objectMapper.createObjectNode().put("type", "bulleted-list");
        bulletedList.set("children", objectMapper.createArrayNode().add(listItem));

        return objectMapper.createArrayNode().add(bulletedList).toString();
    }

    public void update(RecordUpdateDto dto)
    {
        Record record;
        try {
            record = recordDao.get(dto.getId());
        } catch (NoResultException e){
            throw new InvalidInputException("record with id '" + dto.getId() + "' not found");
        }

        if (dto.getTitle() != null) record.setTitle(dto.getTitle());
        if (dto.getContent() != null) record.setContent(dto.getContent());
        if (dto.getReview() != null) record.setReview(dto.getReview());
        if (dto.getStrategy() != null) record.setStrategy(dto.getStrategy());
        if (dto.getRetro() != null) record.setRetro(dto.getRetro());
        if (dto.getTargets() != null) record.setTargets(dto.getTargets());
        if (dto.getPrice() != null) record.setPrice(new BigDecimal(dto.getPrice()));
        if (dto.getDividendYield() != null) {
            record.setDividendYield(dto.getDividendYield().isBlank() ? null : new BigDecimal(dto.getDividendYield()));
        }
        if (dto.getForwardPe() != null) {
            record.setForwardPe(dto.getForwardPe().isBlank() ? null : new BigDecimal(dto.getForwardPe()));
        }
        if (dto.getPriceToRevenues() != null) record.setPriceToRevenues(Utils.createNullableBigDecimal(dto.getPriceToRevenues()));
        if (dto.getPriceToGrossProfit() != null) record.setPriceToGrossProfit(Utils.createNullableBigDecimal(dto.getPriceToGrossProfit()));
        if (dto.getPriceToOperatingIncome() != null) record.setPriceToOperatingIncome(Utils.createNullableBigDecimal(dto.getPriceToOperatingIncome()));
        if (dto.getPriceToNetIncome() != null) record.setPriceToNetIncome(Utils.createNullableBigDecimal(dto.getPriceToNetIncome()));
        if (dto.getPriceToFreeCashFlow() != null) record.setPriceToFreeCashFlow(Utils.createNullableBigDecimal(dto.getPriceToFreeCashFlow()));
        if (dto.getSumAssetQuantity() != null) record.setSumAssetQuantity(Utils.createNullableBigDecimal(dto.getSumAssetQuantity()));
        if (dto.getAvgAssetPrice() != null) record.setAvgAssetPrice(Utils.createNullableBigDecimal(dto.getAvgAssetPrice()));

        recordDao.save(record);
    }

    public List<org.kaleta.model.Record> getBy(Long companyId)
    {
        List<org.kaleta.model.Record> records = recordDao.list(companyId).stream()
                .map(recordEntity -> from(recordEntity)).collect(Collectors.toList());
        records.sort((a, b) -> -Utils.compareDbDates(a.getDate(), b.getDate()));
        return records;
    }

    public void delete(Long recordId){
        try {
            recordDao.get(recordId);
        } catch (NoResultException e){
            throw new InvalidInputException("record with id '" + recordId + "' not found");
        }
        recordDao.delete(recordId);
    }

    private org.kaleta.model.Record from(Record recordEntity)
    {
        org.kaleta.model.Record  record = new org.kaleta.model.Record();

        record.setId(recordEntity.getId());
        record.setDate(recordEntity.getDate());
        record.setTitle(recordEntity.getTitle());
        record.setContent(recordEntity.getContent());
        record.setReview(recordEntity.getReview());

        record.setPrice(recordEntity.getPrice());

        record.setPriceToRevenues(recordEntity.getPriceToRevenues());
        record.setPriceToGrossProfit(recordEntity.getPriceToGrossProfit());
        record.setPriceToOperatingIncome(recordEntity.getPriceToOperatingIncome());
        record.setPriceToNetIncome(recordEntity.getPriceToNetIncome());
        record.setPriceToFreeCashFlow(recordEntity.getPriceToFreeCashFlow());

        record.setForwardPe(recordEntity.getForwardPe());

        record.setDividendYield(recordEntity.getDividendYield());

        record.setStrategy(recordEntity.getStrategy());
        record.setRetro(recordEntity.getRetro());
        record.setTargets(recordEntity.getTargets());

        record.setAsset(arithmeticService.computeAsset(recordEntity.getPrice(), recordEntity.getSumAssetQuantity(), recordEntity.getAvgAssetPrice()));

        return record;
    }
}
