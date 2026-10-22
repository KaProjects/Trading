from datetime import date, timedelta
from decimal import Decimal
from unittest.mock import create_autospec

import pytest

from gemini.currency import (
    MIN_RATE_OBSERVATIONS,
    CurrencyConversionError,
    QuarterCurrencyConverter,
    calendar_quarter_period,
    currency_code,
    quarter_period,
)
from gemini.models import FxRate, Quarter
from polygon.client import PolygonClient


def make_quarter(**overrides):
    data = {
        "name": "Q3 2026",
        "id": "26Q3",
        "ending_month": "26-09",
        "report_date_previous_quarter": "2026-07-29",
        "report_date_this_quarter": "2026-10-28",
        "reported_eps": "3000",
        "reported_revenues": "60000000",
        "reported_gross_profit": "45000000",
        "reported_operating_income": "30000000",
        "reported_net_income": "24000000",
        "reported_capex": "12000000",
        "reported_free_cash_flow": "6000000",
        "reported_div": "300",
        "reported_shares": "728",
        "price_min": "250",
        "price_max": "400",
    }
    data.update(overrides)
    return Quarter(**data)


@pytest.fixture
def client():
    return create_autospec(PolygonClient, instance=True)


@pytest.fixture
def fallback():
    return create_autospec(PolygonClient, instance=True)


@pytest.fixture
def converter(client):
    return QuarterCurrencyConverter(client)


@pytest.fixture
def converter_with_fallback(client, fallback):
    return QuarterCurrencyConverter(client, fallback)


def test_quarter_period_spans_the_three_months_ending_in_the_quarter():
    assert quarter_period(make_quarter()) == (
        date(2026, 7, 1),
        date(2026, 9, 30),
    )


def test_quarter_period_handles_a_year_boundary_and_leap_february():
    first = make_quarter(id="26Q1", ending_month="26-02")
    assert quarter_period(first) == (date(2025, 12, 1), date(2026, 2, 28))
    leap = make_quarter(id="24Q1", ending_month="24-02")
    assert quarter_period(leap) == (date(2023, 12, 1), date(2024, 2, 29))


@pytest.mark.parametrize(
    ("symbol", "code"),
    [("$", "USD"), ("₩", "KRW"), ("€", "EUR"), ("£", "GBP"), ("krw", "KRW")],
)
def test_currency_code_maps_symbols_and_iso_codes(symbol, code):
    assert currency_code(symbol) == code


def test_currency_code_rejects_an_unknown_symbol():
    with pytest.raises(CurrencyConversionError, match="Unknown currency"):
        currency_code("¥")


def test_convert_divides_money_by_the_quarter_average_rate(client, converter):
    # 50 days alternating 1400 / 1600 average to exactly 1500.
    client.get_forex_daily_closes.return_value = [
        Decimal("1400"),
        Decimal("1600"),
    ] * 25

    converted, rate = converter.convert(
        make_quarter(),
        from_currency="₩",
        to_currency="$",
    )

    client.get_forex_daily_closes.assert_called_once_with(
        "USD", "KRW", date(2026, 7, 1), date(2026, 9, 30)
    )
    assert rate == FxRate(rate=Decimal("1500.000000"), source="polygon")
    assert converted.reported_revenues == Decimal("40000.0000")
    assert converted.reported_net_income == Decimal("16000.0000")
    assert converted.reported_capex == Decimal("8000.0000")
    assert converted.reported_eps == Decimal("2.0000")
    assert converted.reported_div == Decimal("0.2000")


def test_convert_keeps_shares_prices_and_missing_values(client, converter):
    client.get_forex_daily_closes.return_value = [Decimal("1500")] * 60

    converted, _rate = converter.convert(
        make_quarter(reported_gross_profit=None, reported_capex=None),
        from_currency="₩",
        to_currency="$",
    )

    assert converted.reported_shares == Decimal("728")
    assert converted.price_min == Decimal("250")
    assert converted.price_max == Decimal("400")
    assert converted.reported_gross_profit is None
    assert converted.reported_capex is None
    assert converted.id == "26Q3"
    assert converted.report_date_this_quarter == date(2026, 10, 28)


def test_convert_does_not_mutate_the_original_quarter(client, converter):
    client.get_forex_daily_closes.return_value = [Decimal("1500")] * 60
    quarter = make_quarter()

    converter.convert(quarter, from_currency="₩", to_currency="$")

    assert quarter.reported_revenues == Decimal("60000000")


def test_too_few_rate_observations_fail_instead_of_guessing(
    client,
    converter,
):
    client.get_forex_daily_closes.return_value = [
        Decimal("1500")
    ] * (MIN_RATE_OBSERVATIONS - 1)

    with pytest.raises(
        CurrencyConversionError,
        match="polygon: only 39 daily rates",
    ):
        converter.convert(
            make_quarter(),
            from_currency="₩",
            to_currency="$",
        )


def test_a_quarter_that_has_not_ended_is_not_converted(client, converter):
    quarter = make_quarter(id="99Q4", ending_month="99-12")

    with pytest.raises(CurrencyConversionError, match="has not ended"):
        converter.convert(quarter, from_currency="₩", to_currency="$")

    client.get_forex_daily_closes.assert_not_called()


def test_a_missing_rate_source_error_propagates(client, converter):
    client.get_forex_daily_closes.side_effect = RuntimeError("boom")

    with pytest.raises(RuntimeError, match="boom"):
        converter.convert(
            make_quarter(),
            from_currency="₩",
            to_currency="$",
        )


def test_calendar_quarter_period_follows_the_quarter_id():
    assert calendar_quarter_period("26Q4") == (
        date(2026, 10, 1),
        date(2026, 12, 31),
    )
    assert calendar_quarter_period("27Q1") == (
        date(2027, 1, 1),
        date(2027, 3, 31),
    )


def test_rate_for_an_ended_period_is_its_average(client, converter):
    client.get_forex_daily_closes.return_value = [
        Decimal("1400"),
        Decimal("1600"),
    ] * 25

    rate = converter.rate_for_period(
        date(2026, 7, 1),
        date(2026, 9, 30),
        from_currency="₩",
        to_currency="$",
    )

    assert rate.rate == Decimal("1500.000000")


def test_rate_for_a_period_still_running_is_the_latest_close(
    client,
    converter,
):
    client.get_forex_daily_closes.return_value = [
        Decimal("1490"),
        Decimal("1510.5"),
    ]
    end = date.today() + timedelta(days=30)

    rate = converter.rate_for_period(
        end - timedelta(days=90),
        end,
        from_currency="₩",
        to_currency="$",
    )

    assert rate.rate == Decimal("1510.500000")
    args = client.get_forex_daily_closes.call_args.args
    assert args[:2] == ("USD", "KRW")
    assert args[3] == date.today()


def test_latest_rate_fails_without_any_recent_close(client, converter):
    client.get_forex_daily_closes.return_value = []

    with pytest.raises(CurrencyConversionError, match="No usable USD/KRW rates"):
        converter.latest_rate(from_currency="₩", to_currency="$")


def test_fallback_is_used_when_the_primary_source_fails(
    client,
    fallback,
    converter_with_fallback,
):
    client.get_forex_daily_closes.side_effect = RuntimeError("polygon down")
    fallback.get_forex_daily_closes.return_value = [Decimal("1500")] * 60

    converted, rate = converter_with_fallback.convert(
        make_quarter(),
        from_currency="₩",
        to_currency="$",
    )

    assert rate == FxRate(rate=Decimal("1500.000000"), source="frankfurter")
    assert converted.reported_revenues == Decimal("40000.0000")


def test_fallback_is_used_when_the_primary_data_has_a_gap(
    client,
    fallback,
    converter_with_fallback,
):
    client.get_forex_daily_closes.return_value = [Decimal("1500")] * 10
    fallback.get_forex_daily_closes.return_value = [Decimal("1520")] * 60

    rate = converter_with_fallback.average_rate_for_period(
        date(2026, 7, 1),
        date(2026, 9, 30),
        from_currency="₩",
        to_currency="$",
    )

    assert rate == FxRate(rate=Decimal("1520.000000"), source="frankfurter")


def test_primary_rates_are_not_mixed_with_the_fallback(
    client,
    fallback,
    converter_with_fallback,
):
    client.get_forex_daily_closes.return_value = [Decimal("1500")] * 60

    rate = converter_with_fallback.average_rate_for_period(
        date(2026, 7, 1),
        date(2026, 9, 30),
        from_currency="₩",
        to_currency="$",
    )

    assert rate.source == "polygon"
    fallback.get_forex_daily_closes.assert_not_called()


def test_every_source_failing_reports_each_reason(
    client,
    fallback,
    converter_with_fallback,
):
    client.get_forex_daily_closes.side_effect = RuntimeError("polygon down")
    fallback.get_forex_daily_closes.return_value = []

    with pytest.raises(CurrencyConversionError) as error:
        converter_with_fallback.latest_rate(
            from_currency="₩",
            to_currency="$",
        )

    message = str(error.value)
    assert "polygon: polygon down" in message
    assert "frankfurter: only 0 daily rates" in message
