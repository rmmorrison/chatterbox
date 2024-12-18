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
    try {
        database.models.Quote.create({
            message: message.id,
            channel: message.channel.id,
            author: message.author.id,
            content: message.content
        });
    } catch (error) {
        logger.error('An error occurred while inserting a new quote.', error);
    }
}

function random(channel) {
    return new Promise((resolve) => {
        database.models.Quote.findOne({
            where: {
                channel: channel
            },
            order: database.sequelize.random()
        }).then((result) => {
            resolve(result);
        });
    });
}

function history(quote) {
    try {
        database.models.QuoteHistory.create({
            message: quote.message,
            channel: quote.channel
        });
    } catch (error) {
        logger.error('An error occurred while inserting history.', error);
    }
}

module.exports = {
    models: (sequelize) => ({
        Quote: sequelize.define('quote', {
            message: {
                type: DataTypes.BIGINT,
                primaryKey: true,
                allowNull: false,
                unique: true
            },
            channel: {
                type: DataTypes.BIGINT,
                allowNull: false
            },
            author: {
                type: DataTypes.BIGINT,
                allowNull: false
            },
            content: {
                type: DataTypes.TEXT,
                allowNull: false
            }
        }),
        QuoteHistory: sequelize.define('quote_history', {
            message: {
                type: DataTypes.BIGINT,
                allowNull: false
            },
            channel: {
                type: DataTypes.BIGINT,
                allowNull: false
            }
        }),
    }),
    type: Events.MessageCreate,
    async execute(client, message) {
        if (message.author.id === client.user.id) return; // ignore any messages from ourselves
        if (message.channel.type !== ChannelType.GuildText) return; // ignore any messages sent in DMs

        if (!matches(message.content)) return; // ignore messages that fail the test

        create(message);
        random(message.channel.id).then(quote => {
            message.reply(`**${quote.content}**`);
            history(quote);
        });
    }
}