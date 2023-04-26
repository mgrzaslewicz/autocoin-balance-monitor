#!/usr/bin/env bash
set -x # turn on debug mode
set -e # exit on any error

AUTOCOIN_DEPLOYMENT_DIR="${AUTOCOIN_DEPLOYMENT_DIR:=/opt/autocoin/apps}"
SERVICE_NAME="${SERVICE_NAME:=autocoin-exchange-mediator}"
PROPERTY_FILE="${PROPERTY_FILE:=env.properties}"
APP_DATA_PATH="${APP_DATA_PATH:=$AUTOCOIN_DEPLOYMENT_DIR/$SERVICE_NAME/data}"
LOG_PATH="${LOG_PATH:=$AUTOCOIN_DEPLOYMENT_DIR/$SERVICE_NAME/log}"
VERSION="${VERSION:=latest}"
TELEGRAF_HOSTNAME="${TELEGRAF_HOSTNAME:=telegraf}"

preconditions() {
  declare -a requiredVariablesWithoutDefaults=(
    "DB_PASSWORD"
    "DB_USERNAME"
    "JDBC_URL"
    "HOST_PORT"
    "DOCKER_PORT"
    "OAUTH_CLIENT_SECRET"
  )

  echo "Expecting variables ${requiredVariablesWithoutDefaults[*]} to be provided by the environment or $PROPERTY_FILE file as those have no default values."

  if [[ -f "$PROPERTY_FILE" ]]; then
    echo "Loading variables from $PROPERTY_FILE"
    . "$PROPERTY_FILE"
  else
    echo "Can't find $PROPERTY_FILE. Expecting variables to be provided by the environment"
  fi

  for requiredVariable in "${requiredVariablesWithoutDefaults[@]}"; do
    if [ -z "${!requiredVariable}" ]; then
      echo "$requiredVariable not set. Provide it via $PROPERTY_FILE file in deployment directory or env variable."
      exit 1
    fi
  done
}

preconditions
if [ ! -v DOCKER_REGISTRY ]; then
  echo "Setting DOCKER_REGISTRY to default localhost:5000/"
  DOCKER_REGISTRY="localhost:5000/"
fi

# Run new container
echo "Starting container '${SERVICE_NAME}:${VERSION}'"
echo "Exposing docker port ${DOCKER_PORT} to host port ${HOST_PORT}"

# in theory JVM -Xmx should be enough, but it's good to keep memory limit for container too
docker run --name "${SERVICE_NAME}" -d \
  --network=autocoin-services \
  --net-alias="${SERVICE_NAME}" \
  -p "${HOST_PORT}":"${DOCKER_PORT}" \
  -v "${LOG_PATH}":/app/log \
  -v "${APP_DATA_PATH}":/app/data \
  --memory=200m \
  --restart=no \
  -e SERVICE_NAME="${SERVICE_NAME}" \
  -e JVM_ARGS="-Xmx200M" \
  -e TELEGRAF_HOSTNAME="${TELEGRAF_HOSTNAME}" \
  -e OAUTH_CLIENT_SECRET="${OAUTH_CLIENT_SECRET}" \
  -e DB_USERNAME=${DB_USERNAME} \
  -e DB_PASSWORD=${DB_PASSWORD} \
  -e JDBC_URL=${JDBC_URL} \
  "${DOCKER_REGISTRY}${SERVICE_NAME}:${VERSION}"

docker network connect autocoin-tig-monitoring "${SERVICE_NAME}"
echo "Connected to networks:$(docker inspect "${SERVICE_NAME}" --format='{{range $k,$v := .NetworkSettings.Networks}} {{$k}} {{end}}')"
