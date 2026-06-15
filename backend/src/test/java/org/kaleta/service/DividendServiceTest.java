package org.kaleta.service;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kaleta.rest.dto.DividendCreateDto;
import org.kaleta.model.Dividends;
import org.kaleta.model.PeriodFrequency;
import org.kaleta.persistence.api.DividendDao;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.Dividend;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.kaleta.framework.Assert.assertBigDecimals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class DividendServiceTest
{
    @InjectMock
    DividendDao dividendDao;
    @InjectMock
    CompanyService companyService;

    @Inject
    DividendService dividendService;

    @BeforeEach
    void beforeEach()
    {
        reset(dividendDao, companyService);
    }

    @Test
    void getBy()
    {
        Company company1 = entityCompany("company-1", "NVDA", Currency.$);
        Company company2 = entityCompany("company-2", "SHELL", Currency.€);
        org.kaleta.model.Company modelCompany1 = modelCompany("company-1", "NVDA", Currency.$);
        org.kaleta.model.Company modelCompany2 = modelCompany("company-2", "SHELL", Currency.€);

        Dividend dividend1 = dividend("dividend-1", company1, "2024-02-10", "100", "15");
        Dividend dividend2 = dividend("dividend-2", company2, "2024-01-01", "50", "5");
        Dividend dividend3 = dividend("dividend-3", company1, "2023-12-31", "80", "8");

        when(dividendDao.list(null, null, null, null)).thenReturn(List.of(dividend3, dividend1, dividend2));
        when(companyService.from(company1)).thenReturn(modelCompany1);
        when(companyService.from(company2)).thenReturn(modelCompany2);

        Dividends dividends = dividendService.getBy(null, null, null, null);

        assertThat(dividends.getDividends().size(), is(3));

        assertThat(dividends.getDividends().get(0).getId(), is("dividend-1"));
        assertThat(dividends.getDividends().get(0).getCompany().getTicker(), is("NVDA"));
        assertThat(dividends.getDividends().get(0).getCompany().getCurrency(), is(Currency.$));
        assertBigDecimals(dividends.getDividends().get(0).getNet(), new BigDecimal("85"));

        assertThat(dividends.getDividends().get(1).getId(), is("dividend-2"));
        assertThat(dividends.getDividends().get(1).getCompany().getTicker(), is("SHELL"));
        assertThat(dividends.getDividends().get(1).getCompany().getCurrency(), is(Currency.€));
        assertBigDecimals(dividends.getDividends().get(1).getNet(), new BigDecimal("45"));

        assertThat(dividends.getDividends().get(2).getId(), is("dividend-3"));
        assertThat(dividends.getDividends().get(2).getCompany().getTicker(), is("NVDA"));
        assertBigDecimals(dividends.getDividends().get(2).getNet(), new BigDecimal("72"));

        assertThat(dividends.getAggregates().getCompanies(), is(2));
        assertThat(dividends.getAggregates().getCurrencies(), is(2));
        assertBigDecimals(dividends.getAggregates().getDividendSum(), new BigDecimal("230"));
        assertBigDecimals(dividends.getAggregates().getTaxSum(), new BigDecimal("28"));
        assertBigDecimals(dividends.getAggregates().getNetSum(), new BigDecimal("202"));
    }

    @Test
    void getBy_empty()
    {
        when(dividendDao.list(null, null, null, null)).thenReturn(List.of());

        Dividends dividends = dividendService.getBy(null, null, null, null);

        assertThat(dividends.getDividends().size(), is(0));
        assertThat(dividends.getAggregates().getCompanies(), is(0));
        assertThat(dividends.getAggregates().getCurrencies(), is(0));
        assertBigDecimals(dividends.getAggregates().getDividendSum(), BigDecimal.ZERO);
        assertBigDecimals(dividends.getAggregates().getTaxSum(), BigDecimal.ZERO);
        assertBigDecimals(dividends.getAggregates().getNetSum(), BigDecimal.ZERO);
    }

    @Test
    void getByCompany()
    {
        Company company1 = entityCompany("company-1", "NVDA", Currency.$);
        Company company2 = entityCompany("company-2", "SHELL", Currency.€);
        org.kaleta.model.Company modelCompany1 = modelCompany("company-1", "NVDA", Currency.$);
        org.kaleta.model.Company modelCompany2 = modelCompany("company-2", "SHELL", Currency.€);

        Dividend dividend1 = dividend("dividend-1", company1, "2024-02-10", "100", "15");
        Dividend dividend2 = dividend("dividend-2", company2, "2024-01-01", "50", "5");
        Dividend dividend3 = dividend("dividend-3", company1, "2023-12-31", "80", "8");

        when(dividendDao.list(null, null, null, null)).thenReturn(List.of(dividend1, dividend2, dividend3));
        when(companyService.from(company1)).thenReturn(modelCompany1);
        when(companyService.from(company2)).thenReturn(modelCompany2);

        Map<org.kaleta.model.Company, List<Dividends.Dividend>> byCompany = dividendService.getByCompany(null, null, null);

        assertThat(byCompany.size(), is(2));
        assertThat(byCompany.get(modelCompany1).size(), is(2));
        assertThat(byCompany.get(modelCompany1).get(0).getId(), is("dividend-1"));
        assertThat(byCompany.get(modelCompany1).get(1).getId(), is("dividend-3"));
        assertThat(byCompany.get(modelCompany2).size(), is(1));
        assertThat(byCompany.get(modelCompany2).get(0).getId(), is("dividend-2"));
    }

    @Test
    void getByPeriod()
    {
        Company company = entityCompany("company-1", "NVDA", Currency.$);
        org.kaleta.model.Company modelCompany = modelCompany("company-1", "NVDA", Currency.$);

        Dividend dividend1 = dividend("dividend-1", company, "2024-02-10", "100", "15");
        Dividend dividend2 = dividend("dividend-2", company, "2024-04-05", "80", "8");

        when(dividendDao.list(company.getId(), Currency.$.name(), null, null)).thenReturn(List.of(dividend1, dividend2));
        when(companyService.from(company)).thenReturn(modelCompany);

        Map<String, List<Dividends.Dividend>> byPeriod = dividendService.getByPeriod(PeriodFrequency.MONTHLY, company.getId(), Currency.$.name(), null);

        assertThat(byPeriod.size(), is(12));
        assertThat(byPeriod.get("2024-04").size(), is(1));
        assertThat(byPeriod.get("2024-04").get(0).getId(), is("dividend-2"));
        assertThat(byPeriod.get("2024-02").size(), is(1));
        assertThat(byPeriod.get("2024-02").get(0).getId(), is("dividend-1"));
        assertThat(byPeriod.get("2024-01").isEmpty(), is(true));
    }

    @Test
    void createDividend()
    {
        Company company = entityCompany("company-1", "NVDA", Currency.$);
        Dividend stored = dividend("created-dividend", company, "2024-02-10", "100", "15");

        DividendCreateDto dto = new DividendCreateDto();
        dto.setCompanyId(company.getId());
        dto.setDate("2024-02-10");
        dto.setDividend("100");
        dto.setTax("15");

        when(companyService.findEntity(company.getId())).thenReturn(company);
        doAnswer(invocation -> {
            Dividend created = invocation.getArgument(0);
            created.setId("created-dividend");
            return null;
        }).when(dividendDao).create(any(Dividend.class));
        when(dividendDao.get("created-dividend")).thenReturn(stored);

        dividendService.createDividend(dto);

        ArgumentCaptor<Dividend> captor = ArgumentCaptor.forClass(Dividend.class);
        verify(dividendDao).create(captor.capture());

        assertThat(captor.getValue().getCompany().getId(), is(company.getId()));
        assertThat(captor.getValue().getDate(), is(Date.valueOf("2024-02-10")));
        assertBigDecimals(captor.getValue().getDividend(), new BigDecimal("100"));
        assertBigDecimals(captor.getValue().getTax(), new BigDecimal("15"));
    }

    private static Company entityCompany(String id, String ticker, Currency currency)
    {
        Company company = new Company();
        company.setId(id);
        company.setTicker(ticker);
        company.setCurrency(currency);
        return company;
    }

    private static org.kaleta.model.Company modelCompany(String id, String ticker, Currency currency)
    {
        org.kaleta.model.Company company = new org.kaleta.model.Company();
        company.setId(id);
        company.setTicker(ticker);
        company.setCurrency(currency);
        return company;
    }

    private static Dividend dividend(String id, Company company, String date, String dividend, String tax)
    {
        Dividend entity = new Dividend();
        entity.setId(id);
        entity.setCompany(company);
        entity.setDate(Date.valueOf(date));
        entity.setDividend(new BigDecimal(dividend));
        entity.setTax(new BigDecimal(tax));
        return entity;
    }
}
