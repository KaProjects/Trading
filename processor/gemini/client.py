from google import genai
from google.genai import types
from pydantic import BaseModel

from gemini.models import Company, ReportDates, Quarter
from utils import BaseClass


class GeminiClient(BaseClass):
    def __init__(self, api_key, model, parent, **kwargs):
        super().__init__(identity=parent+".GeminiClient", **kwargs)
        self.model = model
        self.client = genai.Client(api_key=api_key)

    def __ask(self, prompt: str, response_model: type[BaseModel]):
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
        """
        return self.__ask(prompt, Company)

    def revalidate_report_dates(self, report_dates: ReportDates) -> ReportDates:
        data = report_dates.model_dump()
        prompt = f"""
        I provide you the list of current quarter report dates for companies, here: {data} 
        
        For every report date of the particular quarter of a company, check whether the report date is still valid,
        because sometimes the report date may change. 
        
        Update the dates in the list and send it back to me. 
        Do not change quarter or ticker values and do not reorder the list.
        """
        return self.__ask(prompt, ReportDates)

    def get_quarter_report(self, company_id, current_quarter: Quarter):
        data = current_quarter.model_dump()
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
