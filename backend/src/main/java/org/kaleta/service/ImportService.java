package org.kaleta.service;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.kaleta.client.AlphaVantageClient;
import org.kaleta.client.PolygonClient;
import org.kaleta.client.dto.AlphaVantageCashFlow;
import org.kaleta.client.dto.AlphaVantageEarnings;
import org.kaleta.client.dto.AlphaVantageIncomeStatement;
import org.kaleta.client.dto.AlphaVantagePriceRange;
import org.kaleta.client.dto.AlphaVantageShares;
import org.kaleta.client.dto.PolygonFinancials;
import org.kaleta.client.dto.PolygonPriceRange;
import org.kaleta.model.Company;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.Period;
import org.kaleta.persistence.entity.PeriodType;
import org.kaleta.rest.dto.EstimateImportDto;
import org.kaleta.rest.dto.PeriodImportDataDto;
import org.kaleta.rest.dto.PeriodImportDto;
import org.kaleta.rest.error.InvalidInputException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ImportService
{
    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000);

    private final CompanyService companyService;
    private final PeriodService periodService;
    private final FirebaseService firebaseService;
    private final PolygonClient polygonClient;
    private final AlphaVantageClient alphaVantageClient;
    private final ArithmeticService arithmeticService;

    @Inject
    public ImportService(
            CompanyService companyService,
            PeriodService periodService,
            FirebaseService firebaseService,
            PolygonClient polygonClient,
            AlphaVantageClient alphaVantageClient,
            ArithmeticService arithmeticService)
    {
        this.companyService = companyService;
        this.periodService = periodService;
        this.firebaseService = firebaseService;
        this.polygonClient = polygonClient;
        this.alphaVantageClient = alphaVantageClient;
        this.arithmeticService = arithmeticService;
    }

    public PeriodImportDataDto getPeriod(Long companyId, String quarterId, String endingMonth)
    {
        Company company = companyService.getCompany(companyId);
        String ticker = company.getTicker();
        PeriodImportDataDto result = new PeriodImportDataDto();
        String firebaseCurrency = loadFirebaseCurrency(result.getWarnings(), ticker);
        boolean useFirebaseFinancials = hasMatchingFirebaseCurrency(
                result.getWarnings(), ticker, quarterId, firebaseCurrency, company.getCurrency());
        PeriodImportDto firebaseData = loadFirebasePeriod(result, ticker, quarterId);
        if (firebaseData != null) {
            populatePeriodMetadata(result, firebaseData);
            if (useFirebaseFinancials) {
                populateFirebaseFinancials(result, firebaseData);
            }
            if (endingMonth == null || endingMonth.isBlank()) {
                endingMonth = firebaseData.getEndingMonth();
            }
        }
        Currency companyCurrency = company.getCurrency();
        boolean useConfiguredAlphaVantageTicker = companyCurrency != Currency.$
                && company.getAlphaVantageTicker() != null
                && !company.getAlphaVantageTicker().isBlank();
        if (companyCurrency != Currency.$ && !useConfiguredAlphaVantageTicker) {
            result.getWarnings().add(
                    "Alpha Vantage ticker for non-USD company " + ticker + " is not configured. "
                            + "Configure it in the company edit dialog to load all Alpha Vantage suggestions.");
        }
        String alphaVantageTicker = useConfiguredAlphaVantageTicker
                ? company.getAlphaVantageTicker()
                : ticker;
        loadAlphaVantageData(result, alphaVantageTicker, companyCurrency, quarterId, endingMonth);
        if (useConfiguredAlphaVantageTicker) {
            loadAlphaVantagePeriodData(
                    result,
                    alphaVantageTicker,
                    companyCurrency,
                    quarterId,
                    endingMonth,
                    firebaseData);
        }

        loadPolygonFinancials(result, ticker, companyCurrency, quarterId);
        if (firebaseData != null && companyCurrency == Currency.$) {
            loadPolygonPrices(result, ticker, companyCurrency, firebaseData);
        }
        if (useFirebaseFinancials) {
            loadExternalAdjustedEps(result, ticker, quarterId);
        }
        return result;
    }

    public EstimateImportDto getEstimate(Long companyId, Long periodId)
    {
        org.kaleta.persistence.entity.Company company = companyService.findEntity(companyId);
        Period period = periodService.get(periodId);
        if (!companyId.equals(period.getCompany().getId())) {
            throw new InvalidInputException(
                    "period with id '" + periodId + "' does not belong to company with id '" + companyId + "'");
        }
        if (!isQuarter(period.getName().getType())) {
            throw new InvalidInputException("period with id '" + periodId + "' is not a quarter");
        }

        String ticker = company.getTicker();
        String currentQuarter = period.getName().toString();
        EstimateImportDto result = new EstimateImportDto();
        String firebaseCurrency = loadFirebaseCurrency(result.getWarnings(), ticker);
        if (!hasMatchingFirebaseCurrency(
                result.getWarnings(), ticker, currentQuarter, firebaseCurrency, company.getCurrency())) {
            return result;
        }
        result.setCurrent(getEstimate(result, ticker, currentQuarter, 0));
        result.setNext1(getEstimate(result, ticker, currentQuarter, 1));
        result.setNext2(getEstimate(result, ticker, currentQuarter, 2));
        result.setNext3(getEstimate(result, ticker, currentQuarter, 3));
        return result;
    }

    private EstimateImportDto.Quarter getEstimate(
            EstimateImportDto result,
            String ticker,
            String quarterId,
            int offset)
    {
        String shiftedQuarter = arithmeticService.shiftQuarter(quarterId, offset);
        try {
            return firebaseService.getLatestEstimate(ticker, shiftedQuarter);
        } catch (Exception exception) {
            addWarning(
                    result.getWarnings(),
                    "Firebase/Finnhub estimate " + shiftedQuarter + " for " + ticker,
                    exception);
            return null;
        }
    }

    private boolean isQuarter(PeriodType periodType)
    {
        return periodType == PeriodType.Q1
                || periodType == PeriodType.Q2
                || periodType == PeriodType.Q3
                || periodType == PeriodType.Q4;
    }

    private void populatePeriodMetadata(PeriodImportDataDto result, PeriodImportDto firebaseData)
    {
        result.setName(firebaseData.getName());
        result.setEndingMonth(firebaseData.getEndingMonth());
        result.setReportDate(firebaseData.getReportDate());
        result.setIsReported(firebaseData.getIsReported());
    }

    private void populateFirebaseFinancials(PeriodImportDataDto result, PeriodImportDto firebaseData)
    {
        PeriodImportDataDto.Source firebase = result.getFirebase();
        firebase.setShares(firebaseData.getShares());
        firebase.setPriceLow(firebaseData.getPriceLow());
        firebase.setPriceHigh(firebaseData.getPriceHigh());
        firebase.setRevenue(firebaseData.getRevenue());
        firebase.setGrossProfit(firebaseData.getGrossProfit());
        firebase.setOperatingIncome(firebaseData.getOperatingIncome());
        firebase.setNetIncome(firebaseData.getNetIncome());
        firebase.setDividend(firebaseData.getDividend());
        firebase.setCapex(firebaseData.getCapex());
        firebase.setFreeCashFlow(firebaseData.getFreeCashFlow());
        firebase.setAdjustedEps(firebaseData.getAdjustedEps());
    }

    private String loadFirebaseCurrency(List<String> warnings, String ticker)
    {
        try {
            return firebaseService.getReportingCurrency(ticker);
        } catch (Exception exception) {
            addWarning(warnings, "Firebase reporting currency for " + ticker, exception);
            return null;
        }
    }

    private boolean hasMatchingFirebaseCurrency(
            List<String> warnings,
            String ticker,
            String periodName,
            String firebaseCurrency,
            Currency companyCurrency)
    {
        if (firebaseCurrency == null || firebaseCurrency.isBlank()) {
            return true;
        }
        if (companyCurrency.name().equals(firebaseCurrency.trim())) {
            return true;
        }

        String warning = "Firebase/Gemini financial data for " + periodName + " and " + ticker
                + " was ignored because reported currency " + firebaseCurrency
                + " does not match the company's configured currency " + companyCurrency;
        Log.warn(warning);
        warnings.add(warning);
        return false;
    }

    private PeriodImportDto loadFirebasePeriod(
            PeriodImportDataDto result,
            String ticker,
            String quarterId)
    {
        try {
            return firebaseService.getPeriod(ticker, quarterId);
        } catch (Exception exception) {
            addWarning(
                    result.getWarnings(),
                    "Firebase period " + quarterId + " for " + ticker,
                    exception);
            return null;
        }
    }

    private void loadExternalAdjustedEps(
            PeriodImportDataDto result,
            String ticker,
            String quarterId)
    {
        try {
            result.getPolygon().setAdjustedEps(firebaseService.getLatestActualEps(ticker, quarterId));
        } catch (Exception exception) {
            addWarning(
                    result.getWarnings(),
                    "Firebase/Finnhub EPS for " + quarterId + " and " + ticker,
                    exception);
        }
    }

    private void loadPolygonFinancials(
            PeriodImportDataDto result,
            String ticker,
            Currency companyCurrency,
            String quarterId)
    {
        String fiscalYear = "20" + quarterId.substring(0, 2);
        String fiscalPeriod = quarterId.substring(2);
        try {
            Optional<PolygonFinancials> financials = polygonClient
                    .getFinancials(ticker, fiscalYear, fiscalPeriod);
            if (financials.isEmpty()) return;

            PolygonFinancials values = financials.get();
            String sourceName = "Polygon.io financial data for " + quarterId + " and " + ticker;
            if (!hasMatchingCurrency(
                    result.getWarnings(),
                    sourceName,
                    "Polygon.io",
                    values.reportedCurrency(),
                    companyCurrency)) {
                return;
            }
            PeriodImportDataDto.Source polygon = result.getPolygon();
            polygon.setShares(toMillions(values.shares()));
            polygon.setRevenue(toMillions(values.revenue()));
            polygon.setGrossProfit(toMillions(values.grossProfit()));
            polygon.setOperatingIncome(toMillions(values.operatingIncome()));
            polygon.setNetIncome(toMillions(values.netIncome()));
        } catch (Exception exception) {
            addWarning(
                    result.getWarnings(),
                    "Polygon.io financial data for " + quarterId + " and " + ticker,
                    exception);
        }
    }

    private void loadAlphaVantageData(
            PeriodImportDataDto result,
            String ticker,
            Currency companyCurrency,
            String periodName,
            String endingMonth)
    {
        if (endingMonth == null || endingMonth.isBlank()) return;

        loadAlphaVantageCashFlow(result, ticker, companyCurrency, periodName, endingMonth);
        loadAlphaVantageIncomeStatement(result, ticker, companyCurrency, periodName, endingMonth);
    }

    private void loadAlphaVantagePeriodData(
            PeriodImportDataDto result,
            String ticker,
            Currency companyCurrency,
            String periodName,
            String endingMonth,
            PeriodImportDto firebaseData)
    {
        if (endingMonth != null && !endingMonth.isBlank()) {
            loadAlphaVantageShares(result, ticker, companyCurrency, periodName, endingMonth);
            loadAlphaVantageEarnings(result, ticker, periodName, endingMonth);
        }
        if (firebaseData != null) {
            loadAlphaVantagePrices(result, ticker, firebaseData);
        }
    }

    private void loadAlphaVantageShares(
            PeriodImportDataDto result,
            String ticker,
            Currency companyCurrency,
            String periodName,
            String endingMonth)
    {
        try {
            Optional<AlphaVantageShares> shares = alphaVantageClient
                    .getShares(ticker, periodName, endingMonth);
            if (shares.isEmpty()) return;

            AlphaVantageShares values = shares.get();
            String sourceName = "Alpha Vantage shares for " + periodName + " and " + ticker;
            if (!hasMatchingCurrency(
                    result.getWarnings(),
                    sourceName,
                    "Alpha Vantage",
                    values.reportedCurrency(),
                    companyCurrency)) {
                return;
            }
            result.getAlphaVantage().setShares(toMillions(values.shares()));
        } catch (Exception exception) {
            addWarning(
                    result.getWarnings(),
                    "Alpha Vantage shares for " + periodName + " and " + ticker,
                    exception);
        }
    }

    private void loadAlphaVantageEarnings(
            PeriodImportDataDto result,
            String ticker,
            String periodName,
            String endingMonth)
    {
        try {
            Optional<AlphaVantageEarnings> earnings = alphaVantageClient
                    .getEarnings(ticker, periodName, endingMonth);
            earnings.ifPresent(values -> result.getAlphaVantage()
                    .setAdjustedEps(toString(values.reportedEps())));
        } catch (Exception exception) {
            addWarning(
                    result.getWarnings(),
                    "Alpha Vantage earnings for " + periodName + " and " + ticker,
                    exception);
        }
    }

    private void loadAlphaVantagePrices(
            PeriodImportDataDto result,
            String ticker,
            PeriodImportDto firebaseData)
    {
        if (firebaseData.getPreviousReportDate() == null || firebaseData.getReportDate() == null) {
            return;
        }

        try {
            Optional<AlphaVantagePriceRange> priceRange = alphaVantageClient.getPriceRange(
                    ticker,
                    firebaseData.getPreviousReportDate(),
                    firebaseData.getReportDate());
            if (priceRange.isEmpty()) return;

            AlphaVantagePriceRange values = priceRange.get();
            PeriodImportDataDto.Source source = result.getAlphaVantage();
            source.setPriceHigh(toString(values.high()));
            source.setPriceLow(toString(values.low()));
        } catch (Exception exception) {
            addWarning(
                    result.getWarnings(),
                    "Alpha Vantage price data for " + ticker,
                    exception);
        }
    }

    private void loadAlphaVantageIncomeStatement(
            PeriodImportDataDto result,
            String ticker,
            Currency companyCurrency,
            String periodName,
            String endingMonth)
    {
        try {
            Optional<AlphaVantageIncomeStatement> statement = alphaVantageClient
                    .getIncomeStatement(ticker, periodName, endingMonth);
            if (statement.isEmpty()) return;

            AlphaVantageIncomeStatement values = statement.get();
            String sourceName = "Alpha Vantage income statement for " + periodName + " and " + ticker;
            if (!hasMatchingCurrency(
                    result.getWarnings(),
                    sourceName,
                    "Alpha Vantage",
                    values.reportedCurrency(),
                    companyCurrency)) {
                return;
            }
            PeriodImportDataDto.Source source = result.getAlphaVantage();
            source.setRevenue(toMillions(values.revenue()));
            source.setGrossProfit(toMillions(values.grossProfit()));
            source.setOperatingIncome(toMillions(values.operatingIncome()));
            source.setNetIncome(toMillions(values.netIncome()));
        } catch (Exception exception) {
            addWarning(
                    result.getWarnings(),
                    "Alpha Vantage income statement for " + periodName + " and " + ticker,
                    exception);
        }
    }

    private void loadAlphaVantageCashFlow(
            PeriodImportDataDto result,
            String ticker,
            Currency companyCurrency,
            String periodName,
            String endingMonth)
    {
        try {
            Optional<AlphaVantageCashFlow> cashFlow = alphaVantageClient
                    .getCashFlow(ticker, periodName, endingMonth);
            if (cashFlow.isEmpty()) return;

            AlphaVantageCashFlow values = cashFlow.get();
            String sourceName = "Alpha Vantage cash flow for " + periodName + " and " + ticker;
            if (!hasMatchingCurrency(
                    result.getWarnings(),
                    sourceName,
                    "Alpha Vantage",
                    values.reportedCurrency(),
                    companyCurrency)) {
                return;
            }
            PeriodImportDataDto.Source source = result.getAlphaVantage();
            source.setDividend(toMillions(values.dividend()));
            source.setCapex(toMillions(values.capex()));
            source.setFreeCashFlow(toMillions(values.freeCashFlow()));
        } catch (Exception exception) {
            addWarning(
                    result.getWarnings(),
                    "Alpha Vantage cash flow for " + periodName + " and " + ticker,
                    exception);
        }
    }

    private boolean hasMatchingCurrency(
            List<String> warnings,
            String source,
            String provider,
            String reportedCurrency,
            Currency companyCurrency)
    {
        String expectedCurrency = companyCurrency.getIsoCode();
        if (reportedCurrency != null
                && expectedCurrency.equalsIgnoreCase(reportedCurrency.trim())) {
            return true;
        }

        String reason = reportedCurrency == null || reportedCurrency.isBlank()
                ? provider + " did not provide its reported currency; expected " + expectedCurrency
                : "reported currency " + reportedCurrency + " does not match the company's configured currency "
                        + expectedCurrency;
        String warning = source + " was ignored because " + reason;
        Log.warn(warning);
        warnings.add(warning);
        return false;
    }

    private void loadPolygonPrices(
            PeriodImportDataDto result,
            String ticker,
            Currency companyCurrency,
            PeriodImportDto firebaseData)
    {
        if (firebaseData.getPreviousReportDate() == null || firebaseData.getReportDate() == null) {
            return;
        }

        String sourceName = "Polygon.io price data for " + ticker;
        if (!hasMatchingCurrency(
                result.getWarnings(),
                sourceName,
                "Polygon.io",
                "USD",
                companyCurrency)) {
            return;
        }

        try {
            Optional<PolygonPriceRange> priceRange = polygonClient.getPriceRange(
                    ticker,
                    firebaseData.getPreviousReportDate(),
                    firebaseData.getReportDate());
            if (priceRange.isEmpty()) return;

            PolygonPriceRange values = priceRange.get();
            if (!hasMatchingCurrency(
                    result.getWarnings(),
                    sourceName,
                    "Polygon.io",
                    values.reportedCurrency(),
                    companyCurrency)) {
                return;
            }
            PeriodImportDataDto.Source polygon = result.getPolygon();
            polygon.setPriceHigh(toString(values.high()));
            polygon.setPriceLow(toString(values.low()));
        } catch (Exception exception) {
            addWarning(
                    result.getWarnings(),
                    "Polygon.io price data for " + ticker,
                    exception);
        }
    }

    private void addWarning(List<String> warnings, String source, Exception exception)
    {
        String warning = ExternalWarnings.unavailable(source, exception);
        Log.warn(warning, exception);
        warnings.add(warning);
    }

    private String toMillions(BigDecimal value)
    {
        return value == null ? null : toString(value.divide(ONE_MILLION));
    }

    private String toString(BigDecimal value)
    {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }
}
