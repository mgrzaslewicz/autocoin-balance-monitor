FROM eclipse-temurin:17-alpine

ADD target/autocoin-balance-monitor*.jar /app/autocoin-balance-monitor.jar

WORKDIR /app
RUN mkdir -p /app/data

RUN adduser -D nonroot
RUN chown -R nonroot:nonroot /app
USER nonroot

EXPOSE 10022
ENTRYPOINT ["java", "-XX:+ExitOnOutOfMemoryError", "-XX:+HeapDumpOnOutOfMemoryError", "-XX:HeapDumpPath=/app/data", "-XX:+PrintFlagsFinal", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "autocoin-balance-monitor.jar"]
