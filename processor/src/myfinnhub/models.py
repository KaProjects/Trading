from datetime import date
from decimal import Decimal
from typing import Annotated

from pydantic import (
    BaseModel,
    ConfigDict,
    RootModel,
    StringConstraints,
    field_validator,
    model_validator,
)

from domain_types import QuarterId

SnapshotDate = Annotated[
    str,
    StringConstraints(pattern=r"^\d{8}$"),
]


class Earnings(BaseModel):
    model_config = ConfigDict(extra="forbid")

    epsa: Decimal | None = None
    epse: Decimal | None = None
    reva: Decimal | None = None
    reve: Decimal | None = None
    report: str

    @field_validator("epsa", "epse", "reva", "reve", mode="before")
    @classmethod
    def empty_string_is_unavailable(cls, value):
        return None if value == "" else value

    @field_validator("report")
    @classmethod
    def report_starts_with_iso_date(cls, value: str) -> str:
        try:
            date.fromisoformat(value[:10])
        except ValueError as exception:
            raise ValueError("report must start with an ISO date") from exception
        return value


class Quarter(RootModel[dict[SnapshotDate, Earnings]]):
    @model_validator(mode="after")
    def snapshot_keys_are_dates(self):
        for snapshot_date in self.root:
            try:
                date.fromisoformat(
                    f"{snapshot_date[:4]}-{snapshot_date[4:6]}-{snapshot_date[6:]}"
                )
            except ValueError as exception:
                raise ValueError(
                    f"Invalid earnings snapshot date: {snapshot_date}"
                ) from exception
        return self


class Company(RootModel[dict[QuarterId, Quarter]]):
    pass
