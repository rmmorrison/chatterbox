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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

/**
 * Entity representing a "Shout", which references a Discord message in which the message is wholly comprised of either
 * capital letters only (which is, in internet usage, a form of shouting at someone), or a mixture of capital letters
 * and non-letter characters, but in which the capital letters remain the majority of characters in the message.
 *
 * These are persisted to a data store; to be queried later by the bot in a random fashion to return as a "response"
 * when another user "shouts" in a channel. Given enough stored message references, the responses can be humourous.
 *
 * @author Ryan Morrison
 * @since 1.0
 */
@Entity
public class Shout {

    @Column(name = "message_id")
    @Id
    @Getter
    private Long messageId;

    @Column(name = "author_id")
    @Getter
    private Long authorId;

    @Column(name = "channel_id")
    @Getter
    private Long channelId;

    @Column(name = "content")
    @Getter
    @Setter
    private String content;

    public Shout() {
    }

    public Shout(Long messageId, Long authorId, Long channelId, String content) {
        this.messageId = messageId;
        this.authorId = authorId;
        this.channelId = channelId;
        this.content = content;
    }
}
