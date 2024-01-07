package org.kaleta.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.kaleta.dto.RecordDto;
import org.kaleta.service.RecordService;

@Path("/record")
public class RecordResource
{
    @Inject
    RecordService recordService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{ticker}")
    public Response getRecords(@PathParam("ticker") String ticker)
    {
        return Endpoint.process(
                () -> ParameterValidator.validateTicker(ticker),
                () -> RecordDto.from(recordService.getRecords(ticker)));
    }
}
