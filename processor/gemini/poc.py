from google import genai
from google.genai import types
from pydantic import BaseModel, Field
from typing import List

import utils

envs = utils.parse("../envs.json")

client = genai.Client(api_key=envs["gemini_api_key"])

class Quarter(BaseModel):
    name: str = Field(description="Name of the quarter.")
    id: str = Field(description="in format YYQX")
    report_date: str = Field(description="report date YYYY-MM-DD")
    ending_month: str = Field(description="end month of the quarter in format YY-MM")
    reported_eps: str = Field(description="reported eps")
    reported_revenues: str = Field(description="reported revenues, in millions of USD")
    reported_cogs: str = Field(description="reported cost of goods, in millions of USD")
    reported_opex: str = Field(description="reported operating expenses, in millions of USD")
    reported_ni: str = Field(description="reported net income, in millions of USD")
    reported_div: str = Field(description="reported dividends, in millions of USD")
    shares: str = Field(description="number of shares in reported period, in millions")
    previous_quarter_report_date: str = Field(description="date of the previous quarter report in format YYYY-MM-DD")
    price_low: str = Field(description="the lowest price in the period from this quarter report date until previous quarter report date")
    price_high: str = Field(description="the highest price in the period from this quarter report date until previous quarter report date")



class Output(BaseModel):
    ticker: str = Field(description="ticker of the company")
    date: str = Field(description="date of this data creation")
    quarters: List[Quarter]

prompt = """
for company with ticker MU, retrieve last 4 reported quarters, including required information
"""

if __name__ == "__main__":
    print("thinking...")

    response = client.models.generate_content(
        # model="gemini-3-flash-preview",
        model="gemini-3.1-pro-preview",
        contents=prompt,
        config={
            "tools": [types.Tool(google_search=types.GoogleSearch())],
            "response_mime_type": "application/json",
            "response_json_schema": Output.model_json_schema(),
        },
    )

    recipe = Output.model_validate_json(response.text)
    print(recipe.model_dump_json())

