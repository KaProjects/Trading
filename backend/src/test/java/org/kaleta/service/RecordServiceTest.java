package org.kaleta.service;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;
import org.kaleta.Utils;
import org.kaleta.framework.Generator;
import org.kaleta.persistence.api.RecordDao;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.Record;
import org.kaleta.rest.dto.RecordCreateDto;
import org.kaleta.rest.dto.RecordUpdateDto;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
public class RecordServiceTest
{
    @InjectMock
    CompanyService companyService;
    @InjectMock
    RecordDao recordDao;

    @Inject
    RecordService recordService;

    @Test
    void create()
    {
        String validD = "3030-01-01";
        String validT = "new title";
        String validP = Generator.randomBigDecimal(999999, 4).toString();
        String validPs = Generator.randomBigDecimal(9999, 2).toString();
        String validPg = Generator.randomBigDecimal(9999, 2).toString();
        String validPo = Generator.randomBigDecimal(9999, 2).toString();
        String validPe = Generator.randomBigDecimal(9999, 2).toString();
        String validDy = Generator.randomBigDecimal(999, 2).toString();
        String validQ = Generator.randomBigDecimal(9999, 4).toString();
        String validPp = Generator.randomBigDecimal(999999, 4).toString();

        createAndAssertRecord(validD, validT, validP, validPs, validPg, validPo, validPe, validDy, validQ, validPp, null);
        createAndAssertRecord(validD, validT, validP, null, null, null, null, null, null, null, null);

        createAndAssertRecord("", validT, validP, validPs, validPg, validPo, validPe, validDy, validQ, validPp, IllegalArgumentException.class);
        createAndAssertRecord("abcd", validT, validP, validPs, validPg, validPo, validPe, validDy, validQ, validPp, IllegalArgumentException.class);
        createAndAssertRecord("2020-30-01", validT, validP, validPs, validPg, validPo, validPe, validDy, validQ, validPp, IllegalArgumentException.class);
        createAndAssertRecord("2020-12-40", validT, validP, validPs, validPg, validPo, validPe, validDy, validQ, validPp, IllegalArgumentException.class);

        createAndAssertRecord(validD, validT, "", validPs, validPg, validPo, validPe, validDy, validQ, validPp, IllegalArgumentException.class);
        createAndAssertRecord(validD, validT, "x", validPs, validPg, validPo, validPe, validDy, validQ, validPp, IllegalArgumentException.class);

        createAndAssertRecord(validD, validT, validP, "", validPg, validPo, validPe, validDy, validQ, validPp, IllegalArgumentException.class);
        createAndAssertRecord(validD, validT, validP, "x", validPg, validPo, validPe, validDy, validQ, validPp, IllegalArgumentException.class);

        createAndAssertRecord(validD, validT, validP, validPs, "", validPo, validPe, validDy, validQ, validPp, IllegalArgumentException.class);
        createAndAssertRecord(validD, validT, validP, validPs, "x", validPo, validPe, validDy, validQ, validPp, IllegalArgumentException.class);

        createAndAssertRecord(validD, validT, validP, validPs, validPg, "", validPe, validDy, validQ, validPp, IllegalArgumentException.class);
        createAndAssertRecord(validD, validT, validP, validPs, validPg, "x", validPe, validDy, validQ, validPp, IllegalArgumentException.class);

        createAndAssertRecord(validD, validT, validP, validPs, validPg, validPo, "", validDy, validQ, validPp, IllegalArgumentException.class);
        createAndAssertRecord(validD, validT, validP, validPs, validPg, validPo, "x", validDy, validQ, validPp, IllegalArgumentException.class);

        createAndAssertRecord(validD, validT, validP, validPs, validPg, validPo, validPe, "", validQ, validPp, IllegalArgumentException.class);
        createAndAssertRecord(validD, validT, validP, validPs, validPg, validPo, validPe, "x", validQ, validPp, IllegalArgumentException.class);

        createAndAssertRecord(validD, validT, validP, validPs, validPg, validPo, validPe, validDy, "", validPp, IllegalArgumentException.class);
        createAndAssertRecord(validD, validT, validP, validPs, validPg, validPo, validPe, validDy, "x", validPp, IllegalArgumentException.class);

        createAndAssertRecord(validD, validT, validP, validPs, validPg, validPo, validPe, validDy, validQ, "", IllegalArgumentException.class);
        createAndAssertRecord(validD, validT, validP, validPs, validPg, validPo, validPe, validDy, validQ, "x", IllegalArgumentException.class);
    }

    @Test
    void update()
    {
        Company company = Generator.generateCompany();
        Record record = Generator.generateRecord(company, "2020-01-01");

        when(recordDao.get(record.getId())).thenReturn(record);
        when(recordDao.get(null)).thenThrow(NoResultException.class);

        RecordUpdateDto dto = new RecordUpdateDto();

        updateAndAssertRecord(dto, record, ServiceFailureException.class);

        dto.setId(record.getId());
        updateAndAssertRecord(dto, record, null);

        dto.setTitle("title");
        updateAndAssertRecord(dto, record, null);

        dto.setContent("content");
        updateAndAssertRecord(dto, record, null);

        dto.setStrategy("strategy");
        updateAndAssertRecord(dto, record, null);
    }

    @Test
    void getBy() {
        Company company = Generator.generateCompany();
        Record record1 = Generator.generateRecord(company, "2025-10-01");
        Record record2 = Generator.generateRecord(company, "2024-11-21");
        Record record3 = Generator.generateRecord(company, "2025-12-15");

        when(companyService.getCompany(company.getId())).thenReturn(company);
        when(recordDao.list(company.getId())).thenReturn(new ArrayList<>(List.of(record1, record2, record3)));

        List<Record> records = recordService.getBy(company.getId());

        assertThat(records.get(0).getId(), is(record3.getId()));
        assertThat(records.get(1).getId(), is(record1.getId()));
        assertThat(records.get(2).getId(), is(record2.getId()));
    }

    @Test
    void delete()
    {
        Company company = Generator.generateCompany();
        Record record = Generator.generateRecord(company, "2020-01-01");

        String randomId = UUID.randomUUID().toString();

        when(recordDao.get(record.getId())).thenReturn(record);
        when(recordDao.get(randomId)).thenThrow(NoResultException.class);

        assertThrows(ServiceFailureException.class, () -> recordService.delete(randomId));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(recordDao, times(0)).delete(captor.capture());

        recordService.delete(record.getId());
        verify(recordDao).delete(captor.capture());

        assertThat(captor.getValue(), is(record.getId()));
    }

    private void updateAndAssertRecord(RecordUpdateDto dto, Record record, Class<? extends Exception> expectedException)
    {
        if (expectedException == null)
        {
            recordService.update(dto);

            ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
            verify(recordDao).save(captor.capture());

            assertThat(captor.getValue().getCompany().getId(), is(record.getCompany().getId()));

            assertThat(captor.getValue().getTitle(), (dto.getTitle() == null) ? is(record.getTitle()) : is(dto.getTitle()));
            assertThat(captor.getValue().getContent(), (dto.getContent() == null) ? is(record.getContent()) : is(dto.getContent()));
            assertThat(captor.getValue().getStrategy(), (dto.getStrategy() == null) ? is(record.getStrategy()) : is(dto.getStrategy()));


            clearInvocations(recordDao);
        } else {
            assertThrows(expectedException, () -> recordService.update(dto));
        }
    }

    private void createAndAssertRecord(String date, String title, String price,
                                       String ps, String pg, String po, String pe, String dy,
                                       String q, String pp,
                                       Class<? extends Exception> expectedException)
    {
        Company company = Generator.generateCompany();
        when(companyService.getCompany(company.getId())).thenReturn(company);

        RecordCreateDto dto = new RecordCreateDto();
        dto.setCompanyId(company.getId());
        dto.setDate(date);
        dto.setPrice(price);
        dto.setTitle(title);
        dto.setPriceToRevenues(ps);
        dto.setPriceToGrossProfit(pg);
        dto.setPriceToOperatingIncome(po);
        dto.setPriceToNetIncome(pe);
        dto.setDividendYield(dy);
        dto.setSumAssetQuantity(q);
        dto.setAvgAssetPrice(pp);

        if (expectedException == null) {
            recordService.create(dto);

            ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
            verify(recordDao).create(captor.capture());

            assertThat(captor.getValue().getCompany().getId(), is(company.getId()));
            assertThat(captor.getValue().getDate(), is(Date.valueOf(date)));
            assertThat(captor.getValue().getPrice(), comparesEqualTo(Utils.createNullableBigDecimal(price)));
            assertThat(captor.getValue().getTitle(), is(title));

            assertThat(captor.getValue().getPriceToRevenues(), nullableComparesEqualTo(ps));
            assertThat(captor.getValue().getPriceToGrossProfit(), nullableComparesEqualTo(pg));
            assertThat(captor.getValue().getPriceToOperatingIncome(), nullableComparesEqualTo(po));
            assertThat(captor.getValue().getPriceToNetIncome(), nullableComparesEqualTo(pe));

            assertThat(captor.getValue().getDividendYield(), nullableComparesEqualTo(dy));

            assertThat(captor.getValue().getSumAssetQuantity(), nullableComparesEqualTo(q));
            assertThat(captor.getValue().getAvgAssetPrice(), nullableComparesEqualTo(pp));

            clearInvocations(recordDao);
        } else {
            assertThrows(expectedException, () -> recordService.create(dto));
        }
    }

    private Matcher<? super java.math.BigDecimal> nullableComparesEqualTo(final String bigDecimal)
    {
        if (bigDecimal == null){
            return is(nullValue());
        } else {
            return comparesEqualTo(new  BigDecimal(bigDecimal));
        }
    }
}
