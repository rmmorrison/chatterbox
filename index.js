const path = require('node:path');
const logger = require('pino')()
const Sequelize = require('sequelize');
const { Client, Collection, Events, GatewayIntentBits, MessageFlags } = require('discord.js');
const { walkDirectoryTree } = require('./util.js')
const { token } = require('./config.json');

// create discord.js client
const client = new Client({ intents: [GatewayIntentBits.Guilds] });
client.commands = new Collection();

// create Sequelize client
const models = {};
const sequelize = new Sequelize('chatterbox', '', '', {
    host: 'localhost',
    dialect: 'sqlite',
    logging: false,
    storage: 'database.sqlite',
});

// iterate over the commands/ directory to discover and auto-register commands
walkDirectoryTree(path.join(__dirname, 'commands'), (file) => {
    if (!file.endsWith('.js')) return;

    const command = require(file);
    // check that the file exports the minimum required fields
    if ('data' in command && 'execute' in command) {
        client.commands.set(command.data.name, command);
        // check if the command exports any Sequelize models
        if ('models' in command) {
            const moduleModels = command.models(sequelize);
            Object.entries(moduleModels).forEach(([name, model]) => {
                // there's probably a better way to handle duplicates but this works for now
                if (models[name]) {
                    logger.warn(`Duplicate model name detected: ${name}. Skipping to avoid conflicts.`);
                }
                else {
                    models[name] = model;
                }
            });
        }
    }
    else {
        logger.warn(`The command at ${file} is missing a required "data" or "execute" property. Commands can not be registered without those properties.`);
    }
});

client.once(Events.ClientReady, readyClient => {
    (async() => {
        try {
            await sequelize.authenticate();
            logger.info('Database connection established.');
            // sync our models to the database
            // we can set { force: true } to force a sync on an existing database (for dev purposes)
            await sequelize.sync({ alter: true });
            logger.info(`Successfully synchronized ${Object.keys(models).length} models.`);
        } catch (error) {
            logger.error('Unable to connect to the database:', error);
        }
    })();
    logger.info(`Ready! Logged in as ${readyClient.user.tag}.`)
});

client.on(Events.InteractionCreate, async interaction => {
    if (!interaction.isChatInputCommand()) return; // ignore non-chat commands
    const command = interaction.client.commands.get(interaction.commandName);

    if (!command) {
        logger.error(`No command matching ${interaction.commandName} was found.`);
        return;
    }

    try {
        await command.execute(interaction);
    } catch (error) {
        logger.error('Unable to execute interaction due to an error:', error);
        if (interaction.replied || interaction.deferred) {
            await interaction.followUp({ content: 'There was an error while executing this command!', flags: MessageFlags.Ephemeral });
        }
        else {
            await interaction.reply({ content: 'There was an error while executing this command!', flags: MessageFlags.Ephemeral });
        }
    }
});

client.login(token);