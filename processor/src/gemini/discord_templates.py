from gemini.models import Quarter, Target

QUARTER_REPORTER_USERNAME = "Quarterly Results Reporter"
QUARTER_REPORTER_AVATAR_URL = (
    "https://cdn-icons-png.flaticon.com/512/1390/1390704.png"
)
TARGET_REPORTER_USERNAME = "Institutional Price Target Reporter"
TARGET_REPORTER_AVATAR_URL = (
    "https://cdn-icons-png.flaticon.com/512/1872/1872505.png"
)


def quarter_report(
    quarter: Quarter,
    ticker: str,
) -> dict[str, object]:
    return {
        "username": QUARTER_REPORTER_USERNAME,
        "avatar_url": QUARTER_REPORTER_AVATAR_URL,
        "embeds": [
            _quarter_report_embed(
                quarter,
                title=f"{ticker} - {quarter.name} report",
            )
        ],
    }


def ticker_quarter_report(quarter: Quarter) -> dict[str, object]:
    return {
        "embeds": [
            _quarter_report_embed(
                quarter,
                title=f"{quarter.name} report",
            )
        ],
    }


def quarter_report_link(
    ticker: str,
    message_url: str,
) -> dict[str, object]:
    return {
        "username": QUARTER_REPORTER_USERNAME,
        "avatar_url": QUARTER_REPORTER_AVATAR_URL,
        "content": (
            f"**{ticker} reported earnings.** "
            f"[View the report in #{ticker}]({message_url})"
        ),
    }


def price_target(target: Target) -> dict[str, object]:
    return {
        "username": TARGET_REPORTER_USERNAME,
        "avatar_url": TARGET_REPORTER_AVATAR_URL,
        "embeds": [{
            "title": (
                f"🎯 {target.ticker} | ${target.price} | "
                f"{target.date.isoformat()}"
            ),
            "color": 0xF1C40F,
            "fields": [
                {
                    "name": target.institution,
                    "value": target.rating or "Not provided",
                    "inline": False,
                },
                {
                    "name": "Source",
                    "value": target.source,
                    "inline": False,
                },
            ],
        }],
    }


def ticker_price_target(target: Target) -> dict[str, object]:
    return {
        "embeds": [{
            "title": f"🎯 new price target ${target.price}",
            "color": 0xF1C40F,
            "fields": [{
                "name": target.institution,
                "value": (
                    f"{target.rating or 'Not provided'}\n"
                    f"{target.date.isoformat()}\n"
                    f"source: {target.source}"
                ),
                "inline": False,
            }],
        }],
    }


def upcoming_earnings(
    fields: list[dict[str, object]],
) -> dict[str, object]:
    return {
        "username": QUARTER_REPORTER_USERNAME,
        "avatar_url": QUARTER_REPORTER_AVATAR_URL,
        "embeds": [{
            "title": "📅 Upcoming Earnings Reports",
            "color": 3447003,
            "fields": fields,
        }],
    }


def format_financial(value: object) -> str:
    if value is None:
        return ""
    try:
        result = float(value)
    except (ValueError, TypeError):
        return ""
    if result == 0:
        return "-"
    if abs(result) >= 1000:
        return str(round(result / 1000, 2)) + "B"
    return str(round(result, 2)) + "M"


def _quarter_report_embed(
    quarter: Quarter,
    *,
    title: str,
) -> dict[str, object]:
    return {
        "title": title,
        "description": (
            f"ending: {quarter.ending_month} | "
            f"reported: {quarter.report_date_this_quarter}"
        ),
        "color": 3066993,
        "fields": [
            {
                "name": "Financials",
                "value": (
                    f"**Revenues:** {format_financial(quarter.reported_revenues)}\n"
                    f"**Gross Profit:** {format_financial(quarter.reported_gross_profit)}\n"
                    f"**Oper. Income:** {format_financial(quarter.reported_operating_income)}\n"
                    f"**Net Income:** {format_financial(quarter.reported_net_income)}\n"
                    f"**Divs:** {format_financial(quarter.reported_div)}\n"
                    f"**Shares:** {format_financial(quarter.reported_shares)}\n"
                    f"**EPS:** {quarter.reported_eps}"
                ),
                "inline": False,
            },
            {
                "name": "Price Range (from previous report)",
                "value": (
                    f"Low: **${quarter.price_min}** — "
                    f"High: **${quarter.price_max}**"
                ),
                "inline": False,
            },
        ],
    }
