package org.kaleta.service;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.MockitoConfig;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.kaleta.firebase.FirebaseStore;
import org.kaleta.model.FirebaseCompany;
import org.kaleta.persistence.entity.CompanyWithStats;
import org.kaleta.rest.dto.ActionableCompanyDto;
import org.kaleta.rest.dto.FirebaseStatsDto;
import org.kaleta.rest.error.InvalidInputException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
public class FirebaseDiffServiceTest
{
    @InjectMock
    @MockitoConfig(convertScopes = true)
    FirebaseService firebaseService;
    @InjectMock
    @MockitoConfig(convertScopes = true)
    FirebaseStore firebaseStore;
    @InjectMock
    CompanyService companyService;
    @InjectMock
    TargetService targetService;

    @Inject
    FirebaseDiffService firebaseDiffService;

    @Test
    void getStats_includesImportableCountsAndEnabledFlag()
    {
        FirebaseCompany nvda = new FirebaseCompany();
        FirebaseCompany amzn = new FirebaseCompany();
        amzn.setEnabled(false);

        when(firebaseService.getAllCompanies()).thenReturn(new FirebaseService.AllCompaniesResult(
                Map.of("NVDA", nvda, "AMZN", amzn), List.of()));
        when(companyService.getAllWithStats()).thenReturn(List.of(company("NVDA")));
        when(targetService.getCompaniesWithImportCandidates()).thenReturn(List.of(
                actionable("NVDA", 1, 12)));

        FirebaseStatsDto result = firebaseDiffService.getStats();

        FirebaseStatsDto.CompanyStats first = result.companies().get(0);
        FirebaseStatsDto.CompanyStats second = result.companies().get(1);

        assertThat(first.ticker(), is("AMZN"));
        assertThat(first.inDatabase(), is(false));
        assertThat(first.enabled(), is(false));
        assertThat(first.importablePeriods(), is(0));
        assertThat(first.importableTargets(), is(0));

        assertThat(second.ticker(), is("NVDA"));
        assertThat(second.inDatabase(), is(true));
        assertThat(second.enabled(), is(true));
        assertThat(second.importablePeriods(), is(1));
        assertThat(second.importableTargets(), is(12));
    }

    @Test
    void updateCompanyEnabled_writesTheFlagOfAnExistingCompany()
    {
        when(firebaseStore.findCompany("NVDA")).thenReturn(Optional.of(new FirebaseCompany()));

        firebaseDiffService.updateCompanyEnabled("NVDA", false);

        verify(firebaseStore).updateCompanyEnabled("NVDA", false);
    }

    @Test
    void updateCompanyEnabled_refusesAnUnknownCompany()
    {
        when(firebaseStore.findCompany("XYZQ")).thenReturn(Optional.empty());

        InvalidInputException exception = assertThrows(InvalidInputException.class,
                () -> firebaseDiffService.updateCompanyEnabled("XYZQ", true));

        assertThat(exception.getMessage(), containsString("XYZQ"));
        verify(firebaseStore, never()).updateCompanyEnabled(any(), anyBoolean());
    }

    private static CompanyWithStats company(String ticker)
    {
        CompanyWithStats company = new CompanyWithStats();
        company.setTicker(ticker);
        return company;
    }

    private static ActionableCompanyDto actionable(String ticker, int periods, int targets)
    {
        ActionableCompanyDto dto = new ActionableCompanyDto();
        dto.setCompany(company(ticker));
        dto.setImportablePeriodsCount(periods);
        dto.setImportableTargetsCount(targets);
        return dto;
    }
}
