import logging
from typing import TypeVar

from pydantic import BaseModel, ValidationError

ModelT = TypeVar("ModelT", bound=BaseModel)


def parse_company_snapshot(
    snapshot: object,
    *,
    data_root: str,
    model: type[ModelT],
    logger: logging.Logger,
) -> dict[str, ModelT | None]:
    if snapshot is None:
        return {}
    if not isinstance(snapshot, dict):
        raise TypeError("Firebase company snapshot must be a mapping")

    companies: dict[str, ModelT | None] = {}
    for company_id, company_data in snapshot.items():
        if not isinstance(company_id, str) or not isinstance(company_data, dict):
            logger.error("Ignoring malformed Firebase company node %r", company_id)
            continue

        model_data = company_data.get(data_root)
        if model_data is None:
            companies[company_id] = None
            continue

        try:
            companies[company_id] = model.model_validate(model_data)
        except ValidationError as exception:
            logger.error(
                "Ignoring invalid %s data for company %s: %s",
                data_root,
                company_id,
                exception,
            )

    return companies
