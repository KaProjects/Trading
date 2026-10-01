import logging
from decimal import Decimal

from gemini.models import InstitutionRating, InstitutionRecord, Quarter, Target
from myfinnhub.models import Earnings

logger = logging.getLogger(__name__)
DISCORD_EMBED_DESCRIPTION_MAX_LENGTH = 4096
# Finnhub's revenueEstimate/revenueActual are raw currency units; Gemini's
# reported_revenues is in millions.
FINNHUB_REVENUE_SCALE = Decimal(1_000_000)
DISCORD_SPACER = "\u200b"
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
    *,
    qoq_quarter: Quarter | None = None,
    yoy_quarter: Quarter | None = None,
    estimates: Earnings | None = None,
) -> dict[str, object]:
    return {
        "username": QUARTER_REPORTER_USERNAME,
        "avatar_url": QUARTER_REPORTER_AVATAR_URL,
        "embeds": [
            _quarter_report_embed(
                quarter,
                title=f"{ticker} - {quarter.name} report",
                qoq_quarter=qoq_quarter,
                yoy_quarter=yoy_quarter,
                estimates=estimates,
            )
        ],
    }


def ticker_quarter_report(
    quarter: Quarter,
    *,
    qoq_quarter: Quarter | None = None,
    yoy_quarter: Quarter | None = None,
    estimates: Earnings | None = None,
) -> dict[str, object]:
    return {
        "embeds": [
            _quarter_report_embed(
                quarter,
                title=f"{quarter.name} report",
                qoq_quarter=qoq_quarter,
                yoy_quarter=yoy_quarter,
                estimates=estimates,
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


def price_target(
    target: Target,
    *,
    rating: InstitutionRating | str | None = None,
) -> dict[str, object]:
    return {
        "username": TARGET_REPORTER_USERNAME,
        "avatar_url": TARGET_REPORTER_AVATAR_URL,
        "embeds": [
            _price_target_embed(target, ticker_prefix=True, rating=rating)
        ],
    }


def new_institutions(
    institutions: list[InstitutionRecord],
) -> dict[str, object]:
    return {
        "username": TARGET_REPORTER_USERNAME,
        "avatar_url": TARGET_REPORTER_AVATAR_URL,
        "embeds": [{
            "title": "🏦 New institutions",
            "color": 0xF1C40F,
            "description": "\n".join(
                institution.name for institution in institutions
            ),
        }],
    }


def price_targets(ticker: str, targets: list[Target]) -> dict[str, object]:
    return {
        "username": TARGET_REPORTER_USERNAME,
        "avatar_url": TARGET_REPORTER_AVATAR_URL,
        "embeds": [
            _price_targets_embed(targets, title=f"🎯 {ticker} | New price targets")
        ],
    }


def ticker_price_targets(targets: list[Target]) -> dict[str, object]:
    return {
        "embeds": [
            _price_targets_embed(targets, title="🎯 New price targets")
        ],
    }


def _price_targets_embed(
    targets: list[Target],
    *,
    title: str,
) -> dict[str, object]:
    return {
        "title": title,
        "color": 0xF1C40F,
        "description": "\n".join(
            f"{target.institution}: ${target.price}"
            for target in targets
        ),
    }


def ticker_price_target(
    target: Target,
    *,
    rating: InstitutionRating | str | None = None,
) -> dict[str, object]:
    return {
        "embeds": [
            _price_target_embed(target, ticker_prefix=False, rating=rating)
        ],
    }


def _price_target_embed(
    target: Target,
    *,
    ticker_prefix: bool,
    rating: InstitutionRating | str | None = None,
) -> dict[str, object]:
    price_target_title = f"Price target ${target.price} | {target.institution}"
    title = (
        f"🎯 {target.ticker} | {price_target_title}"
        if ticker_prefix
        else f"🎯 {price_target_title}"
    )

    field_value = (
        f"{target.rating or 'Not provided'}\n"
        f"{target.date.isoformat()}\n"
        f"source: {target.source}"
    )
    institution_rating = _format_institution_rating(rating)
    if institution_rating:
        field_value += f"\n\n{institution_rating}"

    return {
        "title": title,
        "color": 0xF1C40F,
        "fields": [
            {
                "name": DISCORD_SPACER,
                "value": field_value,
                "inline": False,
            },
        ],
        **_target_report_description(target),
    }


def _format_institution_rating(
    rating: InstitutionRating | str | None,
) -> str:
    # Older institution records may still carry the free-text rating this
    # replaced; only the structured shape has the two scores to show.
    if not isinstance(rating, InstitutionRating):
        return ""
    return (
        f"Institutional Weight: {rating.institutional_weight.score} — "
        f"{rating.institutional_weight.description}\n"
        f"Media Shock Value: {rating.media_shock_value.score} — "
        f"{rating.media_shock_value.description}"
    )


def _target_report_description(target: Target) -> dict[str, str]:
    if target.report is None:
        return {}

    takeaways = "\n".join(
        f"• {takeaway}"
        for takeaway in target.report.key_takeaways
    )
    description = (
        f"**Overview**\n{target.report.overview}\n\n"
        f"**Key takeaways**\n{takeaways}\n\n{DISCORD_SPACER}"
    )
    if len(description) > DISCORD_EMBED_DESCRIPTION_MAX_LENGTH:
        logger.warning(
            "Truncated Discord target report description for %s / %s / %s / "
            "$%s from %d to %d characters",
            target.ticker,
            target.institution,
            target.date.isoformat(),
            target.price,
            len(description),
            DISCORD_EMBED_DESCRIPTION_MAX_LENGTH,
        )
        description = (
            description[:DISCORD_EMBED_DESCRIPTION_MAX_LENGTH - 3]
            + "..."
        )
    return {"description": description}


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


def _percent_change(
    new: Decimal | None,
    old: Decimal | None,
) -> Decimal | None:
    # Dividing by abs(old) instead of old keeps the sign of the result
    # meaningful (an improving loss reads as positive) even when the
    # baseline is negative; only an exact zero baseline is unusable.
    if new is None or old is None or old == 0:
        return None
    return (new - old) / abs(old) * 100


def _format_signed_percent(value: Decimal, *, unit: str = "%") -> str:
    # No direction icon: the sign on the value already says up or down,
    # and every icon tried (colored emoji, plain triangle) either clashed
    # visually with plain text or just added noise on top of the sign.
    # Triple-digit-plus swings (common for a newly profitable or
    # fast-scaling quarter) don't need decimal precision.
    precision = 0 if abs(value) >= 100 else 1
    return f"{value:+.{precision}f}{unit}"


def _change_text(
    current: Decimal | None,
    qoq_quarter: Quarter | None,
    yoy_quarter: Quarter | None,
    field: str,
    *,
    unit: str = "%",
) -> str:
    # The QoQ/YoY order is explained once in the section's field name
    # ("Financials (QoQ/YoY)"), so when both are present the values alone
    # ("+12.5% +27.5%") are unambiguous. Only fall back to a per-value
    # label when just one side is available, since position alone can't
    # disambiguate a single lone value.
    qoq_change = (
        _percent_change(current, getattr(qoq_quarter, field))
        if qoq_quarter is not None
        else None
    )
    yoy_change = (
        _percent_change(current, getattr(yoy_quarter, field))
        if yoy_quarter is not None
        else None
    )
    if qoq_change is not None and yoy_change is not None:
        return (
            f"{_format_signed_percent(qoq_change, unit=unit)}, "
            f"{_format_signed_percent(yoy_change, unit=unit)}"
        )
    if qoq_change is not None:
        return f"{_format_signed_percent(qoq_change, unit=unit)} QoQ"
    if yoy_change is not None:
        return f"{_format_signed_percent(yoy_change, unit=unit)} YoY"
    return ""


def _financial_line(
    label: str,
    quarter: Quarter,
    qoq_quarter: Quarter | None,
    yoy_quarter: Quarter | None,
    field: str,
) -> str:
    # One logical line, QoQ/YoY detail trailing in italicized parens -
    # Discord's own word-wrap already puts it on a second line on a
    # narrow (mobile) screen while keeping it on one line where it fits.
    current = getattr(quarter, field)
    line = f"{label}: {format_financial(current) or '-'}"
    change = _change_text(current, qoq_quarter, yoy_quarter, field)
    if change:
        line += f" *({change})*"
    return line


def _eps_line(quarter: Quarter) -> str:
    # No QoQ/YoY here: EPS moves in lockstep with Net Income, which
    # already carries that comparison above it.
    current = quarter.reported_eps
    return f"EPS: {current if current is not None else '-'}"


def _margin(
    profit: Decimal | None,
    revenue: Decimal | None,
) -> Decimal | None:
    # A non-positive revenue makes profit / revenue meaningless (sign
    # flips, or a division by zero), regardless of the profit's own sign.
    if profit is None or revenue is None or revenue <= 0:
        return None
    return profit / revenue * 100


def _margin_change_text(
    current_margin: Decimal,
    qoq_quarter: Quarter | None,
    yoy_quarter: Quarter | None,
    profit_field: str,
) -> str:
    qoq_margin = (
        _margin(
            getattr(qoq_quarter, profit_field), qoq_quarter.reported_revenues
        )
        if qoq_quarter is not None
        else None
    )
    yoy_margin = (
        _margin(
            getattr(yoy_quarter, profit_field), yoy_quarter.reported_revenues
        )
        if yoy_quarter is not None
        else None
    )
    qoq_delta = None if qoq_margin is None else current_margin - qoq_margin
    yoy_delta = None if yoy_margin is None else current_margin - yoy_margin
    if qoq_delta is not None and yoy_delta is not None:
        return (
            f"{_format_signed_percent(qoq_delta, unit='pp')}, "
            f"{_format_signed_percent(yoy_delta, unit='pp')}"
        )
    if qoq_delta is not None:
        return f"{_format_signed_percent(qoq_delta, unit='pp')} QoQ"
    if yoy_delta is not None:
        return f"{_format_signed_percent(yoy_delta, unit='pp')} YoY"
    return ""


def _margin_line(
    label: str,
    quarter: Quarter,
    qoq_quarter: Quarter | None,
    yoy_quarter: Quarter | None,
    profit_field: str,
) -> str | None:
    current_margin = _margin(
        getattr(quarter, profit_field),
        quarter.reported_revenues,
    )
    if current_margin is None:
        return None

    line = f"{label}: {current_margin:.1f}%"
    change = _margin_change_text(
        current_margin, qoq_quarter, yoy_quarter, profit_field
    )
    if change:
        line += f" *({change})*"
    return line


def _estimates_field(
    quarter: Quarter,
    estimates: Earnings | None,
) -> dict[str, object] | None:
    if estimates is None:
        return None

    # The actual value already appears as the headline figure in
    # Financials; repeating it here would be redundant, so this only
    # states the estimate and the beat/miss result against it.
    lines = []
    eps_surprise = _percent_change(quarter.reported_eps, estimates.epse)
    if eps_surprise is not None:
        verb = "beat" if eps_surprise >= 0 else "miss"
        lines.append(
            f"EPS: est. {estimates.epse} "
            f"*({verb} {_format_signed_percent(eps_surprise)})*"
        )
    # Finnhub reports revenue in raw currency units; Gemini's reported_*
    # fields are in millions, so the estimate must be rescaled before it's
    # comparable.
    revenue_estimate = (
        estimates.reve / FINNHUB_REVENUE_SCALE
        if estimates.reve is not None
        else None
    )
    revenue_surprise = _percent_change(
        quarter.reported_revenues, revenue_estimate
    )
    if revenue_surprise is not None:
        verb = "beat" if revenue_surprise >= 0 else "miss"
        lines.append(
            f"Revenue: est. {format_financial(revenue_estimate)} "
            f"*({verb} {_format_signed_percent(revenue_surprise)})*"
        )
    if not lines:
        return None
    return {"name": "vs. Estimates", "value": "\n".join(lines), "inline": False}


def _quarter_report_embed(
    quarter: Quarter,
    *,
    title: str,
    qoq_quarter: Quarter | None = None,
    yoy_quarter: Quarter | None = None,
    estimates: Earnings | None = None,
) -> dict[str, object]:
    financials_value = "\n".join([
        _financial_line(
            "Revenue", quarter, qoq_quarter, yoy_quarter, "reported_revenues"
        ),
        _financial_line(
            "Gross Profit",
            quarter,
            qoq_quarter,
            yoy_quarter,
            "reported_gross_profit",
        ),
        _financial_line(
            "Oper. Income",
            quarter,
            qoq_quarter,
            yoy_quarter,
            "reported_operating_income",
        ),
        _financial_line(
            "Net Income",
            quarter,
            qoq_quarter,
            yoy_quarter,
            "reported_net_income",
        ),
        f"CapEx: {format_financial(quarter.reported_capex) or '-'}",
        (
            "Free Cash Flow: "
            f"{format_financial(quarter.reported_free_cash_flow) or '-'}"
        ),
        f"Divs: {format_financial(quarter.reported_div) or '-'}",
        f"Shares: {format_financial(quarter.reported_shares) or '-'}",
        _eps_line(quarter),
    ])
    # The QoQ/YoY order is stated once in the section name instead of on
    # every line - see _change_text.
    change_suffix = (
        " (QoQ/YoY)" if qoq_quarter is not None or yoy_quarter is not None
        else ""
    )
    fields = [
        {
            "name": f"**Financials**{change_suffix}",
            "value": financials_value,
            "inline": False,
        },
    ]

    estimates_field = _estimates_field(quarter, estimates)
    if estimates_field is not None:
        fields.append(estimates_field)

    margin_lines = [
        line
        for line in (
            _margin_line(
                label, quarter, qoq_quarter, yoy_quarter, profit_field
            )
            for label, profit_field in (
                ("Gross Margin", "reported_gross_profit"),
                ("Oper. Margin", "reported_operating_income"),
                ("Net Margin", "reported_net_income"),
            )
        )
        if line is not None
    ]
    if margin_lines:
        fields.append({
            "name": "Margins",
            "value": "\n".join(margin_lines),
            "inline": False,
        })

    fields.append({
        "name": "Price Range (from previous report)",
        "value": f"Low: ${quarter.price_min} — High: ${quarter.price_max}",
        "inline": False,
    })

    return {
        "title": title,
        "description": (
            f"ending: {quarter.ending_month} | "
            f"reported: {quarter.report_date_this_quarter}"
        ),
        "color": 3066993,
        "fields": fields,
    }
