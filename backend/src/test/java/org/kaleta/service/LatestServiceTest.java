package org.kaleta.service;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.kaleta.client.FinnhubClient;
import org.kaleta.client.RequestFailureException;
import org.kaleta.client.dto.FinnhubQuote;
import org.kaleta.framework.Generator;
import org.kaleta.persistence.api.LatestDao;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.Latest;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kaleta.framework.Assert.assertBigDecimals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
public class LatestServiceTest
{
    @InjectMock
    LatestDao latestDao;
    @InjectMock
    FinnhubClient finnhubClient;

    @Inject
    LatestService latestService;

    @Test
    void getSyncedFor_syncedUpdate() throws RequestFailureException
    {
        FinnhubQuote finnhubQuote = new FinnhubQuote();
        finnhubQuote.setT(String.valueOf(Instant.now().getEpochSecond()));
        finnhubQuote.setC("1000");
        Company company = Generator.generateCompany();
        company.setCurrency(Currency.$);
        Latest latest = Generator.generateLatest(company);

        when(finnhubClient.quote(company.getTicker())).thenReturn(finnhubQuote);
        when(latestDao.list(company.getId())).thenReturn(List.of(latest));

        Latest actual = latestService.getSyncedFor(company);
        assertThat(actual.getCompany().getId(), is(company.getId()));
        LocalDateTime datetime = Instant.ofEpochSecond(Long.parseLong(finnhubQuote.getT())).atZone(ZoneId.systemDefault()).toLocalDateTime();
        assertThat(actual.getDatetime(), is(datetime));
        assertBigDecimals(actual.getPrice(), new BigDecimal("1000"));

        ArgumentCaptor<Latest> captorCreate = ArgumentCaptor.forClass(Latest.class);
        verify(latestDao, times(0)).create(captorCreate.capture());

        ArgumentCaptor<Latest> captorSave = ArgumentCaptor.forClass(Latest.class);
        verify(latestDao, times(1)).save(captorSave.capture());
    }

    @Test
    void getSyncedFor_syncedCreate() throws RequestFailureException
    {
        FinnhubQuote finnhubQuote = new FinnhubQuote();
        finnhubQuote.setT(String.valueOf(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)));
        finnhubQuote.setC("1000");
        Company company = Generator.generateCompany();
        company.setCurrency(Currency.$);

        when(finnhubClient.quote(company.getTicker())).thenReturn(finnhubQuote);
        when(latestDao.list(company.getId())).thenReturn(new ArrayList<>());

        Latest actual = latestService.getSyncedFor(company);
        assertThat(actual.getCompany().getId(), is(company.getId()));
        LocalDateTime datetime = Instant.ofEpochSecond(Long.parseLong(finnhubQuote.getT())).atZone(ZoneId.systemDefault()).toLocalDateTime();
        assertThat(actual.getDatetime(), is(datetime));
        assertBigDecimals(actual.getPrice(), new BigDecimal("1000"));

        ArgumentCaptor<Latest> captorCreate = ArgumentCaptor.forClass(Latest.class);
        verify(latestDao, times(1)).create(captorCreate.capture());

        ArgumentCaptor<Latest> captorSave = ArgumentCaptor.forClass(Latest.class);
        verify(latestDao, times(0)).save(captorSave.capture());
    }

    @Test
    void getSyncedFor_notSyncedRetrieve() throws RequestFailureException
    {
        Company company = Generator.generateCompany();
        company.setCurrency(Currency.$);
        Latest latest = Generator.generateLatest(company);

        when(finnhubClient.quote(company.getTicker())).thenThrow(RequestFailureException.class);
        when(latestDao.list(company.getId())).thenReturn(List.of(latest));

        Latest actual = latestService.getSyncedFor(company);
        assertThat(actual.getCompany().getId(), is(latest.getCompany().getId()));
        assertThat(actual.getDatetime(), is(latest.getDatetime()));
        assertThat(actual.getPrice(), is(actual.getPrice()));

        ArgumentCaptor<Latest> captorCreate = ArgumentCaptor.forClass(Latest.class);
        verify(latestDao, times(0)).create(captorCreate.capture());

        ArgumentCaptor<Latest> captorSave = ArgumentCaptor.forClass(Latest.class);
        verify(latestDao, times(0)).save(captorSave.capture());
    }

    @Test
    void getSyncedFor_notSyncedNoData() throws RequestFailureException
    {
        Company company = Generator.generateCompany();
        company.setCurrency(Currency.$);

        when(finnhubClient.quote(company.getTicker())).thenThrow(RequestFailureException.class);
        when(latestDao.list(company.getId())).thenReturn(new ArrayList<>());

        Latest actual = latestService.getSyncedFor(company);
        assertThat(actual, is(nullValue()));

        ArgumentCaptor<Latest> captorCreate = ArgumentCaptor.forClass(Latest.class);
        verify(latestDao, times(0)).create(captorCreate.capture());

        ArgumentCaptor<Latest> captorSave = ArgumentCaptor.forClass(Latest.class);
        verify(latestDao, times(0)).save(captorSave.capture());
    }

    @Test
    void getSyncedFor_notSynced_non$() throws RequestFailureException
    {
        Company company = Generator.generateCompany();
        company.setCurrency(Currency.€);

        when(latestDao.list(company.getId())).thenReturn(new ArrayList<>());

        Latest actual = latestService.getSyncedFor(company);
        assertThat(actual, is(nullValue()));

        ArgumentCaptor<String> captorQuote = ArgumentCaptor.forClass(String.class);
        verify(finnhubClient, times(0)).quote(captorQuote.capture());

        ArgumentCaptor<Latest> captorCreate = ArgumentCaptor.forClass(Latest.class);
        verify(latestDao, times(0)).create(captorCreate.capture());

        ArgumentCaptor<Latest> captorSave = ArgumentCaptor.forClass(Latest.class);
        verify(latestDao, times(0)).save(captorSave.capture());
    }

    @Test
    void getSyncedFor_notSynced_emptyData() throws RequestFailureException
    {
        FinnhubQuote finnhubQuote = new FinnhubQuote();
        finnhubQuote.setT("0");
        finnhubQuote.setC("0");

        Company company = Generator.generateCompany();
        company.setCurrency(Currency.$);

        when(finnhubClient.quote(company.getTicker())).thenReturn(finnhubQuote);
        when(latestDao.list(company.getId())).thenReturn(new ArrayList<>());

        Latest actual = latestService.getSyncedFor(company);
        assertThat(actual, is(nullValue()));

        ArgumentCaptor<String> captorQuote = ArgumentCaptor.forClass(String.class);
        verify(finnhubClient, times(1)).quote(captorQuote.capture());

        ArgumentCaptor<Latest> captorCreate = ArgumentCaptor.forClass(Latest.class);
        verify(latestDao, times(0)).create(captorCreate.capture());

        ArgumentCaptor<Latest> captorSave = ArgumentCaptor.forClass(Latest.class);
        verify(latestDao, times(0)).save(captorSave.capture());
    }

    @Test
    void getSyncedFor_invalidData() throws RequestFailureException
    {
        FinnhubQuote finnhubQuote = new FinnhubQuote();
        finnhubQuote.setT(String.valueOf(Instant.now().getEpochSecond()));
        finnhubQuote.setC("1000");
        Company company = Generator.generateCompany();
        company.setCurrency(Currency.$);
        Latest latest1 = Generator.generateLatest(company);
        Latest latest2 = Generator.generateLatest(company);

        when(finnhubClient.quote(company.getTicker())).thenReturn(finnhubQuote);
        when(latestDao.list(company.getId())).thenReturn(List.of(latest1, latest2));

        assertThrows(ServiceFailureException.class, () -> latestService.getSyncedFor(company));

        ArgumentCaptor<Latest> captorCreate = ArgumentCaptor.forClass(Latest.class);
        verify(latestDao, times(0)).create(captorCreate.capture());

        ArgumentCaptor<Latest> captorSave = ArgumentCaptor.forClass(Latest.class);
        verify(latestDao, times(0)).save(captorSave.capture());
    }
}
