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
import org.kaleta.dto.DividendCreateDto;
import org.kaleta.dto.DividendDto;
import org.kaleta.dto.DividendsUiDto;
import org.kaleta.persistence.entity.Dividend;
import org.kaleta.service.DividendService;

import java.util.List;

@Path("/dividend")
public class DividendResource
{
    @Inject
    DividendService dividendService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/")
    public Response getDividends(
            @QueryParam("year") String year,
            @QueryParam("companyId") String companyId,
            @QueryParam("currency") String currency,
            @QueryParam("sector") String sector)
    {
        return Endpoint.process(() -> {
            if (companyId != null) Validator.validateUuid(companyId);
            if (currency != null) Validator.validateCurrency(currency);
            if (year != null) Validator.validateYear(year);
            if (sector != null) Validator.validateSector(sector);
        }, () -> {
            List<Dividend> dividends = dividendService.getDividends(companyId, currency, year, sector);
            DividendsUiDto dto = DividendsUiDto.from(dividends);
            dto.setSums(dividendService.computeSums(dividends));
            return dto;
        });
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/")
    public Response createDividend(DividendCreateDto dividendCreateDto)
    {
        return Endpoint.process(
            () -> {
                Validator.validatePayload(dividendCreateDto);
                Validator.validateCreateDividendDto(dividendCreateDto);
            },
            () -> {
                Dividend dividend = dividendService.createDividend(dividendCreateDto);
                return Response.status(Response.Status.CREATED).entity(DividendDto.from(dividend)).build();
            });
    }
}
