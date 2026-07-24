import pytest

from gemini.institutions import (
    InstitutionRegistry,
    normalize_institution_name,
)
from gemini.models import InstitutionRecord


@pytest.mark.parametrize(
    ("name", "normalized"),
    [
        ("Northland Securities", "northland-securities"),
        ("Robert W. Baird", "robert-w-baird"),
        ("Baird & Co.", "baird-and-co"),
        ("  Bank   of America  ", "bank-of-america"),
    ],
)
def test_normalize_institution_name(name, normalized):
    assert normalize_institution_name(name) == normalized


def test_registry_resolves_aliases_to_canonical_institution():
    bank_of_america = InstitutionRecord(
        name="Bank of America",
        aliases={
            "bank-of-america": "Bank of America",
            "bank-of-america-securities": (
                "Bank of America Securities"
            ),
            "bofa-securities": "BofA Securities",
        },
        enabled=True,
    )
    registry = InstitutionRegistry({
        "bank-of-america": bank_of_america,
    })

    assert registry.resolve("BofA Securities") == bank_of_america
    assert (
        registry.canonical_key("Bank Of America Securities")
        == "bank-of-america"
    )


def test_registry_creates_enabled_institution_for_unknown_name():
    registry = InstitutionRegistry({})

    institution = registry.resolve_or_create("Northland Securities")

    assert institution == InstitutionRecord(
        name="Northland Securities",
        aliases={
            "northland-securities": "Northland Securities",
        },
        enabled=True,
    )
    assert registry.new_institutions == {
        "northland-securities": institution,
    }


def test_registry_rejects_alias_assigned_to_multiple_institutions():
    with pytest.raises(ValueError, match="belongs to both"):
        InstitutionRegistry({
            "first": InstitutionRecord(
                name="First Institution",
                aliases={"shared": "Shared"},
            ),
            "second": InstitutionRecord(
                name="Second Institution",
                aliases={"shared": "Shared"},
            ),
        })
