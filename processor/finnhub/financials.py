import finnhub

import utils

envs = utils.parse("../envs.json")
finnhub_client = finnhub.Client(api_key=envs["finnhub_api_key"])

class FinancialQuarter:
    def __init__(self, year, quarter, end_date, revenue):
        self.year = year
        self.quarter = quarter
        self.end_date = end_date
        self.revenue = revenue
        self.n_revenue = revenue

    def __repr__(self):
        return {"year": self.year, "quarter": self.quarter, "end_date": self.end_date, "revenue": self.revenue, "n_revenue": self.n_revenue}

    def __str__(self):
        return str(self.__repr__())

def sort_report(e: FinancialQuarter):
    return e.end_date

if __name__ == "__main__":
    response = finnhub_client.financials_reported(**{'symbol': 'SMCI', 'freq': 'annual'})
    reports = list()
    for report in response["data"]:
        reports.append(FinancialQuarter(report["year"], 4, report["endDate"], report["report"]["ic"][0]["value"]))

    response = finnhub_client.financials_reported(**{'symbol': 'SMCI', 'freq': 'quarterly'})
    for report in response["data"]:
        reports.append(FinancialQuarter(report["year"], report["quarter"], report["endDate"], report["report"]["ic"][0]["value"]))

    mapp = dict()
    normalized_reports = list()
    for report in reports:
        if report.year not in mapp:
            mapp[report.year] = dict()
        mapp[report.year][report.quarter] = report

    for key in mapp.keys():
        year = mapp[key]
        if 4 in year.keys():
            if 3 in year.keys():
                year[4].n_revenue = year[4].n_revenue - year[3].n_revenue
                if 2 in year.keys():
                    year[3].n_revenue = year[3].n_revenue - year[2].n_revenue
                    if 1 in year.keys():
                        year[2].n_revenue = year[2].n_revenue - year[1].n_revenue
                        normalized_reports.append(year[1])
                    normalized_reports.append(year[2])
                normalized_reports.append(year[3])
            normalized_reports.append(year[4])



    normalized_reports.sort(reverse=True, key=sort_report)

    for report in normalized_reports:
        print(report)

