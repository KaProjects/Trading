package org.kaleta.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.kaleta.service.ResearchService;

@Path("/research")
public class ResearchResource
{
    @Inject
    ResearchService researchService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{companyId}")
    public Response getUiDto(@PathParam("companyId") String companyId)
    {
        return Endpoint.process(
                () -> Validator.validateUuid(companyId),
                () -> {
                    return researchService.getDto(companyId);
                });
    }
}
