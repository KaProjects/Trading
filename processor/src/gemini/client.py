import json
import logging
from dataclasses import dataclass
from datetime import date

from google import genai
from google.genai import types
from pydantic import BaseModel, ValidationError

from gemini.models import (
    Company,
    InitialCompanyResponse,
    Quarter,
    ReportDates,
    TARGET_REPORT_OVERVIEW_MAX_LENGTH,
    TARGET_REPORT_TAKEAWAY_MAX_LENGTH,
    Target,
    TargetReport,
    Targets,
)
from polygon.models import (
    CompanyInsights,
    CompanySentimentAnalysis,
    CompanySentimentSummaries,
    SentimentStatistics,
)

logger = logging.getLogger(__name__)
GEMINI_RETRY_ATTEMPTS = 5
GEMINI_RETRY_INITIAL_DELAY_SECONDS = 2.0
GEMINI_RETRY_MAX_DELAY_SECONDS = 30.0
GEMINI_RETRYABLE_HTTP_STATUS_CODES = [408, 429, 500, 502, 503, 504]
INITIAL_COMPANY_QUARTER_COUNT = 5


def _quarter_ids_ending_at(
    current_quarter_id: str,
    count: int,
) -> list[str]:
    year = int(current_quarter_id[:2])
    quarter = int(current_quarter_id[-1])
    quarter_ids = []
    for _ in range(count):
        quarter_ids.append(f"{year:02d}Q{quarter}")
        quarter -= 1
        if quarter == 0:
            quarter = 4
            year = (year - 1) % 100
    return quarter_ids


class InvalidInitialCompanyResponse(ValueError):
    def __init__(
        self,
        *,
        ticker: str,
        violations: list[str],
        raw_response: str,
    ) -> None:
        self.ticker = ticker
        self.violations = tuple(violations)
        self.raw_response = raw_response
        super().__init__(
            f"Cannot initialize {ticker}: "
            f"{'; '.join(violations)}\n"
            "================ GEMINI RESPONSE START ================\n"
            f"{raw_response}\n"
            "================= GEMINI RESPONSE END ================="
        )


class InvalidQuarterReportResponse(ValueError):
    def __init__(
        self,
        *,
        ticker: str,
        quarter_id: str,
        validation_error: ValidationError,
        raw_response: str,
    ) -> None:
        self.raw_response = raw_response
        super().__init__(
            f"Invalid quarter report response for {ticker} {quarter_id}: "
            f"{validation_error}\n"
            "================ GEMINI RESPONSE START ================\n"
            f"{raw_response}\n"
            "================= GEMINI RESPONSE END ================="
        )


@dataclass(frozen=True)
class InitialCompanyResult:
    company: Company
    errors: tuple[str, ...]
    raw_response: str = ""


@dataclass(frozen=True)
class QuarterReportResult:
    quarter: Quarter
    raw_response: str = ""


class GeminiClient:
    log = logger

    def __init__(self, api_key, model):
        self.model = model
        self.client = genai.Client(
            api_key=api_key,
            http_options=types.HttpOptions(
                retry_options=types.HttpRetryOptions(
                    attempts=GEMINI_RETRY_ATTEMPTS,
                    initial_delay=GEMINI_RETRY_INITIAL_DELAY_SECONDS,
                    max_delay=GEMINI_RETRY_MAX_DELAY_SECONDS,
                    exp_base=2,
                    jitter=1.0,
                    http_status_codes=GEMINI_RETRYABLE_HTTP_STATUS_CODES,
                ),
            ),
        )

    def __ask(
        self,
        prompt: str,
        response_model: type[BaseModel],
        *,
        validation_context: dict[str, object] | None = None,
        use_google_search: bool = True,
    ):
        response = self.__request(
            prompt,
            response_model,
            use_google_search=use_google_search,
        )
        return response_model.model_validate_json(
            response.text,
            context=validation_context,
        )

    def __request(
        self,
        prompt: str,
        response_model: type[BaseModel],
        *,
        use_google_search: bool = True,
    ):
        config = {
            "response_mime_type": "application/json",
            "response_json_schema": response_model.model_json_schema(),
        }
        if use_google_search:
            config["tools"] = [
                types.Tool(google_search=types.GoogleSearch())
            ]
        return self.client.models.generate_content(
            model=self.model,
            contents=prompt,
            config=config,
        )

    def get_initial_stock_data(self, ticker: str) -> InitialCompanyResult:
        self.log.info("Running Gemini client.get_initial_stock_data...")
        prompt = f"""
        For company with ticker {ticker}, retrieve all required information about the company.

        First construct periods = quarters, the current (not yet reproted) quarter and 4 previous (already reported) quarters. 
        For all of them set quarter name, quarter id in required format (important), previous quarter report date and this quarter report date (or expected if not yet reported). 
        Double-check that the current quarter report date is later than the date of today, beacause if it's not, it's not the current quarter.
        Double-check the quarters names, ids and dates (important).

        Determine the original currency used in the company's official financial
        statements and set only its currency symbol in info.currency, for example
        $, €, or £. Do not return an ISO code and do not convert reported financial
        values into USD.

        Use primary financial sources in this order for every reported quarter:
        1. The company's official investor-relations earnings release and detailed
           quarterly financial statements.
        2. The company's official downloadable results files, including PDF and
           spreadsheet tables linked from its investor-relations page.
        3. Regulatory filings.
        Use reputable secondary sources only when primary sources are unavailable.
        Inspect the detailed tables rather than relying on search-result snippets,
        headlines, rounded narrative summaries, or margin percentages. Prefer an
        exact figure from an official table over a rounded figure from any summary.
        A value missing from a summary page is not evidence that it is unavailable.
        Before returning null for a reported value, search the official quarterly
        release, its detailed financial statements, and any linked results files
        for that exact quarter. Treat official labels such as "total net sales" as
        revenues and "income from operations" as operating income when applicable.

        Then, for all already reported quarters, retrieve reported data: revenues,
        gross profit, operating income, net income, capital expenditures, free
        cash flow, number of shares of the company, and dividends.
        Return financial totals in millions of the reporting currency stored in
        info.currency and the number of shares in millions of shares. For example,
        return 16130 for 16.13 billion in that reporting currency and 5104 for
        5.104 billion shares; never return absolute monetary or share amounts.
        Return EPS in the reporting currency per share. Return stock prices in
        the requested ticker's trading currency per share.

        reported_capex must represent capital expenditures for the individual
        fiscal quarter as a positive cash outflow amount. reported_free_cash_flow
        must represent free cash flow for the individual fiscal quarter. Prefer
        an explicitly reported free cash flow value; otherwise calculate it as
        cash flow from operating activities minus positive capital expenditures.
        Never use a year-to-date value as a quarterly value. When a cash-flow
        statement provides cumulative values, derive the standalone quarter by
        subtracting the previous cumulative period and use figures from the same
        accounting period and currency.

        Then, for all already reported quarters, I want you to create the interval between the dates (previous report date and current quarter report date)
        and compute the minimum and maximum price of the stock inside this interval (excluding the edge dates).

        Return quarters as an array containing exactly five quarter objects:
        first the current unreported quarter, then the four immediately preceding
        quarters from newest to oldest. Never return quarters as an object and
        never return an empty or shorter array.

        Never omit a quarter because a financial value or price range could not
        be found. For an unavailable reported financial value or price range,
        use null for that field and add an error explaining what could not be
        retrieved and why. For the current unreported quarter, use null for every
        financial value and price range that has not yet been reported; these
        expected nulls do not require errors. Quarter identity, ending month,
        previous report date, and actual or expected report date are structural
        data and must be present for every quarter. If a structural value is
        uncertain, use the best-supported value and describe the uncertainty and
        its reason in errors instead of omitting the quarter.

        Lastly, set the basic information for the company, including setting the ID of the current quarter (not yet reported). 

        The info.current_quarter_id value must exactly match the id of the first
        quarter in quarters, and that entry must identify the current unreported
        quarter. Every quarter id must be unique.

        Set errors to an empty list when retrieval completed without issues.
        Otherwise, add concise errors describing every unavailable reported
        value, conflicting source, uncertainty, or other retrieval problem and
        why it occurred. Identify every affected quarter by its YYQX id and name
        the affected fields whenever applicable. Never leave errors empty when
        any reported-quarter value is null. An error never permits omitting a
        quarter or any required structural field.
        """
        response = self.__request(prompt, InitialCompanyResponse)
        try:
            initial_company = InitialCompanyResponse.model_validate_json(
                response.text
            )
        except ValidationError as exception:
            raise InvalidInitialCompanyResponse(
                ticker=ticker,
                violations=[f"response validation failed: {exception}"],
                raw_response=response.text,
            ) from exception
        violations = self.__initial_company_violations(
            ticker,
            initial_company,
        )
        if violations:
            raise InvalidInitialCompanyResponse(
                ticker=ticker,
                violations=violations,
                raw_response=response.text,
            )
        company = Company(
            info=initial_company.info,
            quarters={
                quarter.id: Quarter.model_validate(quarter.model_dump())
                for quarter in initial_company.quarters
            },
        )
        return InitialCompanyResult(
            company=company,
            errors=tuple(initial_company.errors),
            raw_response=response.text,
        )

    @staticmethod
    def __initial_company_violations(
        ticker: str,
        response: InitialCompanyResponse,
    ) -> list[str]:
        violations = []
        if response.info.ticker != ticker:
            violations.append(
                f"response ticker {response.info.ticker} does not match {ticker}"
            )

        current_quarter_id = response.info.current_quarter_id
        quarter_ids = [quarter.id for quarter in response.quarters]
        if current_quarter_id not in quarter_ids:
            violations.append(
                f"current quarter {current_quarter_id} is missing"
            )
        elif quarter_ids[0] != current_quarter_id:
            violations.append(
                f"current quarter {current_quarter_id} is not first"
            )

        duplicate_quarters = sorted({
            quarter_id
            for quarter_id in quarter_ids
            if quarter_ids.count(quarter_id) > 1
        })
        if duplicate_quarters:
            violations.append(
                f"quarter IDs are duplicated: {duplicate_quarters}"
            )

        expected_quarters = set(
            _quarter_ids_ending_at(
                current_quarter_id,
                INITIAL_COMPANY_QUARTER_COUNT,
            )
        )
        actual_quarters = set(quarter_ids)
        if actual_quarters != expected_quarters:
            violations.append(
                "quarter set is invalid "
                f"(missing={sorted(expected_quarters - actual_quarters)}, "
                f"unexpected={sorted(actual_quarters - expected_quarters)})"
            )

        return violations

    def revalidate_report_dates(self, report_dates: ReportDates) -> ReportDates:
        self.log.info("Running Gemini client.revalidate_report_dates...")
        data = report_dates.model_dump(mode="json")
        prompt = f"""
        I provide you the list of current quarter report dates for companies, here: {data} 
        
        For every report date of the particular quarter of a company, check whether the report date is still valid,
        because sometimes the report date may change. 
        
        Update the dates in the list and send it back to me. 
        Do not change quarter or ticker values and do not reorder the list.
        """
        return self.__ask(prompt, ReportDates)

    def get_quarter_report(
        self,
        company_id: str,
        current_quarter: Quarter,
        currency: str,
    ) -> QuarterReportResult:
        self.log.info("Running Gemini client.get_quarter_report...")
        data = current_quarter.model_dump(mode="json")
        if currency == "$":
            completeness = """
            All reported financial and price fields except reported_capex and
            reported_free_cash_flow must be populated. Those two optional fields
            may be null when they cannot be found and verified. Return every
            value you did find.
            """
        else:
            completeness = """
            reported_revenues and reported_net_income must be populated. Other
            reported financial or price fields may be null when they cannot be
            found and verified. Return every value you did find.
            """
        prompt = f"""
        For company {company_id} there should be quarter {current_quarter.id} report from {current_quarter.report_date_this_quarter}. 
        Verify the date is really in the past, if not, don't change anything and return the same data.
        Verify the data of the report are already available, if not, don't change anything and return the same data.
        
        Otherwise, collect the report data according to this template {data}, fill empty values, don't change anything else.
        Specifically, we are looking for reported: revenues, gross profit,
        operating income, net income, capital expenditures, free cash flow,
        number of shares of the company, and dividends.
        The company's original financial reporting currency is {currency}. Return
        financial totals in millions of {currency} without converting them to USD,
        and return the number of shares in millions of shares. For example, return
        16130 for 16.13 billion {currency} and 5104 for 5.104 billion shares; never
        return absolute monetary or share amounts. Return EPS in {currency} per
        share. Return stock prices in the ticker's trading currency per share.

        reported_capex must represent capital expenditures for this individual
        fiscal quarter as a positive cash outflow amount. reported_free_cash_flow
        must represent free cash flow for this individual fiscal quarter. Prefer
        an explicitly reported free cash flow value; otherwise calculate it as
        cash flow from operating activities minus positive capital expenditures.
        Never use a year-to-date value as a quarterly value. When a cash-flow
        statement provides cumulative values, derive the standalone quarter by
        subtracting the previous cumulative period and use figures from the same
        accounting period and currency. Try to retrieve these two values, but
        reported_capex and reported_free_cash_flow are optional and may be null.
        
        For price_min and price_max, I want you to create the interval between the dates (previous report date and current quarter report date)
        and compute the minimum and maximum price of the stock inside this interval (excluding the edge dates).

        {completeness}

        Never change or omit the quarter id, name, ending month, previous report
        date, or current report date from the provided template.
        
        Return the filled template.
        """
        response = self.__request(prompt, Quarter)
        try:
            quarter = Quarter.model_validate_json(response.text)
        except ValidationError as exception:
            raise InvalidQuarterReportResponse(
                ticker=company_id,
                quarter_id=current_quarter.id,
                validation_error=exception,
                raw_response=response.text,
            ) from exception
        return QuarterReportResult(
            quarter=quarter,
            raw_response=response.text,
        )

    def get_price_targets(
        self,
        tickers: list[str],
        start_date: date,
        end_date: date,
    ) -> Targets:
        self.log.info("Running Gemini client.get_price_targets...")
        prompt = f"""
        You are a financial-data researcher extracting newly announced institutional
        equity analyst price-target actions.

        REQUESTED TICKERS:
        {tickers}

        INCLUSIVE DATE WINDOW:
        {start_date} through {end_date}

        Use Google Search extensively. For every requested ticker, first search as
        broadly as possible across institutional equity research providers for
        price-target actions announced during this exact date interval. Then assess
        the importance of every institution you found. Return only institutions that
        are highly reputable, influential, or significant in equity research,
        including recognized sector specialists. You must make this assessment from
        current evidence rather than from a fixed institution list. A newly
        established institution may qualify when there is strong evidence of its
        significance. Return no target rather than including an insignificant or
        uncertain institution.

        Return a Targets model whose targets field contains Target objects with
        these fields:

        - ticker: the exact ticker from REQUESTED TICKERS.
        - institution: the canonical name of the important institution that issued
          the target.
        - date: the date the institution announced the action, in YYYY-MM-DD format.
        - price: the newly announced target price in USD.
        - rating: the current rating exactly as stated by the source, or null when no
          rating is stated.
        - source: the most direct public URL supporting the complete record, or the
          source hostname only when a direct URL cannot be obtained.

        A valid Target must satisfy every rule below:

        - Its source explicitly identifies the company or ticker, institution, new
          target price, and action date.
        - date is the date the analyst action was announced, not the publication date
          of a later article repeating an older action.
        - price is the new target, not the previous target, current share price,
          consensus target, or an algorithmic forecast.
        - price is in USD. Exclude the record if its currency cannot be verified.
        - rating preserves the source's wording and is never inferred.
        - source supports all returned facts. Never invent or reconstruct a URL.
        - Prefer an institution publication, then a reputable financial publication
          or wire service, then an established analyst-action database.

        Reddit is never an acceptable source of information for this task. Do not
        use reddit.com links, Reddit posts, Reddit comments, or information copied
        from Reddit to establish any returned field or an institution's significance.
        If Reddit is the only source supporting a target, exclude that target.

        Exclude consensus targets, anonymous analysts, rumors, blogs, social-media
        posts, unsupported search snippets, stale reports republished during the
        interval, and duplicate syndicated reports. Deduplicate by ticker,
        institution, date, and price. If several sources describe the same action,
        retain only the strongest source.

        Before returning the Targets model, verify every field against its source.
        An empty targets list is the correct result when nothing qualifies.
        """
        return self.__ask(prompt, Targets)

    def get_target_report(self, target: Target) -> Target:
        self.log.info("Running Gemini client.get_target_report...")
        data = target.model_dump(mode="json", exclude={"report"})
        prompt = f"""
        {target.institution} recently issued a ${target.price} price target for
        {target.ticker}.

        Continue researching the context and significance of this price-target
        action using the complete target data below:
        {data}

        Determine the institution's stated rationale, material company or industry
        developments supporting the target, significant catalysts, assumptions,
        and risks. Include only information directly relevant to understanding this
        specific target action. Distinguish verified facts from analyst opinions,
        do not infer an unstated rationale, and do not repeat claims that cannot be
        supported by reliable public sources.

        Return a TargetReport containing a concise, self-contained overview and
        between one and four independently useful key takeaways, ordered from most
        to least important. Limit the overview to
        {TARGET_REPORT_OVERVIEW_MAX_LENGTH} characters and each takeaway to
        {TARGET_REPORT_TAKEAWAY_MAX_LENGTH} characters. Do not repeat the overview
        in the takeaways. Each takeaway must communicate one distinct, concrete
        point in plain text without a bullet prefix.
        """
        report = self.__ask(
            prompt,
            TargetReport,
            validation_context={
                "target": (
                    f"{target.ticker} / {target.institution} / "
                    f"{target.date.isoformat()} / ${target.price}"
                ),
            },
        )
        return target.model_copy(update={"report": report})

    def get_news_sentiment_analysis(
        self,
        companies: list[CompanyInsights],
    ) -> list[CompanySentimentAnalysis]:
        self.log.info(
            "Running Gemini client.get_news_sentiment_analysis..."
        )
        company_data = [
            {
                **company.model_dump(mode="json"),
                "statistics": SentimentStatistics.from_insights(
                    company.insights
                ).model_dump(mode="json"),
            }
            for company in companies
        ]
        data = json.dumps(
            company_data,
            ensure_ascii=False,
            separators=(",", ":"),
        )
        prompt = f"""
        Analyze the supplied Polygon news insights grouped by company:
        {data}

        Return a CompanySentimentSummaries model containing exactly one result
        for every input company, in the same order and with the same ticker.
        Never omit, add, duplicate, or reorder companies.

        Return only ticker and key_takeaways for each company. Do not return
        statistics; they were already calculated by the application and will
        be attached after your response. Your only analytical task is to
        produce key_takeaways.

        For key_takeaways, read only the supplied sentiment_reasoning values and
        synthesize up to five concise, non-duplicative takeaways per company.
        article_id identifies the source article. Treat the same article_id for
        the same company as one article and never duplicate its reasoning.
        Merge repeated reasoning and capture the most important recurring events,
        business drivers, catalysts, risks, and conflicting signals. Do not invent
        facts, use external knowledge, browse for more information, or merely
        restate the sentiment counts. A company with no insights must have an
        empty key_takeaways list.
        """
        response = self.__ask(
            prompt,
            CompanySentimentSummaries,
            use_google_search=False,
        )
        return self._combine_news_sentiment_analysis(companies, response)

    @staticmethod
    def _combine_news_sentiment_analysis(
        companies: list[CompanyInsights],
        response: CompanySentimentSummaries,
    ) -> list[CompanySentimentAnalysis]:
        expected_tickers = [company.ticker for company in companies]
        actual_tickers = [company.ticker for company in response.companies]
        if actual_tickers != expected_tickers:
            raise ValueError(
                "Gemini news sentiment company order differs from input: "
                f"expected={expected_tickers}, actual={actual_tickers}"
            )

        return [
            CompanySentimentAnalysis(
                ticker=summary.ticker,
                statistics=SentimentStatistics.from_insights(
                    source.insights
                ),
                key_takeaways=summary.key_takeaways,
            )
            for source, summary in zip(
                companies,
                response.companies,
                strict=True,
            )
        ]
