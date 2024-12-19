const logger = require('pino')();
const { ActionRowBuilder, ButtonBuilder, ButtonStyle, EmbedBuilder, SlashCommandBuilder } = require('discord.js');
const database = require('../database.js');

function buildButtonRow(offset, count) {
    const previous = new ButtonBuilder()
        .setCustomId(`previous-${offset}`)
        .setLabel('Previous')
        .setStyle(ButtonStyle.Secondary);

    const next = new ButtonBuilder()
        .setCustomId(`next-${offset}`)
        .setLabel('Next')
        .setStyle(ButtonStyle.Primary);

    const builder = new ActionRowBuilder();
    if (offset === 0) previous.setDisabled(true);
    if (offset === count - 1) next.setDisabled(true);

    builder.addComponents(previous, next);
    return builder;
}

function buildHistoryEmbed(message) {
    const nickname = message.member.nickname || message.author.globalName;
    const avatarURL = message.member.displayAvatarURL({ dynamic: true });
    const messageURL = `https://discord.com/channels/${message.guild.id}/${message.channel.id}/${message.id}`;

    return new EmbedBuilder()
        .setColor(0x0099FF)
        .setTitle(`**${message.content}**`)
        .setURL(messageURL)
        .setAuthor({ name: nickname, iconURL: avatarURL })
        .setTimestamp(message.createdAt);
}

function findAndCountAll(channel, limit = 1, offset = 0) {
    return database.models.QuoteHistory.findAndCountAll({
        where: {
            channel: channel
        },
        order: [ ['createdAt', 'DESC'] ],
        limit: limit,
        offset: offset
    });
}

async function buildHistoryPage(interaction, offset) {
    const { count, rows } = await findAndCountAll(interaction.channel.id, 1, offset);
    if (count === 0) {
        await interaction.followUp({ content: 'No quotes found in this channel.', components: [] });
        return;
    }

    const history = rows[0];
    if (history === null) {
        logger.error(`Expected history to exist in database because of count, but it was null.`);
        await interaction.followUp({ content: 'An error occurred while fetching quote history.', components: [] });
        return;
    }

    let message;
    try {
        message = await interaction.channel.messages.fetch(history.message);
    } catch (error) {
        logger.error(`Unable to fetch message with ID ${history.message}.`, error);
        await interaction.followUp({ content: 'An error occurred while fetching quote history.', components: [] });
        return;
    }

    return {
        page: buildHistoryEmbed(message),
        count: count
    }
}

module.exports = {
    data: new SlashCommandBuilder()
        .setName('history')
        .setDescription('Interactively displays quote history for the current channel'),
    async execute(client, interaction) {
        await interaction.deferReply({ ephemeral: true });
        const { page, count } = await buildHistoryPage(interaction, 0);

        const response = await interaction.editReply({ embeds: [page], components: [buildButtonRow(0, count)] });
        const collectorFilter = i => i.user.id === interaction.user.id;
        const collector = response.createMessageComponentCollector({
            filter: collectorFilter,
            time: 60_000,
        });

        collector.on('collect', async (confirmation) => {
            // ignore any interactions that aren't valid ones we know of
            if (!confirmation.customId.startsWith('previous') && !confirmation.customId.startsWith('next')) return;
            let offset = parseInt(confirmation.customId.split('-')[1]);

            if (confirmation.customId.startsWith('previous')) {
                offset = offset - 1;
            } else if (confirmation.customId.startsWith('next')) {
                offset = offset + 1;
            }

            const { page, count } = await buildHistoryPage(interaction, offset);
            await confirmation.update({
                embeds: [page],
                components: [buildButtonRow(offset, count)],
            });
        });
    },
};