package org.kaleta.model;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.kaleta.entity.Financial;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@QuarkusTest
public class FinancialsModelTest
{
    @Test
    void getSortedFinancials()
    {
        List<Financial> financials = new ArrayList<>(
                List.of(generate("21YY"), generate("22YY"), generate("20YY"),
                        generate("19H1"), generate("19H2"), generate("18H2"),
                        generate("07Q4"), generate("07Q1"), generate("07Q2"), generate("08Q3"))
        );
        FinancialsModel model = new FinancialsModel(financials);

        List<Financial> sortedFinancials = model.getSortedFinancials();

        assertThat(sortedFinancials.get(0).getQuarter(), is("22YY"));
        assertThat(sortedFinancials.get(1).getQuarter(), is("21YY"));
        assertThat(sortedFinancials.get(2).getQuarter(), is("20YY"));
        assertThat(sortedFinancials.get(3).getQuarter(), is("19H2"));
        assertThat(sortedFinancials.get(4).getQuarter(), is("19H1"));
        assertThat(sortedFinancials.get(5).getQuarter(), is("18H2"));
        assertThat(sortedFinancials.get(6).getQuarter(), is("08Q3"));
        assertThat(sortedFinancials.get(7).getQuarter(), is("07Q4"));
        assertThat(sortedFinancials.get(8).getQuarter(), is("07Q2"));
        assertThat(sortedFinancials.get(9).getQuarter(), is("07Q1"));
    }

    @Test
    void getTtmFinancialsFromQuarters()
    {
        List<Financial> financials = new ArrayList<>(
                List.of(generate("13Q3", "1000", "500", "200", "100"),
                        generate("13Q2", "900", "400", "100", "50"),
                        generate("13Q1", "800", "350", "80", "20"),
                        generate("12Q4", "700", "300", "70", "10"),
                        generate("12Q3", "600", "250", "60", "5"))
        );
        FinancialsModel model = new FinancialsModel(financials);

        Financial ttm = model.getTtmFinancials();
        assertThat(ttm.getRevenue(), is(new BigDecimal("3400")));
        assertThat(ttm.getCostGoodsSold(), is(new BigDecimal("1550")));
        assertThat(ttm.getGrossProfit(), is(new BigDecimal("1850")));
        assertThat(ttm.getGrossMargin(), is(new BigDecimal("54.41")));
        assertThat(ttm.getOperatingExpenses(), is(new BigDecimal("450")));
        assertThat(ttm.getOperatingIncome(), is(new BigDecimal("1400")));
        assertThat(ttm.getOperatingMargin(), is(new BigDecimal("41.18")));
        assertThat(ttm.getNetIncome(), is(new BigDecimal("180")));
        assertThat(ttm.getNetMargin(), is(new BigDecimal("5.29")));
    }

    @Test
    void getTtmFinancialsFromYearAndQuarters()
    {
        List<Financial> financials = new ArrayList<>(
                List.of(generate("13Q1", "100", "50", "20", "10"),
                        generate("12FY", "500", "200", "100", "100"),
                        generate("11FY", "600", "300", "100", "100"))
        );
        FinancialsModel model = new FinancialsModel(financials);

        Financial ttm = model.getTtmFinancials();
        assertThat(ttm.getRevenue(), is(new BigDecimal("475")));
        assertThat(ttm.getCostGoodsSold(), is(new BigDecimal("200")));
        assertThat(ttm.getGrossProfit(), is(new BigDecimal("275")));
        assertThat(ttm.getGrossMargin(), is(new BigDecimal("57.89")));
        assertThat(ttm.getOperatingExpenses(), is(new BigDecimal("95")));
        assertThat(ttm.getOperatingIncome(), is(new BigDecimal("180")));
        assertThat(ttm.getOperatingMargin(), is(new BigDecimal("37.89")));
        assertThat(ttm.getNetIncome(), is(new BigDecimal("85")));
        assertThat(ttm.getNetMargin(), is(new BigDecimal("17.89")));
    }

    @Test
    void getTtmFinancialsFromHalfYears()
    {
        List<Financial> financials = new ArrayList<>(
                List.of(generate("13H1", "1000", "500", "200", "100"),
                        generate("12H2", "900", "400", "100", "50"),
                        generate("12H1", "800", "350", "80", "20"))
        );
        FinancialsModel model = new FinancialsModel(financials);

        Financial ttm = model.getTtmFinancials();
        assertThat(ttm.getRevenue(), is(new BigDecimal("1900")));
        assertThat(ttm.getCostGoodsSold(), is(new BigDecimal("900")));
        assertThat(ttm.getGrossProfit(), is(new BigDecimal("1000")));
        assertThat(ttm.getGrossMargin(), is(new BigDecimal("52.63")));
        assertThat(ttm.getOperatingExpenses(), is(new BigDecimal("300")));
        assertThat(ttm.getOperatingIncome(), is(new BigDecimal("700")));
        assertThat(ttm.getOperatingMargin(), is(new BigDecimal("36.84")));
        assertThat(ttm.getNetIncome(), is(new BigDecimal("150")));
        assertThat(ttm.getNetMargin(), is(new BigDecimal("7.89")));
    }

    @Test
    void getTtmFinancialsFromYearAndHalfYears()
    {
        List<Financial> financials = new ArrayList<>(
                List.of(generate("13H1", "100", "50", "20", "10"),
                        generate("12FY", "500", "200", "100", "100"),
                        generate("11FY", "600", "300", "100", "100"))
        );
        FinancialsModel model = new FinancialsModel(financials);

        Financial ttm = model.getTtmFinancials();
        assertThat(ttm.getRevenue(), is(new BigDecimal("350")));
        assertThat(ttm.getCostGoodsSold(), is(new BigDecimal("150")));
        assertThat(ttm.getGrossProfit(), is(new BigDecimal("200")));
        assertThat(ttm.getGrossMargin(), is(new BigDecimal("57.14")));
        assertThat(ttm.getOperatingExpenses(), is(new BigDecimal("70")));
        assertThat(ttm.getOperatingIncome(), is(new BigDecimal("130")));
        assertThat(ttm.getOperatingMargin(), is(new BigDecimal("37.14")));
        assertThat(ttm.getNetIncome(), is(new BigDecimal("60")));
        assertThat(ttm.getNetMargin(), is(new BigDecimal("17.14")));
    }

    @Test
    void getTtmFinancialsFromLessQuarters()
    {
        List<Financial> financials = new ArrayList<>(
                List.of(generate("13Q3", "1000", "500", "200", "100"),
                        generate("13Q2", "900", "400", "100", "50"),
                        generate("13Q1", "800", "350", "80", "20"))
        );
        FinancialsModel model = new FinancialsModel(financials);

        Financial ttm = model.getTtmFinancials();
        assertThat(ttm.getRevenue(), is(new BigDecimal("3600")));
        assertThat(ttm.getCostGoodsSold(), is(new BigDecimal("1667")));
        assertThat(ttm.getGrossProfit(), is(new BigDecimal("1933")));
        assertThat(ttm.getGrossMargin(), is(new BigDecimal("53.69")));
        assertThat(ttm.getOperatingExpenses(), is(new BigDecimal("507")));
        assertThat(ttm.getOperatingIncome(), is(new BigDecimal("1426")));
        assertThat(ttm.getOperatingMargin(), is(new BigDecimal("39.61")));
        assertThat(ttm.getNetIncome(), is(new BigDecimal("227")));
        assertThat(ttm.getNetMargin(), is(new BigDecimal("6.31")));
    }

    private static Financial generate(String quarter, String revenue, String cogs, String opExp, String netIncome) {
        Financial financial = generate(quarter);
        financial.setRevenue(new BigDecimal(revenue));
        financial.setCostGoodsSold(new BigDecimal(cogs));
        financial.setOperatingExpenses(new BigDecimal(opExp));
        financial.setNetIncome(new BigDecimal(netIncome));
        return financial;
    }

    private static Financial generate(String quarter) {
        Financial financial = new Financial();
        financial.setQuarter(quarter);
        return financial;
    }
}
