from datetime import date
from decimal import Decimal

import pytest

from sec.financials import (
    extract_quarters,
    has_quarterly_filings,
    quarter_end,
)


def fact(start, end, value, filed, form="10-Q"):
    return {"start": start, "end": end, "val": value, "filed": filed, "form": form}


def facts_of(**concepts):
    # concept -> {unit: [facts]}
    return {
        "facts": {
            "us-gaap": {
                name: {"units": units} for name, units in concepts.items()
            },
        },
    }


# A calendar fiscal year 2025: Q1-Q3 as 10-Q, the whole year as 10-K.
def revenue_2025(concept="Revenues"):
    return {
        concept: {
            "USD": [
                fact("2025-01-01", "2025-03-31", 1_000_000_000, "2025-05-01"),
                fact("2025-04-01", "2025-06-30", 1_100_000_000, "2025-08-01"),
                fact("2025-01-01", "2025-06-30", 2_100_000_000, "2025-08-01"),
                fact("2025-07-01", "2025-09-30", 1_200_000_000, "2025-11-01"),
                fact("2025-01-01", "2025-09-30", 3_300_000_000, "2025-11-01"),
                fact("2025-01-01", "2025-12-31", 4_700_000_000, "2026-02-15", "10-K"),
            ],
        },
    }


QUARTERS = {"25Q1": "25-03", "25Q2": "25-06", "25Q3": "25-09", "25Q4": "25-12"}


def test_quarter_end_is_the_last_day_of_the_ending_month():
    assert quarter_end("26-02") == date(2026, 2, 28)
    assert quarter_end("24-02") == date(2024, 2, 29)
    assert quarter_end("25-12") == date(2025, 12, 31)


def test_three_month_figures_are_used_in_millions():
    result = extract_quarters(facts_of(**revenue_2025()), QUARTERS)

    assert result["25Q1"].values["reported_revenues"] == Decimal("1000")
    assert result["25Q2"].values["reported_revenues"] == Decimal("1100")
    assert result["25Q3"].values["reported_revenues"] == Decimal("1200")


def test_the_fourth_quarter_is_the_year_less_nine_months():
    result = extract_quarters(facts_of(**revenue_2025()), QUARTERS)

    assert result["25Q4"].values["reported_revenues"] == Decimal("1400")


def test_cash_flow_quarters_are_year_to_date_differences():
    cash = {
        "PaymentsToAcquirePropertyPlantAndEquipment": {
            "USD": [
                fact("2025-01-01", "2025-03-31", 100_000_000, "2025-05-01"),
                fact("2025-01-01", "2025-06-30", 230_000_000, "2025-08-01"),
                fact("2025-01-01", "2025-09-30", 390_000_000, "2025-11-01"),
                fact("2025-01-01", "2025-12-31", 600_000_000, "2026-02-15", "10-K"),
            ],
        },
        "NetCashProvidedByUsedInOperatingActivities": {
            "USD": [
                fact("2025-01-01", "2025-03-31", 300_000_000, "2025-05-01"),
                fact("2025-01-01", "2025-06-30", 700_000_000, "2025-08-01"),
                fact("2025-01-01", "2025-09-30", 1_200_000_000, "2025-11-01"),
                fact("2025-01-01", "2025-12-31", 1_900_000_000, "2026-02-15", "10-K"),
            ],
        },
    }

    result = extract_quarters(facts_of(**cash), QUARTERS)

    assert [result[q].values["reported_capex"] for q in QUARTERS] == [
        Decimal("100"), Decimal("130"), Decimal("160"), Decimal("210"),
    ]
    assert [result[q].values["reported_free_cash_flow"] for q in QUARTERS] == [
        Decimal("200"), Decimal("270"), Decimal("340"), Decimal("490"),
    ]


def test_free_cash_flow_needs_both_cash_flow_and_capex():
    only_cash_flow = {
        "NetCashProvidedByUsedInOperatingActivities": {
            "USD": [fact("2025-01-01", "2025-03-31", 300_000_000, "2025-05-01")],
        },
    }

    result = extract_quarters(facts_of(**only_cash_flow), {"25Q1": "25-03"})

    assert "reported_free_cash_flow" not in result["25Q1"].values


def test_the_tag_is_chosen_per_period_not_per_company():
    # A company that switched tags: the old one only covers early periods.
    concepts = {
        "RevenueFromContractWithCustomerExcludingAssessedTax": {
            "USD": [fact("2025-01-01", "2025-03-31", 900_000_000, "2025-05-01")],
        },
        "Revenues": {
            "USD": [fact("2025-04-01", "2025-06-30", 1_100_000_000, "2025-08-01")],
        },
    }

    result = extract_quarters(
        facts_of(**concepts),
        {"25Q1": "25-03", "25Q2": "25-06"},
    )

    assert result["25Q1"].values["reported_revenues"] == Decimal("900")
    assert result["25Q2"].values["reported_revenues"] == Decimal("1100")


def test_the_total_tag_wins_when_both_cover_the_period():
    concepts = {
        "Revenues": {
            "USD": [fact("2025-01-01", "2025-03-31", 5_000_000_000, "2025-05-01")],
        },
        "RevenueFromContractWithCustomerExcludingAssessedTax": {
            "USD": [fact("2025-01-01", "2025-03-31", 3_000_000_000, "2025-05-01")],
        },
    }

    result = extract_quarters(facts_of(**concepts), {"25Q1": "25-03"})

    assert result["25Q1"].values["reported_revenues"] == Decimal("5000")


def test_the_figure_as_first_filed_is_used_not_a_restatement():
    concepts = {
        "Revenues": {
            "USD": [
                fact("2025-01-01", "2025-03-31", 1_000_000_000, "2025-05-01"),
                fact("2025-01-01", "2025-03-31", 1_050_000_000, "2026-05-01"),
            ],
        },
    }

    result = extract_quarters(facts_of(**concepts), {"25Q1": "25-03"})

    assert result["25Q1"].values["reported_revenues"] == Decimal("1000")


def test_filings_other_than_10q_and_10k_are_ignored():
    concepts = {
        "Revenues": {
            "USD": [fact("2025-01-01", "2025-03-31", 1, "2025-05-01", "8-K")],
        },
    }

    result = extract_quarters(facts_of(**concepts), {"25Q1": "25-03"})

    assert result["25Q1"].values == {}


def test_52_week_fiscal_quarters_end_a_few_days_off_the_month_end():
    concepts = {
        "Revenues": {
            "USD": [fact("2025-12-28", "2026-03-28", 7_000_000_000, "2026-05-01")],
        },
    }

    result = extract_quarters(facts_of(**concepts), {"26Q1": "26-03"})

    assert result["26Q1"].values["reported_revenues"] == Decimal("7000")


def test_a_negative_derived_revenue_means_the_bases_differ_and_is_dropped():
    concepts = {
        "Revenues": {
            "USD": [
                fact("2025-01-01", "2025-09-30", 900_000_000, "2025-11-01"),
                # Restated for discontinued operations: smaller than nine months.
                fact("2025-01-01", "2025-12-31", 600_000_000, "2026-02-15", "10-K"),
            ],
        },
    }

    result = extract_quarters(facts_of(**concepts), {"25Q4": "25-12"})

    assert "reported_revenues" not in result["25Q4"].values


def test_a_loss_is_a_valid_net_income():
    concepts = {
        "NetIncomeLoss": {
            "USD": [fact("2025-01-01", "2025-03-31", -250_000_000, "2025-05-01")],
        },
    }

    result = extract_quarters(facts_of(**concepts), {"25Q1": "25-03"})

    assert result["25Q1"].values["reported_net_income"] == Decimal("-250")


def test_dividends_are_total_cash_paid_in_millions():
    concepts = {
        "PaymentsOfDividendsCommonStock": {
            "USD": [fact("2025-01-01", "2025-03-31", 243_000_000, "2025-05-01")],
        },
    }

    result = extract_quarters(facts_of(**concepts), {"25Q1": "25-03"})

    assert result["25Q1"].values["reported_div"] == Decimal("243")


def per_share_facts(first_three_shares=(100e6, 100e6, 100e6), year_shares=100e6):
    shares = [
        fact("2025-01-01", "2025-03-31", first_three_shares[0], "2025-05-01"),
        fact("2025-04-01", "2025-06-30", first_three_shares[1], "2025-08-01"),
        fact("2025-07-01", "2025-09-30", first_three_shares[2], "2025-11-01"),
        fact("2025-01-01", "2025-12-31", year_shares, "2026-02-15", "10-K"),
    ]
    eps = [
        fact("2025-01-01", "2025-03-31", 1.0, "2025-05-01"),
        fact("2025-04-01", "2025-06-30", 1.1, "2025-08-01"),
        fact("2025-07-01", "2025-09-30", 1.2, "2025-11-01"),
        fact("2025-01-01", "2025-12-31", 4.7, "2026-02-15", "10-K"),
    ]
    net_income = [
        fact("2025-01-01", "2025-03-31", 100_000_000, "2025-05-01"),
        fact("2025-01-01", "2025-09-30", 330_000_000, "2025-11-01"),
        fact("2025-01-01", "2025-12-31", 470_000_000, "2026-02-15", "10-K"),
    ]
    return facts_of(
        WeightedAverageNumberOfDilutedSharesOutstanding={"shares": shares},
        EarningsPerShareDiluted={"USD/shares": eps},
        NetIncomeLoss={"USD": net_income},
    )


def test_diluted_eps_and_shares_are_taken_as_reported():
    result = extract_quarters(per_share_facts(), QUARTERS)

    assert result["25Q1"].values["reported_eps"] == Decimal("1.0")
    assert result["25Q1"].values["reported_shares"] == Decimal("100")
    assert result["25Q1"].estimated == set()


def test_the_fourth_quarter_eps_and_shares_are_marked_estimates():
    # Shares: 4 x 102 - (100 + 100 + 100) = 108; EPS: 140 / 108.
    facts = per_share_facts(year_shares=102e6)

    result = extract_quarters(facts, QUARTERS)["25Q4"]

    assert result.values["reported_shares"] == Decimal("108")
    assert result.values["reported_eps"] == pytest.approx(
        Decimal("140") / Decimal("108")
    )
    assert result.estimated == {"reported_eps", "reported_shares"}


def test_no_estimate_when_the_year_is_on_another_basis_after_a_split():
    # A 10:1 split: the year is restated to 1,000M shares, the quarters not.
    facts = per_share_facts(year_shares=1000e6)

    result = extract_quarters(facts, QUARTERS)["25Q4"]

    assert "reported_shares" not in result.values
    assert "reported_eps" not in result.values
    assert result.estimated == set()


def test_no_estimate_without_all_three_earlier_quarters():
    facts = per_share_facts()
    shares = facts["facts"]["us-gaap"][
        "WeightedAverageNumberOfDilutedSharesOutstanding"
    ]["units"]["shares"]
    del shares[1]

    result = extract_quarters(facts, QUARTERS)["25Q4"]

    assert "reported_shares" not in result.values


def test_no_estimate_without_the_fourth_quarters_net_income():
    facts = per_share_facts(year_shares=102e6)
    facts["facts"]["us-gaap"].pop("NetIncomeLoss")

    result = extract_quarters(facts, QUARTERS)["25Q4"]

    assert result.estimated == set()


def test_fields_a_company_does_not_report_stay_absent():
    result = extract_quarters(facts_of(**revenue_2025()), QUARTERS)

    values = result["25Q2"].values
    assert "reported_gross_profit" not in values
    assert "reported_operating_income" not in values
    assert "reported_capex" not in values


def test_non_dollar_amounts_are_not_mistaken_for_dollars():
    concepts = {
        "Revenues": {
            "CAD": [fact("2025-01-01", "2025-03-31", 1_000_000_000, "2025-05-01")],
        },
    }

    result = extract_quarters(facts_of(**concepts), {"25Q1": "25-03"})

    assert result["25Q1"].values == {}


@pytest.mark.parametrize(
    ("form", "end", "expected"),
    [
        ("10-Q", "2026-06-30", True),
        ("10-K", "2026-06-30", False),
        ("10-Q", "2020-06-30", False),
        ("20-F", "2026-06-30", False),
    ],
)
def test_domestic_quarterly_filings_are_recognised(form, end, expected):
    facts = facts_of(Revenues={
        "USD": [fact("2026-04-01", end, 1, "2026-08-01", form)],
    })

    assert has_quarterly_filings(facts, since=date(2025, 1, 1)) is expected


def test_empty_facts_are_not_quarterly():
    assert has_quarterly_filings({"facts": {}}, since=date(2025, 1, 1)) is False
    assert extract_quarters({"facts": {}}, {"25Q1": "25-03"})["25Q1"].values == {}
