package org.kaleta.service;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kaleta.client.AlphaVantageClient;
import org.kaleta.client.PolygonClient;
import org.kaleta.client.dto.AlphaVantageTicker;
import org.kaleta.client.dto.PolygonCompanyProfile;
import org.kaleta.framework.Generator;
import org.kaleta.model.CompanyAggregates;
import org.kaleta.persistence.entity.CompanyWithStats;
import org.kaleta.persistence.api.CompanyDao;
import org.kaleta.persistence.api.RecordDao;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.CompanyWithAggregates;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.Sector;
import org.kaleta.rest.dto.CompanyCreateDto;
import org.kaleta.rest.dto.CompanyTagCreateDto;
import org.kaleta.rest.dto.CompanyUpdateDto;
import org.kaleta.rest.error.InvalidInputException;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    AlphaVantageClient alphaVantageClient;
    @InjectMock
    PolygonClient polygonClient;

    @Inject
    CompanyService companyService;

    @BeforeEach
    void beforeEach()
    {
        reset(companyDao, recordDao, alphaVantageClient, polygonClient);
    }

    @Test
    void getCompaniesWithAggregates()
    {
        CompanyWithAggregates company1 = new CompanyWithAggregates();
        company1.setId(1L);
        company1.setTicker("ZZZZ");
        company1.setAlphaVantageTicker("ZZZZ.AMS");
        company1.setCurrency(Currency.$);
        company1.setSector(Sector.SEMICONDUCTORS);
        company1.setTotalTrades(5);
        company1.setActiveTrades(2);
        company1.setDividends(3);
        company1.setRecords(4);
        company1.setPeriods(1);

        CompanyWithAggregates company2 = new CompanyWithAggregates();
        company2.setId(2L);
        company2.setTicker("AAAA");
        company2.setCurrency(Currency.€);
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
        assertThat(first.getId(), is(1L));
        assertThat(first.getTicker(), is("ZZZZ"));
        assertThat(first.getAlphaVantageTicker(), is("ZZZZ.AMS"));
        assertThat(first.getCurrency(), is(Currency.$));
        assertThat(first.getSector().getKey(), is(Sector.SEMICONDUCTORS.toString()));
        assertThat(first.getSector().getName(), is(Sector.SEMICONDUCTORS.getName()));
        assertThat(first.getTotalTrades(), is(5));
        assertThat(first.getActiveTrades(), is(2));
        assertThat(first.getDividends(), is(3));
        assertThat(first.getRecords(), is(4));
        assertThat(first.getPeriods(), is(1));

        CompanyAggregates.Company second = companies.getCompanies().get(1);
        assertThat(second.getId(), is(2L));
        assertThat(second.getTicker(), is("AAAA"));
        assertThat(second.getCurrency(), is(Currency.€));
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
        Company entity = Generator.generateCompany(1L);
        entity.setTicker(" MSFT ");
        entity.setSector(Sector.SOFTWARE);

        when(companyDao.get(entity.getId())).thenReturn(entity);

        org.kaleta.model.Company company = companyService.getCompany(entity.getId());

        assertModelCompany(company, entity);
    }

    @Test
    void findAlphaVantageTickers_filtersByCurrencyAndEquityAndSortsExactTickerFirst()
            throws Exception
    {
        when(alphaVantageClient.searchTickers("ASML")).thenReturn(List.of(
                new AlphaVantageTicker("ASML", "ASML ADR", "Equity", "United States",
                        "USD", new BigDecimal("1.0000")),
                new AlphaVantageTicker("ASME.FRK", "ASML", "Equity", "Frankfurt",
                        "EUR", new BigDecimal("0.6000")),
                new AlphaVantageTicker("ASML.AMS", "ASML", "Equity", "Amsterdam",
                        "EUR", new BigDecimal("0.7000")),
                new AlphaVantageTicker("ASMLX", "ASML Fund", "Mutual Fund", "United States",
                        "EUR", new BigDecimal("0.9000"))));

        List<AlphaVantageTicker> result = companyService.findAlphaVantageTickers(
                "ASML", Currency.€.name());

        assertThat(result.stream().map(AlphaVantageTicker::symbol).toList(),
                is(List.of("ASML.AMS", "ASME.FRK")));
    }

    @Test
    void getPolygonCompanyProfile() throws Exception
    {
        PolygonCompanyProfile profile = new PolygonCompanyProfile(
                "NVIDIA Corporation",
                "Accelerated computing company",
                "https://example.test/nvda.svg",
                "https://www.nvidia.com");
        when(polygonClient.getCompanyProfile("NVDA")).thenReturn(Optional.of(profile));

        assertThat(companyService.getPolygonCompanyProfile("NVDA"), is(profile));
    }

    @Test
    void findEntity()
    {
        Company entity = Generator.generateCompany(1L);

        when(companyDao.get(entity.getId())).thenReturn(entity);

        assertThat(companyService.findEntity(entity.getId()), is(entity));
    }

    @Test
    void findEntity_invalid()
    {
        Long companyId = 404L;
        when(companyDao.get(companyId)).thenThrow(NoResultException.class);

        InvalidInputException exception = assertThrows(InvalidInputException.class, () -> companyService.findEntity(companyId));

        assertThat(exception.getMessage(), is("company with id '" + companyId + "' not found"));
    }

    @Test
    void getCompaniesByTag()
    {
        YearMonth periodCutoff = YearMonth.now().minusYears(1);
        LocalDate recordCutoff = LocalDate.now().minusYears(1);

        CompanyWithStats company1 = new CompanyWithStats();
        company1.setId(1L);
        company1.setTicker("NVDA");
        company1.setCurrency(Currency.$);
        company1.setLatestPurchaseDate(Date.valueOf("2024-07-10"));
        company1.setLatestRecordDate(Date.valueOf(recordCutoff));
        company1.setLatestPeriodEndingMonth(periodCutoff);
        company1.setTags(List.of("ai", "growth", "ai"));

        CompanyWithStats company2 = new CompanyWithStats();
        company2.setId(2L);
        company2.setTicker("XCW");
        company2.setCurrency(Currency.$);
        company2.setLatestRecordDate(Date.valueOf(recordCutoff.minusDays(1)));
        company2.setLatestPeriodEndingMonth(periodCutoff.minusMonths(1));
        company2.setTags(List.of("growth"));

        CompanyWithStats company3 = new CompanyWithStats();
        company3.setId(3L);
        company3.setTicker("TSLA");
        company3.setCurrency(Currency.$);
        company3.setLatestPurchaseDate(Date.valueOf("2024-01-01"));
        company3.setLatestRecordDate(Date.valueOf(recordCutoff.plusMonths(1)));
        company3.setLatestPeriodEndingMonth(periodCutoff.plusMonths(1));
        company3.setTags(List.of("researched"));

        CompanyWithStats company4 = new CompanyWithStats();
        company4.setId(4L);
        company4.setTicker("RR");
        company4.setCurrency(Currency.£);

        when(companyDao.listWithStats()).thenReturn(List.of(company1, company2, company3, company4));

        Map<String, List<CompanyWithStats>> companiesByTag = companyService.getCompaniesByTag();

        assertThat(companiesByTag.keySet().stream().toList(),
                is(List.of("owned", "recent", "researched", "ai", "growth", "all")));
        assertThat(companiesByTag.get("ai"), is(List.of(company1)));
        assertThat(companiesByTag.get("growth"), is(List.of(company1, company2)));
        assertThat(companiesByTag.get("owned"), is(List.of(company1, company3)));
        assertThat(companiesByTag.get("researched"), is(List.of(company1, company3, company3)));
        assertThat(companiesByTag.get("recent"), is(List.of(company1, company3)));
        assertThat(companiesByTag.get("all"), is(List.of(company1, company2, company3, company4)));
    }

    @Test
    void update()
    {
        Company entity = Generator.generateCompany(1L);
        entity.setCurrency(Currency.$);
        entity.setSector(Sector.SEMICONDUCTORS);

        when(companyDao.get(entity.getId())).thenReturn(entity);

        CompanyUpdateDto dto = new CompanyUpdateDto();
        dto.setId(entity.getId());
        dto.setCurrency(Currency.€.name());
        dto.setSector(Sector.SOFTWARE.toString());
        dto.setAlphaVantageTicker("ASML.AMS");
        dto.setName("ASML Holding N.V.");
        dto.setDescription("Semiconductor equipment company");
        dto.setLogoUrl("https://example.test/asml.svg");
        dto.setWebsite("https://www.asml.com");

        companyService.update(dto);

        ArgumentCaptor<Company> captor = ArgumentCaptor.forClass(Company.class);
        verify(companyDao).save(captor.capture());

        assertThat(captor.getValue().getId(), is(entity.getId()));
        assertThat(captor.getValue().getCurrency(), is(Currency.€));
        assertThat(captor.getValue().getSector(), is(Sector.SOFTWARE));
        assertThat(captor.getValue().getAlphaVantageTicker(), is("ASML.AMS"));
        assertThat(captor.getValue().getName(), is("ASML Holding N.V."));
        assertThat(captor.getValue().getDescription(), is("Semiconductor equipment company"));
        assertThat(captor.getValue().getLogoUrl(), is("https://example.test/asml.svg"));
        assertThat(captor.getValue().getWebsite(), is("https://www.asml.com"));
    }

    @Test
    void update_nullSector()
    {
        Company entity = Generator.generateCompany(1L);
        entity.setSector(Sector.SEMICONDUCTORS);

        when(companyDao.get(entity.getId())).thenReturn(entity);

        CompanyUpdateDto dto = new CompanyUpdateDto();
        dto.setId(entity.getId());
        dto.setCurrency(Currency.$.name());
        dto.setSector(null);

        companyService.update(dto);

        ArgumentCaptor<Company> captor = ArgumentCaptor.forClass(Company.class);
        verify(companyDao).save(captor.capture());

        assertThat(captor.getValue().getSector(), is(nullValue()));
        assertThat(captor.getValue().getCurrency(), is(Currency.$));
    }

    @Test
    void update_invalidCompany()
    {
        Long companyId = 404L;
        when(companyDao.get(companyId)).thenThrow(NoResultException.class);

        CompanyUpdateDto dto = new CompanyUpdateDto();
        dto.setId(companyId);
        dto.setCurrency(Currency.$.name());
        dto.setSector(Sector.SEMICONDUCTORS.toString());
        dto.setName("NVIDIA Corporation");
        dto.setDescription("Accelerated computing company");
        dto.setLogoUrl("https://example.test/nvda.svg");
        dto.setWebsite("https://www.nvidia.com");

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
        dto.setSector(Sector.SEMICONDUCTORS.toString());
        dto.setName("NVIDIA Corporation");
        dto.setDescription("Accelerated computing company");
        dto.setLogoUrl("https://example.test/nvda.svg");
        dto.setWebsite("https://www.nvidia.com");

        companyService.create(dto);

        ArgumentCaptor<Company> captor = ArgumentCaptor.forClass(Company.class);
        verify(companyDao).create(captor.capture());

        assertThat(captor.getValue().getTicker(), is("NVDA"));
        assertThat(captor.getValue().getCurrency(), is(Currency.$));
        assertThat(captor.getValue().getSector(), is(Sector.SEMICONDUCTORS));
        assertThat(captor.getValue().getName(), is("NVIDIA Corporation"));
        assertThat(captor.getValue().getDescription(), is("Accelerated computing company"));
        assertThat(captor.getValue().getLogoUrl(), is("https://example.test/nvda.svg"));
        assertThat(captor.getValue().getWebsite(), is("https://www.nvidia.com"));
    }

    @Test
    void create_nullSector()
    {
        when(companyDao.getByTicker("AAPL")).thenThrow(NoResultException.class);

        CompanyCreateDto dto = new CompanyCreateDto();
        dto.setTicker("AAPL");
        dto.setCurrency(Currency.€.name());
        dto.setSector(null);
        dto.setAlphaVantageTicker("AAPL.DEX");

        companyService.create(dto);

        ArgumentCaptor<Company> captor = ArgumentCaptor.forClass(Company.class);
        verify(companyDao).create(captor.capture());

        assertThat(captor.getValue().getTicker(), is("AAPL"));
        assertThat(captor.getValue().getCurrency(), is(Currency.€));
        assertThat(captor.getValue().getSector(), is(nullValue()));
        assertThat(captor.getValue().getAlphaVantageTicker(), is("AAPL.DEX"));
    }

    @Test
    void create_duplicateTicker()
    {
        Company existing = Generator.generateCompany(1L);
        existing.setTicker("NVDA");
        when(companyDao.getByTicker("NVDA")).thenReturn(existing);

        CompanyCreateDto dto = new CompanyCreateDto();
        dto.setTicker("NVDA");
        dto.setCurrency(Currency.$.name());
        dto.setSector(Sector.SEMICONDUCTORS.toString());

        InvalidInputException exception = assertThrows(InvalidInputException.class, () -> companyService.create(dto));

        assertThat(exception.getMessage(), is("company with ticker 'NVDA' already exists!"));
        verify(companyDao, never()).create(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void addTag()
    {
        Company entity = Generator.generateCompany(1L);
        entity.setTags(new java.util.ArrayList<>(List.of("growth")));
        when(companyDao.get(entity.getId())).thenReturn(entity);

        CompanyTagCreateDto dto = new CompanyTagCreateDto();
        dto.setCompanyId(entity.getId());
        dto.setValue("ai");

        companyService.addTag(dto);

        assertThat(entity.getTags(), is(List.of("growth", "ai")));
        verify(companyDao).save(entity);
    }

    @Test
    void addTag_existingTagIsRejected()
    {
        Company entity = Generator.generateCompany(1L);
        entity.setTags(new java.util.ArrayList<>(List.of("growth")));
        when(companyDao.get(entity.getId())).thenReturn(entity);

        CompanyTagCreateDto dto = new CompanyTagCreateDto();
        dto.setCompanyId(entity.getId());
        dto.setValue("GROWTH");

        InvalidInputException exception = assertThrows(InvalidInputException.class, () -> companyService.addTag(dto));

        assertThat(exception.getMessage(), is("tag 'GROWTH' is already assigned to company '" + entity.getTicker() + "'"));
        assertThat(entity.getTags(), is(List.of("growth")));
        verify(companyDao, never()).save(entity);
    }

    @Test
    void addTag_reservedTagIsRejected()
    {
        CompanyTagCreateDto dto = new CompanyTagCreateDto();
        dto.setCompanyId(1L);
        dto.setValue("Owned");

        InvalidInputException exception = assertThrows(InvalidInputException.class, () -> companyService.addTag(dto));

        assertThat(exception.getMessage(), is("tag 'Owned' is reserved"));
        verify(companyDao, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void removeTag()
    {
        Company entity = Generator.generateCompany(1L);
        entity.setTags(new java.util.ArrayList<>(List.of("growth", "income")));
        when(companyDao.get(entity.getId())).thenReturn(entity);

        companyService.removeTag(entity.getId(), "GROWTH");

        assertThat(entity.getTags(), is(List.of("income")));
        verify(companyDao).save(entity);
    }

    @Test
    void removeTag_missingTagIsRejected()
    {
        Company entity = Generator.generateCompany(1L);
        entity.setTags(new java.util.ArrayList<>(List.of("growth")));
        when(companyDao.get(entity.getId())).thenReturn(entity);

        InvalidInputException exception = assertThrows(
                InvalidInputException.class,
                () -> companyService.removeTag(entity.getId(), "income"));

        assertThat(exception.getMessage(), is("tag 'income' is not assigned to company '" + entity.getTicker() + "'"));
        assertThat(entity.getTags(), is(List.of("growth")));
        verify(companyDao, never()).save(entity);
    }

    @Test
    void from()
    {
        Company entity = new Company();
        entity.setId(1L);
        entity.setTicker(" NVDA ");
        entity.setAlphaVantageTicker("NVDA.DEX");
        entity.setCurrency(Currency.$);
        entity.setSector(Sector.SEMICONDUCTORS);
        entity.setName("NVIDIA Corporation");
        entity.setDescription("Accelerated computing company");
        entity.setLogoUrl("https://example.test/nvda.svg");
        entity.setWebsite("https://www.nvidia.com");

        org.kaleta.model.Company company = companyService.from(entity);

        assertThat(company.getId(), is(1L));
        assertThat(company.getTicker(), is("NVDA"));
        assertThat(company.getAlphaVantageTicker(), is("NVDA.DEX"));
        assertThat(company.getName(), is("NVIDIA Corporation"));
        assertThat(company.getDescription(), is("Accelerated computing company"));
        assertThat(company.getLogoUrl(), is("https://example.test/nvda.svg"));
        assertThat(company.getWebsite(), is("https://www.nvidia.com"));
        assertThat(company.getCurrency(), is(Currency.$));
        assertThat(company.getSector().getKey(), is(Sector.SEMICONDUCTORS.toString()));
        assertThat(company.getSector().getName(), is(Sector.SEMICONDUCTORS.getName()));
    }

    @Test
    void from_nullSector()
    {
        Company entity = new Company();
        entity.setId(2L);
        entity.setTicker("AAPL");
        entity.setCurrency(Currency.$);
        entity.setSector(null);

        org.kaleta.model.Company company = companyService.from(entity);

        assertThat(company.getId(), is(2L));
        assertThat(company.getTicker(), is("AAPL"));
        assertThat(company.getCurrency(), is(Currency.$));
        assertThat(company.getSector(), is(nullValue()));
    }

    private static void assertModelCompany(org.kaleta.model.Company actual, Company expected)
    {
        assertThat(actual.getId(), is(expected.getId()));
        assertThat(actual.getTicker(), is(expected.getTicker()));
        assertThat(actual.getAlphaVantageTicker(), is(expected.getAlphaVantageTicker()));
        assertThat(actual.getName(), is(expected.getName()));
        assertThat(actual.getDescription(), is(expected.getDescription()));
        assertThat(actual.getLogoUrl(), is(expected.getLogoUrl()));
        assertThat(actual.getWebsite(), is(expected.getWebsite()));
        assertThat(actual.getCurrency(), is(expected.getCurrency()));
        assertThat(actual.getTags(), is(expected.getTags()));
        if (expected.getSector() == null) {
            assertThat(actual.getSector(), is(nullValue()));
        } else {
            assertThat(actual.getSector().getKey(), is(expected.getSector().toString()));
            assertThat(actual.getSector().getName(), is(expected.getSector().getName()));
        }
    }
}
