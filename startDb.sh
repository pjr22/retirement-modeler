#!/usr/bin/env bash
# Start the local Postgres database for retirement-modeler development.
# Idempotent: creates the container the first time, starts it on subsequent runs.
# Data persists across runs in the named docker volume.
set -euo pipefail

CONTAINER_NAME="retirement-modeler-db"
IMAGE="postgres:16-alpine"
VOLUME_NAME="retirement-modeler-pgdata"
DB_USER="retirement_modeler"
DB_PASSWORD="retirement_modeler"
DB_NAME="retirement_modeler"
HOST_PORT="5432"

if docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
  echo "Container '${CONTAINER_NAME}' is already running."
elif docker ps -a --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
  echo "Starting existing container '${CONTAINER_NAME}'..."
  docker start "${CONTAINER_NAME}" >/dev/null
else
  echo "Creating and starting container '${CONTAINER_NAME}'..."
  docker run -d \
    --name "${CONTAINER_NAME}" \
    -e POSTGRES_USER="${DB_USER}" \
    -e POSTGRES_PASSWORD="${DB_PASSWORD}" \
    -e POSTGRES_DB="${DB_NAME}" \
    -p "${HOST_PORT}:5432" \
    -v "${VOLUME_NAME}:/var/lib/postgresql/data" \
    "${IMAGE}" >/dev/null
fi

echo "Waiting for Postgres to be ready..."
until docker exec "${CONTAINER_NAME}" pg_isready -U "${DB_USER}" -d "${DB_NAME}" -q; do
  sleep 1
done

echo "Postgres ready on localhost:${HOST_PORT}"
echo "  database: ${DB_NAME}"
echo "  user:     ${DB_USER}"
