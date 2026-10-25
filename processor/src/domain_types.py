from typing import Annotated

from pydantic import StringConstraints

QuarterId = Annotated[
    str,
    StringConstraints(pattern=r"^\d{2}Q[1-4]$"),
]
Ticker = Annotated[
    str,
    StringConstraints(
        min_length=1,
        max_length=15,
        pattern=r"^[A-Z][A-Z0-9.-]*$",
        strip_whitespace=True,
        to_upper=True,
    ),
]
