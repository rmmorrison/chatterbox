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

package net.ryanmorrison.chatterbox.jukebox;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.entities.VoiceChannel;

/**
 * Manages the state of audio playback for a specific Discord Guild (server).
 *
 * @author Ryan Morrison
 * @since 1.0
 */
public class GuildMusicManager {

    @Getter
    private final AudioPlayer player;

    @Getter
    private final TrackScheduler scheduler;

    @Getter
    @Setter
    private VoiceChannel voiceChannel;

    public GuildMusicManager(AudioPlayerManager manager) {
        this.player = manager.createPlayer();
        scheduler = new TrackScheduler(this.player);
        this.player.addListener(this.scheduler);
    }

    public AudioPlayerSendHandler getSendHandler() {
        return new AudioPlayerSendHandler(this.player);
    }
}
