![license](https://img.shields.io/github/license/rmmorrison/chatterbox)

# chatterbox

chatterbox is a Discord bot intending to provide fun (and possibly sometimes useful) commands to a private Discord
server in which it operates.

## Running

### Prerequisites

chatterbox is primarily run in containerized form, so Docker is recommended.

Alternatively, chatterbox can be run via its `jar` file directly. In this mode, a Java 17 runtime (or greater) is required.

You must also first use a Discord account to create a bot and obtain its bot token, used to authenticate to Discord's API.
This is a mandatory requirement. To obtain a bot token:

1. Visit [Discord's Developer Portal](https://discord.com/developers).
2. Sign in using your Discord account.
3. Click the "New Applications" button in the top right corner.
4. Name the new application. (Note: the name given here is what the bot's nickname will be once added to your server.)
5. Click "Bot" in the left-hand menu.
6. Click "Add Bot" and accept the prompt that appears.
7. Click the "Copy" button to copy the required token to your clipboard. You can also use the "Click to Reveal Token" link to reveal the token's value.

Make sure that both of the "server members intent" and "message content intent" privileged gateway intents are enabled for your bot in the Discord Developer Portal.
Without them, chatterbox may not operate correctly.

### Run with Docker (Recommended)

In order to obtain a Docker image, building the project at this time is a requirement. You will need to clone this project and use the provided Maven wrapper to build the project and generate a Docker image archive. A Java 17+ Development Kit (JDK) is required and can be obtained from [Oracle](https://www.oracle.com/java/technologies/downloads/) or another Java vendor, such as [Eclipse Temurin](https://adoptium.net/) (formerly AdoptOpenJDK).

Once you have a suitable Java Development Kit, clone this repository and execute the Maven wrapper to build the project:

`./mvnw clean package` (on Mac/Linux) or

`mvnw.bat clean package` (on Windows)

Once the Java project is built (and a resulting `.jar` file is placed in the auto-created `target/` directory), execute Maven a second time to build the Docker image archive:

`./mvnw jib:buildTar` (on Mac/Linux) or

`mvnw.bat jib:buildTar` (on Windows)

(Note: this Docker image build process uses a method that does **not** require a Docker runtime installed and active on the system to produce images. You do not need Docker installed if you are not intending to run the Docker image on the same system you are building with.)

The Docker image will be built and stored as a TAR archive at `target/chatterbox-latest.tar` when complete.

You can now load the resulting image archive into Docker:

`docker load < target/chatterbox-latest.tar` (on Mac/Linux) or

`docker load --input target/chatterbox-latest.tar` (on all platforms)

A `docker-compose.yml` Compose file is provided alongside this README as an example of how to run the bot via Docker Compose, including a separate standalone PostgreSQL database. You will need to edit the Compose file and, at minimum, set the `DISCORD_TOKEN` environment variable to the bot token you obtained in the Prerequisites section.

Alternatively, the bot can be started via Docker directly:

`docker run -e DISCORD_TOKEN=<token> rmmorrison/chatterbox:latest`

Additional environment variables can be set as necessary. Note that without explicit database configuration, the bot will start using an in-memory database which is lost when the application stops. For non-test use, database configuration is **mandatory**.

chatterbox is built with Spring Boot using Spring Data to manage its access to the database; thus its database configuration inherits the configuration provided by Spring. You can see a full list of properties available in the [Spring Boot documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/application-properties.html#appendix.application-properties.data). Note that to provide these values as environment variables to Docker, they must be converted to uppercase and the periods replaced with underscores - for example, if you wanted to configure the property `spring.datasource.url`, you will pass the environment variable `SPRING_DATASOURCE_URL` with your desired value.

You will likely wish to configure:

* `SPRING_DATASOURCE_URL`: the JDBC URL to your database (e.g. `jdbc:postgres://localhost:5432/chatterbox`)
* `SPRING_DATASOURCE_USERNAME`: the username to authenticate to your database (e.g. `chatterbox`)
* `SPRING_DATASOURCE_PASSWORD`: the password to authenticate to your database (e.g. `chatterbox`)

For more detailed tuning, such as managing connection pool properties, see the [Spring Boot documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/application-properties.html#appendix.application-properties.data) for the properties prefixed with `spring.datasource.hikari.*`.

### Run via JAR directly

If you want to start the bot outside of Docker, building the project provides a "jar-with-dependencies" artifact in the `target/` directory that is an all-in-one JAR file containing everything the bot needs to run.

It can be started via a terminal:

`DISCORD_TOKEN=<token> java -jar target/chatterbox-0.0.1-SNAPSHOT.jar`

As with running under Docker, without explicit database configuration the bot will start using an in-memory database which is lost when the application stops. For non-test use, database configuration is **mandatory**. See the above section on Docker deployments for a reference on what environment variables are recommended.