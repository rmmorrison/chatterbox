# syntax=docker/dockerfile:1.7
# Build JDK matches the runtime JRE and the JDK that CI tests on. They had
# drifted (build on 26, run on 25, test on 25), which meant the jar shipped to
# production was produced by a compiler no test run ever exercised.
FROM maven:3-eclipse-temurin-26 AS build
WORKDIR /src

# Cache dependencies first.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -q dependency:go-offline

# Build. Tests are skipped here on purpose -- CI runs them, and the deploy
# workflow now gates on that CI job rather than racing it.
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -q -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app

# curl is only here for HEALTHCHECK: a JRE image has no javac, so there is no
# way to run a throwaway Java HTTP client, and the base image ships no wget.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

RUN groupadd --system --gid 10001 chatterbox \
 && useradd  --system --uid 10001 --gid 10001 --no-create-home chatterbox

COPY --from=build /src/target/chatterbox.jar /app/chatterbox.jar

ENV CHATTERBOX_HTTP_PORT=8080

# /health returns 503 until JDA is connected, so start-period covers the
# gateway handshake and the container only reports healthy once the bot can
# actually serve. Without this a hung bot stayed "up" indefinitely.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS "http://127.0.0.1:${CHATTERBOX_HTTP_PORT}/health" || exit 1

USER chatterbox
ENTRYPOINT ["java", "-jar", "/app/chatterbox.jar"]
