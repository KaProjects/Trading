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
            TradeDto dto = TradeDto.from(trades);
            dto.setSums(tradeService.computeSums(trades));
            return dto;
        });
    }
}
