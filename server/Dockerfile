FROM python:3.12-slim

WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Copy package metadata and source
COPY pyproject.toml .
COPY src/ src/
COPY scripts/ scripts/

# Install python dependencies
RUN pip install --no-cache-dir -e .

EXPOSE 8000 8001 8002

ENV TDA_WS_HOST=0.0.0.0
ENV TDA_WS_PORT=8000
ENV TDA_HTTP_PORT=8001
ENV TDA_INGEST_PORT=8002

CMD ["python", "scripts/serve_local.py"]
