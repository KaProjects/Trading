package org.kaleta.service;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.kaleta.client.PolygonClient;
import org.kaleta.client.dto.PolygonFinancials;
import org.kaleta.client.dto.PolygonPriceRange;
import org.kaleta.model.Company;
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
    private final ArithmeticService arithmeticService;

    @Inject
    public ImportService(
            CompanyService companyService,
            PeriodService periodService,
            FirebaseService firebaseService,
            PolygonClient polygonClient,
            ArithmeticService arithmeticService)
    {
        this.companyService = companyService;
        this.periodService = periodService;
        this.firebaseService = firebaseService;
        this.polygonClient = polygonClient;
        this.arithmeticService = arithmeticService;
    }

    public PeriodImportDataDto getPeriod(Long companyId, String quarterId)
    {
        Company company = companyService.getCompany(companyId);
        String ticker = company.getTicker();
        PeriodImportDataDto result = new PeriodImportDataDto();
        PeriodImportDto firebaseData = loadFirebasePeriod(result, ticker, quarterId);
        if (firebaseData != null) {
            populatePeriodData(result, firebaseData);
        }

        loadPolygonFinancials(result, ticker, quarterId);
        if (firebaseData != null) {
            loadPolygonPrices(result, ticker, firebaseData);
        }
        loadExternalAdjustedEps(result, ticker, quarterId);
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

    private void populatePeriodData(PeriodImportDataDto result, PeriodImportDto firebaseData)
    {
        result.setName(firebaseData.getName());
        result.setEndingMonth(firebaseData.getEndingMonth());
        result.setReportDate(firebaseData.getReportDate());
        result.setIsReported(firebaseData.getIsReported());

        PeriodImportDataDto.Source firebase = result.getFirebase();
        firebase.setShares(firebaseData.getShares());
        firebase.setPriceLow(firebaseData.getPriceLow());
        firebase.setPriceHigh(firebaseData.getPriceHigh());
        firebase.setRevenue(firebaseData.getRevenue());
        firebase.setGrossProfit(firebaseData.getGrossProfit());
        firebase.setOperatingIncome(firebaseData.getOperatingIncome());
        firebase.setNetIncome(firebaseData.getNetIncome());
        firebase.setDividend(firebaseData.getDividend());
        firebase.setAdjustedEps(firebaseData.getAdjustedEps());
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
            String quarterId)
    {
        String fiscalYear = "20" + quarterId.substring(0, 2);
        String fiscalPeriod = quarterId.substring(2);
        try {
            Optional<PolygonFinancials> financials = polygonClient
                    .getFinancials(ticker, fiscalYear, fiscalPeriod);
            if (financials.isEmpty()) return;

            PolygonFinancials values = financials.get();
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

    private void loadPolygonPrices(
            PeriodImportDataDto result,
            String ticker,
            PeriodImportDto firebaseData)
    {
        if (firebaseData.getPreviousReportDate() == null || firebaseData.getReportDate() == null) {
            return;
        }

        try {
            Optional<PolygonPriceRange> priceRange = polygonClient.getPriceRange(
                    ticker,
                    firebaseData.getPreviousReportDate(),
                    firebaseData.getReportDate());
            if (priceRange.isEmpty()) return;

            PeriodImportDataDto.Source polygon = result.getPolygon();
            polygon.setPriceHigh(toString(priceRange.get().high()));
            polygon.setPriceLow(toString(priceRange.get().low()));
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
