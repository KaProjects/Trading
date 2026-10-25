package org.kaleta.rest;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestResponse;
import org.kaleta.model.PeriodEstimates;
import org.kaleta.persistence.entity.Estimate;
import org.kaleta.rest.dto.EstimateCreateDto;
import org.kaleta.rest.dto.EstimateDto;
import org.kaleta.rest.validation.ValidId;
import org.kaleta.service.EstimateService;

import java.util.List;

@Path("/estimate")
public class EstimateEndpoints
{
    @Inject
    EstimateService estimateService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{periodId}/eps")
    public List<EstimateDto> getAllEps(@NotNull @ValidId @PathParam("periodId") Long periodId)
    {
        return estimateService.getAll(periodId, Estimate.EPS);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{periodId}/eps/latest")
    public RestResponse<PeriodEstimates> getLatestEps(@NotNull @ValidId @PathParam("periodId") Long periodId)
    {
        return estimateService.getLatest(periodId, Estimate.EPS)
                .map(RestResponse::ok)
                .orElseGet(RestResponse::noContent);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{periodId}/eps")
    public Response createEps(
            @NotNull @ValidId @PathParam("periodId") Long periodId,
            @Valid @NotNull EstimateCreateDto dto)
    {
        estimateService.create(periodId, Estimate.EPS, dto);
        return Response.status(Response.Status.CREATED).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{periodId}/revenue")
    public List<EstimateDto> getAllRevenue(@NotNull @ValidId @PathParam("periodId") Long periodId)
    {
        return estimateService.getAll(periodId, Estimate.REVENUE);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{periodId}/revenue/latest")
    public RestResponse<PeriodEstimates> getLatestRevenue(@NotNull @ValidId @PathParam("periodId") Long periodId)
    {
        return estimateService.getLatest(periodId, Estimate.REVENUE)
                .map(RestResponse::ok)
                .orElseGet(RestResponse::noContent);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{periodId}/revenue")
    public Response createRevenue(
            @NotNull @ValidId @PathParam("periodId") Long periodId,
            @Valid @NotNull EstimateCreateDto dto)
    {
        estimateService.create(periodId, Estimate.REVENUE, dto);
        return Response.status(Response.Status.CREATED).build();
    }
}
