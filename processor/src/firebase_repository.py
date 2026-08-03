import logging
from collections.abc import Collection
from typing import TypeVar

from pydantic import BaseModel, ValidationError

from error_reporting import ErrorReporter

ModelT = TypeVar("ModelT", bound=BaseModel)


def parse_company_snapshot(
    snapshot: object,
    *,
    data_root: str,
    model: type[ModelT],
    logger: logging.Logger,
    error_reporter: ErrorReporter | None = None,
    required_fields: Collection[str] = (),
) -> dict[str, ModelT | None]:
    if snapshot is None:
        return {}
    if not isinstance(snapshot, dict):
        raise TypeError("Firebase company snapshot must be a mapping")

    companies: dict[str, ModelT | None] = {}
    for company_id, company_data in snapshot.items():
        if not isinstance(company_id, str):
            logger.error("Ignoring malformed Firebase company node %r", company_id)
            continue
        if not isinstance(company_data, dict):
            companies[company_id] = None
            continue

        model_data = company_data.get(data_root)
        if model_data is None:
            companies[company_id] = None
            continue
        if (
            isinstance(model_data, dict)
            and not all(field in model_data for field in required_fields)
        ):
            companies[company_id] = None
            continue

        try:
            companies[company_id] = model.model_validate(model_data)
        except ValidationError as exception:
            if error_reporter is None:
                logger.error(
                    "Ignoring invalid %s data for company %s: %s",
                    data_root,
                    company_id,
                    exception,
                )
            else:
                error_reporter.report(
                    exception,
                    logger=logger,
                    source="FirebaseRepository",
                    operation="parse_company",
                    context={
                        "company_id": company_id,
                        "data_root": data_root,
                    },
                )

    return companies
