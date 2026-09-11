package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.kaleta.persistence.api.TradeDao;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.Latest;
import org.kaleta.persistence.entity.Trade;
import org.kaleta.rest.dto.SplitApplyDto;
import org.kaleta.rest.dto.SplitResultDto;
import org.kaleta.rest.error.InvalidInputException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class SplitService
{
    private static final int VALUE_SCALE = 4;
    private static final BigDecimal MAX_QUANTITY = new BigDecimal("9999999.9999");
    private static final BigDecimal MAX_PRICE = new BigDecimal("999999.9999");

    @Inject
    TradeDao tradeDao;
    @Inject
    CompanyService companyService;
    @Inject
    RecordService recordService;
    @Inject
    LatestService latestService;

    @Transactional
    public SplitResultDto apply(SplitApplyDto dto)
    {
        Company company = companyService.findEntity(dto.getCompanyId());
        List<Trade> trades = tradeDao.list(true, dto.getCompanyId(), null, null, null, null);
        if (trades.isEmpty()) {
            throw new InvalidInputException(
                    "no open trades to split for company '" + company.getTicker() + "'");
        }

        List<String> details = new ArrayList<>();
        for (Trade trade : trades) {
            BigDecimal quantity = scaled(trade.getQuantity()
                    .multiply(dto.getSplitTo())
                    .divide(dto.getSplitFrom(), VALUE_SCALE + 4, RoundingMode.HALF_UP));
            BigDecimal price = scaled(trade.getPurchasePrice()
                    .multiply(dto.getSplitFrom())
                    .divide(dto.getSplitTo(), VALUE_SCALE + 4, RoundingMode.HALF_UP));

            requireStorable(company, trade, quantity, MAX_QUANTITY, "quantity");
            requireStorable(company, trade, price, MAX_PRICE, "purchase price");

            details.add("- " + trade.getPurchaseDate() + ": "
                    + plain(trade.getQuantity()) + "@" + plain(trade.getPurchasePrice())
                    + " -> " + plain(quantity) + "@" + plain(price));

            trade.setQuantity(quantity);
            trade.setPurchasePrice(price);
        }

        tradeDao.saveAll(trades);

        List<String> warnings = new ArrayList<>();
        boolean recordCreated = createRecord(dto, company, details, warnings);

        return new SplitResultDto(trades.size(), recordCreated, warnings);
    }

    private boolean createRecord(
            SplitApplyDto dto,
            Company company,
            List<String> details,
            List<String> warnings)
    {
        Latest latest = latestService.getSyncedForWithWarnings(company.getId()).latest();
        if (latest == null || latest.getPrice() == null) {
            warnings.add("Trades were split, but no record was created because the current price of "
                    + company.getTicker() + " is unknown.");
            return false;
        }

        String strategy = "split " + plain(dto.getSplitFrom()) + ":" + plain(dto.getSplitTo());
        recordService.createWithStrategy(
                company.getId(),
                strategy,
                dto.getDate(),
                latest.getPrice().toPlainString(),
                details);
        return true;
    }

    private void requireStorable(
            Company company,
            Trade trade,
            BigDecimal value,
            BigDecimal max,
            String field)
    {
        if (value.abs().compareTo(max) > 0) {
            throw new InvalidInputException("split would make the " + field + " of the "
                    + company.getTicker() + " trade purchased on " + trade.getPurchaseDate()
                    + " too large to store (" + plain(value) + ", maximum is " + plain(max) + ")");
        }
    }

    private BigDecimal scaled(BigDecimal value)
    {
        return value.setScale(VALUE_SCALE, RoundingMode.HALF_UP);
    }

    private String plain(BigDecimal value)
    {
        return value.stripTrailingZeros().toPlainString();
    }
}
