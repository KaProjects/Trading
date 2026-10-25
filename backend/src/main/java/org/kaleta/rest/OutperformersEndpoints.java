package org.kaleta.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.kaleta.rest.dto.OutperformersDto;
import org.kaleta.rest.dto.OutperformersPriceSyncDto;
import org.kaleta.service.OutperformersService;

import java.util.List;

@Path("/outperformers")
public class OutperformersEndpoints
{
    @Inject
    OutperformersService outperformersService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/")
    public OutperformersDto get()
    {
        return outperformersService.get();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/prices/sync")
    public OutperformersPriceSyncDto syncPrices(List<String> tickers)
    {
        return outperformersService.syncPrices(tickers);
    }
}
