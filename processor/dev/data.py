from datetime import date, timedelta
from decimal import Decimal

from cmc.models import (
    BitcoinQuote,
    FearAndGreedReading,
)
from gemini.models import (
    Company as GeminiCompany,
    Info,
    Quarter as GeminiQuarter,
    Target,
    TargetReport,
)
from myfinnhub.models import (
    Company as FinnhubCompany,
    Earnings,
)
from polygon.models import NewsResponse


def cmc_fear_and_greed() -> FearAndGreedReading:
    return FearAndGreedReading.model_validate({
        "data": {
            "value": 18,
            "value_classification": "Extreme fear",
        },
    })


def cmc_bitcoin_quote() -> BitcoinQuote:
    return BitcoinQuote.model_validate({
        "data": {
            "BTC": [{
                "quote": {
                    "USD": {
                        "price": "63125.42",
                    },
                },
            }],
        },
    })


def polygon_news(tickers: list[str] | None = None) -> NewsResponse:
    return _polygon_news_response(
        sorted(set(tickers or ["ACME", "NEWC", "FUTR", "STBL"])),
        articles_per_ticker=2,
        id_group="market",
    )


def _polygon_news_response(
    company_tickers: list[str],
    *,
    articles_per_ticker: int,
    id_group: str,
) -> NewsResponse:
    today = date.today()
    sentiment_profiles = [
        (
            "positive",
            "Demand accelerated and management raised its revenue outlook.",
        ),
        (
            "neutral",
            "Management maintained guidance while monitoring market demand.",
        ),
        (
            "negative",
            "Higher input costs put near-term pressure on operating margins.",
        ),
        (
            "mixed",
            "Bookings improved, but execution risks remain for the expansion.",
        ),
    ]
    results = []
    for ticker_index, ticker in enumerate(company_tickers):
        for article_index in range(articles_per_ticker):
            sentiment, reasoning = sentiment_profiles[
                (ticker_index + article_index) % len(sentiment_profiles)
            ]
            days_ago = (ticker_index + article_index * 3) % 8
            article_id = (
                f"fake-{id_group}-{ticker.lower()}-{article_index + 1}"
            )
            results.append({
                "id": article_id,
                "publisher": {
                    "name": "Development Market Wire",
                    "homepage_url": "https://news.example.com",
                },
                "title": f"{ticker} development market update",
                "author": "Development Reporter",
                "published_utc": (
                    f"{(today - timedelta(days=days_ago)).isoformat()}"
                    "T12:00:00Z"
                ),
                "article_url": f"https://news.example.com/{article_id}",
                "tickers": [ticker],
                "description": (
                    f"Development-only company news for {ticker}."
                ),
                "keywords": ["development", "market"],
                "insights": [{
                    "ticker": ticker,
                    "sentiment": sentiment,
                    "sentiment_reasoning": f"{ticker}: {reasoning}",
                }],
            })
    return NewsResponse.model_validate({
        "count": len(results),
        "status": "OK",
        "request_id": "fake-polygon-request",
        "results": results,
    })


def _quarter_parts(offset: int) -> tuple[int, int]:
    today = date.today()
    absolute_quarter = (
        today.year * 4
        + (today.month - 1) // 3
        + offset
    )
    year, zero_based_quarter = divmod(absolute_quarter, 4)
    return year, zero_based_quarter + 1


def _quarter_id(year: int, quarter: int) -> str:
    return f"{year % 100:02d}Q{quarter}"


def _ending_month(year: int, quarter: int) -> str:
    return f"{year % 100:02d}-{quarter * 3:02d}"


def _gemini_quarter(
    offset: int,
    report_date: date,
    *,
    reported: bool,
) -> GeminiQuarter:
    year, quarter = _quarter_parts(offset)
    scale = Decimal(str(abs(offset) + 1))
    financials = {
        "reported_eps": Decimal("1.25") + scale / 10,
        "reported_revenues": Decimal("42500") + scale * 750,
        "reported_gross_profit": Decimal("18300") + scale * 300,
        "reported_operating_income": Decimal("9200") + scale * 175,
        "reported_net_income": Decimal("7800") + scale * 150,
        "reported_div": Decimal("0.24"),
        "reported_shares": Decimal("15400"),
        "price_min": Decimal("171.35") + scale,
        "price_max": Decimal("198.80") + scale,
    }
    if not reported:
        financials = {}

    return GeminiQuarter(
        name=f"Q{quarter} {year}",
        id=_quarter_id(year, quarter),
        ending_month=_ending_month(year, quarter),
        report_date_previous_quarter=report_date - timedelta(days=91),
        report_date_this_quarter=report_date,
        **financials,
    )


def _gemini_company(
    ticker: str,
    current_offset: int,
    current_report_date: date,
    history_depth: int = 4,
) -> GeminiCompany:
    quarters: dict[str, GeminiQuarter] = {}
    for offset in range(current_offset - history_depth, current_offset):
        age = current_offset - offset
        historical_date = current_report_date - timedelta(days=age * 91)
        quarter = _gemini_quarter(
            offset,
            historical_date,
            reported=True,
        )
        quarters[quarter.id] = quarter

    current_quarter = _gemini_quarter(
        current_offset,
        current_report_date,
        reported=False,
    )
    quarters[current_quarter.id] = current_quarter
    return GeminiCompany(
        info=Info(
            ticker=ticker,
            last_update=date.today() - timedelta(days=1),
            current_quarter_id=current_quarter.id,
        ),
        quarters=quarters,
    )


def gemini_firebase_companies() -> dict[str, GeminiCompany | None]:
    today = date.today()
    return {
        "ACME": _gemini_company(
            ticker="ACME",
            current_offset=-1,
            current_report_date=today - timedelta(days=2),
        ),
        "NEWC": None,
        "FUTR": _gemini_company(
            ticker="FUTR",
            current_offset=0,
            current_report_date=today + timedelta(days=3),
        ),
    }


def gemini_initial_company(ticker: str) -> GeminiCompany:
    return _gemini_company(
        ticker=ticker,
        current_offset=0,
        current_report_date=date.today() + timedelta(days=30),
    )


def gemini_reported_quarter(
    quarter: GeminiQuarter,
) -> GeminiQuarter:
    return quarter.model_copy(
        deep=True,
        update={
            "reported_eps": Decimal("2.84"),
            "reported_revenues": Decimal("48250"),
            "reported_gross_profit": Decimal("21100"),
            "reported_operating_income": Decimal("10650"),
            "reported_net_income": Decimal("9125"),
            "reported_div": Decimal("0.28"),
            "reported_shares": Decimal("15320"),
            "price_min": Decimal("182.15"),
            "price_max": Decimal("214.70"),
        },
    )


def gemini_price_targets(
    tickers: list[str],
    start_date: date,
    end_date: date,
) -> list[Target]:
    institutions = (
        ("Northstar Global Research", "Outperform"),
        ("Summit Capital Markets", "Buy"),
    )
    targets: list[Target] = []
    for ticker_index, ticker in enumerate(tickers):
        for institution_index, (institution, rating) in enumerate(
            institutions
        ):
            day_offset = (ticker_index + institution_index) % 7
            target_date = min(
                start_date + timedelta(days=day_offset),
                end_date,
            )
            targets.append(Target(
                ticker=ticker,
                institution=institution,
                date=target_date,
                price=Decimal("150")
                + Decimal(ticker_index * 10 + institution_index * 5),
                rating=rating,
                source=(
                    "https://research.example.com/"
                    f"{ticker.lower()}/{target_date.isoformat()}"
                    f"-{institution_index}"
                ),
            ))
    return targets


def gemini_target_report(target: Target) -> TargetReport:
    return TargetReport(
        overview=(
            f"{target.institution} set a ${target.price} price target for "
            f"{target.ticker}."
        ),
        key_takeaways=[
            f"The published rating is {target.rating or 'not provided'}.",
            f"The target action was announced on {target.date.isoformat()}.",
        ],
    )


def _finnhub_earnings(
    report_date: date,
    *,
    epse: str,
    reve: str,
    epsa: str | None = None,
    reva: str | None = None,
) -> Earnings:
    return Earnings(
        report=f"{report_date.isoformat()}-bmo",
        epse=Decimal(epse),
        reve=Decimal(reve),
        epsa=Decimal(epsa) if epsa is not None else None,
        reva=Decimal(reva) if reva is not None else None,
    )


def finnhub_firebase_companies() -> dict[str, FinnhubCompany | None]:
    today = date.today()
    current_year, current_quarter = _quarter_parts(0)
    current_id = _quarter_id(current_year, current_quarter)
    snapshot = (today - timedelta(days=1)).strftime("%Y%m%d")
    acme = _finnhub_earnings(
        today + timedelta(days=7),
        epse="1.20",
        reve="5100000000",
    )
    stable = _finnhub_earnings(
        today + timedelta(days=12),
        epse="0.88",
        reve="980000000",
    )
    return {
        "ACME": FinnhubCompany.model_validate({
            current_id: {snapshot: acme},
        }),
        "NEWC": None,
        "STBL": FinnhubCompany.model_validate({
            current_id: {snapshot: stable},
        }),
    }


def finnhub_client_earnings() -> dict[str, dict[str, Earnings]]:
    today = date.today()
    current_year, current_quarter = _quarter_parts(0)
    next_year, next_quarter = _quarter_parts(1)
    current_id = _quarter_id(current_year, current_quarter)
    next_id = _quarter_id(next_year, next_quarter)
    stable = _finnhub_earnings(
        today + timedelta(days=12),
        epse="0.88",
        reve="980000000",
    )
    return {
        "ACME": {
            current_id: _finnhub_earnings(
                today + timedelta(days=7),
                epse="1.35",
                reve="5400000000",
                epsa="1.42",
                reva="5620000000",
            ),
            next_id: _finnhub_earnings(
                today + timedelta(days=98),
                epse="1.55",
                reve="5900000000",
            ),
        },
        "NEWC": {
            current_id: _finnhub_earnings(
                today + timedelta(days=5),
                epse="0.72",
                reve="740000000",
                epsa="0.77",
                reva="768000000",
            ),
            next_id: _finnhub_earnings(
                today + timedelta(days=96),
                epse="0.81",
                reve="825000000",
            ),
        },
        "STBL": {
            current_id: stable,
        },
    }


def finnhub_earnings_for(company_id: str) -> dict[str, Earnings]:
    known_earnings = finnhub_client_earnings()
    if company_id in known_earnings:
        return known_earnings[company_id]

    today = date.today()
    current_year, current_quarter = _quarter_parts(0)
    next_year, next_quarter = _quarter_parts(1)
    current_id = _quarter_id(current_year, current_quarter)
    next_id = _quarter_id(next_year, next_quarter)
    variation = Decimal(sum(map(ord, company_id)) % 25) / 100
    return {
        current_id: _finnhub_earnings(
            today + timedelta(days=7),
            epse=str(Decimal("1.10") + variation),
            reve=str(Decimal("1800000000") + variation * 100_000_000),
            epsa=str(Decimal("1.18") + variation),
            reva=str(Decimal("1910000000") + variation * 100_000_000),
        ),
        next_id: _finnhub_earnings(
            today + timedelta(days=98),
            epse=str(Decimal("1.28") + variation),
            reve=str(Decimal("2050000000") + variation * 100_000_000),
        ),
    }


def firebase_company_snapshot() -> dict[str, object]:
    gemini_companies = gemini_firebase_companies()
    finnhub_companies = finnhub_firebase_companies()
    snapshot: dict[str, object] = {}
    for ticker in gemini_companies.keys() | finnhub_companies.keys():
        company: dict[str, object] = {}
        gemini_company = gemini_companies.get(ticker)
        if gemini_company is not None:
            company["gemini"] = gemini_company.model_dump(mode="json")
        finnhub_company = finnhub_companies.get(ticker)
        if finnhub_company is not None:
            company["fhe"] = finnhub_company.model_dump(mode="json")
        snapshot[ticker] = company
    return snapshot
