package org.kaleta.rest;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.kaleta.model.Company;
import org.kaleta.model.CompanyAggregates;
import org.kaleta.model.Trades;
import org.kaleta.client.dto.AlphaVantageTicker;
import org.kaleta.client.dto.PolygonCompanyProfile;
import org.kaleta.persistence.entity.CompanyWithStats;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.Exchange;
import org.kaleta.persistence.entity.Portfolio;
import org.kaleta.persistence.entity.Sector;
import org.kaleta.rest.dto.CompanyCreateDto;
import org.kaleta.rest.dto.CompanyTagCreateDto;
import org.kaleta.rest.dto.CompanyUpdateDto;
import org.kaleta.rest.dto.CompanyValuesDto;
import org.kaleta.rest.validation.ValueOfEnum;
import org.kaleta.rest.validation.ValidId;
import org.kaleta.service.CompanyService;
import org.kaleta.service.DividendService;
import org.kaleta.service.TradeService;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
    public CompanyValuesDto getCompanyValues()
    {
        CompanyValuesDto dto = new CompanyValuesDto();

        dto.setCurrencies(List.of(Currency.values()));

        List.of(Sector.values()).forEach(sector -> dto.getSectors().add(new Company.Sector(sector)));
        dto.getSectors().sort(Company.Sector::compareTo);

        List.of(Exchange.values()).forEach(exchange -> dto.getExchanges().add(new Company.Exchange(exchange)));
        dto.getExchanges().sort(Company.Exchange::compareTo);

        List.of(Portfolio.values()).forEach(portfolio -> dto.getPortfolios().add(new Trades.Portfolio(portfolio)));
        dto.getPortfolios().sort(Trades.Portfolio::compareTo);

        Set<String> years = new TreeSet<>(Comparator.reverseOrder());
        years.addAll(tradeService.getYears());
        years.addAll(dividendService.getYears());
        dto.setYears(List.copyOf(years));

        return dto;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/")
    public CompanyAggregates getCompaniesWithAggregates(
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
        return dto;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/lists")
    public Map<String, List<CompanyWithStats>> getCompaniesByTag()
    {
        Map<String, List<CompanyWithStats>> dto = companyService.getCompaniesByTag();
        dto.forEach((tag, companies) -> companies.sort(switch (tag) {
            case "researched" -> Comparator.comparing(CompanyWithStats::getLatestPeriodEndingMonth,
                    Comparator.nullsLast(Comparator.reverseOrder()));
            case "recent" -> Comparator.comparing(CompanyWithStats::getLatestRecordDate,
                    Comparator.nullsLast(Comparator.reverseOrder()));
            default -> Comparator.comparing(CompanyWithStats::getTicker);
        }));

        return dto;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/alpha-vantage/tickers")
    public List<AlphaVantageTicker> getAlphaVantageTickers(
            @NotNull @org.kaleta.rest.validation.ValidTicker
            @QueryParam("ticker") String ticker,
            @NotNull @ValueOfEnum(enumClass = Currency.class)
            @QueryParam("currency") String currency)
    {
        return companyService.findAlphaVantageTickers(ticker, currency);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/polygon/profile")
    public PolygonCompanyProfile getPolygonCompanyProfile(
            @NotNull @org.kaleta.rest.validation.ValidTicker
            @QueryParam("ticker") String ticker)
    {
        return companyService.getPolygonCompanyProfile(ticker);
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

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/tag")
    public Response addTag(@NotNull @Valid CompanyTagCreateDto dto)
    {
        companyService.addTag(dto);
        return Response.status(Response.Status.CREATED).build();
    }

    @DELETE
    @Path("/{companyId}/tag")
    public Response removeTag(
            @ValidId @PathParam("companyId") Long companyId,
            @NotBlank
            @Size(max = 30)
            @Pattern(regexp = "\\S+", message = "must not contain whitespace")
            @QueryParam("value") String value)
    {
        companyService.removeTag(companyId, value);
        return Response.noContent().build();
    }
}
