# syntax=docker/dockerfile:1

FROM python:3.13-slim

ENV PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_NO_CACHE_DIR=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /src

RUN --mount=type=bind,source=requirements.txt,target=/tmp/requirements.txt,readonly \
    python -m pip install -r /tmp/requirements.txt

ARG APP_UID=10001
ARG APP_GID=10001
RUN groupadd --gid "${APP_GID}" app \
    && useradd --uid "${APP_UID}" --gid "${APP_GID}" --create-home --shell /usr/sbin/nologin app

COPY --chown=app:app src/ ./

USER app

CMD ["python", "main.py"]
