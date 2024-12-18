const logger = require('pino')();
const { Events } = require('discord.js');
const database = require('../database.js');

module.exports = {
    type: Events.MessageDelete,
    async execute(client, message) {
        try {
            await database.models.Quote.destroy({
                where: {
                    message: message.id
                }
            });

            await database.models.QuoteHistory.destroy({
                where: {
                    message: message.id
                }
            });
        } catch (error) {
            logger.error(`Unable to delete quote with message ID ${message.id} due to an error.`, error);
        }
    }
}