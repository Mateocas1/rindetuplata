# API image (T7e, S16): the Spring Boot application, built with the Maven wrapper and run on a JDK 21
# runtime as a non-root user. Configuration comes only from environment variables (Spring relaxed
# binding), so the image holds no secrets: SPRING_DATASOURCE_URL, SPRING_RABBITMQ_HOST,
# SPRING_MAIL_HOST, RINDETUPLATA_AUTH_MAGICLINKURL, RINDETUPLATA_WEB_ALLOWEDORIGINS and so on (see
# docs/deploy.md and compose.yaml).
#
# Tests are not run here: they need a Docker daemon for Testcontainers. They run locally with
# `./mvnw test` and in CI (.github/workflows/java-test.yml).

FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /build

# The wrapper and the POM go in first, so editing sources does not re-download the dependencies.
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x mvnw && ./mvnw -B -DskipTests dependency:go-offline

COPY src src
RUN ./mvnw -B -DskipTests package && cp "$(ls target/*.jar | head -1)" /build/app.jar

FROM eclipse-temurin:21-jre-alpine AS runtime

# curl is only the health probe; the application itself needs nothing from the base image.
RUN apk add --no-cache curl \
	&& addgroup -S -g 1001 app \
	&& adduser -S -u 1001 -G app -h /app app

WORKDIR /app
COPY --from=build --chown=app:app /build/app.jar /app/app.jar

USER app
EXPOSE 8080

# The same probe `docker compose up --wait` waits for; /actuator/health is public and reports the
# database and the broker.
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=6 \
	CMD curl -fsS http://localhost:8080/actuator/health || exit 1

# JAVA_OPTS lets a small VPS cap the heap (`-Xmx`) without rebuilding; exec keeps PID 1 the JVM.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -XX:MaxRAMPercentage=75 -jar /app/app.jar"]
