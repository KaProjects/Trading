

class LogMsg:
    COMPANY_INIT = "company {company_id} initiated with {n_quarters} quarters with {quarter_id} being the current."
    REPORT_DATE_UPDATED = "report date updated {previous_date} -> {new_date} for company {company_id} and quarter {quarter_id}."

class ErrorMsg:
    QUARTER_NOT_FOUND = "Quarter {quarter_id} not found for {company_id}"
