#!/usr/bin/env bash

preconditions() {
  if [[ -f "env.properties" ]]; then
    . "env.properties"
  else
    echo "Can't find env.properties. Maybe forgot to create one in scripts dir?"
    exit 100
  fi

  declare -a requiredVariables=(
    "APP_DATA_PATH"
    "OAUTH_CLIENT_SECRET"
    "DB_PASSWORD"
    "DB_USERNAME"
    "JDBC_URL"
    "DOCKER_PORT"
    "HOST_PORT"
    "LOG_PATH"
    "SERVICE_NAME"
    "TELEGRAF_HOSTNAME"
  )

  for requiredVariable in "${requiredVariables[@]}"; do
    if [ -z "${!requiredVariable}" ]; then
      echo "$requiredVariable not set. Please edit env.properties file in deployment directory."
      exit 1
    fi
  done

}

preconditions

VERSION_TAG="${SERVICE_NAME}-${VERSION}"

# Run new container
echo "Starting new version of container. Using version: ${VERSION}"
echo "Exposing docker port ${DOCKER_PORT} to host port ${HOST_PORT}"

# Use JAVA_OPTS="-XX:+ExitOnOutOfMemoryError" to prevent from running when any of threads runs of out memory and dies

docker run --name ${SERVICE_NAME} -d \
  --network=autocoin-services \
  --net-alias=${SERVICE_NAME} \
  -p ${HOST_PORT}:${DOCKER_PORT} \
  -e BASIC_PASS=${BASIC_PASS} \
  -e DOCKER_TAG=${VERSION_TAG} \
  -e DB_USERNAME=${DB_USERNAME} \
  -e DB_PASSWORD=${DB_PASSWORD} \
  -e JDBC_URL=${JDBC_URL} \
  -e OAUTH_CLIENT_SECRET=${OAUTH_CLIENT_SECRET} \
  -e SERVICE_NAME=${SERVICE_NAME} \
  -e TELEGRAF_HOSTNAME=${TELEGRAF_HOSTNAME} \
  -v ${LOG_PATH}:/app/log \
  -v ${APP_DATA_PATH}:/app/data \
  --memory=200m \
  --restart=no \
  localhost:5000/${SERVICE_NAME}:${VERSION_TAG}

docker network connect autocoin-tig-monitoring ${SERVICE_NAME}
echo "Connected to networks:$(docker inspect ${SERVICE_NAME} --format='{{range $k,$v := .NetworkSettings.Networks}} {{$k}} {{end}}')"
