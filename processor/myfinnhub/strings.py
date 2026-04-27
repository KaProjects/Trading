
class LogMsg:
    COMPANY_INIT = "{company_id} initiated with {n_quarters} quarter(s)."
    QUARTER_INIT = "{company_id} - new quarter {quarter_id} found."
    NEW_EARNINGS = "{company_id} change in earnings detected for quarter {quarter_id}"
    NO_CHANGE = "{company_id} no change detected"

class ErrorMsg:
    NO_EARNINGS_FOUND = "[Err] no earnings found for {company_id}"
    ERROR_PROCESSING_COMPANY = "[Err] error while processing {company_id}\n{trace}"
