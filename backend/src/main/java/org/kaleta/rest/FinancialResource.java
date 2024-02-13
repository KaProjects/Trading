package org.kaleta.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.kaleta.dto.FinancialCreateDto;
import org.kaleta.service.FinancialService;

@Path("/financial")
public class FinancialResource
{
    @Inject
    FinancialService financialService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/")
    public Response createFinancial(FinancialCreateDto financialCreateDto)
    {
        return Endpoint.process(
            () -> {
                Validator.validatePayload(financialCreateDto);
                Validator.validateCreateFinancialDto(financialCreateDto);
            },
            () -> {
                financialService.createFinancial(financialCreateDto);
                return Response.status(Response.Status.CREATED).build();
            });
    }
}
