package org.kaleta.rest;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.kaleta.model.Company;
import org.kaleta.model.CompanyAggregates;
import org.kaleta.model.CompanyGroups;
import org.kaleta.model.Trades;
import org.kaleta.persistence.entity.CompanyWithStats;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.Portfolio;
import org.kaleta.persistence.entity.Sector;
import org.kaleta.rest.dto.CompanyCreateDto;
import org.kaleta.rest.dto.CompanyUpdateDto;
import org.kaleta.rest.dto.CompanyValuesDto;
import org.kaleta.rest.validation.ValueOfEnum;
import org.kaleta.service.CompanyService;
import org.kaleta.service.DividendService;
import org.kaleta.service.TradeService;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Path("/company")
public class CompanyEndpoints
{
    @Inject
    CompanyService companyService;
    @Inject
    TradeService tradeService;
    @Inject
    DividendService dividendService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/values")
    public Response getCompanyValues()
    {
        CompanyValuesDto dto = new CompanyValuesDto();

        dto.setCurrencies(List.of(Currency.values()));

        List.of(Sector.values()).forEach(sector -> dto.getSectors().add(new Company.Sector(sector)));
        dto.getSectors().sort(Company.Sector::compareTo);

        List.of(Portfolio.values()).forEach(portfolio -> dto.getPortfolios().add(new Trades.Portfolio(portfolio)));
        dto.getPortfolios().sort(Trades.Portfolio::compareTo);

        dto.setCompanies(companyService.getCompanies());
        dto.getCompanies().sort(Comparator.comparing(Company::getTicker));
        dto.getCompanies().forEach(company -> dto.getTags().addAll(company.getTags()));

        dto.setRecentCompanies(companyService.getRecentCompanies());
        dto.getRecentCompanies().sort(Comparator.comparing(Company::getTicker));

        Set<String> years = new TreeSet<>(Comparator.reverseOrder());
        years.addAll(tradeService.getYears());
        years.addAll(dividendService.getYears());
        dto.setYears(List.copyOf(years));

        return Response.ok(dto).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/")
    public Response getCompaniesWithAggregates(
            @ValueOfEnum(enumClass = CompanyAggregates.Sort.class)
            @QueryParam("sort")
            String sort,
            @ValueOfEnum(enumClass = Currency.class)
            @QueryParam("currency")
            String currency,
            @ValueOfEnum(enumClass = Sector.class)
            @QueryParam("sector")
            String sector
    ) {
        CompanyAggregates dto = companyService.getCompaniesWithAggregates(currency, sector);
        dto.sort((sort == null) ? CompanyAggregates.Sort.TICKER : CompanyAggregates.Sort.valueOf(sort));
        return Response.ok().entity(dto).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/lists")
    public Response getCompanyLists()
    {
        CompanyGroups dto = companyService.getCompanyGroups();

        dto.getWatching().sort(Comparator.comparing(CompanyWithStats::getTicker));
        dto.getDeprecated().sort(Comparator.comparing(CompanyWithStats::getTicker));
        dto.getOwned().sort(Comparator.comparing(CompanyWithStats::getLatestPurchaseDate, Comparator.nullsLast(Comparator.reverseOrder())));
        dto.getUnreported().sort(Comparator.comparing(CompanyWithStats::getLatestUnreportedPeriodEndingMonth));

        return Response.ok(dto).build();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/")
    public Response updateCompany(@NotNull @Valid CompanyUpdateDto dto)
    {
        companyService.update(dto);
        return Response.noContent().build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/")
    public Response createCompany(@NotNull @Valid CompanyCreateDto dto)
    {
        companyService.create(dto);
        return Response.status(Response.Status.CREATED).build();
    }
}
