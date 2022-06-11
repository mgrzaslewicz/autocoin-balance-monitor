#!/bin/bash
preconditions() {
  cd ..
  if [ -f "env.properties" ]; then
    echo "Using env.properties"
    . "./env.properties"
  else
    echo "env.properties not found"
    exit 1
  fi

  declare -a requiredVariables=(
    "APP_DATA_PATH"
    "HOST_PORT"
    "LOG_PATH"
    "OAUTH_CLIENT_SECRET"
    "SERVICE_NAME"
  )

  for requiredVariable in "${requiredVariables[@]}"; do
    if [ -z "${!requiredVariable}" ]; then
      echo "$requiredVariable not set. Please edit env.properties file in deployment directory or provide env variable."
      exit 1
    fi
  done

}

buildJar() {
  if [ -n "${SKIP_TESTS}" ]; then
     mvn clean package -DskipTests
  else
    mvn clean package
  fi
  (cd target/ && cp "${SERVICE_NAME}"*.jar "${SERVICE_NAME}".jar)
}

clearLogs() {
  rm -f log/"${SERVICE_NAME}"*.log
}

runJar() {
  while read envPropertyFileLine; do
    export "$envPropertyFileLine"
  done < env.properties
  java \
  -XX:+ExitOnOutOfMemoryError \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=${APP_DATA_PATH} \
  -Xmx800M \
  -XX:+PrintFlagsFinal \
  -Djava.security.egd=file:/dev/./urandom \
  -Dtelegraf.hostname="" \
  -DautocoinOauth2ServerUrl=http://localhost:9002 \
  -DexchangesApiUrl=http://localhost:9001 \
  -jar target/${SERVICE_NAME}.jar
}

preconditions
buildJar
clearLogs
runJar

