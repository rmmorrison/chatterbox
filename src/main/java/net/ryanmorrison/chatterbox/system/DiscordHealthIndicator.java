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

package net.ryanmorrison.chatterbox.system;

import io.micronaut.health.HealthStatus;
import io.micronaut.management.health.indicator.AbstractHealthIndicator;
import net.dv8tion.jda.api.JDA;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.Map;

@Singleton
public class DiscordHealthIndicator extends AbstractHealthIndicator<Map<String, Object>> {

    private final JDA client;

    @Inject
    public DiscordHealthIndicator(JDA client) {
        this.client = client;
    }

    @Override
    protected String getName() {
        return "discordClient";
    }

    @Override
    protected Map<String, Object> getHealthInformation() {
        Map<String, Object> detailsMap = new LinkedHashMap<>(2);

        if (client.getStatus() != JDA.Status.CONNECTED) {
            this.healthStatus = HealthStatus.DOWN;
            detailsMap.put("error", "The Discord API is not fully connected.");
        }
        else {
            this.healthStatus = HealthStatus.UP;
            detailsMap.put("connected", true);
            detailsMap.put("username", client.getSelfUser().getName());
        }

        return detailsMap;
    }
}
