package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.kaleta.client.PolygonClient;
import org.kaleta.client.RequestFailureException;
import org.kaleta.client.dto.PolygonFinancials;
import org.kaleta.client.dto.PolygonPriceRange;
import org.kaleta.model.Company;
import org.kaleta.model.Periods;
import org.kaleta.persistence.entity.Period;
import org.kaleta.persistence.entity.PeriodType;
import org.kaleta.rest.dto.EstimateImportDto;
import org.kaleta.rest.dto.PeriodImportCandidateDto;
import org.kaleta.rest.dto.PeriodImportDataDto;
import org.kaleta.rest.dto.PeriodImportDto;
import org.kaleta.rest.error.InvalidInputException;

import java.math.BigDecimal;
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
        Periods periods = periodService.getBy(companyId);
        String latestPeriodId = periods.getPeriods().stream()
                .findFirst()
                .map(period -> period.getName().toString())
                .orElse(null);

        PeriodImportCandidateDto candidate = firebaseService
                .getNewerPeriods(company.getTicker(), latestPeriodId).stream()
                .filter(period -> quarterId.equals(period.getName()))
                .findFirst()
                .orElseThrow(() -> new InvalidInputException(
                        "period '" + quarterId + "' is not available for import"));

        PeriodImportDto firebaseData = firebaseService.getPeriod(company.getTicker(), quarterId);
        if (firebaseData == null) {
            throw new InvalidInputException("period '" + quarterId + "' is not available for import");
        }

        PeriodImportDataDto result = createPeriodData(candidate, firebaseData);
        if (Boolean.TRUE.equals(candidate.getIsReported())) {
            loadPolygonFinancials(result, company.getTicker(), quarterId);
            loadPolygonPrices(result, company.getTicker(), firebaseData);
            result.getPolygon().setAdjustedEps(
                    firebaseService.getLatestActualEps(company.getTicker(), quarterId));
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
        result.setPast4(getEstimate(ticker, currentQuarter, -4));
        result.setPast3(getEstimate(ticker, currentQuarter, -3));
        result.setPast2(getEstimate(ticker, currentQuarter, -2));
        result.setPast1(getEstimate(ticker, currentQuarter, -1));
        result.setCurrent(getEstimate(ticker, currentQuarter, 0));
        result.setNext1(getEstimate(ticker, currentQuarter, 1));
        result.setNext2(getEstimate(ticker, currentQuarter, 2));
        result.setNext3(getEstimate(ticker, currentQuarter, 3));
        return result;
    }

    private EstimateImportDto.Quarter getEstimate(String ticker, String quarterId, int offset)
    {
        return firebaseService.getLatestEstimate(ticker, arithmeticService.shiftQuarter(quarterId, offset));
    }

    private boolean isQuarter(PeriodType periodType)
    {
        return periodType == PeriodType.Q1
                || periodType == PeriodType.Q2
                || periodType == PeriodType.Q3
                || periodType == PeriodType.Q4;
    }

    private PeriodImportDataDto createPeriodData(
            PeriodImportCandidateDto candidate,
            PeriodImportDto firebaseData)
    {
        PeriodImportDataDto result = new PeriodImportDataDto();
        result.setName(candidate.getName());
        result.setEndingMonth(candidate.getEndingMonth());
        result.setReportDate(firebaseData.getReportDate());
        result.setIsReported(candidate.getIsReported());

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
        return result;
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
            if (financials.isEmpty()) {
                result.getWarnings().add(
                        "Polygon.io financial data was not found for " + quarterId);
                return;
            }

            PolygonFinancials values = financials.get();
            PeriodImportDataDto.Source polygon = result.getPolygon();
            polygon.setShares(toMillions(values.shares()));
            polygon.setRevenue(toMillions(values.revenue()));
            polygon.setGrossProfit(toMillions(values.grossProfit()));
            polygon.setOperatingIncome(toMillions(values.operatingIncome()));
            polygon.setNetIncome(toMillions(values.netIncome()));
        } catch (RequestFailureException exception) {
            result.getWarnings().add(
                    "Polygon.io financial data could not be loaded: " + exception.getMessage());
        }
    }

    private void loadPolygonPrices(
            PeriodImportDataDto result,
            String ticker,
            PeriodImportDto firebaseData)
    {
        if (firebaseData.getPreviousReportDate() == null || firebaseData.getReportDate() == null) {
            result.getWarnings().add(
                    "Polygon.io prices could not be loaded because report dates are missing");
            return;
        }

        try {
            Optional<PolygonPriceRange> priceRange = polygonClient.getPriceRange(
                    ticker,
                    firebaseData.getPreviousReportDate(),
                    firebaseData.getReportDate());
            if (priceRange.isEmpty()) {
                result.getWarnings().add("Polygon.io prices were not found between "
                        + firebaseData.getPreviousReportDate() + " and " + firebaseData.getReportDate());
                return;
            }

            PeriodImportDataDto.Source polygon = result.getPolygon();
            polygon.setPriceHigh(toString(priceRange.get().high()));
            polygon.setPriceLow(toString(priceRange.get().low()));
        } catch (RequestFailureException exception) {
            result.getWarnings().add(
                    "Polygon.io prices could not be loaded: " + exception.getMessage());
        }
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
