import logging
from uuid import uuid4

import pytest

from utils import BaseClass


@pytest.mark.xfail(strict=True, reason="BaseClass adds a new handler for every instance")
def test_base_class_configures_each_named_logger_once():
    identity = f"test.{uuid4()}"
    logger = logging.getLogger(identity)

    try:
        BaseClass(identity=identity)
        BaseClass(identity=identity)
        assert len(logger.handlers) == 1
    finally:
        for handler in list(logger.handlers):
            logger.removeHandler(handler)
            handler.close()
