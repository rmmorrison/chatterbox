const path = require('node:path');
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
                    console.log(`[WARNING] Duplicate model name detected: ${name}. Skipping.`);
                }
                else {
                    models[name] = model;
                }
            });
        }
    }
    else {
        console.log(`[WARNING] The command at ${file} is missing a required "data" or "execute" property.`);
    }
});

client.once(Events.ClientReady, readyClient => {
    (async() => {
        try {
            await sequelize.authenticate();
            console.log('Database connection established.');
            // sync our models to the database
            // we can set { force: true } to force a sync on an existing database (for dev purposes)
            await sequelize.sync({ alter: true });
            console.log(`Successfully synchronized ${Object.keys(models).length} models.`);
        } catch (error) {
            console.error('Unable to connect to the database:', error);
        }
    })();
    console.log(`Ready! Logged in as ${readyClient.user.tag}`)
});

client.on(Events.InteractionCreate, async interaction => {
    if (!interaction.isChatInputCommand()) return; // ignore non-chat commands
    const command = interaction.client.commands.get(interaction.commandName);

    if (!command) {
        console.error(`No command matching ${interaction.commandName} was found.`);
        return;
    }

    try {
        await command.execute(interaction);
    } catch (error) {
        console.error(error);
        if (interaction.replied || interaction.deferred) {
            await interaction.followUp({ content: 'There was an error while executing this command!', flags: MessageFlags.Ephemeral });
        }
        else {
            await interaction.reply({ content: 'There was an error while executing this command!', flags: MessageFlags.Ephemeral });
        }
    }
});

client.login(token);