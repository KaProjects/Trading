package org.kaleta.service;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.junit.jupiter.api.Test;
import org.kaleta.dao.CompanyDao;
import org.kaleta.dao.RecordDao;
import org.kaleta.dto.RecordCreateDto;
import org.kaleta.dto.RecordDto;
import org.kaleta.entity.Company;
import org.kaleta.entity.Record;
import org.kaleta.framework.Generator;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
public class RecordServiceTest
{
    @InjectMock
    CompanyDao companyDao;

    @InjectMock
    RecordDao recordDao;

    @Inject
    RecordService service;

    @Test
    void getByCompanyId() {
        Company company = Generator.generateCompany();
        Record record1 = Generator.generateRecord(company, "2025-10-01");
        Record record2 = Generator.generateRecord(company, "2024-11-21");
        Record record3 = Generator.generateRecord(company, "2025-12-15");

        when(companyDao.get(company.getId())).thenReturn(company);
        when(recordDao.list(company.getId())).thenReturn(new ArrayList<>(List.of(record1, record2, record3)));

        List<Record> records = service.getBy(company.getId());

        assertThat(records.get(0).getId(), is(record3.getId()));
        assertThat(records.get(1).getId(), is(record1.getId()));
        assertThat(records.get(2).getId(), is(record2.getId()));
    }

    @Test
    void update()
    {
        Company company = Generator.generateCompany();
        Record record = Generator.generateRecord(company);

        when(recordDao.get(record.getId())).thenReturn(record);

        RecordDto dto = new RecordDto();
        dto.setId(record.getId());
        dto.setDate("3030-01-01");
        dto.setTitle("new title");
        dto.setPrice("1234.56");
        dto.setPe("66.11");
        dto.setPs("30.4");
        dto.setDy("5.5");
        dto.setTargets("(10-100)~60");
        dto.setContent("a content");
        dto.setStrategy("buy or so");

        service.update(dto);

        ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
        verify(recordDao).save(captor.capture());

        assertThat(captor.getValue().getCompany().getId(), is(company.getId()));
        assertThat(captor.getValue().getDate(), is(Date.valueOf("3030-1-1")));
        assertThat(captor.getValue().getTitle(), is("new title"));
        assertThat(captor.getValue().getPrice(), is(new BigDecimal("1234.56")));
        assertThat(captor.getValue().getPe(), is(new BigDecimal("66.11")));
        assertThat(captor.getValue().getPs(), is(new BigDecimal("30.4")));
        assertThat(captor.getValue().getDy(), is(new  BigDecimal("5.5")));
        assertThat(captor.getValue().getTargets(), is("(10-100)~60"));
        assertThat(captor.getValue().getContent(), is("a content"));
        assertThat(captor.getValue().getStrategy(), is("buy or so"));
    }

    @Test
    void updateNoChange()
    {
        Company company = Generator.generateCompany();
        Record record = Generator.generateRecord(company);

        when(recordDao.get(record.getId())).thenReturn(record);

        RecordDto dto = new RecordDto();
        dto.setId(record.getId());

        service.update(dto);

        ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
        verify(recordDao).save(captor.capture());

        assertThat(captor.getValue().getCompany().getId(), is(company.getId()));
        assertThat(captor.getValue().getDate(), is(record.getDate()));
        assertThat(captor.getValue().getTitle(), is(record.getTitle()));
        assertThat(captor.getValue().getPrice(), is(record.getPrice()));
        assertThat(captor.getValue().getPe(), is(record.getPe()));
        assertThat(captor.getValue().getPs(), is(record.getPs()));
        assertThat(captor.getValue().getDy(), is(record.getDy()));
        assertThat(captor.getValue().getTargets(), is(record.getTargets()));
        assertThat(captor.getValue().getContent(), is(record.getContent()));
        assertThat(captor.getValue().getStrategy(), is(record.getStrategy()));
    }

    @Test
    void updateNullableValues()
    {
        Company company = Generator.generateCompany();
        Record record = Generator.generateRecord(company);

        when(recordDao.get(record.getId())).thenReturn(record);

        RecordDto dto = new RecordDto();
        dto.setId(record.getId());
        dto.setDate("3030-01-01");
        dto.setTitle("new title");
        dto.setPrice("1234.56");
        dto.setPe("");
        dto.setPs("");
        dto.setDy("");
        dto.setTargets("");
        dto.setContent("");
        dto.setStrategy("");

        service.update(dto);

        ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
        verify(recordDao).save(captor.capture());

        assertThat(captor.getValue().getCompany().getId(), is(company.getId()));
        assertThat(captor.getValue().getDate(), is(Date.valueOf("3030-1-1")));
        assertThat(captor.getValue().getTitle(), is("new title"));
        assertThat(captor.getValue().getPrice(), is(new BigDecimal("1234.56")));
        assertThat(captor.getValue().getPe(), is(nullValue()));
        assertThat(captor.getValue().getPs(), is(nullValue()));
        assertThat(captor.getValue().getDy(), is(nullValue()));
        assertThat(captor.getValue().getTargets(), is(""));
        assertThat(captor.getValue().getContent(), is(""));
        assertThat(captor.getValue().getStrategy(), is(""));
    }

    @Test
    void updateNonexistent()
    {
        RecordDto dto = new RecordDto();
        dto.setId(UUID.randomUUID().toString());

        when(recordDao.get(dto.getId())).thenThrow(NoResultException.class);

        try {
            service.update(dto);
        } catch (RuntimeException e) {
            assertThat(e.getClass(), is(ServiceFailureException.class));
            assertThat(e.getMessage(), is("record with id '" + dto.getId() + "' not found"));
        }
        verify(recordDao, times(1)).get(dto.getId());
    }

    @Test
    void create()
    {
        Company company = Generator.generateCompany();

        when(companyDao.get(company.getId())).thenReturn(company);

        RecordCreateDto createDto = new RecordCreateDto();
        createDto.setCompanyId(company.getId());
        createDto.setDate("3030-01-01");
        createDto.setTitle("new title");
        createDto.setPrice("1234.56");
        createDto.setPe("66.11");
        createDto.setPs("30.4");
        createDto.setDy("5.5");

        service.create(createDto);

        ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
        verify(recordDao).create(captor.capture());

        assertThat(captor.getValue().getCompany().getId(), is(company.getId()));
        assertThat(captor.getValue().getDate(), is(Date.valueOf("3030-01-01")));
        assertThat(captor.getValue().getTitle(), is("new title"));
        assertThat(captor.getValue().getPrice(), is(new BigDecimal("1234.56")));
        assertThat(captor.getValue().getPe(), is(new BigDecimal("66.11")));
        assertThat(captor.getValue().getPs(), is(new BigDecimal("30.4")));
        assertThat(captor.getValue().getDy(), is(new  BigDecimal("5.5")));
    }

    @Test
    void createWithNullableValues()
    {
        Company company = Generator.generateCompany();

        when(companyDao.get(company.getId())).thenReturn(company);

        RecordCreateDto createDto = new RecordCreateDto();
        createDto.setCompanyId(company.getId());
        createDto.setDate("3030-01-01");
        createDto.setTitle("new title");
        createDto.setPrice("1234.56");

        service.create(createDto);

        ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
        verify(recordDao).create(captor.capture());

        assertThat(captor.getValue().getCompany().getId(), is(company.getId()));
        assertThat(captor.getValue().getDate(), is(Date.valueOf("3030-01-01")));
        assertThat(captor.getValue().getTitle(), is("new title"));
        assertThat(captor.getValue().getPrice(), is(new BigDecimal("1234.56")));
        assertThat(captor.getValue().getPe(), is(nullValue()));
        assertThat(captor.getValue().getPs(), is(nullValue()));
        assertThat(captor.getValue().getDy(), is(nullValue()));
    }
}
