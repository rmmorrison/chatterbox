const logger = require('pino')();
const { Events, ChannelType } = require('discord.js');
const { DataTypes } = require("sequelize");
const database = require('../database.js');
const { isQuote } = require('../util.js');

function create(message) {
    return database.models.Quote.create({
        message: message.id,
        channel: message.channel.id,
        author: message.author.id,
        content: message.content
    });
}

function random(channel) {
    return database.models.Quote.findOne({
        where: {
            channel: channel
        },
        order: database.sequelize.random()
    });
}

function writeHistory(quote) {
    return database.models.QuoteHistory.create({
        message: quote.message,
        channel: quote.channel
    });
}

module.exports = {
    models: (sequelize) => ({
        Quote: sequelize.define('quote', {
            message: {
                type: DataTypes.BLOB,
                primaryKey: true,
                allowNull: false,
                unique: true
            },
            channel: {
                type: DataTypes.BLOB,
                allowNull: false
            },
            author: {
                type: DataTypes.BLOB,
                allowNull: false
            },
            content: {
                type: DataTypes.TEXT,
                allowNull: false,
                unique: true
            }
        }),
        QuoteHistory: sequelize.define('quote_history', {
            message: {
                type: DataTypes.BLOB,
                allowNull: false
            },
            channel: {
                type: DataTypes.BLOB,
                allowNull: false
            }
        }),
    }),
    type: Events.MessageCreate,
    async execute(client, message) {
        if (message.author.id === client.user.id) return; // ignore any messages from ourselves
        if (message.channel.type !== ChannelType.GuildText) return; // ignore any messages sent in DMs

        if (!isQuote(message.content)) return; // ignore messages that fail the test

        try {
            await create(message);
        } catch (error) {
            // was it a non-unique error?
            if (error.name === 'SequelizeUniqueConstraintError') {
                if (error.fields.includes('content')) {
                    // expected - duplicate content adds weight to randomness which we want to avoid
                    logger.debug(`Could not add new quote '${message.content} for channel ${message.channel.id} because it already exists.`);
                    return;
                }
            }
            logger.error(`Unable to create new quote in channel ${message.channel.id} due to an error.`, error);
        }

        let quote;
        try {
            quote = await random(message.channel.id);
        } catch (error) {
            logger.error(`Unable to select a random quote for channel ${message.channel.id} due to an error.`, error);
            return;
        }

        message.reply(`**${quote.content}**`);

        try {
            writeHistory(quote);
        } catch (error) {
            logger.error(`Unable to persist quote history for channel ${message.channel.id} due to an error.`, error);
        }
    }
}