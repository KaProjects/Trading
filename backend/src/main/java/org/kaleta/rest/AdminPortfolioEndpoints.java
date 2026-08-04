package org.kaleta.rest;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.kaleta.rest.dto.PortfolioAssignmentDto;
import org.kaleta.rest.validation.ValidId;
import org.kaleta.service.TradeService;

@Path("/admin/portfolio")
public class AdminPortfolioEndpoints
{
    @Inject
    TradeService tradeService;

    @GET
    @Path("/trades")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTradesWithoutPortfolio(
            @ValidId @QueryParam("companyId") Long companyId
    )
    {
        return Response.ok(tradeService.getTradesWithoutPortfolio(companyId)).build();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response assignPortfolio(@Valid @NotNull PortfolioAssignmentDto dto)
    {
        tradeService.assignPortfolio(dto);
        return Response.noContent().build();
    }
}
