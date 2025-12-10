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
import org.kaleta.dto.TradeCreateDto;
import org.kaleta.dto.TradeSellDto;
import org.kaleta.dto.TradesUiDto;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.Latest;
import org.kaleta.persistence.entity.Sector;
import org.kaleta.persistence.entity.Trade;
import org.kaleta.rest.validation.ValidUuid;
import org.kaleta.rest.validation.ValueOfEnum;
import org.kaleta.service.FirebaseService;
import org.kaleta.service.LatestService;
import org.kaleta.service.RecordService;
import org.kaleta.service.TradeService;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.DoubleStream;

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

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/")
    public Response getTrades(
            @QueryParam("active")
            Boolean active,
            @Pattern(regexp = "^\\d\\d\\d\\d$", message = "must match YYYY")
            @QueryParam("year")
            String year,
            @ValidUuid
            @QueryParam("companyId")
            String companyId,
            @ValueOfEnum(enumClass = Currency.class)
            @QueryParam("currency")
            String currency,
            @ValueOfEnum(enumClass = Sector.class)
            @QueryParam("sector")
            String sector
    ) {
        List<Trade> trades = tradeService.getTrades(active, companyId, currency, year, year, sector);

        if (active != null && active)
        {
            Map<Company, Latest> synced = new HashMap<>();

            for (Trade trade : trades)
            {
                synced.computeIfAbsent(trade.getCompany(), company -> latestService.getSyncedFor(company));

                if (synced.containsKey(trade.getCompany()))
                {
                    trade.setSellDate(new Date(synced.get(trade.getCompany()).getDatetime()
                            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()));
                    trade.setSellPrice(synced.get(trade.getCompany()).getPrice());
                    trade.setSellFees(trade.getPurchaseFees());
                }
            }
        }
        TradesUiDto dto = TradesUiDto.from(trades);
        dto.setSums(tradeService.computeSums(trades));
        return Response.ok(dto).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/")
    public Response createTrade(@Valid @NotNull TradeCreateDto tradeCreateDto)
    {
        tradeService.createTrade(tradeCreateDto);
        firebaseService.pushAssets(tradeService.getTrades(true, null, null, null, null, null));

        String recordTitle = "bought " + tradeCreateDto.getQuantity();
        recordService.createCurrent(tradeCreateDto.getCompanyId(), recordTitle,tradeCreateDto.getDate(), tradeCreateDto.getPrice());

        return Response.status(Response.Status.CREATED).build();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/")
    public Response sellTrade(@Valid @NotNull TradeSellDto tradeSellDto)
    {
        tradeService.sellTrade(tradeSellDto);
        firebaseService.pushAssets(tradeService.getTrades(true, null, null, null, null, null));

        double quantity = tradeSellDto.getTrades().stream().flatMapToDouble(trade -> DoubleStream.of(Double.parseDouble(trade.getQuantity()))).sum();
        String recordTitle = "sold " + new BigDecimal(quantity);
        recordService.createCurrent(tradeSellDto.getCompanyId(), recordTitle,tradeSellDto.getDate(), tradeSellDto.getPrice());

        return Response.noContent().build();
    }
}
