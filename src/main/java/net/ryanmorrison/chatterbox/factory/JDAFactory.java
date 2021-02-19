/*
 * chatterbox - a (not so helpful) Discord bot custom written for a private server
 * Copyright (C) 2021 Ryan Morrison
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.ryanmorrison.chatterbox.factory;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandClientBuilder;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Property;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.security.auth.login.LoginException;
import java.util.Collection;

/**
 * Factory class for constructing a {@link net.dv8tion.jda.api.JDA} singleton.
 *
 * @author Ryan Morrison
 * @since 1.0
 */
@Context
@Factory
public class JDAFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(JDAFactory.class);

    private final BeanContext beanContext;

    @Inject
    public JDAFactory(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @Bean(preDestroy = "shutdown")
    @Singleton
    JDA jda(@Property(name = "discord.token") String token,
            @Property(name = "discord.commandprefix", defaultValue = "!") String commandPrefix,
            @Property(name = "discord.alternateprefix", defaultValue = ".") String alternatePrefix,
            @Property(name = "discord.ownerid") String ownerId) throws LoginException {
        CommandClientBuilder commandClientBuilder = new CommandClientBuilder()
                .setPrefix(commandPrefix)
                .setAlternativePrefix(alternatePrefix)
                .setOwnerId(ownerId)
                .setEmojis("\uD83D\uDE03", "\uD83D\uDE2E", "\uD83D\uDE26");

        Collection<? extends Command> discoveredCommands = beanContext.getBeansOfType(Command.class);
        for (Command command : discoveredCommands) {
            LOGGER.debug("Registering class {} as command to the command client.", command.getClass().getCanonicalName());
            commandClientBuilder.addCommand(command);
        }

        JDA jda = JDABuilder
                .createDefault(token)
                .build();
        jda.addEventListener(commandClientBuilder.build());

        Collection<ListenerAdapter> discoveredListeners = beanContext.getBeansOfType(ListenerAdapter.class);
        for (ListenerAdapter listener : discoveredListeners) {
            LOGGER.debug("Registering class {} as event listener to JDA.", listener.getClass().getCanonicalName());
            jda.addEventListener(listener);
        }

        return jda;
    }
}
