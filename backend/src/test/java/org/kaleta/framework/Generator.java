package org.kaleta.framework;

import org.kaleta.entity.Company;
import org.kaleta.entity.Currency;
import org.kaleta.entity.Period;
import org.kaleta.entity.Sector;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class Generator
{
    private static final Random RANDOM = new Random();

    public static <T extends Enum<?>> T randomEnum(Class<T> clazz){
        T[] constants = clazz.getEnumConstants();
        return constants[RANDOM.nextInt(constants.length)];
    }

    public static BigDecimal randomBigDecimal(BigDecimal max, int scale) {
        BigDecimal scaledMax = max.movePointRight(scale);
        long bound = scaledMax.longValueExact();
        long randomLong = (long) (RANDOM.nextDouble() * bound);
        return BigDecimal.valueOf(randomLong, scale).setScale(scale, RoundingMode.DOWN);
    }

    public static String randomDate(int year) {
        long randomDay = ThreadLocalRandom.current()
                .nextLong(LocalDate.of(year, 1, 1).toEpochDay(),
                        LocalDate.of(year, 12, 31).toEpochDay() + 1);

        return LocalDate.ofEpochDay(randomDay).format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    public static Company generateCompany() {
        Company company = new Company();
        company.setTicker(RANDOM.ints(4, 'A', 'Z' + 1)
                .mapToObj(i -> String.valueOf((char) i))
                .collect(Collectors.joining()));
        company.setCurrency(randomEnum(Currency.class));
        company.setSector(randomEnum(Sector.class));
        company.setWatching(RANDOM.nextBoolean());
        return company;
    }

    public static Period generatePeriod(Company company, boolean reported) {
        String name = String.format("%02d", RANDOM.nextInt(100))
                + List.of("FY", "H1", "H2", "Q1", "Q2", "Q3", "Q4").get(RANDOM.nextInt(7));
        String endingMonth = name.substring(0,2) + String.format("%02d", RANDOM.nextInt(12) + 1);
        return generatePeriod(company, reported, name, endingMonth);
    }

    public static Period generatePeriod(Company company, boolean reported, String name, String endingMonth) {
        Period period = new Period();
        period.setCompany(company);
        period.setName(name);
        period.setEndingMonth(endingMonth);
        period.setResearch("content");
        period.setShares(randomBigDecimal(new BigDecimal(999999), 2));
        period.setPriceHigh(randomBigDecimal(new BigDecimal(999999), 4));
        period.setPriceLatest(randomBigDecimal(period.getPriceHigh(), 4));
        period.setPriceLow(randomBigDecimal(period.getPriceLatest(), 4));
        if (reported) {
            period.setReportDate(Date.valueOf(randomDate(Integer.parseInt("20" + period.getName().substring(0,2)))));
            period.setRevenue(randomBigDecimal(new BigDecimal(999999), 2));
            period.setCostGoodsSold(randomBigDecimal(period.getRevenue(), 2));
            period.setOperatingExpenses(randomBigDecimal(period.getCostGoodsSold(), 2));
            period.setNetIncome(randomBigDecimal(period.getOperatingExpenses(), 2));
            period.setDividend(randomBigDecimal(period.getNetIncome(), 2));
        }
        return period;
    }
}
