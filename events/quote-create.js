const logger = require('pino')()
const { Events, ChannelType } = require("discord.js");
const { DataTypes } = require("sequelize");
const database = require("../database.js");

function matches(str) {
    // Remove emojis and count them (if necessary, use regex for specific emoji ranges)
    const emojiRegex = /\p{Emoji_Presentation}/gu;
    const stringWithoutEmojis = str.replace(emojiRegex, "");

    // Check if string length without emojis is more than 5
    if (stringWithoutEmojis.length <= 5) {
        logger.debug(`String failed length check: ${str}`);
        return false;
    }

    // Check if string represents a currency or URL
    const currencyOrUrlRegex = /\$|€|\u00A3|https?:\/\//i;
    if (currencyOrUrlRegex.test(stringWithoutEmojis)) {
        logger.debug(`String failed currency/URL check: ${str}`);
        return false;
    }

    // Check if all alphabetical characters are uppercase
    const alphaRegex = /[a-z]/; // Look for lowercase letters
    if (alphaRegex.test(stringWithoutEmojis)) {
        logger.debug(`String failed uppercase check: ${str}`);
        return false;
    }

    // Count non-alphabetical characters
    const nonAlphaCount = (stringWithoutEmojis.match(/[^a-zA-Z]/g) || []).length;
    const totalLength = stringWithoutEmojis.length;

    if (nonAlphaCount / totalLength > 0.25) {
        logger.debug(`String failed non-alphabetical character ratio check: ${str}`);
        return false;
    }

    return true;
}

function create(message) {
    return new Promise((resolve, reject) => {
        database.models.Quote.create({
            message: message.id,
            channel: message.channel.id,
            author: message.author.id,
            content: message.content
        }).catch((error) => {
            // was it a non-unique error?
            if (error.name === 'SequelizeUniqueConstraintError') {
                if (error.fields.includes('content')) {
                    // expected - duplicate content adds weight to randomness which we want to avoid
                    logger.debug(`Could not add new quote '${message.content} for channel ${message.channel.id} because it already exists.`);
                    return;
                }
            }
            reject(error);
        });

        resolve();
    });
}

function random(channel) {
    return new Promise((resolve, reject) => {
        database.models.Quote.findOne({
            where: {
                channel: channel
            },
            order: database.sequelize.random()
        }).then((result) => {
            resolve(result);
        }).catch((error) => {
            reject(error);
        });
    });
}

function writeHistory(quote) {
    return new Promise((resolve, reject) => {
        database.models.QuoteHistory.create({
            message: quote.message,
            channel: quote.channel
        }).catch((error) => {
            reject(error);
            logger.error('An error occurred while inserting history.', error);
        });

        resolve();
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

        if (!matches(message.content)) return; // ignore messages that fail the test

        create(message).catch((error) => {
            logger.error(`Unable to create new quote in channel ${message.channel.id} due to an error.`, error);
        });
        random(message.channel.id).then(quote => {
            message.reply(`**${quote.content}**`);
            writeHistory(quote).catch((error) => {
                logger.error(`Unable to persist quote history for channel ${message.channel.id} due to an error.`, error);
            })
        }).catch((error) => {
            logger.error(`Unable to select a random quote for channel ${message.channel.id} due to an error.`, error);
        })
    }
}