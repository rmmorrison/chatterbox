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

package net.ryanmorrison.chatterbox.persistence.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

/**
 * Entity representing "shout history", which references the last returned random shout in a given text channel.
 *
 * @author Ryan Morrison
 * @since 1.0
 */
@Entity
public class ShoutHistory {

    @Id
    @Getter
    private Integer id = 0;

    @Column
    @Getter
    @Setter
    private Long messageId;

    @Column
    @Getter
    private Long channelId;

    public ShoutHistory(Long messageId, Long channelId) {
        this.messageId = messageId;
        this.channelId = channelId;
    }

    public ShoutHistory() {
    }
}
