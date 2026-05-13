package org.kaleta.service;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kaleta.framework.Generator;
import org.kaleta.model.CompanyAggregates;
import org.kaleta.model.CompanyInfo;
import org.kaleta.persistence.api.CompanyDao;
import org.kaleta.persistence.api.RecordDao;
import org.kaleta.persistence.api.TradeDao;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.CompanyWithAggregates;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.Sector;
import org.kaleta.rest.dto.CompanyCreateDto;
import org.kaleta.rest.dto.CompanyUpdateDto;
import org.kaleta.rest.error.InvalidInputException;
import org.mockito.ArgumentCaptor;

import java.sql.Date;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
public class CompanyServiceTest
{
    @InjectMock
    CompanyDao companyDao;
    @InjectMock
    RecordDao recordDao;
    @InjectMock
    TradeDao tradeDao;

    @Inject
    CompanyService companyService;

    @BeforeEach
    void beforeEach()
    {
        reset(companyDao, recordDao, tradeDao);
    }

    @Test
    void getCompanies()
    {
        Company company1 = Generator.generateCompany("company-1");
        company1.setTicker(" NVDA ");
        company1.setCurrency(Currency.$);
        company1.setWatching(true);
        company1.setSector(Sector.SEMICONDUCTORS);

        Company company2 = Generator.generateCompany("company-2");
        company2.setTicker("AAPL");
        company2.setCurrency(Currency.€);
        company2.setWatching(false);
        company2.setSector(null);

        when(companyDao.list()).thenReturn(List.of(company1, company2));

        List<org.kaleta.model.Company> companies = companyService.getCompanies();

        assertThat(companies.size(), is(2));
        assertModelCompany(companies.get(0), company1);
        assertModelCompany(companies.get(1), company2);
    }

    @Test
    void getCompanies_filteredSorted()
    {
        Company company1 = Generator.generateCompany("company-1");
        company1.setTicker("ZZZZ");
        company1.setCurrency(Currency.$);
        company1.setSector(Sector.SEMICONDUCTORS);

        Company company2 = Generator.generateCompany("company-2");
        company2.setTicker("AAAA");
        company2.setCurrency(Currency.$);
        company2.setSector(Sector.SEMICONDUCTORS);

        when(companyDao.list(Currency.$.name(), Sector.SEMICONDUCTORS.toString())).thenReturn(List.of(company1, company2));

        List<org.kaleta.model.Company> companies = companyService.getCompanies(Currency.$.name(), Sector.SEMICONDUCTORS.toString());

        assertThat(companies.size(), is(2));
        assertThat(companies.get(0).getId(), is(company2.getId()));
        assertThat(companies.get(1).getId(), is(company1.getId()));
    }

    @Test
    void getCompaniesWithAggregates()
    {
        CompanyWithAggregates company1 = new CompanyWithAggregates();
        company1.setId("company-1");
        company1.setTicker("ZZZZ");
        company1.setCurrency(Currency.$);
        company1.setWatching(true);
        company1.setSector(Sector.SEMICONDUCTORS);
        company1.setTotalTrades(5);
        company1.setActiveTrades(2);
        company1.setDividends(3);
        company1.setRecords(4);
        company1.setPeriods(1);

        CompanyWithAggregates company2 = new CompanyWithAggregates();
        company2.setId("company-2");
        company2.setTicker("AAAA");
        company2.setCurrency(Currency.€);
        company2.setWatching(false);
        company2.setSector(null);
        company2.setTotalTrades(0);
        company2.setActiveTrades(0);
        company2.setDividends(1);
        company2.setRecords(2);
        company2.setPeriods(3);

        when(companyDao.listWithAggregates(Currency.$.name(), Sector.SEMICONDUCTORS.toString()))
                .thenReturn(List.of(company1, company2));

        CompanyAggregates companies = companyService.getCompaniesWithAggregates(Currency.$.name(), Sector.SEMICONDUCTORS.toString());

        assertThat(companies.getSorts().size(), is(CompanyAggregates.Sort.values().length));
        assertThat(companies.getCompanies().size(), is(2));

        CompanyAggregates.Company first = companies.getCompanies().get(0);
        assertThat(first.getId(), is("company-1"));
        assertThat(first.getTicker(), is("ZZZZ"));
        assertThat(first.getCurrency(), is(Currency.$));
        assertThat(first.getWatching(), is(true));
        assertThat(first.getSector().getKey(), is(Sector.SEMICONDUCTORS.toString()));
        assertThat(first.getSector().getName(), is(Sector.SEMICONDUCTORS.getName()));
        assertThat(first.getTotalTrades(), is(5));
        assertThat(first.getActiveTrades(), is(2));
        assertThat(first.getDividends(), is(3));
        assertThat(first.getRecords(), is(4));
        assertThat(first.getPeriods(), is(1));

        CompanyAggregates.Company second = companies.getCompanies().get(1);
        assertThat(second.getId(), is("company-2"));
        assertThat(second.getTicker(), is("AAAA"));
        assertThat(second.getCurrency(), is(Currency.€));
        assertThat(second.getWatching(), is(false));
        assertThat(second.getSector(), is(nullValue()));
        assertThat(second.getTotalTrades(), is(0));
        assertThat(second.getActiveTrades(), is(0));
        assertThat(second.getDividends(), is(1));
        assertThat(second.getRecords(), is(2));
        assertThat(second.getPeriods(), is(3));
    }

    @Test
    void getCompany()
    {
        Company entity = Generator.generateCompany("company-1");
        entity.setTicker(" MSFT ");
        entity.setSector(Sector.SOFTWARE);

        when(companyDao.get(entity.getId())).thenReturn(entity);

        org.kaleta.model.Company company = companyService.getCompany(entity.getId());

        assertModelCompany(company, entity);
    }

    @Test
    void findEntity()
    {
        Company entity = Generator.generateCompany("company-1");

        when(companyDao.get(entity.getId())).thenReturn(entity);

        assertThat(companyService.findEntity(entity.getId()), is(entity));
    }

    @Test
    void findEntity_invalid()
    {
        String companyId = "company-404";
        when(companyDao.get(companyId)).thenThrow(NoResultException.class);

        InvalidInputException exception = assertThrows(InvalidInputException.class, () -> companyService.findEntity(companyId));

        assertThat(exception.getMessage(), is("company with id '" + companyId + "' not found"));
    }

    @Test
    void getCompaniesInfo()
    {
        Company company1 = Generator.generateCompany("company-1");
        company1.setTicker("NVDA");
        company1.setWatching(true);
        company1.setSector(Sector.SEMICONDUCTORS);

        Company company2 = Generator.generateCompany("company-2");
        company2.setTicker("AAPL");
        company2.setWatching(false);
        company2.setSector(Sector.SOFTWARE);

        CompanyInfo latestRecord = new CompanyInfo();
        latestRecord.setId(company1.getId());
        latestRecord.setLatestReviewDate(Date.valueOf("2024-05-20"));

        CompanyInfo latestStrategy = new CompanyInfo();
        latestStrategy.setId(company2.getId());
        latestStrategy.setLatestStrategyDate(Date.valueOf("2024-06-15"));

        CompanyInfo latestPurchase = new CompanyInfo();
        latestPurchase.setId(company1.getId());
        latestPurchase.setLatestPurchaseDate(Date.valueOf("2024-07-10"));

        CompanyInfo unmatched = new CompanyInfo();
        unmatched.setId("company-999");
        unmatched.setLatestReviewDate(Date.valueOf("2020-01-01"));

        when(companyDao.list()).thenReturn(List.of(company1, company2));
        when(recordDao.latestRecords()).thenReturn(List.of(latestRecord, unmatched));
        when(recordDao.latestStrategy()).thenReturn(List.of(latestStrategy));
        when(tradeDao.latestPurchase()).thenReturn(List.of(latestPurchase));

        List<CompanyInfo> companiesInfo = companyService.getCompaniesInfo();

        assertThat(companiesInfo.size(), is(2));

        assertThat(companiesInfo.get(0).getId(), is(company1.getId()));
        assertThat(companiesInfo.get(0).getTicker(), is(company1.getTicker()));
        assertThat(companiesInfo.get(0).isWatching(), is(company1.isWatching()));
        assertThat(companiesInfo.get(0).getSector(), is(company1.getSector()));
        assertThat(companiesInfo.get(0).getLatestReviewDate(), is(Date.valueOf("2024-05-20")));
        assertThat(companiesInfo.get(0).getLatestPurchaseDate(), is(Date.valueOf("2024-07-10")));
        assertThat(companiesInfo.get(0).getLatestStrategyDate(), is(nullValue()));

        assertThat(companiesInfo.get(1).getId(), is(company2.getId()));
        assertThat(companiesInfo.get(1).getTicker(), is(company2.getTicker()));
        assertThat(companiesInfo.get(1).isWatching(), is(company2.isWatching()));
        assertThat(companiesInfo.get(1).getSector(), is(company2.getSector()));
        assertThat(companiesInfo.get(1).getLatestReviewDate(), is(nullValue()));
        assertThat(companiesInfo.get(1).getLatestPurchaseDate(), is(nullValue()));
        assertThat(companiesInfo.get(1).getLatestStrategyDate(), is(Date.valueOf("2024-06-15")));
    }

    @Test
    void update()
    {
        Company entity = Generator.generateCompany("company-1");
        entity.setCurrency(Currency.$);
        entity.setWatching(false);
        entity.setSector(Sector.SEMICONDUCTORS);

        when(companyDao.get(entity.getId())).thenReturn(entity);

        CompanyUpdateDto dto = new CompanyUpdateDto();
        dto.setId(entity.getId());
        dto.setCurrency(Currency.€.name());
        dto.setWatching("true");
        dto.setSector(Sector.SOFTWARE.toString());

        companyService.update(dto);

        ArgumentCaptor<Company> captor = ArgumentCaptor.forClass(Company.class);
        verify(companyDao).save(captor.capture());

        assertThat(captor.getValue().getId(), is(entity.getId()));
        assertThat(captor.getValue().getCurrency(), is(Currency.€));
        assertThat(captor.getValue().isWatching(), is(true));
        assertThat(captor.getValue().getSector(), is(Sector.SOFTWARE));
    }

    @Test
    void update_nullSector()
    {
        Company entity = Generator.generateCompany("company-1");
        entity.setSector(Sector.SEMICONDUCTORS);

        when(companyDao.get(entity.getId())).thenReturn(entity);

        CompanyUpdateDto dto = new CompanyUpdateDto();
        dto.setId(entity.getId());
        dto.setCurrency(Currency.$.name());
        dto.setWatching("false");
        dto.setSector(null);

        companyService.update(dto);

        ArgumentCaptor<Company> captor = ArgumentCaptor.forClass(Company.class);
        verify(companyDao).save(captor.capture());

        assertThat(captor.getValue().getSector(), is(nullValue()));
        assertThat(captor.getValue().isWatching(), is(false));
        assertThat(captor.getValue().getCurrency(), is(Currency.$));
    }

    @Test
    void update_invalidCompany()
    {
        String companyId = "company-404";
        when(companyDao.get(companyId)).thenThrow(NoResultException.class);

        CompanyUpdateDto dto = new CompanyUpdateDto();
        dto.setId(companyId);
        dto.setCurrency(Currency.$.name());
        dto.setWatching("true");
        dto.setSector(Sector.SEMICONDUCTORS.toString());

        InvalidInputException exception = assertThrows(InvalidInputException.class, () -> companyService.update(dto));

        assertThat(exception.getMessage(), is("company with id '" + companyId + "' not found"));
        verify(companyDao, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void create()
    {
        when(companyDao.getByTicker("NVDA")).thenThrow(NoResultException.class);

        CompanyCreateDto dto = new CompanyCreateDto();
        dto.setTicker("NVDA");
        dto.setCurrency(Currency.$.name());
        dto.setWatching("true");
        dto.setSector(Sector.SEMICONDUCTORS.toString());

        companyService.create(dto);

        ArgumentCaptor<Company> captor = ArgumentCaptor.forClass(Company.class);
        verify(companyDao).create(captor.capture());

        assertThat(captor.getValue().getTicker(), is("NVDA"));
        assertThat(captor.getValue().getCurrency(), is(Currency.$));
        assertThat(captor.getValue().isWatching(), is(true));
        assertThat(captor.getValue().getSector(), is(Sector.SEMICONDUCTORS));
    }

    @Test
    void create_nullSector()
    {
        when(companyDao.getByTicker("AAPL")).thenThrow(NoResultException.class);

        CompanyCreateDto dto = new CompanyCreateDto();
        dto.setTicker("AAPL");
        dto.setCurrency(Currency.€.name());
        dto.setWatching("false");
        dto.setSector(null);

        companyService.create(dto);

        ArgumentCaptor<Company> captor = ArgumentCaptor.forClass(Company.class);
        verify(companyDao).create(captor.capture());

        assertThat(captor.getValue().getTicker(), is("AAPL"));
        assertThat(captor.getValue().getCurrency(), is(Currency.€));
        assertThat(captor.getValue().isWatching(), is(false));
        assertThat(captor.getValue().getSector(), is(nullValue()));
    }

    @Test
    void create_duplicateTicker()
    {
        Company existing = Generator.generateCompany("company-1");
        existing.setTicker("NVDA");
        when(companyDao.getByTicker("NVDA")).thenReturn(existing);

        CompanyCreateDto dto = new CompanyCreateDto();
        dto.setTicker("NVDA");
        dto.setCurrency(Currency.$.name());
        dto.setWatching("true");
        dto.setSector(Sector.SEMICONDUCTORS.toString());

        InvalidInputException exception = assertThrows(InvalidInputException.class, () -> companyService.create(dto));

        assertThat(exception.getMessage(), is("company with ticker 'NVDA' already exists!"));
        verify(companyDao, never()).create(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void from()
    {
        Company entity = new Company();
        entity.setId("company-1");
        entity.setTicker(" NVDA ");
        entity.setCurrency(Currency.$);
        entity.setWatching(true);
        entity.setSector(Sector.SEMICONDUCTORS);

        org.kaleta.model.Company company = companyService.from(entity);

        assertThat(company.getId(), is("company-1"));
        assertThat(company.getTicker(), is("NVDA"));
        assertThat(company.getCurrency(), is(Currency.$));
        assertThat(company.getWatching(), is(true));
        assertThat(company.getSector().getKey(), is(Sector.SEMICONDUCTORS.toString()));
        assertThat(company.getSector().getName(), is(Sector.SEMICONDUCTORS.getName()));
    }

    @Test
    void from_nullSector()
    {
        Company entity = new Company();
        entity.setId("company-2");
        entity.setTicker("AAPL");
        entity.setCurrency(Currency.$);
        entity.setWatching(false);
        entity.setSector(null);

        org.kaleta.model.Company company = companyService.from(entity);

        assertThat(company.getId(), is("company-2"));
        assertThat(company.getTicker(), is("AAPL"));
        assertThat(company.getCurrency(), is(Currency.$));
        assertThat(company.getWatching(), is(false));
        assertThat(company.getSector(), is(nullValue()));
    }

    private static void assertModelCompany(org.kaleta.model.Company actual, Company expected)
    {
        assertThat(actual.getId(), is(expected.getId()));
        assertThat(actual.getTicker(), is(expected.getTicker()));
        assertThat(actual.getCurrency(), is(expected.getCurrency()));
        assertThat(actual.getWatching(), is(expected.isWatching()));
        if (expected.getSector() == null) {
            assertThat(actual.getSector(), is(nullValue()));
        } else {
            assertThat(actual.getSector().getKey(), is(expected.getSector().toString()));
            assertThat(actual.getSector().getName(), is(expected.getSector().getName()));
        }
    }
}
