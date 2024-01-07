package org.kaleta.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.kaleta.dto.TradeDto;
import org.kaleta.service.TradeService;

@Path("/trade")
public class TradeResource
{
    @Inject
    TradeService tradeService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/")
    public Response getAllTrades()
    {
        return Endpoint.process(() -> {}, () -> TradeDto.from(tradeService.getAllTrades()));
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/active")
    public Response getActiveTrades()
    {
        return Endpoint.process(() -> {}, () -> TradeDto.from(tradeService.getActiveTrades()));
    }
}
