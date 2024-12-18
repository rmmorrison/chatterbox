const logger = require('pino')();
const { Events, ChannelType} = require('discord.js');
const database = require('../database.js');
const { isQuote } = require('../util.js');

function find(oldMessage) {
    return database.models.Quote.findOne({
        where: {
            message: oldMessage.id
        }
    });
}

module.exports = {
    type: Events.MessageUpdate,
    async execute(client, oldMessage, newMessage) {
        if (oldMessage.partial) {
            try {
                oldMessage = await oldMessage.fetch();
            } catch (error) {
                logger.error(`Unable to complete partial message with ID ${oldMessage.id} due to an error.`, error);
            }
        }

        if (newMessage.author.id === client.user.id) return; // ignore any messages from ourselves
        if (newMessage.channel.type !== ChannelType.GuildText) return; // ignore any messages sent in DMs

        // if the existing message wasn't already a quote, then ignore - don't allow 'promotion'
        if (!isQuote(oldMessage.content)) return;

        let quote;
        try {
            quote = await find(oldMessage);
        } catch (error) {
            logger.error(`Unable to check if message with ID ${oldMessage.id} previously exists in the database due to an error.`, error);
            return;
        }

        if (!isQuote(newMessage.content)) {
            try {
                await quote.destroy();
            } catch (error) {
                logger.error(`Unable to delete quote due to an error.`, error);
            }
        }

        quote.content = newMessage.content;
        try {
            await quote.save();
        } catch (error) {
            logger.error(`Unable to update quote with message ID ${oldMessage.id} due to an error.`, error);
        }
    }
}