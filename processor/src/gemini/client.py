import logging
from datetime import date

from google import genai
from google.genai import types
from pydantic import BaseModel

from gemini.models import Company, Quarter, ReportDates, Targets

logger = logging.getLogger(__name__)
GEMINI_RETRY_ATTEMPTS = 5
GEMINI_RETRY_INITIAL_DELAY_SECONDS = 2.0
GEMINI_RETRY_MAX_DELAY_SECONDS = 30.0
GEMINI_RETRYABLE_HTTP_STATUS_CODES = [408, 429, 500, 502, 503, 504]


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
    ):
        response = self.client.models.generate_content(
            model=self.model,
            contents=prompt,
            config={
                "tools": [types.Tool(google_search=types.GoogleSearch())],
                "response_mime_type": "application/json",
                "response_json_schema": response_model.model_json_schema(),
            },
        )
        return response_model.model_validate_json(response.text)

    def get_initial_stock_data(self, ticker: str) -> Company:
        prompt = f"""
        For company with ticker {ticker}, retrieve all required information about the company.

        First construct periods = quarters, the current (not yet reproted) quarter and 4 previous (already reported) quarters. 
        For all of them set quarter name, quarter id in required format (important), previous quarter report date and this quarter report date (or expected if not yet reported). 
        Double-check that the current quarter report date is later than the date of today, beacause if it's not, it's not the current quarter.
        Double-check the quarters names, ids and dates (important).

        Then, for all already reported quarters, retrieve reported data: revenues, gross profit, operating income, net income, number of shares of the company and dividends. 

        Then, for all already reported quarters, I want you to create the interval between the dates (previous report date and current quarter report date)
        and compute the minimum and maximum price of the stock inside this interval (excluding the edge dates).

        Already reported quarters should have all the values set (no n/a allowed), for the current quarter let the un-reported values as empty string.

        Lastly, set the basic information for the company, including setting the ID of the current quarter (not yet reported). 

        Key of the quarter is its ID.
        Set targets to an empty object. Price targets are retrieved separately.
        """
        return self.__ask(prompt, Company)

    def revalidate_report_dates(self, report_dates: ReportDates) -> ReportDates:
        data = report_dates.model_dump(mode="json")
        prompt = f"""
        I provide you the list of current quarter report dates for companies, here: {data} 
        
        For every report date of the particular quarter of a company, check whether the report date is still valid,
        because sometimes the report date may change. 
        
        Update the dates in the list and send it back to me. 
        Do not change quarter or ticker values and do not reorder the list.
        """
        return self.__ask(prompt, ReportDates)

    def get_quarter_report(self, company_id, current_quarter: Quarter):
        data = current_quarter.model_dump(mode="json")
        prompt = f"""
        For company {company_id} there should be quarter {current_quarter.id} report from {current_quarter.report_date_this_quarter}. 
        Verify the date is really in the past, if not, don't change anything and return the same data.
        Verify the data of the report are already available, if not, don't change anything and return the same data.
        
        Otherwise, collect the report data according to this template {data}, fill empty values, don't change anything else.
        Specifically, we are looking for reported: revenues, gross profit, operating income, net income, number of shares of the company and dividends. 
        
        For price_min and price_max, I want you to create the interval between the dates (previous report date and current quarter report date)
        and compute the minimum and maximum price of the stock inside this interval (excluding the edge dates).

        The data template should now have all the values set,no n/a or empty strings are allowed.
        
        Return the filled template.
        """
        return self.__ask(prompt, Quarter)

    def get_price_targets(
        self,
        tickers: list[str],
        start_date: date,
        end_date: date,
    ) -> Targets:
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

        Exclude consensus targets, anonymous analysts, rumors, blogs, social-media
        posts, unsupported search snippets, stale reports republished during the
        interval, and duplicate syndicated reports. Deduplicate by ticker,
        institution, date, and price. If several sources describe the same action,
        retain only the strongest source.

        Before returning the Targets model, verify every field against its source.
        An empty targets list is the correct result when nothing qualifies.
        """
        return self.__ask(prompt, Targets)
