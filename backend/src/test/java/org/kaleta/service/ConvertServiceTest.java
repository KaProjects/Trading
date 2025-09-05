package org.kaleta.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Date;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@QuarkusTest
public class ConvertServiceTest
{

    @Inject
    ConvertService convertService;


    @Test
    public void formatMonth()
    {
        assertThat(convertService.formatMonth(null), is(""));
        try
        {
            convertService.formatMonth("");
        }
        catch (ServiceFailureException e)
        {
            assertThat(e.getMessage(), is("Invalid Month: '', expected YYMM or null"));
        }
        try
        {
            convertService.formatMonth("123x");
        }
        catch (ServiceFailureException e)
        {
            assertThat(e.getMessage(), is("Invalid Month: '123x', expected YYMM or null"));
        }
        try
        {
            convertService.formatMonth("123");
        }
        catch (ServiceFailureException e)
        {
            assertThat(e.getMessage(), is("Invalid Month: '123', expected YYMM or null"));
        }
        try
        {
            convertService.formatMonth("12345");
        }
        catch (ServiceFailureException e)
        {
            assertThat(e.getMessage(), is("Invalid Month: '12345', expected YYMM or null"));
        }
        assertThat(convertService.formatMonth("2512"), is("12/2025"));
    }

    @Test
    public void formatNoDecimal()
    {
        assertThat(convertService.formatNoDecimal(null), is(""));
        assertThat(convertService.formatNoDecimal(new BigDecimal("1.4")), is("1"));
        assertThat(convertService.formatNoDecimal(new BigDecimal("1.5")), is("2"));
    }

    @Test
    public void formatMillions()
    {
        assertThat(convertService.formatMillions(null), is(""));
        assertThat(convertService.formatMillions(new BigDecimal("1114")), is("1.11B"));
        assertThat(convertService.formatMillions(new BigDecimal("1115")), is("1.12B"));
        assertThat(convertService.formatMillions(new BigDecimal("1000")), is("1B"));
        assertThat(convertService.formatMillions(new BigDecimal("999")), is("999M"));
        assertThat(convertService.formatMillions(new BigDecimal("0")), is("0M"));
        assertThat(convertService.formatMillions(new BigDecimal("-1")), is("-1M"));
        assertThat(convertService.formatMillions(new BigDecimal("-999")), is("-999M"));
        assertThat(convertService.formatMillions(new BigDecimal("-1000")), is("-1B"));
        assertThat(convertService.formatMillions(new BigDecimal("-1114")), is("-1.11B"));
        assertThat(convertService.formatMillions(new BigDecimal("-1115")), is("-1.12B"));
    }

    @Test
    public void formatBigDecimal()
    {
        assertThat(convertService.format((BigDecimal) null), is(""));
        assertThat(convertService.format(new BigDecimal("111.10000")), is("111.1"));
    }

    @Test
    public void formatDate()
    {
        assertThat(convertService.format((Date) null), is(""));
        assertThat(convertService.format(Date.valueOf("3030-1-12")), is("12.01.3030"));
    }

    @Test
    public void parseDate()
    {
        try
        {
            convertService.parseDate(null);
        } catch (ServiceFailureException e) {
            assertThat(e.getMessage(), is("invalid date format 'null' not YYYY-MM-DD"));
        }
        try
        {
            convertService.parseDate("");
        } catch (ServiceFailureException e) {
            assertThat(e.getMessage(), is("invalid date format '' not YYYY-MM-DD"));
        }
        try
        {
            convertService.parseDate("1.1.2000");
        } catch (ServiceFailureException e) {
            assertThat(e.getMessage(), is("invalid date format '1.1.2000' not YYYY-MM-DD"));
        }
        try
        {
            convertService.parseDate("3030-1-12");
        } catch (ServiceFailureException e) {
            assertThat(e.getMessage(), is("invalid date format '3030-1-12' not YYYY-MM-DD"));
        }
        assertThat(convertService.parseDate("3030-01-12"), is(Date.valueOf("3030-1-12")));
    }
}
