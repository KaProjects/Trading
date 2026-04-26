from pydantic import BaseModel, Field


class Info(BaseModel):
    ticker: str = Field(description="ticker of the company")
    last_update: str = Field(description="date of this data creation")
    current_quarter_id: str = Field(description="in format YYQX")

class Quarter(BaseModel):
    name: str = Field(description="Name of the quarter.")
    id: str = Field(description="in format YYQX")
    ending_month: str = Field(description="end month of the quarter in format YY-MM")
    report_date_previous_quarter: str = Field(description="date of the previous quarter report in format YYYY-MM-DD")
    report_date_this_quarter: str = Field(description="report date of this quarter in format YYYY-MM-DD")
    reported_eps: str = Field(description="reported eps")
    reported_revenues: str = Field(description="reported revenues, in millions of USD")
    reported_gross_profit: str = Field(description="reported gross profit, in millions of USD")
    reported_operating_income: str = Field(description="reported operating income, in millions of USD, (should equal gross profit minus operating expenses)")
    reported_net_income: str = Field(description="reported net income, in millions of USD")
    reported_div: str = Field(description="reported dividends, in millions of USD")
    reported_shares: str = Field(description="number of shares in reported period, in millions")
    price_min: str = Field(description="minimum price in the period from previous quarter report date until this quarter report date, excluding those edge days")
    price_max: str = Field(description="maximum price in the period from previous quarter report date until this quarter report date, excluding those edge days")

class Company(BaseModel):
    info: Info
    quarters: dict[str, Quarter]

class ReportDate(BaseModel):
    ticker: str = Field(description="ticker of the company")
    quarter: str = Field(description="if of the current quarter")
    report_date: str = Field(description="date of the quarterly report in format YYYY-MM-DD")

class ReportDates(BaseModel):
    report_dates: list[ReportDate]
