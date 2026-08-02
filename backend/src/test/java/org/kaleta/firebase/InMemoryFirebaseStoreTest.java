package org.kaleta.firebase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.database.utilities.encoding.CustomClassMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kaleta.model.FirebaseAsset;
import org.kaleta.model.FirebaseCompany;
import org.kaleta.model.Trades;
import org.kaleta.persistence.entity.Period;
import org.kaleta.persistence.entity.PeriodName;
import org.kaleta.rest.dto.PeriodImportCandidateDto;
import org.kaleta.rest.dto.PeriodImportDto;
import org.kaleta.rest.error.InvalidInputException;
import org.kaleta.service.FirebaseService;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryFirebaseStoreTest
{
    private InMemoryFirebaseStore firebaseStore;
    private FirebaseService firebaseService;

    @BeforeEach
    void setUp()
    {
        firebaseStore = new InMemoryFirebaseStore(
                new ObjectMapper(),
                () -> {
                    throw new AssertionError("Existing Firebase snapshot must not be downloaded again");
                },
                "src/test/resources/firebaseTestData.json");
        firebaseService = new FirebaseService(firebaseStore);
    }

    @Test
    void downloadsCompanyDataWhenSnapshotIsMissing(@TempDir Path directory)
    {
        Path snapshot = directory.resolve("firebase.json");
        AtomicBoolean downloaded = new AtomicBoolean();

        InMemoryFirebaseStore store = new InMemoryFirebaseStore(
                new ObjectMapper(),
                () -> {
                    downloaded.set(true);
                    return Map.of(
                            "company-dep", Map.of(
                                    "AMD", Map.of("ticker", "AMD", "signal", "BUY")),
                            "company", Map.of("AMD", Map.of()),
                            "asset", List.of());
                },
                snapshot.toString());

        assertThat(downloaded.get(), is(true));
        assertThat(Files.exists(snapshot), is(true));
        assertThat(store.findCompanyDep("AMD").orElseThrow().getSignal(), is("BUY"));
        assertThat(store.findCompany("AMD").isPresent(), is(true));
    }

    @Test
    void doesNotDownloadWhenSnapshotExists(@TempDir Path directory) throws Exception
    {
        Path snapshot = directory.resolve("firebase.json");
        Files.copy(Path.of("src/test/resources/firebaseTestData.json"), snapshot);
        AtomicBoolean downloaded = new AtomicBoolean();

        InMemoryFirebaseStore store = new InMemoryFirebaseStore(
                new ObjectMapper(),
                () -> {
                    downloaded.set(true);
                    return Map.of();
                },
                snapshot.toString());

        assertThat(downloaded.get(), is(false));
        assertThat(store.findCompany("NVDA").isPresent(), is(true));
    }

    @Test
    void readsSeededCompanies()
    {
        assertThat(firebaseService.hasCompany("NVDA"), is(true));
        assertThat(firebaseService.hasCompany("AMD"), is(false));
        assertThat(firebaseService.getCompanyDep("NVDA").getSignal(), is("BUY"));

        InvalidInputException exception = assertThrows(
                InvalidInputException.class,
                () -> firebaseService.getCompanyDep("AMD"));
        assertThat(exception.getMessage(), is("company with ticker 'AMD' not found"));
    }

    @Test
    void readsNewerPeriodsInDescendingOrder()
    {
        List<PeriodImportCandidateDto> periods = firebaseService.getNewerPeriods("NVDA", "24Q4");

        assertThat(periods.size(), is(2));
        assertThat(periods.get(0).getName(), is("25Q2"));
        assertThat(periods.get(0).getEndingMonth(), is("2025-07"));
        assertThat(periods.get(0).getIsReported(), is(true));
        assertThat(periods.get(1).getName(), is("25Q1"));
        assertThat(firebaseService.getPeriod("NVDA", "25Q1").getEndingMonth(), is("2025-04"));
        assertThat(firebaseService.getNewerPeriods("AMD", "24Q4"), is(empty()));
    }

    @Test
    void readsSeededTargets()
    {
        FirebaseCompany.Gemini.Target target = firebaseStore
                .findCompany("NVDA")
                .orElseThrow()
                .getGemini()
                .getTargets()
                .get("2026-07-24-test");

        assertThat(target.getDate(), is("2026-07-24"));
        assertThat(target.getInstitution(), is("Example Capital"));
        assertThat(target.getPrice(), is("210"));
        assertThat(target.getRating(), is("Buy"));
        assertThat(target.getSource(), is("example"));
    }

    @Test
    void mapsTargetsWithFirebaseMapper()
    {
        Map<String, Object> targetData = Map.of(
                "date", "2026-07-24",
                "institution", "Example Capital",
                "price", "210",
                "rating", "Buy",
                "source", "example");
        Map<String, Object> companyData = Map.of(
                "gemini", Map.of(
                        "targets", Map.of("2026-07-24-test", targetData)));

        FirebaseCompany company = CustomClassMapper.convertToCustomClass(companyData, FirebaseCompany.class);
        FirebaseCompany.Gemini.Target target = company.getGemini()
                .getTargets()
                .get("2026-07-24-test");

        assertThat(target.getInstitution(), is("Example Capital"));
        assertThat(target.getPrice(), is("210"));
    }

    @Test
    void mapsFinnhubEarningsStringsWithFirebaseMapper()
    {
        Map<String, Object> earningsData = Map.of(
                "epsa", "1.25",
                "epse", "1.20",
                "report", "2026-04-27-bmo",
                "reva", "44100000000",
                "reve", "44000000000");
        Map<String, Object> companyData = Map.of(
                "fhe", Map.of(
                        "26Q1", Map.of(
                                "20260427", earningsData)));

        FirebaseCompany company = CustomClassMapper.convertToCustomClass(companyData, FirebaseCompany.class);
        FirebaseCompany.FinnhubEarnings earnings = company.getFhe()
                .get("26Q1")
                .get("20260427");

        assertThat(earnings.getEpsa(), is("1.25"));
        assertThat(earnings.getEpse(), is("1.20"));
        assertThat(earnings.getReport(), is("2026-04-27-bmo"));
        assertThat(earnings.getReva(), is("44100000000"));
        assertThat(earnings.getReve(), is("44000000000"));
    }

    @Test
    void replacesAssets()
    {
        org.kaleta.model.Company company = new org.kaleta.model.Company();
        company.setTicker("NVDA");
        Trades.Trade trade = new Trades.Trade();
        trade.setCompany(company);
        trade.setPurchaseQuantity(new BigDecimal("5.5000"));
        trade.setPurchasePrice(new BigDecimal("142.2500"));
        Trades trades = new Trades();
        trades.setTrades(List.of(trade));

        firebaseService.pushAssets(trades);

        List<FirebaseAsset> assets = firebaseStore.getAssets();
        assertThat(assets.size(), is(1));
        assertThat(assets.get(0).getTicker(), is("NVDA"));
        assertThat(assets.get(0).getQuantity(), is("5.5"));
        assertThat(assets.get(0).getPrice(), is("142.25"));

        firebaseService.pushAssets(new Trades());
        assertThat(firebaseStore.getAssets(), is(empty()));
    }

    @Test
    void updatesSeededPeriod()
    {
        org.kaleta.persistence.entity.Company company = new org.kaleta.persistence.entity.Company();
        company.setTicker("NVDA");
        Period period = new Period();
        period.setCompany(company);
        period.setName(PeriodName.valueOf("25Q1"));
        period.setReportDate(Date.valueOf("2025-05-29"));
        period.setShares(new BigDecimal("24500"));
        period.setPriceLow(new BigDecimal("90.25"));
        period.setPriceHigh(new BigDecimal("155.50"));
        period.setRevenue(new BigDecimal("45000"));
        period.setGrossProfit(new BigDecimal("22000"));
        period.setOperatingIncome(new BigDecimal("20000"));
        period.setNetIncome(new BigDecimal("19000"));

        firebaseService.updatePeriod(period);

        org.kaleta.model.FirebaseCompany.Gemini.Quarter quarter = firebaseStore
                .findCompany("NVDA")
                .orElseThrow()
                .getGemini()
                .getQuarters()
                .get("25Q1");
        assertThat(quarter.getReport_date_this_quarter(), is("2025-05-29"));
        assertThat(quarter.getReported_shares(), is("24500"));
        assertThat(quarter.getPrice_min(), is("90.25"));
        assertThat(quarter.getPrice_max(), is("155.50"));
        assertThat(quarter.getReported_revenues(), is("45000"));
        assertThat(quarter.getReported_gross_profit(), is("22000"));
        assertThat(quarter.getReported_operating_income(), is("20000"));
        assertThat(quarter.getReported_net_income(), is("19000"));
        assertThat(quarter.getReported_div(), is(""));
    }
}
