const path = require('node:path')
const logger = require('pino')();
const { REST, Routes } = require('discord.js');
const { walkDirectoryTree } = require('./util.js');
const { clientId, guildId, token } = require('./config.json');

const commands = [];

walkDirectoryTree(path.join(__dirname, 'commands'), (file) => {
    if (!file.endsWith('.js')) return;

    const command = require(file);
    if ('data' in command && 'execute' in command) {
        commands.push(command.data.toJSON());
    }
    else {
        logger.warn(`The command at ${file} is missing a required "data" or "execute" property.`);
    }
});

const rest = new REST().setToken(token);

(async () => {
    try {
        logger.info(`Started refreshing ${commands.length} application (/) commands.`);

        const data = await rest.put(
            Routes.applicationGuildCommands(clientId, guildId),
            { body: commands }
        );

        logger.info(`Successfully reloaded ${data.length} application (/) commands.`);
    } catch (error) {
        logger.error(error);
    }
})();