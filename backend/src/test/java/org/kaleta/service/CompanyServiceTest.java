package org.kaleta.service;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.junit.jupiter.api.Test;
import org.kaleta.dao.CompanyDao;
import org.kaleta.dto.CompanyDto;
import org.kaleta.entity.Company;
import org.kaleta.framework.Generator;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;


@QuarkusTest
public class CompanyServiceTest
{
    @InjectMock
    CompanyDao companyDao;
    @Inject
    CompanyService companyService;

    @Test
    void getDtoByCompanyId()
    {
        Company company = Generator.generateCompany();

        when(companyDao.get(company.getId())).thenReturn(company);

        CompanyDto dto = companyService.getDto(company.getId());
        assertThat(dto.getId(), is(company.getId()));
        assertThat(dto.getTicker(), is(company.getTicker()));

        assertThat(dto.getCurrency(), is(company.getCurrency()));
        assertThat(dto.getWatching(), is(company.isWatching()));
        assertThat(dto.getSector().getKey(), is(company.getSector().toString()));
        assertThat(dto.getSector().getName(), is(company.getSector().getName()));
        assertThat(dto.getShares(), is(company.getShares()));
    }

    @Test
    void getDtoByNonExistentCompanyId()
    {
        String nonExistentCompanyId = UUID.randomUUID().toString();
        when(companyDao.get(nonExistentCompanyId)).thenThrow(NoResultException.class);

        try {
            companyService.getDto(nonExistentCompanyId);
        } catch (ServiceFailureException e) {
            assertThat(e.getMessage(), is("company with id '" + nonExistentCompanyId + "' not found"));
        }
    }

    @Test
    void getDtoByCompanyIdNullSector()
    {
        Company company = Generator.generateCompany();
        company.setSector(null);

        when(companyDao.get(company.getId())).thenReturn(company);

        CompanyDto dto = companyService.getDto(company.getId());
        assertThat(dto.getId(), is(company.getId()));
        assertThat(dto.getTicker(), is(company.getTicker()));

        assertThat(dto.getCurrency(), is(company.getCurrency()));
        assertThat(dto.getWatching(), is(company.isWatching()));
        assertThat(dto.getSector(), is(nullValue()));
        assertThat(dto.getShares(), is(company.getShares()));
    }
}
