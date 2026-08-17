package org.kaleta.rest;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.kaleta.model.Company;
import org.kaleta.model.TradeSaleSummary;
import org.kaleta.model.Trades;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.Latest;
import org.kaleta.persistence.entity.Portfolio;
import org.kaleta.persistence.entity.Sector;
import org.kaleta.rest.dto.TradeCreateDto;
import org.kaleta.rest.dto.TradeSellDto;
import org.kaleta.rest.validation.ValidId;
import org.kaleta.rest.validation.ValueOfEnum;
import org.kaleta.service.ArithmeticService;
import org.kaleta.service.FirebaseService;
import org.kaleta.service.LatestService;
import org.kaleta.service.RecordService;
import org.kaleta.service.TradeService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

@Path("/trade")
public class TradeEndpoints
{
    @Inject
    TradeService tradeService;
    @Inject
    FirebaseService firebaseService;
    @Inject
    RecordService recordService;
    @Inject
    LatestService latestService;
    @Inject
    ArithmeticService arithmeticService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/")
    public Response getTrades(
            @QueryParam("active")
            Boolean active,
            @Pattern(regexp = "^\\d\\d\\d\\d$", message = "must match YYYY")
            @QueryParam("year")
            String year,
            @ValidId
            @QueryParam("companyId")
            Long companyId,
            @ValueOfEnum(enumClass = Currency.class)
            @QueryParam("currency")
            String currency,
            @ValueOfEnum(enumClass = Sector.class)
            @QueryParam("sector")
            String sector,
            @ValueOfEnum(enumClass = Portfolio.class)
            @QueryParam("portfolio")
            String portfolio
    ) {
        Trades trades = tradeService.getBy(active, companyId, currency, year, year, sector, portfolio);

        if (active != null && active)
        {
            Map<Company, LatestService.SyncResult> synced = new HashMap<>();

            for (Trades.Trade trade : trades.getTrades())
            {
                LatestService.SyncResult syncResult = synced.computeIfAbsent(
                        trade.getCompany(),
                        company -> latestService.getSyncedForWithWarnings(company.getId()));
                Latest latest = syncResult.latest();

                if (latest != null)
                {
                    trade.setSellDate(new Date(latest.getDatetime()
                            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()));
                    trade.setSellQuantity(trade.getPurchaseQuantity());
                    trade.setSellPrice(latest.getPrice());
                    trade.setSellFees(trade.getPurchaseFees());
                    trade.setSellTotal(arithmeticService.sellTotal(trade.getSellPrice(), trade.getSellQuantity(), trade.getSellFees()));
                    trade.setProfit(trade.getSellTotal().subtract(trade.getPurchaseTotal()));
                    trade.setProfitPercentage(arithmeticService.profitPercentage(trade.getPurchaseTotal(), trade.getSellTotal()));
                }
            }
            synced.values().stream()
                    .flatMap(result -> result.warnings().stream())
                    .distinct()
                    .forEach(trades.getWarnings()::add);
        }
        return Response.ok(trades).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/")
    public Response createTrade(@Valid @NotNull TradeCreateDto tradeCreateDto)
    {
        tradeService.createTrade(tradeCreateDto);
        firebaseService.pushAssets(tradeService.getBy(true, null, null, null, null, null));

        String recordTitle = "bought " + tradeCreateDto.getQuantity();
        recordService.createCurrent(tradeCreateDto.getCompanyId(), recordTitle,tradeCreateDto.getDate(), tradeCreateDto.getPrice());

        return Response.status(Response.Status.CREATED).build();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/")
    public Response sellTrade(@Valid @NotNull TradeSellDto tradeSellDto)
    {
        TradeSaleSummary sale = tradeService.sellTrade(tradeSellDto);
        firebaseService.pushAssets(tradeService.getBy(true, null, null, null, null, null));

        String recordTitle = "sold " + formatDecimal(sale.quantity(), 5);
        recordService.createCurrent(
                tradeSellDto.getCompanyId(),
                recordTitle,
                tradeSellDto.getDate(),
                formatDecimal(new BigDecimal(tradeSellDto.getPrice()), 5),
                sale);

        return Response.noContent().build();
    }

    private String formatDecimal(BigDecimal value, int maxScale)
    {
        return value.setScale(maxScale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
