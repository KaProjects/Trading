package org.kaleta.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.kaleta.dto.TradeDto;
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
            @QueryParam("active") boolean active,
            @QueryParam("year") String year,
            @QueryParam("company") String company,
            @QueryParam("currency") String currency
    ) {
        return Endpoint.process(() -> {
            if (company != null) ParameterValidator.validateTicker(company);
            if (currency != null) ParameterValidator.validateCurrency(currency);
            if (year != null) ParameterValidator.validateYear(year);
        }, () -> {
            List<Trade> trades = tradeService.getTrades(active, company, currency, year);
            TradeDto dto = TradeDto.from(trades);
            dto.setSums(tradeService.computeSums(trades));
            return dto;
        });
    }
}
