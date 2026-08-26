import re
import unicodedata
from collections.abc import Mapping

from gemini.models import InstitutionRecord

NON_ALPHANUMERIC = re.compile(r"[\W_]+", flags=re.UNICODE)


def normalize_institution_name(name: str) -> str:
    normalized = unicodedata.normalize("NFKC", name).casefold()
    normalized = normalized.replace("&", " and ")
    return NON_ALPHANUMERIC.sub("-", normalized).strip("-")


class InstitutionRegistry:
    def __init__(
        self,
        institutions: Mapping[str, InstitutionRecord],
    ) -> None:
        self.institutions = dict(institutions)
        self.new_institutions: dict[str, InstitutionRecord] = {}
        self._institution_ids_by_alias: dict[str, str] = {}

        for institution_id, institution in self.institutions.items():
            self._index_alias(institution_id, institution_id)
            self._index_alias(institution.name, institution_id)
            for alias_key, alias_name in institution.aliases.items():
                self._index_alias(alias_key, institution_id)
                self._index_alias(alias_name, institution_id)

    def resolve(self, name: str) -> InstitutionRecord | None:
        institution_id = self._institution_ids_by_alias.get(
            normalize_institution_name(name)
        )
        if institution_id is None:
            return None
        return self.institutions[institution_id]

    def resolve_or_create(self, name: str) -> InstitutionRecord:
        observed_name = " ".join(name.split())
        alias_key = normalize_institution_name(observed_name)
        if not alias_key:
            raise ValueError("Institution name cannot be normalized")

        institution = self.resolve(observed_name)
        if institution is not None:
            return institution

        institution = InstitutionRecord(
            name=observed_name,
            aliases={alias_key: observed_name},
            enabled=True,
            trusted=False,
        )
        self.institutions[alias_key] = institution
        self.new_institutions[alias_key] = institution
        self._index_alias(alias_key, alias_key)
        return institution

    def canonical_key(self, name: str) -> str:
        institution = self.resolve(name)
        return normalize_institution_name(
            institution.name if institution is not None else name
        )

    def _index_alias(
        self,
        alias: str,
        institution_id: str,
    ) -> None:
        normalized_alias = normalize_institution_name(alias)
        if not normalized_alias:
            raise ValueError(
                f"Institution {institution_id!r} has an empty alias"
            )

        existing_id = self._institution_ids_by_alias.get(normalized_alias)
        if existing_id is not None and existing_id != institution_id:
            raise ValueError(
                f"Institution alias {normalized_alias!r} belongs to both "
                f"{existing_id!r} and {institution_id!r}"
            )
        self._institution_ids_by_alias[normalized_alias] = institution_id
