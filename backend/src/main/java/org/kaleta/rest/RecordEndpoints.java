package org.kaleta.rest;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
import org.kaleta.dto.RecordsUiDto;
import org.kaleta.model.FirebaseCompany;
import org.kaleta.model.RecordsModel;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.Trade;
import org.kaleta.rest.dto.RecordCreateDto;
import org.kaleta.rest.dto.RecordUpdateDto;
import org.kaleta.service.CompanyService;
import org.kaleta.service.FinancialService;
import org.kaleta.service.FirebaseService;
import org.kaleta.service.RecordService;
import org.kaleta.service.TradeService;

import java.math.BigDecimal;
import java.sql.Date;

import static org.kaleta.Utils.format;

@Path("/record")
public class RecordEndpoints
{
    @Inject
    RecordService recordService;
    @Inject
    TradeService tradeService;
    @Inject
    CompanyService companyService;
    @Inject
    FirebaseService firebaseService;
    @Inject
    FinancialService financialService;

    @Deprecated
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{companyId}")
    public Response getRecords(@PathParam("companyId") String companyId)
    {
        return Endpoint.process(
            () -> Validator.validateUuid(companyId),
            () -> {
                Company company = companyService.getCompany(companyId);
                RecordsModel recordsModel = recordService.getRecordsModel(company.getId());

                RecordsUiDto dto = new RecordsUiDto(company, recordsModel);

                if (firebaseService.hasCompany(company.getTicker())){
                    FirebaseCompany firebaseCompany = firebaseService.getCompany(company.getTicker());
                    Date firebaseLatestDate = Date.valueOf(firebaseCompany.getTime().split("T")[0]);
                    if (Utils.compareDbDates(firebaseLatestDate, recordsModel.getLatestPrice().getDate()) >= 0){
                        dto.setLatestPrice(new RecordsUiDto.Latest(firebaseCompany.getPrice(), format(firebaseLatestDate)));
                    }
                }

                if (dto.getLatest().getPrice() != null && company.getShares() != null){
                    dto.setMarketCap(companyService.computeMarketCap(dto.getLatest().getPrice().getValue(), company.getShares()));
                }

                dto.setFinancialsFrom(financialService.getFinancialsModel(companyId));

                for (Trade trade : tradeService.getTrades(true, companyId, null, null, null, null))
                {
                    RecordsUiDto.Own own = new RecordsUiDto.Own();
                    own.setQuantity(format(trade.getQuantity()));
                    own.setPrice(format(trade.getPurchasePrice()));
                    BigDecimal profit = Utils.computeProfit(trade.getPurchasePrice(), new BigDecimal(dto.getLatest().getPrice().getValue()));
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
    public Response update(@Valid @NotNull RecordUpdateDto recordUpdateDto)
    {
        return Endpoint.process(
            () -> {},
            () -> {
                recordService.update(recordUpdateDto);
                return Response.noContent().build();
            });
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/")
    public Response create(@Valid @NotNull RecordCreateDto recordCreateDto)
    {
        return Endpoint.process(
            () -> {},
            () -> {
                recordService.create(recordCreateDto);
                return Response.status(Response.Status.CREATED).build();
            });
    }
}
