![license](https://img.shields.io/github/license/rmmorrison/chatterbox)

# chatterbox

chatterbox is a Discord bot intending to provide fun (and possibly sometimes useful) commands to a private Discord
server in which it operates.

## Running

### Prerequisites

In order to add the bot to your own server, you'll need to create a new Application with Discord and obtain the bot
token, which is the authentication credential chatterbox requires to access the Discord API. To create an Application,
you'll need to sign into Discord's [Developer Portal](https://discord.com/developers) with a valid set of Discord
credentials. Once signed in, follow these steps:

1. Click the "New Applications" button in the top right corner.
2. Name the new application. (Note: the name given here is what the bot's nickname will be once added to your server.)
3. Click "Bot" in the left-hand menu.
4. Click the blue "Add Bot" button beside the "Build-A-Bot" option. When prompted to add a bot to this app, select
"Yes, do it!"
5. Scroll down to "Privileged Gateway Intents" and enable both the "Server Members Intent" and "Message Content Intent"
options. These options are mandatory for certain bot behaviour and chatterbox will not function correctly without them.
6. Scroll back to the top. Click the blue "Reset Token" button. When prompted to confirm, select "Yes, do it!"
7. (If prompted) Enter the current two-factor authentication code for your Discord account.
8. Copy the value that appears and keep it in a safe place for now. You'll require it as a mandatory configuration
value for chatterbox. You can click the blue "Copy" button to automatically copy the token to your clipboard.

### Run with Docker (Recommended)

The build process for chatterbox can produce a Docker image and is the recommended way to run the bot, as it doesn't
require any additional runtime dependencies (except for Docker itself or an OCI-compliant container runtime). At this
time, pre-built Docker images are not available, and you will need to build your own. See the [Building](#building)
section for instructions on building (including Docker images).

Docker images are built in compressed `.tar.gz` archive format and need to be loaded into the Docker runtime in order to
be launched as containers. By default, these archives are available at `target/chatterbox-latest.tar.gz` after building.
Loading them into the Docker runtime is done via the following command:

* `docker load --input target/chatterbox.jar`

Running the Docker image with minimal required configuration (the Discord bot token) will launch the bot using an
in-memory database. Using an in-memory database is only recommended for testing purposes, and actual deployments should
use a separate standalone database. PostgreSQL is recommended, and a `docker-compose.yml` is included with this
repository to demonstrate running chatterbox with an external PostgreSQL database.

If you don't wish to use Compose to run the Docker image, you can start a container using Docker directly. The same
caveats apply; an in-memory database is used unless specified otherwise and an external database is recommended. You can
launch the bot via:

* `docker run -e DISCORD_TOKEN=<token> rmmorrison/chatterbox:latest`

See the [Configuration](#configuration) section for more details on available configuration properties.

### Run via JAR

If you don't wish to use Docker to run chatterbox, it can be run via a JAR file directly. You will require a Java 17+
distribution on your system in order for chatterbox to run. Building the project will produce a JAR file in the `target`
directory, which can be started via a terminal:

* `DISCORD_TOKEN=<token> java -jar target/chatterbox-0.0.1-SNAPSHOT.jar`

Configuration is passed as environment variables to the `java` command. As with running in a Docker container, an
in-memory database is used unless specified otherwise and an external database is recommended. See the
[Configuration](#configuration) section for more details on available configuration properties.

# Building

A Java 17+ development kit (JDK) is required to build chatterbox. You can confirm whether your JDK is installed
correctly via executing at a terminal:

* `java -version`

You should see version information similar to the below as a response:

```
openjdk version "17.0.2" 2022-01-18
OpenJDK Runtime Environment (build 17.0.2+8-86)
OpenJDK 64-Bit Server VM (build 17.0.2+8-86, mixed mode, sharing)
```

chatterbox uses the Maven build tool to execute builds and includes the Maven wrapper which enables a fully-encapsulated
Maven build setup without requiring any build tools to be pre-installed on the host. It will obtain a compatible Maven
version, store it alongside the project and use it to execute build actions.

The first step is to clone this repository to a location of your choice. Next, open a terminal (or Command Prompt) and
navigate to the directory you cloned this repository to. Use the Maven wrapper to instruct Maven to build the project
and create a Docker image archive:

* `./mvnw clean package jib:buildTar` (Mac/Linux users)
* `mvnw.bat clean package jib:buildTar` (Windows users)

Note that Docker is _not_ required to be installed or running on the host to build the Docker image. If you don't wish
to run with Docker, you can skip the image build process by omitting `jib:buildTar` from the Maven wrapper command.

Maven will perform the build and hopefully output that it was successful:

```
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

The newly built JAR will be available at `target/chatterbox-0.0.1-SNAPSHOT.jar`. If you built a Docker image archive as
well, it will be available at `target/chatterbox-latest.tar.gz`.

# Configuration

The easiest way to configure chatterbox is via environment variables made available to the Java process (or Docker
container). The below table lists required, recommended and available environment variables that can be provided:

| Environment Variable             | Required?            | Description                                                                                                                  | Example                                                     |
|----------------------------------|----------------------|------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------|
| `DISCORD_TOKEN`                  | Yes                  | The bot token obtained from creating a new Application on the Discord Developer Portal.                                      | OTY0Mzc3MTg1MDM5MzA2ODAy.YljwPg.EZ536TsU8AyYuRzSQcRwz1JHTl8 |
| `DISCORD_ACTIVITY`               | No                   | A custom activity status the bot should report when connecting to Discord. Must be in the format "<activity type>:<status>". | listening:everything you say                                |
| `DISCORD_FORCEGUILDREGISTRATION` | No                   | Configures chatterbox to register its slash commands on a per-guild level and not globally. Useful for testing or debugging. | true                                                        |
| `SPRING_DATASOURCE_URL`          | No (but recommended) | A JDBC URL to an external database for chatterbox to use.                                                                    | jdbc:postgres://localhost:5432/chatterbox                   |
| `SPRING_DATASOURCE_USERNAME`     | No (but recommended) | The username to authenticate to an external database with.                                                                   | chatterbox                                                  |
| `SPRING_DATASOURCE_PASSWORD`     | No (but recommended) | The password to authenticate to an external database with.                                                                   | Xw4ptVsKeWaRPCE8KlxbWzBinTl6XMc4                            |
