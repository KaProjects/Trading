package org.kaleta.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.kaleta.dto.CompanyDto;
import org.kaleta.dto.CompanyUiDto;
import org.kaleta.dto.RecordsUiCompanyListsDto;
import org.kaleta.entity.Company;
import org.kaleta.entity.Sort;
import org.kaleta.model.CompanyInfo;
import org.kaleta.service.CompanyService;
import org.kaleta.service.DividendService;
import org.kaleta.service.FinancialService;
import org.kaleta.service.RecordService;
import org.kaleta.service.TradeService;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Path("/company")
public class CompanyResource
{
    @Inject
    CompanyService companyService;
    @Inject
    TradeService tradeService;
    @Inject
    DividendService dividendService;
    @Inject
    RecordService recordService;
    @Inject
    FinancialService financialService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/")
    public Response getCompanies()
    {
        return Endpoint.process(() -> {}, () -> CompanyDto.from(companyService.getCompanies()));
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/aggregate")
    public Response getCompaniesWithAggregates(
            @QueryParam("sort") String sort,
            @QueryParam("currency") String currency,
            @QueryParam("sector") String sector)
    {
        return Endpoint.process(() -> {
            if (sort != null) Validator.validateCompanyAggregateSort(sort);
            if (currency != null) Validator.validateCurrency(currency);
            if (sector != null) Validator.validateSector(sector);
        }, () -> {
            CompanyUiDto dto = new CompanyUiDto();
            Map<String, int[]> tradeAggregates = tradeService.getCompanyAggregates();
            Map<String, int[]> dividendAggregates = dividendService.getCompanyAggregates();
            Map<String, int[]> recordAggregates = recordService.getCompanyAggregates();
            Map<String, int[]> financialAggregates = financialService.getCompanyAggregates();
            for (Company company :  companyService.getCompanies(currency, sector))
            {
                CompanyUiDto.Company companyDto = CompanyUiDto.Company.from(company);

                if (tradeAggregates.containsKey(company.getId())){
                    companyDto.setTotalTrades(tradeAggregates.get(company.getId())[0]);
                    companyDto.setActiveTrades(tradeAggregates.get(company.getId())[1]);
                }
                if (dividendAggregates.containsKey(company.getId())){
                    companyDto.setDividends(dividendAggregates.get(company.getId())[0]);
                }
                if (recordAggregates.containsKey(company.getId())){
                    companyDto.setRecords(recordAggregates.get(company.getId())[0]);
                }
                if (financialAggregates.containsKey(company.getId())){
                    companyDto.setFinancials(financialAggregates.get(company.getId())[0]);
                }
                dto.getCompanies().add(companyDto);
            }
            Sort.CompanyAggregate sortBy = (sort == null) ? Sort.CompanyAggregate.COMPANY : Sort.CompanyAggregate.valueOf(sort);
            switch (sortBy){
                case COMPANY: dto.getCompanies().sort(Comparator.comparing(company -> company.getTicker())); break;
                case CURRENCY: dto.getCompanies().sort(Comparator.comparing(company -> company.getCurrency())); break;
                case WATCHING: dto.getCompanies().sort(Comparator.comparing(company -> !company.getWatching())); break;
                case SECTOR: dto.getCompanies().sort(Comparator.comparing(company -> company.getSector(), Comparator.nullsLast(Comparator.naturalOrder()))); break;
                case ALL_TRADES: dto.getCompanies().sort(Comparator.comparing(company -> -company.getTotalTrades())); break;
                case ACTIVE_TRADES: dto.getCompanies().sort(Comparator.comparing(company -> -company.getActiveTrades())); break;
                case DIVIDENDS: dto.getCompanies().sort(Comparator.comparing(company -> -company.getDividends())); break;
                case RECORDS: dto.getCompanies().sort(Comparator.comparing(company -> -company.getRecords())); break;
                case FINANCIALS: dto.getCompanies().sort(Comparator.comparing(company -> -company.getFinancials())); break;
            }
            return dto;
        });
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/lists")
    public Response getCompanyLists()
    {
        return Endpoint.process(() -> {}, () -> {
            List<CompanyInfo> infos = companyService.getCompaniesInfo();
            RecordsUiCompanyListsDto dto = new RecordsUiCompanyListsDto();
            dto.addWatchingOldestReview(infos);
            dto.addOwnedWithoutStrategy(infos);
            dto.addNotWatching(infos);
            dto.addSectors(infos);
            return dto;
        });
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/")
    public Response updateCompany(CompanyDto companyDto)
    {
        return Endpoint.process(
            () -> {
                Validator.validatePayload(companyDto);
                Validator.validateUuid(companyDto.getId());
            },
            () -> {
                companyService.updateCompany(companyDto);
                return Response.noContent().build();
            });
    }
}
