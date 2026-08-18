package org.kaleta.rest;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.kaleta.rest.dto.DividendCreateDto;
import org.kaleta.rest.dto.DividendImportDto;
import org.kaleta.rest.dto.DividendImportPreviewDto;
import org.kaleta.model.Dividends;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.Sector;
import org.kaleta.rest.validation.ValidId;
import org.kaleta.rest.validation.ValueOfEnum;
import org.kaleta.service.DividendService;
import org.kaleta.service.DividendImportService;

@Path("/dividend")
public class DividendEndpoints
{
    @Inject
    DividendService dividendService;
    @Inject
    DividendImportService dividendImportService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/")
    public Response getDividends(
            @Pattern(regexp = "^\\d\\d\\d\\d$", message = "must match YYYY")
            @QueryParam("year")
            String year,
            @ValidId
            @QueryParam("companyId")
            Long companyId,
            @ValueOfEnum(enumClass = Currency.class)
            @QueryParam("currency")
            String currency,
            @ValueOfEnum(enumClass = Sector.class)
            @QueryParam("sector")
            String sector
    ) {
        Dividends model = dividendService.getBy(companyId, currency, year, sector);
        return Response.ok(model).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/")
    public Response createDividend(@NotNull @Valid DividendCreateDto dividendCreateDto)
    {
        dividendService.createDividend(dividendCreateDto);
        return Response.status(Response.Status.CREATED).build();
    }

    @POST
    @Consumes("text/csv")
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/import/preview")
    public Response previewDividendImport(@NotNull String csv)
    {
        return Response.ok(dividendImportService.preview(csv)).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/import")
    public Response importDividends(@Valid @NotNull DividendImportDto dividendImportDto)
    {
        DividendImportPreviewDto preview = dividendImportService.importDividends(dividendImportDto);
        if (!preview.isValid()) {
            return Response.status(Response.Status.CONFLICT).entity(preview).build();
        }
        return Response.status(Response.Status.CREATED).entity(preview).build();
    }
}
