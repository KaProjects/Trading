package org.kaleta.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.kaleta.dto.TradeCreateDto;
import org.kaleta.dto.TradeDto;
import org.kaleta.dto.TradesUiDto;
import org.kaleta.entity.Trade;
import org.kaleta.service.TradeService;

import java.util.List;

@Path("/trade")
public class TradeResource
{
    @Inject
    TradeService tradeService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/")
    public Response getTrades(
            @QueryParam("active") Boolean active,
            @QueryParam("year") String year,
            @QueryParam("companyId") String companyId,
            @QueryParam("currency") String currency
    ) {
        return Endpoint.process(() -> {
            if (companyId != null) Validator.validateUuid(companyId);
            if (currency != null) Validator.validateCurrency(currency);
            if (year != null) Validator.validateYear(year);
        }, () -> {
            List<Trade> trades = tradeService.getTrades(active, companyId, currency, year);
            TradesUiDto dto = TradesUiDto.from(trades);
            dto.setSums(tradeService.computeSums(trades));
            return dto;
        });
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/")
    public Response createRecord(TradeCreateDto tradeCreateDto){
        return Endpoint.process(
                () -> {
                    Validator.validatePayload(tradeCreateDto);
                    Validator.validateCreateTradeDto(tradeCreateDto);
                },
                () -> {
                    Trade trade = tradeService.createTrade(tradeCreateDto);
                    return Response.status(Response.Status.CREATED).entity(TradeDto.from(trade)).build();
                });
    }
}
