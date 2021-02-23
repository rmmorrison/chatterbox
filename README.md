![license](https://img.shields.io/github/license/rmmorrison/chatterbox)
![build](https://img.shields.io/github/workflow/status/rmmorrison/chatterbox/CI)
![release](https://img.shields.io/github/v/release/rmmorrison/chatterbox?include_prereleases)

# chatterbox

chatterbox is a Discord bot intending to provide fun (and possibly sometimes useful) commands to a private Discord
server in which it operates.

## Running

### Prerequisites

chatterbox relies on a MySQL (or MariaDB) database at present to persist and retrieve data. The database can be deployed separately in a standalone
format, deployed as a Docker container (backed by a persistent volume), or made available via the procurement of a managed MySQL/MariaDB instance.

See the included [docker-compose.yml](https://github.com/rmmorrison/chatterbox/blob/master/docker-compose.yml) file with an example of how to deploy
MariaDB as part of a Docker Compose setup.

### With Docker (and Docker Compose)

In order to run chatterbox, you first must use a Discord account to create a bot and obtain its bot token, used to authenticate to Discord's API.
This is a mandatory requirement. To obtain a bot token:

1. Visit [Discord's Developer Portal](https://discord.com/developers).
1. Sign in using your Discord account.
1. Click the "New Applications" button in the top right corner.
1. Name the new application. (Note: the name given here is what the bot's nickname will be once added to your server.)
1. Click "Bot" in the left-hand menu.
1. Click "Add Bot" and accept the prompt that appears.
1. Click the "Copy" button to copy the required token to your clipboard. You can also use the "Click to Reveal Token" link to reveal the token's value.

You will also need to obtain Discord's internal unique identifier for the user considered the "owner" of the bot. This is presumably you, the reader,
but can be any Discord user you wish.

**NOTE:** Be careful who you consider the bot's owner. The bot may obtain administrator privilege and through that, the user selected may be able to perform sensitive operations (e.g. kicking or banning users) using the bot, even if they lack the privilege to do so themselves within the server.

To obtain the required user identifier:

1. Open Discord's settings in the web or desktop client.
1. Select "Appearance" under "App Settings" in the left-hand menu pane.
1. Under "Advanced", enable "Developer Mode".
1. Exit the menu and return to Discord's main interface.
1. On the user you wish to consider the owner (either yourself or another user), right-click on their profile.
1. Select "Copy ID". This copies the required value to your clipboard.

Docker images are available from GitHub Container Registry and are automatically updated with every release. You can pull the image for a specific release with:

    docker pull ghcr.io/rmmorrison/chatterbox:<version>

You may also use `latest` to obtain the latest available image, but be warned that this tag may pull pre-release versions still in testing. For maximum stability, use a specific version number and increment as desired for upgrades.

The Docker image can be run using environment variables set within the container to configure both
mandatory and optional configuration properties. A table of available properties is defined below:

| Environment Variable                | Description                                                                                                                                                                                                                                 | Mandatory? | Default Value                                                          |
|-------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------|------------------------------------------------------------------------|
| DISCORD_TOKEN                       | The token value obtained from Discord's developer console used to authenticate the bot with Discord's API.                                                                                                                                  | Yes        | N/A                                                                    |
| DISCORD_OWNERID                     | The unique identifier of the user considered the "owner" of this instance of the bot.                                                                                                                                                       | Yes        | N/A                                                                    |
| DISCORD_COMMANDPREFIX               | The prefix the bot will look for to trigger commands.                                                                                                                                                                                       | No         | "!"                                                                    |
| DISCORD_ALTERNATEPREFIX             | An alternative prefix the bot will also look for to trigger commands.                                                                                                                                                                       | No         | "."                                                                    |
| DATASOURCES_DEFAULT_URL             | The URL (in JDBC format) of the database to connect to. This must be available to the Docker container; via Docker networking, an externally accessible resource or a service (e.g. if the bot is deployed inside of a Kubernetes cluster). | No         | "jdbc:mysql://localhost:3306/chatterbox?createDatabaseIfNotExist=true" |
| DATASOURCES_DEFAULT_DRIVERCLASSNAME | The class name of the JDBC driver used to connect to the database. If you are connecting to a MySQL/MariaDB database, this does not need to be changed.                                                                                     | No         | "com.mysql.cj.jdbc.Driver"                                             |
| DATASOURCES_DEFAULT_USERNAME        | The username used to connect to the database server.                                                                                                                                                                                        | No         | "root"                                                                 |
| DATASOURCES_DEFAULT_PASSWORD        | The password used to connect to the database server.                                                                                                                                                                                        | No         | ""                                                                     |
| DATASOURCES_DEFAULT_DIALECT         | The SQL dialect to use when executing queries against the database. If you are connecting to a MySQL/MariaDB database, this does not need to be changed.                                                                                    | No         | MYSQL                                                                  |

Using a reasonable bare minimum properties to start the bot, the Docker command would look like:

    docker run
      -e DISCORD_TOKEN=<insert token here>
      -e DISCORD_OWNERID=<insert owner ID here>
      -e DATASOURCES_DEFAULT_URL=jdbc:mysql://my-database:3306/chatterbox?createDatabaseIfNotExist=true
      -e DATASOURCES_DEFAULT_USERNAME=myuser
      -e DATASOURCES_DEFAULT_PASSWORD=supersecretpassword
      ghcr.io/rmmorrison/chatterbox:latest

### With Kubernetes

**Experimental!**

The [rmmorrison/helm-charts](https://github.com/rmmorrison/helm-charts) repository contains a Helm (chart API v2) chart for chatterbox, which is automatically built and published to a Helm repository on each change.

This Helm chart is tested (and is how chatterbox is currently deployed for its intended purpose), but the author makes no guarantees or claims about its functionality. Namely, this chart has not been tested against Kubernetes clusters with RBAC enabled.

To set up the repository in your own Helm instance, see that repository's [README file](https://github.com/rmmorrison/helm-charts/blob/master/README.md).

Once the repository is set up and the latest repository data pulled (via `helm repo update`), the chart can be installed into a cluster following the example below:

    helm install chatterbox . \
      --set discord.token="<token>" \
      --set discord.ownerId=":<owner ID>" \
      --set database.url="<JDBC database URL>" \
      --set database.username="<username>" \
      --set database.password="<password>"

**Note:** the colon (:) character prefixing the owner ID value is required, to workaround a Helm issue in which integer-only values beyond a certain length are always treated as int64 values and not strings, even if they are quoted.

## Building

The recommended way to build this project is to use the included Maven wrapper to generate a Docker image, which can then be run
to start the bot.

On macOS or Linux, the command below will invoke Maven via a shell script, build the project and construct the Docker image using a locally
available Docker instance:

    ./mvnw package -Dpackaging=docker

On Windows, the included batch file will do the same:

    mvnw.bat package -Dpackaging=docker

If you wish to build a JAR and not a Docker image, use the below Maven command on macOS or Linux:

    ./mvnw package

On Windows:

    mvnw.bat package

## Additional Notes

### Incompatibility with enforced primary keys on tables

If you are using a managed database from a provider such as DigitalOcean, or any provider which mandates the use of the MySQL property `SQL_REQUIRE_PRIMARY_KEY`, your JDBC database URL _must_ contain:
`&sessionVariables=SQL_REQUIRE_PRIMARY_KEY=0`. This is due to Liquibase's automatically generated changelog and lock tables not including any primary keys, which is a mandated requirement by the `SQL_REQUIRE_PRIMARY_KEY` configuration.
The noted addition to the JDBC URL disables this requirement on a per-connection basis, which allows Liquibase to execute successfully.

If your managed database provider offers a configuration setting to disable this requirement, that also can be used instead. However, not all managed service providers allow this configuration to be changed (DigitalOcean notably does not).

## Contributing

Contributions are welcome! The GitHub Actions workflows in this repository are fully parameterized to allow forks to set up their own CI jobs in GitHub Actions with minimal effort.

You will, however, require a GitHub Personal Access Token (PAT) in order to push Docker images to GitHub Container Registry. Use [this link](https://github.com/settings/tokens) to access the Settings page for your GitHub account, generate a new token and select scopes:
* `write:packages`
* `delete:packages`

Once the token has been created, go to the Settings page for your forked repository, select Secrets on the left-hand menu pane, and create a new repository secret called `CR_PAT` with the value being the Personal Access Token you just obtained.

If you do not want to inherit any CI or release workflows, simply push a commit removing them from the `.github/workflows` directory.