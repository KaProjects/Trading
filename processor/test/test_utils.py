import logging

from utils import configure_logging


def test_configure_logging_does_not_duplicate_root_handlers():
    logger = logging.getLogger()
    google_genai_logger = logging.getLogger("google_genai.models")
    original_handlers = logger.handlers[:]
    original_level = logger.level
    original_google_genai_level = google_genai_logger.level

    try:
        logger.handlers.clear()
        configure_logging()
        configure_logging()
        assert len(logger.handlers) == 1
        assert google_genai_logger.level == logging.WARNING
    finally:
        for handler in list(logger.handlers):
            logger.removeHandler(handler)
            handler.close()
        logger.handlers.extend(original_handlers)
        logger.setLevel(original_level)
        google_genai_logger.setLevel(original_google_genai_level)
