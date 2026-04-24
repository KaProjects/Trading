from google import genai
from google.genai import types

from pydantic import BaseModel

from gemini.models import Company
from utils import BaseClass


class GeminiClient(BaseClass):
    def __init__(self, gemini_api_key, model, **kwargs):
        super().__init__(**kwargs)
        self.model = model
        self.client = genai.Client(api_key=gemini_api_key)

    def __ask(self, prompt: str, model: type[BaseModel]):
        response = self.client.models.generate_content(
            model=self.model,
            contents=prompt,
            config={
                "tools": [types.Tool(google_search=types.GoogleSearch())],
                "response_mime_type": "application/json",
                "response_json_schema": model.model_json_schema(),
            },
        )
        return model.model_validate_json(response.text)

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
