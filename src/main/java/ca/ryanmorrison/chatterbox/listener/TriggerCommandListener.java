package ca.ryanmorrison.chatterbox.listener;

import ca.ryanmorrison.chatterbox.constants.TriggerConstants;
import ca.ryanmorrison.chatterbox.exception.DuplicateResourceException;
import ca.ryanmorrison.chatterbox.exception.ResourceNotFoundException;
import ca.ryanmorrison.chatterbox.extension.FormattedListenerAdapter;
import ca.ryanmorrison.chatterbox.persistence.entity.Trigger;
import ca.ryanmorrison.chatterbox.service.TriggerService;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

@Component
public class TriggerCommandListener extends FormattedListenerAdapter {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final String modalPrefix = String.format("%s-", TriggerConstants.TRIGGER_COMMAND_NAME.toLowerCase());
    private final TriggerService triggerService;

    public TriggerCommandListener(@Autowired TriggerService triggerService) {
        this.triggerService = triggerService;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getUser().isBot()) return;
        if (!event.isFromGuild()) return;
        if (!event.getName().equals(TriggerConstants.TRIGGER_COMMAND_NAME)) return;

        if (event.getSubcommandName() == null) {
            log.error("Received trigger command without subcommand somehow. Discord shouldn't be allowing this.");
            event.replyEmbeds(buildErrorResponse("An error occurred while processing your request."))
                    .setEphemeral(true).queue();
            return;
        }

        switch(event.getSubcommandName()) {
            case TriggerConstants.ADD_SUBCOMMAND_NAME:
                event.replyModal(constructAddModal()).queue();
                break;
            case TriggerConstants.EDIT_SUBCOMMAND_NAME:
                requestTrigger(TriggerConstants.EDIT_SUBCOMMAND_NAME, event);
                break;
            case TriggerConstants.DELETE_SUBCOMMAND_NAME:
                requestTrigger(TriggerConstants.DELETE_SUBCOMMAND_NAME, event);
                break;
            default:
                log.error("Received trigger command with unknown subcommand '{}'.", event.getSubcommandName());
                event.replyEmbeds(buildErrorResponse("An error occurred while processing your request."))
                        .setEphemeral(true).queue();
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
            try {
                triggerService.save(new Trigger.Builder()
                        .channelId(event.getChannel().getIdLong())
                        .challenge(event.getValue("challenge").getAsString())
                        .response(event.getValue("response").getAsString())
                        .build());
            } catch (DuplicateResourceException e) {
                event.getHook().sendMessageEmbeds(buildErrorResponse("A trigger with that challenge already exists.")).queue();
                return;
            }

            event.getHook().sendMessageEmbeds(buildSuccessResponse("Trigger added successfully.")).queue();
        } else if (action.startsWith("edit-")) {
            int id = Integer.parseInt(action.substring("edit-".length()));
            try {
                triggerService.edit(id, event.getValue("response").getAsString());
            } catch (ResourceNotFoundException e) {
                event.getHook().sendMessageEmbeds(buildErrorResponse("The specified trigger does not exist.")).queue();
                return;
            }

            event.getHook().sendMessageEmbeds(buildSuccessResponse("Trigger edited successfully.")).queue();
        }
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!event.getComponentId().startsWith(TriggerConstants.TRIGGER_COMMAND_NAME + "-")) return;

        String action = event.getComponentId().substring((TriggerConstants.TRIGGER_COMMAND_NAME + "-").length());

        switch(action) {
            case TriggerConstants.EDIT_SUBCOMMAND_NAME:
                triggerService.find(event.getChannel().getIdLong(), event.getSelectedOptions().get(0).getValue()).ifPresentOrElse(trigger -> {
                    event.replyModal(constructEditModal(trigger.getId(), trigger.getResponse())).queue();
                }, () -> event.replyEmbeds(buildErrorResponse("The specified trigger does not exist.")).setEphemeral(true).queue());
                break;
            case TriggerConstants.DELETE_SUBCOMMAND_NAME:
                event.deferReply().setEphemeral(true).queue();
                try {
                    triggerService.delete(event.getChannel().getIdLong(), event.getSelectedOptions().get(0).getValue());
                } catch (ResourceNotFoundException e) {
                    event.getHook().sendMessage(MessageCreateData.fromEmbeds(buildErrorResponse("The specified trigger does not exist."))).queue();
                    return;
                }
                event.getHook().sendMessage(MessageCreateData.fromEmbeds(buildSuccessResponse("Trigger deleted successfully."))).queue();
                break;
            default:
                log.error("Received string select interaction for triggers with unknown action '{}'.", action);
                event.getHook().sendMessage(MessageCreateData.fromEmbeds(buildErrorResponse("An error occurred while processing your request."))).queue();
        }
    }

    private void requestTrigger(String requestType, SlashCommandInteractionEvent event) {
        Map<Pattern, String> expressions = triggerService.getExpressions(event.getChannel().getIdLong());
        if (expressions.isEmpty()) {
            event.replyEmbeds(buildErrorResponse("No triggers have been set up in this channel."))
                    .setEphemeral(true).queue();
            return;
        }

        event.reply(String.format("⚠️ Please select the trigger you wish to %s.", requestType))
                .addActionRow(
                        StringSelectMenu.create(String.format("%s-%s", TriggerConstants.TRIGGER_COMMAND_NAME, requestType))
                                .addOptions(expressions.keySet().stream()
                                        .map(pattern -> SelectOption.of(pattern.pattern(), pattern.pattern()))
                                        .toList())
                                .build()
                ).setEphemeral(true).queue();
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
