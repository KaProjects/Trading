package org.kaleta.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.kaleta.Utils;
import org.kaleta.dto.RecordCreateDto;
import org.kaleta.dto.RecordDto;
import org.kaleta.dto.RecordsUiDto;
import org.kaleta.entity.Company;
import org.kaleta.entity.Record;
import org.kaleta.entity.Trade;
import org.kaleta.service.CompanyService;
import org.kaleta.service.RecordService;
import org.kaleta.service.TradeService;

import java.math.BigDecimal;
import java.util.List;

import static org.kaleta.Utils.format;

@Path("/record")
public class RecordResource
{
    @Inject
    RecordService recordService;
    @Inject
    TradeService tradeService;
    @Inject
    CompanyService companyService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{companyId}")
    public Response getRecords(@PathParam("companyId") String companyId)
    {
        return Endpoint.process(
            () -> Validator.validateUuid(companyId),
            () -> {
                Company company = companyService.getCompany(companyId);

                List<Record> records = recordService.getRecords(company.getId());

                RecordsUiDto dto = new RecordsUiDto(records);
                dto.setCompany(company);

                for (Trade trade : tradeService.getTrades(true, companyId, null, null))
                {
                    RecordsUiDto.Own own = new RecordsUiDto.Own();
                    own.setQuantity(format(trade.getQuantity()));
                    own.setPrice(format(trade.getPurchasePrice()));
                    BigDecimal profit = Utils.computeProfit(trade.getPurchasePrice(), new BigDecimal(dto.getLatestPrice().getValue()));
                    if (profit != null){
                        own.setProfit((profit.compareTo(new BigDecimal("0")) > 0 ? "+" : "") + format(profit) + "%");
                    }
                    dto.getOwns().add(own);
                }
                return dto;
            });
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/")
    public Response updateRecord(RecordDto recordDto)
    {
        return Endpoint.process(
            () -> {
                Validator.validatePayload(recordDto);
                Validator.validateUuid(recordDto.getId());
                Validator.validateUpdateRecordDto(recordDto);
            },
            () -> {
                recordService.updateRecord(recordDto);
                return Response.noContent().build();
            });
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/")
    public Response createRecord(RecordCreateDto recordCreateDto)
    {
        return Endpoint.process(
            () -> {
                Validator.validatePayload(recordCreateDto);
                Validator.validateCreateRecordDto(recordCreateDto);
            },
            () -> {
                Record record = recordService.createRecord(recordCreateDto);
                return Response.status(Response.Status.CREATED).entity(RecordDto.from(record)).build();
            });
    }
}
