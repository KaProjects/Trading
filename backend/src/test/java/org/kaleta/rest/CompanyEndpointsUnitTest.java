package org.kaleta.rest;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.kaleta.model.Company;
import org.kaleta.rest.dto.CompanyValuesDto;
import org.kaleta.service.CompanyService;
import org.kaleta.service.DividendService;
import org.kaleta.service.TradeService;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompanyEndpointsUnitTest
{
    @Test
    void companyValuesContainDistinctNaturallySortedTags()
    {
        Company first = company("NVDA", "growth", "ai");
        Company second = company("AMD", "semiconductors", "ai");

        CompanyService companyService = mock(CompanyService.class);
        when(companyService.getCompanies()).thenReturn(new ArrayList<>(List.of(first, second)));
        TradeService tradeService = mock(TradeService.class);
        when(tradeService.getYears()).thenReturn(List.of());

        DividendService dividendService = mock(DividendService.class);
        when(dividendService.getYears()).thenReturn(List.of());

        CompanyEndpoints endpoints = new CompanyEndpoints();
        endpoints.companyService = companyService;
        endpoints.tradeService = tradeService;
        endpoints.dividendService = dividendService;

        Response response = endpoints.getCompanyValues();
        CompanyValuesDto dto = (CompanyValuesDto) response.getEntity();

        assertThat(response.getStatus(), is(200));
        assertThat(dto.getTags(), contains("ai", "growth", "semiconductors"));
    }

    private static Company company(String ticker, String... tags)
    {
        Company company = new Company();
        company.setTicker(ticker);
        company.setTags(List.of(tags));
        return company;
    }
}
