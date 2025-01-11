package ca.ryanmorrison.chatterbox.listener;

import ca.ryanmorrison.chatterbox.extension.FormattedListenerAdapter;
import ca.ryanmorrison.chatterbox.persistence.entity.Trigger;
import ca.ryanmorrison.chatterbox.persistence.repository.TriggerRepository;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TriggerCommandListener extends FormattedListenerAdapter {

    private final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

    private final String slashCommand = "trigger";
    private final String modalPrefix = String.format("%s-", slashCommand);
    private final TriggerRepository triggerRepository;

    public TriggerCommandListener(@Autowired TriggerRepository triggerRepository) {
        this.triggerRepository = triggerRepository;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getUser().isBot()) return;
        if (!event.isFromGuild()) return;
        if (!event.getName().equals(slashCommand)) return;

        if (event.getSubcommandName() == null) {
            LOGGER.error("Received trigger command without subcommand somehow. Discord shouldn't be allowing this.");
            event.replyEmbeds(buildErrorResponse("An error occurred while processing your request."))
                    .setEphemeral(true).queue();
            return;
        }

        switch(event.getSubcommandName()) {
            case "add":
                event.replyModal(constructAddModal()).queue();
                break;
            case "edit":
                triggerRepository.findByChannelIdAndChallenge(event.getChannel().getIdLong(), event.getOption("challenge").getAsString())
                        .ifPresentOrElse(trigger -> event.replyModal(constructEditModal(trigger.getId(), trigger.getResponse())).queue(),
                                () -> event.replyEmbeds(buildErrorResponse("The specified trigger does not exist."))
                                        .setEphemeral(true).queue());
                break;
            case "delete":
                triggerRepository.findByChannelIdAndChallenge(event.getChannel().getIdLong(), event.getOption("challenge").getAsString())
                        .ifPresentOrElse(trigger -> {
                            triggerRepository.delete(trigger);
                            event.replyEmbeds(buildSuccessResponse("Trigger deleted successfully.")).setEphemeral(true).queue();
                        }, () -> event.replyEmbeds(buildErrorResponse("The specified trigger does not exist.")).setEphemeral(true).queue());
                break;
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (event.getUser().isBot()) return;
        if (!event.isFromGuild()) return;
        if (!event.getModalId().startsWith(modalPrefix)) return;

        event.deferReply().setEphemeral(true).queue();

        String action = event.getModalId().substring(modalPrefix.length());
        if (action.equals("add")) {
            triggerRepository.findByChannelIdAndChallenge(event.getChannel().getIdLong(), event.getValue("challenge").getAsString()).ifPresentOrElse(trigger -> {
                event.getHook().sendMessageEmbeds(buildErrorResponse("A trigger with that challenge already exists.")).queue();
            }, () -> triggerRepository.save(new Trigger.Builder()
                    .channelId(event.getChannel().getIdLong())
                    .challenge(event.getValue("challenge").getAsString())
                    .response(event.getValue("response").getAsString())
                    .build()));
            event.getHook().sendMessageEmbeds(buildSuccessResponse("Trigger added successfully.")).queue();
        } else if (action.startsWith("edit-")) {
            int id = Integer.parseInt(action.substring("edit-".length()));
            triggerRepository.findById(id).ifPresentOrElse(trigger -> {
                trigger.setResponse(event.getValue("response").getAsString());
                triggerRepository.save(trigger);
                event.getHook().sendMessageEmbeds(buildSuccessResponse("Trigger edited successfully.")).queue();
            }, () -> event.getHook().sendMessageEmbeds(buildErrorResponse("The specified trigger does not exist.")).queue());
        }
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (event.getUser().isBot()) return;
        if (!event.isFromGuild()) return;
        if (!event.getName().equals(slashCommand)) return;

        if (event.getFocusedOption().getName().equals("challenge")) {
            List<Command.Choice> challenges = triggerRepository.findAllByChannelId(event.getChannel().getIdLong()).stream()
                    .filter(trigger -> trigger.getChallenge().contains(event.getFocusedOption().getValue()))
                    .map(trigger -> new Command.Choice(trigger.getChallenge(), trigger.getChallenge()))
                    .toList();

            event.replyChoices(challenges).queue();
        }
    }

    private Modal constructAddModal() {
        TextInput challenge = TextInput.create("challenge", "Challenge", TextInputStyle.SHORT)
                .build();

        TextInput response = TextInput.create("response", "Response", TextInputStyle.PARAGRAPH)
                .build();

        return Modal.create(String.format("%s%s", modalPrefix, "add"), "Add Trigger")
                .addComponents(ActionRow.of(challenge), ActionRow.of(response))
                .build();
    }

    private Modal constructEditModal(int id, String existingResponse) {
        TextInput response = TextInput.create("response", "Response", TextInputStyle.PARAGRAPH)
                .setPlaceholder(existingResponse)
                .build();

        return Modal.create(String.format("%s%s-%d", modalPrefix, "edit", id), "Edit Trigger")
                .addComponents(ActionRow.of(response))
                .build();
    }
}
