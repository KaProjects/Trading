from typing import Any

from pydantic import BaseModel, RootModel


class Earnings(BaseModel):
    epsa: Any = None
    epse: Any
    reva: Any = None
    reve: Any
    report: str

class Quarter(RootModel):
    root: dict[str, Earnings]

class Company(RootModel):
    root: dict[str, Quarter]
