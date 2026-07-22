FROM python:3.13-slim

ENV PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_NO_CACHE_DIR=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /workdir

COPY requirements.txt ./
RUN python -m pip install -r requirements.txt

ARG APP_UID=10001
ARG APP_GID=10001
RUN groupadd --gid "${APP_GID}" app \
    && useradd --uid "${APP_UID}" --gid "${APP_GID}" --create-home --shell /usr/sbin/nologin app

COPY --chown=app:app . ./

# Empty mount points keep credentials out of the image while preserving the
# paths used by the application.
RUN touch envs.json cert.json \
    && chown app:app /workdir envs.json cert.json

USER app

CMD ["python", "main.py"]
