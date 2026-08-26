from datetime import date
from unittest.mock import MagicMock, patch

from polygon.models import (
    CompanyNewsHistory,
    CompanySentimentAnalysis,
    NewsSentimentRecord,
    SentimentStatistics,
)
from polygon.service import (
    FirebaseService,
    company_path,
    create_sentiment_analysis_id,
)


def test_get_companies_reads_pgn_nodes_and_preserves_missing_roots():
    analysis_id = "2026-08-26-a1b2c3"
    record_data = {
        "sentiment": {"positive": 2, "mixed": 1},
        "key_takeaways": ["Demand remained strong."],
    }
    database_reference = MagicMock(spec_set=["get"])
    database_reference.get.return_value = {
        "AAPL": {"pgn": {analysis_id: record_data}},
        "MSFT": {"gemini": {"info": {}}},
        "NEWC": "",
    }

    with patch(
        "polygon.service.db.reference",
        autospec=True,
        return_value=database_reference,
    ) as reference:
        companies = FirebaseService().get_companies()

    reference.assert_called_once_with("company")
    assert companies == {
        "AAPL": CompanyNewsHistory.model_validate({
            analysis_id: record_data,
        }),
        "MSFT": None,
        "NEWC": None,
    }


def test_upsert_sentiment_analysis_creates_dated_record_without_total():
    service = FirebaseService()
    analysis = CompanySentimentAnalysis(
        ticker="BRK.B",
        statistics=SentimentStatistics(
            total=4,
            positive=2,
            neutral=1,
            mixed=1,
        ),
        key_takeaways=["Insurance demand remained resilient."],
    )
    analysis_date = date(2026, 8, 26)
    analysis_id = create_sentiment_analysis_id(
        analysis,
        analysis_date,
    )
    database_reference = MagicMock(spec_set=["set"])

    with (
        patch(
            "polygon.service.db.reference",
            autospec=True,
            return_value=database_reference,
        ) as reference,
        patch("polygon.service.date") as current_date,
    ):
        current_date.today.return_value = analysis_date
        result = service.upsert_sentiment_analysis(analysis)

    assert result == analysis_id
    assert analysis_id.startswith("2026-08-26-")
    reference.assert_called_once_with(
        f"{company_path('BRK.B')}/{analysis_id}"
    )
    database_reference.set.assert_called_once_with(
        NewsSentimentRecord.from_analysis(analysis).model_dump(mode="json")
    )
    persisted = database_reference.set.call_args.args[0]
    assert persisted == {
        "sentiment": {
            "mixed": 1,
            "neutral": 1,
            "positive": 2,
        },
        "key_takeaways": ["Insurance demand remained resilient."],
    }
    assert "total" not in persisted["sentiment"]
    assert company_path("BRK.B") == "company/BRK-B/pgn"
