# Use Eclipse Temurin Java 21 Alpine base image
FROM eclipse-temurin:21-alpine

# Add a user to run our application so that it doesn't run as root
RUN addgroup -S chatterbox && adduser -S chatterbox -G chatterbox
USER chatterbox:chatterbox

# Copy classes and dependencies
ARG DEPENDENCY=target/dependency
COPY ${DEPENDENCY}/BOOT-INF/lib /app/lib
COPY ${DEPENDENCY}/META-INF /app/META-INF
COPY ${DEPENDENCY}/BOOT-INF/classes /app

# Define the entrypoint
ENTRYPOINT ["java","-cp","app:app/lib/*","ca.ryanmorrison.chatterbox.ChatterboxApplication"]