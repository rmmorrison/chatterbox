# syntax=docker/dockerfile:1.7
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src

# Cache dependencies first.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -q dependency:go-offline

# Build.
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -q -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app

RUN groupadd --system --gid 10001 chatterbox \
 && useradd  --system --uid 10001 --gid 10001 --no-create-home chatterbox

COPY --from=build /src/target/chatterbox.jar /app/chatterbox.jar

USER chatterbox
ENTRYPOINT ["java", "-jar", "/app/chatterbox.jar"]
