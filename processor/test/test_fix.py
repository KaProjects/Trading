from fix import collect_target_institution_names


def test_collect_target_institution_names_from_raw_company_snapshot():
    snapshot = {
        "AAPL": {
            "gemini": {
                "targets": {
                    "first": {"institution": "Baird"},
                    "second": {
                        "institution": "  Northland   Securities ",
                    },
                },
            },
        },
        "MSFT": {
            "gemini": {
                "targets": {
                    "third": {"institution": "Baird"},
                    "invalid": {"institution": None},
                },
            },
        },
    }

    assert collect_target_institution_names(snapshot) == {
        "Baird",
        "Northland Securities",
    }
