package org.kaleta.rest;

import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.kaleta.rest.validation.ValidId;
import org.kaleta.service.NewsSentimentService;

@Path("/news-sentiment")
@Produces(MediaType.APPLICATION_JSON)
public class NewsSentimentEndpoints
{
    @Inject
    NewsSentimentService newsSentimentService;

    @GET
    @Path("/company/{companyId}/latest")
    public Response getLatest(@NotNull @ValidId @PathParam("companyId") Long companyId)
    {
        return Response.ok(newsSentimentService.getLatest(companyId)).build();
    }

    @GET
    @Path("/period/{periodId}")
    public Response getByPeriod(@NotNull @ValidId @PathParam("periodId") Long periodId)
    {
        return Response.ok(newsSentimentService.getByPeriod(periodId)).build();
    }
}
