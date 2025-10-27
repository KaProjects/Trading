package org.kaleta.framework;

import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.Latest;
import org.kaleta.persistence.entity.Period;
import org.kaleta.persistence.entity.PeriodName;
import org.kaleta.persistence.entity.Record;
import org.kaleta.persistence.entity.Sector;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.YearMonth;
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
        return generateCompany(null);
    }

    public static Company generateCompany(String requiredId) {
        Company company = new Company();
        if (requiredId != null) company.setId(requiredId);
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
        YearMonth endingMonth = YearMonth.of(RANDOM.nextInt(130) + 1900, RANDOM.nextInt(12) + 1);
        return generatePeriod(company, reported, PeriodName.valueOf(name), endingMonth);
    }

    public static Period generatePeriod(Company company, boolean reported, PeriodName name, YearMonth endingMonth) {
        Period period = new Period();
        period.setCompany(company);
        period.setName(name);
        period.setEndingMonth(endingMonth);
        period.setResearch("content " + String.format("%02d", RANDOM.nextInt(100)));
        period.setShares(randomBigDecimal(new BigDecimal(999999), 2));
        period.setPriceHigh(randomBigDecimal(new BigDecimal(999999), 4));
        period.setPriceLow(randomBigDecimal(period.getPriceHigh(), 4));
        if (reported) {
            period.setReportDate(Date.valueOf(randomDate(name.getYear().getValue())));
            period.setRevenue(randomBigDecimal(new BigDecimal(999999), 2));
            period.setCostGoodsSold(randomBigDecimal(period.getRevenue(), 2));
            period.setOperatingExpenses(randomBigDecimal(period.getCostGoodsSold(), 2));
            period.setNetIncome(randomBigDecimal(period.getOperatingExpenses(), 2));
            period.setDividend(randomBigDecimal(period.getNetIncome(), 2));
        }
        return period;
    }

    public static Period generatePeriod(
            Company company, PeriodName name, YearMonth endingMonth,
            String revenue, String costGoodsSold, String operatingExpense, String netIncome, String dividend
    ) {
        Period period = generatePeriod(company, false, name, endingMonth);
        period.setReportDate(Date.valueOf(randomDate(name.getYear().getValue())));
        period.setRevenue(new BigDecimal(revenue));
        period.setCostGoodsSold(new BigDecimal(costGoodsSold));
        period.setOperatingExpenses(new BigDecimal(operatingExpense));
        period.setNetIncome(new BigDecimal(netIncome));
        period.setDividend(new BigDecimal(dividend));
        period.setShares(randomBigDecimal(new BigDecimal(999999), 2));
        return period;
    }

    public static Record generateRecord(Company company)
    {
        return generateRecord(company, randomDate(RANDOM.nextInt(100) + 2000));
    }

    public static Record generateRecord(Company company, String date)
    {
        Record record = new Record();
        record.setCompany(company);
        record.setTitle("title " + String.format("%02d", RANDOM.nextInt(100)));
        record.setDate(Date.valueOf(date));
        record.setPrice(randomBigDecimal(new BigDecimal(999999), 2));
        record.setPe(randomBigDecimal(new BigDecimal(999), 2));
        record.setPs(randomBigDecimal(new BigDecimal(999), 2));
        record.setDy(randomBigDecimal(new BigDecimal(999), 2));
        record.setTargets("targets " + String.format("%02d", RANDOM.nextInt(100)));
        record.setContent("content " + String.format("%02d", RANDOM.nextInt(100)));
        record.setStrategy("strategy " + String.format("%02d", RANDOM.nextInt(100)));
        return record;
    }

    public static Latest generateLatest(Company company)
    {
        Latest latest = new Latest();
        latest.setDatetime(LocalDateTime.of(
                2000 + RANDOM.nextInt(100),
                Month.of(RANDOM.nextInt(12) + 1),
                RANDOM.nextInt(31) + 1,
                RANDOM.nextInt(24),
                RANDOM.nextInt(60)
        ));
        latest.setCompany(company);
        latest.setPrice(randomBigDecimal(new BigDecimal(999999), 2));
        return latest;
    }
}
