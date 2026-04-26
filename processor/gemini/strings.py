

class LogMsg:
    COMPANY_INIT = "company {company_id} initiated with {n_quarters} quarters with {quarter_id} being the current."
    REPORT_DATE_UPDATED = "report date updated {previous_date} -> {new_date} for company {company_id} and quarter {quarter_id}."
    QUARTER_REPORTED = "{company_id} - {quarter_id} reported on {date}."
    QUARTER_CREATED = "{company_id} - quarter {quarter_id} created."

class ErrorMsg:
    QUARTER_NOT_FOUND = "[Err] quarter {quarter_id} not found for {company_id}"
    QUARTER_REPORT_FAILED = "[Err] failed getting report for quarter {quarter_id} of {company_id}"
