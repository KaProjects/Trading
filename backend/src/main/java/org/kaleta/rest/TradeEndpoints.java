package org.kaleta.rest;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
import org.kaleta.dto.TradeDto;
import org.kaleta.dto.TradeSellDto;
import org.kaleta.dto.TradesUiDto;
import org.kaleta.persistence.entity.Trade;
import org.kaleta.model.FirebaseCompany;
import org.kaleta.service.FirebaseService;
import org.kaleta.service.TradeService;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;

@Path("/trade")
public class TradeEndpoints
{
    @Inject
    TradeService tradeService;
    @Inject
    FirebaseService firebaseService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/")
    public Response getTrades(
            @QueryParam("active") Boolean active,
            @QueryParam("year") String year,
            @QueryParam("companyId") String companyId,
            @QueryParam("currency") String currency,
            @QueryParam("sector") String sector)
    {
        return Endpoint.process(() -> {
            if (companyId != null) Validator.validateUuid(companyId);
            if (currency != null) Validator.validateCurrency(currency);
            if (year != null) Validator.validateYear(year);
            if (sector != null) Validator.validateSector(sector);
        }, () -> {
            List<Trade> trades = tradeService.getTrades(active, companyId, currency, year, year, sector);

            if (active != null && active) {
                for (Trade trade : trades) {
                    if (firebaseService.hasCompany(trade.getTicker())) {
                        FirebaseCompany firebaseCompany = firebaseService.getCompany(trade.getTicker());
                        trade.setSellDate(Date.valueOf(firebaseCompany.getTime().split("T")[0]));
                        trade.setSellPrice(new BigDecimal(firebaseCompany.getPrice()));
                        trade.setSellFees(trade.getPurchaseFees());
                    }
                }
            }
            TradesUiDto dto = TradesUiDto.from(trades);
            dto.setSums(tradeService.computeSums(trades));
            return dto;
        });
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/")
    public Response createTrade(@Valid @NotNull TradeCreateDto tradeCreateDto)
    {
        tradeService.createTrade(tradeCreateDto);
        firebaseService.pushAssets(tradeService.getTrades(true, null, null, null, null, null));
        // TODO push record
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
        // TODO push record
        return Response.noContent().build();
    }
}
