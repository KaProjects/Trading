
class LogMsg:
    COMPANY_INIT = "company {company_id} initiated with {n_quarters} quarters with {quarter_id} being the current."
    REPORT_DATE_UPDATED = "report date updated {previous_date} -> {new_date} for company {company_id} and quarter {quarter_id}."
    QUARTER_REPORTED = "{company_id} - {quarter_id} reported on {date}."
    QUARTER_CREATED = "{company_id} - quarter {quarter_id} created."
    TARGET_UPSERTED = "{company_id} - price target {target_id} persisted."
    TARGETS_RETRIEVED = (
        "retrieved {target_count} price targets for {company_count} companies "
        "from {start_date} through {end_date}."
    )

class ErrorMsg:
    QUARTER_NOT_FOUND = "quarter {quarter_id} not found for {company_id}"
    QUARTER_REPORT_FAILED = "failed getting report for quarter {quarter_id} of {company_id}"
    SHOULD_RUN_ON_SUNDAY = "should run on Sunday, but is {today}"
